package com.example.terradiver.mixin;

import com.example.terradiver.physics.DrillCrownPartBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*
 * Трещины разрушения на всю буровую корону.
 * Ведомые ячейки невидимы (нет квадов модели) — оверлей разрушения на них не виден.
 * При установке прогресса разрушения на ведомую ячейку зеркалим прогресс на мастер,
 * чья модель накрывает весь footprint -> трещины проступают по всей короне.
 * Мастер не несёт DrillCrownPartBlockEntity -> зеркальный вызов не рекурсирует.
 *
 * Диагностика: LOG.info печатает в лог при каждом зеркалировании. Если при сломе
 * ведомого в логе НЕТ строк "[TD-crack]" -> миксин не вызывается (не применился или
 * destroyBlockProgress не зовётся для локального игрока). Если строки ЕСТЬ, а трещин
 * нет -> проблема на стороне отрисовки, копаем дальше.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    private static final Logger LOG = LoggerFactory.getLogger("terra_diver-crack");

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
                LOG.info("[TD-crack] ведомая {} -> мастер {} прогресс {}", pos, masterPos, progress);
                // Отдельный breakerId (отрицательный), чтобы запись мастера не вытесняла запись ведомого.
                ((LevelRenderer) (Object) this).destroyBlockProgress(-(breakerId + 1), masterPos, progress);
            }
        }
    }
}