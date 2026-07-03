package com.example.terradiver.mixin;

import com.example.terradiver.physics.DrillCrownBlock;
import com.example.terradiver.physics.DrillCrownItem;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*
 * Универсальный запрет положить что-либо во вторую руку через "своп со второй рукой" (F) прямо
 * внутри ЛЮБОГО контейнера — своего инвентаря, сундука, и т.д. Такой своп идёт мимо Slot.mayPlace:
 * ячейку второй руки контейнер пишет напрямую как playerInventory.setItem(40, ...), поэтому SlotMixin
 * его не ловит. Здесь бьём в общий узел AbstractContainerMenu.clicked (через него проходят ВСЕ клики
 * во ВСЕХ меню), отменяя своп во вторую руку (button == SLOT_OFFHAND), когда:
 *   - предмет, уезжающий во вторую руку, — бур (кроме 1x1), либо
 *   - в основной руке уже бур (тогда вторая рука должна оставаться пустой).
 * require = 0: если сигнатура метода вдруг не совпадёт на другой сборке — миксин молча не применится,
 * не роняя игру; подстраховкой останется серверный тик в DrillOffhandLock.
 */
@Mixin(AbstractContainerMenu.class)
public class ContainerOffhandMixin {

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void terra_diver$blockOffhandSwap(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        if (clickType != ClickType.SWAP || button != Inventory.SLOT_OFFHAND) {
            return;
        }
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        if (slotId < 0 || slotId >= self.slots.size()) {
            return;
        }
        Slot hoveredSlot = self.slots.get(slotId);
        ItemStack hovered = hoveredSlot.getItem();
        if (isLiftedDrill(hovered) || isLiftedDrill(player.getMainHandItem())) {
            ci.cancel();
        }
    }

    private static boolean isLiftedDrill(ItemStack stack) {
        return stack.getItem() instanceof DrillCrownItem item
                && item.getBlock() instanceof DrillCrownBlock block
                && !"1x1".equals(block.crownSize());
    }
}
