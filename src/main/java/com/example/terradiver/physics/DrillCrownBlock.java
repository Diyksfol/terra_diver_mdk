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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
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
    private static final VoxelShape TIP_BASE = Shapes.or(
        Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0),
        Shapes.box(0.25, 0.5, 0.25, 0.75, 1.0, 0.75));

    private final String size;
    private final Map<Direction, VoxelShape> shapes;

    public DrillCrownBlock(Properties properties, String size) {
        super(properties);
        this.size = size;
        VoxelShape base = "1x1".equals(size) ? TIP_BASE : Shapes.block(); // NxN-ячейка — полный куб
        EnumMap<Direction, VoxelShape> m = new EnumMap<>(Direction.class);
        for (Direction d : Direction.values()) {
            m.put(d, rotateShape(base, d));
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
        // Бур смотрит туда, куда целится игрок (направление бурения). Тонкая настройка — в игре.
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection());
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
            DrillCrownMultiblock.breakStructure(level, pos); // pos = мастер
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    // ── Поворот формы по FACING (та же конвенция, что DrillCrownStructure.rotate) ──

    private static VoxelShape rotateShape(VoxelShape base, Direction facing) {
        if (facing == Direction.UP) {
            return base;
        }
        VoxelShape[] acc = { Shapes.empty() };
        base.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            double[] a = rotPoint(x1, y1, z1, facing);
            double[] b = rotPoint(x2, y2, z2, facing);
            acc[0] = Shapes.or(acc[0], Shapes.box(
                Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])));
        });
        return acc[0];
    }

    // Поворот точки вокруг центра блока (0.5,0.5,0.5); +Y модели → FACING.
    private static double[] rotPoint(double x, double y, double z, Direction f) {
        double cx = x - 0.5, cy = y - 0.5, cz = z - 0.5;
        double rx, ry, rz;
        switch (f) {
            case DOWN  -> { rx = cx;  ry = -cy; rz = -cz; }
            case NORTH -> { rx = cx;  ry = cz;  rz = -cy; }
            case SOUTH -> { rx = cx;  ry = -cz; rz = cy;  }
            case EAST  -> { rx = cy;  ry = -cx; rz = cz;  }
            case WEST  -> { rx = -cy; ry = cx;  rz = cz;  }
            default    -> { rx = cx;  ry = cy;  rz = cz;  }
        }
        return new double[]{ rx + 0.5, ry + 0.5, rz + 0.5 };
    }
}