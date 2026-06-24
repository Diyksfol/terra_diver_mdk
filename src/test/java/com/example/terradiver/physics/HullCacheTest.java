package com.example.terradiver.pressure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Юнит-тесты для HullCache.
 * Спецификация — TD_06 v1.0, TD_03.
 *
 * <p><b>Нет зависимостей от MC-реестра:</b> Bootstrap не нужен.
 * BlockState создаётся через Mockito.mock() — только интерфейс, без MC Bootstrap.
 * isFullCollision вынесен в IFullCollisionChecker: тесты передают лямбду
 * {@code state -> state != null}, не вызывая Shapes.block() / EmptyBlockGetter.
 *
 * <p>Тесты invalidate_hull_cache: оркестраторные гарантии (порядок, атомарность,
 * начальное состояние). Расширить после реализации girder_lines/rib_coverage.
 */
@DisplayName("HullCache — compute_exterior_surface + invalidate_hull_cache")
class HullCacheTest {

    private HullCache cache;
    private static final float SUPPORT_STEP = 0.2f;

    // ── Тестовые провайдеры (без MC Bootstrap) ────────────────────────────────────

    private static HullCache.ISubLevelBlockReader readerOf(Map<BlockPos, BlockState> blocks) {
        return (subLevel, pos) -> blocks.get(pos); // null = пусто
    }

    private static HullCache.ISubLevelBoundsProvider boundsOf(Map<BlockPos, BlockState> blocks) {
        return subLevel -> {
            if (blocks.isEmpty()) return new BlockPos[]{ BlockPos.ZERO, BlockPos.ZERO };
            int xMin = Integer.MAX_VALUE, yMin = Integer.MAX_VALUE, zMin = Integer.MAX_VALUE;
            int xMax = Integer.MIN_VALUE, yMax = Integer.MIN_VALUE, zMax = Integer.MIN_VALUE;
            for (BlockPos p : blocks.keySet()) {
                xMin = Math.min(xMin, p.getX()); xMax = Math.max(xMax, p.getX());
                yMin = Math.min(yMin, p.getY()); yMax = Math.max(yMax, p.getY());
                zMin = Math.min(zMin, p.getZ()); zMax = Math.max(zMax, p.getZ());
            }
            return new BlockPos[]{ new BlockPos(xMin,yMin,zMin), new BlockPos(xMax,yMax,zMax) };
        };
    }

    /**
     * В тестах полная коллизия = «блок есть» (state != null).
     * Не вызывает Shapes.block() или EmptyBlockGetter — нет MC Bootstrap.
     */
    private static final HullCache.IFullCollisionChecker TEST_COLLISION = state -> state != null;

    /**
     * Mock-BlockState, представляющий «полный блок корпуса».
     * Mockito.mock() не требует MC Bootstrap — создаёт прокси интерфейса/класса.
     */
    private static BlockState solid() {
        return Mockito.mock(BlockState.class);
    }

    // ── Вспомогательные методы для запуска с тестовыми провайдерами ───────────────

    private static Set<BlockPos> exterior(Map<BlockPos, BlockState> blocks) {
        return HullCache.computeExteriorSurface(
            null, readerOf(blocks), boundsOf(blocks), TEST_COLLISION);
    }

    @BeforeEach
    void setUp() {
        cache = new HullCache();
    }

    // ──── Начальное состояние ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Начальное состояние: кэши пусты/нулевые до первой инвалидации")
    void testInitialState() {
        assertNotNull(cache.getExteriorSurface());
        assertTrue(cache.getExteriorSurface().isEmpty());
        assertNotNull(cache.getValidGirderLines());
        assertTrue(cache.getValidGirderLines().isEmpty());
        assertEquals(0f, cache.getRibCoverage(), 0.001f);
    }

    // ──── invalidate_hull_cache: оркестраторные гарантии ─────────────────────────

