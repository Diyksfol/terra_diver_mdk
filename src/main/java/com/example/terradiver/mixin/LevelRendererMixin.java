package com.example.terradiver.mixin;

import com.example.terradiver.physics.DrillCrownPartBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*
 * Трещины разрушения на всю буровую корону.
 * Ведомые ячейки невидимы (нет квадов модели) — оверлей разрушения на них не виден.
 * При установке прогресса разрушения на ведомую ячейку зеркалим тот же прогресс на мастер:
 * модель мастера накрывает весь footprint, поэтому трещины проступают по всей короне.
 * Мастер не несёт DrillCrownPartBlockEntity, поэтому зеркальный вызов не рекурсирует.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "destroyBlockProgress", at = @At("HEAD"))
    private void terra_diver$mirrorCrownDestroy(int breakerId, BlockPos pos, int progress, CallbackInfo ci) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof DrillCrownPartBlockEntity part) {
            BlockPos masterPos = part.getMaster();
            if (masterPos != null && !masterPos.equals(pos)) {
                ((LevelRenderer) (Object) this).destroyBlockProgress(breakerId, masterPos, progress);
            }
        }
    }
}
