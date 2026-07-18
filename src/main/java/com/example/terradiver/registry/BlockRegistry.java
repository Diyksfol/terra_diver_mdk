package com.example.terradiver.registry;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.example.terradiver.physics.DrillCrownBlock;
import com.example.terradiver.kinetics.CrownBearingBlock;
import com.example.terradiver.physics.DrillCrownPartBlock;

import net.minecraft.world.level.material.PushReaction;

public class BlockRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("terra_diver");

    private static Block.Properties props() {
        return Block.Properties.ofFullCopy(Blocks.IRON_BLOCK).explosionResistance(1200.0F); // переживают взрывы (как обсидиан)
    }

    // ЭКСПЕРИМЕНТ (#6): тусклое собственное свечение короны, чтобы нижние грани модели не уходили
    // в чёрное, когда под мастер ставят сплошной блок. Настоящий per-face emissive на OBJ-модели
    // просто так не включить, поэтому берём минимальную эмиссию света. Тусклее = меньше значение;
    // 0 полностью выключает эффект (вернёмся к обычному поведению). Крутить одну цифру.
    private static final int CROWN_GLOW = 4; // 0..15 - TUNE (0 = выкл.)

    private static Block.Properties masterProps() {
        return props().noOcclusion().noLootTable().lightLevel(s -> CROWN_GLOW).pushReaction(PushReaction.BLOCK);
    }

    // ==================== DRILL CROWNS 1x1 (мастер мультиблока) ====================
    public static final DeferredHolder<Block, Block> DRILL_CROWN_1x1_COPPER =
            BLOCKS.register("drill_crown_1x1_copper", () -> (Block) new DrillCrownBlock(masterProps(), "1x1", com.example.terradiver.physics.CrownMaterial.COPPER));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_1x1_IRON =
            BLOCKS.register("drill_crown_1x1_iron", () -> (Block) new DrillCrownBlock(masterProps(), "1x1", com.example.terradiver.physics.CrownMaterial.IRON));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_1x1_BRASS =
            BLOCKS.register("drill_crown_1x1_brass", () -> (Block) new DrillCrownBlock(masterProps(), "1x1", com.example.terradiver.physics.CrownMaterial.BRASS));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_1x1_NETHERITE =
            BLOCKS.register("drill_crown_1x1_netherite", () -> (Block) new DrillCrownBlock(masterProps(), "1x1", com.example.terradiver.physics.CrownMaterial.NETHERITE));

    // ==================== DRILL CROWNS 3x3 (мастер мультиблока) ====================
    public static final DeferredHolder<Block, Block> DRILL_CROWN_3x3_COPPER =
            BLOCKS.register("drill_crown_3x3_copper", () -> (Block) new DrillCrownBlock(masterProps(), "3x3", com.example.terradiver.physics.CrownMaterial.COPPER));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_3x3_IRON =
            BLOCKS.register("drill_crown_3x3_iron", () -> (Block) new DrillCrownBlock(masterProps(), "3x3", com.example.terradiver.physics.CrownMaterial.IRON));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_3x3_BRASS =
            BLOCKS.register("drill_crown_3x3_brass", () -> (Block) new DrillCrownBlock(masterProps(), "3x3", com.example.terradiver.physics.CrownMaterial.BRASS));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_3x3_NETHERITE =
            BLOCKS.register("drill_crown_3x3_netherite", () -> (Block) new DrillCrownBlock(masterProps(), "3x3", com.example.terradiver.physics.CrownMaterial.NETHERITE));

    // ==================== DRILL CROWNS 5x5 (мастер мультиблока) ====================
    public static final DeferredHolder<Block, Block> DRILL_CROWN_5x5_COPPER =
            BLOCKS.register("drill_crown_5x5_copper", () -> (Block) new DrillCrownBlock(masterProps(), "5x5", com.example.terradiver.physics.CrownMaterial.COPPER));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_5x5_IRON =
            BLOCKS.register("drill_crown_5x5_iron", () -> (Block) new DrillCrownBlock(masterProps(), "5x5", com.example.terradiver.physics.CrownMaterial.IRON));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_5x5_BRASS =
            BLOCKS.register("drill_crown_5x5_brass", () -> (Block) new DrillCrownBlock(masterProps(), "5x5", com.example.terradiver.physics.CrownMaterial.BRASS));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_5x5_NETHERITE =
            BLOCKS.register("drill_crown_5x5_netherite", () -> (Block) new DrillCrownBlock(masterProps(), "5x5", com.example.terradiver.physics.CrownMaterial.NETHERITE));

    // ==================== DRILL CROWNS 7x7 (мастер мультиблока) ====================
    public static final DeferredHolder<Block, Block> DRILL_CROWN_7x7_COPPER =
            BLOCKS.register("drill_crown_7x7_copper", () -> (Block) new DrillCrownBlock(masterProps(), "7x7", com.example.terradiver.physics.CrownMaterial.COPPER));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_7x7_IRON =
            BLOCKS.register("drill_crown_7x7_iron", () -> (Block) new DrillCrownBlock(masterProps(), "7x7", com.example.terradiver.physics.CrownMaterial.IRON));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_7x7_BRASS =
            BLOCKS.register("drill_crown_7x7_brass", () -> (Block) new DrillCrownBlock(masterProps(), "7x7", com.example.terradiver.physics.CrownMaterial.BRASS));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_7x7_NETHERITE =
            BLOCKS.register("drill_crown_7x7_netherite", () -> (Block) new DrillCrownBlock(masterProps(), "7x7", com.example.terradiver.physics.CrownMaterial.NETHERITE));

    // ==================== DRILL CROWNS 9x9 (мастер мультиблока) ====================
    public static final DeferredHolder<Block, Block> DRILL_CROWN_9x9_COPPER =
            BLOCKS.register("drill_crown_9x9_copper", () -> (Block) new DrillCrownBlock(masterProps(), "9x9", com.example.terradiver.physics.CrownMaterial.COPPER));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_9x9_IRON =
            BLOCKS.register("drill_crown_9x9_iron", () -> (Block) new DrillCrownBlock(masterProps(), "9x9", com.example.terradiver.physics.CrownMaterial.IRON));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_9x9_BRASS =
            BLOCKS.register("drill_crown_9x9_brass", () -> (Block) new DrillCrownBlock(masterProps(), "9x9", com.example.terradiver.physics.CrownMaterial.BRASS));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_9x9_NETHERITE =
            BLOCKS.register("drill_crown_9x9_netherite", () -> (Block) new DrillCrownBlock(masterProps(), "9x9", com.example.terradiver.physics.CrownMaterial.NETHERITE));

    // ==================== DRILL CROWNS 11x11 (мастер мультиблока) ====================
    public static final DeferredHolder<Block, Block> DRILL_CROWN_11x11_COPPER =
            BLOCKS.register("drill_crown_11x11_copper", () -> (Block) new DrillCrownBlock(masterProps(), "11x11", com.example.terradiver.physics.CrownMaterial.COPPER));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_11x11_IRON =
            BLOCKS.register("drill_crown_11x11_iron", () -> (Block) new DrillCrownBlock(masterProps(), "11x11", com.example.terradiver.physics.CrownMaterial.IRON));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_11x11_BRASS =
            BLOCKS.register("drill_crown_11x11_brass", () -> (Block) new DrillCrownBlock(masterProps(), "11x11", com.example.terradiver.physics.CrownMaterial.BRASS));
    public static final DeferredHolder<Block, Block> DRILL_CROWN_11x11_NETHERITE =
            BLOCKS.register("drill_crown_11x11_netherite", () -> (Block) new DrillCrownBlock(masterProps(), "11x11", com.example.terradiver.physics.CrownMaterial.NETHERITE));

    // ==================== MULTIBLOCK PART (внутренний, невидимый) ====================
    public static final DeferredHolder<Block, DrillCrownPartBlock> DRILL_CROWN_PART =
            BLOCKS.register("drill_crown_part",
                    () -> new DrillCrownPartBlock(props().noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK)));

    // ==================== OTHER BLOCKS ====================
    public static final DeferredHolder<Block, Block> CROWN_BEARING_ANDESITE =
            BLOCKS.register("crown_bearing_andesite", () -> new CrownBearingBlock(props().noOcclusion()));
    public static final DeferredHolder<Block, Block> CROWN_BEARING_STURDY =
            BLOCKS.register("crown_bearing_sturdy", () -> new CrownBearingBlock(props().noOcclusion()));
    public static final DeferredHolder<Block, Block> PRESSURE_GAUGE =
            BLOCKS.register("pressure_gauge", () -> new Block(props()));
    public static final DeferredHolder<Block, Block> TERRADAR =
            BLOCKS.register("terradar", () -> new Block(props()));
    public static final DeferredHolder<Block, Block> CARTOGRAPH_CONSOLE =
            BLOCKS.register("cartograph_console", () -> new Block(props()));
}       