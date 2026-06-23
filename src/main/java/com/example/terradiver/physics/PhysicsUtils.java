package com.example.terradiver.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf; // ⚠️ см. примечание к get_heading — тип уточнить по Sable

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Domain 0 primitives (Terra Diver). Спецификация и обоснования — TD_06 v1.0, Домен 0.
 * Здесь только реализация; развёрнутые граничные случаи не дублируются (см. документ).
 */
public class PhysicsUtils {

    // Локальная ось "вперёд" (нос) в системе координат штуковины. Конструктивная константа.
    private static final Vec3 LOCAL_FORWARD_AXIS = new Vec3(0, 0, 1);

    /**
     * Провайдер скорости подшипника по умолчанию (production).
     * TODO[API]: заменить тело на реальный вызов BoreheadBearingBlockEntity.getSpeed()
     * после подтверждения типа.
     */
    private static final IBearingSpeedProvider DEFAULT_BEARING_SPEED_PROVIDER =
        bearing -> 0.0f; // placeholder до подключения реального типа

    // ── Публичные функции (Domain 0) ─────────────────────────────────────────────

    /**
     * get_heading() — нос штуковины в мировых координатах. См. TD_06, Домен 0.
     * heading = ориентация носа (НЕ вектор скорости): при падении они расходятся.
     *
     * ⚠️ API НЕ ПОДТВЕРЖДЁН: тип orientation. TD_06 указывает источник
     * KinematicContraption.sable$getOrientation(). Здесь предполагается org.joml.Quaternionf
     * (стандарт MC 1.21). Старый com.simibubi.create...Quaternion удалён ещё в 1.19 —
     * НЕ использовать. Свериться с реальным возвращаемым типом Sable.
     */
    public static Vec3 get_heading(Quaternionf orientation) {
        if (orientation == null) {
            return LOCAL_FORWARD_AXIS.normalize();
        }
        org.joml.Vector3f v = new org.joml.Vector3f(0f, 0f, 1f);
        orientation.transform(v); // поворот оси носа кватернионом
        return new Vec3(v.x(), v.y(), v.z()).normalize(); // нормализация — защита от дрейфа float
    }

    /**
     * is_diggable() — можно ли бурить блок. См. TD_06, Домен 0.
     * Возвращает (hardness >= 0) AND (NOT is_fluid).
     */
    public static boolean is_diggable(BlockState block) {
        if (block == null) {
            return false;
        }
        // НЕ is_fluid. ⚠️ В MC 1.21 класс Material удалён — getMaterial().isLiquid() НЕ существует.
        if (!block.getFluidState().isEmpty()) {
            return false;
        }
        // hardness >= 0. Bedrock = -1.
        // ⚠️ getDestroySpeed(level,pos) с null небезопасен для блоков, читающих level/pos.
        // Для статической твёрдости используем defaultDestroyTime() самого блока.
        float hardness = block.getBlock().defaultDestroyTime();
        return hardness >= 0;
    }

    /**
     * get_aligned_crowns() — короны, выровненные с heading. См. TD_06, Домен 0.
     * Единый источник aligned-набора для compute_crown_face_area / compute_avg_material_factor.
     */
    public static List<CrownBlock> get_aligned_crowns(List<CrownBlock> crownBlocks, Vec3 heading) {
        List<CrownBlock> aligned = new ArrayList<>();
        if (crownBlocks == null || crownBlocks.isEmpty() || heading == null) {
            return aligned;
        }
        for (CrownBlock crown : crownBlocks) {
            if (crown.isAlignedWithHeading(heading)) {
                aligned.add(crown);
            }
        }
        return aligned;
    }

    /**
     * check_crown_rotation_consistency() — production-версия (использует DEFAULT_BEARING_SPEED_PROVIDER).
     * See overload with explicit provider for testing.
     */
    public static Optional<Integer> check_crown_rotation_consistency(
            List<CrownBlock> crownBlocks, Vec3 heading) {
        return check_crown_rotation_consistency(crownBlocks, heading, DEFAULT_BEARING_SPEED_PROVIDER);
    }

