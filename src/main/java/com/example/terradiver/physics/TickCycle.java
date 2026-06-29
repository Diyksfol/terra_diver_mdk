package com.example.terradiver.physics;

import com.example.terradiver.drilling.DrillingRate;
import com.example.terradiver.drilling.DrillingUtils;
import com.example.terradiver.drilling.DrillMode;
import com.example.terradiver.drilling.DrillModeDetector;
import com.example.terradiver.pressure.PressureUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/*
 * Тик-цикл (TD_01) — единая точка правды. Каждый тик: detect_drill_mode → одна из трёх веток
 * (A продвижение / B бур на месте / C остывание) → общий хвост выталкивания (A и C, не B).
 * См. TD_06, «Полный порядок вызовов в тик-цикле».
 *
 * Архитектура: advanceTick — ЧИСТАЯ оркестрация. Все формулы (detect_drill_mode, давление, темп,
 * сопротивление, выталкивание) — статические, вызываются здесь в правильном порядке и тестируются
 * без Minecraft. Все ЭФФЕКТЫ в мире (прочистка, импульсы, крен, звук) — за сёмой Context, которую
 * адаптер реализует через реальные функции, а тест — фейком. Состояние между тиками — в State.
 *
 * На стороне адаптера остаётся: чтение сенсоров/движка в Inputs (включая наш scanContactSolids и
 * предрасчёт геометрии до прочистки — решение «без лага на тик»), и реализация Context поверх
 * clear_blocks / apply_drilling_velocity / lock_roll_for_segment / ensure_signal_*. Там же осядут
 * оставшиеся TODO[API-CHECK] (mining_candidates, RPM, доступ к блокам SubLevel, масса тела).
 */
public final class TickCycle {

    private TickCycle() {}

    // ── Состояние между тиками (живёт в BlockEntity подшипника) ─────────────────

    public record State(float previousForwardRate, float pendingResistance,
                        Float segmentLockedRoll, boolean wasForwardLastTick) {
        public static State initial() {
            return new State(0f, 0f, null, false);
        }
    }

    // ── Прочитанное за тик (заполняет адаптер с сенсоров/движка) ────────────────

    public record Inputs(
        Optional<Integer> consistentSign, // check_crown_rotation_consistency (>0 CW, <0 CCW, empty рассогласование)
        float speed,                       // velocity_sensor
        boolean hasDiggableNearby,         // !diggable_set.isEmpty()
        float effectiveSpeed,              // rpm_input / 4
        float rpmInput,                    // обороты подшипника
        int foundCount,                    // len(mining_candidates)
        float avgMaterialFactor,           // compute_avg_material_factor (геометрия ДО прочистки)
        float crownFaceArea,               // compute_crown_face_area
        float[] hardnesses,                // твёрдости diggable_set (предизвлечены из BlockState)
        float y,                           // высотомер
        float ribCoverage,                 // HullCache
        Vec3 crownCenter,                  // ResistanceField.crownCenter(позиции корон)
        List<BlockPos> contactSolids,      // ResistanceField.scanContactSolids (наш скан)
        float[] gimbalAngles               // gimbal_sensor.getAngles() [pitch,yaw,roll]
    ) {}

    // ── Балансовые константы (из ModConfig) ─────────────────────────────────────

    public record Constants(
        float diveTriggerSpeed, float diveHoldSpeed,
        int minBlocksPerTick,
        float pendingGrowth, float pendingDecay, float pendingMax,
        float maxCompensation,
        float minHardness, float rateMax, float pendingResistanceFactor,
        float pushbackThreshold, float pushbackFactor,
        float soundDamping,
        float deepslateY, float bedrockY, float pressureK, float pressureScale
    ) {}

    // ── Сёма эффектов в мире (реализует адаптер) ────────────────────────────────

    public interface Context {
        // clear_blocks(mining_candidates, sub_level, buffer, max) → processed_count
        int clearBlocks(int maxBlocksPerTick);
        // lock_roll_for_segment(gimbal, locked, rigid_body) → новый зафиксированный крен (плюс угловой импульс)
        Float lockRoll(Float segmentLockedRoll);
        // apply_drilling_velocity(rate, heading, ..., driveForward=true) — тяга вперёд
        void applyForwardImpulse(float forwardRate);
        // apply_drilling_velocity(magnitude, direction, ..., driveForward=false) — выталкивание вдоль direction
        void applyPushbackImpulse(Vec3 direction, float magnitude);
        // ensure_signal_active(y, deepslateY, true, channel, volume)
        void signalActive(float y, float deepslateY, float volume);
        // ensure_signal_stopped(channel)
        void signalStopped();
    }

