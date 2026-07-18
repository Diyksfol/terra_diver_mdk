package com.example.terradiver.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/*
 * Дочерняя ячейка мультиблок-короны: НЕВИДИМА (модель рендерит только мастер), но осязаема —
 * полный куб коллизии, поэтому по телу короны можно ходить и ломать любую ячейку. Ломание
 * перенаправляется мастеру (снос всей структуры). См. DrillCrownStructure / DrillCrownMultiblock.
 */
public class DrillCrownPartBlock extends Block implements EntityBlock {

    public DrillCrownPartBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE; // модель рисует мастер, дочерние невидимы
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        // Контур/выделение ячейки: полный куб (внутренняя) или точные краевые суб-воксели — из BE.
        if (level.getBlockEntity(pos) instanceof DrillCrownPartBlockEntity be) {
            return be.getShape();
        }
        return Shapes.block();
    }

    private static final VoxelShape BOTTOM_HALF = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D);
    private static final VoxelShape TOP_HALF = Shapes.box(0.0D, 0.5D, 0.0D, 1.0D, 1.0D, 1.0D);

    // Коллизия «как ступень», а не «как полный блок»:
    //  - нижняя половина: если внизу есть форма — кладём ПОЛНОШИРИННУЮ плиту (0..0.5). Это опора во
    //    всю клетку, шире игрока (0.6), поэтому под ним всегда есть куда встать — не выталкивает вбок
    //    и не скатывает по конусу вниз (каскад ломается на этой плите).
    //  - верхняя половина: берём РЕАЛЬНУЮ форму ячейки (ступень/бугор, не во всю клетку). Поэтому по
    //    ощущению это ступень, а не полный блок, и коллизия совпадает с текстурой (не «толще» неё).
    // Итог: игрок ходит по уровню плиты, верхние бугры переступает; ср. прошлый вариант, где любая
    // ячейка с верхней восьмушкой схлопывалась в полный куб (оттого и «как блок»).
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        VoxelShape shape = level.getBlockEntity(pos) instanceof DrillCrownPartBlockEntity be
                ? be.getShape() : Shapes.block();
        if (shape.isEmpty()) {
            return Shapes.empty();
        }
        VoxelShape bottomReal = Shapes.join(shape, BOTTOM_HALF, BooleanOp.AND);
        VoxelShape base = bottomReal.isEmpty() ? Shapes.empty() : BOTTOM_HALF;
        VoxelShape topReal = Shapes.join(shape, TOP_HALF, BooleanOp.AND);
        return Shapes.or(base, topReal);
    }

    // Свои частицы разрушения (в dissolve) красим по материалу мастера, а ванильные — подавляем:
    // у ведомой ячейки они брались бы из модели drill_crown_part (медь), из-за чего сломанный блок
    // сыпал «не тем» цветом. Возвращаем true → ванильных частиц нет, остаются только правильные.
    @Override
    public void initializeClient(java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions> consumer) {
        consumer.accept(new net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions() {
            @Override
            public boolean addDestroyEffects(BlockState s, net.minecraft.world.level.Level l, BlockPos p,
                                             net.minecraft.client.particle.ParticleEngine mgr) {
                return true;
            }
        });
    }

    // Не затеняем соседей: короне-мультиблоку не нужно блокировать небесный свет — иначе блок,
    // поставленный под мастером/ячейкой, уходит в тень (тёмная нижняя грань). Пропускаем свет насквозь.
    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    // Средний клик (pick block) по любой ячейке короны, даже невидимой ведомой, должен давать сам
    // предмет короны (как у мастера), а не пустоту. Читаем мастер из BE и возвращаем его предмет.
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof DrillCrownPartBlockEntity be) {
            BlockState ms = level.getBlockState(be.getMaster());
            if (ms.getBlock() instanceof DrillCrownMultiblock.Master) {
                return new ItemStack(ms.getBlock());
            }
        }
        return super.getCloneItemStack(state, target, level, pos, player);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DrillCrownPartBlockEntity(pos, state);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Сломали часть в выживании → выронить один предмет короны у мастера. В креативе не роняем,
        // но в обоих случаях помечаем позицию мастера как «обработана игроком», чтобы страховочный
        // дроп в onRemove (для слома НЕ игроком) здесь не сработал и не дал второй предмет.
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof DrillCrownPartBlockEntity be) {
            BlockPos masterPos = be.getMaster();
            if (!player.getAbilities().instabuild) {
                DrillCrownMultiblock.dropCrownItem(level, masterPos);
            }
            DrillCrownMultiblock.markPlayerHandled(masterPos);
            // Сбросить прогресс ломания по всей короне (для остальных игроков; локальному — гасим при
            // установке, см. DrillCrownBlock.onPlace). Ведомые видны как коллизия по краям, поэтому
            // ломают часто именно их.
            BlockState ms = level.getBlockState(masterPos);
            if (ms.getBlock() instanceof DrillCrownMultiblock.Master m) {
                for (BlockPos cell : DrillCrownStructure.worldCells(m.crownSize(), m.crownFacing(ms), masterPos)) {
                    level.destroyBlockProgress(player.getId(), cell, -1);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        // Сломали часть → снести всю структуру. Флаг DISSOLVING в helper гасит рекурсию,
        // когда разборка сама удаляет остальные ячейки. При moved=true ведомую забирает контраптия
        // Create (захват всей короны) — сносить структуру НЕ надо.
        if (!moved && !state.is(newState.getBlock()) && !DrillCrownMultiblock.isDissolving()) {
            if (level.getBlockEntity(pos) instanceof DrillCrownPartBlockEntity be) {
                BlockPos masterPos = be.getMaster();
                BlockState ms = level.getBlockState(masterPos);
                if (ms.getBlock() instanceof DrillCrownMultiblock.Master m) {
                    DrillCrownMultiblock.dissolve(level, masterPos, m.crownSize(), m.crownFacing(ms), ms);
                    // Страховка от исчезновения при сломе НЕ игроком (другой мод, поршень, взрыв):
                    // ведомую сломали, а мастер ещё в мире — роняем предмет короны у мастера. Игрок
                    // роняет его сам в playerWillDestroy и помечает позицию мастера, поэтому здесь для
                    // его пути дроп не дублируется. Без этой ветки слом ведомой чужим модом уносил
                    // корону бесследно (мастер выпадал, ведомая — нет).
                    if (!level.isClientSide && !DrillCrownMultiblock.wasPlayerHandled(masterPos)) {
                        DrillCrownMultiblock.dropCrownItem(level, masterPos, ms);
                    }
                    DrillCrownMultiblock.clearPlayerHandled(masterPos);
                }
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}