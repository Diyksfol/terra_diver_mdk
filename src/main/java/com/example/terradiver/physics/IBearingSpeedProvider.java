package com.example.terradiver.physics;

/**
 * Интерфейс чтения скорости подшипника (offroad:borehead_bearing).
 *
 * <p>Вынесен отдельно, чтобы:
 * <ul>
 *   <li>Изолировать неподтверждённое API в одном месте (TODO[API]).</li>
 *   <li>Позволить юнит-тестам подставить мок без рефлексии и Minecraft-окружения.</li>
 * </ul>
 *
 * <p>Реальная реализация: {@code BoreheadBearingSpeedProvider} (подключается после
 * подтверждения типа {@code BoreheadBearingBlockEntity}).
 * Спецификация — TD_06 v1.0 / {@code check_crown_rotation_consistency()}.
 */
@FunctionalInterface
public interface IBearingSpeedProvider {

    /**
     * Возвращает текущую скорость подшипника.
     * Знак: {@code > 0} — CW, {@code < 0} — CCW, {@code 0} — стоит.
     *
     * @param bearing ссылка на объект подшипника (Object, пока тип не подтверждён)
     * @return скорость в RPM (знаковая)
     */
    float getSpeed(Object bearing);
}
