package com.example.terradiver.physics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Юнит-тесты lock_roll_for_segment (TD_01). См. TD_06. Чистая логика крена (с обёрткой ±180°),
 * без Minecraft-runtime; приложение момента проверяется фейк-сёмой.
 */
@DisplayName("RollLock — lock_roll_for_segment")
class RollLockTest {

    private static final float EPS = 1e-6f;
    private static final float DEAD = 3f, GAIN = 1f;

    @Nested
    @DisplayName("computeRollCorrection (ядро)")
    class Correction {
        @Test @DisplayName("в мёртвой зоне → 0")
        void deadZone() { assertEquals(0f, RollLock.computeRollCorrection(2f, 0f, DEAD, GAIN), EPS); }

        @Test @DisplayName("на границе мёртвой зоны → 0")
        void boundary() { assertEquals(0f, RollLock.computeRollCorrection(3f, 0f, DEAD, GAIN), EPS); }

        @Test @DisplayName("крен ушёл вверх → восстанавливающий момент вниз")
        void positiveDeviation() { assertEquals(-10f, RollLock.computeRollCorrection(10f, 0f, DEAD, GAIN), EPS); }

        @Test @DisplayName("крен ушёл вниз → момент вверх")
        void negativeDeviation() { assertEquals(10f, RollLock.computeRollCorrection(-10f, 0f, DEAD, GAIN), EPS); }

        @Test @DisplayName("переход через ±180: locked 170, cur −170 → кратчайшее +20 → момент −20")
        void wrapAround() { assertEquals(-20f, RollLock.computeRollCorrection(-170f, 170f, DEAD, GAIN), EPS); }

        @Test @DisplayName("gain масштабирует момент")
        void gainScales() { assertEquals(-5f, RollLock.computeRollCorrection(10f, 0f, DEAD, 0.5f), EPS); }
    }

    @Nested
    @DisplayName("lock_roll_for_segment (оркестрация)")
    class Segment {
        /** Фейк сёмы: фиксирует приложенный момент (или его отсутствие). */
        static final class FakeApplier implements RollLock.AngularImpulseApplier {
            Float applied = null;
            public void applyRollTorque(Object rb, float torque) { applied = torque; }
        }

        @Test @DisplayName("начало сегмента (null): фиксирует текущий крен, момент не прикладывается")
        void segmentStart() {
            FakeApplier f = new FakeApplier();
            float locked = RollLock.lock_roll_for_segment(new float[]{0, 0, 45f}, null, new Object(), DEAD, GAIN, f);
            assertEquals(45f, locked, EPS);
            assertNull(f.applied, "в момент фиксации отклонения нет → импульса нет");
        }

        @Test @DisplayName("крен ушёл за мёртвую зону → корректирующий момент, locked не меняется")
        void corrects() {
            FakeApplier f = new FakeApplier();
            float locked = RollLock.lock_roll_for_segment(new float[]{0, 0, 55f}, 45f, new Object(), DEAD, GAIN, f);
            assertEquals(45f, locked, EPS);
            assertNotNull(f.applied);
            assertEquals(-10f, f.applied, EPS);
        }

        @Test @DisplayName("крен в мёртвой зоне → импульс не прикладывается")
        void withinDeadZone() {
            FakeApplier f = new FakeApplier();
            float locked = RollLock.lock_roll_for_segment(new float[]{0, 0, 46f}, 45f, new Object(), DEAD, GAIN, f);
            assertEquals(45f, locked, EPS);
            assertNull(f.applied);
        }

        @Test @DisplayName("тангаж и курс не влияют (меняем pitch/yaw — результат тот же)")
        void pitchYawIrrelevant() {
            FakeApplier f = new FakeApplier();
            float locked = RollLock.lock_roll_for_segment(new float[]{90f, 180f, 45f}, 45f, new Object(), DEAD, GAIN, f);
            assertEquals(45f, locked, EPS);
            assertNull(f.applied, "крен не отклонён → момента нет независимо от pitch/yaw");
        }
    }
}
