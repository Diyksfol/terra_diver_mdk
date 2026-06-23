package com.example.terradiver.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "terra_diver");

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TERRA_DIVER_TAB = CREATIVE_TABS.register("terra_diver_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.literal("Terra Diver"))
                    .withTabsBefore(net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES)
                    .icon(() -> BlockRegistry.DRILL_CROWN_1x1_COPPER.get().asItem().getDefaultInstance())
                    .displayItems((params, output) -> {
                        // Add all drill crowns
                        output.accept(BlockRegistry.DRILL_CROWN_1x1_COPPER.get());
                        output.accept(BlockRegistry.DRILL_CROWN_1x1_IRON.get());
                        output.accept(BlockRegistry.DRILL_CROWN_1x1_BRASS.get());
                        output.accept(BlockRegistry.DRILL_CROWN_1x1_NETHERITE.get());

                        output.accept(BlockRegistry.DRILL_CROWN_3x3_COPPER.get());
                        output.accept(BlockRegistry.DRILL_CROWN_3x3_IRON.get());
                        output.accept(BlockRegistry.DRILL_CROWN_3x3_BRASS.get());
                        output.accept(BlockRegistry.DRILL_CROWN_3x3_NETHERITE.get());

                        output.accept(BlockRegistry.DRILL_CROWN_5x5_COPPER.get());
                        output.accept(BlockRegistry.DRILL_CROWN_5x5_IRON.get());
                        output.accept(BlockRegistry.DRILL_CROWN_5x5_BRASS.get());
                        output.accept(BlockRegistry.DRILL_CROWN_5x5_NETHERITE.get());

                        output.accept(BlockRegistry.DRILL_CROWN_7x7_COPPER.get());
                        output.accept(BlockRegistry.DRILL_CROWN_7x7_IRON.get());
                        output.accept(BlockRegistry.DRILL_CROWN_7x7_BRASS.get());
                        output.accept(BlockRegistry.DRILL_CROWN_7x7_NETHERITE.get());

                        output.accept(BlockRegistry.DRILL_CROWN_9x9_COPPER.get());
                        output.accept(BlockRegistry.DRILL_CROWN_9x9_IRON.get());
                        output.accept(BlockRegistry.DRILL_CROWN_9x9_BRASS.get());
                        output.accept(BlockRegistry.DRILL_CROWN_9x9_NETHERITE.get());

                        output.accept(BlockRegistry.DRILL_CROWN_11x11_COPPER.get());
                        output.accept(BlockRegistry.DRILL_CROWN_11x11_IRON.get());
                        output.accept(BlockRegistry.DRILL_CROWN_11x11_BRASS.get());
                        output.accept(BlockRegistry.DRILL_CROWN_11x11_NETHERITE.get());

                        // Add other blocks
                        output.accept(BlockRegistry.CROWN_BEARING_ANDESITE.get());
                        output.accept(BlockRegistry.CROWN_BEARING_STURDY.get());
                        output.accept(BlockRegistry.PRESSURE_GAUGE.get());
                        output.accept(BlockRegistry.TERRADAR.get());
                        output.accept(BlockRegistry.CARTOGRAPH_CONSOLE.get());

                        // Add crafting items
                        output.accept(ItemRegistry.PIEZO_ELEMENT.get());
                        output.accept(ItemRegistry.DRILL_MODULE.get());
                    })
                    .build()
    );
}
