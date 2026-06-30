package com.example.terradiver.physics;

import com.example.terradiver.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/*
 * Дочерняя ячейка мультиблок-короны: хранит позицию мастера (для разборки) и данные формы своей
 * ячейки — размер, смещение в модельном пространстве, направление. Форма (полный куб или точные
 * краевые суб-воксели) строится лениво и кэшируется. См. DrillCrownStructure / CrownShapes.
 */
public class DrillCrownPartBlockEntity extends BlockEntity {

    private BlockPos masterPos = BlockPos.ZERO;
    private String size = "3x3";
    private int ox, oy, oz;
    private Direction facing = Direction.UP;

    private VoxelShape cachedShape; // ленивый кэш

    public DrillCrownPartBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.DRILL_CROWN_PART.get(), pos, state);
    }

    public void setMaster(BlockPos master) {
        this.masterPos = master;
        setChanged();
    }

    // Данные формы ячейки (зовётся предметом при постановке).
    public void setShapeData(String size, int ox, int oy, int oz, Direction facing) {
        this.size = size;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.facing = facing;
        this.cachedShape = null;
        setChanged();
    }

    public BlockPos getMaster() {
        return masterPos;
    }

    public VoxelShape getShape() {
        if (cachedShape == null) {
            cachedShape = CrownShapes.build(
                DrillCrownStructure.cellShapeBoxes(size, ox, oy, oz), facing);
        }
        return cachedShape;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("master", masterPos.asLong());
        tag.putString("size", size);
        tag.putInt("ox", ox);
        tag.putInt("oy", oy);
        tag.putInt("oz", oz);
        tag.putInt("facing", facing.get3DDataValue());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("master")) {
            masterPos = BlockPos.of(tag.getLong("master"));
        }
        if (tag.contains("size")) {
            size = tag.getString("size");
            ox = tag.getInt("ox");
            oy = tag.getInt("oy");
            oz = tag.getInt("oz");
            facing = Direction.from3DDataValue(tag.getInt("facing"));
            cachedShape = null;
        }
    }
}