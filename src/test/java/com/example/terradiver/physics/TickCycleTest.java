package com.example.terradiver.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Юнит-тесты оркестрации тик-цикла (TD_01). См. TD_06. Проверяют ВЕТВЛЕНИЕ, ПОРЯДОК вызовов и
 * ПЕРЕХОД состояния — формулы протестированы каждая отдельно. Эффекты подменены фейк-Context;
 * Minecraft-runtime не нужен (чистая оркестрация над POJO/примитивами).
 */
@DisplayName("TickCycle — advanceTick")
class TickCycleTest {

    private static final float EPS = 1e-4f;

    // Фейк эффектов: журнал вызовов в порядке + зафиксированные аргументы.
    static final class FakeContext implements TickCycle.Context {
        final List<String> calls = new ArrayList<>();
        int processedToReturn = 4;
        Float lockRollToReturn = 7f;
        Integer clearMax = null;
        Float forwardApplied = null;
        Vec3 pushDir = null;
        Float pushMag = null;
        Float signalVolume = null;

        public int clearBlocks(int max) { calls.add("clear"); clearMax = max; return processedToReturn; }
        public Float lockRoll(Float locked) { calls.add("lock"); return lockRollToReturn; }
        public void applyForwardImpulse(float rate) { calls.add("forward"); forwardApplied = rate; }
        public void applyPushbackImpulse(Vec3 dir, float mag) { calls.add("push"); pushDir = dir; pushMag = mag; }
        public void signalActive(float y, float deep, float vol) { calls.add("sigOn"); signalVolume = vol; }
        public void signalStopped() { calls.add("sigOff"); }
    }

    private static TickCycle.Constants constants() {
        return new TickCycle.Constants(
            /*diveTrigger*/ 1f, /*diveHold*/ 0.5f,
            /*minBlocks*/ 1,
            /*pendingGrowth*/ 0.5f, /*pendingDecay*/ 0.3f, /*pendingMax*/ 10f,
            /*maxCompensation*/ 0.5f,
            /*minHardness*/ 0.1f, /*rateMax*/ 10f, /*pendingResistanceFactor*/ 0.5f,
            /*pushbackThreshold*/ 2f, /*pushbackFactor*/ 0.5f,
            /*soundDamping*/ 0.5f,
            /*deepslateY*/ 8f, /*bedrockY*/ -64f, /*pressureK*/ 0.65f, /*pressureScale*/ 20f);
    }

    // База: CW, скорость выше порога, порода спереди (+z → выталкивание назад).
    private static TickCycle.Inputs inputs(Optional<Integer> sign, int foundCount) {
        return new TickCycle.Inputs(
            sign, /*speed*/ 2f, /*hasDiggableNearby*/ true,
            /*effectiveSpeed*/ 64f, /*rpmInput*/ 256f, foundCount,
            /*avgMaterialFactor*/ 2f, /*crownFaceArea*/ 9f, /*hardnesses*/ new float[]{3f, 3f, 3f},
            /*y*/ -30f, /*ribCoverage*/ 0f,
            /*crownCenter*/ Vec3.ZERO, /*contactSolids*/ List.of(new BlockPos(0, 0, 2)),
            /*gimbalAngles*/ new float[]{0f, 0f, 0f});
    }

    @Test @DisplayName("FORWARD: порядок clear→lock→forward→sigOn; состояние (rate, pending, locked, wasForward)")
    void forwardBasic() {
        FakeContext ctx = new FakeContext();
        TickCycle.State out = TickCycle.advanceTick(
            TickCycle.State.initial(), inputs(Optional.of(1), 5), constants(), ctx);

        assertEquals(List.of("clear", "lock", "forward", "sigOn"), ctx.calls);
        assertEquals(64, ctx.clearMax);                 // compute_max_blocks_per_tick(0,64,2,1)=64
        assertEquals(10f, ctx.forwardApplied, EPS);     // темп зажат RATE_MAX
        assertEquals(0.5f, out.pendingResistance(), EPS); // deficit 5-4=1 → 0.5
        assertEquals(10f, out.previousForwardRate(), EPS);
        assertEquals(7f, out.segmentLockedRoll(), EPS);  // вернула фейк-lockRoll
        assertTrue(out.wasForwardLastTick());
    }

