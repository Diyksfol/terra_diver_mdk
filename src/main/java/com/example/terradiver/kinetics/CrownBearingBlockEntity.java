package com.example.terradiver.kinetics;

import com.example.terradiver.physics.DrillCrownBlock;
import com.example.terradiver.physics.DrillCrownPartBlock;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

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

    // Нагрузку СУММИРУЕМ по ВСЕМ коронам, прикреплённым к подшипнику, как у подшипника Aeronautics.
    // Когда собрано — короны уже в контраптии (в мире перед лицом их нет!), поэтому читаем их из
    // контраптии; иначе (до сборки) — по короне в мире перед лицом. Это же чинит пропажу показа
    // нагрузки в гогглах при вращении: раньше фронт был пустым (корона уехала) и выходил 0.
    @Override
    public float calculateStressApplied() {
        float impact = 0.0F;
        if (movedContraption != null && movedContraption.getContraption() != null) {
            for (StructureBlockInfo info : movedContraption.getContraption().getBlocks().values()) {
                if (info.state().getBlock() instanceof DrillCrownBlock crown) {
                    impact += stressForSide(sideOf(crown.crownSize()));
                }
            }
        } else {
            impact = worldCrownStress();
        }
        this.lastStressApplied = impact;
        return impact;
    }

    // До сборки короны стоят в мире. Суммируем нагрузку ВСЕХ коронов связной структуры перед лицом
    // (BFS по блокам короны — мастер+ведомые), а не только ближайшей. Так остановленный подшипник
    // показывает ту же суммарную нагрузку, что и при вращении (стойка 3x3 + 1x1 = сумма обоих).
    private float worldCrownStress() {
        if (level == null) {
            return 0.0F;
        }
        BlockState self = getBlockState();
        if (!(self.getBlock() instanceof CrownBearingBlock)) {
            return 0.0F;
        }
        Direction facing = self.getValue(CrownBearingBlock.FACING);
        BlockPos start = worldPosition.relative(facing);
        if (!isCrown(level.getBlockState(start))) {
            return 0.0F;
        }
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        float sum = 0.0F;
        int cap = 4096; // страховка от разрастания
        while (!queue.isEmpty() && cap-- > 0) {
            BlockPos p = queue.poll();
            BlockState st = level.getBlockState(p);
            if (st.getBlock() instanceof DrillCrownBlock crown) {
                sum += stressForSide(sideOf(crown.crownSize()));
            }
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.add(n) && isCrown(level.getBlockState(n))) {
                    queue.add(n);
                }
            }
        }
        return sum;
    }

    private static boolean isCrown(BlockState state) {
        return state.getBlock() instanceof DrillCrownBlock
                || state.getBlock() instanceof DrillCrownPartBlock;
    }

    // Полностью прячем create-хинт "empty_bearing" ("Активировать подшипник...") и ставим свой.
    // Родителя НЕ зовём (иначе он добавит create-хинт). Показ нагрузки в гогглах идёт отдельным
    // методом (addToGoggleTooltip) и здесь не затрагивается; опускается лишь overstress-хинт.
    @Override
    public boolean addToTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip, boolean isPlayerSneaking) {
        if (running || level == null) {
            return false;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof CrownBearingBlock)) {
            return false;
        }
        BlockState front = level.getBlockState(worldPosition.relative(state.getValue(CrownBearingBlock.FACING)));
        if (front.canBeReplaced()) {
            return false;
        }
        tooltip.add(net.minecraft.network.chat.Component.translatable("hint.terra_diver.crown_bearing.title")
                .withStyle(net.minecraft.ChatFormatting.GOLD));
        tooltip.add(net.minecraft.network.chat.Component.translatable("hint.terra_diver.crown_bearing.line1")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(net.minecraft.network.chat.Component.translatable("hint.terra_diver.crown_bearing.line2")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        return true;
    }

    // Собирать разрешаем только когда перед лицом реально стоит корона.
    @Override
    public void assemble() {
        if (frontCrownSide() <= 0) {
            return; // нет короны — крутить нечего, подшипник остаётся стоять
        }
        super.assemble();
    }

    // При остановке наш подшипник ВСЕГДА возвращает всю прикреплённую сборку в ЕДИНСТВЕННОЕ
    // исходное положение (угол 0), а не в ближайшие 90°. Тогда что бы к нему ни было прицеплено —
    // корона, короны на короне, любые блоки — при разборке ложится ровно так, как собиралось, и
    // никакие данные формы ведомых не устаревают. Create в makeStructureTransform читает угол у
    // самой контраптии (в disassemble он там НЕ сбрасывается), поэтому обнуляем его ДО super —
    // трансформ выходит нулевым и блоки встают в исходные клетки. Проверено по исходникам Create
    // (ControlledContraptionEntity.makeStructureTransform + AbstractContraptionEntity.disassemble).
    @Override
    public void disassemble() {
        if (movedContraption != null) {
            movedContraption.setAngle(0.0F);
        }
        super.disassemble();
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
