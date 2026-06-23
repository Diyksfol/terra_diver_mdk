package com.example.terradiver.registry;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.DeferredHolder;

public class BlockRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("terra_diver");

    // ==================== DRILL CROWNS 1x1 ====================
    public static final DeferredHolder<Block, Block> DRILL_CROWN_1x1_COPPER =
            BLOCKS.register("drill_crown_1x1_copper", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_1x1_IRON =
            BLOCKS.register("drill_crown_1x1_iron", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_1x1_BRASS =
            BLOCKS.register("drill_crown_1x1_brass", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_1x1_NETHERITE =
            BLOCKS.register("drill_crown_1x1_netherite", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));

    // ==================== DRILL CROWNS 3x3 ====================
    public static final DeferredHolder<Block, Block> DRILL_CROWN_3x3_COPPER =
            BLOCKS.register("drill_crown_3x3_copper", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_3x3_IRON =
            BLOCKS.register("drill_crown_3x3_iron", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_3x3_BRASS =
            BLOCKS.register("drill_crown_3x3_brass", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_3x3_NETHERITE =
            BLOCKS.register("drill_crown_3x3_netherite", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));

    // ==================== DRILL CROWNS 5x5 ====================
    public static final DeferredHolder<Block, Block> DRILL_CROWN_5x5_COPPER =
            BLOCKS.register("drill_crown_5x5_copper", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_5x5_IRON =
            BLOCKS.register("drill_crown_5x5_iron", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_5x5_BRASS =
            BLOCKS.register("drill_crown_5x5_brass", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_5x5_NETHERITE =
            BLOCKS.register("drill_crown_5x5_netherite", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));

    // ==================== DRILL CROWNS 7x7 ====================
    public static final DeferredHolder<Block, Block> DRILL_CROWN_7x7_COPPER =
            BLOCKS.register("drill_crown_7x7_copper", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_7x7_IRON =
            BLOCKS.register("drill_crown_7x7_iron", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_7x7_BRASS =
            BLOCKS.register("drill_crown_7x7_brass", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_7x7_NETHERITE =
            BLOCKS.register("drill_crown_7x7_netherite", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));

    // ==================== DRILL CROWNS 9x9 ====================
    public static final DeferredHolder<Block, Block> DRILL_CROWN_9x9_COPPER =
            BLOCKS.register("drill_crown_9x9_copper", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_9x9_IRON =
            BLOCKS.register("drill_crown_9x9_iron", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_9x9_BRASS =
            BLOCKS.register("drill_crown_9x9_brass", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_9x9_NETHERITE =
            BLOCKS.register("drill_crown_9x9_netherite", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));

    // ==================== DRILL CROWNS 11x11 ====================
    public static final DeferredHolder<Block, Block> DRILL_CROWN_11x11_COPPER =
            BLOCKS.register("drill_crown_11x11_copper", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_11x11_IRON =
            BLOCKS.register("drill_crown_11x11_iron", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_11x11_BRASS =
            BLOCKS.register("drill_crown_11x11_brass", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_11x11_NETHERITE =
            BLOCKS.register("drill_crown_11x11_netherite", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));

    // ==================== OTHER BLOCKS ====================
    public static final DeferredHolder<Block, Block> CROWN_BEARING_ANDESITE =
            BLOCKS.register("crown_bearing_andesite", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredHolder<Block, Block> CROWN_BEARING_STURDY =
            BLOCKS.register("crown_bearing_sturdy", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredHolder<Block, Block> PRESSURE_GAUGE =
            BLOCKS.register("pressure_gauge", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredHolder<Block, Block> TERRADAR =
            BLOCKS.register("terradar", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    public static final DeferredHolder<Block, Block> CARTOGRAPH_CONSOLE =
            BLOCKS.register("cartograph_console", () -> new Block(Block.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
}