    // ── Оркестрация ─────────────────────────────────────────────────────────────

    /*
     * Один тик. Чистая: ветвление, порядок вызовов через Context, переход состояния.
     * Возвращает новое State (для сохранения в BlockEntity до следующего тика).
     */
    public static State advanceTick(State s, Inputs in, Constants k, Context ctx) {
        DrillMode mode = DrillModeDetector.detect_drill_mode(
            in.consistentSign(), in.speed(), s.wasForwardLastTick(), in.hasDiggableNearby(),
            k.diveTriggerSpeed(), k.diveHoldSpeed());

        float pending = s.pendingResistance();
        float forwardRate;
        Float lockedRoll;
        boolean wasForward;

        switch (mode) {
            case DRILLING_FORWARD -> {
                // Ветка A: бур + продвижение. Порядок шагов важен (каждый кормит следующий).
                int maxBlocks = DrillingUtils.compute_max_blocks_per_tick(
                    s.previousForwardRate(), in.effectiveSpeed(), in.avgMaterialFactor(), k.minBlocksPerTick());
                int processed = ctx.clearBlocks(maxBlocks);
                pending = DrillingUtils.update_pending_resistance(
                    pending, in.foundCount(), processed, k.pendingGrowth(), k.pendingDecay(), k.pendingMax());
                float raw = PressureUtils.compute_raw_debuff(
                    in.y(), k.deepslateY(), k.bedrockY(), k.pressureK(), k.pressureScale());
                float pressureEff = PressureUtils.compute_pressure_debuff_effective(
                    raw, in.ribCoverage(), k.maxCompensation());
                forwardRate = DrillingRate.compute_drilling_rate_core(
                    in.avgMaterialFactor(), in.crownFaceArea(), pressureEff, in.hardnesses(),
                    in.rpmInput(), k.minHardness(), k.rateMax(), pending, k.pendingResistanceFactor());
                lockedRoll = ctx.lockRoll(s.segmentLockedRoll());     // фиксация крена + корректирующий момент
                ctx.applyForwardImpulse(forwardRate);                 // тяга вперёд вдоль носа
                ctx.signalActive(in.y(), k.deepslateY(), ambientVolume(in, k));
                wasForward = true;
            }
            case DRILLING_STATIONARY -> {
                // Ветка B: бур на месте (CCW). Прочистка без тяги; сопротивление только спадает.
                int maxBlocks = DrillingUtils.compute_max_blocks_per_tick(
                    s.previousForwardRate(), in.effectiveSpeed(), in.avgMaterialFactor(), k.minBlocksPerTick());
                ctx.clearBlocks(maxBlocks);                           // дроп в буфер, тело не двигается
                pending = DrillingUtils.decay_pending_resistance(pending, k.pendingDecay());
                ctx.signalActive(in.y(), k.deepslateY(), ambientVolume(in, k));
                // Хвост ПРОПУСКАЕТСЯ — стационар без тяги. Сегмент продвижения окончен → крен сброшен.
                return new State(0f, pending, null, false);
            }
            case INACTIVE -> {
                // Ветка C: остывание / Air Mode. Сопротивление спадает; сигнал глушится.
                pending = DrillingUtils.decay_pending_resistance(pending, k.pendingDecay());
                forwardRate = 0f;
                lockedRoll = null;                                    // сегмент окончен
                ctx.signalStopped();
                wasForward = false;
            }
            default -> throw new IllegalStateException("unreachable DrillMode: " + mode);
        }

        // Общий хвост выталкивания — ТОЛЬКО ветки A и C (B вышла раньше). См. TD_06 v1.2.
        Vec3 dir = ResistanceField.compute_resistance_direction(in.crownCenter(), in.contactSolids());
        float mag = ResistanceField.compute_pushback(
            pending, k.pushbackThreshold(), k.pushbackFactor(), k.rateMax());
        if (mag > 0f && !dir.equals(Vec3.ZERO)) {
            ctx.applyPushbackImpulse(dir, mag);                       // толчок наружу из породы
        }

        return new State(forwardRate, pending, lockedRoll, wasForward);
    }

    // Громкость скрежета по глубине и покрытию рёбрами (общий шаг веток A и B).
    private static float ambientVolume(Inputs in, Constants k) {
        return PressureUtils.compute_ambient_signal_volume(
            in.y(), k.deepslateY(), k.bedrockY(), in.ribCoverage(), k.soundDamping());
    }
}
