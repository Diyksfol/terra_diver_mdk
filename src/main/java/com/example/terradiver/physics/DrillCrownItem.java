package com.example.terradiver.physics;

import com.example.terradiver.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.List;

/*
 * Предмет буровой короны: ставит всю NxN-структуру разом. Считает ячейки тела по размеру и
 * направлению взгляда (DrillCrownStructure), проверяет, что ВСЕ свободны, иначе отменяет
 * постановку. Ставит мастер + дочерние ячейки и связывает их с мастером.
 */
public class DrillCrownItem extends BlockItem {

    public DrillCrownItem(Block block, Properties properties) {
        super(block, properties);
    }

    // Показать причину отказа ТОЛЬКО тому игроку, кто ставит: строкой над хотбаром (action bar) —
    // это и есть «плашка», отдельный GUI под неё не нужен. Клиентскую половину вызова пропускаем,
    // иначе сообщение продублируется (place() выполняется на обеих сторонах).
    private static void warn(BlockPlaceContext context, String key, String size) {
        if (context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player) {
            player.displayClientMessage(net.minecraft.network.chat.Component
                    .translatable(key, size)
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
        }
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {        if (!(getBlock() instanceof DrillCrownBlock master)) {
            return InteractionResult.FAIL;
        }
        Level level = context.getLevel();
        // Направление = грань поверхности, на которую ставим (бур смотрит ОТ поверхности).
        // Shift инвертирует направление и сдвигает структуру наружу, чтобы она не ушла в поверхность.
        Direction face = context.getClickedFace();
        boolean inverted = context.isSecondaryUseActive();
        Direction facing = inverted ? face.getOpposite() : face;
        BlockPos masterPos = context.getClickedPos();
        if (inverted) {
            masterPos = masterPos.relative(face, DrillCrownStructure.depthLayers(master.crownSize()) - 1);
        }

        List<BlockPos> cells = DrillCrownStructure.worldCells(master.crownSize(), facing, masterPos);

        // Проверка места: все ячейки должны быть заменяемыми (воздух/трава и т.п.)
        // И свободны от живых существ (иначе игрок окажется замурован и начнёт задыхаться).
        for (BlockPos cell : cells) {
            if (!level.getBlockState(cell).canBeReplaced(context)) {
                warn(context, "msg.terra_diver.crown_no_space", master.crownSize());
                return InteractionResult.FAIL; // нет места
            }
            if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(cell)).isEmpty()) {
                warn(context, "msg.terra_diver.crown_blocked_entity", master.crownSize());
                return InteractionResult.FAIL; // в ячейке живое существо
            }
        }

        if (!level.isClientSide) {
            BlockState masterState = master.defaultBlockState().setValue(DrillCrownBlock.FACING, facing);
            level.setBlock(masterPos, masterState, Block.UPDATE_ALL);
            // Дочерние ячейки строит onPlace мастера (общая точка для игрока и разборки контраптии).
            if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
            level.gameEvent(GameEvent.BLOCK_PLACE, masterPos,
                GameEvent.Context.of(context.getPlayer(), masterState));
            SoundType st = masterState.getSoundType();
            level.playSound(null, masterPos, st.getPlaceSound(), SoundSource.BLOCKS,
                (st.getVolume() + 1f) / 2f, st.getPitch() * 0.8f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}