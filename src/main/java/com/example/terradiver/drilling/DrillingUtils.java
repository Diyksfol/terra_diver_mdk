package com.example.terradiver.drilling;

import com.example.terradiver.physics.CrownBlock;
import com.example.terradiver.physics.PhysicsUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/*
 * Чистые функции буровой системы (TD_02). Спецификация — TD_06, TD_02.
 * MC-привязка инвентаря изолирована в сёме CrownBuffer (production-адаптер проверяется в игре).
 */
public final class DrillingUtils {

    private DrillingUtils() {}

    // ── Лимит мощности и сопротивление ──────────────────────────────────────────

    // compute_max_blocks_per_tick — лимит мощности для clear_blocks с лагом на тик. См. TD_06.
    public static int compute_max_blocks_per_tick(float previousRate, float effectiveSpeed,
                                                  float avgMaterialFactor, int minBlocksPerTick) {
        float rawCapacity = effectiveSpeed * avgMaterialFactor;
        float laggedCapacity = (rawCapacity + previousRate) / 2f;
        int blocks = (int) Math.ceil(laggedCapacity);
        return Math.max(blocks, minBlocksPerTick);
    }

    // decay_pending_resistance — спад накопленного давления на один шаг. См. TD_06.
    public static float decay_pending_resistance(float pendingResistance, float pendingDecay) {
        return Math.max(0f, pendingResistance - pendingDecay);
    }

    /*
     * update_pending_resistance — рост/спад «давления недогруза» по deficit = found - processed.
     * См. TD_06, update_pending_resistance. Форма спада — постоянный темп (выбор реализации по спеке).
     */
    public static float update_pending_resistance(float pendingResistance, int foundCount, int processedCount,
                                                  float pendingGrowth, float pendingDecay, float pendingMax) {
        int deficit = foundCount - processedCount;
        if (deficit > 0) {
            pendingResistance += deficit * pendingGrowth;
        } else {
            pendingResistance -= pendingDecay;
        }
        return Math.max(0f, Math.min(pendingResistance, pendingMax));
    }

    /*
     * is_bedrock_blocking — любой блок с hardness<0 на линии продвижения. См. TD_06.
     * Твёрдость — block.defaultDestroyTime() (как is_diggable); Bedrock = -1.
     */
    public static boolean is_bedrock_blocking(List<PhysicsUtils.BlockStateAtPos> blocksAhead) {
        if (blocksAhead == null || blocksAhead.isEmpty()) {
            return false;
        }
        for (PhysicsUtils.BlockStateAtPos b : blocksAhead) {
            BlockState s = b.state();
            if (s != null && s.getBlock().defaultDestroyTime() < 0f) {
                return true; // ранний выход на первом непробиваемом
            }
        }
        return false;
    }

    // ── Геометрия переднего слоя короны (TD_02) ─────────────────────────────────

    /*
     * compute_crown_face_area — суммарная площадь переднего выровненного слоя корон. См. TD_06.
     * Форма короны произвольна; передний слой = короны на минимальной глубине (см. frontAlignedLayer).
     */
    public static float compute_crown_face_area(List<CrownBlock> crownBlocks, Vec3 heading) {
        float area = 0f;
        for (CrownBlock c : frontAlignedLayer(crownBlocks, heading)) {
            area += c.getArea();
        }
        return area;
    }

    /*
     * compute_avg_material_factor — средневзвешенный по площади фактор материала переднего слоя.
     * См. TD_06. Однородная корона даёт ровно свой фактор (инвариант).
     */
    public static float compute_avg_material_factor(List<CrownBlock> crownBlocks, Vec3 heading,
                                                    float crownFaceArea) {
        List<CrownBlock> front = frontAlignedLayer(crownBlocks, heading);
        if (front.isEmpty() || crownFaceArea == 0f) {
            return 1.0f; // нейтральный фактор меди, не деление на ноль (TD_06)
        }
        float numerator = 0f;
        for (CrownBlock c : front) {
            numerator += c.getMaterialFactor() * c.getArea();
        }
        return numerator / crownFaceArea;
    }

    /*
     * Передний выровненный слой: aligned-короны (get_aligned_crowns) с минимальной depthAlongHeading.
     * Единый критерий «передний слой» для compute_crown_face_area и compute_avg_material_factor.
     *
     * v1.3: depthAlongHeading — это передний край КОЛЬЦА, общий для всех блоков кольца (см. TD_06 v1.3).
     * Поэтому блоки одного кольца имеют одинаковую глубину, а следующее кольцо отстоит минимум на
     * свою высоту (≥1 блок: остриё 1, диск 2). Допуск 0.5 (< минимального шага 1) берёт ровно переднее
     * кольцо и поглощает погрешность float, не захватывая соседнее кольцо. Высоту колец агрегат не знает.
     */
    private static List<CrownBlock> frontAlignedLayer(List<CrownBlock> crownBlocks, Vec3 heading) {
        List<CrownBlock> aligned = PhysicsUtils.get_aligned_crowns(crownBlocks, heading);
        if (aligned.isEmpty()) {
            return aligned;
        }
        float minDepth = Float.MAX_VALUE;
        for (CrownBlock c : aligned) {
            minDepth = Math.min(minDepth, c.getDepthAlongHeading());
        }
        final float LAYER_TOLERANCE = 0.5f; // < минимального шага между кольцами (1); только на float-погрешность
        List<CrownBlock> front = new ArrayList<>();
        for (CrownBlock c : aligned) {
            if (c.getDepthAlongHeading() <= minDepth + LAYER_TOLERANCE) {
                front.add(c);
            }
        }
        return front;
    }

    // ── Буфер крепления (сёма + предпроверка/вставка) ───────────────────────────

    /*
     * buffer_has_space — грубая предпроверка: есть ли в буфере хоть какое-то место. См. TD_06.
     */
    public static boolean buffer_has_space(CrownBuffer buffer) {
        if (buffer == null) {
            return false;
        }
        for (int i = 0; i < buffer.size(); i++) {
            if (buffer.slotAvailable(i)) {
                return true;
            }
        }
        return false;
    }

    /*
     * deposit_to_inventory — поместить дроп в буфер; False при переполнении в процессе. См. TD_06.
     * Стекинг делегирован реализации CrownBuffer (движку), здесь не дублируется. Оракул вставки — GameTest.
     */
    public static boolean deposit_to_inventory(List<ItemStack> drops, CrownBuffer buffer) {
        if (drops == null || drops.isEmpty()) {
            return true;
        }
        if (buffer == null) {
            return false;
        }
        for (ItemStack stack : drops) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ItemStack leftover = buffer.insert(stack);
            if (leftover != null && !leftover.isEmpty()) {
                return false; // место кончилось в середине
            }
        }
        return true;
    }

    /*
     * Сёма буфера крепления короны. Production-адаптер оборачивает инвентарь крепления (NeoForge
     * IItemHandler), TODO[API-CHECK]; в тестах — фейк/мок. insert возвращает остаток (EMPTY если влез).
     */
    public interface CrownBuffer {
        int size();
        boolean slotAvailable(int slot);    // пусто ИЛИ неполный стак → есть место
        ItemStack insert(ItemStack stack);  // остаток, не влезший в буфер
    }
}