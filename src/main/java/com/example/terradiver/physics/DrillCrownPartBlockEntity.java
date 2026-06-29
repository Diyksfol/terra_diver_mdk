package com.example.terradiver.physics;

import com.example.terradiver.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/*
 * Дочерняя ячейка мультиблок-короны: хранит позицию мастера, чтобы перенаправлять ему
 * ломание/взаимодействие. Лёгкая BE без тика. См. дизайн мультиблока (DrillCrownStructure).
 */
public class DrillCrownPartBlockEntity extends BlockEntity {

    private BlockPos masterPos = BlockPos.ZERO;

    public DrillCrownPartBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.DRILL_CROWN_PART.get(), pos, state);
    }

    public void setMaster(BlockPos master) {
        this.masterPos = master;
        setChanged();
    }

    public BlockPos getMaster() {
        return masterPos;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("master", masterPos.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("master")) {
            masterPos = BlockPos.of(tag.getLong("master"));
        }
    }
}
