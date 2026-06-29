package com.example.terradiver.registry;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.example.terradiver.physics.DrillCrownPartBlockEntity;


public class BlockEntityRegistry {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(
            net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, "terra_diver");

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DrillCrownPartBlockEntity>> DRILL_CROWN_PART =
        BLOCK_ENTITIES.register("drill_crown_part",
                () -> BlockEntityType.Builder.of(DrillCrownPartBlockEntity::new,
                        BlockRegistry.DRILL_CROWN_PART.get()).build(null));
}
