package com.example.terradiver.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/*
 * Мастер-блок мультиблок-короны: несёт направление (FACING), размер и логику структуры. Рендерит
 * полную OBJ-модель (дочерние ячейки невидимы); коллизия мастер-ячейки — полный куб (NxN) или
 * спецформа острия (1x1). Реализует DrillCrownMultiblock.Master. См. DrillCrownStructure.
 */
public class DrillCrownBlock extends Block implements DrillCrownMultiblock.Master {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    // Спецформа острия 1x1 (модельная ориентация, глубина +Y): низ полный + центр-полублок сверху.
    // Острие 1x1: один куб 8x8x8 px по центру в нижней части блока (модельная ориентация).
    private static final double[][] TIP_BASE = {{0.25, 0.0, 0.25, 0.75, 0.5, 0.75}};
    private static final double[][] FULL_CUBE = {{0.0, 0.0, 0.0, 1.0, 1.0, 1.0}};

    private final String size;
    private final Map<Direction, VoxelShape> shapes;

    public DrillCrownBlock(Properties properties, String size) {
        super(properties);
        this.size = size;
        double[][] base = "1x1".equals(size) ? TIP_BASE : FULL_CUBE; // NxN мастер-ячейка — полный куб
        EnumMap<Direction, VoxelShape> m = new EnumMap<>(Direction.class);
        for (Direction d : Direction.values()) {
            m.put(d, CrownShapes.build(base, d));
        }
        this.shapes = m;
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = context.isSecondaryUseActive() ? face.getOpposite() : face;
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Сломали мастера в выживании → выронить один предмет короны (структура снесётся в onRemove).
        if (!level.isClientSide && !player.getAbilities().instabuild) {
            DrillCrownMultiblock.dropCrownItem(level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapes.get(state.getValue(FACING));
    }

    // ── DrillCrownMultiblock.Master ──
    @Override
    public String crownSize() {
        return size;
    }

    @Override
    public Direction crownFacing(BlockState state) {
        return state.getValue(FACING);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        // Сломали мастера → снести всю структуру (флаг DISSOLVING гасит рекурсию).
        if (!state.is(newState.getBlock()) && !DrillCrownMultiblock.isDissolving()) {
            // Размер и направление берём из СВОЕГО старого состояния — мастер в мире уже заменён.
            DrillCrownMultiblock.dissolve(level, pos, crownSize(), state.getValue(FACING));
        }
        super.onRemove(state, level, pos, newState, moved);
    }

}