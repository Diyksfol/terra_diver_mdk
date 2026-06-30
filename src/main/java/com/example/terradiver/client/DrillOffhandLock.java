package com.example.terradiver.client;

import com.example.terradiver.physics.DrillCrownBlock;
import com.example.terradiver.physics.DrillCrownItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/*
 * Пока в основной руке буровая корона (кроме 1x1), вторая рука принудительно пуста:
 * любой предмет из второй руки уезжает в инвентарь. Это убирает нелогичность
 * (щит/блок во второй руке, пока бур висит над головой). Серверная логика.
 * На заметку: «возврат предмета после снятия бура» пока не делаем — это потребовало бы
 * хранить вытесненный предмет; сейчас он просто кладётся в инвентарь.
 */
@EventBusSubscriber(modid = "terra_diver")
public class DrillOffhandLock {

    private static boolean isLiftedDrill(ItemStack stack) {
        return stack.getItem() instanceof DrillCrownItem item
                && item.getBlock() instanceof DrillCrownBlock block
                && !"1x1".equals(block.crownSize());
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        if (!isLiftedDrill(player.getMainHandItem())) {
            return;
        }
        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty()) {
            ItemStack moved = off.copy();
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            if (!player.getInventory().add(moved)) {
                player.drop(moved, false);
            }
        }
    }
}
