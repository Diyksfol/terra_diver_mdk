package com.example.terradiver.client;

import com.example.terradiver.physics.DrillCrownBlock;
import com.example.terradiver.physics.DrillCrownItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;


/*
 * Клиентский рендер буровой короны в руках.
 *  - Поза: обе руки строго вверх (кастомная ArmPose, см. TerraDiverArmPoses + enumextensions.json).
 *  - Модель: бур рисуется над головой, по центру между руками, поворачивается вслед за игроком.
 *  - 1x1 исключён: держится как обычный блок (без позы и без над-головой рендера).
 * Значения положения/масштаба помечены TUNE.
 */
@EventBusSubscriber(modid = "terra_diver", value = Dist.CLIENT)
public class DrillClientExtensions {

    // Равное уменьшение в 2 раза от исходного для всех размеров (3x3 тоньше 11x11 — как и должно).
    private static final float SCALE = 0.5F; // - TUNE
    // Сдвиг вдоль линии плеч к центру. Модель центрирована в 0.5; бур был у левой руки,
    // поэтому добавляем половину ширины плеч. Если уехало не туда — поменяй знак 0.7 на -0.7.
    private static final double CENTER_X = -0.5D + 0.525D; // - TUNE

    // Размер короны для предмета, либо null если это не корона.
    private static String crownSize(ItemStack stack) {
        if (stack.getItem() instanceof DrillCrownItem item
                && item.getBlock() instanceof DrillCrownBlock block) {
            return block.crownSize();
        }
        return null;
    }

    // Корона в основной руке, исключая 1x1 (тот держится как обычный блок).
    private static ItemStack liftedDrill(Player player) {
        ItemStack main = player.getMainHandItem();
        String size = crownSize(main);
        if (size != null && !"1x1".equals(size)) {
            return main;
        }
        return ItemStack.EMPTY;
    }

    private static HumanoidModel.ArmPose drillLiftPose() {
        return HumanoidModel.ArmPose.valueOf("TERRA_DIVER_DRILL_LIFT");
    }

    // Поза рук — только для корон крупнее 1x1.
    @SubscribeEvent
    static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        IClientItemExtensions ext = new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                String size = crownSize(stack);
                if (size != null && !"1x1".equals(size)) {
                    return drillLiftPose();
                }
                return null;
            }
        };
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof DrillCrownItem) {
                event.registerItem(ext, item);
            }
        }
    }

    // Рендер бура над головой.
    @SubscribeEvent
    static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        ItemStack stack = liftedDrill(player);
        if (stack.isEmpty()) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        pose.pushPose();

        float bodyYaw = Mth.lerp(event.getPartialTick(), player.yBodyRotO, player.yBodyRot);
        pose.translate(0.0D, player.getBbHeight() + 0.4D, 0.0D);  // над головой - TUNE
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));  // поворот вслед за игроком
        // без наклона: ось бура +Y смотрит вверх, диск смотрит вверх, мастер (Y=0) снизу у рук
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(CENTER_X, 0.0D, -0.5D);                    // центр: CENTER_X вдоль линии плеч - TUNE

        Minecraft mc = Minecraft.getInstance();
        mc.getItemRenderer().renderStatic(
            stack, ItemDisplayContext.NONE,
            event.getPackedLight(), OverlayTexture.NO_OVERLAY,
            pose, event.getMultiBufferSource(), player.level(), 0);
        pose.popPose();
    }
}