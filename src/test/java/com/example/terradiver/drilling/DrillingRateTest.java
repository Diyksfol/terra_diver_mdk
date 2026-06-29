package com.example.terradiver.drilling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Юнит-тесты ядра compute_drilling_rate (TD_02). См. TD_06 v1.0. Чистая формула на float[]
 * твёрдостей, без Minecraft-runtime.
 */
@DisplayName("DrillingRate — compute_drilling_rate_core")
class DrillingRateTest {

    private static final float EPS = 1e-6f;

    // Удобная обёртка с дефолтами: mat, area, press, hardnesses, rpm, minH, rateMax, pending, pFactor.
    private static float rate(float mat, float area, float press, float[] h, float rpm,
                              float rateMax, float pending, float pFactor) {
        return DrillingRate.compute_drilling_rate_core(mat, area, press, h, rpm, 0.1f, rateMax, pending, pFactor);
    }

    @Test @DisplayName("n==0 (бурить нечего) → 0.0")
    void emptySet() {
        assertEquals(0f, rate(2f, 9f, 1f, new float[0], 256f, 1f, 0f, 0.5f), EPS);
        assertEquals(0f, DrillingRate.compute_drilling_rate_core(2f, 9f, 1f, null, 256f, 0.1f, 1f, 0f, 0.5f), EPS);
    }

    @Test @DisplayName("базовая формула: eff=1, всё ×1, h=2, n=1 → 0.5")
    void basic() {
        assertEquals(0.5f, rate(1f, 1f, 1f, new float[]{2f}, 4f, 10f, 0f, 1f), EPS);
    }

    @Test @DisplayName("сопротивление тормозит: pending=1, factor=1 → ×0.5 → 0.25")
    void resistanceBrakes() {
        assertEquals(0.25f, rate(1f, 1f, 1f, new float[]{2f}, 4f, 10f, 1f, 1f), EPS);
    }

    @Test @DisplayName("монотонность: больше pending → меньше темп")
    void resistanceMonotone() {
        assertTrue(rate(1f, 1f, 1f, new float[]{2f}, 4f, 10f, 3f, 1f)
                 < rate(1f, 1f, 1f, new float[]{2f}, 4f, 10f, 1f, 1f));
    }

    @Test @DisplayName("resistance_multiplier остаётся в (0,1] даже при огромном pending")
    void resistanceBounded() {
        float huge = rate(1f, 1f, 1f, new float[]{2f}, 4f, 10f, 1e6f, 1f);
        assertTrue(huge > 0f, "не уходит в ноль/отрицательное");
        assertTrue(huge < rate(1f, 1f, 1f, new float[]{2f}, 4f, 10f, 0f, 1f));
    }

    @Test @DisplayName("пол MIN_HARDNESS спасает от деления на ~0 (h=0 → 0.1)")
    void hardnessFloor() {
        // (1)/(2*0.1) = 5
        assertEquals(5f, rate(1f, 1f, 1f, new float[]{0f, 0f}, 4f, 10f, 0f, 1f), EPS);
    }

    @Test @DisplayName("кламп сверху RATE_MAX")
    void clampMax() {
        assertEquals(1.0f, rate(4f, 9f, 1f, new float[]{1f}, 256f, 1.0f, 0f, 0.5f), EPS);
    }

    @Test @DisplayName("монотонность по материалу и давлению")
    void materialAndPressure() {
        assertTrue(rate(2f, 1f, 1f, new float[]{2f}, 4f, 10f, 0f, 1f) > rate(1f, 1f, 1f, new float[]{2f}, 4f, 10f, 0f, 1f));
        assertTrue(rate(1f, 1f, 0.5f, new float[]{2f}, 4f, 10f, 0f, 1f) < rate(1f, 1f, 1f, new float[]{2f}, 4f, 10f, 0f, 1f));
    }

    @Test @DisplayName("n в знаменателе: больше блоков той же твёрдости → ниже темп")
    void nDenominator() {
        assertTrue(rate(1f, 1f, 1f, new float[]{2f, 2f}, 4f, 10f, 0f, 1f)
                 < rate(1f, 1f, 1f, new float[]{2f}, 4f, 10f, 0f, 1f));
    }

    @Test @DisplayName("RPM=0 → темп 0 (мультипликативно через effective_speed)")
    void zeroRpm() {
        assertEquals(0f, rate(4f, 9f, 1f, new float[]{2f}, 0f, 10f, 0f, 1f), EPS);
    }
}
