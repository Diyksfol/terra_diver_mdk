package com.example.terradiver.pressure;

import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Кэш трёх взаимосвязанных характеристик корпуса штуковины (TD_03):
 * {@code exterior_surface}, {@code valid_girder_lines}, {@code rib_coverage}.
 *
 * <p>Единственный оркестратор инвалидации — {@link #invalidate_hull_cache}.
 * Читатели ({@code compute_pressure_debuff_effective}, {@code compute_ambient_signal_volume},
 * {@code compute_pressure_gauge_needles}) обращаются к геттерам, не пересчитывая.
 *
 * <p><b>Архитектура (важно).</b> Тяжёлый алгоритм {@code compute_exterior_surface}
 * разделён на два слоя, чтобы его можно было юнит-тестировать без запущенного Minecraft:
 * <ul>
 *   <li>{@link #computeExteriorSurface(Set)} — <b>чистое ядро</b>. Вход — множество позиций
 *       блоков с полной кубической коллизией; выход — подмножество, образующее внешнюю
 *       поверхность. Зависит только от {@link BlockPos} (лёгкий POJO, грузится в тестах).
 *       Вся логика flood-fill живёт здесь.</li>
 *   <li>{@link #computeExteriorSurface(Object)} — <b>тонкий MC-адаптер</b>. Единственная
 *       MC-специфичная задача: перечислить блоки SubLevel и оставить те, у кого
 *       {@code getCollisionShape() == Shapes.block()}. Проверяется в игре, не юнит-тестом.</li>
 * </ul>
 * Спецификация — TD_06 v1.0, TD_03.
 */
public class HullCache {

    // 6-связность (грани), как в шаге 1 (распространение пустоты) и шаге 2 (соседство) TD_06.
    private static final int[][] FACE_OFFSETS = {
        { 1, 0, 0}, {-1, 0, 0},
        { 0, 1, 0}, { 0,-1, 0},
        { 0, 0, 1}, { 0, 0,-1},
    };

    // Три кэша обновляются только атомарно через invalidate_hull_cache().
    // volatile — видимость между тиками (физический тик и потенциальные читатели).
    private volatile Set<BlockPos> exteriorSurface  = Set.of();
    private volatile List<Object>  validGirderLines = List.of(); // TODO: заменить Object на GirderLine
    private volatile float         ribCoverage      = 0f;

    /** Внешняя поверхность корпуса (локальные координаты SubLevel). */
    public Set<BlockPos> getExteriorSurface()  { return exteriorSurface; }

    /** Валидные линии балок жёсткости. */
    public List<Object>  getValidGirderLines() { return validGirderLines; }

    /** Доля внешней поверхности, покрытая балками жёсткости, [0.0, 1.0]. */
    public float         getRibCoverage()      { return ribCoverage; }

    // ── compute_exterior_surface() ───────────────────────────────────────────────

    /**
     * compute_exterior_surface() — <b>чистое ядро</b>. Множество блоков корпуса, образующих
     * внешнюю поверхность: блоки с полной коллизией, граничащие с «внешней пустотой» —
     * пустотой, достижимой снаружи структуры. Спецификация: TD_06 v1.0, TD_03.
     *
     * <p><b>Алгоритм</b> (flood-fill, шаги по TD_06):
     * <ol>
     *   <li>Bounding box по {@code solidFullCubes}, расширенный на 1 блок. Внешний слой
     *       расширенного box гарантированно вне структуры → это связная оболочка пустоты.</li>
     *   <li>Flood-fill от угла расширенного box по всем НЕ-полным клеткам (воздух, частичные
     *       блоки, жидкости — всё, чего нет в {@code solidFullCubes}). Достигнутые клетки —
     *       «внешняя пустота».</li>
     *   <li>Блок корпуса входит в результат ⟺ хотя бы один из 6 соседей — внешняя пустота.</li>
     * </ol>
     *
     * <p><b>Почему вход — только полные кубы.</b> Шаг 1 TD_06 распространяет пустоту «по
     * воздуху/отсутствующим блокам»; частичные блоки (двери, лестницы, люки) и жидкости
     * не герметизируют корпус против давления → flood проходит сквозь них. Шаг 2 повышает
     * до поверхности только полные кубы. Передавая лишь множество полных кубов, мы получаем
     * обе семантики разом: всё вне множества — проходимо для заливки и не может стать
     * поверхностью. MC-проверка {@code getCollisionShape() == Shapes.block()} вынесена
     * в адаптер {@link #computeExteriorSurface(Object)}, ядро о Mc-типах не знает.
     *
     * <p><b>Граничные случаи (TD_06):</b>
     * <ul>
     *   <li><b>Внутренние полости</b> (кабина, отсеки): их пустота не достижима от угла →
     *       не «внешняя». Блоки вокруг закрытой полости в результат не входят (давление
     *       действует снаружи внутрь). Достигается тем, что flood идёт только от внешней
     *       оболочки.</li>
     *   <li><b>Открытая/пустая конструкция</b>: {@code solidFullCubes} пуст или null →
     *       пустой результат. Не ошибка — {@code rib_coverage} получится ≈0.</li>
     *   <li><b>Локальная система координат обязательна</b>: алгоритм ничего не знает о мире
     *       (порода/воздух вокруг штуковины). Он работает по структуре самой штуковины в её
     *       координатах — потому что вход уже в локальных координатах SubLevel. Мировой
     *       flood-fill под землёй схлопнул бы поверхность в пусто (критическая ошибка TD_06),
     *       здесь этого не может быть по построению.</li>
     * </ul>
     *
     * <p><b>Оптимизация:</b> функция дорогая, но кэшируется (вызов только из
     * {@link #invalidate_hull_cache} при изменении корпуса, не каждый тик). Внутри: заливка
     * по плоскому {@code boolean[]} с совершенной индексацией внутри расширенного box —
     * без боксинга и аллокаций {@link BlockPos} в горячем цикле.
     *
     * @param solidFullCubes позиции блоков с полной кубической коллизией (локальные координаты)
     * @return неизменяемое множество позиций внешней поверхности
     */
    public static Set<BlockPos> computeExteriorSurface(Set<BlockPos> solidFullCubes) {
        if (solidFullCubes == null || solidFullCubes.isEmpty()) {
            return Set.of(); // открытая/пустая конструкция → поверхности нет (TD_06)
        }

        // Шаг 1а: bounding box по корпусу.
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : solidFullCubes) {
            int x = p.getX(), y = p.getY(), z = p.getZ();
            if (x < minX) minX = x;  if (x > maxX) maxX = x;
            if (y < minY) minY = y;  if (y > maxY) maxY = y;
            if (z < minZ) minZ = z;  if (z > maxZ) maxZ = z;
        }

        // Шаг 1б: расширение на 1 блок. Внешний слой — связная оболочка гарантированной пустоты.
        final int oX = minX - 1, oY = minY - 1, oZ = minZ - 1; // origin расширенного box
        final int sizeX = (maxX + 1) - oX + 1;
        final int sizeY = (maxY + 1) - oY + 1;
        final int sizeZ = (maxZ + 1) - oZ + 1;
        final int volume = sizeX * sizeY * sizeZ;

        // Совершенная индексация: каждая клетка расширенного box ↔ один индекс, без коллизий.
        boolean[] solid = new boolean[volume];
        for (BlockPos p : solidFullCubes) {
            solid[idx(p.getX() - oX, p.getY() - oY, p.getZ() - oZ, sizeY, sizeZ)] = true;
        }

        // Шаг 2: flood-fill внешней пустоты от угла расширенного box.
        // Угол (0,0,0) локально — это (oX,oY,oZ): он на оболочке расширения → заведомо не solid.
        boolean[] exteriorVoid = new boolean[volume];
        Deque<int[]> queue = new ArrayDeque<>();
        exteriorVoid[idx(0, 0, 0, sizeY, sizeZ)] = true;
        queue.add(new int[]{0, 0, 0});
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            int cx = c[0], cy = c[1], cz = c[2];
            for (int[] d : FACE_OFFSETS) {
                int nx = cx + d[0], ny = cy + d[1], nz = cz + d[2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= sizeX || ny >= sizeY || nz >= sizeZ) {
                    continue; // за пределами расширенного box
                }
                int ni = idx(nx, ny, nz, sizeY, sizeZ);
                if (solid[ni] || exteriorVoid[ni]) {
                    continue; // полный блок останавливает заливку; посещённое не трогаем
                }
                exteriorVoid[ni] = true;
                queue.add(new int[]{nx, ny, nz});
            }
        }

        // Шаг 3: блок поверхности ⟺ есть сосед из внешней пустоты.
        Set<BlockPos> surface = new HashSet<>();
        for (BlockPos p : solidFullCubes) {
            int lx = p.getX() - oX, ly = p.getY() - oY, lz = p.getZ() - oZ;
            for (int[] d : FACE_OFFSETS) {
                int nx = lx + d[0], ny = ly + d[1], nz = lz + d[2];
                // Сосед solid-блока всегда в пределах расширенного box (запас 1 это гарантирует).
                if (exteriorVoid[idx(nx, ny, nz, sizeY, sizeZ)]) {
                    surface.add(p);
                    break;
                }
            }
        }
        return Set.copyOf(surface); // неизменяемый результат (читатели не мутируют кэш)
    }

    /** Совершённый индекс клетки внутри расширенного box: коллизий нет по построению. */
    private static int idx(int x, int y, int z, int sizeY, int sizeZ) {
        return (x * sizeY + y) * sizeZ + z;
    }

    /**
     * compute_exterior_surface() — <b>MC-адаптер</b>. Извлекает из SubLevel позиции блоков
     * с полной кубической коллизией и делегирует чистому ядру {@link #computeExteriorSurface(Set)}.
     *
     * <p>Единственный MC-специфичный шаг: перечислить блоки SubLevel и оставить те, у кого
     * {@code state.getCollisionShape(...) == Shapes.block()} (TD_06, шаг 2). Координаты —
     * локальные для SubLevel (НЕ мировые: мировой flood-fill под землёй ломает механику).
     *
     * @param subLevel контекст SubLevel (тип не подтверждён → Object, как в остальном TD_03-коде)
     * @return неизменяемое множество позиций внешней поверхности
     */
    static Set<BlockPos> computeExteriorSurface(Object subLevel) {
        Set<BlockPos> solidFullCubes = extractSolidFullCubes(subLevel);
        return computeExteriorSurface(solidFullCubes);
    }

    /**
     * Извлечь позиции блоков с полной кубической коллизией из SubLevel.
     *
     * <p>TODO[API-CHECK]: реальная реализация требует подтверждённого API SubLevel:
     * перебор блоков и доступ к {@code BlockState} в ЛОКАЛЬНЫХ координатах. Псевдокод:
     * <pre>
     *   Set&lt;BlockPos&gt; result = new HashSet&lt;&gt;();
     *   for (BlockPos local : subLevel.getAllLocalBlockPositions()) {     // TODO: verify
     *       BlockState state = subLevel.getBlockState(local);             // TODO: verify (локальные коорд.)
     *       if (state.getCollisionShape(subLevel, local) == Shapes.block()) { // TODO: verify сравнение формы
     *           result.add(local.immutable());
     *       }
     *   }
     *   return result;
     * </pre>
     * Пока возвращает пустое множество — invalidate отработает штатно (ribCoverage=0),
     * а ядро {@link #computeExteriorSurface(Set)} уже полностью реализовано и протестировано.
     */
    private static Set<BlockPos> extractSolidFullCubes(Object subLevel) {
        // TODO[API-CHECK]: заполнить через подтверждённый API перебора блоков SubLevel.
        return Set.of();
    }

    // ── invalidate_hull_cache() ──────────────────────────────────────────────────

    /**
     * invalidate_hull_cache() — атомарно пересчитать все три кэша корпуса.
     * Спецификация: TD_06 v1.0, TD_03.
     *
     * <p><b>Порядок строгий:</b> exterior_surface → girder_lines → rib_coverage.
     * Каждый следующий шаг читает результат предыдущего — перестановка нарушает
     * топологический порядок зависимостей и приводит к использованию устаревших данных.
     *
     * <p><b>Атомарность:</b> три поля обновляются одновременно в конце метода, не по одному.
     * Между пересчётом и сохранением читатели видят предыдущее согласованное состояние.
     *
     * <p><b>Батчинг:</b> вызывается один раз после завершения всей операции сборки/разборки
     * SubLevel — не на каждый добавленный блок. Источник истины по батчингу здесь.
     *
     * @param subLevel    штуковина, чей корпус изменился
     * @param supportStep константа затухания поддержки балок (TerrraDiverConfig.SUPPORT_STEP)
     */
    public void invalidate_hull_cache(Object subLevel, float supportStep) {
        // Шаг 1: внешняя поверхность — база, от неё зависят шаги 2 и 3.
        Set<BlockPos> newExteriorSurface = computeExteriorSurface(subLevel);

        // Шаг 2: валидные линии балок — зависит от exterior_surface шага 1.
        List<Object> newGirderLines = findValidGirderLines(subLevel, newExteriorSurface);

        // Шаг 3: покрытие балками — зависит от результатов шагов 1 и 2.
        // Если exterior_surface пуст (открытая конструкция) → 0.0, без NPE/деления на ноль.
        float newRibCoverage = computeRibCoverage(newExteriorSurface, newGirderLines, supportStep);

        // Шаг 4: атомарная запись — читатели не видят смесь «новый surface + старый ribCoverage».
        this.exteriorSurface  = newExteriorSurface;
        this.validGirderLines = newGirderLines;
        this.ribCoverage      = newRibCoverage;
    }

    // ── Заглушки следующих функций TD_03 (вне рамок текущей задачи) ──────────────

    /**
     * find_valid_girder_lines() — поиск валидных линий балок жёсткости.
     * TODO[IMPL]: реализовать (следующая функция в графе TD_03 после compute_exterior_surface).
     * TODO: заменить {@code Object} на тип GirderLine после его определения.
     */
    private static List<Object> findValidGirderLines(Object subLevel, Set<BlockPos> exteriorSurface) {
        return List.of(); // placeholder
    }

    /**
     * compute_rib_coverage() — доля exterior_surface, покрытая балками (multi-source BFS).
     * TODO[IMPL]: реализовать (после find_valid_girder_lines).
     * Граничный случай: exteriorSurface пуст → возвращать 0.0, не делить на 0.
     */
    private static float computeRibCoverage(Set<BlockPos> exteriorSurface,
                                            List<Object>  validGirderLines,
                                            float         supportStep) {
        return 0f; // placeholder
    }
}