package com.example.terradiver.drilling;

import java.util.Optional;

/*
 * detect_drill_mode (TD_01) — выбор режима бурения на тике с гистерезисом входа/удержания
 * (только для продвижения) и проверкой согласованности вращения. См. TD_06 v1.1, detect_drill_mode.
 *
 * Чистая машина состояний. consistentSign из check_crown_rotation_consistency (Domain 0):
 * пусто — рассогласование/стоп; >0 — все CW; <0 — все CCW.
 */
public final class DrillModeDetector {

    private DrillModeDetector() {}

    public static DrillMode detect_drill_mode(Optional<Integer> consistentSign, float speed,
                                              boolean wasForwardLastTick, boolean hasDiggableNearby,
                                              float diveTriggerSpeed, float diveHoldSpeed) {
        if (consistentSign == null || consistentSign.isEmpty()) {
            return DrillMode.INACTIVE; // рассогласование/стоп → штатная физика
        }
        int sign = consistentSign.get();
        if (sign < 0) {
            // CCW: бур на месте. Скоростного порога нет — режим стационарный.
            return hasDiggableNearby ? DrillMode.DRILLING_STATIONARY : DrillMode.INACTIVE;
        }
        if (sign == 0) {
            return DrillMode.INACTIVE; // защитный случай (consistency обычно даёт пусто при стопе)
        }
        // CW: продвигающее бурение с гистерезисом.
        if (!wasForwardLastTick) {
            return (hasDiggableNearby && speed >= diveTriggerSpeed)   // вход
                ? DrillMode.DRILLING_FORWARD : DrillMode.INACTIVE;
        }
        return speed >= diveHoldSpeed                                 // удержание (низкий порог)
            ? DrillMode.DRILLING_FORWARD : DrillMode.INACTIVE;
    }
}