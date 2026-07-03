package com.example.terradiver.client;

import com.example.terradiver.physics.DrillCrownBlock;
import com.example.terradiver.physics.DrillCrownItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/*
 * Держит буровую корону (кроме 1x1) строго в основной руке, вторую руку — пустой.
 * Уровни защиты (каждый закрывает свой путь; серверный тик — общий страховочный):
 *  1) LivingSwapItemsEvent.Hands — отменяем своп F в обычной игре (из хотбара), если во вторую
 *     руку уехал бы бур.
 *  2) SlotMixin (Slot.mayPlace) — запрещает перетаскивание мышью во вторую руку в GUI.
 *  3) ContainerOffhandMixin (AbstractContainerMenu.clicked) — запрещает своп во вторую руку клавишей F
 *     ВНУТРИ любого контейнера (инвентарь, сундук и т.д.); этот путь идёт мимо Slot.mayPlace.
 *  4) Серверный тик (ниже) — общая страховка на любой прочий путь (раздатчик, команды, смерть):
 *     бур во второй руке → в основную/инвентарь; пока бур в основной — вторая рука пуста.
 * На заметку: «возврат вытесненного предмета после снятия бура» не делаем — он просто в инвентарь.
 */
@EventBusSubscriber(modid = "terra_diver")
public class DrillOffhandLock {

    private static boolean isLiftedDrill(ItemStack stack) {
        return stack.getItem() instanceof DrillCrownItem item
                && item.getBlock() instanceof DrillCrownBlock block
                && !"1x1".equals(block.crownSize());
    }

    // 1) Отмена F-свопа, если бур уехал бы во вторую руку. getItemSwappedToOffHand() — предмет,
    //    который попадёт в левую руку (= сейчас в правой). Обратный своп (бур из левой в правую)
    //    не трогаем — он как раз возвращает бур на место.
    @SubscribeEvent
    static void onSwapHands(LivingSwapItemsEvent.Hands event) {
        if (isLiftedDrill(event.getItemSwappedToOffHand())) {
            event.setCanceled(true);
        }
    }

    // 2) Серверная страховка на тик.
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();

        // Бур каким-то путём попал во вторую руку → вернуть в основную, иначе в инвентарь.
        if (isLiftedDrill(off)) {
            ItemStack drill = off.copy();
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            if (main.isEmpty()) {
                player.setItemInHand(InteractionHand.MAIN_HAND, drill);
            } else if (!player.getInventory().add(drill)) {
                player.drop(drill, false);
            }
            return;
        }

        // Бур в основной руке → вторая рука пуста.
        if (isLiftedDrill(main) && !off.isEmpty()) {
            ItemStack moved = off.copy();
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            if (!player.getInventory().add(moved)) {
                player.drop(moved, false);
            }
        }
    }
}