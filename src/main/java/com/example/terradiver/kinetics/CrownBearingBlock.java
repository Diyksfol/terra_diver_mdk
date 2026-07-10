package com.example.terradiver.kinetics;

import com.example.terradiver.registry.BlockEntityRegistry;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/*
 * Буровой подшипник — кинетический блок Create. Вал подключается СЗАДИ (со стороны, противоположной
 * лицевой), спереди на нём висит буровая корона. Нагрузка (SU) считается в подшипнике по размеру
 * прикреплённой короны — см. CrownBearingBlockEntity. Ось вращения = ось лицевой стороны.
 *
 * Наследуем DirectionalKineticBlock: он уже даёт свойство FACING, установку по взгляду игрока и
 * базовую кинетическую обвязку. IBE связывает блок с его BlockEntity (аналог EntityBlock у Create).
 */
public class CrownBearingBlock extends DirectionalKineticBlock implements IBE<CrownBearingBlockEntity> {

    public CrownBearingBlock(Properties properties) {
        super(properties);
    }

    // Ось вращения совпадает с осью, вдоль которой смотрит подшипник.
    @Override
    public Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    // Вал принимаем только с тыльной стороны: спереди место занято короной.
    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    // Корону поставили/сломали рядом — пересчитать нагрузку сети.
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide) {
            withBlockEntityDo(level, pos, CrownBearingBlockEntity::refreshStress);
        }
    }

    @Override
    public Class<CrownBearingBlockEntity> getBlockEntityClass() {
        return CrownBearingBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CrownBearingBlockEntity> getBlockEntityType() {
        return BlockEntityRegistry.CROWN_BEARING.get();
    }
}
