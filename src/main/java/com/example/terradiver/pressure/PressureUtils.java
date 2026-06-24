package com.example.terradiver.pressure;

/**
 * Утилиты давления среды (TD_03). Чистые функции, не зависят ни от одного другого домена.
 * Спецификация — TD_06 v1.0, Домен TD_03.
 */
public class PressureUtils {

    private PressureUtils() {}

    /**
     * compute_raw_debuff() — давление среды на текущей глубине, без компенсации рёбрами.
     * Спецификация: TD_06 v1.0, TD_03.
     *
     * <p>Чистая функция одного аргумента (Y). Кэширование «последнего значения» целесообразно
     * в вызывающем коде (Y меняется медленно), здесь не реализуется — функция stateless.
     *
     * @param y          текущий мировой Y штуковины (altitude_sensor.getWorldHeight())
     * @param deepsLateY Y-граница зоны давления (TerrraDiverConfig.DEEPSLATE_Y)
     * @param bedrockY   нижняя граница диапазона (TerrraDiverConfig.BEDROCK_Y ≈ -60)
     * @param k          коэффициент логарифмической кривой (TerrraDiverConfig.PRESSURE_K)
     * @param scale      масштаб кривой (TerrraDiverConfig.PRESSURE_SCALE)
     * @return raw_debuff в [0.0, 1.0]; 1.0 = давления нет, ~0.0 у Bedrock
     */
    public static float compute_raw_debuff(float y, float deepsLateY, float bedrockY,
                                           float k, float scale) {
        // Шаг 1: глубина ниже границы deepslate. max(0,...) — граничный случай Y > DEEPSLATE_Y.
        // При Y выше зоны depth = 0, что немедленно даёт raw = 1.0 без отдельной ветки.
        float depth = Math.max(0f, deepsLateY - y);

        // Шаг 2: логарифмическая кривая убывания. ln(1 + 0) = 0 → raw = 1.0 (нет давления).
        // При больших depth raw уходит ниже 0 — клампить здесь (см. граничные случаи TD_06:
        // клампить после компенсации рёбрами — баг, искажающий показания Pressure Gauge).
        float raw = 1f - k * (float) Math.log(1.0 + depth / scale);

        // Шаг 3: клампим до использования в вышестоящих расчётах.
        return Math.max(0f, Math.min(1f, raw));
    }
}
