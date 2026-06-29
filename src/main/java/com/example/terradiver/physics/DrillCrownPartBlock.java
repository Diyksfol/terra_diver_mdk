package com.example.terradiver.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/*
 * Дочерняя ячейка мультиблок-короны: НЕВИДИМА (модель рендерит только мастер), но осязаема —
 * полный куб коллизии, поэтому по телу короны можно ходить и ломать любую ячейку. Ломание
 * перенаправляется мастеру (снос всей структуры). См. DrillCrownStructure / DrillCrownMultiblock.
 */
public class DrillCrownPartBlock extends Block implements EntityBlock {

    public DrillCrownPartBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE; // модель рисует мастер, дочерние невидимы
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.block(); // полный куб: ходимо и выделяемо
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DrillCrownPartBlockEntity(pos, state);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Сломали часть в выживании → выронить один предмет короны у мастера.
        if (!level.isClientSide && !player.getAbilities().instabuild
                && level.getBlockEntity(pos) instanceof DrillCrownPartBlockEntity be) {
            DrillCrownMultiblock.dropCrownItem(level, be.getMaster());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        // Сломали часть → снести всю структуру. Флаг DISSOLVING в helper гасит рекурсию,
        // когда разборка сама удаляет остальные ячейки.
        if (!state.is(newState.getBlock()) && !DrillCrownMultiblock.isDissolving()) {
            if (level.getBlockEntity(pos) instanceof DrillCrownPartBlockEntity be) {
                DrillCrownMultiblock.breakStructure(level, be.getMaster());
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}