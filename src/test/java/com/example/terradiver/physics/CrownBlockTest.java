package com.example.terradiver.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Юнит-тесты для CrownBlock.
 * Спецификация — TD_06 v1.0, Домен 0.
 */
@DisplayName("CrownBlock — Crown Block Data Model")
class CrownBlockTest {

    private CrownBlock crown;

    @BeforeEach
    void setUp() {
        crown = new CrownBlock(
            new BlockPos(10, 64, 20),
            Direction.NORTH,
            1.5f,
            1.0f,
            0.5f,
            new MockBearingRef()
        );
    }

    // ──── Constructor & Getters ────────────────────────────────────

    @Test
    @DisplayName("CrownBlock: конструктор сохраняет все поля")
    void testConstructor() {
        assertEquals(new BlockPos(10, 64, 20), crown.getPosition());
        assertEquals(Direction.NORTH, crown.getFace());
        assertEquals(1.5f, crown.getMaterialFactor(), 0.001f);
        assertEquals(1.0f, crown.getArea(),           0.001f);
        assertEquals(0.5f, crown.getDepthAlongHeading(), 0.001f);
        assertNotNull(crown.getBearingReference());
        assertInstanceOf(MockBearingRef.class, crown.getBearingReference());
    }

    @Test
    @DisplayName("CrownBlock: getPosition возвращает правильные координаты")
    void testGetPosition() {
        BlockPos pos = crown.getPosition();
        assertEquals(10, pos.getX());
        assertEquals(64, pos.getY());
        assertEquals(20, pos.getZ());
    }

    @Test
    @DisplayName("CrownBlock: getFace возвращает Direction.NORTH")
    void testGetFace() {
        assertEquals(Direction.NORTH, crown.getFace());
    }

    @Test
    @DisplayName("CrownBlock: materialFactor — нижняя граница 1.0 (медь)")
    void testMaterialFactorMin() {
        CrownBlock copper = new CrownBlock(BlockPos.ZERO, Direction.NORTH, 1.0f, 1.0f, 0.0f, null);
        assertEquals(1.0f, copper.getMaterialFactor(), 0.001f);
    }

    @Test
    @DisplayName("CrownBlock: materialFactor — верхняя граница 3.0 (незерит)")
    void testMaterialFactorMax() {
        CrownBlock netherite = new CrownBlock(BlockPos.ZERO, Direction.NORTH, 3.0f, 1.0f, 0.0f, null);
        assertEquals(3.0f, netherite.getMaterialFactor(), 0.001f);
    }

    @Test
    @DisplayName("CrownBlock: area = 1.0 для одиночного блока")
    void testAreaSingle() {
        CrownBlock single = new CrownBlock(BlockPos.ZERO, Direction.NORTH, 1.0f, 1.0f, 0.0f, null);
        assertEquals(1.0f, single.getArea(), 0.001f);
    }

    @Test
    @DisplayName("CrownBlock: area = 2.5 для составной короны")
    void testAreaComposite() {
        CrownBlock composite = new CrownBlock(BlockPos.ZERO, Direction.NORTH, 1.0f, 2.5f, 0.0f, null);
        assertEquals(2.5f, composite.getArea(), 0.001f);
    }

    @Test
    @DisplayName("CrownBlock: depthAlongHeading = 0.0 для переднего слоя")
    void testDepthFrontLayer() {
        CrownBlock front = new CrownBlock(BlockPos.ZERO, Direction.NORTH, 1.0f, 1.0f, 0.0f, null);
        assertEquals(0.0f, front.getDepthAlongHeading(), 0.001f);
    }

    @Test
    @DisplayName("CrownBlock: depthAlongHeading = 2.5 для заднего слоя")
    void testDepthBackLayer() {
        CrownBlock back = new CrownBlock(BlockPos.ZERO, Direction.NORTH, 1.0f, 1.0f, 2.5f, null);
        assertEquals(2.5f, back.getDepthAlongHeading(), 0.001f);
    }

