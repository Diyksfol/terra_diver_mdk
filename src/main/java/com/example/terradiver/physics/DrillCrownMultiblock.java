package com.example.terradiver.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/*
 * Сборка/разборка мультиблок-короны — общая логика мастера и дочерних ячеек. Разборка защищена
 * от рекурсии флагом DISSOLVING. Размер и направление передаются ЯВНО (мастер при сломе уже
 * заменён на воздух, прочитать его из мира нельзя — берём из старого BlockState). См.
 * DrillCrownStructure (раскладка) и DrillCrownPartBlock.
 */
public final class DrillCrownMultiblock {

    private DrillCrownMultiblock() {}

    private static final ThreadLocal<Boolean> DISSOLVING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /* Мастер-блок короны раскрывает размер и направление — для расчёта ячеек структуры. */
    public interface Master {
        String crownSize();                       // "3x3", "11x11", ...
        Direction crownFacing(BlockState state);  // направление из blockstate
    }

    public static boolean isDissolving() {
        return DISSOLVING.get();
    }

    /*
     * Снести всю структуру: размер и направление заданы явно (надёжно и при сломе мастера, когда
     * его уже нет в мире). Идемпотентно: флаг DISSOLVING гасит повторный вход во время разборки.
     */
    public static void dissolve(Level level, BlockPos masterPos, String size, Direction facing) {
        if (DISSOLVING.get()) {
            return;
        }
        DISSOLVING.set(Boolean.TRUE);
        try {
            for (BlockPos cell : DrillCrownStructure.worldCells(size, facing, masterPos)) {
                if (isCrownCell(level.getBlockState(cell))) {
                    level.removeBlock(cell, false);
                }
            }
        } finally {
            DISSOLVING.set(Boolean.FALSE);
        }
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