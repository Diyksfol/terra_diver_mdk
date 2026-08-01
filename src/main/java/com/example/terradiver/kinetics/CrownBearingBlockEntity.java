package com.example.terradiver.kinetics;

import com.example.terradiver.physics.DrillCrownBlock;
import com.example.terradiver.physics.PhysicsUtils;
import com.example.terradiver.physics.DrillCrownPartBlock;
import com.example.terradiver.physics.DrillCrownStructure;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.base.IRotate.StressImpact;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.foundation.utility.ServerSpeedProvider;
import com.example.terradiver.config.ModConfig;
import com.example.terradiver.registry.BlockRegistry;
import dev.ryanhcode.offroad.handlers.server.MultiMiningSupplier;
import dev.ryanhcode.offroad.handlers.server.MultiMiningServerManager;
import dev.ryanhcode.sable.Sable;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
 *
 * ── Кто считает скорость вращения ──
 * Профиль скорости (S-кривая разгона и торможения) считает ТОЛЬКО сервер. Клиент профиль не строит:
 * он получает параметры кривой пакетом и просто крутит счётчик тиков по той же формуле.
 *
 * Причина категорическая. Родительский getAngularSpeed() у Create на клиенте возвращает НЕ чистую
 * скорость вала, а скорость, домноженную на ServerSpeedProvider.get() (компенсатор тикрейта, живой
 * LerpedFloat) и с добавленным clientAngleDiff/3 (добор до серверного угла, делится пополам каждый
 * тик). Обе величины дрожат от тика к тику. Любая кривая с внутренним состоянием, у которой ЦЕЛЬ
 * взята из этой величины, обнуляет свой прогресс каждый тик и превращается в асимптотическое
 * микроползание. Предыдущий (линейный) профиль этого не замечал: Mth.approach шёл к цели шагом от
 * ТЕКУЩЕЙ скорости и состояния не имел, поэтому дрожание цели его не ломало.
 * Поэтому: цель берётся из convertToAngular(getSpeed()) — чистой синхронизируемой скорости сети,
 * одинаковой на обеих сторонах, а клиентские поправки Create добавляются поверх готового результата,
 * в getAngularSpeed(), ровно как это делает сам Create.
 */
public class CrownBearingBlockEntity extends MechanicalBearingBlockEntity implements MultiMiningSupplier {

    public CrownBearingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ── Настройки профиля ──
    private static final float SPEED_FACTOR = 0.25F;    // визуальная скорость = RPM/4
    private static final int BASE_RAMP_TICKS = 50;      // ~2.5с базы (пустой/лёгкий бур)
    private static final float TICKS_PER_SU = 1.0F;     // + столько тиков разгона на единицу нагрузки
    private static final float TARGET_EPSILON = 0.001F; // мёртвая зона смены цели
    private static final float MIN_BRAKE_SPEED = 0.1F;  // ниже этой скорости тормозить нечего
    private static final float BRAKE_ARRIVE_EPS = 0.05F;// остаток пути, считающийся нулевым (градусы)
    private static final float BRAKE_CRAWL = 1.0F;      // минимальный доводочный шаг, градусов/тик
    private static final int BRAKE_GUARD_TICKS = 40;    // страховочный запас тиков сверх расчётного

    // Итоговая скорость профиля. Сервер её считает, клиент — воспроизводит по синхронизированным
    // параметрам кривой. Клиентские поправки Create сюда НЕ входят (см. getAngularSpeed).
    private float easedAngularSpeed = 0.0F;

    // Разгон. rampStart/rampTarget/rampLen задаёт сервер при смене цели и синкает; rampAge крутят обе
    // стороны сами и он тоже синкается — пакет приходит раз в 3 тика (lazyTick), между пакетами
    // клиент идёт по кривой сам, поэтому разгон плавный, а не ступенчатый.
    private float rampStart = 0.0F;
    private float rampTarget = 0.0F;
    private int rampLen = 1;
    private int rampAge = 0;

    // Торможение с докруткой в исходный угол. Профиль целиком задаёт сервер в disassemble().
    // Это трапеция: brakeCoast тиков на постоянной скорости (выбег), затем brakeLen тиков спада по
    // S-кривой. Выбег нужен потому, что до исходного угла бывает дальше, чем накрывает сам спад, а
    // растягивать спад на всю дистанцию нельзя — на малых оборотах он превращался в десятисекундное
    // переползание. Длина спада всегда = rampTicks(), поэтому останов ощущается зеркально разгону.
    private boolean braking = false;
    private float brakeV0 = 0.0F;
    private int brakeCoast = 0;
    private int brakeLen = 1;
    private int brakeAge = 0;
    private float brakeRemaining = 0.0F;
    private int brakeDir = 1;
    private int brakeGuard = 0;