    /**
     * check_crown_rotation_consistency() — все ли выровненные короны крутятся согласованно.
     * См. TD_06, Домен 0. Возвращает знак (+1 CW / -1 CCW), либо empty если нет короны,
     * знаки смешаны, или есть стоящий подшипник среди вращающихся.
     *
     * <p>Перегрузка с явным {@code speedProvider} используется в юнит-тестах для подстановки мока.
     * В production-коде использовать {@link #check_crown_rotation_consistency(List, Vec3)}.
     */
    static Optional<Integer> check_crown_rotation_consistency(
            List<CrownBlock> crownBlocks, Vec3 heading, IBearingSpeedProvider speedProvider) {

        List<CrownBlock> aligned = get_aligned_crowns(crownBlocks, heading);
        if (aligned.isEmpty()) {
            return Optional.empty();
        }

        // Собираем уникальные подшипники из выровненных корон
        Set<Object> bearings = new HashSet<>();
        for (CrownBlock crown : aligned) {
            Object b = crown.getBearingReference();
            if (b != null) {
                bearings.add(b);
            }
        }
        if (bearings.isEmpty()) {
            return Optional.empty();
        }

        Integer sign = null;
        for (Object bearing : bearings) {
            float speed = speedProvider.getSpeed(bearing);
            int s = (speed > 0) ? 1 : (speed < 0) ? -1 : 0;
            if (s == 0) {
                // Стоящий подшипник при наличии короны → бурение не активно
                return Optional.empty();
            }
            if (sign == null) {
                sign = s;
            } else if (!sign.equals(s)) {
                // Смешанные знаки → рассогласование
                return Optional.empty();
            }
        }
        return Optional.ofNullable(sign);
    }

    /**
     * project_crown_front() — production-версия (использует DEFAULT_WORLD_BLOCK_READER).
     */
    public static List<BlockStateAtPos> project_crown_front(
            List<CrownBlock> alignedCrowns, Vec3 heading, Object subLevel) {
        return project_crown_front(alignedCrowns, heading, subLevel, DEFAULT_WORLD_BLOCK_READER);
    }

    /**
     * project_crown_front() — Domain 0. Узкая линия продвижения для Bedrock-гейта.
     * См. TD_06, Домен 0. НЕ моделирует форму выработки (её строит Offroad).
     * Единственный потребитель — is_bedrock_blocking() (TD_02).
     *
     * <p>Перегрузка с явным {@code blockReader} используется в юнит-тестах.
     */
    static List<BlockStateAtPos> project_crown_front(
            List<CrownBlock> alignedCrowns, Vec3 heading, Object subLevel,
            IWorldBlockReader blockReader) {

        List<BlockStateAtPos> blocksAhead = new ArrayList<>();
        if (alignedCrowns == null || alignedCrowns.isEmpty() || heading == null) {
            return blocksAhead;
        }

        // Шаг 1: передний слой (минимальная глубина вдоль heading, допуск ~1 блок).
        float minDepth = Float.MAX_VALUE;
        for (CrownBlock crown : alignedCrowns) {
            minDepth = Math.min(minDepth, crown.getDepthAlongHeading());
        }
        final float LAYER_TOLERANCE = 1.0f;

        for (CrownBlock crown : alignedCrowns) {
            if (crown.getDepthAlongHeading() > minDepth + LAYER_TOLERANCE) {
                continue; // позади переднего слоя — пропустить
            }
            // Шаг 2: позиция на шаг вперёд вдоль heading (узкая линия по позициям корон).
            BlockPos p = crown.getPosition();
            BlockPos ahead = new BlockPos(
                p.getX() + (int) Math.round(heading.x),
                p.getY() + (int) Math.round(heading.y),
                p.getZ() + (int) Math.round(heading.z)
            );
            // Шаг 3-4: прочитать блок мира, собрать (позиция, состояние).
            BlockState state = blockReader.readBlock(subLevel, ahead);
            if (state != null) {
                blocksAhead.add(new BlockStateAtPos(ahead, state));
            }
        }
        return blocksAhead; // Шаг 5. Bedrock-гейт сам найдёт hardness<0.
    }

    // ── Вспомогательные типы ─────────────────────────────────────────────────────

    /**
     * Провайдер скорости подшипника для инъекции в тесты.
     */
    @FunctionalInterface
    interface IBearingSpeedProvider {
        float getSpeed(Object bearing);
    }

    /**
     * Интерфейс чтения мирового блока из SubLevel — изолирует неподтверждённый API.
     * TODO[API-CHECK]: подтвердить subLevel.getLevel().getBlockState(worldPos) в игре.
     */
    @FunctionalInterface
    interface IWorldBlockReader {
        BlockState readBlock(Object subLevel, BlockPos worldPos);
    }

    /** Production-читалка мировых блоков (placeholder до подтверждения API). */
    private static final IWorldBlockReader DEFAULT_WORLD_BLOCK_READER =
        (subLevel, worldPos) -> null; // TODO[API-CHECK]

    /** Пара (позиция, состояние) — замена Tuple из спецификации. */
    public record BlockStateAtPos(BlockPos pos, BlockState state) {}
}
