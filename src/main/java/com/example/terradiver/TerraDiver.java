package com.example.terradiver;

import org.slf4j.Logger;

import com.example.terradiver.registry.BlockEntityRegistry;
import com.example.terradiver.registry.BlockRegistry;
import com.example.terradiver.registry.ItemRegistry;
import com.mojang.logging.LogUtils;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod("terra_diver")
public class TerraDiver {
    public static final String MODID = "terra_diver";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TerraDiver(IEventBus modEventBus) {
        // Register the deferred registers to the mod event bus
        BlockRegistry.BLOCKS.register(modEventBus);
        BlockEntityRegistry.BLOCK_ENTITIES.register(modEventBus);
        ItemRegistry.ITEMS.register(modEventBus);

        // Register the common setup event
        modEventBus.addListener(this::commonSetup);

        // Register server starting event - register static methods
        NeoForge.EVENT_BUS.register(TerraDiver.class);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Terra Diver mod loaded!");
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Terra Diver server starting!");
    }
}
