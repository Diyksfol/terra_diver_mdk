package com.example.terradiver.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/*
 * Сборка/разборка мультиблок-короны — общая логика мастера и дочерних ячеек. Разборка
 * идемпотентна и защищена от рекурсии флагом DISSOLVING: ломание ячейки во время разборки
 * не запускает разборку заново. См. DrillCrownStructure (раскладка) и DrillCrownPartBlock.
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
     * Снести всю структуру по позиции мастера. Безопасно звать из onRemove любой ячейки.
     * Дроп предмета пока не делается (removeBlock без дропа) — экономику добавим отдельно.
     */
    public static void breakStructure(Level level, BlockPos masterPos) {
        if (DISSOLVING.get()) {
            return;
        }
        BlockState masterState = level.getBlockState(masterPos);
        if (!(masterState.getBlock() instanceof Master master)) {
            return; // мастера уже нет, или это не корона
        }
        DISSOLVING.set(Boolean.TRUE);
        try {
            List<BlockPos> cells = DrillCrownStructure.worldCells(
                master.crownSize(), master.crownFacing(masterState), masterPos);
            for (BlockPos cell : cells) {
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