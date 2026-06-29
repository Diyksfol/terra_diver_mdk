package com.example.terradiver.drilling;

import com.example.terradiver.physics.PhysicsUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/*
 * clear_blocks (TD_02) — направить дроп ограниченного числа блоков (по лимиту мощности),
 * реально сломанных движком Offroad, в буфер крепления; вернуть число обработанных блоков.
 * См. TD_06 v1.1, clear_blocks.
 *
 * Развязка: чистое ядро clearBlocks(...) гоняет всю логику цикла (лимит, фильтр добываемости,
 * заморозка буфера, подсчёт) и тестируется без Minecraft. Всё касание мира (добываемость, лут,
 * удаление блока) спрятано за сёмой MiningTarget; её production-реализация лезет в SubLevel —
 * это неподтверждённый API, помечен TODO[API-CHECK] и проверяется в игре.
 */
public final class BlockClearer {

    private BlockClearer() {}

    /*
     * Одна цель прочистки. Скрывает MC-доступ от ядра.
     *   isDiggable      — проходит ли блок is_diggable (Домен 0)
     *   breakAndDeposit — получить лут, положить в буфер, удалить блок В МИРЕ (всегда, даже если
     *                     дроп влез не весь); вернуть true, если весь дроп размещён (fully deposited)
     */
    public interface MiningTarget {
        boolean isDiggable();
        boolean breakAndDeposit();
    }

    /*
     * Чистое ядро. Обрабатывает цели по порядку, пока не упрётся в лимит мощности или в полный буфер.
     * См. TD_06 v1.1, clear_blocks, шаги 1-5.
     *
     * @param candidates       цели (из mining_candidates родного движка)
     * @param bufferHasSpace   "есть ли место в буфере" — опрашивается заново перед каждым блоком
     *                         (состояние буфера меняется по мере вставки дропа)
     * @param maxBlocksPerTick лимит мощности механизма за тик (из compute_max_blocks_per_tick)
     * @return processed_count — блоки, удалённые С ПОЛНОСТЬЮ размещённым дропом
     */
    public static int clearBlocks(List<MiningTarget> candidates,
                                  BooleanSupplier bufferHasSpace,
                                  int maxBlocksPerTick) {
        int processed = 0;
        if (candidates == null) {
            return 0;
        }
        for (MiningTarget t : candidates) {
            if (processed >= maxBlocksPerTick) {
                break; // лимит мощности достигнут — остальное движок добёрет в следующем тике
            }
            if (!t.isDiggable()) {
                continue; // непробиваемые/жидкости не трогаем (не входят в лимит)
            }
            if (!bufferHasSpace.getAsBoolean()) {
                break; // буфер полон → заморозка: блок НЕ ломаем, дроп на землю не роняем
            }
            // Блок ломается и удаляется ВНУТРИ breakAndDeposit независимо от fully (движок уже сломал).
            if (t.breakAndDeposit()) {
                processed++; // засчитываем только при полностью размещённом дропе
            }
            // fully == false: блок удалён, но не засчитан → недогруз перетечёт в pending_resistance.
        }
        return processed;
    }

    /*
     * Production-сигнатура из TD_06. Строит цели из mining_candidates и запускает ядро.
     * Доступ к миру (лут, удаление) — в WorldMiningTarget, TODO[API-CHECK].
     */
    public static int clear_blocks(List<PhysicsUtils.BlockStateAtPos> miningCandidates,
                                   Object subLevel,
                                   DrillingUtils.CrownBuffer buffer,
                                   int maxBlocksPerTick) {
        if (miningCandidates == null || miningCandidates.isEmpty()) {
            return 0;
        }
        List<MiningTarget> targets = new ArrayList<>(miningCandidates.size());
        for (PhysicsUtils.BlockStateAtPos c : miningCandidates) {
            targets.add(new WorldMiningTarget(subLevel, c.pos(), c.state(), buffer));
        }
        return clearBlocks(targets, () -> DrillingUtils.buffer_has_space(buffer), maxBlocksPerTick);
    }

    // ── MC-адаптер цели (проверяется в игре, не юнит-тестом) ─────────────────────

    private static final class WorldMiningTarget implements MiningTarget {
        private final Object subLevel;
        private final BlockPos pos;
        private final BlockState state;
        private final DrillingUtils.CrownBuffer buffer;

        WorldMiningTarget(Object subLevel, BlockPos pos, BlockState state, DrillingUtils.CrownBuffer buffer) {
            this.subLevel = subLevel;
            this.pos = pos;
            this.state = state;
            this.buffer = buffer;
        }

        @Override
        public boolean isDiggable() {
            return PhysicsUtils.is_diggable(state);
        }

        @Override
        public boolean breakAndDeposit() {
            List<ItemStack> drops = getDrops(subLevel, pos, state);
            boolean fully = DrillingUtils.deposit_to_inventory(drops, buffer);
            removeBlock(subLevel, pos); // удалить независимо от fully (TD_06: блок уже сломан движком)
            return fully;
        }
    }

    /*
     * Лут блока (loot table, как у ванильного слома).
     * TODO[API-CHECK]: генерация лута требует ServerLevel + LootParams из контекста SubLevel.
     * Сигнатура доступа к миру из SubLevel не подтверждена (TD_06). Заглушка — пустой дроп.
     */
    private static List<ItemStack> getDrops(Object subLevel, BlockPos pos, BlockState state) {
        // TODO[API-CHECK]
        return List.of();
    }

    /*
     * Удалить блок в мире (заменить на воздух) из контекста SubLevel.
     * TODO[API-CHECK]: subLevel.getLevel().setBlock(worldPos, AIR, ...) — доступ не подтверждён (TD_06).
     */
    private static void removeBlock(Object subLevel, BlockPos pos) {
        // TODO[API-CHECK]
    }
}