    @Test @DisplayName("FORWARD + большой недогруз → выталкивание срабатывает в хвосте ветки A")
    void forwardWithPushback() {
        FakeContext ctx = new FakeContext();
        TickCycle.State out = TickCycle.advanceTick(
            TickCycle.State.initial(), inputs(Optional.of(1), 10), constants(), ctx); // found 10, processed 4 → deficit 6

        assertEquals(3.0f, out.pendingResistance(), EPS); // 6 * 0.5
        assertTrue(ctx.calls.contains("push"), "выталкивание есть и в ветке продвижения");
        assertEquals(0.5f, ctx.pushMag, EPS);             // (3.0 - 2) * 0.5
        assertEquals(new Vec3(0, 0, -1), ctx.pushDir);    // порода спереди → назад
        assertEquals(List.of("clear", "lock", "forward", "sigOn", "push"), ctx.calls);
    }

    @Test @DisplayName("STATIONARY: clear+sigOn, БЕЗ lock/forward/push даже при высоком pending")
    void stationaryNoTail() {
        FakeContext ctx = new FakeContext();
        TickCycle.State start = new TickCycle.State(0f, 5f, 5f, true);
        TickCycle.State out = TickCycle.advanceTick(start, inputs(Optional.of(-1), 5), constants(), ctx);

        assertEquals(List.of("clear", "sigOn"), ctx.calls); // хвоста нет
        assertFalse(ctx.calls.contains("push"), "в стационаре выталкивания нет, несмотря на pending 5");
        assertEquals(0f, out.previousForwardRate(), EPS);
        assertEquals(4.7f, out.pendingResistance(), EPS);   // 5 - 0.3
        assertNull(out.segmentLockedRoll(), "сегмент продвижения окончен → крен сброшен");
        assertFalse(out.wasForwardLastTick());
    }

    @Test @DisplayName("INACTIVE + остаточный pending → sigOff затем выталкивание")
    void inactiveWithPushback() {
        FakeContext ctx = new FakeContext();
        TickCycle.State start = new TickCycle.State(0f, 5f, 5f, true);
        TickCycle.State out = TickCycle.advanceTick(start, inputs(Optional.empty(), 5), constants(), ctx);

        assertEquals(List.of("sigOff", "push"), ctx.calls);
        assertEquals(1.35f, ctx.pushMag, EPS);              // (4.7 - 2) * 0.5
        assertEquals(0f, out.previousForwardRate(), EPS);
        assertEquals(4.7f, out.pendingResistance(), EPS);
        assertNull(out.segmentLockedRoll());
        assertFalse(out.wasForwardLastTick());
    }

    @Test @DisplayName("INACTIVE + малый pending → только sigOff (выталкивание ниже порога)")
    void inactiveCoolDown() {
        FakeContext ctx = new FakeContext();
        TickCycle.State start = new TickCycle.State(0f, 1f, null, false);
        TickCycle.advanceTick(start, inputs(Optional.empty(), 5), constants(), ctx);

        assertEquals(List.of("sigOff"), ctx.calls); // 1-0.3=0.7 < порог 2 → mag 0
    }

    @Test @DisplayName("INACTIVE + нет контакта породы → нет выталкивания даже при pending выше порога")
    void inactiveNoContactNoPush() {
        FakeContext ctx = new FakeContext();
        TickCycle.State start = new TickCycle.State(0f, 5f, null, false);
        TickCycle.Inputs in = new TickCycle.Inputs(
            Optional.empty(), 2f, true, 64f, 256f, 5, 2f, 9f, new float[]{3f, 3f, 3f},
            -30f, 0f, Vec3.ZERO, List.of(), new float[]{0f, 0f, 0f}); // contactSolids пуст → dir ZERO
        TickCycle.advanceTick(start, in, constants(), ctx);

        assertEquals(List.of("sigOff"), ctx.calls); // направление ZERO → толчка нет
    }

    @Test @DisplayName("гистерезис удержания: был FORWARD, скорость между hold и trigger → остаётся FORWARD")
    void forwardHold() {
        FakeContext ctx = new FakeContext();
        TickCycle.State start = new TickCycle.State(5f, 0f, 3f, true); // wasForward=true
        TickCycle.Inputs in = new TickCycle.Inputs(
            Optional.of(1), 0.7f, true, 64f, 256f, 5, 2f, 9f, new float[]{3f, 3f, 3f},
            -30f, 0f, Vec3.ZERO, List.of(new BlockPos(0, 0, 2)), new float[]{0f, 0f, 0f});
        TickCycle.State out = TickCycle.advanceTick(start, in, constants(), ctx);

        assertTrue(out.wasForwardLastTick(), "0.7 ниже trigger 1, но >= hold 0.5 → удержание");
        assertTrue(ctx.calls.contains("forward"));
    }
}
