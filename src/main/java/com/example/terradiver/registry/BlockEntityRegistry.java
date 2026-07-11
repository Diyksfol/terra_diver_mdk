package com.example.terradiver.registry;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.example.terradiver.physics.DrillCrownPartBlockEntity;
import com.example.terradiver.kinetics.CrownBearingBlockEntity;


public class BlockEntityRegistry {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(
            net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, "terra_diver");

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DrillCrownPartBlockEntity>> DRILL_CROWN_PART =
        BLOCK_ENTITIES.register("drill_crown_part",
                () -> BlockEntityType.Builder.of(DrillCrownPartBlockEntity::new,
                        BlockRegistry.DRILL_CROWN_PART.get()).build(null));

    // Один тип BE на оба подшипника (андезитовый и прочный) — логика одинаковая.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrownBearingBlockEntity>> CROWN_BEARING =
        BLOCK_ENTITIES.register("crown_bearing",
                () -> BlockEntityType.Builder.<CrownBearingBlockEntity>of(
                        (pos, state) -> new CrownBearingBlockEntity(
                                BlockEntityRegistry.CROWN_BEARING.get(), pos, state),
                        BlockRegistry.CROWN_BEARING_ANDESITE.get(),
                        BlockRegistry.CROWN_BEARING_STURDY.get()).build(null));
}
