package com.example.terradiver.pressure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Юнит-тесты для PressureUtils.compute_raw_debuff().
 * Спецификация — TD_06 v1.0, TD_03.
 *
 * <p>Нет зависимостей от MC-реестра — Bootstrap не нужен.
 * Балансовые константы взяты из ModConfig (дефолтные значения):
 * DEEPSLATE_Y=8, BEDROCK_Y=-64, K=0.653, SCALE=20.0
 * При изменении дефолтов в ModConfig — обновить здесь.
 */
@DisplayName("PressureUtils — compute_raw_debuff")
class PressureUtilsTest {

    // Дефолтные значения из ModConfig
    private static final float DEEPSLATE_Y = 8f;
    private static final float BEDROCK_Y   = -64f;
    private static final float K           = 0.653f;
    private static final float SCALE       = 20f;

    // ──── Граничные случаи из TD_06 ────────────────────────────────────

    @Test
    @DisplayName("Y > DEEPSLATE_Y → depth=0 → raw_debuff=1.0 (нет давления выше зоны)")
    void testAboveDeepslateZone() {
        float result = PressureUtils.compute_raw_debuff(64f, DEEPSLATE_Y, BEDROCK_Y, K, SCALE);
        assertEquals(1.0f, result, 0.001f,
            "Выше DEEPSLATE_Y: давления нет → 1.0");
    }

    @Test
    @DisplayName("Y = DEEPSLATE_Y → depth=0 → raw_debuff=1.0 (ровно на границе)")
    void testAtDeepslateY() {
        // depth = max(0, 8-8) = 0 → ln(1+0)=0 → raw = 1.0
        float result = PressureUtils.compute_raw_debuff(DEEPSLATE_Y, DEEPSLATE_Y, BEDROCK_Y, K, SCALE);
        assertEquals(1.0f, result, 0.001f,
            "На границе DEEPSLATE_Y: depth=0 → 1.0");
    }

    @Test
    @DisplayName("Y = BEDROCK_Y → raw_debuff в [0,1], строго меньше чем у DEEPSLATE_Y")
    void testAtBedrockY() {
        // depth = 8-(-64) = 72; raw = 1 - 0.653 * ln(1 + 72/20) = 1 - 0.653*ln(4.6) ≈ 0.004
        // Точное значение зависит от k/SCALE. Гарантии спецификации: в [0,1] и < значения у DEEPSLATE_Y.
        float atBedrock   = PressureUtils.compute_raw_debuff(BEDROCK_Y,   DEEPSLATE_Y, BEDROCK_Y, K, SCALE);
        float atDeepslate = PressureUtils.compute_raw_debuff(DEEPSLATE_Y, DEEPSLATE_Y, BEDROCK_Y, K, SCALE);
        assertTrue(atBedrock >= 0f,         "raw_debuff не должен быть отрицательным");
        assertTrue(atBedrock <= 1f,         "raw_debuff не должен превышать 1.0");
        assertTrue(atBedrock < atDeepslate, "У BEDROCK_Y давление больше, чем у DEEPSLATE_Y");
    }

    @Test
    @DisplayName("TD_06 пример: Y=8 (DEEPSLATE_Y) → 1.0")
    void testSpecExampleDeepslate() {
        float result = PressureUtils.compute_raw_debuff(8f, DEEPSLATE_Y, BEDROCK_Y, K, SCALE);
        assertEquals(1.0f, result, 0.001f);
    }

    @Test
    @DisplayName("TD_06 пример: Y≈-15 → ≈0.5 (проверяем диапазон, не точное значение)")
    void testSpecExampleMidDepth() {
        // Из спецификации: Y ≈ -15 → ≈ 0.5 (с реальными K=0.653, SCALE=20)
        // depth = 23; raw = 1 - 0.653 * ln(2.15) ≈ 1 - 0.653 * 0.765 ≈ 0.50 ✓
        float result = PressureUtils.compute_raw_debuff(-15f, DEEPSLATE_Y, BEDROCK_Y, K, SCALE);
        assertTrue(result > 0.3f && result < 0.7f,
            "Y=-15: ожидается ~0.5, получено: " + result);
    }

    // ──── Монотонность ──────────────────────────────────────────────────

