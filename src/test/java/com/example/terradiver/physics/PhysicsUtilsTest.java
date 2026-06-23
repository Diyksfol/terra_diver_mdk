package com.example.terradiver.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для Domain 0 primitives (PhysicsUtils).
 * Спецификация — TD_06 v1.0, Домен 0.
 */
@DisplayName("PhysicsUtils — Domain 0 Primitives")
class PhysicsUtilsTest {

    private List<CrownBlock> testCrowns;

    @BeforeEach
    void setUp() {
        testCrowns = new ArrayList<>();
    }

    // ──── get_heading() ────────────────────────────────────

    @Test
    @DisplayName("get_heading: null orientation → default forward (0,0,1)")
    void testGetHeadingNullOrientation() {
        Vec3 result = PhysicsUtils.get_heading(null);
        assertNotNull(result);
        assertEquals(0.0, result.x, 0.01);
        assertEquals(0.0, result.y, 0.01);
        assertEquals(1.0, result.z, 0.01);
    }

    @Test
    @DisplayName("get_heading: identity quaternion → forward unchanged (0,0,1)")
    void testGetHeadingIdentity() {
        Quaternionf identity = new Quaternionf(0f, 0f, 0f, 1f);
        Vec3 result = PhysicsUtils.get_heading(identity);
        assertNotNull(result);
        assertEquals(0.0, result.x, 0.01);
        assertEquals(0.0, result.y, 0.01);
        assertEquals(1.0, result.z, 0.01);
    }

    @Test
    @DisplayName("get_heading: 90° yaw (Y-axis) → heading points right (+X)")
    void testGetHeadingYawRotation() {
        // Поворот на 90° вокруг оси Y: q = (x=0, y=sin(π/4), z=0, w=cos(π/4))
        // Трансформация (0,0,1) → (1,0,0)
        float halfAngle = (float) (Math.PI / 4);
        Quaternionf yaw90 = new Quaternionf(
            0f,
            (float) Math.sin(halfAngle),
            0f,
            (float) Math.cos(halfAngle)
        ).normalize();
        Vec3 result = PhysicsUtils.get_heading(yaw90);
        assertNotNull(result);
        assertEquals(1.0, result.x, 0.01, "После поворота 90° вокруг Y должен смотреть вправо (x=1)");
        assertEquals(0.0, result.y, 0.01);
        assertEquals(0.0, result.z, 0.01);
    }

    @Test
    @DisplayName("get_heading: 90° pitch (X-axis) → heading points down (-Y)")
    void testGetHeadingPitchRotation() {
        // Поворот на 90° вокруг оси X (нос вниз): q = (x=sin(π/4), y=0, z=0, w=cos(π/4))
        // Трансформация (0,0,1) → (0,-1,0)
        float halfAngle = (float) (Math.PI / 4);
        Quaternionf pitch90 = new Quaternionf(
            (float) Math.sin(halfAngle),
            0f,
            0f,
            (float) Math.cos(halfAngle)
        ).normalize();
        Vec3 result = PhysicsUtils.get_heading(pitch90);
        assertNotNull(result);
        assertEquals(0.0,  result.x, 0.01);
        assertEquals(-1.0, result.y, 0.01, "После pitchDown 90° нос смотрит вниз (y=-1)");
        assertEquals(0.0,  result.z, 0.01);
    }

    @Test
    @DisplayName("get_heading: result is normalized (magnitude=1)")
    void testGetHeadingNormalized() {
        Quaternionf rot = new Quaternionf(0.1f, 0.2f, 0.3f, 0.9f).normalize();
        Vec3 result = PhysicsUtils.get_heading(rot);
        double magnitude = Math.sqrt(result.x * result.x + result.y * result.y + result.z * result.z);
        assertEquals(1.0, magnitude, 0.01, "Heading должен быть единичным вектором");
    }

    // ──── get_aligned_crowns() ────────────────────────────────────

