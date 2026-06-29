package com.example.terradiver.drilling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Юнит-тесты ядра clear_blocks (TD_02). См. TD_06 v1.1. Без Minecraft-runtime —
 * цели и "есть место" подменяются фейками, MC-доступ (лут/удаление) сюда не попадает.
 */
@DisplayName("BlockClearer — clearBlocks (ядро)")
class BlockClearerTest {

    /** Фейк цели: задаются добываемость и "весь дроп влез"; считает вызовы (= удаления). */
    static final class FakeTarget implements BlockClearer.MiningTarget {
        final boolean diggable;
        final boolean fully;
        boolean broken = false;
        FakeTarget(boolean diggable, boolean fully) { this.diggable = diggable; this.fully = fully; }
        public boolean isDiggable() { return diggable; }
        public boolean breakAndDeposit() { broken = true; return fully; }
    }

    private static FakeTarget dig(boolean fully) { return new FakeTarget(true, fully); }
    private static final BooleanSupplier ALWAYS = () -> true;

    private static int brokenCount(List<FakeTarget> ts) {
        int n = 0;
        for (FakeTarget t : ts) if (t.broken) n++;
        return n;
    }

    @Test @DisplayName("лимит мощности: 5 целей, max 3 → processed 3, сломано 3")
    void powerLimit() {
        List<FakeTarget> ts = List.of(dig(true), dig(true), dig(true), dig(true), dig(true));
        assertEquals(3, BlockClearer.clearBlocks(new ArrayList<>(ts), ALWAYS, 3));
        assertEquals(3, brokenCount(ts));
        assertFalse(ts.get(3).broken, "4-я цель не тронута");
    }

    @Test @DisplayName("недобываемые пропускаются (не считаются, не ломаются)")
    void skipsNonDiggable() {
        FakeTarget nd1 = new FakeTarget(false, true), nd2 = new FakeTarget(false, true);
        List<FakeTarget> ts = List.of(nd1, dig(true), nd2, dig(true));
        assertEquals(2, BlockClearer.clearBlocks(new ArrayList<>(ts), ALWAYS, 10));
        assertFalse(nd1.broken);
        assertFalse(nd2.broken);
        assertEquals(2, brokenCount(ts));
    }

    @Test @DisplayName("заморозка буфера: место есть 2 раза → processed 2, 3-я не сломана")
    void bufferFreeze() {
        int[] calls = {0};
        BooleanSupplier space = () -> ++calls[0] <= 2;
        List<FakeTarget> ts = List.of(dig(true), dig(true), dig(true), dig(true));
        assertEquals(2, BlockClearer.clearBlocks(new ArrayList<>(ts), space, 10));
        assertEquals(2, brokenCount(ts));
        assertFalse(ts.get(2).broken, "после заморозки блок не ломается");
    }

    @Test @DisplayName("частичный дроп (fully=false): блок сломан, но НЕ засчитан, цикл идёт дальше")
    void partialDeposit() {
        List<FakeTarget> ts = List.of(dig(false), dig(false), dig(false));
        assertEquals(0, BlockClearer.clearBlocks(new ArrayList<>(ts), ALWAYS, 5));
        assertEquals(3, brokenCount(ts), "все блоки удалены, несмотря на потерю части дропа");
    }

    @Test @DisplayName("смесь fully/partial → считаются только полные")
    void mixed() {
        List<FakeTarget> ts = List.of(dig(true), dig(false), dig(true));
        assertEquals(2, BlockClearer.clearBlocks(new ArrayList<>(ts), ALWAYS, 5));
        assertEquals(3, brokenCount(ts));
    }

    @Test @DisplayName("пустой/null список → 0")
    void empty() {
        assertEquals(0, BlockClearer.clearBlocks(List.of(), ALWAYS, 5));
        assertEquals(0, BlockClearer.clearBlocks(null, ALWAYS, 5));
    }

    @Test @DisplayName("max 0 (защитный) → ничего не обрабатывается")
    void zeroMax() {
        List<FakeTarget> ts = List.of(dig(true), dig(true));
        assertEquals(0, BlockClearer.clearBlocks(new ArrayList<>(ts), ALWAYS, 0));
        assertEquals(0, brokenCount(ts));
    }
}
