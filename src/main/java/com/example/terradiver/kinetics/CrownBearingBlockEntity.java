package com.example.terradiver.kinetics;

import com.example.terradiver.physics.DrillCrownBlock;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/*
 * BlockEntity бурового подшипника. Берём всю сборку/разборку/вращение от механического подшипника
 * Create и добавляем ровно две вещи:
 *   1) собирать можно ТОЛЬКО если прямо перед лицом стоит буровая корона (её мастер-блок);
 *   2) нагрузка (SU) считается по стороне короны — см. stressForSide.
 * Нагрузка задана явной таблицей, а не формулой: рост между 3x3 и 5x5 круче остальных шагов.
 * Ориентир Create: мех.бур = 4, бурильное колесо (3x3) = 8; здесь взят вдвое более крутой рост.
 * Значения: 1x1=8, 3x3=24, 5x5=48, 7x7=64, 9x9=80, 11x11=96 (полный 11x11 на максимуме ~81920 SU).
 */
public class CrownBearingBlockEntity extends MechanicalBearingBlockEntity {

    public CrownBearingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // Убираем прокручиваемую настройку «Режим движения», унаследованную от подшипника Create:
    // super добавляет её в список и в поле movementMode; мы вызываем super (поле остаётся валидным,
    // логика сборки читает его как ROTATE по умолчанию), а из списка поведений — убираем, чтобы в
    // интерфейсе блока её не было.
    @Override
    public void addBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        if (movementMode != null) {
            behaviours.remove(movementMode);
        }
    }

    // Нагрузка (SU за единицу скорости) по стороне короны. Крутить прямо тут. - TUNE
    public static float stressForSide(int side) {
        return switch (side) {
            case 1 -> 8.0F;
            case 3 -> 24.0F;
            case 5 -> 48.0F;
            case 7 -> 64.0F;
            case 9 -> 80.0F;
            case 11 -> 96.0F;
            default -> 0.0F;
        };
    }

    // Механический подшипник — это генератор скорости, но нагрузку он ПОТРЕБЛЯЕТ (impact). Отдаём
    // нагрузку прикреплённой короны; нет короны → сборка не состоится, но подстрахуемся нулём.
    @Override
    public float calculateStressApplied() {
        float impact = stressForSide(frontCrownSide());
        this.lastStressApplied = impact;
        return impact;
    }

    // Собирать разрешаем только когда перед лицом реально стоит корона.
    @Override
    public void assemble() {
        if (frontCrownSide() <= 0) {
            return; // нет короны — крутить нечего, подшипник остаётся стоять
        }
        super.assemble();
    }

    // Сторона короны (мастер-блока) прямо перед лицом подшипника; 0 — если короны нет.
    private int frontCrownSide() {
        if (level == null) {
            return 0;
        }
        BlockState self = getBlockState();
        if (!(self.getBlock() instanceof CrownBearingBlock)) {
            return 0;
        }
        Direction facing = self.getValue(CrownBearingBlock.FACING);
        BlockState front = level.getBlockState(worldPosition.relative(facing));
        if (front.getBlock() instanceof DrillCrownBlock crown) {
            return sideOf(crown.crownSize());
        }
        return 0;
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
}
