package com.example.terradiver.pressure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Юнит-тесты для HullCache: compute_exterior_surface, find_valid_girder_lines (чистые ядра)
 * и invalidate_hull_cache (оркестратор). Спецификация — TD_06 v1.0, TD_03.
 *
 * Без Minecraft-runtime: только BlockPos, Direction.Axis и коллекции. Никакого Bootstrap,
 * реестров или реальных BlockState — MC-специфика вынесена в адаптеры.
 */
@DisplayName("HullCache — exterior_surface + girder_lines + invalidate")
class HullCacheTest {

    private HullCache cache;
    private static final float SUPPORT_STEP = 0.2f;
    private static final int RAY = 64;

    @BeforeEach
    void setUp() {
        cache = new HullCache();
    }

    private static Set<BlockPos> solids(BlockPos... positions) {
        return new HashSet<>(List.of(positions));
    }

    private static Set<BlockPos> filledBox(int x0, int y0, int z0, int x1, int y1, int z1) {
        Set<BlockPos> s = new HashSet<>();
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++)
                    s.add(new BlockPos(x, y, z));
        return s;
    }

    /** Цепочка балок вдоль X на одной линии (y,z), с заданной блокстейт-осью. */
    private static Map<BlockPos, Direction.Axis> girdersAlongX(int y, int z, Direction.Axis axis, int... xs) {
        Map<BlockPos, Direction.Axis> g = new HashMap<>();
        for (int x : xs) g.put(new BlockPos(x, y, z), axis);
        return g;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  compute_exterior_surface
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("compute_exterior_surface")
    class ExteriorSurface {

        @Test @DisplayName("один блок → входит в surface")
        void singleBlock() {
            BlockPos p = new BlockPos(0, 0, 0);
            assertEquals(Set.of(p), HullCache.computeExteriorSurface(solids(p)));
        }

        @Test @DisplayName("полоса 3x1x3 → все 9 блоков")
        void flatStrip() {
            Set<BlockPos> hull = filledBox(0, 0, 0, 2, 0, 2);
            assertEquals(hull, HullCache.computeExteriorSurface(hull));
        }

        @Test @DisplayName("сплошной куб 3³ → 26 (центр исключён)")
        void solidCube() {
            Set<BlockPos> surface = HullCache.computeExteriorSurface(filledBox(0, 0, 0, 2, 2, 2));
            assertEquals(26, surface.size());
            assertFalse(surface.contains(new BlockPos(1, 1, 1)));
        }

        @Test @DisplayName("куб 5³ → внутренний блок исключён, оболочка 98")
        void internalCavity() {
            Set<BlockPos> surface = HullCache.computeExteriorSurface(filledBox(0, 0, 0, 4, 4, 4));
            assertFalse(surface.contains(new BlockPos(2, 2, 2)));
            assertEquals(98, surface.size());
        }

        @Test @DisplayName("две изолированные структуры → обе в surface")
        void twoIsolated() {
            Set<BlockPos> surface = HullCache.computeExteriorSurface(
                solids(new BlockPos(0, 0, 0), new BlockPos(10, 0, 0)));
            assertEquals(2, surface.size());
        }

        @Test @DisplayName("пустой/null → пустой surface")
        void empty() {
            assertTrue(HullCache.computeExteriorSurface(Set.of()).isEmpty());
            assertTrue(HullCache.computeExteriorSurface(null).isEmpty());
        }

        @Test @DisplayName("детерминированность")
        void deterministic() {
            Set<BlockPos> hull = filledBox(0, 0, 0, 2, 2, 2);
            assertEquals(HullCache.computeExteriorSurface(hull), HullCache.computeExteriorSurface(hull));
        }

        @Test @DisplayName("результат immutable")
        void immutable() {
            Set<BlockPos> surface = HullCache.computeExteriorSurface(solids(new BlockPos(0, 0, 0)));
            assertThrows(UnsupportedOperationException.class, () -> surface.add(new BlockPos(5, 5, 5)));
        }

        @Test @DisplayName("отрицательные координаты")
        void negativeCoords() {
            Set<BlockPos> surface = HullCache.computeExteriorSurface(filledBox(-3, -3, -3, -1, -1, -1));
            assertEquals(26, surface.size());
            assertFalse(surface.contains(new BlockPos(-2, -2, -2)));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  find_valid_girder_lines
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("find_valid_girder_lines")
    class GirderLines {

        @Test @DisplayName("валидная распорка между двумя стенами по X → 1 линия")
        void validBrace() {
            Set<BlockPos> solid = solids(new BlockPos(-1, 0, 0), new BlockPos(4, 0, 0));
            Set<BlockPos> voids = HullCache.computeExteriorVoid(solid);
            var g = girdersAlongX(0, 0, Direction.Axis.X, 0, 1, 2, 3);

            List<GirderLine> lines = HullCache.findValidGirderLines(g, solid, voids, RAY);
            assertEquals(1, lines.size());
            GirderLine l = lines.get(0);
            assertEquals(Direction.Axis.X, l.axis());
            assertEquals(new BlockPos(0, 0, 0), l.anchorA());
            assertEquals(new BlockPos(3, 0, 0), l.anchorB());
        }

        @Test @DisplayName("цепочка длины 1 → 0 линий")
        void lengthOne() {
            Set<BlockPos> solid = solids(new BlockPos(-1, 0, 0), new BlockPos(1, 0, 0));
            var g = girdersAlongX(0, 0, Direction.Axis.X, 0);
            assertTrue(HullCache.findValidGirderLines(g, solid, HullCache.computeExteriorVoid(solid), RAY).isEmpty());
        }

        @Test @DisplayName("торец в толстую стену (за W ещё блок) → 0 линий")
        void thickWall() {
            Set<BlockPos> solid = solids(new BlockPos(-1, 0, 0),
                new BlockPos(3, 0, 0), new BlockPos(4, 0, 0), new BlockPos(5, 0, 0));
            var g = girdersAlongX(0, 0, Direction.Axis.X, 0, 1);
            assertTrue(HullCache.findValidGirderLines(g, solid, HullCache.computeExteriorVoid(solid), RAY).isEmpty());
        }

        @Test @DisplayName("один торец без стены → 0 линий (нужны оба)")
        void oneEndOnly() {
            Set<BlockPos> solid = solids(new BlockPos(4, 0, 0)); // стена только справа
            var g = girdersAlongX(0, 0, Direction.Axis.X, 0, 1, 2, 3);
            assertTrue(HullCache.findValidGirderLines(g, solid, HullCache.computeExteriorVoid(solid), RAY).isEmpty());
        }

        @Test @DisplayName("две параллельные линии → 2 линии")
        void twoParallel() {
            Set<BlockPos> solid = solids(
                new BlockPos(-1, 0, 0), new BlockPos(4, 0, 0),
                new BlockPos(-1, 0, 2), new BlockPos(4, 0, 2));
            Map<BlockPos, Direction.Axis> g = new HashMap<>();
            g.putAll(girdersAlongX(0, 0, Direction.Axis.X, 0, 1, 2, 3));
            g.putAll(girdersAlongX(0, 2, Direction.Axis.X, 0, 1, 2, 3));
            assertEquals(2, HullCache.findValidGirderLines(g, solid, HullCache.computeExteriorVoid(solid), RAY).size());
        }

        @Test @DisplayName("блокстейт-ось ≠ геометрия ряда → 0 линий")
        void axisMismatch() {
            // Балки в ряд по X, но ось=Y → по Y каждая одиночка → цепочки длины 1.
            Set<BlockPos> solid = solids(new BlockPos(-1, 0, 0), new BlockPos(4, 0, 0));
            var g = girdersAlongX(0, 0, Direction.Axis.Y, 0, 1, 2, 3);
            assertTrue(HullCache.findValidGirderLines(g, solid, HullCache.computeExteriorVoid(solid), RAY).isEmpty());
        }

        @Test @DisplayName("неполная перегородка прозрачна → 1 линия")
        void partialPartitionTransparent() {
            // Перегородки в x=2 НЕТ в solids (неполный блок) → луч проходит сквозь до стен.
            Set<BlockPos> solid = solids(new BlockPos(-1, 0, 0), new BlockPos(6, 0, 0));
            var g = girdersAlongX(0, 0, Direction.Axis.X, 0, 1, 2, 3, 4, 5);
            assertEquals(1, HullCache.findValidGirderLines(g, solid, HullCache.computeExteriorVoid(solid), RAY).size());
        }

        @Test @DisplayName("закрытый корпус: балка кабина→внешняя стена → 1 линия")
        void cabinToOuterWall() {
            // Полая оболочка 5³; балка внутри по X упирается в стены оболочки изнутри.
            Set<BlockPos> shell = new HashSet<>();
            for (int x = 0; x < 5; x++)
                for (int y = 0; y < 5; y++)
                    for (int z = 0; z < 5; z++)
                        if (x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4)
                            shell.add(new BlockPos(x, y, z));
            var g = girdersAlongX(2, 2, Direction.Axis.X, 1, 2, 3);
            assertEquals(1, HullCache.findValidGirderLines(g, shell, HullCache.computeExteriorVoid(shell), RAY).size());
        }

        @Test @DisplayName("внутренняя перегородка между комнатами → 0 линий")
        void internalPartitionInvalid() {
            // Корпус 7×5×5, внутри полный-куб стена x=3 делит на две комнаты.
            Set<BlockPos> hull = new HashSet<>();
            for (int x = 0; x < 7; x++)
                for (int y = 0; y < 5; y++)
                    for (int z = 0; z < 5; z++)
                        if (x == 0 || x == 6 || y == 0 || y == 4 || z == 0 || z == 4)
                            hull.add(new BlockPos(x, y, z));
            for (int y = 1; y < 4; y++)
                for (int z = 1; z < 4; z++)
                    hull.add(new BlockPos(3, y, z)); // внутренняя перегородка
            // Балка в левой комнате, торец B к перегородке x=3; за ней — ВНУТРЕННЯЯ пустота.
            var g = girdersAlongX(2, 2, Direction.Axis.X, 1, 2);
            assertTrue(HullCache.findValidGirderLines(g, hull, HullCache.computeExteriorVoid(hull), RAY).isEmpty());
        }

        @Test @DisplayName("вертикальная распорка по Y → 1 линия")
        void verticalY() {
            Set<BlockPos> solid = solids(new BlockPos(0, -1, 0), new BlockPos(0, 4, 0));
            Map<BlockPos, Direction.Axis> g = new HashMap<>();
            for (int y = 0; y <= 3; y++) g.put(new BlockPos(0, y, 0), Direction.Axis.Y);
            List<GirderLine> lines = HullCache.findValidGirderLines(g, solid, HullCache.computeExteriorVoid(solid), RAY);
            assertEquals(1, lines.size());
            assertEquals(Direction.Axis.Y, lines.get(0).axis());
        }

        @Test @DisplayName("балка в пустоту (нет стен) → 0 линий")
        void intoVoid() {
            var g = girdersAlongX(0, 0, Direction.Axis.X, 0, 1);
            assertTrue(HullCache.findValidGirderLines(g, Set.of(), Set.of(), RAY).isEmpty());
        }

        @Test @DisplayName("дальность луча ограничивает: стена дальше maxRange → 0 линий")
        void rayRangeLimit() {
            Set<BlockPos> solid = solids(new BlockPos(-1, 0, 0), new BlockPos(100, 0, 0));
            var g = girdersAlongX(0, 0, Direction.Axis.X, 0, 1, 2, 3);
            // Правая стена на x=100, дальше короткого луча от торца B (x=3) → торец B невалиден.
            assertTrue(HullCache.findValidGirderLines(g, solid, HullCache.computeExteriorVoid(solid), 5).isEmpty());
        }

        @Test @DisplayName("пустой ввод балок → пустой список")
        void emptyGirders() {
            assertTrue(HullCache.findValidGirderLines(Map.of(), Set.of(), Set.of(), RAY).isEmpty());
        }

        @Test @DisplayName("результат immutable")
        void immutableResult() {
            Set<BlockPos> solid = solids(new BlockPos(-1, 0, 0), new BlockPos(4, 0, 0));
            var g = girdersAlongX(0, 0, Direction.Axis.X, 0, 1, 2, 3);
            List<GirderLine> lines = HullCache.findValidGirderLines(g, solid, HullCache.computeExteriorVoid(solid), RAY);
            assertThrows(UnsupportedOperationException.class,
                () -> lines.add(new GirderLine(Direction.Axis.X, new BlockPos(0, 0, 0), new BlockPos(1, 0, 0))));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  invalidate_hull_cache — оркестратор
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("invalidate_hull_cache")
    class Invalidate {

        @Test @DisplayName("начальное состояние: кэши пусты/нулевые")
        void initialState() {
            assertTrue(cache.getExteriorSurface().isEmpty());
            assertTrue(cache.getValidGirderLines().isEmpty());
            assertEquals(0f, cache.getRibCoverage(), 0.001f);
        }

        @Test @DisplayName("не бросает на Object-заглушке SubLevel")
        void doesNotThrow() {
            assertDoesNotThrow(() -> cache.invalidate_hull_cache(new Object(), SUPPORT_STEP));
        }

        @Test @DisplayName("после вызова кэши согласованы")
        void consistent() {
            cache.invalidate_hull_cache(new Object(), SUPPORT_STEP);
            assertNotNull(cache.getExteriorSurface());
            assertNotNull(cache.getValidGirderLines());
            float cov = cache.getRibCoverage();
            assertTrue(cov >= 0f && cov <= 1f);
        }

        @Test @DisplayName("открытый корпус → ribCoverage = 0.0")
        void openStructure() {
            cache.invalidate_hull_cache(new Object(), SUPPORT_STEP);
            assertEquals(0f, cache.getRibCoverage(), 0.001f);
        }

        @Test @DisplayName("повторная инвалидация без рассинхрона")
        void repeated() {
            cache.invalidate_hull_cache(new Object(), SUPPORT_STEP);
            cache.invalidate_hull_cache(new Object(), SUPPORT_STEP * 2);
            assertTrue(cache.getRibCoverage() >= 0f && cache.getRibCoverage() <= 1f);
        }

        @Test @DisplayName("supportStep 0 и отрицательный → не бросает")
        void edgeSupportStep() {
            assertDoesNotThrow(() -> cache.invalidate_hull_cache(new Object(), 0f));
            assertDoesNotThrow(() -> cache.invalidate_hull_cache(new Object(), -0.1f));
        }

        @Test @DisplayName("getters возвращают immutable")
        void gettersImmutable() {
            cache.invalidate_hull_cache(new Object(), SUPPORT_STEP);
            assertThrows(UnsupportedOperationException.class,
                () -> cache.getExteriorSurface().add(new BlockPos(0, 0, 0)));
            assertThrows(UnsupportedOperationException.class,
                () -> cache.getValidGirderLines().add(new GirderLine(Direction.Axis.X, new BlockPos(0, 0, 0), new BlockPos(1, 0, 0))));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  GirderLine — инвариант типа
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GirderLine — инвариант")
    class GirderLineType {

        @Test @DisplayName("валидная линия конструируется")
        void valid() {
            GirderLine l = new GirderLine(Direction.Axis.X, new BlockPos(0, 0, 0), new BlockPos(3, 0, 0));
            assertEquals(List.of(new BlockPos(0, 0, 0), new BlockPos(3, 0, 0)), l.anchors());
        }

        @Test @DisplayName("невыровненные торцы → IllegalArgumentException")
        void notCollinear() {
            assertThrows(IllegalArgumentException.class,
                () -> new GirderLine(Direction.Axis.X, new BlockPos(0, 0, 0), new BlockPos(3, 1, 0)));
        }

        @Test @DisplayName("перевёрнутый порядок (A не раньше B) → IllegalArgumentException")
        void reversed() {
            assertThrows(IllegalArgumentException.class,
                () -> new GirderLine(Direction.Axis.X, new BlockPos(3, 0, 0), new BlockPos(0, 0, 0)));
        }

        @Test @DisplayName("нулевая длина (A == B) → IllegalArgumentException")
        void zeroLength() {
            assertThrows(IllegalArgumentException.class,
                () -> new GirderLine(Direction.Axis.X, new BlockPos(0, 0, 0), new BlockPos(0, 0, 0)));
        }

        @Test @DisplayName("null → NullPointerException")
        void nulls() {
            assertThrows(NullPointerException.class,
                () -> new GirderLine(null, new BlockPos(0, 0, 0), new BlockPos(1, 0, 0)));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  RibCoverage — покрытие
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("compute_rib_coverage")
    class RibCoverage {
        private static final float STEP = 0.2f;

        private GirderLine lineX(int x0, int x1) {
            return new GirderLine(Direction.Axis.X, new BlockPos(x0, 0, 0), new BlockPos(x1, 0, 0));
        }

        @Test @DisplayName("пустая поверхность → 0.0")
        void emptySurface() {
            assertEquals(0f, HullCache.computeRibCoverage(Set.of(), List.of(lineX(0, 4)), STEP), 1e-6f);
        }

        @Test @DisplayName("нет линий → 0.0")
        void noLines() {
            Set<BlockPos> surf = new HashSet<>();
            for (int i = 0; i < 5; i++) surf.add(new BlockPos(i, 0, 0));
            assertEquals(0f, HullCache.computeRibCoverage(surf, List.of(), STEP), 1e-6f);
        }

        @Test @DisplayName("линия 0..4, STEP 0.2 → всё покрыто (1.0)")
        void fullyCovered() {
            Set<BlockPos> surf = new HashSet<>();
            for (int i = 0; i < 5; i++) surf.add(new BlockPos(i, 0, 0));
            assertEquals(1.0f, HullCache.computeRibCoverage(surf, List.of(lineX(0, 4)), STEP), 1e-6f);
        }

        @Test @DisplayName("STEP 0.6 → центр непокрыт → 0.8")
        void centerUncovered() {
            Set<BlockPos> surf = new HashSet<>();
            for (int i = 0; i < 5; i++) surf.add(new BlockPos(i, 0, 0));
            assertEquals(0.8f, HullCache.computeRibCoverage(surf, List.of(lineX(0, 4)), 0.6f), 1e-6f);
        }
    }
}