    @Test
    @DisplayName("Монотонное убывание: глубже → меньше raw_debuff")
    void testMonotonicallyDecreasing() {
        float atDeepslate = PressureUtils.compute_raw_debuff(  8f, DEEPSLATE_Y, BEDROCK_Y, K, SCALE);
        float atMinus20   = PressureUtils.compute_raw_debuff(-20f, DEEPSLATE_Y, BEDROCK_Y, K, SCALE);
        float atMinus40   = PressureUtils.compute_raw_debuff(-40f, DEEPSLATE_Y, BEDROCK_Y, K, SCALE);
        float atBedrock   = PressureUtils.compute_raw_debuff(-64f, DEEPSLATE_Y, BEDROCK_Y, K, SCALE);

        assertTrue(atDeepslate >= atMinus20, "Y=8 >= Y=-20");
        assertTrue(atMinus20   >= atMinus40, "Y=-20 >= Y=-40");
        assertTrue(atMinus40   >= atBedrock, "Y=-40 >= Y=-64");
    }

    // ──── Диапазон вывода ──────────────────────────────────────────────

    @Test
    @DisplayName("Результат всегда в [0.0, 1.0] для любого Y в диапазоне")
    void testOutputInRange() {
        float[] testYs = {200f, 64f, 8f, 0f, -10f, -30f, -64f, -100f};
        for (float y : testYs) {
            float result = PressureUtils.compute_raw_debuff(y, DEEPSLATE_Y, BEDROCK_Y, K, SCALE);
            assertTrue(result >= 0f && result <= 1f,
                "raw_debuff вне [0,1] при Y=" + y + ": " + result);
        }
    }

    @Test
    @DisplayName("Y значительно ниже BEDROCK_Y → клампируется в 0.0, не уходит в минус")
    void testBelowBedrockClamped() {
        // Y=-200: depth=208; raw = 1 - 0.653*ln(11.4) ≈ 1 - 1.589 ≈ -0.589 → clamp → 0.0
        float result = PressureUtils.compute_raw_debuff(-200f, DEEPSLATE_Y, BEDROCK_Y, K, SCALE);
        assertEquals(0f, result, 0.001f,
            "Экстремальная глубина: результат должен быть клампнут в 0.0");
    }

    // ──── Нейтральные параметры кривой ────────────────────────────────

    @Test
    @DisplayName("k=0 → raw_debuff=1.0 на любой глубине (кривая выключена)")
    void testKZeroNoDecay() {
        // raw = 1 - 0 * ln(...) = 1.0 всегда
        float result = PressureUtils.compute_raw_debuff(-50f, DEEPSLATE_Y, BEDROCK_Y, 0f, SCALE);
        assertEquals(1.0f, result, 0.001f,
            "k=0: коэффициент кривой нулевой → давления нет");
    }

    @Test
    @DisplayName("Очень большой SCALE → кривая почти плоская, raw_debuff близок к 1.0")
    void testLargeScaleFlatCurve() {
        // SCALE → ∞: depth/SCALE → 0 → ln(1+0) → 0 → raw → 1.0
        float result = PressureUtils.compute_raw_debuff(-64f, DEEPSLATE_Y, BEDROCK_Y, K, 100000f);
        assertTrue(result > 0.95f,
            "При огромном SCALE кривая почти плоская, давление минимально");
    }

    // ──── Корректность формулы ─────────────────────────────────────────

    @Test
    @DisplayName("Ручной расчёт: Y=-32 → точное значение по формуле")
    void testManualCalculation() {
        // depth = 8-(-32) = 40; raw = 1 - 0.653 * ln(1 + 40/20) = 1 - 0.653 * ln(3)
        // ln(3) ≈ 1.0986; raw ≈ 1 - 0.653 * 1.0986 ≈ 0.283
        float expected = 1f - K * (float) Math.log(1.0 + 40.0 / SCALE);
        float result   = PressureUtils.compute_raw_debuff(-32f, DEEPSLATE_Y, BEDROCK_Y, K, SCALE);
        assertEquals(expected, result, 0.001f,
            "Результат должен точно соответствовать формуле 1 - k*ln(1 + depth/scale)");
    }
}