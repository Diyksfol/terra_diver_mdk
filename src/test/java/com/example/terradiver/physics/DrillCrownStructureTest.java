package com.example.terradiver.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Юнит-тесты раскладки мультиблок-короны. Чистая математика footprint + поворота, без Minecraft-runtime.
 */
@DisplayName("DrillCrownStructure — footprint и поворот")
class DrillCrownStructureTest {

    @Test @DisplayName("число ячеек тела по размерам")
    void cellCounts() {
        assertEquals(1, DrillCrownStructure.cells("1x1").length);
        assertEquals(10, DrillCrownStructure.cells("3x3").length);
        assertEquals(30, DrillCrownStructure.cells("5x5").length);
        assertEquals(194, DrillCrownStructure.cells("11x11").length);
    }

    @Test @DisplayName("неизвестный размер → исключение")
    void unknownSize() {
        assertThrows(IllegalArgumentException.class, () -> DrillCrownStructure.cells("13x13"));
    }

    @Test @DisplayName("ось глубины +Y отображается на FACING")
    void depthAxisMapsToFacing() {
        int[] depth = {0, 1, 0}; // один шаг по глубине в модельном пространстве
        assertArrayEquals(new int[]{0, 1, 0},  DrillCrownStructure.rotate(depth, Direction.UP));
        assertArrayEquals(new int[]{0, -1, 0}, DrillCrownStructure.rotate(depth, Direction.DOWN));
        assertArrayEquals(new int[]{0, 0, -1}, DrillCrownStructure.rotate(depth, Direction.NORTH));
        assertArrayEquals(new int[]{0, 0, 1},  DrillCrownStructure.rotate(depth, Direction.SOUTH));
        assertArrayEquals(new int[]{1, 0, 0},  DrillCrownStructure.rotate(depth, Direction.EAST));
        assertArrayEquals(new int[]{-1, 0, 0}, DrillCrownStructure.rotate(depth, Direction.WEST));
    }

    @Test @DisplayName("UP — тождественный поворот")
    void upIdentity() {
        assertArrayEquals(new int[]{2, 1, -3}, DrillCrownStructure.rotate(new int[]{2, 1, -3}, Direction.UP));
    }

    @Test @DisplayName("поворот — биекция: ячейки не схлопываются в одну для любой грани")
    void rotationBijective() {
        for (Direction f : Direction.values()) {
            Set<String> seen = new HashSet<>();
            for (int[] off : DrillCrownStructure.cells("11x11")) {
                int[] r = DrillCrownStructure.rotate(off, f);
                assertTrue(seen.add(r[0] + "," + r[1] + "," + r[2]),
                    "коллизия ячеек при " + f);
            }
            assertEquals(194, seen.size());
        }
    }

    @Test @DisplayName("worldCells: первая — мастер (смещение 0,0,0)")
    void masterIsFirst() {
        BlockPos master = new BlockPos(10, 64, -5);
        List<BlockPos> cells = DrillCrownStructure.worldCells("3x3", Direction.UP, master);
        assertEquals(10, cells.size());
        assertTrue(cells.contains(master), "мастер входит в структуру");
        // смещение (0,0,0) присутствует → сам master в списке
    }

    @Test @DisplayName("worldCells: горизонтальная постановка уводит глубину вбок, не вверх")
    void horizontalPlacement() {
        BlockPos master = new BlockPos(0, 64, 0);
        List<BlockPos> cells = DrillCrownStructure.worldCells("3x3", Direction.EAST, master);
        // у диска 2 слоя по глубине → должны быть ячейки и на x=0, и на x=1 (глубина вдоль EAST=+X)
        boolean hasDepth0 = cells.stream().anyMatch(p -> p.getX() == 0);
        boolean hasDepth1 = cells.stream().anyMatch(p -> p.getX() == 1);
        assertTrue(hasDepth0 && hasDepth1, "глубина диска идёт вдоль +X при FACING=EAST");
        // и не уходит по Y за пределы одного уровня сечения (сечение в плоскости Y-Z при EAST)
    }
}