    // Счётчик тиков между перезаказами грызни. Чисто серверный, синхронизировать не нужно.
    private int miningRefreshCounter = 0;

    // Блок сносят: тормозить некогда, иначе BE умрёт с недоразобранной контраптией на руках.
    private boolean removing = false;

    // Буфер крепления. Создаётся лениво: объём зависит от того, андезитовый подшипник или прочный,
    // а блокстейт в конструкторе ещё не гарантирован.
    private CrownBufferHandler buffer;

    // Делитель темпа грызни. ВНИМАНИЕ на масштаб: делим ПРОФИЛЬ (градусы поворота за тик), а не
    // обороты вала — это числа разного порядка, при 64 об это 4.8 против 64. Подбирая, помни, что
    // 12 здесь даёт примерно то же, что 160 давало бы от оборотов вала. - TUNE
    private static final float BREAK_SPEED_DIVISOR = 12.0F; // темп грызни; выше = медленнее

    // На сколько блоков вперёд от передней грани короны заказываем грызню. - TUNE
    private static final double DIG_REACH = 1.0;
    // Как часто перезаказывать грызню, в тиках. Движок Offroad копит прогресс, пока с момента
    // последнего заказа прошло меньше 5 тиков, а сам заказ живёт 20 тиков (проверено по коду
    // MultiMiningServerManager.BlockBreakingData.tick). Поэтому 4 — максимальный интервал, при
    // котором темп грызни не падает вовсе, и вчетверо меньше работы на сервере. - TUNE
    private static final int MINING_REFRESH_TICKS = 4;
    // Ниже этой скорости грызть нечем — бур фактически стоит.
    private static final float MIN_DIG_SPEED = 0.05F;

    // Парковка. Корона симметрична на четверть оборота, поэтому вставать можно в любой из четырёх
    // ориентаций, а не только в исходной: путь докрутки сокращается вчетверо.
    private static final float PARK_STEP = 90.0F;
    // Если до ближайшей парковки ближе этого — просто доснапить: на глаз такой доворот не виден.
    // Это же чинит «запустил и сразу остановил»: бур не успел провернуться, снап незаметен.
    private static final float PARK_SNAP = 15.0F;
    // Скорость, ниже которой бур считается «ползущим»: если он при этом рядом с четвертью — снапим
    // (п.4, «запустил и сразу стоп»). Выше MIN_BRAKE_SPEED, чтобы это был именно медленный доворот,
    // а не полный ход. - TUNE
    private static final float SNAP_SPEED = 1.0F;
    // Во сколько раз внешне замедляется вращение при полном буфере (косметика, п.13). - TUNE
    private static final float BUFFER_FULL_SLOWDOWN = 0.4F;

    // Куда паркуемся в этой разборке. Считает сервер в disassemble().
    private float parkAngle = 0.0F;

    /*
     * Скорость вращения для родителя: он ею и крутит угол в tick(), и интерполирует рендер.
     * Структура один в один как у Create — только вместо чистой скорости вала наша кривая.
     * Клиентские слагаемые обязательны: без clientAngleDiff клиентский угол уедет от серверного
     * и никогда не догонит, а без ServerSpeedProvider вращение поплывёт при лаге.
     */
    @Override
    public float getAngularSpeed() {
        float speed = easedAngularSpeed;
        if (level != null && level.isClientSide) {
            speed *= ServerSpeedProvider.get();
            // clientAngleDiff НЕ добавляем намеренно. У обычного подшипника Create он добирает
            // клиентский угол к серверному, потому что клиент скорость сам не знает. У нас клиент
            // ЗНАЕТ профиль (те же синхронизированные параметры кривой) и считает угол сам, поэтому
            // расхождение мало. А вот сама добавка приходит скачками при каждом серверном пакете
            // (раз в 3 тика) и делится пополам между ними — это и давало рывки «то быстрее, то
            // медленнее» на статичной машине. На физичной её эффекта не было (угол берёт Sable),
            // поэтому там рывков и не наблюдалось.
        }
        return speed;
    }

