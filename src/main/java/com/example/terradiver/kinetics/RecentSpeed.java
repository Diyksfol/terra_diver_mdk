package com.example.terradiver.kinetics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/*
 * Короткоживущая память скорости бурового подшипника по позиции.
 *
 * Зачем: физикализация и обратный переход переносят машину через РАЗБОРКУ контраптии и мгновенную
 * пересборку в другом уровне. Между ними блок-сущность пересоздаётся, и её поле скорости стартовало
 * бы с нуля — бур заново разгонялся (это и был баг перехода). Сохранять в NBT бесполезно: перенос
 * идёт не через save/load блока, а через роспуск и новую сборку.
 *
 * Решение: при разборке крутящегося бура кладём сюда его скорость с отметкой времени. При новой
 * сборке той же позиции в пределах короткого окна — забираем и стартуем профиль сразу на ней. Память
 * привязана к позиции и живёт считанные тики, поэтому обычный пуск (недавно тут никто не крутился) её
 * не видит и разгоняется штатно. Никакого дюпа состояния: take() удаляет запись.
 *
 * Только серверная сторона. Ключ включает измерение уровня: у обычного мира и подлевела Sable оно
 * разное, но позиция при переносе пересчитывается — поэтому кладём и берём по фактической worldPosition
 * в том уровне, где происходит разборка/сборка, а окно времени покрывает перенос между ними.
 */
final class RecentSpeed {

    private RecentSpeed() {}

    // Окно жизни записи в игровых тиках. Перенос происходит в тот же тик или соседний; 20 тиков —
    // щедрый запас, за который обычный игрок не успеет собрать в этой же точке НОВЫЙ бур вручную.
    private static final long WINDOW_TICKS = 20L;

    private record Entry(float speed, long time) {}

    private static final Map<Long, Entry> BY_POS = new HashMap<>();

    static synchronized void put(Level level, BlockPos pos, float speed) {
        prune(level.getGameTime());
        BY_POS.put(pos.asLong(), new Entry(speed, level.getGameTime()));
    }

    // Забрать и удалить. null — если записи нет или она протухла.
    static synchronized Float take(Level level, BlockPos pos) {
        prune(level.getGameTime());
        Entry e = BY_POS.remove(pos.asLong());
        return e == null ? null : e.speed();
    }

    private static void prune(long now) {
        Iterator<Map.Entry<Long, Entry>> it = BY_POS.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().time() > WINDOW_TICKS) {
                it.remove();
            }
        }
    }
}