    @Test
    @DisplayName("CrownBlock: null bearingReference не бросает NPE в конструкторе")
    void testNullBearingReference() {
        assertDoesNotThrow(() ->
            new CrownBlock(BlockPos.ZERO, Direction.NORTH, 1.0f, 1.0f, 0.0f, null));
    }

    // ──── getFaceVector() ────────────────────────────────────

    @Test
    @DisplayName("getFaceVector: NORTH → (0, 0, -1)")
    void testGetFaceVectorNorth() {
        Vec3 v = new CrownBlock(BlockPos.ZERO, Direction.NORTH, 1.0f, 1.0f, 0.0f, null).getFaceVector();
        assertEquals( 0.0, v.x, 0.01);
        assertEquals( 0.0, v.y, 0.01);
        assertEquals(-1.0, v.z, 0.01);
    }

    @Test
    @DisplayName("getFaceVector: SOUTH → (0, 0, 1)")
    void testGetFaceVectorSouth() {
        Vec3 v = new CrownBlock(BlockPos.ZERO, Direction.SOUTH, 1.0f, 1.0f, 0.0f, null).getFaceVector();
        assertEquals(0.0, v.x, 0.01);
        assertEquals(0.0, v.y, 0.01);
        assertEquals(1.0, v.z, 0.01);
    }

    @Test
    @DisplayName("getFaceVector: EAST → (1, 0, 0)")
    void testGetFaceVectorEast() {
        Vec3 v = new CrownBlock(BlockPos.ZERO, Direction.EAST, 1.0f, 1.0f, 0.0f, null).getFaceVector();
        assertEquals(1.0, v.x, 0.01);
        assertEquals(0.0, v.y, 0.01);
        assertEquals(0.0, v.z, 0.01);
    }

    @Test
    @DisplayName("getFaceVector: WEST → (-1, 0, 0)")
    void testGetFaceVectorWest() {
        Vec3 v = new CrownBlock(BlockPos.ZERO, Direction.WEST, 1.0f, 1.0f, 0.0f, null).getFaceVector();
        assertEquals(-1.0, v.x, 0.01);
        assertEquals( 0.0, v.y, 0.01);
        assertEquals( 0.0, v.z, 0.01);
    }

    @Test
    @DisplayName("getFaceVector: UP → (0, 1, 0)")
    void testGetFaceVectorUp() {
        Vec3 v = new CrownBlock(BlockPos.ZERO, Direction.UP, 1.0f, 1.0f, 0.0f, null).getFaceVector();
        assertEquals(0.0, v.x, 0.01);
        assertEquals(1.0, v.y, 0.01);
        assertEquals(0.0, v.z, 0.01);
    }

    @Test
    @DisplayName("getFaceVector: DOWN → (0, -1, 0)")
    void testGetFaceVectorDown() {
        Vec3 v = new CrownBlock(BlockPos.ZERO, Direction.DOWN, 1.0f, 1.0f, 0.0f, null).getFaceVector();
        assertEquals( 0.0, v.x, 0.01);
        assertEquals(-1.0, v.y, 0.01);
        assertEquals( 0.0, v.z, 0.01);
    }

    @Test
    @DisplayName("getFaceVector: результат нормализован (magnitude=1)")
    void testGetFaceVectorNormalized() {
        Vec3 v = crown.getFaceVector();
        double mag = Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
        assertEquals(1.0, mag, 0.01);
    }

    // ──── isAlignedWithHeading() ────────────────────────────────────

    @Test
    @DisplayName("isAlignedWithHeading: null heading → false")
    void testIsAlignedNullHeading() {
        assertFalse(crown.isAlignedWithHeading(null));
    }

