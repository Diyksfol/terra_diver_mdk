package com.example.terradiver.client;

import com.example.terradiver.registry.BlockEntityRegistry;
import com.simibubi.create.content.contraptions.bearing.BearingRenderer;
import com.simibubi.create.content.contraptions.bearing.BearingVisual;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = "terra_diver", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onRegisterKeymaps(RegisterKeyMappingsEvent event) {
        // Placeholder for future key bindings
    }

    // Рендер бурового подшипника берём у механического подшипника Create: BearingRenderer рисует
    // вращающуюся верхнюю шапку (bearing/top) и входной вал (shaft_half) сзади. Это фолбэк, когда
    // Flywheel выключен (сам рендерер при активном Flywheel выходит в начале).
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlockEntityRegistry.CROWN_BEARING.get(), BearingRenderer::new);
    }

    // Основной путь рендера (Flywheel включён по умолчанию): визуал подшипника рисует ту же
    // вращающуюся шапку и вал как GPU-инстансы. Без него при Flywheel не было бы ни шапки, ни вала.
    // База блока (наша модель) рендерится как обычно чанком — vanilla render не пропускаем.
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        SimpleBlockEntityVisualizer.builder(BlockEntityRegistry.CROWN_BEARING.get())
                .factory(BearingVisual::new)
                .skipVanillaRender(be -> false)
                .apply();
    }
}
