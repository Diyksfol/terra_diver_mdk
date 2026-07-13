package com.example.terradiver.physics;

import com.example.terradiver.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
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
    private Direction cachedFacing;  // при какой ориентации собран кэш

    public DrillCrownPartBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.DRILL_CROWN_PART.get(), pos, state);
    }

    public void setMaster(BlockPos master) {
        this.masterPos = master;
        this.cachedShape = null; // положение относительно мастера изменилось — форма пересоберётся
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
        if (level != null && !level.isClientSide) {
            // Синхронизировать форму на клиент: контур и клиентская коллизия зовут getShape на клиенте.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public BlockPos getMaster() {
        return masterPos;
    }

    public VoxelShape getShape() {
        // Форму ячейки строим по её ФАКТИЧЕСКОМУ положению относительно мастера, а не по сохранённому
        // смещению: тогда после любого поворота/крена, наложенного чужой механикой (подшипник, Sable),
        // октант тела вращения follows реальное положение и совпадает с повёрнутой моделью. Сторону
        // берём живой у мастера. Кэш по стороне; при смене мастера (setMaster) сбрасывается.
        Direction f = currentFacing();
        if (cachedShape == null || f != cachedFacing) {
            int[] o = geometricOffset(f);
            cachedShape = CrownShapes.build(
                DrillCrownStructure.cellShapeBoxes(size, o[0], o[1], o[2]), f);
            cachedFacing = f;
        }
        return cachedShape;
    }

    // Каноническое смещение ячейки по её фактическому положению (worldPos - master) при текущей
    // стороне. Если положение не распознано (мастер не на месте) — сохранённое при постройке.
    private int[] geometricOffset(Direction f) {
        int dx = worldPosition.getX() - masterPos.getX();
        int dy = worldPosition.getY() - masterPos.getY();
        int dz = worldPosition.getZ() - masterPos.getZ();
        int[] o = DrillCrownStructure.inverseCell(size, f, dx, dy, dz);
        return o != null ? o : new int[]{ ox, oy, oz };
    }

    // Текущая сторона мастера (если он на месте), иначе — сохранённая при постройке.
    private Direction currentFacing() {
        if (level != null) {
            BlockState ms = level.getBlockState(masterPos);
            if (ms.getBlock() instanceof DrillCrownBlock) {
                return ms.getValue(DrillCrownBlock.FACING);
            }
        }
        return facing;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries); // включить size/смещение/facing в пакет чанка
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this); // присылает getUpdateTag клиенту
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection,
                             ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        // Дефолтный onDataPacket пустой — без этого данные формы/мастера не применяются на клиенте.
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            loadAdditional(tag, registries);
        }
        cachedShape = null; // пересобрать форму с новыми данными
        // Клиент узнал о ячейке короны → это НАДЁЖНЫЙ клиентский триггер (в отличие от onPlace,
        // который при блок-апдейте с сервера может не вызываться). Просим погасить залипший прогресс
        // ломания у локального игрока на несколько тиков — иначе переустановленный бур появляется «с
        // трещинами» от прошлой добычи. См. ClientCrownEffects.
        com.example.terradiver.client.ClientCrownEffects.requestClear();
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