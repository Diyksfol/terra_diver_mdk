package com.example.terradiver;

import org.slf4j.Logger;

import com.example.terradiver.config.ModConfig;
import com.example.terradiver.registry.BlockEntityRegistry;
import com.example.terradiver.registry.BlockRegistry;
import com.example.terradiver.registry.CreativeTabs;
import com.example.terradiver.registry.ItemRegistry;
import com.mojang.logging.LogUtils;

import net.neoforged.api.distmarker.Dist;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod("terra_diver")
public class TerraDiver {
    public static final String MODID = "terra_diver";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TerraDiver(IEventBus modEventBus, ModContainer modContainer) {
        // Register config
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, ModConfig.SPEC, "terra_diver-common.toml");

        // Register the deferred registers to the mod event bus
        BlockRegistry.BLOCKS.register(modEventBus);
        BlockEntityRegistry.BLOCK_ENTITIES.register(modEventBus);
        ItemRegistry.ITEMS.register(modEventBus);
        CreativeTabs.CREATIVE_TABS.register(modEventBus);

        // Register the common setup event
        modEventBus.addListener(this::commonSetup);

        // Register server starting event - register static methods
        NeoForge.EVENT_BUS.register(TerraDiver.class);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Terra Diver mod loaded!");
        event.enqueueWork(TerraDiver::registerContraptionAttachment);
    }

    // Короны (мастер + ведомые) должны ехать в контраптии Create единым целым: тогда бур вращается/
    // двигается полностью и сталкивается с СУЩНОСТЯМИ (ContraptionCollider берёт коллизию блоков).
    // «Прозрачность к БЛОКАМ» (проход сквозь породу) — это слой ДВИЖЕНИЯ (Sable/погружение), а не
    // форма коллизии, поэтому здесь его не трогаем. Тут только «склеиваем» короны между собой, чтобы
    // подшипник захватывал всю корону, а не один мастер (иначе ведомые не сталкиваются с сущностями).
    private static void registerContraptionAttachment() {
        BlockMovementChecks.registerAttachedCheck((state, world, pos, direction) -> {
            if (isCrown(state) && isCrown(world.getBlockState(pos.relative(direction)))) {
                return BlockMovementChecks.CheckResult.SUCCESS;
            }
            return BlockMovementChecks.CheckResult.PASS;
        });
        BlockMovementChecks.registerMovementNecessaryCheck((state, world, pos) ->
                isCrown(state) ? BlockMovementChecks.CheckResult.SUCCESS : BlockMovementChecks.CheckResult.PASS);
    }

    private static boolean isCrown(net.minecraft.world.level.block.state.BlockState state) {
        return state.getBlock() instanceof com.example.terradiver.physics.DrillCrownBlock
                || state.getBlock() instanceof com.example.terradiver.physics.DrillCrownPartBlock;
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Terra Diver server starting!");
    }
}
