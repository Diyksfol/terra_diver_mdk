package com.example.terradiver.physics;

/*
 * Ambient Depth Signal (TD_01) — идемпотентная машина состояний непрерывного атмосферного отклика
 * корпуса (скрежет/вибрация на глубине в Dive Mode). См. TD_06 v1.0, ensure_signal_active /
 * ensure_signal_stopped.
 *
 * Логика старт/обновление/стоп чистая и тестируется без Minecraft. Само воспроизведение — за сёмой
 * AmbientChannel: конкретный движок (Sable-вибрация или звук) в спеке не зафиксирован, потому
 * абстрагирован; его реализация подключается и проверяется в игре.
 */
public final class AmbientSignal {

    private AmbientSignal() {}

    /*
     * Абстрактный ambient-канал. Реализация (звук/вибрация Sable) — TODO[API-CHECK], проверяется в игре.
     *   isPlaying  — играет ли цикл сейчас
     *   startLoop  — запустить непрерывный цикл
     *   setVolume  — плавно обновить громкость (без рывка)
     *   fadeStop   — плавно затухить и остановить (не резкий обрыв)
     */
    public interface AmbientChannel {
        boolean isPlaying();
        void startLoop();
        void setVolume(float volume);
        void fadeStop();
    }

    /*
     * Идемпотентно запустить/обновить сигнал. Безопасно звать каждый тик: если уже играет —
     * не перезапускает (не «заикается»), только обновляет громкость. См. TD_06 v1.0, ensure_signal_active.
     */
    public static void ensure_signal_active(float y, float deepslateY, boolean diveActive,
                                            AmbientChannel channel, float volume) {
        boolean active = diveActive && y <= deepslateY; // И Dive Mode, И в зоне давления
        if (active) {
            if (!channel.isPlaying()) {
                channel.startLoop(); // запуск только если ещё не играет (идемпотентность)
            }
            channel.setVolume(volume); // плавное обновление громкости каждый тик
        } else {
            ensure_signal_stopped(channel); // гейт ложен → делегируем остановку (не дублируем здесь)
        }
    }

    /*
     * Идемпотентно остановить сигнал с затуханием. Безопасно звать каждый тик в неактивном
     * состоянии. Условия активности НЕ проверяет — это дело ensure_signal_active.
     * См. TD_06 v1.0, ensure_signal_stopped.
     */
    public static void ensure_signal_stopped(AmbientChannel channel) {
        if (channel.isPlaying()) {
            channel.fadeStop();
        }
    }
}