    @Test
    @DisplayName("invalidate_hull_cache: не бросает исключений на пустом SubLevel")
    void testDoesNotThrowOnEmptySubLevel() {
        assertDoesNotThrow(() -> cache.invalidate_hull_cache(new Object(), SUPPORT_STEP));
    }

    @Test
    @DisplayName("invalidate_hull_cache: после вызова все кэши не null, ribCoverage в [0,1]")
    void testAllCachesNotNullAfterInvalidation() {
        cache.invalidate_hull_cache(new Object(), SUPPORT_STEP);
        assertNotNull(cache.getExteriorSurface());
        assertNotNull(cache.getValidGirderLines());
        float cov = cache.getRibCoverage();
        assertTrue(cov >= 0f && cov <= 1f);
    }

    @Test
    @DisplayName("invalidate_hull_cache: открытый корпус → ribCoverage=0.0 без исключения")
    void testOpenStructureRibCoverageZero() {
        cache.invalidate_hull_cache(new Object(), SUPPORT_STEP);
        assertEquals(0f, cache.getRibCoverage(), 0.001f);
    }

    @Test
    @DisplayName("Атомарность: повторная инвалидация перезаписывает все три кэша")
    void testRepeatedInvalidationOverwritesAll() {
        cache.invalidate_hull_cache(new Object(), SUPPORT_STEP);
        cache.invalidate_hull_cache(new Object(), SUPPORT_STEP * 2);
        assertNotNull(cache.getExteriorSurface());
        assertNotNull(cache.getValidGirderLines());
        assertTrue(cache.getRibCoverage() >= 0f && cache.getRibCoverage() <= 1f);
    }

    @Test
    @DisplayName("supportStep=0 → не бросает исключений")
    void testZeroSupportStep() {
        assertDoesNotThrow(() -> cache.invalidate_hull_cache(new Object(), 0f));
    }

    @Test
    @DisplayName("supportStep отрицательный → не бросает исключений")
    void testNegativeSupportStep() {
        assertDoesNotThrow(() -> cache.invalidate_hull_cache(new Object(), -0.1f));
    }

    // ──── Геттеры: защитные свойства ──────────────────────────────────────────────

    @Test
    @DisplayName("getExteriorSurface: immutable Set")
    void testExteriorSurfaceImmutable() {
        cache.invalidate_hull_cache(new Object(), SUPPORT_STEP);
        Set<?> surface = cache.getExteriorSurface();
        assertThrows(UnsupportedOperationException.class,
            () -> ((Set<Object>) surface).add(null));
    }

    @Test
    @DisplayName("getValidGirderLines: immutable List")
    void testValidGirderLinesImmutable() {
        cache.invalidate_hull_cache(new Object(), SUPPORT_STEP);
        List<?> lines = cache.getValidGirderLines();
        assertThrows(UnsupportedOperationException.class,
            () -> ((List<Object>) lines).add(null));
    }

    // ──── compute_exterior_surface: null/пустые входы ────────────────────────────

    @Test
    @DisplayName("computeExteriorSurface: null bounds → пустое множество")
    void testNullBounds() {
        Set<BlockPos> result = HullCache.computeExteriorSurface(
            null, (sl, p) -> null, sl -> null, TEST_COLLISION);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("computeExteriorSurface: пустой SubLevel (нет блоков) → пустое множество")
    void testEmptySubLevel() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        assertTrue(exterior(blocks).isEmpty());
    }

    // ──── compute_exterior_surface: основная логика ──────────────────────────────

    @Test
    @DisplayName("computeExteriorSurface: один блок → входит в surface")
    void testSingleBlock() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockPos center = new BlockPos(0, 0, 0);
        blocks.put(center, solid());

        Set<BlockPos> result = exterior(blocks);

