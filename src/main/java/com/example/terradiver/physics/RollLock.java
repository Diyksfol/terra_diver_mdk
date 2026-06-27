package com.example.terradiver.physics;

/*
 * lock_roll_for_segment (TD_01) — зафиксировать крен штуковины в начале сегмента бурения и
 * удерживать до конца сегмента (тангаж и курс свободны). Блокирует «бочку» во время бурения.
 * См. TD_06, lock_roll_for_segment.
 *
 * Развязка: расчёт отклонения крена и корректирующего момента — чистое ядро (тестируется без
 * Minecraft, с корректной обработкой перехода через ±180°). Приложение УГЛОВОГО импульса — за
 * сёмой: точный метод углового импульса в API Sable не подтверждён (аналог applyLinearImpulse,
 * но для момента), помечен TODO[API-CHECK].
 */
public final class RollLock {

    private RollLock() {}

    private static final int ROLL = 2; // gimbal_angles = [pitch, yaw, roll]

    // Дефолты (ориентир; финальные значения — TD_05).
    private static final float DEFAULT_DEAD_ZONE = 1.0f; // не корректировать шум физики ниже этого
    private static final float DEFAULT_GAIN = 0.1f;      // сила восстанавливающего момента на градус

    /*
     * Сёма приложения корректирующего момента крена. Production: rigid_body.applyAngularImpulse
     * вокруг продольной оси (оси крена). TODO[API-CHECK]: точный метод углового импульса не подтверждён.
     */
    @FunctionalInterface
    public interface AngularImpulseApplier {
        void applyRollTorque(Object rigidBody, float torque);
    }

    private static final AngularImpulseApplier DEFAULT_APPLIER = (rigidBody, torque) -> {
        // TODO[API-CHECK]: rigidBody.applyAngularImpulse вокруг продольной (roll) оси на величину torque
    };

    /*
     * Чистое ядро: корректирующий момент крена. 0, если отклонение в мёртвой зоне (шум физики).
     * Иначе восстанавливающий момент (против отклонения). Отклонение берётся по кратчайшей дуге,
     * чтобы переход через ±180° не давал ложный «полный оборот».
     * Углы в градусах (предположение для gimbal-сенсора; при радианах сменить wrap-период).
     */
    public static float computeRollCorrection(float currentRoll, float lockedRoll,
                                              float deadZone, float gain) {
        float deviation = wrapDegrees(currentRoll - lockedRoll);
        if (Math.abs(deviation) <= deadZone) {
            return 0f; // микроотклонение — не дёргаем тело
        }
        return -deviation * gain; // восстанавливаем к зафиксированному крену
    }

    // Кратчайшая знаковая разница углов → [-180, 180].
    private static float wrapDegrees(float angle) {
        float r = (angle + 180f) % 360f;
        if (r < 0f) {
            r += 360f; // Java % может дать отрицательное — нормализуем
        }
        return r - 180f;
    }

    /*
     * Production-сигнатура (TD_06). segmentLockedRoll == null → начало сегмента: фиксируем текущий
     * крен. Иначе удерживаем зафиксированный, прикладывая корректирующий момент при отклонении.
     * Тангаж и курс не трогаются. Возвращает крен, на котором сегмент зафиксирован (в состояние).
     */
    public static float lock_roll_for_segment(float[] gimbalAngles, Float segmentLockedRoll,
                                              Object rigidBody) {
        return lock_roll_for_segment(gimbalAngles, segmentLockedRoll, rigidBody,
            DEFAULT_DEAD_ZONE, DEFAULT_GAIN, DEFAULT_APPLIER);
    }

    /* Перегрузка с явными константами и сёмой — для тестов и подключения реального Sable. */
    public static float lock_roll_for_segment(float[] gimbalAngles, Float segmentLockedRoll,
                                              Object rigidBody, float deadZone, float gain,
                                              AngularImpulseApplier applier) {
        float currentRoll = gimbalAngles[ROLL];
        float lockedRoll = (segmentLockedRoll == null) ? currentRoll : segmentLockedRoll; // фиксация в начале
        float torque = computeRollCorrection(currentRoll, lockedRoll, deadZone, gain);
        if (torque != 0f) {
            applier.applyRollTorque(rigidBody, torque); // только при отклонении вне мёртвой зоны
        }
        return lockedRoll;
    }
}
