package com.example.terradiver.drilling;

import com.example.terradiver.physics.PhysicsUtils;
import com.example.terradiver.physics.CrownBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/*
 * Юнит-тесты буровой системы (TD_02): чистые функции + оркестрация инвентаря через сёму.
 * Спецификация — TD_06 v1.0, TD_02. BlockState/ItemStack — моки (Mockito), без MC-runtime.
 */
@DisplayName("DrillingUtils — TD_02")
class DrillingUtilsTest {

    // ── compute_max_blocks_per_tick ──────────────────────────────────────────

    @Nested
    @DisplayName("compute_max_blocks_per_tick")
    class MaxBlocks {

        @Test @DisplayName("eff*mat=8, prev=8 → 8")
        void steady() {
            assertEquals(8, DrillingUtils.compute_max_blocks_per_tick(8f, 4f, 2f, 1));
        }

        @Test @DisplayName("первый тик (prev=0): (8+0)/2=4")
        void firstTick() {
            assertEquals(4, DrillingUtils.compute_max_blocks_per_tick(0f, 4f, 2f, 1));
        }

        @Test @DisplayName("округление вверх: 3.1 → 4")
        void ceil() {
            assertEquals(4, DrillingUtils.compute_max_blocks_per_tick(0f, 3.1f, 2f, 1));
        }

        @Test @DisplayName("почти нулевой RPM → пол MIN_BLOCKS_PER_TICK")
        void floorMin() {
            assertEquals(1, DrillingUtils.compute_max_blocks_per_tick(0f, 0f, 2f, 1));
            assertEquals(3, DrillingUtils.compute_max_blocks_per_tick(0f, 0f, 1f, 3));
        }
    }

    // ── decay_pending_resistance ─────────────────────────────────────────────

    @Nested
    @DisplayName("decay_pending_resistance")
    class Decay {

        @Test @DisplayName("5 - 2 = 3")
        void normal() {
            assertEquals(3f, DrillingUtils.decay_pending_resistance(5f, 2f), 1e-6f);
        }

        @Test @DisplayName("1 - 2 → clamp 0")
        void clamp() {
            assertEquals(0f, DrillingUtils.decay_pending_resistance(1f, 2f), 1e-6f);
        }

        @Test @DisplayName("0 → 0")
        void zero() {
            assertEquals(0f, DrillingUtils.decay_pending_resistance(0f, 0.3f), 1e-6f);
        }
    }

    // ── is_bedrock_blocking ──────────────────────────────────────────────────

    @Nested
    @DisplayName("is_bedrock_blocking")
    class Bedrock {

        private PhysicsUtils.BlockStateAtPos at(float hardness) {
            Block block = mock(Block.class);
            when(block.defaultDestroyTime()).thenReturn(hardness);
            BlockState state = mock(BlockState.class);
            when(state.getBlock()).thenReturn(block);
            return new PhysicsUtils.BlockStateAtPos(BlockPos.ZERO, state);
        }

        @Test @DisplayName("пробиваемые блоки → False")
        void allBreakable() {
            assertFalse(DrillingUtils.is_bedrock_blocking(List.of(at(1.5f), at(3.0f))));
        }

        @Test @DisplayName("есть непробиваемый (hardness<0) → True")
        void hasBedrock() {
            assertTrue(DrillingUtils.is_bedrock_blocking(List.of(at(1.5f), at(-1.0f))));
        }

        @Test @DisplayName("пустой/null список → False")
        void empty() {
            assertFalse(DrillingUtils.is_bedrock_blocking(List.of()));
            assertFalse(DrillingUtils.is_bedrock_blocking(null));
        }
    }

    // ── buffer_has_space (фейк CrownBuffer) ──────────────────────────────────

    @Nested
    @DisplayName("buffer_has_space")
    class BufferSpace {

        /** Фейк буфера: доступность слотов задаётся массивом, insert не используется. */
        private DrillingUtils.CrownBuffer fake(boolean... available) {
            return new DrillingUtils.CrownBuffer() {
                public int size() { return available.length; }
                public boolean slotAvailable(int slot) { return available[slot]; }
                public ItemStack insert(ItemStack stack) { throw new UnsupportedOperationException(); }
            };
        }

        @Test @DisplayName("все слоты заняты → False")
        void full() {
            assertFalse(DrillingUtils.buffer_has_space(fake(false, false, false)));
        }

        @Test @DisplayName("есть доступный слот → True")
        void hasRoom() {
            assertTrue(DrillingUtils.buffer_has_space(fake(false, true, false)));
        }

        @Test @DisplayName("буфер 0 слотов → False")
        void zeroSlots() {
            assertFalse(DrillingUtils.buffer_has_space(fake()));
        }

        @Test @DisplayName("null буфер → False")
        void nullBuffer() {
            assertFalse(DrillingUtils.buffer_has_space(null));
        }
    }

    // ── deposit_to_inventory (оркестрация; стекинг делегирован сёме) ──────────

    @Nested
    @DisplayName("deposit_to_inventory — оркестрация")
    class Deposit {

        private ItemStack stack(boolean empty) {
            ItemStack s = mock(ItemStack.class);
            when(s.isEmpty()).thenReturn(empty);
            return s;
        }

        @Test @DisplayName("весь дроп влез → True")
        void allFit() {
            DrillingUtils.CrownBuffer buf = mock(DrillingUtils.CrownBuffer.class);
            when(buf.insert(any())).thenReturn(stack(true)); // остаток пуст
            List<ItemStack> drops = List.of(stack(false), stack(false));
            assertTrue(DrillingUtils.deposit_to_inventory(drops, buf));
        }

