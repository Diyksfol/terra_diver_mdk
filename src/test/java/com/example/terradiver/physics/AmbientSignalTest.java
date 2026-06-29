package com.example.terradiver.physics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Юнит-тесты Ambient Depth Signal (TD_01). См. TD_06 v1.0. Без Minecraft-runtime —
 * ambient-канал подменён фейком, фиксирующим вызовы.
 */
@DisplayName("AmbientSignal — ensure_signal_active / ensure_signal_stopped")
class AmbientSignalTest {

    /** Фейк канала: хранит playing и журнал действий. */
    static final class FakeChannel implements AmbientSignal.AmbientChannel {
        boolean playing;
        final List<String> log = new ArrayList<>();
        Float lastVolume = null;
        FakeChannel(boolean playing) { this.playing = playing; }
        public boolean isPlaying() { return playing; }
        public void startLoop() { playing = true; log.add("start"); }
        public void setVolume(float v) { lastVolume = v; log.add("vol"); }
        public void fadeStop() { playing = false; log.add("stop"); }
    }

    private static final float DEEP = 8f;

    @Test @DisplayName("вход (не играл, dive, в зоне) → старт + установка громкости")
    void enters() {
        FakeChannel c = new FakeChannel(false);
        AmbientSignal.ensure_signal_active(-10f, DEEP, true, c, 0.5f);
        assertEquals(List.of("start", "vol"), c.log);
        assertTrue(c.playing);
        assertEquals(0.5f, c.lastVolume);
    }

    @Test @DisplayName("идемпотентность: уже играет → без перезапуска, только громкость")
    void idempotentUpdate() {
        FakeChannel c = new FakeChannel(true);
        AmbientSignal.ensure_signal_active(-10f, DEEP, true, c, 0.7f);
        assertEquals(List.of("vol"), c.log);
        assertEquals(0.7f, c.lastVolume);
    }

    @Test @DisplayName("Dive Mode выключен при играющем → остановка")
    void diveOffStops() {
        FakeChannel c = new FakeChannel(true);
        AmbientSignal.ensure_signal_active(-10f, DEEP, false, c, 0.5f);
        assertEquals(List.of("stop"), c.log);
        assertFalse(c.playing);
    }

    @Test @DisplayName("выше зоны давления при играющем → остановка")
    void aboveZoneStops() {
        FakeChannel c = new FakeChannel(true);
        AmbientSignal.ensure_signal_active(20f, DEEP, true, c, 0.5f);
        assertEquals(List.of("stop"), c.log);
    }

    @Test @DisplayName("неактивно и не играл → тишина, никаких вызовов")
    void inactiveNoop() {
        FakeChannel c = new FakeChannel(false);
        AmbientSignal.ensure_signal_active(20f, DEEP, false, c, 0.5f);
        assertTrue(c.log.isEmpty());
    }

    @Test @DisplayName("граница зоны (Y == DEEPSLATE_Y) считается «в зоне»")
    void boundaryInclusive() {
        FakeChannel c = new FakeChannel(false);
        AmbientSignal.ensure_signal_active(DEEP, DEEP, true, c, 0.3f);
        assertTrue(c.playing);
    }

    @Test @DisplayName("ensure_signal_stopped: играет → затухание")
    void stopsWhenPlaying() {
        FakeChannel c = new FakeChannel(true);
        AmbientSignal.ensure_signal_stopped(c);
        assertEquals(List.of("stop"), c.log);
        assertFalse(c.playing);
    }

    @Test @DisplayName("ensure_signal_stopped: не играл → ничего (идемпотентность)")
    void stopIdempotent() {
        FakeChannel c = new FakeChannel(false);
        AmbientSignal.ensure_signal_stopped(c);
        assertTrue(c.log.isEmpty());
    }
}
