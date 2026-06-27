package com.example.terradiver.drilling;

/*
 * Режим бурения на текущем тике (TD_01). См. TD_06 v1.1, detect_drill_mode.
 *   DRILLING_FORWARD    — CW: бур + продвижение вперёд
 *   DRILLING_STATIONARY — CCW: бур на месте, без движения (для разворота в породе)
 *   INACTIVE            — бурение не активно
 */
public enum DrillMode {
    DRILLING_FORWARD,
    DRILLING_STATIONARY,
    INACTIVE
}