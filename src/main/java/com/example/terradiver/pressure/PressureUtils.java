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

    /*
     * compute_pressure_debuff_effective — итоговый множитель скорости бурения с учётом давления
     * и компенсации рёбрами жёсткости. См. TD_06 v1.0, compute_pressure_debuff_effective.
     *
     * Кламп 0.1 применяется ЗДЕСЬ, после компенсации (не в compute_raw_debuff) — иначе показания
     * Pressure Gauge (TD_04) исказятся артефактом клампа rate-системы.
     */
    public static float compute_pressure_debuff_effective(float rawDebuff, float ribCoverage,
                                                          float maxCompensation) {
        float penalty = 1f - rawDebuff;                                  // штраф давления
        float compensated = penalty * (1f - ribCoverage * maxCompensation); // рёбра гасят часть штрафа
        float result = 1f - compensated;
        return Math.max(0.1f, Math.min(1.0f, result));
    }

    /*
     * compute_ambient_signal_volume — громкость атмосферного скрежета (Ambient Depth Signal)
     * по глубине и покрытию рёбрами. См. TD_06 v1.0, compute_ambient_signal_volume.
     *
     * Сама функция не решает, активен ли сигнал — это делает вызывающий код (Y <= DEEPSLATE_Y И
     * Dive Mode); здесь честный ноль на нерелевантной глубине.
     */
    public static float compute_ambient_signal_volume(float y, float deepslateY, float bedrockY,
                                                      float ribCoverage, float soundDamping) {
        float depth = Math.max(0f, deepslateY - y);
        float maxDepth = deepslateY - bedrockY;
        float depthFraction = Math.min(1.0f, depth / maxDepth);

        // f(depth_fraction): конкретная кривая — балансовый выбор TD_05. Линейный ориентир (raw =
        // depth_fraction); если на плейтесте выберут степенную — менять только эту строку.
        float rawVolume = depthFraction;

        float volume = rawVolume * (1f - ribCoverage * soundDamping); // рёбра приглушают скрежет
        return Math.max(0.0f, Math.min(1.0f, volume));
    }
}