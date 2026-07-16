package com.example.terradiver.kinetics;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/*
 * Буфер крепления бурового подшипника — куда падает выгрызенная порода.
 *
 * Правила игрока (решение по дизайну):
 *  - руками не открывается: своего интерфейса нет, только воронки и логистика Create;
 *  - наружу отдаёт свободно (воронка/сундук/туннель забирают);
 *  - СНАРУЖИ в него ничего не положить: это выход бура, а не ящик. Иначе игрок мог бы
 *    забить его мусором и остановить бурение, а воронка сверху — гонять предметы по кругу.
 *
 * Отсюда две разные вставки: internalInsert() зовёт сам бур (разрешено всегда), а insertItem()
 * — это то, что видят чужие блоки, и она молча отказывает, возвращая стак нетронутым.
 */
public class CrownBufferHandler extends ItemStackHandler {

    // Открывается только на время приёма дропа от бура; для всех остальных вставка закрыта.
    private boolean insertAllowed = false;

    public CrownBufferHandler(int slots) {
        super(Math.max(1, slots));
    }

    // Приём дропа от самого бура. Возвращает остаток, не влезший в буфер (пусто — влезло всё).
    public ItemStack internalInsert(ItemStack stack) {
        insertAllowed = true;
        try {
            ItemStack rest = stack;
            for (int slot = 0; slot < getSlots() && !rest.isEmpty(); slot++) {
                rest = insertItem(slot, rest, false);
            }
            return rest;
        } finally {
            insertAllowed = false;
        }
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!insertAllowed) {
            return stack; // чужая вставка — отказ, стак остаётся у отправителя
        }
        return super.insertItem(slot, stack, simulate);
    }

    // Есть ли куда класть: пустой слот ИЛИ неполный стак. Опрашивается каждый тик — от этого
    // зависит, бурит механизм или встал (при полном буфере бурение замирает, дроп на землю НЕ
    // роняется, прогресс разрушения не идёт).
    public boolean hasSpace() {
        for (int slot = 0; slot < getSlots(); slot++) {
            ItemStack inSlot = getStackInSlot(slot);
            if (inSlot.isEmpty() || inSlot.getCount() < Math.min(inSlot.getMaxStackSize(), getSlotLimit(slot))) {
                return true;
            }
        }
        return false;
    }
}
