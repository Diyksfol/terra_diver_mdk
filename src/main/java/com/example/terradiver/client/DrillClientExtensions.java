package com.example.terradiver.client;

import com.example.terradiver.physics.DrillCrownBlock;
import com.example.terradiver.physics.DrillCrownItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
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
    private static final double CENTER_X = -0.5D + 0.5D; // - TUNE
    // Сдвиг ВПЕРЁД/НАЗАД по взгляду игрока (ось Z после поворота по телу). Было -0.5 — из-за него
    // бур уезжал вперёд по взгляду. Обнулил (0.0). В мире смещение ≈ CENTER_Z * SCALE.
    // Крутить: отрицательное = вперёд по взгляду, положительное = назад. - TUNE
    private static final double CENTER_Z = 0.0D; // - TUNE (было -0.5)
    // Высота бура над головой (мировые блоки, до масштабирования). Было 0.4 — бур висел на 2 пикселя
    // выше рук. Опустил на 2 пикселя (2/16 = 0.125). Шаг тонкой подстройки: 1 пиксель = 0.0625. - TUNE
    private static final double ABOVE_HEAD = 0.275D; // - TUNE (было 0.4; -0.125 = -2px)

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
        pose.translate(0.0D, player.getBbHeight() + ABOVE_HEAD, 0.0D);  // над головой - TUNE
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));  // поворот вслед за игроком
        // без наклона: ось бура +Y смотрит вверх, диск смотрит вверх, мастер (Y=0) снизу у рук
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(CENTER_X, 0.0D, CENTER_Z);                    // центр: CENTER_X вдоль линии плеч - TUNE

        Minecraft mc = Minecraft.getInstance();
        mc.getItemRenderer().renderStatic(
            stack, ItemDisplayContext.NONE,
            event.getPackedLight(), OverlayTexture.NO_OVERLAY,
            pose, event.getMultiBufferSource(), player.level(), 0);
        pose.popPose();
    }

    // Рендер бура над собой в виде от первого лица. Модель игрока в 1-м лице не рисуется,
    // поэтому RenderPlayerEvent не срабатывает — рисуем в мировых координатах над игроком.
    // Третье лицо остаётся на RenderPlayerEvent, двойного рендера нет (проверка isFirstPerson).
    @SubscribeEvent
    static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        Player player = mc.player;
        if (player == null) {
            return;
        }
        ItemStack stack = liftedDrill(player);
        if (stack.isEmpty()) {
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        double px = Mth.lerp(pt, player.xo, player.getX());
        double py = Mth.lerp(pt, player.yo, player.getY());
        double pz = Mth.lerp(pt, player.zo, player.getZ());

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(px - cam.x, py - cam.y, pz - cam.z);      // от камеры к игроку (мир)
        float bodyYaw = Mth.lerp(pt, player.yBodyRotO, player.yBodyRot);
        pose.translate(0.0D, player.getBbHeight() + ABOVE_HEAD, 0.0D); // над головой - TUNE (как в 3-м лице)
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(CENTER_X, 0.0D, CENTER_Z);

        int light = LevelRenderer.getLightColor(player.level(), player.blockPosition().above(2));
        var buffers = mc.renderBuffers().bufferSource();
        mc.getItemRenderer().renderStatic(
            stack, ItemDisplayContext.NONE,
            light, OverlayTexture.NO_OVERLAY,
            pose, buffers, player.level(), 0);
        buffers.endBatch();
        pose.popPose();
    }
}