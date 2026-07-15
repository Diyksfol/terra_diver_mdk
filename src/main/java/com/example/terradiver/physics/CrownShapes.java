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
        return build(boxes, facing, 0);
    }

    /*
     * roll — крен короны вокруг оси бура (0..3, шаг 90°), см. DrillCrownBlock.ROLL.
     * Применяем ТУ ЖЕ трансформацию, что и blockstate модели: сперва поворот по FACING (аналог "x"),
     * затем поворот вокруг МИРОВОЙ оси Y на тот же угол, что стоит в blockstate ("y" = (4-roll)%4*90).
     * Так коллизия гарантированно совпадает с моделью при любом крене. Крен имеет смысл только для
     * ВЕРТИКАЛЬНОГО бура: у горизонтального поворот вокруг Y меняет FACING, а не крен, и в blockstate
     * для него доп. "y" не добавляется — поэтому здесь тоже игнорируем.
     */
    public static VoxelShape build(double[][] boxes, Direction facing, int roll) {
        int quarters = facing.getAxis() == Direction.Axis.Y ? (4 - (roll & 3)) & 3 : 0;
        VoxelShape acc = Shapes.empty();
        for (double[] b : boxes) {
            double[] a = rotY(rot(b[0], b[1], b[2], facing), quarters);
            double[] c = rotY(rot(b[3], b[4], b[5], facing), quarters);
            acc = Shapes.or(acc, Shapes.box(
                Math.min(a[0], c[0]), Math.min(a[1], c[1]), Math.min(a[2], c[2]),
                Math.max(a[0], c[0]), Math.max(a[1], c[1]), Math.max(a[2], c[2])));
        }
        // Слить копланарные боксы: убирает внутренние грани между восьмушками, за которые
        // иначе цепляется/выталкивает игрока при ходьбе по телу короны (см. точка 5).
        return acc.optimize();
    }

    // Поворот вокруг мировой оси Y на quarters×90° ПО ЧАСОВОЙ, если смотреть сверху — та же
    // конвенция, что у blockstate "y" (восток→юг). Точка задана в мировых [0,1] координатах блока.
    private static double[] rotY(double[] p, int quarters) {
        double x = p[0], y = p[1], z = p[2];
        for (int i = 0; i < quarters; i++) {
            double cx = x - 0.5, cz = z - 0.5;
            x = -cz + 0.5;
            z = cx + 0.5;
        }
        return new double[]{ x, y, z };
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