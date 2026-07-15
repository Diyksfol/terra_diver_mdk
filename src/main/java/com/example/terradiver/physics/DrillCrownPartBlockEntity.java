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
    private int cachedRoll = -1;     // при каком крене собран кэш
    private BlockPos cachedShapeMaster; // от какого мастера считался октант

    public DrillCrownPartBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.DRILL_CROWN_PART.get(), pos, state);
    }

    public void setMaster(BlockPos master) {
        this.masterPos = master;
        this.cachedMaster = null; // перепроверить/переискать мастера обходом
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

    private BlockPos cachedMaster = null;

    // Мастера ищем ОБХОДОМ смежных ячеек короны (BFS), а не по сохранённому абсолютному masterPos.
    // Причина: когда бур становится физштуковиной, его ячейки живут в СУБЛЕВЕЛЕ Sable (др. координаты),
    // и абсолютный masterPos устаревает → ломание ведомой не находит мастера и не сносит корону.
    // Смежность же сохраняется при любом жёстком переносе и в любом уровне, поэтому поиск надёжен.
    // Кэшируем; сбрасываем, если в кэше уже не мастер.
    public BlockPos getMaster() {
        if (level != null) {
            if (cachedMaster == null || !isMyMaster(cachedMaster)) {
                // Сохранённый masterPos пробуем ПЕРВЫМ (обычно он верен), обход — запасной путь.
                cachedMaster = isMyMaster(masterPos) ? masterPos : searchMaster();
            }
            if (cachedMaster != null) {
                return cachedMaster;
            }
        }
        return masterPos; // запас, если обойти не удалось
    }

    // Кандидат годится, только если это мастер МОЕЙ короны: тот же размер И моя ячейка реально входит
    // в его футпринт. Иначе, когда рядом стоят два бура (3x3 и 1x1), ведомая цепляла ЧУЖОГО мастера —
    // первого попавшегося при обходе — и брала сторону/октант от него. Отсюда «часть блоков коллизии
    // повёрнута не туда», причём невоспроизводимо: зависело от порядка обхода.
    private boolean isMyMaster(BlockPos p) {
        if (level == null || p == null) {
            return false;
        }
        BlockState ms = level.getBlockState(p);
        if (!(ms.getBlock() instanceof DrillCrownBlock crown) || !size.equals(crown.crownSize())) {
            return false;
        }
        return DrillCrownStructure.inverseCell(size, ms.getValue(DrillCrownBlock.FACING),
                worldPosition.getX() - p.getX(),
                worldPosition.getY() - p.getY(),
                worldPosition.getZ() - p.getZ()) != null;
    }

    private BlockPos searchMaster() {
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        seen.add(worldPosition);
        q.add(worldPosition);
        int cap = 512; // страховка от разрастания
        while (!q.isEmpty() && cap-- > 0) {
            BlockPos p = q.poll();
            BlockState st = level.getBlockState(p);
            if (st.getBlock() instanceof DrillCrownBlock && isMyMaster(p)) {
                return p; // мастер МОЕЙ короны найден
            }
            // Идём дальше только по ячейкам короны (сама ведомая или соседние части).
            if (p.equals(worldPosition) || st.getBlock() instanceof DrillCrownPartBlock) {
                for (Direction d : Direction.values()) {
                    BlockPos n = p.relative(d);
                    if (seen.add(n)) {
                        q.add(n);
                    }
                }
            }
        }
        return null;
    }

    public VoxelShape getShape() {
        // Октант ячейки выводим из её ФАКТИЧЕСКОГО положения относительно мастера, а не из
        // сохранённого смещения: rotate(off,facing) не переживает поворот структуры чужой механикой
        // (ячейка уезжает не в ту клетку, где её ждёт раскладка новой стороны), и сохранённое смещение
        // становится чужим — отсюда «блоки на местах, но повёрнуты не туда». Мастера ищем обходом
        // (getMaster), поэтому работает и в сублевеле Sable. Кэш — по паре (сторона, крен).
        Direction f = currentFacing();
        int r = currentRoll();
        BlockPos m = getMaster();
        if (cachedShape == null || f != cachedFacing || r != cachedRoll || !m.equals(cachedShapeMaster)) {
            cachedShapeMaster = m;
            int[] o = geometricOffset(f);
            cachedShape = CrownShapes.build(
                DrillCrownStructure.cellShapeBoxes(size, o[0], o[1], o[2]), f, r);
            cachedFacing = f;
            cachedRoll = r;
        }
        return cachedShape;
    }

    // Каноническое смещение по фактическому положению ячейки; если не распознано — сохранённое.
    private int[] geometricOffset(Direction f) {
        BlockPos m = getMaster();
        int[] o = DrillCrownStructure.inverseCell(size, f,
                worldPosition.getX() - m.getX(),
                worldPosition.getY() - m.getY(),
                worldPosition.getZ() - m.getZ());
        return o != null ? o : new int[]{ ox, oy, oz };
    }

    // Крен мастера (0..3); если мастера нет — 0.
    private int currentRoll() {
        if (level != null) {
            BlockState ms = level.getBlockState(getMaster());
            if (ms.getBlock() instanceof DrillCrownBlock) {
                return ms.getValue(DrillCrownBlock.ROLL);
            }
        }
        return 0;
    }

    // Текущая сторона мастера (если он на месте), иначе — сохранённая при постройке.
    private Direction currentFacing() {
        if (level != null) {
            BlockState ms = level.getBlockState(getMaster());
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