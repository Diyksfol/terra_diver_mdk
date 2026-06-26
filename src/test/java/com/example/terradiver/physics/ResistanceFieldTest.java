package com.example.terradiver.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Юнит-тесты поля сопротивления (TD_01, v1.2). См. TD_06 v1.2. Чистая геометрия + скан через
 * фейк-предикат, без Minecraft-runtime.
 */
@DisplayName("ResistanceField — v1.2")
class ResistanceFieldTest {

    private static final Vec3 CENTER = Vec3.ZERO;
    private static final float EPS = 1e-6f;

    private static void assertVec(Vec3 exp, Vec3 got) {
        assertEquals(exp.x, got.x, EPS);
        assertEquals(exp.y, got.y, EPS);
        assertEquals(exp.z, got.z, EPS);
    }

    private static Predicate<BlockPos> rock(BlockPos... ps) {
        Set<BlockPos> set = Set.of(ps);
        return set::contains;
    }

    // ── compute_resistance_direction ──

    @Nested @DisplayName("compute_resistance_direction")
    class Direction {
        private Vec3 dir(List<BlockPos> c) { return ResistanceField.compute_resistance_direction(CENTER, c); }

        @Test @DisplayName("порода спереди (+z) → назад (−z)")
        void ahead() { assertVec(new Vec3(0, 0, -1), dir(List.of(new BlockPos(0, 0, 2), new BlockPos(0, 0, 3)))); }

        @Test @DisplayName("порода снизу → вверх (восьмёрка)")
        void below() { assertVec(new Vec3(0, 1, 0), dir(List.of(new BlockPos(0, -2, 0), new BlockPos(0, -3, 0)))); }

        @Test @DisplayName("порода сбоку (+x) → вбок (−x)")
        void side() { assertVec(new Vec3(-1, 0, 0), dir(List.of(new BlockPos(2, 0, 0), new BlockPos(3, 0, 0)))); }

        @Test @DisplayName("пусто/null → ZERO")
        void none() {
            assertVec(Vec3.ZERO, dir(List.of()));
            assertVec(Vec3.ZERO, ResistanceField.compute_resistance_direction(CENTER, null));
        }

        @Test @DisplayName("симметрия → ZERO")
        void symmetric() {
            assertVec(Vec3.ZERO, dir(List.of(new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
                new BlockPos(0, 1, 0), new BlockPos(0, -1, 0))));
        }
    }

    // ── crownCenter ──

    @Nested @DisplayName("crownCenter")
    class Center {
        @Test @DisplayName("среднее двух позиций")
        void avg() { assertVec(new Vec3(1, 0, 0), ResistanceField.crownCenter(List.of(new BlockPos(0, 0, 0), new BlockPos(2, 0, 0)))); }

        @Test @DisplayName("пусто → ZERO")
        void empty() { assertVec(Vec3.ZERO, ResistanceField.crownCenter(List.of())); }
    }

    // ── scanContactSolids (ядро) ──

    @Nested @DisplayName("scanContactSolids")
    class Scan {
        @Test @DisplayName("находит породу-соседей короны (спереди + сбоку)")
        void findsNeighbours() {
            BlockPos front = new BlockPos(0, 0, 1), sideP = new BlockPos(1, 0, 0);
            List<BlockPos> got = ResistanceField.scanContactSolids(List.of(BlockPos.ZERO), rock(front, sideP));
            assertEquals(Set.of(front, sideP), Set.copyOf(got));
        }

        @Test @DisplayName("сосед-не-порода исключён")
        void excludesNonRock() {
            List<BlockPos> got = ResistanceField.scanContactSolids(List.of(BlockPos.ZERO), rock(new BlockPos(0, 0, 1)));
            assertEquals(List.of(new BlockPos(0, 0, 1)), got);
        }

        @Test @DisplayName("общий сосед двух корон — без дублей")
        void dedup() {
            List<BlockPos> got = ResistanceField.scanContactSolids(
                List.of(BlockPos.ZERO, new BlockPos(2, 0, 0)), rock(new BlockPos(1, 0, 0)));
            assertEquals(List.of(new BlockPos(1, 0, 0)), got);
        }

        @Test @DisplayName("нет породы / нет корон → пусто")
        void empties() {
            assertTrue(ResistanceField.scanContactSolids(List.of(BlockPos.ZERO), p -> false).isEmpty());
            assertTrue(ResistanceField.scanContactSolids(List.of(), p -> true).isEmpty());
        }
    }

    // ── интеграция ──

    @Test @DisplayName("восьмёрка: нижняя корона в породе → выталкивает ВВЕРХ")
    void eightShapeEjectsUp() {
        List<BlockPos> crowns = List.of(new BlockPos(0, 0, 0), new BlockPos(0, -3, 0)); // верх в воздухе, низ в породе
        Predicate<BlockPos> rock = rock(
            new BlockPos(0, -4, 0), new BlockPos(1, -3, 0), new BlockPos(-1, -3, 0),
            new BlockPos(0, -3, 1), new BlockPos(0, -3, -1));
        List<BlockPos> contacts = ResistanceField.scanContactSolids(crowns, rock);
        Vec3 dir = ResistanceField.compute_resistance_direction(ResistanceField.crownCenter(crowns), contacts);
        assertTrue(dir.y > 0.5, "доминирует выталкивание вверх, а не назад");
    }

    @Test @DisplayName("симметричная восьмёрка: оба конца в породе → стоит на месте (ZERO)")
    void symmetricEightStaysStill() {
        // Короны симметрично сверху и снизу относительно центра (0,0,0).
        List<BlockPos> crowns = List.of(new BlockPos(0, 3, 0), new BlockPos(0, -3, 0));
        // Порода симметрично окружает оба конца → силы уравновешиваются.
        Predicate<BlockPos> rock = rock(
            new BlockPos(0, 4, 0), new BlockPos(1, 3, 0), new BlockPos(-1, 3, 0),
            new BlockPos(0, 3, 1), new BlockPos(0, 3, -1),
            new BlockPos(0, -4, 0), new BlockPos(1, -3, 0), new BlockPos(-1, -3, 0),
            new BlockPos(0, -3, 1), new BlockPos(0, -3, -1));
        List<BlockPos> contacts = ResistanceField.scanContactSolids(crowns, rock);
        Vec3 dir = ResistanceField.compute_resistance_direction(ResistanceField.crownCenter(crowns), contacts);
        assertVec(Vec3.ZERO, dir);
    }
}