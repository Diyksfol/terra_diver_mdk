package com.example.terradiver.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import com.example.terradiver.registry.BlockRegistry;

/*
 * Сборка/разборка мультиблок-короны — общая логика мастера и дочерних ячеек. Разборка защищена
 * от рекурсии флагом DISSOLVING. Размер и направление передаются ЯВНО (мастер при сломе уже
 * заменён на воздух, прочитать его из мира нельзя — берём из старого BlockState). См.
 * DrillCrownStructure (раскладка) и DrillCrownPartBlock.
 */
public final class DrillCrownMultiblock {

    private DrillCrownMultiblock() {}

    private static final ThreadLocal<Boolean> DISSOLVING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    // Частиц разрушения на ячейку у МУЛЬТИ-буров. Мало — чтобы большие короны (до 190 ячеек) не
    // выбрасывали сотни частиц разом. Бур 1x1 сюда не попадает — у него обычные ванильные частицы.
    private static final int PARTICLES_PER_CELL = 3;

    /* Мастер-блок короны раскрывает размер и направление — для расчёта ячеек структуры. */
    public interface Master {
        String crownSize();                       // "3x3", "11x11", ...
        Direction crownFacing(BlockState state);  // направление из blockstate
    }

    public static boolean isDissolving() {
        return DISSOLVING.get();
    }

    /*
     * Построить дочерние ячейки вокруг мастера. Вызывается при ЛЮБОЙ установке мастера — и игроком
     * (через предмет), и при разборке контраптии Create (мастер ставится обратно в мир через setBlock,
     * что дёргает onPlace). Раньше это жило только в предмете, поэтому после разборки контраптии
     * дочерние блоки не восстанавливались — теперь восстанавливаются. Ставим принудительно: место
     * под короной — её собственное (при установке предметом оно уже проверено на заменяемость).
     */
    public static void buildParts(Level level, BlockPos masterPos, String size, Direction facing) {
        if (level.isClientSide) {
            return;
        }
        Block partBlock = BlockRegistry.DRILL_CROWN_PART.get();
        for (int[] off : DrillCrownStructure.cells(size)) {
            int[] r = DrillCrownStructure.rotate(off, facing);
            BlockPos cell = masterPos.offset(r[0], r[1], r[2]);
            if (cell.equals(masterPos)) {
                continue;
            }
            level.setBlock(cell, partBlock.defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(cell) instanceof DrillCrownPartBlockEntity be) {
                be.setMaster(masterPos);
                be.setShapeData(size, off[0], off[1], off[2], facing);
            }
        }
    }

    /*
     * Снести всю структуру: размер и направление заданы явно (надёжно и при сломе мастера, когда
     * его уже нет в мире). Идемпотентно: флаг DISSOLVING гасит повторный вход во время разборки.
     */
    public static void dissolve(Level level, BlockPos masterPos, String size, Direction facing, BlockState particleState) {
        if (DISSOLVING.get()) {
            return;
        }
        DISSOLVING.set(Boolean.TRUE);
        try {
            // Частицы разрушения выбрасываем по ВСЕЙ короне, а не только по сломанной ячейке.
            // По PARTICLES_PER_CELL штук на ячейку — немного, чтобы не грузить клиент на больших коронах.
            // Бур 1x1 исключение: у него свои частицы НЕ рисуем — оставляем полные ванильные (см. addDestroyEffects).
            boolean single = "1x1".equals(size);
            ServerLevel server = level instanceof ServerLevel sl ? sl : null;
            BlockParticleOption particle = server != null && particleState != null && !single
                    ? new BlockParticleOption(ParticleTypes.BLOCK, particleState) : null;
            int removed = 0;
            for (BlockPos cell : DrillCrownStructure.worldCells(size, facing, masterPos)) {
                // Частицы — по КАЖДОЙ ячейке футпринта, включая уже сломанную (её ванильные частицы
                // подавлены, иначе она сыпала бы «не тем» цветом). Красим все по материалу мастера.
                if (particle != null) {
                    server.sendParticles(particle,
                            cell.getX() + 0.5D, cell.getY() + 0.5D, cell.getZ() + 0.5D,
                            PARTICLES_PER_CELL, 0.3D, 0.3D, 0.3D, 0.0D);
                }
                if (isCrownCell(level.getBlockState(cell))) {
                    level.removeBlock(cell, false);
                    removed++;
                }
            }
            if (!level.isClientSide) {
                org.slf4j.LoggerFactory.getLogger("terra_diver-heal").info(
                        "[TD-break] dissolve @ {} facing {} size {}: снесено {} ячеек", masterPos, facing, size, removed);
            }
        } finally {
            DISSOLVING.set(Boolean.FALSE);
        }
    }

    // Совместимость: старый вызов без состояния для частиц (без частиц по всей короне).
    public static void dissolve(Level level, BlockPos masterPos, String size, Direction facing) {
        dissolve(level, masterPos, size, facing, null);
    }

    /* Выронить один предмет короны у мастера (предмет = тот же блок-мастер: размер+материал). */
    public static void dropCrownItem(Level level, BlockPos masterPos) {
        BlockState ms = level.getBlockState(masterPos);
        if (ms.getBlock() instanceof Master) {
            Block.popResource(level, masterPos, new ItemStack(ms.getBlock()));
        }
    }

    private static boolean isCrownCell(BlockState state) {
        Block b = state.getBlock();
        return b instanceof Master || b instanceof DrillCrownPartBlock;
    }
}