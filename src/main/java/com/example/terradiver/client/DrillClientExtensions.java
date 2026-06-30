package com.example.terradiver.client;

import com.example.terradiver.physics.DrillCrownItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
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
 * Клиентский рендер буровой короны в руках:
 *  - поза: обе руки игрока подняты вверх (кастомная ArmPose, см. TerraDiverArmPoses + enumextensions.json);
 *  - модель: гигантский бур рисуется над головой параллельно земле (в руке он спрятан thirdperson-трансформом).
 * Значения положения/масштаба помечены TUNE — подстраиваются в игре.
 *
 * Один @EventBusSubscriber обслуживает оба события: RenderPlayerEvent (игровая шина) и
 * RegisterClientExtensionsEvent (мод-шина) — в NeoForge 1.21.1 шина определяется по типу события автоматически.
 */
@EventBusSubscriber(modid = "terra_diver", value = Dist.CLIENT)
public class DrillClientExtensions {

    // Кэш кастомной позы. valueOf безопасен после расширения enum при его загрузке классов.
    private static HumanoidModel.ArmPose drillLiftPose() {
        return HumanoidModel.ArmPose.valueOf("TERRA_DIVER_DRILL_LIFT");
    }

    // Регистрация позы рук для всех предметов короны.
    @SubscribeEvent
    static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        IClientItemExtensions ext = new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return drillLiftPose();
            }
        };
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof DrillCrownItem) {
                event.registerItem(ext, item);
            }
        }
    }

    // Рендер модели бура над головой, пока игрок держит корону.
    @SubscribeEvent
    static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof DrillCrownItem)) {
            stack = player.getOffhandItem();
            if (!(stack.getItem() instanceof DrillCrownItem)) {
                return;
            }
        }

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0.0D, player.getBbHeight() + 0.5D, 0.0D); // над головой - TUNE
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));            // параллельно земле - TUNE
        float s = 0.35F;                                          // масштаб - TUNE
        pose.scale(s, s, s);
        pose.translate(-0.5D, 0.0D, -0.5D);                       // грубое центрирование - TUNE

        Minecraft mc = Minecraft.getInstance();
        mc.getItemRenderer().renderStatic(
            stack, ItemDisplayContext.NONE,
            event.getPackedLight(), OverlayTexture.NO_OVERLAY,
            pose, event.getMultiBufferSource(), player.level(), 0);
        pose.popPose();
    }
}