    @Test
    @DisplayName("get_aligned_crowns: null список → пустой результат")
    void testGetAlignedCrownsNull() {
        List<CrownBlock> result = PhysicsUtils.get_aligned_crowns(null, new Vec3(0, 0, 1));
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("get_aligned_crowns: пустой список → пустой результат")
    void testGetAlignedCrownsEmpty() {
        List<CrownBlock> result = PhysicsUtils.get_aligned_crowns(new ArrayList<>(), new Vec3(0, 0, 1));
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("get_aligned_crowns: null heading → пустой результат")
    void testGetAlignedCrownsNullHeading() {
        testCrowns.add(new CrownBlock(BlockPos.ZERO, Direction.NORTH, 1.0f, 1.0f, 0.0f, null));
        List<CrownBlock> result = PhysicsUtils.get_aligned_crowns(testCrowns, null);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("get_aligned_crowns: корона NORTH, heading (0,0,-1) → включена")
    void testGetAlignedCrownsForwardMatch() {
        CrownBlock north = new CrownBlock(BlockPos.ZERO, Direction.NORTH, 1.0f, 1.0f, 0.0f, null);
        testCrowns.add(north);
        List<CrownBlock> result = PhysicsUtils.get_aligned_crowns(testCrowns, new Vec3(0, 0, -1));
        assertEquals(1, result.size());
        assertSame(north, result.get(0));
    }

    @Test
    @DisplayName("get_aligned_crowns: корона EAST, heading (0,0,1) → исключена (dot=0)")
    void testGetAlignedCrownsNoMatch() {
        CrownBlock east = new CrownBlock(BlockPos.ZERO, Direction.EAST, 1.0f, 1.0f, 0.0f, null);
        testCrowns.add(east);
        List<CrownBlock> result = PhysicsUtils.get_aligned_crowns(testCrowns, new Vec3(0, 0, 1));
        assertTrue(result.isEmpty(), "Корона EAST не совпадает с heading (0,0,1)");
    }

    @Test
    @DisplayName("get_aligned_crowns: смешанные направления → фильтрация по heading")
    void testGetAlignedCrownsMixed() {
        CrownBlock forward = new CrownBlock(BlockPos.ZERO,         Direction.NORTH, 1.0f, 1.0f, 0.0f, null);
        CrownBlock right   = new CrownBlock(new BlockPos(1, 0, 0), Direction.EAST,  1.0f, 1.0f, 0.0f, null);
        CrownBlock up      = new CrownBlock(new BlockPos(0, 1, 0), Direction.UP,    1.0f, 1.0f, 0.0f, null);
        testCrowns.add(forward);
        testCrowns.add(right);
        testCrowns.add(up);
        List<CrownBlock> result = PhysicsUtils.get_aligned_crowns(testCrowns, new Vec3(0, 0, -1));
        assertEquals(1, result.size());
        assertSame(forward, result.get(0));
    }

    @Test
    @DisplayName("get_aligned_crowns: все три SOUTH, heading (0,0,1) → все включены")
    void testGetAlignedCrownsAllAligned() {
        testCrowns.add(new CrownBlock(BlockPos.ZERO,         Direction.SOUTH, 1.0f, 1.0f, 0.0f, null));
        testCrowns.add(new CrownBlock(new BlockPos(1, 0, 0), Direction.SOUTH, 1.0f, 1.0f, 0.0f, null));
        testCrowns.add(new CrownBlock(new BlockPos(2, 0, 0), Direction.SOUTH, 1.0f, 1.0f, 0.0f, null));
        List<CrownBlock> result = PhysicsUtils.get_aligned_crowns(testCrowns, new Vec3(0, 0, 1));
        assertEquals(3, result.size());
    }

    // ──── check_crown_rotation_consistency() ────────────────────────────────────

    /** Провайдер-мок: downcasting к MockBearing — безопасно, тип точно известен в тестах. */
    private static final PhysicsUtils.IBearingSpeedProvider MOCK_SPEED_PROVIDER =
        bearing -> (bearing instanceof MockBearing mb) ? mb.getSpeed() : 0f;

    @Test
    @DisplayName("check_crown_rotation_consistency: нет выровненных корон → empty")
    void testCheckConsistencyNoAligned() {
        testCrowns.add(new CrownBlock(BlockPos.ZERO, Direction.EAST, 1.0f, 1.0f, 0.0f, null));
        Optional<Integer> result = PhysicsUtils.check_crown_rotation_consistency(
            testCrowns, new Vec3(0, 0, 1), MOCK_SPEED_PROVIDER);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("check_crown_rotation_consistency: выровненная корона, подшипник null → empty")
    void testCheckConsistencyNullBearing() {
        testCrowns.add(new CrownBlock(BlockPos.ZERO, Direction.NORTH, 1.0f, 1.0f, 0.0f, null));
        Optional<Integer> result = PhysicsUtils.check_crown_rotation_consistency(
            testCrowns, new Vec3(0, 0, -1), MOCK_SPEED_PROVIDER);
        assertTrue(result.isEmpty(), "Корона без подшипника → нет подтверждения вращения");
    }

    @Test
    @DisplayName("check_crown_rotation_consistency: один подшипник CW (+5 RPM) → +1")
    void testCheckConsistencySingleCW() {
        testCrowns.add(new CrownBlock(BlockPos.ZERO, Direction.NORTH, 1.0f, 1.0f, 0.0f, new MockBearing(5.0f)));
        Optional<Integer> result = PhysicsUtils.check_crown_rotation_consistency(
            testCrowns, new Vec3(0, 0, -1), MOCK_SPEED_PROVIDER);
        assertTrue(result.isPresent());
        assertEquals(1, result.get(), "Положительная скорость → CW = +1");
    }

    @Test
    @DisplayName("check_crown_rotation_consistency: один подшипник CCW (-3 RPM) → -1")
    void testCheckConsistencySingleCCW() {
        testCrowns.add(new CrownBlock(BlockPos.ZERO, Direction.NORTH, 1.0f, 1.0f, 0.0f, new MockBearing(-3.0f)));
        Optional<Integer> result = PhysicsUtils.check_crown_rotation_consistency(
            testCrowns, new Vec3(0, 0, -1), MOCK_SPEED_PROVIDER);
        assertTrue(result.isPresent());
        assertEquals(-1, result.get(), "Отрицательная скорость → CCW = -1");
    }

    @Test
    @DisplayName("check_crown_rotation_consistency: подшипник стоит (0 RPM) → empty")
    void testCheckConsistencyStationary() {
        testCrowns.add(new CrownBlock(BlockPos.ZERO, Direction.NORTH, 1.0f, 1.0f, 0.0f, new MockBearing(0.0f)));
        Optional<Integer> result = PhysicsUtils.check_crown_rotation_consistency(
            testCrowns, new Vec3(0, 0, -1), MOCK_SPEED_PROVIDER);
        assertTrue(result.isEmpty(), "Скорость 0 (стоит) → нельзя бурить → empty");
    }

    @Test
    @DisplayName("check_crown_rotation_consistency: CW + CCW → mixed → empty")
    void testCheckConsistencyMixedSigns() {
        testCrowns.add(new CrownBlock(BlockPos.ZERO,         Direction.NORTH, 1.0f, 1.0f, 0.0f, new MockBearing( 5.0f)));
        testCrowns.add(new CrownBlock(new BlockPos(1, 0, 0), Direction.NORTH, 1.0f, 1.0f, 0.0f, new MockBearing(-3.0f)));
        Optional<Integer> result = PhysicsUtils.check_crown_rotation_consistency(
            testCrowns, new Vec3(0, 0, -1), MOCK_SPEED_PROVIDER);
        assertTrue(result.isEmpty(), "Разные знаки вращения → рассогласование → empty");
    }

    @Test
    @DisplayName("check_crown_rotation_consistency: два CW с разными RPM → consistent → +1")
    void testCheckConsistencyMultipleSameSigns() {
        testCrowns.add(new CrownBlock(BlockPos.ZERO,         Direction.NORTH, 1.0f, 1.0f, 0.0f, new MockBearing(5.0f)));
        testCrowns.add(new CrownBlock(new BlockPos(1, 0, 0), Direction.NORTH, 1.0f, 1.0f, 0.0f, new MockBearing(7.5f)));
        Optional<Integer> result = PhysicsUtils.check_crown_rotation_consistency(
            testCrowns, new Vec3(0, 0, -1), MOCK_SPEED_PROVIDER);
        assertTrue(result.isPresent());
        assertEquals(1, result.get(), "Оба CW → согласованы → +1");
    }

    @Test
    @DisplayName("check_crown_rotation_consistency: один стоит среди вращающихся → empty")
    void testCheckConsistencyOneStationaryAmongRotating() {
        testCrowns.add(new CrownBlock(BlockPos.ZERO,         Direction.NORTH, 1.0f, 1.0f, 0.0f, new MockBearing(5.0f)));
        testCrowns.add(new CrownBlock(new BlockPos(1, 0, 0), Direction.NORTH, 1.0f, 1.0f, 0.0f, new MockBearing(0.0f)));
        Optional<Integer> result = PhysicsUtils.check_crown_rotation_consistency(
            testCrowns, new Vec3(0, 0, -1), MOCK_SPEED_PROVIDER);
        assertTrue(result.isEmpty(), "Один подшипник стоит → нельзя бурить → empty");
    }

    @Test
    @DisplayName("check_crown_rotation_consistency: один физический подшипник на двух коронах → не дублируется")
    void testCheckConsistencyDeduplicatesBearings() {
        MockBearing shared = new MockBearing(5.0f);
        testCrowns.add(new CrownBlock(BlockPos.ZERO,         Direction.NORTH, 1.0f, 1.0f, 0.0f, shared));
        testCrowns.add(new CrownBlock(new BlockPos(1, 0, 0), Direction.NORTH, 1.0f, 1.0f, 0.0f, shared));
        Optional<Integer> result = PhysicsUtils.check_crown_rotation_consistency(
            testCrowns, new Vec3(0, 0, -1), MOCK_SPEED_PROVIDER);
        assertTrue(result.isPresent());
        assertEquals(1, result.get(), "Один физический подшипник → согласованность подтверждена");
    }

    // ──── Helper Mock ────────────────────────────────────────────────────────────

    static class MockBearing {
        private final float speed;
        MockBearing(float speed) { this.speed = speed; }
        public float getSpeed() { return speed; }
    }
}