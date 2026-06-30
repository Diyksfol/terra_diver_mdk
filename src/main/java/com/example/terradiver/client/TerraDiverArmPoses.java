package com.example.terradiver.client;

import net.minecraft.client.model.HumanoidModel;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/*
 * Параметры кастомной позы рук (обе руки вверх) для расширяемого enum HumanoidModel.ArmPose.
 * Вынесено в отдельный класс — требование механизма enumextensions NeoForge:
 * источник параметров не должен грузить классы мода слишком рано.
 * Дескриптор конструктора и ссылку см. META-INF/enumextensions.json.
 */
public class TerraDiverArmPoses {

    public static final EnumProxy<HumanoidModel.ArmPose> DRILL_LIFT = new EnumProxy<>(
        HumanoidModel.ArmPose.class,
        false, // useItem
        (IArmPoseTransformer) (model, entity, arm) -> {
            // Поднять обе руки почти вертикально вверх. Значения подстраиваются в игре.
            model.rightArm.xRot = -3.05F; // почти строго вверх
            model.leftArm.xRot = -3.05F;
            model.rightArm.yRot = 0.0F;
            model.leftArm.yRot = 0.0F;
            model.rightArm.zRot = 0.05F;
            model.leftArm.zRot = -0.05F;
        });
}