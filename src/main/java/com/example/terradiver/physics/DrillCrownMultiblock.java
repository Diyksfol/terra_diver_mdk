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

/*
 * Сборка/разборка мультиблок-короны — общая логика мастера и дочерних ячеек. Разборка защищена
 * от рекурсии флагом DISSOLVING. Размер и направление передаются ЯВНО (мастер при сломе уже
 * заменён на воздух, прочитать его из мира нельзя — берём из старого BlockState). См.
 * DrillCrownStructure (раскладка) и DrillCrownPartBlock.
 */
public final class DrillCrownMultiblock {

    private DrillCrownMultiblock() {}

    private static final ThreadLocal<Boolean> DISSOLVING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    // Сколько частиц разрушения на одну ячейку короны. Мало — чтобы большие короны (до 190 ячеек)
    // не выбрасывали сотни частиц разом. Крутить тут.
    private static final int PARTICLES_PER_CELL = 2;

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
    public static void dissolve(Level level, BlockPos masterPos, String size, Direction facing, BlockState particleState) {
        if (DISSOLVING.get()) {
            return;
        }
        DISSOLVING.set(Boolean.TRUE);
        try {
            // Частицы разрушения выбрасываем по ВСЕЙ короне, а не только по сломанной ячейке.
            // По PARTICLES_PER_CELL штук на ячейку — немного, чтобы не грузить клиент на больших коронах.
            ServerLevel server = level instanceof ServerLevel sl ? sl : null;
            BlockParticleOption particle = server != null && particleState != null
                    ? new BlockParticleOption(ParticleTypes.BLOCK, particleState) : null;
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
                }
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