package com.example.terradiver.physics;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/*
 * Построение VoxelShape ячейки короны из боксов (локальные [0,1]) с поворотом по FACING вокруг
 * центра блока (0.5,0.5,0.5). Конвенция поворота — та же, что DrillCrownStructure.rotate (+Y → FACING).
 */
public final class CrownShapes {

    private CrownShapes() {}

    public static VoxelShape build(double[][] boxes, Direction facing) {
        VoxelShape acc = Shapes.empty();
        for (double[] b : boxes) {
            double[] a = rot(b[0], b[1], b[2], facing);
            double[] c = rot(b[3], b[4], b[5], facing);
            acc = Shapes.or(acc, Shapes.box(
                Math.min(a[0], c[0]), Math.min(a[1], c[1]), Math.min(a[2], c[2]),
                Math.max(a[0], c[0]), Math.max(a[1], c[1]), Math.max(a[2], c[2])));
        }
        return acc;
    }

    private static double[] rot(double x, double y, double z, Direction f) {
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
