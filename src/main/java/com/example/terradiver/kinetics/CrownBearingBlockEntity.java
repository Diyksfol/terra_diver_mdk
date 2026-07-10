package com.example.terradiver.kinetics;

import com.example.terradiver.physics.DrillCrownBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/*
 * Нагрузка (Stress Impact) бурового подшипника. Считается ЗДЕСЬ, а не в короне — как у бурового
 * подшипника Aeronautics: подшипник знает, что на нём висит, и суммирует нагрузку.
 *
 * Формула STRESS_BASE + STRESS_PER_SIDE * (N - 1), где N — сторона короны (1, 3, 5, 7, 9, 11).
 * Откалибрована по двум реальным точкам Create:
 *   механический бур (ломает 1 блок)  -> 4 ед. нагрузки на единицу скорости;
 *   бурильное колесо (ломает 3x3)     -> 8 ед.
 * Обе точки ложатся на прямую по СТОРОНЕ (не по площади): 4 при N=1, 8 при N=3 => шаг 2 на сторону.
 * Отсюда: 1x1=4, 3x3=8, 5x5=12, 7x7=16, 9x9=20, 11x11=24.
 * Крутить STRESS_BASE / STRESS_PER_SIDE, если баланс на плейтесте попросит другое.
 *
 * Без короны подшипник крутится вхолостую — нагрузка 0.
 */
public class CrownBearingBlockEntity extends KineticBlockEntity {

    public static final float STRESS_BASE = 4.0F;      // - TUNE (нагрузка короны 1x1)
    public static final float STRESS_PER_SIDE = 2.0F;  // - TUNE (прибавка на каждые +2 к стороне... см. ниже)

    public CrownBearingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // Поведений пока нет; метод обязателен (SmartBlockEntity).
    }

    // Нагрузка по стороне короны: N=1 -> 4, N=3 -> 8, N=5 -> 12, ...
    public static float stressForSide(int side) {
        if (side <= 0) {
            return 0.0F;
        }
        return STRESS_BASE + STRESS_PER_SIDE * (side - 1);
    }

    @Override
    public float calculateStressApplied() {
        float impact = crownStress();
        this.lastStressApplied = impact;
        return impact;
    }

    // Ищем корону перед подшипником и берём её размер.
    private float crownStress() {
        if (level == null) {
            return 0.0F;
        }
        BlockState self = getBlockState();
        if (!(self.getBlock() instanceof CrownBearingBlock)) {
            return 0.0F;
        }
        Direction facing = self.getValue(CrownBearingBlock.FACING);
        BlockState front = level.getBlockState(worldPosition.relative(facing));
        if (front.getBlock() instanceof DrillCrownBlock crown) {
            return stressForSide(sideOf(crown.crownSize()));
        }
        return 0.0F;
    }

    // "9x9" -> 9
    private static int sideOf(String crownSize) {
        int x = crownSize.indexOf('x');
        if (x <= 0) {
            return 0;
        }
        try {
            return Integer.parseInt(crownSize.substring(0, x));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Корону поставили или сломали — отцепляемся от кинетической сети, чтобы она пересобралась
    // с новой нагрузкой на следующем тике.
    public void refreshStress() {
        if (level == null || level.isClientSide) {
            return;
        }
        detachKinetics();
        setChanged();
        sendData();
    }
}