    @Test
    @DisplayName("isAlignedWithHeading: точное совпадение (dot=1) → true")
    void testIsAlignedPerfect() {
        CrownBlock north = new CrownBlock(BlockPos.ZERO, Direction.NORTH, 1.0f, 1.0f, 0.0f, null);
        assertTrue(north.isAlignedWithHeading(new Vec3(0, 0, -1)));
    }

    @Test
    @DisplayName("isAlignedWithHeading: противоположное направление (dot=-1) → false")
    void testIsAlignedOpposite() {
        CrownBlock north = new CrownBlock(BlockPos.ZERO, Direction.NORTH, 1.0f, 1.0f, 0.0f, null);
        assertFalse(north.isAlignedWithHeading(new Vec3(0, 0, 1)));
    }

    @Test
    @DisplayName("isAlignedWithHeading: перпендикуляр (dot=0) → false")
    void testIsAlignedPerpendicular() {
        CrownBlock north = new CrownBlock(BlockPos.ZERO, Direction.NORTH, 1.0f, 1.0f, 0.0f, null);
        assertFalse(north.isAlignedWithHeading(new Vec3(1, 0, 0)));
    }

    @Test
    @DisplayName("isAlignedWithHeading: ровно 45° (dot≈0.707) → true (на границе порога 0.7)")
    void testIsAlignedAt45Degrees() {
        CrownBlock north = new CrownBlock(BlockPos.ZERO, Direction.NORTH, 1.0f, 1.0f, 0.0f, null);
        Vec3 heading45 = new Vec3(-1, 0, -1).normalize();
        assertTrue(north.isAlignedWithHeading(heading45), "45° — на пороге, должен проходить");
    }

    @Test
    @DisplayName("isAlignedWithHeading: чуть больше 45° (dot<0.7) → false")
    void testIsAlignedBeyond45Degrees() {
        CrownBlock north = new CrownBlock(BlockPos.ZERO, Direction.NORTH, 1.0f, 1.0f, 0.0f, null);
        Vec3 headingBeyond = new Vec3(-1.1, 0, -1).normalize();
        assertFalse(north.isAlignedWithHeading(headingBeyond), "Чуть дальше 45° → не проходит");
    }

    @Test
    @DisplayName("isAlignedWithHeading: EAST корона, верный heading → true")
    void testIsAlignedEastMatch() {
        CrownBlock east = new CrownBlock(BlockPos.ZERO, Direction.EAST, 1.0f, 1.0f, 0.0f, null);
        assertTrue(east.isAlignedWithHeading(new Vec3(1, 0, 0)));
    }

    @Test
    @DisplayName("isAlignedWithHeading: EAST корона, другие направления → false")
    void testIsAlignedEastNoMatch() {
        CrownBlock east = new CrownBlock(BlockPos.ZERO, Direction.EAST, 1.0f, 1.0f, 0.0f, null);
        assertFalse(east.isAlignedWithHeading(new Vec3(0, 0, 1)));
        assertFalse(east.isAlignedWithHeading(new Vec3(0, 1, 0)));
    }

    @Test
    @DisplayName("isAlignedWithHeading: DOWN корона, heading вниз → true; вверх → false")
    void testIsAlignedDown() {
        CrownBlock down = new CrownBlock(BlockPos.ZERO, Direction.DOWN, 1.0f, 1.0f, 0.0f, null);
        assertTrue(down.isAlignedWithHeading(new Vec3(0, -1, 0)));
        assertFalse(down.isAlignedWithHeading(new Vec3(0, 1, 0)));
    }

    // ──── toString() ────────────────────────────────────

    @Test
    @DisplayName("toString: null bearingReference не бросает NPE")
    void testToStringNullBearing() {
        CrownBlock noBearing = new CrownBlock(BlockPos.ZERO, Direction.SOUTH, 2.0f, 4.0f, 1.0f, null);
        assertDoesNotThrow(noBearing::toString);
    }

    // ──── Helper Mock ────────────────────────────────────────────────────────────

    static class MockBearingRef {
        public float getSpeed() { return 5.0f; }
    }
}