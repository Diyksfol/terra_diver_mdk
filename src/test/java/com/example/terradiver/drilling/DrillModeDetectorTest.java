package com.example.terradiver.drilling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.example.terradiver.drilling.DrillMode.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * Юнит-тесты detect_drill_mode (TD_01). См. TD_06 v1.1. Чистая логика, без Minecraft-runtime.
 */
@DisplayName("DrillModeDetector — detect_drill_mode")
class DrillModeDetectorTest {

    private static final float TRIGGER = 1.0f;
    private static final float HOLD = 0.5f;

    private static DrillMode mode(Optional<Integer> sign, float speed, boolean wasFwd, boolean diggable) {
        return DrillModeDetector.detect_drill_mode(sign, speed, wasFwd, diggable, TRIGGER, HOLD);
    }

    @Test @DisplayName("рассогласование (empty) → INACTIVE")
    void misaligned() { assertEquals(INACTIVE, mode(Optional.empty(), 2f, true, true)); }

    @Test @DisplayName("null sign → INACTIVE (без NPE)")
    void nullSign() { assertEquals(INACTIVE, mode(null, 2f, true, true)); }

    @Test @DisplayName("CCW + diggable → DRILLING_STATIONARY (без скоростного порога)")
    void ccwStationary() { assertEquals(DRILLING_STATIONARY, mode(Optional.of(-1), 0f, false, true)); }

    @Test @DisplayName("CCW без diggable → INACTIVE")
    void ccwNoDiggable() { assertEquals(INACTIVE, mode(Optional.of(-1), 5f, false, false)); }

    @Test @DisplayName("CW вход: diggable + speed>=trigger → DRILLING_FORWARD")
    void cwEnter() { assertEquals(DRILLING_FORWARD, mode(Optional.of(1), TRIGGER, false, true)); }

    @Test @DisplayName("CW вход: speed<trigger → INACTIVE")
    void cwEnterTooSlow() { assertEquals(INACTIVE, mode(Optional.of(1), 0.9f, false, true)); }

    @Test @DisplayName("CW вход: нет diggable → INACTIVE")
    void cwEnterNoDiggable() { assertEquals(INACTIVE, mode(Optional.of(1), 2f, false, false)); }

    @Test @DisplayName("CW удержание: speed между hold и trigger → DRILLING_FORWARD (гистерезис)")
    void cwHoldHysteresis() { assertEquals(DRILLING_FORWARD, mode(Optional.of(1), 0.7f, true, false)); }

    @Test @DisplayName("CW удержание: speed<hold → INACTIVE (выход)")
    void cwHoldExit() { assertEquals(INACTIVE, mode(Optional.of(1), 0.4f, true, true)); }

    @Test @DisplayName("CW удержание: diggable не нужен")
    void cwHoldDiggableIrrelevant() { assertEquals(DRILLING_FORWARD, mode(Optional.of(1), HOLD, true, false)); }
}