        assertTrue(result.contains(center),
            "Одиночный блок: все 6 сторон — внешняя пустота, должен быть в surface");
    }

    @Test
    @DisplayName("computeExteriorSurface: плоская плита 3×1×3 → все 9 блоков в surface")
    void testFlatSlab() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int x = 0; x <= 2; x++)
            for (int z = 0; z <= 2; z++)
                blocks.put(new BlockPos(x, 0, z), solid());

        assertEquals(9, exterior(blocks).size(),
            "Плоский слой: все блоки граничат с внешней пустотой сверху/снизу");
    }

    @Test
    @DisplayName("computeExteriorSurface: сплошной куб 3×3×3 → ровно 26 внешних блоков")
    void testSolidCubeExterior() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int x = 0; x <= 2; x++)
            for (int y = 0; y <= 2; y++)
                for (int z = 0; z <= 2; z++)
                    blocks.put(new BlockPos(x, y, z), solid());

        Set<BlockPos> result = exterior(blocks);

        assertEquals(26, result.size(),
            "3×3×3: 27 блоков, центральный (1,1,1) без соседей в externalAir → не в surface");
        assertFalse(result.contains(new BlockPos(1, 1, 1)),
            "Центральный блок куба не граничит с внешней пустотой");
    }

    @Test
    @DisplayName("computeExteriorSurface: внутренняя закрытая полость → внутренний блок НЕ в surface")
    void testClosedCavityInnerBlockExcluded() {
        // Сплошной куб 5×5×5 (оболочка) + один блок в центре закрытой полости.
        // Flood-fill снаружи не достигает центра — он не должен попасть в surface.
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        // Внешняя оболочка
        for (int x = 0; x <= 4; x++)
            for (int y = 0; y <= 4; y++)
                for (int z = 0; z <= 4; z++) {
                    boolean isShell = x==0||x==4||y==0||y==4||z==0||z==4;
                    if (isShell) blocks.put(new BlockPos(x,y,z), solid());
                }
        // Блок внутри закрытой полости
        BlockPos inner = new BlockPos(2, 2, 2);
        blocks.put(inner, solid());

        Set<BlockPos> result = exterior(blocks);

        assertFalse(result.contains(inner),
            "Блок внутри закрытой полости не граничит с внешней пустотой (TD_06, граничный случай)");
        assertTrue(result.contains(new BlockPos(0, 0, 0)),
            "Угол внешней оболочки должен быть в surface");
    }

    @Test
    @DisplayName("computeExteriorSurface: две изолированные структуры → обе дают surface")
    void testTwoSeparateStructures() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockPos a = new BlockPos(0, 0, 0);
        BlockPos b = new BlockPos(10, 0, 0); // далеко, нет общих соседей
        blocks.put(a, solid());
        blocks.put(b, solid());

        Set<BlockPos> result = exterior(blocks);

        assertTrue(result.contains(a), "Первый одиночный блок должен быть в surface");
        assertTrue(result.contains(b), "Второй одиночный блок должен быть в surface");
    }

    @Test
    @DisplayName("computeExteriorSurface: результат immutable")
    void testResultIsImmutable() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(BlockPos.ZERO, solid());

        Set<BlockPos> result = exterior(blocks);

        assertThrows(UnsupportedOperationException.class,
            () -> result.add(new BlockPos(99, 99, 99)));
    }

    @Test
    @DisplayName("computeExteriorSurface: детерминированность — повторный вызов даёт тот же результат")
    void testDeterministic() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int x = 0; x < 3; x++)
            for (int y = 0; y < 3; y++)
                blocks.put(new BlockPos(x, y, 0), solid());

        Set<BlockPos> r1 = exterior(blocks);
        Set<BlockPos> r2 = exterior(blocks);
        assertEquals(r1, r2);
    }

    @Test
    @DisplayName("computeExteriorSurface: воздушный блок (null) не входит в surface")
    void testAirBlockExcluded() {
        // Блок в центре окружён воздухом — сам воздух не должен попасть в surface
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(new BlockPos(0, 0, 0), solid());
        // Позиция (1,0,0) — отсутствует (воздух). Не должна быть в surface.
        Set<BlockPos> result = exterior(blocks);
        assertFalse(result.contains(new BlockPos(1, 0, 0)),
            "Воздушная позиция не должна входить в exterior_surface");
    }
}