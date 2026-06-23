package com.example.terradiver.pressure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Юнит-тесты для HullCache.invalidate_hull_cache().
 * Спецификация — TD_06 v1.0, TD_03.
 *
 * <p>Нет зависимостей от MC-реестра. Bootstrap не нужен.
 *
 * <p><b>Ограничение текущих тестов:</b> compute_exterior_surface, find_valid_girder_lines
 * и compute_rib_coverage ещё не реализованы (заглушки возвращают пустые коллекции/0.0).
 * Тесты покрывают гарантии оркестратора: порядок вызовов, атомарность, корректное
 * начальное состояние, поведение на пустом корпусе. Когда функции будут реализованы —
 * тесты расширить, заглушки убрать.
 */
@DisplayName("HullCache — invalidate_hull_cache")
class HullCacheTest {

    private HullCache cache;
    private static final float SUPPORT_STEP = 0.2f;

    @BeforeEach
    void setUp() {
        cache = new HullCache();
    }

    // ──── Начальное состояние ──────────────────────────────────────────

    @Test
    @DisplayName("Начальное состояние: кэши пусты/нулевые до первой инвалидации")
    void testInitialState() {
        assertNotNull(cache.getExteriorSurface(), "exteriorSurface не null изначально");
        assertTrue(cache.getExteriorSurface().isEmpty(), "exteriorSurface пуст изначально");
        assertNotNull(cache.getValidGirderLines(), "validGirderLines не null изначально");
        assertTrue(cache.getValidGirderLines().isEmpty(), "validGirderLines пуст изначально");
        assertEquals(0f, cache.getRibCoverage(), 0.001f, "ribCoverage = 0.0 изначально");
    }

    // ──── invalidate_hull_cache: базовое поведение ────────────────────

    @Test
    @DisplayName("invalidate_hull_cache: не бросает исключений на null-like SubLevel")
    void testDoesNotThrowOnEmptySubLevel() {
        // SubLevel — Object-заглушка (реальный тип не подтверждён)
        // Должен отработать без NPE даже на плейсхолдере
        assertDoesNotThrow(() -> cache.invalidate_hull_cache(new Object(), SUPPORT_STEP));
    }

    @Test
    @DisplayName("invalidate_hull_cache: после вызова все три кэша не null")
    void testAllCachesNotNullAfterInvalidation() {
        cache.invalidate_hull_cache(new Object(), SUPPORT_STEP);

        assertNotNull(cache.getExteriorSurface());
        assertNotNull(cache.getValidGirderLines());
        // ribCoverage — примитив float, null невозможен; проверяем диапазон
        float cov = cache.getRibCoverage();
        assertTrue(cov >= 0f && cov <= 1f, "ribCoverage в [0,1] после инвалидации");
    }

    @Test
    @DisplayName("invalidate_hull_cache: открытый корпус → ribCoverage=0.0 (не исключение)")
    void testOpenStructureRibCoverageZero() {
        // Пустой SubLevel → computeExteriorSurface вернёт пустое множество →
        // computeRibCoverage на пустом exterior_surface обязан вернуть 0.0, не упасть
        // (граничный случай из TD_06: деление на ноль запрещено)
        cache.invalidate_hull_cache(new Object(), SUPPORT_STEP);

        assertEquals(0f, cache.getRibCoverage(), 0.001f,
            "Открытый корпус (нет exterior_surface) → ribCoverage=0.0");
    }

    // ──── Атомарность ─────────────────────────────────────────────────

    @Test
    @DisplayName("Атомарность: повторная инвалидация перезаписывает все три кэша")
    void testRepeatedInvalidationOverwritesAll() {
        cache.invalidate_hull_cache(new Object(), SUPPORT_STEP);
        Set<?>  surfaceAfterFirst  = cache.getExteriorSurface();
        List<?> girdersAfterFirst  = cache.getValidGirderLines();
        float   coverageAfterFirst = cache.getRibCoverage();

        // Повторная инвалидация с другим SUPPORT_STEP
        cache.invalidate_hull_cache(new Object(), SUPPORT_STEP * 2);

        // Состояние согласовано: не должно быть рассинхрона «новый surface, старый coverage»
        // Реальная проверка согласованности будет возможна после реализации заглушек.
        // Сейчас проверяем хотя бы что ссылки обновились или остались теми же (не NPE).
        assertNotNull(cache.getExteriorSurface());
        assertNotNull(cache.getValidGirderLines());
        float cov = cache.getRibCoverage();
        assertTrue(cov >= 0f && cov <= 1f);
    }

    // ──── SUPPORT_STEP параметр ────────────────────────────────────────

    @Test
    @DisplayName("supportStep=0 → не бросает исключений (крайний параметр)")
    void testZeroSupportStep() {
        assertDoesNotThrow(() -> cache.invalidate_hull_cache(new Object(), 0f));
    }

    @Test
    @DisplayName("supportStep отрицательный → не бросает исключений (неверный ввод)")
    void testNegativeSupportStep() {
        // Отрицательный SUPPORT_STEP — ошибка конфига, но оркестратор не должен упасть
        assertDoesNotThrow(() -> cache.invalidate_hull_cache(new Object(), -0.1f));
    }

    // ──── Геттеры: защитные свойства ──────────────────────────────────

    @Test
    @DisplayName("getExteriorSurface: возвращает неизменяемую коллекцию (Set.of())")
    void testExteriorSurfaceImmutable() {
        cache.invalidate_hull_cache(new Object(), SUPPORT_STEP);
        Set<?> surface = cache.getExteriorSurface();
        // Set.of() бросает UnsupportedOperationException при попытке мутировать
        // Тест документирует ожидаемое поведение: читатели не должны мутировать кэш
        assertThrows(UnsupportedOperationException.class,
            () -> ((Set<Object>) surface).add(null),
            "Кэш exterior_surface должен быть immutable (Set.of())");
    }

    @Test
    @DisplayName("getValidGirderLines: возвращает неизменяемую коллекцию (List.of())")
    void testValidGirderLinesImmutable() {
        cache.invalidate_hull_cache(new Object(), SUPPORT_STEP);
        List<?> lines = cache.getValidGirderLines();
        assertThrows(UnsupportedOperationException.class,
            () -> ((List<Object>) lines).add(null),
            "Кэш validGirderLines должен быть immutable (List.of())");
    }
}
