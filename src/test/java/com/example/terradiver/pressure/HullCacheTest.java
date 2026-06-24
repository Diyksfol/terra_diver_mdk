package com.example.terradiver.pressure;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Юнит-тесты для HullCache: compute_exterior_surface (чистое ядро) и invalidate_hull_cache.
 * Спецификация — TD_06 v1.0, TD_03.
 *
 * <p><b>Без Minecraft-runtime.</b> Тесты работают исключительно с {@link BlockPos}
 * (лёгкий POJO) и множествами. Никакого Bootstrap, реестров или реальных BlockState —
 * вся MC-специфика ({@code getCollisionShape() == Shapes.block()}) вынесена в адаптер
 * {@code computeExteriorSurface(Object)} и здесь не задействована. Именно это разделение
 * чинит прежний прогон, где тесты падали на загрузке MC-классов
 * (IllegalStateException → ClassNotFoundException).
 */
@DisplayName("HullCache — compute_exterior_surface + invalidate_hull_cache")
class HullCacheTest {

    private HullCache cache;
    private static final float SUPPORT_STEP = 0.2f;

    @BeforeEach
    void setUp() {
        cache = new HullCache();
    }

    /** Удобный билдер множества полных кубов. */
    private static Set<BlockPos> solids(BlockPos... positions) {
        return new HashSet<>(List.of(positions));
    }

    /** Сплошной заполненный параллелепипед [x0..x1]×[y0..y1]×[z0..z1]. */
    private static Set<BlockPos> filledBox(int x0, int y0, int z0, int x1, int y1, int z1) {
        Set<BlockPos> s = new HashSet<>();
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++)
                    s.add(new BlockPos(x, y, z));
        return s;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  compute_exterior_surface — геометрия
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("compute_exterior_surface")
    class ExteriorSurface {

        @Test
        @DisplayName("один блок → входит в surface")
        void singleBlock() {
            BlockPos p = new BlockPos(0, 0, 0);
            Set<BlockPos> surface = HullCache.computeExteriorSurface(solids(p));
            assertEquals(Set.of(p), surface);
        }

        @Test
        @DisplayName("полоса 3×1×3 → все 9 блоков в surface")
        void flatStrip() {
            // Плоская плита 3×3, толщина 1: каждый блок касается воздуха сверху/снизу → все внешние.
            Set<BlockPos> hull = filledBox(0, 0, 0, 2, 0, 2);
            assertEquals(9, hull.size(), "контрольная проверка входа");
            Set<BlockPos> surface = HullCache.computeExteriorSurface(hull);
            assertEquals(hull, surface, "вся плита — внешняя поверхность");
        }

        @Test
        @DisplayName("сплошной куб 3×3×3 → ровно 26 внешних блоков (центр исключён)")
        void solidCubeExcludesCenter() {
            Set<BlockPos> hull = filledBox(0, 0, 0, 2, 2, 2); // 27 блоков
            assertEquals(27, hull.size());
            Set<BlockPos> surface = HullCache.computeExteriorSurface(hull);

            assertEquals(26, surface.size(), "26 внешних из 27");
            BlockPos center = new BlockPos(1, 1, 1);
            assertFalse(surface.contains(center),
                "внутренний блок (центр) НЕ в surface — все 6 соседей сплошные");
        }

        @Test
        @DisplayName("внутренняя закрытая полость — внутренний блок НЕ в surface")
        void internalCavityBlockExcluded() {
            // 5×5×5 сплошной куб: центральный блок (2,2,2) полностью окружён → не поверхность.
            // Его полость не достижима снаружи, оболочка из полных кубов запечатывает её.
            Set<BlockPos> hull = filledBox(0, 0, 0, 4, 4, 4);
            Set<BlockPos> surface = HullCache.computeExteriorSurface(hull);

            assertFalse(surface.contains(new BlockPos(2, 2, 2)),
                "глубоко внутренний блок не подвергается внешнему давлению");
            // Внешняя оболочка 5³: 125 − 27(внутренний 3³) = 98.
            assertEquals(98, surface.size(), "поверхность = оболочка, внутренний объём исключён");
        }

        @Test
        @DisplayName("запечатанная воздушная полость не создаёт ложную поверхность")
        void sealedAirCavityDoesNotLeak() {
            // 3×3×3 сплошной куб с вынутым центром: центр — воздух, запертый оболочкой.
            // Эта внутренняя пустота недостижима снаружи → не должна добавлять/менять surface.
            Set<BlockPos> hull = filledBox(0, 0, 0, 2, 2, 2);
            hull.remove(new BlockPos(1, 1, 1)); // 26 блоков-оболочка вокруг пустого центра

            Set<BlockPos> surface = HullCache.computeExteriorSurface(hull);
            // Все 26 блоков оболочки касаются ВНЕШНЕГО воздуха (это всего лишь тонкая корка),
            // поэтому все 26 — поверхность; внутренняя полость на это не влияет.
            assertEquals(hull, surface);
            assertFalse(surface.contains(new BlockPos(1, 1, 1)),
                "пустая клетка-полость не входит в surface (возвращаются только блоки)");
        }

        @Test
        @DisplayName("две изолированные структуры → обе дают surface")
        void twoIsolatedStructures() {
            BlockPos a = new BlockPos(0, 0, 0);
            BlockPos b = new BlockPos(10, 0, 0); // далеко, разделены воздухом
            Set<BlockPos> surface = HullCache.computeExteriorSurface(solids(a, b));
            assertTrue(surface.contains(a), "первая структура в surface");
            assertTrue(surface.contains(b), "вторая структура в surface");
            assertEquals(2, surface.size());
        }

