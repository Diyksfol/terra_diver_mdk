package com.example.terradiver.client;

import com.example.terradiver.physics.DrillCrownStructure;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/*
 * Клиентская зачистка «прогресса ломания» (трещин) на ячейках короны при её установке.
 * Зачем: у ведомых блоков есть коллизия, поэтому по краям игрок часто ломает НЕ мастер, а ведомую.
 * Сервер при рассылке очистки прогресса исключает самого ломающего (он предсказывает локально),
 * поэтому его локальный оверлей трещин не гасится и «прилипает» к позиции. При установке новой
 * короны на то же место гасим локальный оверлей у всех её ячеек для локального игрока.
 *
 * Класс клиентский: вызывается только из ветки level.isClientSide, поэтому на выделенном сервере
 * не загружается (Minecraft-класс не трогается).
 */
public final class ClientCrownEffects {

    private ClientCrownEffects() {
    }

    public static void clearBreakProgress(BlockPos masterPos, String size, Direction facing) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.levelRenderer == null) {
            return;
        }
        int id = mc.player.getId();
        for (BlockPos cell : DrillCrownStructure.worldCells(size, facing, masterPos)) {
            // progress = -1 → запись удаляется (см. LevelRenderer.destroyBlockProgress)
            mc.levelRenderer.destroyBlockProgress(id, cell, -1);
        }
    }
}
