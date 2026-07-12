package com.example.terradiver.client;

import com.example.terradiver.physics.DrillCrownStructure;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/*
 * Клиентская зачистка «прогресса ломания» (трещин) на ячейках короны.
 * Зачем: у ведомых блоков есть коллизия, поэтому по краям игрок часто ломает НЕ мастер, а ведомую.
 * Сервер шлёт прогресс ломания и самому ломающему тоже; когда корона на том же месте переустановлена,
 * ПОЗДНИЙ такой пакет может воссоздать оверлей трещин уже после разовой очистки, и трещины «липнут»
 * к новому буру до первого ЛКМ. Поэтому гасим прогресс не разово, а несколько тиков подряд после того,
 * как клиент узнал о короне (см. requestClear). Триггерим из синка данных ведомой — это надёжный
 * клиентский путь, в отличие от onPlace, который на клиенте при блок-апдейте может не вызываться.
 *
 * destroyBlockProgress(id, pos, -1) удаляет запись прогресса игрока ЦЕЛИКОМ (ветка удаления в
 * LevelRenderer игнорирует pos), поэтому одной ячейки достаточно; чистим только запись ЛОКАЛЬНОГО
 * игрока и только когда он сам ничего не ломает, чтобы не стереть его законный оверлей добычи.
 *
 * Класс клиентский (Dist.CLIENT): на выделенном сервере не загружается.
 */
@EventBusSubscriber(modid = "terra_diver", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ClientCrownEffects {

    private ClientCrownEffects() {
    }

    // Сколько клиентских тиков после появления короны продолжаем гасить прогресс — с запасом на
    // задержку прихода поздних пакетов прогресса от сервера.
    private static final int CLEAR_TICKS = 10;
    private static int clearTicks = 0;

    // Запросить очистку: держим её активной CLEAR_TICKS тиков.
    public static void requestClear() {
        clearTicks = CLEAR_TICKS;
    }

    // Разовая очистка при установке мастера (если onPlace на клиенте всё же вызывается) + запуск
    // отложенной очистки. Проходим все ячейки короны у локального игрока.
    public static void clearBreakProgress(BlockPos masterPos, String size, Direction facing) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.levelRenderer != null) {
            int id = mc.player.getId();
            for (BlockPos cell : DrillCrownStructure.worldCells(size, facing, masterPos)) {
                mc.levelRenderer.destroyBlockProgress(id, cell, -1);
            }
        }
        requestClear();
    }

    // Отложенное добивание: пока активно, каждый тик снимаем залипший прогресс у локального игрока,
    // НО только если он сейчас сам ничего не ломает (иначе стёрли бы его законный оверлей добычи).
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (clearTicks <= 0) {
            return;
        }
        clearTicks--;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.levelRenderer == null) {
            return;
        }
        if (mc.gameMode != null && mc.gameMode.isDestroying()) {
            return;
        }
        // Снимаем ОБЕ записи прогресса у локального игрока (pos для ветки удаления не важен):
        //  - обычную (его id) — от прямого ломания ведомой/мастера;
        //  - ЗЕРКАЛЬНУЮ на мастере под id -(id+1) — её ставит наш LevelRendererMixin, чтобы трещины
        //    проступали по всей короне; именно она оставалась «залипшей» на переустановленном буре.
        // pos = позиция игрока (не ячейка короны), чтобы миксин на HEAD не зеркалил снова.
        int id = mc.player.getId();
        BlockPos here = mc.player.blockPosition();
        mc.levelRenderer.destroyBlockProgress(id, here, -1);
        mc.levelRenderer.destroyBlockProgress(-(id + 1), here, -1);
    }
}
