package com.example.terradiver.kinetics;

import com.example.terradiver.physics.DrillCrownBlock;
import com.example.terradiver.physics.DrillCrownPartBlock;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.base.IRotate.StressImpact;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
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

    // ── Плавный старт/торможение + визуальная скорость RPM/4 ──
    private static final float SPEED_FACTOR = 0.25F;   // визуальная скорость = RPM/4
    private static final int BASE_RAMP_TICKS = 50;     // ~2.5с базы (пустой/лёгкий бур)
    private static final float TICKS_PER_SU = 1.0F;    // + столько тиков разгона на единицу нагрузки
    private float easedAngularSpeed = 0.0F;
    private int brakeTicks = -1;                        // сервер: обратный отсчёт торможения
    private float brakeStep = 0.0F;                     // шаг линейного торможения (синхронизируется)
    private boolean clientBraking = false;              // флаг торможения, пришедший с сервера

    @Override
    public float getAngularSpeed() {
        return easedAngularSpeed;
    }

    // Длительность разгона/торможения в тиках: чем больше нагрузка (SU — больше/крупнее буров), тем
    // ДОЛЬШЕ раскрутка — показываем «тяжесть». Прочное буровое крепление позже переопределит этот
    // метод (встроенный маховик → разгон быстрее). Значения — TUNE.
    protected int rampTicks() {
        return (int) (BASE_RAMP_TICKS + Math.abs(lastStressApplied) * TICKS_PER_SU);
    }

    // Идёт ли торможение: на сервере — по brakeTicks, на клиенте — по синхронизированному флагу.
    private boolean braking() {
        return level != null && level.isClientSide ? clientBraking : brakeTicks >= 0;
    }

    @Override
    public void tick() {
        if (braking()) {
            // Линейно гасим скорость до нуля. Шаг синхронизирован → клиент тормозит так же, как сервер
            // (раньше клиент не знал о торможении и рисовал полную скорость до внезапного стопа).
            float step = brakeStep > 0.0F
                    ? brakeStep
                    : Math.abs(easedAngularSpeed) / Math.max(1, rampTicks()) + 0.02F;
            easedAngularSpeed = Mth.approach(easedAngularSpeed, 0.0F, step);
        } else {
            // Плавный разгон: только когда собрано (running) — иначе eased раскручивался ещё до сборки
            // (вал крутится вхолостую) и старт был мгновенным.
            float target = running ? SPEED_FACTOR * super.getAngularSpeed() : 0.0F;
            float ref = Math.max(Math.abs(target), Math.abs(easedAngularSpeed));
            float astep = ref / Math.max(1, rampTicks()) + 0.02F;
            easedAngularSpeed = Mth.approach(easedAngularSpeed, target, astep);
        }
        // Разбор — на сервере, когда скорость упала до ~0 (или по страховочному пределу).
        if (brakeTicks >= 0) {
            brakeTicks--;
            if (level != null && !level.isClientSide
                    && (Math.abs(easedAngularSpeed) <= 0.05F || brakeTicks <= 0)) {
                doDisassemble();
                return;
            }
        }
        super.tick();
    }

    // Разборка НЕ резкая: если корона ещё крутится и торможение не запущено — запускаем плавное
    // торможение (линейно до нуля за ~rampTicks) и синхронизируем его на клиент; сама разборка
    // сработает в tick, когда докрутит.
    @Override
    public void disassemble() {
        if (brakeTicks < 0 && running && movedContraption != null && Math.abs(easedAngularSpeed) > 0.1F) {
            // Тормозим так, чтобы ОСТАНОВИТЬСЯ РОВНО В ИСХОДНОМ ПОЛОЖЕНИИ (угол 0), а не встать где
            // попало и потом резко доснапиться при разборке. Считаем путь до нуля в текущем направлении
            // и добираем целые обороты, чтобы торможение длилось примерно rampTicks: при линейном
            // замедлении путь = v0*N/2, отсюда шаг = v0²/(2*путь). Итог: бур плавно докручивается и
            // замирает на исходном угле, а setAngle(0) при разборке уже ничего не двигает.
            float v0 = Math.abs(easedAngularSpeed);
            int dir = easedAngularSpeed >= 0 ? 1 : -1;
            float a = ((angle % 360.0F) + 360.0F) % 360.0F;
            float remaining = dir > 0 ? (360.0F - a) % 360.0F : a;
            int n = Math.max(1, rampTicks());
            int turns = Math.max(0, Math.round((v0 * n / 2.0F - remaining) / 360.0F));
            float dist = remaining + 360.0F * turns;
            if (dist < 1.0F) {
                doDisassemble(); // уже практически в исходном
                return;
            }
            brakeStep = v0 * v0 / (2.0F * dist);
            brakeTicks = (int) (2.0F * dist / v0) + 40; // страховочный предел
            notifyUpdate(); // сказать клиенту, что пошло торможение
            return;
        }
        doDisassemble();
    }

    @Override
    public void write(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            tag.putBoolean("CrownBraking", brakeTicks >= 0);
            tag.putFloat("CrownBrakeStep", brakeStep);
        }
    }

    @Override
    protected void read(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) {
            clientBraking = tag.getBoolean("CrownBraking");
            brakeStep = tag.getFloat("CrownBrakeStep");
        }
    }

    private void doDisassemble() {
        brakeTicks = -1;
        brakeStep = 0.0F;
        clientBraking = false;
        if (movedContraption != null) {
            movedContraption.setAngle(0.0F); // парковка в исходную ориентацию
        }
        super.disassemble();
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
        // Стартуем с блока перед лицом, даже если это НЕ корона: обход пройдёт по склейке и найдёт
        // корону за обычным блоком (стойка подшипник-блок-бур). Пусто впереди — считать нечего.
        if (level.getBlockState(start).isAir()) {
            return 0.0F;
        }
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        float sum = 0.0F;
        int cap = 4096; // страховка от разрастания
        java.util.Set<SuperGlueEntity> glueCache = new java.util.HashSet<>();
        while (!queue.isEmpty() && cap-- > 0) {
            BlockPos p = queue.poll();
            BlockState st = level.getBlockState(p);
            if (st.getBlock() instanceof DrillCrownBlock crown) {
                sum += stressForSide(sideOf(crown.crownSize()));
            }
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.contains(n)) {
                    continue;
                }
                // К соседу идём, если ОБА — короны (ячейки короны/смежные короны), ИЛИ текущий блок
                // приклеен к соседу суперклеем. От ОБЫЧНОГО блока — только по клею, поэтому корона,
                // просто СТОЯЩАЯ вплотную к блоку, но не приклеенная, больше не досчитывается (как и
                // при сборке контраптии: неприклеенное к стойке не захватывается). Нагрузку дают
                // только сами короны.
                boolean bothCrowns = isCrown(st) && isCrown(level.getBlockState(n));
                if (bothCrowns || SuperGlueEntity.isGlued(level, p, d, glueCache)) {
                    seen.add(n);
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

    // Показ НАГРУЗКИ в очках. Наследуемый метод Create прячет строку при нагрузке 0 (return false),
    // а нам нужно, чтобы буровой подшипник, как и обычный, показывал нагрузку ВСЕГДА — даже 0.
    // Здесь же (в очках, не в hover-описании) показываем свой хинт «нужна корона».
    @Override
    public boolean addToGoggleTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip, boolean isPlayerSneaking) {
        if (!StressImpact.isEnabled()) {
            return false;
        }
        // gui.goggles.kinetic_stats — ключ САМОГО Create, для него CreateLang.translate корректен.
        float stress = calculateStressApplied();
        CreateLang.translate("gui.goggles.kinetic_stats").forGoggles(tooltip);
        addStressImpactStats(tooltip, stress);
        // Хинт «нужна корона» — когда НЕ собрано и НИ ОДНА корона не досягаема (в т.ч. через склейку),
        // то есть нагрузка 0. Перед хинтом — пустая строка: перенос между блоком нагрузки и плашкой (#1).
        // ВАЖНО: наши ключи добавляем через builder().add(Component.translatable(...)), а НЕ через
        // CreateLang.translate(...) — тот подставляет префикс "create." и ключ не находится (из-за
        // этого показывались имена переменных). forGoggles даёт правильный отступ.
        if (!running && stress <= 0.0F && getBlockState().getBlock() instanceof CrownBearingBlock) {
            tooltip.add(net.minecraft.network.chat.Component.empty());
            CreateLang.builder().add(net.minecraft.network.chat.Component
                    .translatable("hint.terra_diver.crown_bearing.title").withStyle(net.minecraft.ChatFormatting.GOLD)).forGoggles(tooltip);
            CreateLang.builder().add(net.minecraft.network.chat.Component
                    .translatable("hint.terra_diver.crown_bearing.line1").withStyle(net.minecraft.ChatFormatting.GRAY)).forGoggles(tooltip);
            CreateLang.builder().add(net.minecraft.network.chat.Component
                    .translatable("hint.terra_diver.crown_bearing.line2").withStyle(net.minecraft.ChatFormatting.GRAY)).forGoggles(tooltip);
        }
        return true;
    }

    // Обычный hover-хинт. Пусто и super НЕ зовём: у MechanicalBearing здесь вешается стандартный хинт
    // empty_bearing, который мы прячем; а свою инфу показываем ТОЛЬКО в очках (addToGoggleTooltip),
    // а не в hover-описании блока (раньше хинт по ошибке дублировался туда).
    @Override
    public boolean addToTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip, boolean isPlayerSneaking) {
        return false;
    }

    // Собирать разрешаем, если в присоединённой конструкции перед лицом (в т.ч. ЧЕРЕЗ склейку) есть
    // хотя бы одна корона. worldCrownStress > 0 именно это и значит — обход идёт по короне и суперклею,
    // поэтому стойка подшипник-блок-бур (склеенная) тоже соберётся, а не только корона впритык.
    @Override
    public void assemble() {
        if (worldCrownStress() <= 0.0F) {
            return; // корон не найдено — крутить нечего, подшипник остаётся стоять
        }
        super.assemble();
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