    // S-кривая (cubic ease-in-out): плавно трогается и плавно приходит к цели — скорость выглядит
    // трапецией с «круглыми» концами. Среднее значение = 0.5, как у линейного разгона, поэтому
    // пройденный путь при торможении считается той же формулой (v0*N/2) и докрутка остаётся точной.
    private static float smoothstep(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    // Длительность разгона/торможения в тиках: чем больше нагрузка (SU — больше/крупнее буров), тем
    // ДОЛЬШЕ раскрутка — показываем «тяжесть». Прочное буровое крепление позже переопределит этот
    // метод (встроенный маховик → разгон быстрее). Значения — TUNE.
    // Зовётся ТОЛЬКО на сервере: lastStressApplied на клиенте приезжает из NBT и может отстать.
    protected int rampTicks() {
        return (int) (BASE_RAMP_TICKS + Math.abs(lastStressApplied) * TICKS_PER_SU);
    }

    /*
     * Длина конкретного разгона: полная длительность масштабируется долей пути, который реально
     * надо пройти. Пуск с нуля на полные обороты = полная длительность; подкрутка оборотов на ходу
     * с 32 до 40 = пятая часть. Иначе любая мелкая правка скорости тянулась бы те же 2.5-7 секунд.
     */
    private int rampLengthFor(float from, float to) {
        int base = Math.max(1, rampTicks());
        float scale = Math.max(Math.abs(from), Math.abs(to));
        if (scale <= TARGET_EPSILON) {
            return 1;
        }
        float portion = Mth.clamp(Math.abs(to - from) / scale, 0.0F, 1.0F);
        return Math.max(1, Math.round(base * portion));
    }

    @Override
    public void tick() {
        if (level != null && !level.isClientSide) {
            if (serverProfileTick()) {
                return; // разобрались в этом тике, дальше тикать нечего
            }
        } else {
            advanceProfile();
        }
        super.tick();
    }

    /*
     * Серверный ход профиля. Возвращает true, если подшипник в этом тике разобрался.
     * Разгон гейтится по running: иначе скорость раскручивалась бы ещё до сборки (вал крутится
     * вхолостую) и старт выглядел бы мгновенным.
     */
    private boolean serverProfileTick() {
        if (!braking) {
            float target = running ? SPEED_FACTOR * convertToAngular(getSpeed()) : 0.0F;
            if (Math.abs(target - rampTarget) > TARGET_EPSILON) {
                // Цель сменилась (пуск/стоп/другие обороты) — новая кривая ОТ ТЕКУЩЕЙ скорости,
                // поэтому смена оборотов на ходу не даёт скачка. При переезде между обычным миром и
                // физичным кораблём профиль восстанавливается из NBT в read(), и там rampTarget уже
                // равен цели — эта ветка НЕ срабатывает, разгон заново не запускается.
                rampStart = easedAngularSpeed;
                rampTarget = target;
                rampAge = 0;
                rampLen = rampLengthFor(rampStart, target);
                notifyUpdate(); // клиент должен узнать про новую кривую сразу, а не через 3 тика
            }
        }
        advanceProfile();
        // Перезаказ грызни не каждый тик: заказ у движка живёт дольше, чем тик, и до истечения
        // прогресс копится сам. См. MINING_REFRESH_TICKS.
        if (++miningRefreshCounter >= MINING_REFRESH_TICKS) {
            miningRefreshCounter = 0;
            updateMiningBlocks();
        }
        if (braking) {
            brakeGuard--;
            if (brakeRemaining <= BRAKE_ARRIVE_EPS || brakeGuard <= 0) {
                // Диагностика недокрута: сравнить фактический накрученный угол с целевой парковкой.
                // TODO снять после отладки торможения.
                float actual = ((angle % 360.0F) + 360.0F) % 360.0F;
                org.slf4j.LoggerFactory.getLogger("terra_diver").info(
                        "[brake] parkAngle={} actualAngle={} остаток={} причина={}",
                        String.format("%.1f", parkAngle), String.format("%.1f", actual),
                        String.format("%.2f", brakeRemaining), brakeGuard <= 0 ? "GUARD" : "remaining");
                angle = parkAngle; // приехали ровно в парковку
                doDisassemble();
                return true;
            }
        }
        return false;
    }

    /*
     * Один шаг профиля. Считается ОДИНАКОВО на сервере и на клиенте из одних и тех же (синхронных)
     * параметров, поэтому стороны не расходятся, а приходящий раз в 3 тика пакет их лишь подравнивает.
     */
    private void advanceProfile() {
        if (braking) {
            if (brakeAge < brakeCoast + brakeLen) {
                brakeAge++;
            }
            // Выбег на постоянной (подогнанной) скорости, затем спад по S-кривой длиной rampTicks().
            // Скорость brakeV0 подобрана так, что суммарный путь = ровно расстояние до четверти,
            // поэтому доползание crawl'ом больше не нужно — бур приходит точно, без клипа у четверти.
            float mag = brakeAge <= brakeCoast
                    ? brakeV0
                    : brakeV0 * (1.0F - smoothstep((brakeAge - brakeCoast) / (float) Math.max(1, brakeLen)));
            mag = Math.min(mag, Math.max(0.0F, brakeRemaining)); // страховка: не проскочить четверть
            easedAngularSpeed = brakeDir * mag;
            brakeRemaining -= mag;
            return;
        }
        if (rampAge < rampLen) {
            rampAge++;
        }
        easedAngularSpeed = rampStart + (rampTarget - rampStart) * smoothstep(rampAge / (float) Math.max(1, rampLen));
        // Косметика (как у бурильного колеса Offroad): при полном буфере бур внешне замедляется —
        // визуальный сигнал «забился». Грызня и так стоит (isActive=false из-за полного буфера), это
        // только про то, как крутится модель. Применяем на ОБЕИХ сторонах: клиент рисует вращение по
        // своему профилю, поэтому только серверного замедления он бы не увидел (п.11). Буфер синкается,
        // hasSpace одинаков на клиенте и сервере.
        if (running && !getBuffer().hasSpace()) {
            easedAngularSpeed *= BUFFER_FULL_SLOWDOWN;
        }
    }

    /*
     * Разборка НЕ резкая: если корона ещё крутится — запускаем плавное торможение с докруткой ровно
     * в исходный угол, а сама разборка сработает в tick, когда путь исчерпан.
     * Родитель зовёт этот метод из трёх мест: ПКМ игрока, потеря оборотов (SU) и снос блока.
     * Снос блока обязан разбирать МГНОВЕННО — BE вот-вот перестанет тикать и докручивать будет некому.
     */
    @Override
    public void disassemble() {
        if (removing || level == null || level.isClientSide) {
            doDisassemble();
            return;
        }
        if (braking) {
            return; // уже тормозим; повторный ПКМ не рвёт торможение и не разбирает на полном ходу
        }
        if (!running || movedContraption == null || Math.abs(easedAngularSpeed) <= MIN_BRAKE_SPEED) {
            doDisassemble();
            return;
        }
        // Тормозим так, чтобы ОСТАНОВИТЬСЯ РОВНО В ИСХОДНОМ ПОЛОЖЕНИИ (угол 0), а не встать где
        // попало и потом резко доснапиться при разборке. Спад всегда длится rampTicks() — столько
        // же, сколько разгон, поэтому останов ощущается зеркально пуску. Сам спад накрывает
        // v0*n/2 градусов; если до исходного угла дальше, добираем целые обороты, а остаток
        // (меньше одного оборота) проходим выбегом на постоянной скорости ПЕРЕД спадом.
        float v0 = Math.abs(easedAngularSpeed);
        int dir = easedAngularSpeed >= 0 ? 1 : -1;
        float a = ((angle % 360.0F) + 360.0F) % 360.0F;

        // Мгновенный снап — когда тормозить фактически нечего: бур либо почти встал, либо едва
        // тронулся и уже рядом с четвертью. Оба условия ТРЕБУЮТ малой скорости, поэтому раскрученный
        // на полном ходу бур у четверти (п.5) сюда НЕ попадает и тормозится плавно, а «запустил и
        // сразу стоп» (п.4) — попадает и снапится. Разводит их именно скорость, а не только угол.
        float nearest = Math.round(a / PARK_STEP) * PARK_STEP;
        boolean almostStopped = v0 <= MIN_BRAKE_SPEED;
        boolean creepingNearQuarter = v0 <= SNAP_SPEED && Math.abs(a - nearest) <= PARK_SNAP;
        if (almostStopped || creepingNearQuarter) {
            parkAngle = ((nearest % 360.0F) + 360.0F) % 360.0F;
            angle = parkAngle;
            doDisassemble();
            return;
        }
        // Иначе докручиваемся до СЛЕДУЮЩЕЙ четверти по ходу вращения — не больше 90 градусов.
        float next = dir > 0
                ? (float) (Math.floor(a / PARK_STEP) + 1.0) * PARK_STEP
                : (float) (Math.ceil(a / PARK_STEP) - 1.0) * PARK_STEP;
        float remaining = Math.abs(next - a);
        int n = Math.max(1, rampTicks());
        // Путь спада на единицу скорости — ТОЧНАЯ сумма (1 - smoothstep(i/n)) по тикам, а не
        // приближение n/2. Именно замена интеграла приближением давала клип у четверти на десяток
        // градусов: дискретная S-кривая проходит чуть меньше n/2, и пути не хватало.
        float decayIntegral = 0.0F;
        for (int i = 1; i <= n; i++) {
            decayIntegral += 1.0F - smoothstep(i / (float) n);
        }
        float decelDist = v0 * decayIntegral;
        // Добираем ЧЕТВЕРТЯМИ, а не целыми оборотами: любая четверть — валидная парковка.
        int steps = Math.max(0, (int) Math.ceil((decelDist - remaining) / PARK_STEP));
        float dist = remaining + PARK_STEP * steps;
        parkAngle = ((next + dir * PARK_STEP * steps) % 360.0F + 360.0F) % 360.0F;

        // Точная посадка в четверть: путь = выбег (coast тиков на fitted) + спад (fitted*decayIntegral).
        // Берём целое число тиков выбега и ПОДГОНЯЕМ стартовую скорость так, чтобы суммарный путь
        // сошёлся ровно в dist. Тогда бур приходит на четверть без остатка и без доползания.
        int coast = Math.max(0, (int) Math.floor((dist - v0 * decayIntegral) / v0));
        float fitted = dist / (coast + decayIntegral);

        braking = true;
        brakeV0 = fitted;
        brakeCoast = coast;
        brakeLen = n;
        brakeAge = 0;
        brakeRemaining = dist;
        brakeDir = dir;
        brakeGuard = brakeCoast + brakeLen + BRAKE_GUARD_TICKS;
        notifyUpdate(); // сказать клиенту, что пошло торможение
    }

    // Блок сносят. Родительский remove() зовёт disassemble() — флаг говорит ему не тормозить.
    @Override
    public void remove() {
        removing = true;
        super.remove();
    }

    @Override
    public void write(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("CrownBuffer", getBuffer().serializeNBT(registries));
        // Профиль пишем ВСЕГДА, не только в клиентский пакет. Когда машину физикализируют, Sable
        // переносит блок в сублевел, и блок-сущность там ПЕРЕСОЗДАЁТСЯ — она читает серверный NBT.
        // Раньше профиль лежал только в клиентском пакете, поэтому на новом месте easedAngularSpeed
        // стартовал с нуля: бур перезапускался и заново разгонялся. Теперь состояние переезжает.
        tag.putFloat("CrownEased", easedAngularSpeed);
        tag.putFloat("CrownRampStart", rampStart);
        tag.putFloat("CrownRampTarget", rampTarget);
        tag.putInt("CrownRampLen", rampLen);
        tag.putInt("CrownRampAge", rampAge);
        tag.putBoolean("CrownBraking", braking);
        tag.putFloat("CrownBrakeV0", brakeV0);
        tag.putInt("CrownBrakeCoast", brakeCoast);
        tag.putInt("CrownBrakeLen", brakeLen);
        tag.putInt("CrownBrakeAge", brakeAge);
        tag.putFloat("CrownBrakeLeft", brakeRemaining);
        tag.putInt("CrownBrakeDir", brakeDir);
        tag.putFloat("CrownParkAngle", parkAngle);
    }

    @Override
    protected void read(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("CrownBuffer")) {
            getBuffer().deserializeNBT(registries, tag.getCompound("CrownBuffer"));
        }
        if (tag.contains("CrownRampTarget")) {
            rampStart = tag.getFloat("CrownRampStart");
            rampTarget = tag.getFloat("CrownRampTarget");
            rampLen = Math.max(1, tag.getInt("CrownRampLen"));
            rampAge = tag.getInt("CrownRampAge");
            braking = tag.getBoolean("CrownBraking");
            brakeV0 = tag.getFloat("CrownBrakeV0");
            brakeCoast = Math.max(0, tag.getInt("CrownBrakeCoast"));
            brakeLen = Math.max(1, tag.getInt("CrownBrakeLen"));
            brakeAge = tag.getInt("CrownBrakeAge");
            brakeRemaining = tag.getFloat("CrownBrakeLeft");
            brakeDir = tag.getInt("CrownBrakeDir");
            parkAngle = tag.getFloat("CrownParkAngle");
            // На сервере берём сохранённую скорость напрямую — не пересчитываем, чтобы переезд был
            // бесшовным. На клиенте пересчёт по кривой (как раньше): один тик иначе рисовался бы по
            // устаревшему значению.
            if (clientPacket) {
                if (braking) {
                    float mag = brakeAge <= brakeCoast
                            ? brakeV0
                            : brakeV0 * (1.0F - smoothstep((brakeAge - brakeCoast) / (float) brakeLen));
                    easedAngularSpeed = brakeDir * Math.min(mag, Math.max(0.0F, brakeRemaining));
                } else {
                    easedAngularSpeed = rampStart + (rampTarget - rampStart) * smoothstep(rampAge / (float) rampLen);
                }
            } else if (tag.contains("CrownEased")) {
                easedAngularSpeed = tag.getFloat("CrownEased");
            }
        }
    }

