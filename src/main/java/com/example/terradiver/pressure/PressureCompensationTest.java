package com.example.terradiver.pressure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Юнит-тесты компенсации давления (TD_03): compute_pressure_debuff_effective и
 * compute_ambient_signal_volume. См. TD_06 v1.0. Чистая арифметика, без MC-runtime.
 */
@DisplayName("PressureUtils — компенсация и сигнал")
class PressureCompensationTest {

    private static final float EPS = 1e-6f;

    @Nested
    @DisplayName("compute_pressure_debuff_effective")
    class Effective {
        @Test @DisplayName("raw=1 (выше зоны давления) → 1.0 независимо от рёбер")
        void noPressure() {
            assertEquals(1.0f, PressureUtils.compute_pressure_debuff_effective(1.0f, 0f, 1f), EPS);
            assertEquals(1.0f, PressureUtils.compute_pressure_debuff_effective(1.0f, 1f, 1f), EPS);
        }

        @Test @DisplayName("rib=1 (полное покрытие) → 1.0 даже при максимальном давлении")
        void fullCoverage() {
            assertEquals(1.0f, PressureUtils.compute_pressure_debuff_effective(0.1f, 1.0f, 1f), EPS);
        }

        @Test @DisplayName("половинное покрытие — частичная компенсация (0.5/0.5 → 0.75)")
        void partial() {
            assertEquals(0.75f, PressureUtils.compute_pressure_debuff_effective(0.5f, 0.5f, 1f), EPS);
        }

        @Test @DisplayName("нижний кламп 0.1 (максимальное давление, нет рёбер)")
        void floorClamp() {
            assertEquals(0.1f, PressureUtils.compute_pressure_debuff_effective(0.0f, 0.0f, 1f), EPS);
        }

        @Test @DisplayName("всегда в [0.1, 1.0]")
        void bounds() {
            for (float r : new float[]{0f, 0.3f, 0.7f, 1f}) {
                for (float c : new float[]{0f, 0.5f, 1f}) {
                    float v = PressureUtils.compute_pressure_debuff_effective(r, c, 1f);
                    assertTrue(v >= 0.1f && v <= 1.0f, "v=" + v);
                }
            }
        }
    }

    @Nested
    @DisplayName("compute_ambient_signal_volume")
    class Volume {
        private static final float DEEP = 8f, BED = -64f;

        @Test @DisplayName("Y выше deepslate → 0 (глубина 0)")
        void aboveZone() {
            assertEquals(0f, PressureUtils.compute_ambient_signal_volume(20f, DEEP, BED, 0f, 0.5f), EPS);
            assertEquals(0f, PressureUtils.compute_ambient_signal_volume(DEEP, DEEP, BED, 0f, 0.5f), EPS);
        }

        @Test @DisplayName("у Bedrock без рёбер → 1.0")
        void atBedrock() {
            assertEquals(1.0f, PressureUtils.compute_ambient_signal_volume(BED, DEEP, BED, 0f, 0.5f), EPS);
        }

        @Test @DisplayName("Bedrock + полные рёбра + damping 1 → ~0 (укреплён не скрипит)")
        void dampened() {
            assertEquals(0f, PressureUtils.compute_ambient_signal_volume(BED, DEEP, BED, 1.0f, 1.0f), EPS);
        }

        @Test @DisplayName("глубже Bedrock → depth_fraction зажат 1.0")
        void belowBedrock() {
            assertEquals(1.0f, PressureUtils.compute_ambient_signal_volume(-100f, DEEP, BED, 0f, 0.5f), EPS);
        }

        @Test @DisplayName("всегда в [0, 1]")
        void bounds() {
            for (float y : new float[]{10f, 0f, -30f, -64f, -100f}) {
                for (float c : new float[]{0f, 0.5f, 1f}) {
                    float v = PressureUtils.compute_ambient_signal_volume(y, DEEP, BED, c, 0.5f);
                    assertTrue(v >= 0f && v <= 1f, "v=" + v);
                }
            }
        }
    }
}
