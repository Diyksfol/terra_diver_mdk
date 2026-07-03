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

    // Коллизия — полноширинная «ступень», НЕ точная суб-воксельная форма и НЕ грубый полный куб.
    // По горизонтали ячейка всегда 1×1 (шире игрока 0.6 — не за что цепляться и выталкиваться вбок),
    // по вертикали — реальная высота формы ячейки. Так сохраняется ощущение ступенчатого скоса
    // (полушаги 0.5), но исчезает выталкивание, которое давали узкие (0.5) суб-воксельные грани.
    // Контур/выделение (getShape) остаётся точным — меняется только физика столкновений.
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        VoxelShape shape = level.getBlockEntity(pos) instanceof DrillCrownPartBlockEntity be
                ? be.getShape() : Shapes.block();
        if (shape.isEmpty()) {
            return Shapes.empty();
        }
        var b = shape.bounds();
        return Shapes.box(0.0D, b.minY, 0.0D, 1.0D, b.maxY, 1.0D);
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
        // Сломали часть в выживании → выронить один предмет короны у мастера.
        if (!level.isClientSide && !player.getAbilities().instabuild
                && level.getBlockEntity(pos) instanceof DrillCrownPartBlockEntity be) {
            DrillCrownMultiblock.dropCrownItem(level, be.getMaster());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        // Сломали часть → снести всю структуру. Флаг DISSOLVING в helper гасит рекурсию,
        // когда разборка сама удаляет остальные ячейки.
        if (!state.is(newState.getBlock()) && !DrillCrownMultiblock.isDissolving()) {
            if (level.getBlockEntity(pos) instanceof DrillCrownPartBlockEntity be) {
                BlockState ms = level.getBlockState(be.getMaster());
                if (ms.getBlock() instanceof DrillCrownMultiblock.Master m) {
                    DrillCrownMultiblock.dissolve(level, be.getMaster(), m.crownSize(), m.crownFacing(ms), ms);
                }
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}