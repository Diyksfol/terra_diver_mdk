package com.example.terradiver.physics;

import com.example.terradiver.physics.AmbientSignal.AmbientChannel;
import net.minecraft.world.phys.Vec3;

/*
 * СКЕЛЕТ адаптера Context для тик-цикла — реализует эффекты в мире поверх готовых функций.
 * Это твоя точка интеграции: заполни поля хэндлами из BlockEntity подшипника и закрой
 * пометки TODO[API-CHECK], когда подтвердишь точные вызовы Offroad/Sable/SubLevel.
 *
 * Что держит адаптер (ставится при сборке короны / каждый тик перед advanceTick):
 *   heading         — get_heading(rotation), направление носа (для тяги вперёд)
 *   currentVelocity — velocity_sensor, текущая скорость тела (для apply_drilling_velocity)
 *   rigidBody       — хэндл тела Sable (линейный и угловой импульс)
 *   subLevel/buffer — для clear_blocks (доступ к блокам мира штуковины + инвентарь крепления)
 *   channel         — AmbientChannel (звук/вибрация), переживает между тиками
 *   miningCandidates — список от движка Offroad за этот тик
 *
 * НЕ компилируется как есть — это каркас: типы хэндлов и вызовы помечены TODO.
 */
public final class DriveContextAdapter implements TickCycle.Context {

    private Vec3 heading;
    private Vec3 currentVelocity;
    private Object rigidBody;        // TODO[API-CHECK]: тип тела Sable
    private Object subLevel;         // TODO[API-CHECK]: доступ к блокам мира штуковины
    private Object buffer;           // TODO[API-CHECK]: инвентарь крепления (CrownBuffer-совместимый)
    private Object miningCandidates; // от Offroad
    private AmbientChannel channel;  // переживает между тиками, держится в BlockEntity

    private final float deepslateY;  // из ModConfig
    // ... остальные константы Sound/импульса по необходимости

    public DriveContextAdapter(float deepslateY) {
        this.deepslateY = deepslateY;
    }

    // Каждый тик до advanceTick: обновить изменяемые хэндлы.
    public void refresh(Vec3 heading, Vec3 currentVelocity, Object miningCandidates) {
        this.heading = heading;
        this.currentVelocity = currentVelocity;
        this.miningCandidates = miningCandidates;
    }

    @Override
    public int clearBlocks(int maxBlocksPerTick) {
        // return BlockClearer.clear_blocks(miningCandidates, subLevel, buffer, maxBlocksPerTick);
        // TODO[API-CHECK]: source mining_candidates + доступ к блокам SubLevel (Проверка B)
        return 0;
    }

    @Override
    public Float lockRoll(Float segmentLockedRoll) {
        // float[] gimbal = gimbalSensor.getAngles();
        // return RollLock.lock_roll_for_segment(gimbal, segmentLockedRoll, rigidBody);
        // TODO[API-CHECK]: угловой импульс через rigidBody
        return segmentLockedRoll;
    }

    @Override
    public void applyForwardImpulse(float forwardRate) {
        // DrillingVelocity.apply_drilling_velocity(forwardRate, heading, rigidBody, currentVelocity, true, ...);
        // TODO[API-CHECK]: applyLinearImpulse + масса тела из Sable
    }

    @Override
    public void applyPushbackImpulse(Vec3 direction, float magnitude) {
        // DrillingVelocity.apply_drilling_velocity(magnitude, direction, rigidBody, currentVelocity, false, ...);
        // Направление-агностично: тот же метод, что и тяга вперёд, но direction = выталкивание.
    }

    @Override
    public void signalActive(float y, float deepslateY, float volume) {
        AmbientSignal.ensure_signal_active(y, deepslateY, true, channel, volume);
    }

    @Override
    public void signalStopped() {
        AmbientSignal.ensure_signal_stopped(channel);
    }
}