        @Test
        @DisplayName("воздушный блок (отсутствующий) не входит в surface")
        void airBlockNotInSurface() {
            // Вход содержит только полные кубы. Любая позиция вне множества — воздух/частичный
            // блок и не может попасть в surface. Проверяем, что соседняя пустая клетка отсутствует.
            BlockPos p = new BlockPos(0, 0, 0);
            Set<BlockPos> surface = HullCache.computeExteriorSurface(solids(p));
            assertFalse(surface.contains(new BlockPos(1, 0, 0)),
                "пустая (воздушная) клетка не может быть поверхностью");
            assertFalse(surface.contains(new BlockPos(0, 1, 0)));
        }

        @Test
        @DisplayName("пустой/null вход → пустой surface (открытая конструкция)")
        void emptyInput() {
            assertTrue(HullCache.computeExteriorSurface(Set.of()).isEmpty(),
                "пустой корпус → пустая поверхность");
            assertTrue(HullCache.computeExteriorSurface(null).isEmpty(),
                "null → пустая поверхность, без NPE");
        }

        @Test
        @DisplayName("детерминированность — повторный вызов даёт тот же результат")
        void deterministic() {
            Set<BlockPos> hull = filledBox(0, 0, 0, 2, 2, 2);
            Set<BlockPos> first  = HullCache.computeExteriorSurface(hull);
            Set<BlockPos> second = HullCache.computeExteriorSurface(hull);
            assertEquals(first, second);
        }

        @Test
        @DisplayName("результат immutable")
        void resultImmutable() {
            Set<BlockPos> surface = HullCache.computeExteriorSurface(solids(new BlockPos(0, 0, 0)));
            assertThrows(UnsupportedOperationException.class,
                () -> surface.add(new BlockPos(5, 5, 5)),
                "результат compute_exterior_surface должен быть неизменяемым");
        }

        @Test
        @DisplayName("отрицательные/смещённые координаты — корректная работа (локальная система)")
        void negativeCoordinates() {
            // Локальные координаты SubLevel могут быть отрицательными — индексация это учитывает.
            Set<BlockPos> hull = filledBox(-3, -3, -3, -1, -1, -1); // 3×3×3, целиком в минусах
            Set<BlockPos> surface = HullCache.computeExteriorSurface(hull);
            assertEquals(26, surface.size());
            assertFalse(surface.contains(new BlockPos(-2, -2, -2)), "центр исключён и в минусах");
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  invalidate_hull_cache — оркестратор
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("invalidate_hull_cache")
    class Invalidate {

        @Test
        @DisplayName("начальное состояние: кэши пусты/нулевые до первой инвалидации")
        void initialState() {
            assertNotNull(cache.getExteriorSurface());
            assertTrue(cache.getExteriorSurface().isEmpty());
            assertNotNull(cache.getValidGirderLines());
            assertTrue(cache.getValidGirderLines().isEmpty());
            assertEquals(0f, cache.getRibCoverage(), 0.001f);
        }

        @Test
        @DisplayName("не бросает исключений на Object-заглушке SubLevel")
        void doesNotThrowOnStubSubLevel() {
            assertDoesNotThrow(() -> cache.invalidate_hull_cache(new Object(), SUPPORT_STEP));
        }

        @Test
        @DisplayName("после вызова все три кэша согласованы (не null, ribCoverage ∈ [0,1])")
        void cachesConsistentAfterInvalidation() {
            cache.invalidate_hull_cache(new Object(), SUPPORT_STEP);
            assertNotNull(cache.getExteriorSurface());
            assertNotNull(cache.getValidGirderLines());
            float cov = cache.getRibCoverage();
            assertTrue(cov >= 0f && cov <= 1f, "ribCoverage в [0,1]");
        }

        @Test
        @DisplayName("открытый корпус (адаптер-заглушка пуст) → ribCoverage = 0.0")
        void openStructureRibCoverageZero() {
            cache.invalidate_hull_cache(new Object(), SUPPORT_STEP);
            assertEquals(0f, cache.getRibCoverage(), 0.001f);
        }

        @Test
        @DisplayName("повторная инвалидация перезаписывает все три кэша без рассинхрона")
        void repeatedInvalidationOverwritesAll() {
            cache.invalidate_hull_cache(new Object(), SUPPORT_STEP);
            cache.invalidate_hull_cache(new Object(), SUPPORT_STEP * 2);
            assertNotNull(cache.getExteriorSurface());
            assertNotNull(cache.getValidGirderLines());
            float cov = cache.getRibCoverage();
            assertTrue(cov >= 0f && cov <= 1f);
        }

        @Test
        @DisplayName("supportStep = 0 → не бросает исключений")
        void zeroSupportStep() {
            assertDoesNotThrow(() -> cache.invalidate_hull_cache(new Object(), 0f));
        }

        @Test
        @DisplayName("supportStep отрицательный → не бросает исключений")
        void negativeSupportStep() {
            assertDoesNotThrow(() -> cache.invalidate_hull_cache(new Object(), -0.1f));
        }

        @Test
        @DisplayName("getExteriorSurface: возвращает неизменяемую коллекцию")
        void exteriorSurfaceImmutable() {
            cache.invalidate_hull_cache(new Object(), SUPPORT_STEP);
            Set<BlockPos> surface = cache.getExteriorSurface();
            assertThrows(UnsupportedOperationException.class,
                () -> surface.add(new BlockPos(0, 0, 0)));
        }

        @Test
        @DisplayName("getValidGirderLines: возвращает неизменяемую коллекцию")
        void validGirderLinesImmutable() {
            cache.invalidate_hull_cache(new Object(), SUPPORT_STEP);
            List<Object> lines = cache.getValidGirderLines();
            assertThrows(UnsupportedOperationException.class, () -> lines.add(new Object()));
        }
    }
}