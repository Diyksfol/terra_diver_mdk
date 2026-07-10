package com.example.terradiver.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.example.terradiver.physics.DrillCrownItem;

public class ItemRegistry {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("terra_diver");

    // ==================== DRILL CROWN 1x1 ITEMS (ставят структуру) ====================
    public static final DeferredHolder<Item, Item> DRILL_CROWN_1x1_COPPER =
            ITEMS.register("drill_crown_1x1_copper", () -> new DrillCrownItem(BlockRegistry.DRILL_CROWN_1x1_COPPER.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> DRILL_CROWN_1x1_IRON =
            ITEMS.register("drill_crown_1x1_iron", () -> new DrillCrownItem(BlockRegistry.DRILL_CROWN_1x1_IRON.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> DRILL_CROWN_1x1_BRASS =
            ITEMS.register("drill_crown_1x1_brass", () -> new DrillCrownItem(BlockRegistry.DRILL_CROWN_1x1_BRASS.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> DRILL_CROWN_1x1_NETHERITE =
            ITEMS.register("drill_crown_1x1_netherite", () -> new DrillCrownItem(BlockRegistry.DRILL_CROWN_1x1_NETHERITE.get(), new Item.Properties()));

    // ==================== DRILL CROWN 3x3 ITEMS (ставят структуру) ====================
    public static final DeferredHolder<Item, Item> DRILL_CROWN_3x3_COPPER =
            ITEMS.register("drill_crown_3x3_copper", () -> new DrillCrownItem(BlockRegistry.DRILL_CROWN_3x3_COPPER.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> DRILL_CROWN_3x3_IRON =
            ITEMS.register("drill_crown_3x3_iron", () -> new DrillCrownItem(BlockRegistry.DRILL_CROWN_3x3_IRON.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> DRILL_CROWN_3x3_BRASS =
            ITEMS.register("drill_crown_3x3_brass", () -> new DrillCrownItem(BlockRegistry.DRILL_CROWN_3x3_BRASS.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> DRILL_CROWN_3x3_NETHERITE =
            ITEMS.register("drill_crown_3x3_netherite", () -> new DrillCrownItem(BlockRegistry.DRILL_CROWN_3x3_NETHERITE.get(), new Item.Properties()));

    // ==================== DRILL CROWN 5x5 ITEMS (ставят структуру) ====================
    public static final DeferredHolder<Item, Item> DRILL_CROWN_5x5_COPPER =
            ITEMS.register("drill_crown_5x5_copper", () -> new DrillCrownItem(BlockRegistry.DRILL_CROWN_5x5_COPPER.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> DRILL_CROWN_5x5_IRON =
            ITEMS.register("drill_crown_5x5_iron", () -> new DrillCrownItem(BlockRegistry.DRILL_CROWN_5x5_IRON.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> DRILL_CROWN_5x5_BRASS =
            ITEMS.register("drill_crown_5x5_brass", () -> new DrillCrownItem(BlockRegistry.DRILL_CROWN_5x5_BRASS.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> DRILL_CROWN_5x5_NETHERITE =
            ITEMS.register("drill_crown_5x5_netherite", () -> new DrillCrownItem(BlockRegistry.DRILL_CROWN_5x5_NETHERITE.get(), new Item.Properties()));

    // ==================== DRILL CROWN 7x7 ITEMS (ставят структуру) ====================
    public static final DeferredHolder<Item, Item> DRILL_CROWN_7x7_COPPER =
            ITEMS.register("drill_crown_7x7_copper", () -> new DrillCrownItem(BlockRegistry.DRILL_CROWN_7x7_COPPER.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> DRILL_CROWN_7x7_IRON =
            ITEMS.register("drill_crown_7x7_iron", () -> new DrillCrownItem(BlockRegistry.DRILL_CROWN_7x7_IRON.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> DRILL_CROWN_7x7_BRASS =
            ITEMS.register("drill_crown_7x7_brass", () -> new DrillCrownItem(BlockRegistry.DRILL_CROWN_7x7_BRASS.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> DRILL_CROWN_7x7_NETHERITE =
            ITEMS.register("drill_crown_7x7_netherite", () -> new DrillCrownItem(BlockRegistry.DRILL_CROWN_7x7_NETHERITE.get(), new Item.Properties()));

    // ==================== DRILL CROWN 9x9 ITEMS (ставят структуру) ====================
    public static final DeferredHolder<Item, Item> DRILL_CROWN_9x9_COPPER =
            ITEMS.register("drill_crown_9x9_copper", () -> new DrillCrownItem(BlockRegistry.DRILL_CROWN_9x9_COPPER.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> DRILL_CROWN_9x9_IRON =
            ITEMS.register("drill_crown_9x9_iron", () -> new DrillCrownItem(BlockRegistry.DRILL_CROWN_9x9_IRON.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> DRILL_CROWN_9x9_BRASS =
            ITEMS.register("drill_crown_9x9_brass", () -> new DrillCrownItem(BlockRegistry.DRILL_CROWN_9x9_BRASS.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> DRILL_CROWN_9x9_NETHERITE =
            ITEMS.register("drill_crown_9x9_netherite", () -> new DrillCrownItem(BlockRegistry.DRILL_CROWN_9x9_NETHERITE.get(), new Item.Properties()));

    // ==================== DRILL CROWN 11x11 ITEMS (ставят структуру) ====================
    public static final DeferredHolder<Item, Item> DRILL_CROWN_11x11_COPPER =
            ITEMS.register("drill_crown_11x11_copper", () -> new DrillCrownItem(BlockRegistry.DRILL_CROWN_11x11_COPPER.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> DRILL_CROWN_11x11_IRON =
            ITEMS.register("drill_crown_11x11_iron", () -> new DrillCrownItem(BlockRegistry.DRILL_CROWN_11x11_IRON.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> DRILL_CROWN_11x11_BRASS =
            ITEMS.register("drill_crown_11x11_brass", () -> new DrillCrownItem(BlockRegistry.DRILL_CROWN_11x11_BRASS.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> DRILL_CROWN_11x11_NETHERITE =
            ITEMS.register("drill_crown_11x11_netherite", () -> new DrillCrownItem(BlockRegistry.DRILL_CROWN_11x11_NETHERITE.get(), new Item.Properties()));

    // ==================== OTHER BLOCK ITEMS ====================
    public static final DeferredHolder<Item, Item> CROWN_BEARING_ANDESITE =
            ITEMS.register("crown_bearing_andesite", () -> new BlockItem(BlockRegistry.CROWN_BEARING_ANDESITE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> CROWN_BEARING_STURDY =
            ITEMS.register("crown_bearing_sturdy", () -> new BlockItem(BlockRegistry.CROWN_BEARING_STURDY.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> PRESSURE_GAUGE =
            ITEMS.register("pressure_gauge", () -> new BlockItem(BlockRegistry.PRESSURE_GAUGE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> TERRADAR =
            ITEMS.register("terradar", () -> new BlockItem(BlockRegistry.TERRADAR.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> CARTOGRAPH_CONSOLE =
            ITEMS.register("cartograph_console", () -> new BlockItem(BlockRegistry.CARTOGRAPH_CONSOLE.get(), new Item.Properties()));

    // ==================== CRAFTING ITEMS ====================
    public static final DeferredHolder<Item, Item> PIEZO_ELEMENT =
            ITEMS.register("piezo_element", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> DRILL_MODULE =
            ITEMS.register("drill_module", () -> new Item(new Item.Properties()));
    // Переходный предмет для create:sequenced_assembly (Буровой модуль). Появляется на конвейере
    // между проходами; Create требует, чтобы transitional_item был реально зарегистрирован.
    public static final DeferredHolder<Item, Item> INCOMPLETE_DRILL_MODULE =
            ITEMS.register("incomplete_drill_module", () -> new Item(new Item.Properties()));
}