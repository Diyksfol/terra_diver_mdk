package com.example.terradiver.drilling;

import com.example.terradiver.physics.PhysicsUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import com.example.terradiver.physics.CrownBlock;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/*
 * Чистые функции буровой системы (TD_02). Спецификация — TD_06 v1.0, TD_02.
 * MC-привязка инвентаря изолирована в сёме CrownBuffer (production-адаптер проверяется в игре).
 */
public final class DrillingUtils {

    private DrillingUtils() {}

    /*
     * compute_max_blocks_per_tick — лимит мощности для clear_blocks с лагом на тик.
     * Назначение, обоснование лага и граничные случаи — см. TD_06 v1.0, compute_max_blocks_per_tick.
     */
    public static int compute_max_blocks_per_tick(float previousRate, float effectiveSpeed,
                                                  float avgMaterialFactor, int minBlocksPerTick) {
        float rawCapacity = effectiveSpeed * avgMaterialFactor;          // мгновенная мощность
        float laggedCapacity = (rawCapacity + previousRate) / 2f;        // смешение с темпом прошлого тика
        int blocks = (int) Math.ceil(laggedCapacity);                    // округление вверх
        return Math.max(blocks, minBlocksPerTick);                       // пол MIN_BLOCKS_PER_TICK
    }

    /*
     * decay_pending_resistance — спад накопленного давления на один шаг (тики вне Dive Mode).
     * См. TD_06 v1.0, decay_pending_resistance.
     */
    public static float decay_pending_resistance(float pendingResistance, float pendingDecay) {
        return Math.max(0f, pendingResistance - pendingDecay);
    }

    /*
     * is_bedrock_blocking — стоит ли непробиваемый блок (hardness<0) на линии продвижения.
     * См. TD_06 v1.0, is_bedrock_blocking. Твёрдость берётся как block.defaultDestroyTime()
     * (та же конвенция, что is_diggable; Bedrock = -1), не через getDestroySpeed(level,pos).
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

    /*
     * buffer_has_space — грубая предпроверка: есть ли в буфере хоть какое-то место.
     * См. TD_06 v1.0, buffer_has_space. Точную проверку даёт deposit_to_inventory по факту вставки.
     */
    public static boolean buffer_has_space(CrownBuffer buffer) {
        if (buffer == null) {
            return false; // нет буфера → морозим (безопасная сторона)
        }
        for (int i = 0; i < buffer.size(); i++) {
            if (buffer.slotAvailable(i)) {
                return true;
            }
        }
        return false;
    }

    /*
     * deposit_to_inventory — поместить дроп в буфер; False при переполнении в процессе.
     * См. TD_06 v1.0, deposit_to_inventory.
     *
     * Стекинг НЕ реимплементируется здесь — он делегирован реализации CrownBuffer (в production
     * это движок Minecraft через IItemHandler), что устойчивее к обновлениям. Эта функция —
     * только оркестрация: пройти дропы, поймать момент, когда остаток не влез. Реальная вставка
     * проверяется в игре (GameTest), не юнит-тестом; юнит покрывает лишь оркестрацию.
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
     * Сёма буфера крепления короны (16 слотов Андезит / 80 Прочное, TD_02). Production-адаптер
     * оборачивает инвентарь крепления (NeoForge IItemHandler) — TODO[API-CHECK]; в тестах — фейк/мок.
     * insert возвращает остаток, не влезший в буфер (EMPTY если всё влезло); стекинг — на реализации.
     */
    public interface CrownBuffer {
        int size();
        boolean slotAvailable(int slot);    // пусто ИЛИ неполный стак → есть место
        ItemStack insert(ItemStack stack);  // остаток, не влезший в буфер
    }

    /*
    * compute_crown_face_area — суммарная площадь переднего выровненного слоя корон.
    * См. TD_06 v1.0, compute_crown_face_area.
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
    * См. TD_06 v1.0, compute_avg_material_factor. Однородная корона даёт ровно свой фактор (инвариант).
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
    * update_pending_resistance — рост/спад "давления недогруза" по deficit = found - processed.
    * См. TD_06 v1.0, update_pending_resistance. Форма спада — постоянный темп (выбор реализации по спеке).
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
    * Передний выровненный слой: aligned-короны (get_aligned_crowns) с минимальной глубиной вдоль heading,
    * допуск 1 блок. Единый критерий "передний слой" для compute_crown_face_area и compute_avg_material_factor.
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
        final float LAYER_TOLERANCE = 1.0f;
        List<CrownBlock> front = new ArrayList<>();
        for (CrownBlock c : aligned) {
            if (c.getDepthAlongHeading() <= minDepth + LAYER_TOLERANCE) {
                front.add(c);
            }
        }
        return front;
    }
}
