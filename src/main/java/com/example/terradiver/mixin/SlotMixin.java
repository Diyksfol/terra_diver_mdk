package com.example.terradiver.mixin;

import com.example.terradiver.physics.DrillCrownBlock;
import com.example.terradiver.physics.DrillCrownItem;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/*
 * Полный запрет класть что-либо во вторую руку, пока в основной руке буровая корона (кроме 1x1).
 * Слот второй руки в инвентаре игрока — SLOT_OFFHAND (40). При буре в основной руке mayPlace=false,
 * поэтому GUI-постановка предмета во вторую руку становится невозможна (без мигания).
 * F-свап и прочее ловит серверный тик DrillOffhandLock.
 */
@Mixin(Slot.class)
public class SlotMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void terra_diver$blockOffhand(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Slot self = (Slot) (Object) this;
        Container c = self.container;
        if (c instanceof Inventory inv && self.getContainerSlot() == Inventory.SLOT_OFFHAND) {
            // 1) сам бур (кроме 1x1) нельзя класть во вторую руку никогда
            if (isLiftedDrill(stack)) {
                cir.setReturnValue(false);
                return;
            }
            // 2) пока бур в основной руке — во вторую нельзя класть вообще ничего
            if (isLiftedDrill(inv.player.getMainHandItem())) {
                cir.setReturnValue(false);
            }
        }
    }

    private static boolean isLiftedDrill(ItemStack stack) {
        return stack.getItem() instanceof DrillCrownItem item
                && item.getBlock() instanceof DrillCrownBlock block
                && !"1x1".equals(block.crownSize());
    }
}