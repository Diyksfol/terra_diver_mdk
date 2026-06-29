package com.example.terradiver.physics;

import com.example.terradiver.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (!(getBlock() instanceof DrillCrownBlock master)) {
            return InteractionResult.FAIL;
        }
        Level level = context.getLevel();
        BlockPos masterPos = context.getClickedPos();
        Direction facing = context.getNearestLookingDirection(); // направление бурения

        List<BlockPos> cells = DrillCrownStructure.worldCells(master.crownSize(), facing, masterPos);

        // Проверка места: все ячейки должны быть заменяемыми (воздух/трава и т.п.).
        for (BlockPos cell : cells) {
            if (!level.getBlockState(cell).canBeReplaced(context)) {
                return InteractionResult.FAIL; // нет места (в будущем — предупреждение игроку)
            }
        }

        if (!level.isClientSide) {
            BlockState masterState = master.defaultBlockState().setValue(DrillCrownBlock.FACING, facing);
            level.setBlock(masterPos, masterState, Block.UPDATE_ALL);

            Block partBlock = BlockRegistry.DRILL_CROWN_PART.get();
            for (BlockPos cell : cells) {
                if (cell.equals(masterPos)) {
                    continue;
                }
                level.setBlock(cell, partBlock.defaultBlockState(), Block.UPDATE_ALL);
                if (level.getBlockEntity(cell) instanceof DrillCrownPartBlockEntity be) {
                    be.setMaster(masterPos);
                }
            }

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