        @Test @DisplayName("переполнение в середине → False")
        void overflowMidway() {
            DrillingUtils.CrownBuffer buf = mock(DrillingUtils.CrownBuffer.class);
            when(buf.insert(any())).thenReturn(stack(true), stack(false)); // 2-й не влез целиком
            List<ItemStack> drops = List.of(stack(false), stack(false), stack(false));
            assertFalse(DrillingUtils.deposit_to_inventory(drops, buf));
        }

        @Test @DisplayName("пустой список дропа → True")
        void emptyDrops() {
            DrillingUtils.CrownBuffer buf = mock(DrillingUtils.CrownBuffer.class);
            assertTrue(DrillingUtils.deposit_to_inventory(List.of(), buf));
            verifyNoInteractions(buf);
        }

        @Test @DisplayName("пустые стаки пропускаются, не вставляются")
        void skipsEmptyStacks() {
            DrillingUtils.CrownBuffer buf = mock(DrillingUtils.CrownBuffer.class);
            List<ItemStack> drops = new ArrayList<>();
            drops.add(stack(true)); // пустой — пропустить
            assertTrue(DrillingUtils.deposit_to_inventory(drops, buf));
            verify(buf, never()).insert(any());
        }

        @Test @DisplayName("null буфер → False")
        void nullBuffer() {
            assertFalse(DrillingUtils.deposit_to_inventory(List.of(stack(false)), null));
        }
    }

    // ── compute_crown_face_area  ──────────

    private static CrownBlock crown(float factor, float area, float depth, net.minecraft.core.Direction face) {
    return new CrownBlock(net.minecraft.core.BlockPos.ZERO, face, factor, area, depth, null);
    }
    private static final net.minecraft.world.phys.Vec3 HEADING = new net.minecraft.world.phys.Vec3(0, 0, 1);
    private static final net.minecraft.core.Direction ALIGNED = net.minecraft.core.Direction.SOUTH; // (0,0,1)
    private static final net.minecraft.core.Direction OFF = net.minecraft.core.Direction.EAST;       // (1,0,0)

    @Nested @DisplayName("compute_crown_face_area")
    class FaceArea {
        @Test @DisplayName("нет выровненных → 0")
        void none() {
            assertEquals(0f, DrillingUtils.compute_crown_face_area(List.of(crown(1f, 9f, 0f, OFF)), HEADING), 1e-6f);
        }
        @Test @DisplayName("один диск 9 → 9")
        void single() {
            assertEquals(9f, DrillingUtils.compute_crown_face_area(List.of(crown(1f, 9f, 0f, ALIGNED)), HEADING), 1e-6f);
        }
        @Test @DisplayName("передний слой суммируется, задний (depth5) игнорируется")
        void frontOnly() {
            var crowns = List.of(crown(1f, 9f, 0f, ALIGNED), crown(1f, 16f, 5f, ALIGNED));
            assertEquals(9f, DrillingUtils.compute_crown_face_area(crowns, HEADING), 1e-6f);
        }
        @Test @DisplayName("допуск 1 блок: depth0 + depth1 = один слой")
        void tolerance() {
            var crowns = List.of(crown(1f, 9f, 0f, ALIGNED), crown(1f, 4f, 1f, ALIGNED));
            assertEquals(13f, DrillingUtils.compute_crown_face_area(crowns, HEADING), 1e-6f);
        }
    }

    @Nested @DisplayName("compute_avg_material_factor")
    class AvgFactor {
        @Test @DisplayName("face_area 0 → 1.0")
        void zeroArea() {
            assertEquals(1.0f, DrillingUtils.compute_avg_material_factor(List.of(crown(2f, 9f, 0f, ALIGNED)), HEADING, 0f), 1e-6f);
        }
        @Test @DisplayName("однородная корона → ровно её фактор (инвариант)")
        void homogeneous() {
            var c = List.of(crown(2f, 9f, 0f, ALIGNED), crown(2f, 4f, 0f, ALIGNED));
            float fa = DrillingUtils.compute_crown_face_area(c, HEADING);
            assertEquals(2.0f, DrillingUtils.compute_avg_material_factor(c, HEADING, fa), 1e-6f);
        }
        @Test @DisplayName("медь25(f1) + железо11(f1.5) → взвешенно между 1.0 и 1.5")
        void mixed() {
            var c = List.of(crown(1.0f, 25f, 0f, ALIGNED), crown(1.5f, 11f, 0f, ALIGNED));
            float fa = DrillingUtils.compute_crown_face_area(c, HEADING);
            float af = DrillingUtils.compute_avg_material_factor(c, HEADING, fa);
            assertEquals((25f * 1f + 11f * 1.5f) / 36f, af, 1e-6f);
            assertTrue(af > 1.0f && af < 1.5f);
        }
    }

    @Nested @DisplayName("update_pending_resistance")
    class PendingResistance {
        @Test @DisplayName("недогруз (found5 proc2) → рост deficit*growth")
        void growth() {
            assertEquals(1.5f, DrillingUtils.update_pending_resistance(0f, 5, 2, 0.5f, 0.3f, 10f), 1e-6f);
        }
        @Test @DisplayName("избыток → спад на DECAY")
        void decay() {
            assertEquals(0.7f, DrillingUtils.update_pending_resistance(1.0f, 1, 4, 0.5f, 0.3f, 10f), 1e-6f);
        }
        @Test @DisplayName("спад clamp 0")
        void clampZero() {
            assertEquals(0f, DrillingUtils.update_pending_resistance(0.1f, 0, 5, 0.5f, 0.3f, 10f), 1e-6f);
        }
        @Test @DisplayName("рост clamp MAX")
        void clampMax() {
            assertEquals(10f, DrillingUtils.update_pending_resistance(9.5f, 10, 0, 0.5f, 0.3f, 10f), 1e-6f);
        }
    }
}