    private void doDisassemble() {
        // Если разборка застала бур ещё крутящимся (не доторможенным) — это резкий снос: снос блока,
        // либо разборка контраптии при физикализации. Запоминаем скорость на пару тиков по позиции,
        // чтобы пересобранный бур подхватил её и не разгонялся с нуля. Штатная остановка сюда приходит
        // уже с нулевой скоростью (доторможенной), поэтому она память не засоряет и следующий обычный
        // пуск стартует с нуля как надо.
        if (level != null && !level.isClientSide && Math.abs(easedAngularSpeed) > MIN_BRAKE_SPEED) {
            RecentSpeed.put(level, worldPosition, easedAngularSpeed);
        }
        // Парковка (parkAngle) НЕ обнуляется здесь: он уже выставлен в disassemble() на ближайшую
        // кратную 90 четверть, и именно в ней контраптия должна собраться, чтобы коллизия блоков
        // легла ровно (Create кладёт блоки по ориентации; некратный угол разъезжается — это чинили
        // раньше). Обнуление стояло ДО setAngle и всегда парковало в ноль, съедая четверть.
        braking = false;
        brakeV0 = 0.0F;
        brakeCoast = 0;
        brakeAge = 0;
        brakeRemaining = 0.0F;
        brakeGuard = 0;
        rampStart = 0.0F;
        rampTarget = 0.0F;
        rampAge = 0;
        rampLen = 1;
        easedAngularSpeed = 0.0F;
        // Мгновенно доводим угол до выбранной четверти ДО разборки: super.disassemble() соберёт
        // контраптию из текущего угла. Округляем на всякий случай — угол обязан быть кратен 90.
        float quarter = Math.round(parkAngle / PARK_STEP) * PARK_STEP;
        quarter = ((quarter % 360.0F) + 360.0F) % 360.0F;
        angle = quarter;
        if (movedContraption != null) {
            movedContraption.setAngle(quarter);
        }
        parkAngle = 0.0F;
        super.disassemble(); // ставит angle=0 у СЕБЯ и собирает контраптию из её текущей ориентации
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

    // ── Буфер и подключение к движку бурения Offroad ──
    //
    // Породу МЫ не ломаем. Движок Offroad — общий сервис: любой блок, который представится ему
    // поставщиком, может сказать «вот эти позиции надо выгрызть». Движок сам копит прогресс, сам
    // рисует трещины, сам ломает и сам возвращает нам каждый выпавший стак. Наше дело — решить,
    // ЧТО грызть (своя геометрия), и принять дроп.
    //
    // Заморозка при полном буфере получается сама: движок каждый тик спрашивает «ты ещё активен?»,
    // и отрицательный ответ снимает наши заявки. Прогресс разрушения не идёт, блоки не ломаются,
    // на землю ничего не падает. Освободился буфер — грызня продолжается.

    /*
     * Какие блоки грызть в этом тике. Заявки живут 20 тиков, поэтому подаём их заново каждый тик —
     * перестали подавать, и грызня сама затухает.
     *
     * Координаты — главный тонкий момент, из-за которого это долго не писалось. Корона едет в
     * контраптии Create, и её собственные координаты — локальные. Если машина собрана как физический
     * корабль, то даже после перевода в «мировые» это будут координаты ВНУТРИ корабля (Sable держит
     * корабли на гигантских координатах того же мира), а порода лежит в настоящем мире совсем в
     * другом месте. Грызть по ним — грызть пустоту.
     * Перевод делает хелпер Sable: он выталкивает точку наружу из корабля в настоящий мир, а если
     * машина обычная и никакого корабля нет — возвращает точку как есть. Поэтому одна и та же ветка
     * работает и на статичной стойке, и на физичной штуковине. Ровно так же устроено бурильное
     * колесо самих Offroad — оттуда и взято.
     */
    private void updateMiningBlocks() {
        if (level == null || level.isClientSide || movedContraption == null || !isActive()) {
            return;
        }
        var contraption = movedContraption.getContraption();
        if (contraption == null) {
            return;
        }
        BlockState self = getBlockState();
        if (!(self.getBlock() instanceof CrownBearingBlock)) {
            return;
        }
        Direction bearingFacing = self.getValue(CrownBearingBlock.FACING);
        double margin = ModConfig.TUNNEL_CLEARANCE.get();
        // Каждая корона грызёт СВОЙ цилиндр — так корректно считается и стойка из нескольких корон.
        for (Map.Entry<BlockPos, StructureBlockInfo> entry : contraption.getBlocks().entrySet()) {
            BlockState st = entry.getValue().state();
            if (!(st.getBlock() instanceof DrillCrownBlock crown)) {
                continue;
            }
            int side = sideOf(crown.crownSize());
            if (side <= 0) {
                continue;
            }
            Direction crownFacing = st.hasProperty(DrillCrownBlock.FACING)
                    ? st.getValue(DrillCrownBlock.FACING)
                    : bearingFacing;
            int layers = DrillCrownStructure.depthLayers(crown.crownSize());
            digCylinder(entry.getKey(), crownFacing, side, layers, margin);
        }
    }

    /*
     * Заказать грызню в цилиндре перед ОДНОЙ короной.
     *
     * Геометрия задана непрерывно: ось (мировое направление бурения), радиус (половина стороны короны
     * плюс запас из конфига) и отрезок вдоль оси (от плоскости мастера до передней грани плюс глубина
     * заказа). Блок попадает в заказ, если его ЦЕНТР лежит внутри этого цилиндра.
     *
     * Так сделано взамен прежнего «от каждой ячейки колонка вперёд плюс кольцо соседей». Прежний
     * способ наследовал форму самой короны: у неё два слоя разного радиуса (усечённый конус), поэтому
     * у передней грани тоннель выходил уже, а у задней шире — разница около клетки, ровно та, что
     * видна в тесте. Вдобавок заказ считался от ПОВЁРНУТЫХ на текущий угол ячеек с округлением их
     * центров к сетке блоков, из-за чего зона дрожала вместе с вращением и уезжала на клетку, когда
     * машина стоит не по сетке (на физкорабле это обычное дело). Цилиндр от оси не зависит ни от угла
     * поворота, ни от дробной позиции корабля: запас вокруг тела одинаков со всех сторон и по всей
     * длине.
     *
     * Заодно дешевле: перебирается один компактный ящик вокруг оси, а тяжёлая проверка блока делается
     * только для позиций, реально попавших в цилиндр.
     */
    private void digCylinder(BlockPos masterLocal, Direction crownFacing, int side, int layers, double margin) {
        Vec3 centre = toRealWorld(masterLocal.getCenter());
        Vec3 aheadOne = toRealWorld(masterLocal.getCenter().add(
                crownFacing.getStepX(), crownFacing.getStepY(), crownFacing.getStepZ()));
        Vec3 axis = aheadOne.subtract(centre);
        if (axis.lengthSqr() < 1.0E-6) {
            return;
        }
        axis = axis.normalize();

        double radius = side / 2.0 + margin;        // радиус тоннеля: тело короны плюс запас
        double from = 0.5;                          // сразу за плоскостью мастера
        double to = (layers - 1) + 0.5 + DIG_REACH; // передняя грань короны плюс глубина заказа
        double radiusSq = radius * radius;

        Vec3 segA = centre.add(axis.scale(from));
        Vec3 segB = centre.add(axis.scale(to));
        int minX = Mth.floor(Math.min(segA.x, segB.x) - radius);
        int maxX = Mth.ceil(Math.max(segA.x, segB.x) + radius);
        int minY = Mth.floor(Math.min(segA.y, segB.y) - radius);
        int maxY = Mth.ceil(Math.max(segA.y, segB.y) + radius);
        int minZ = Mth.floor(Math.min(segA.z, segB.z) - radius);
        int maxZ = Mth.ceil(Math.max(segA.z, segB.z) + radius);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double vx = x + 0.5 - centre.x;
                    double vy = y + 0.5 - centre.y;
                    double vz = z + 0.5 - centre.z;
                    double along = vx * axis.x + vy * axis.y + vz * axis.z;
                    if (along <= from || along > to) {
                        continue;
                    }
                    double radialSq = vx * vx + vy * vy + vz * vz - along * along;
                    if (radialSq > radiusSq) {
                        continue;
                    }
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir() || !PhysicsUtils.is_diggable(state)) {
                        continue;
                    }
                    MultiMiningServerManager.addOrRefreshPos(level, cursor.immutable(), this);
                }
            }
        }
    }

    // Локальная точка контраптии -> настоящий мир. Второй шаг обязателен: если машина собрана в
    // физический корабль, Sable держит его на гигантских координатах того же мира, и без выталкивания
    // наружу мы бы грызли пустоту. Обычная стойка — точка возвращается как есть.
    private Vec3 toRealWorld(Vec3 local) {
        return Sable.HELPER.projectOutOfSubLevel(level, movedContraption.toGlobalVector(local, 1.0F));
    }


    public CrownBufferHandler getBuffer() {
        if (buffer == null) {
            boolean sturdy = getBlockState().getBlock() == BlockRegistry.CROWN_BEARING_STURDY.get();
            int slots = sturdy ? ModConfig.BEARING_BUFFER_STURDY.get() : ModConfig.BEARING_BUFFER_ANDESITE.get();
            buffer = new CrownBufferHandler(slots);
        }
        return buffer;
    }

    // Активен ли бур с точки зрения движка. Полный буфер сюда входит намеренно — это и есть
    // заморозка. Торможение тоже: докручиваясь в исходный угол, бур уже не грызёт.
    @Override
    public boolean isActive() {
        // Грызём, пока подшипник СОБРАН и на валу есть обороты. Намеренно НЕ завязываемся на порог
        // скорости профиля: при физикализации профиль на миг сбрасывается и дёргается, и порог то и
        // дело выдавал бы «неактивен», обрывая грызню. Плавность темпа (нарастание при раскрутке,
        // затухание при торможении) обеспечивает getBreakingSpeed, который читает профиль напрямую.
        // Полный буфер намеренно гасит активность — это заморозка грызни.
        return !isRemoved()
                && movedContraption != null
                && getSpeed() != 0.0F
                && getBuffer().hasSpace();
    }

    // Кто заказал грызню — движок по этой позиции адресует клиенту показ трещин.
    @Override
    public BlockPos getLocation() {
        return isActive() ? worldPosition : null;
    }

    // Темп грызни одного блока. Движок делит его на твёрдость и копит, пока не наберётся порог.
    // Считается от ПРОФИЛЯ: как бур выглядит, так он и грызёт. Раскрутка — грызня нарастает вместе
    // с ней; торможение — затухает вместе с ним. - TUNE через BREAK_SPEED_DIVISOR
    @Override
    public float getBreakingSpeed(Level level, BlockPos pos, BlockState state) {
        if (!isActive()) {
            return 0.0F;
        }
        // Темп по ПРОФИЛЮ: раскрутка наращивает грызню, торможение затухает. Но пока профиль догоняет
        // обороты (в т.ч. сразу после перезапуска при физикализации), берём хотя бы малый пол, иначе
        // грызня замирает при собранном буре с живым валом. - TUNE через BREAK_SPEED_DIVISOR
        float fromProfile = Math.abs(easedAngularSpeed) / (BREAK_SPEED_DIVISOR * 3.0F); // *3 = ещё втрое медленнее (п.4)
        float floor = 0.017F; // ~медленно, но грызёт (втрое ниже прежнего, п.4)
        // Множитель материала: медь базовая, дальше быстрее (значения в конфиге). Средний по всем
        // коронам этого бура — на одном подшипнике можно смешать материалы, тогда темп усредняется.
        float speed = Math.max(fromProfile, floor) * materialFactor();
        return (float) Mth.clamp(speed, 0.001, 16.0);
    }

    // Средний множитель материала по всем коронам в контраптии. Если корон нет (не должно случаться
    // при активном буре) — 1.0. Считается на лету: коронов немного, а балансовые прав­ки конфига
    // подхватываются сразу.
    private float materialFactor() {
        if (movedContraption == null || movedContraption.getContraption() == null) {
            return 1.0F;
        }
        double sum = 0.0;
        int count = 0;
        for (StructureBlockInfo info : movedContraption.getContraption().getBlocks().values()) {
            if (info.state().getBlock() instanceof DrillCrownBlock crown) {
                sum += crown.crownMaterial().factor();
                count++;
            }
        }
        return count == 0 ? 1.0F : (float) (sum / count);
    }

    // Дроп сломанного блока. Что не влезло — движок предложит другим заказчикам, а потом уронит
    // на землю. Но до этого не дойдёт: полный буфер гасит isActive() и ломать перестают заранее.
    @Override
    public void itemCallback(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack rest = getBuffer().internalInsert(stack.copy());
        stack.setCount(rest.getCount());
        setChanged();
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
        // Физикализация/дефизикализация переносит машину через РАЗБОРКУ и мгновенную пересборку в
        // другом уровне. Между ними блок-сущность пересоздаётся, и обычно профиль стартовал бы с нуля
        // (бур заново разгоняется — это и был баг). Если ту же позицию только что покинул крутящийся
        // бур, подхватываем его скорость и стартуем профиль сразу на ней, без разгона. Память живёт
        // считанные тики и привязана к позиции, поэтому обычный пуск (никто недавно тут не крутился)
        // её не видит и разгоняется штатно.
        if (level != null && !level.isClientSide) {
            Float carried = RecentSpeed.take(level, worldPosition);
            if (carried != null && Math.abs(carried) > TARGET_EPSILON) {
                easedAngularSpeed = carried;
                rampStart = carried;
                rampTarget = carried;
                rampAge = rampLen;
            }
        }
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
