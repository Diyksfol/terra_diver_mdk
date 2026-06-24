package com.example.terradiver.pressure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Кэш трёх взаимосвязанных характеристик корпуса штуковины (TD_03):
 * {@code exterior_surface}, {@code valid_girder_lines}, {@code rib_coverage}.
 *
 * <p>Единственный оркестратор инвалидации — {@link #invalidate_hull_cache}.
 * Читатели ({@code compute_pressure_debuff_effective}, {@code compute_ambient_signal_volume},
 * {@code compute_pressure_gauge_needles}) обращаются к геттерам, не пересчитывая.
 *
 * <p><b>Архитектура.</b> Тяжёлые алгоритмы TD_03 разделены на два слоя, чтобы их можно было
 * юнит-тестировать без запущенного Minecraft:
 * <ul>
 *   <li><b>Чистые ядра</b> ({@link #computeExteriorVoid}, {@link #computeExteriorSurface},
 *       {@link #findValidGirderLines}) — работают с {@link BlockPos}/{@link Direction.Axis}
 *       и коллекциями. Зависят только от лёгких POJO MC, грузятся в тестах. Вся геометрия здесь.</li>
 *   <li><b>Тонкие MC-адаптеры</b> ({@link #extractSolidFullCubes}, {@link #extractGirders}) —
 *       единственная MC-специфика: перечислить блоки SubLevel и прочитать их свойства.
 *       Проверяются в игре, не юнит-тестом.</li>
 * </ul>
 * Спецификация — TD_06 v1.0, TD_03.
 */
public class HullCache {

    // 6-связность (грани): распространение пустоты (шаг 1) и соседство (шаг 2) в TD_06.
    private static final int[][] FACE_OFFSETS = {
        { 1, 0, 0}, {-1, 0, 0},
        { 0, 1, 0}, { 0,-1, 0},
        { 0, 0, 1}, { 0, 0,-1},
    };

    // Максимальная дальность луча торца балки (шаг 3b). TODO[CONFIG]: вынести в TerrraDiverConfig.
    private static final int DEFAULT_GIRDER_RAY_MAX_RANGE = 64;

    // Три кэша обновляются только атомарно через invalidate_hull_cache().
    // volatile — видимость между тиками (физический тик и потенциальные читатели).
    private volatile Set<BlockPos>     exteriorSurface  = Set.of();
    private volatile List<GirderLine>  validGirderLines = List.of();
    private volatile float             ribCoverage      = 0f;

    /** Внешняя поверхность корпуса (локальные координаты SubLevel). */
    public Set<BlockPos>    getExteriorSurface()  { return exteriorSurface; }

    /** Валидные линии балок жёсткости. */
    public List<GirderLine> getValidGirderLines() { return validGirderLines; }

    /** Доля внешней поверхности, покрытая балками жёсткости, [0.0, 1.0]. */
    public float            getRibCoverage()      { return ribCoverage; }

    // ════════════════════════════════════════════════════════════════════════════
    //  compute_exterior_surface() — чистое ядро (+ внешняя пустота)
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Множество клеток «внешней пустоты» — пустота, достижимая снаружи структуры (flood-fill
     * от угла bounding box, расширенного на 1). Внутренние полости сюда НЕ попадают: они
     * отрезаны корпусом и от внешнего угла недостижимы. Локальные координаты SubLevel.
     *
     * <p>Это промежуточный результат: {@link #computeExteriorSurface} выводит из него
     * поверхность, а {@link #findValidGirderLines} использует его для проверки нормали торца
     * (нужно знать, где ВНЕШНЯЯ пустота, а не любой воздух). Не кэшируется — живёт только
     * в пределах одного пересчёта {@link #invalidate_hull_cache}.
     *
     * @param solidFullCubes позиции блоков с полной кубической коллизией (локальные координаты)
     * @return неизменяемое множество клеток внешней пустоты
     */
    public static Set<BlockPos> computeExteriorVoid(Set<BlockPos> solidFullCubes) {
        if (solidFullCubes == null || solidFullCubes.isEmpty()) {
            return Set.of();
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : solidFullCubes) {
            int x = p.getX(), y = p.getY(), z = p.getZ();
            if (x < minX) minX = x;  if (x > maxX) maxX = x;
            if (y < minY) minY = y;  if (y > maxY) maxY = y;
            if (z < minZ) minZ = z;  if (z > maxZ) maxZ = z;
        }

        // Расширение на 1: внешний слой — связная оболочка гарантированной пустоты.
        final int oX = minX - 1, oY = minY - 1, oZ = minZ - 1;
        final int sizeX = (maxX + 1) - oX + 1;
        final int sizeY = (maxY + 1) - oY + 1;
        final int sizeZ = (maxZ + 1) - oZ + 1;
        final int volume = sizeX * sizeY * sizeZ;

        boolean[] solid = new boolean[volume];
        for (BlockPos p : solidFullCubes) {
            solid[idx(p.getX() - oX, p.getY() - oY, p.getZ() - oZ, sizeY, sizeZ)] = true;
        }

        boolean[] visited = new boolean[volume];
        Set<BlockPos> voidCells = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        visited[idx(0, 0, 0, sizeY, sizeZ)] = true;        // угол гарантированно не solid
        queue.add(new int[]{0, 0, 0});
        voidCells.add(new BlockPos(oX, oY, oZ));
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            int cx = c[0], cy = c[1], cz = c[2];
            for (int[] d : FACE_OFFSETS) {
                int nx = cx + d[0], ny = cy + d[1], nz = cz + d[2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= sizeX || ny >= sizeY || nz >= sizeZ) {
                    continue;
                }
                int ni = idx(nx, ny, nz, sizeY, sizeZ);
                if (solid[ni] || visited[ni]) {
                    continue; // полный блок останавливает заливку; посещённое не трогаем
                }
                visited[ni] = true;
                queue.add(new int[]{nx, ny, nz});
                voidCells.add(new BlockPos(nx + oX, ny + oY, nz + oZ));
            }
        }
        return Set.copyOf(voidCells);
    }

    /**
     * compute_exterior_surface() — блоки корпуса, граничащие с внешней пустотой.
     * Спецификация: TD_06 v1.0, TD_03. Обоснование (внутренние полости, локальная система
     * координат, почему вход — только полные кубы) — в TD_06 и в комментарии
     * {@link #computeExteriorVoid}.
     *
     * @param solidFullCubes позиции блоков с полной кубической коллизией (локальные координаты)
     * @return неизменяемое множество позиций внешней поверхности
     */
    public static Set<BlockPos> computeExteriorSurface(Set<BlockPos> solidFullCubes) {
        if (solidFullCubes == null || solidFullCubes.isEmpty()) {
            return Set.of(); // открытая/пустая конструкция → поверхности нет (TD_06)
        }
        return surfaceFromVoid(solidFullCubes, computeExteriorVoid(solidFullCubes));
    }

    /** Блок поверхности ⟺ хотя бы один из 6 соседей — внешняя пустота (шаг 2 TD_06). */
    private static Set<BlockPos> surfaceFromVoid(Set<BlockPos> solidFullCubes,
                                                 Set<BlockPos> exteriorVoid) {
        Set<BlockPos> surface = new HashSet<>();
        for (BlockPos p : solidFullCubes) {
            for (int[] d : FACE_OFFSETS) {
                if (exteriorVoid.contains(new BlockPos(p.getX() + d[0], p.getY() + d[1], p.getZ() + d[2]))) {
                    surface.add(p);
                    break;
                }
            }
        }
        return Set.copyOf(surface);
    }

    /** Совершённый индекс клетки внутри расширенного box: коллизий нет по построению. */
    private static int idx(int x, int y, int z, int sizeY, int sizeZ) {
        return (x * sizeY + y) * sizeZ + z;
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  find_valid_girder_lines() — чистое ядро
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * find_valid_girder_lines() — <b>чистое ядро</b>. Валидные линии балок жёсткости: прямые
     * цепочки {@code metal_girder} вдоль одной оси, ОБА торца которых перпендикулярно упираются
     * во внешнюю стену корпуса. Спецификация: TD_06 v1.0, TD_03.
     *
     * <p><b>Алгоритм</b> (шаги по TD_06):
     * <ol>
     *   <li>Сгруппировать балки в непрерывные прямые цепочки по их осевому блокстейту
     *       (минимум 2 блока). Цепочка по оси A — последовательные по A балки с блокстейт-осью A.</li>
     *   <li>Для каждого торца пустить луч ВДОЛЬ оси наружу. Луч прозрачен для всего, кроме полных
     *       кубов (неполные блоки/перегородки пропускаются); останавливается на первом блоке из
     *       {@code solidFullCubes} — это {@code W}.</li>
     *   <li>Торец валиден ⟺ клетка ЗА {@code W} ({@code W + d}) — внешняя пустота. Это разом
     *       проверяет «{@code W} ∈ exterior_surface» и «нормаль {@code W} вдоль оси линии».</li>
     *   <li>Линия валидна ⟺ оба торца валидны.</li>
     * </ol>
     *
     * <p><b>Почему вход — {@code exteriorVoid}, а не {@code exterior_surface}.</b> Спека описывает
     * нормаль как «направление к ближайшей ВНЕШНЕЙ пустоте». По одному лишь множеству поверхности
     * нельзя отличить внешнюю грань блока от грани во ВНУТРЕННЮЮ полость — а это различает настоящую
     * распорку (балка кабина→внешняя стена) от ложной (балка во внутреннюю перегородку между двумя
     * комнатами). Поэтому ядру передаётся множество внешней пустоты; {@link #invalidate_hull_cache}
     * считает его один раз и кэширует уже выведенную из него поверхность.
     *
     * <p><b>Граничные случаи (TD_06):</b> параллельная/толстая стена (за {@code W} снова блок →
     * невалид); неполная перегородка прозрачна для луча; цепочка длины 1 отброшена; балка в пустоту
     * невалидна; параллельные линии дают разные точки покрытия.
     *
     * @param girders        позиция балки → её осевой блокстейт (X/Y/Z)
     * @param solidFullCubes все полные кубы корпуса (для остановки луча)
     * @param exteriorVoid   множество клеток внешней пустоты (для проверки нормали торца)
     * @param maxRayRange    предел дальности луча торца (отсекает трассы в пустоту)
     * @return неизменяемый список валидных линий
     */
    public static List<GirderLine> findValidGirderLines(Map<BlockPos, Direction.Axis> girders,
                                                        Set<BlockPos> solidFullCubes,
                                                        Set<BlockPos> exteriorVoid,
                                                        int maxRayRange) {
        if (girders == null || girders.isEmpty()) {
            return List.of();
        }

        // Индекс «ось → её балки» — группировка по блокстейт-оси за O(1) на блок (не O(n²)).
        Map<Direction.Axis, Set<BlockPos>> byAxis = new EnumMap<>(Direction.Axis.class);
        for (var e : girders.entrySet()) {
            byAxis.computeIfAbsent(e.getValue(), a -> new HashSet<>()).add(e.getKey());
        }

        List<GirderLine> lines = new ArrayList<>();
        for (Direction.Axis axis : Direction.Axis.values()) {
            Set<BlockPos> members = byAxis.get(axis);
            if (members == null) {
                continue;
            }
            int ux = axis == Direction.Axis.X ? 1 : 0;
            int uy = axis == Direction.Axis.Y ? 1 : 0;
            int uz = axis == Direction.Axis.Z ? 1 : 0;

            for (BlockPos p : members) {
                // Начало цепочки — балка, у которой предыдущей по оси нет.
                if (members.contains(new BlockPos(p.getX() - ux, p.getY() - uy, p.getZ() - uz))) {
                    continue;
                }
                // Пройти цепочку вперёд до конца.
                BlockPos a = p, cur = p;
                while (true) {
                    BlockPos next = new BlockPos(cur.getX() + ux, cur.getY() + uy, cur.getZ() + uz);
                    if (members.contains(next)) {
                        cur = next;
                    } else {
                        break;
                    }
                }
                BlockPos b = cur;
                if (a.equals(b)) {
                    continue; // длина 1 — не линия (минимум 2)
                }
                // Торец A смотрит наружу в −ось, торец B — в +ось.
                boolean validA = endpointBracesExteriorWall(a, -ux, -uy, -uz, solidFullCubes, exteriorVoid, maxRayRange);
                boolean validB = endpointBracesExteriorWall(b,  ux,  uy,  uz, solidFullCubes, exteriorVoid, maxRayRange);
                if (validA && validB) {
                    lines.add(new GirderLine(axis, a, b));
                }
            }
        }
        return List.copyOf(lines);
    }

    /**
     * Луч от торца наружу (шаги 3b–3c): прозрачен для всего, кроме полных кубов; останавливается
     * на первом полном кубе {@code W}; торец валиден ⟺ клетка за {@code W} ({@code W + d}) —
     * внешняя пустота (т.е. {@code W} — внешняя стена, нормаль вдоль оси луча).
     */
    private static boolean endpointBracesExteriorWall(BlockPos endpoint, int dx, int dy, int dz,
                                                      Set<BlockPos> solidFullCubes,
                                                      Set<BlockPos> exteriorVoid,
                                                      int maxRayRange) {
        int x = endpoint.getX(), y = endpoint.getY(), z = endpoint.getZ();
        for (int i = 0; i < maxRayRange; i++) {
            x += dx; y += dy; z += dz;
            BlockPos pos = new BlockPos(x, y, z);
            if (solidFullCubes.contains(pos)) {                       // первый полный куб = W
                return exteriorVoid.contains(new BlockPos(x + dx, y + dy, z + dz)); // W + d
            }
        }
        return false; // в пределах дальности стены нет → торец ни на что не опирается
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  invalidate_hull_cache() — оркестратор
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * invalidate_hull_cache() — атомарно пересчитать все три кэша корпуса.
     * Спецификация: TD_06 v1.0, TD_03.
     *
     * <p><b>Порядок строгий:</b> exterior_surface → girder_lines → rib_coverage. MC-данные
     * (полные кубы, балки) извлекаются по одному разу; внешняя пустота считается один раз и
     * переиспользуется для поверхности и для проверки торцов балок.
     *
     * <p><b>Атомарность:</b> три поля обновляются одновременно в конце метода.
     * <p><b>Батчинг:</b> вызывается один раз после завершения сборки/разборки SubLevel.
     *
     * @param subLevel    штуковина, чей корпус изменился
     * @param supportStep константа затухания поддержки балок (TerrraDiverConfig.SUPPORT_STEP)
     */
    public void invalidate_hull_cache(Object subLevel, float supportStep) {
        // MC-извлечение (по разу).
        Set<BlockPos> solidFullCubes = extractSolidFullCubes(subLevel);
        Map<BlockPos, Direction.Axis> girders = extractGirders(subLevel);

        // Чистые ядра.
        Set<BlockPos> exteriorVoid    = computeExteriorVoid(solidFullCubes);   // шаг 1 (база)
        Set<BlockPos> exteriorSurface = surfaceFromVoid(solidFullCubes, exteriorVoid);
        List<GirderLine> girderLines  = findValidGirderLines(                  // шаг 2
            girders, solidFullCubes, exteriorVoid, DEFAULT_GIRDER_RAY_MAX_RANGE);
        float ribCov = computeRibCoverage(exteriorSurface, girderLines, supportStep); // шаг 3

        // Атомарная запись.
        this.exteriorSurface  = exteriorSurface;
        this.validGirderLines = girderLines;
        this.ribCoverage      = ribCov;
    }

    // ── MC-адаптеры (проверяются в игре, не юнит-тестом) ────────────────────────

    /**
     * Извлечь позиции блоков с полной кубической коллизией из SubLevel.
     * TODO[API-CHECK]: перебор блоков SubLevel в ЛОКАЛЬНЫХ координатах и сравнение
     * {@code state.getCollisionShape(...) == Shapes.block()}.
     */
    private static Set<BlockPos> extractSolidFullCubes(Object subLevel) {
        // TODO[API-CHECK]
        return Set.of();
    }

    /**
     * Извлечь балки {@code create:metal_girder} из SubLevel: позиция → осевой блокстейт.
     * TODO[API-CHECK]: перебор блоков SubLevel, фильтр по типу metal_girder, чтение свойства
     * {@code axis} ({@code BlockStateProperties.AXIS}) в локальных координатах. Допущение TD_06
     * (Проверка A): metal_girder имеет осевой блокстейт и НЕполную коллизию (не попадает в
     * {@code solidFullCubes}).
     */
    private static Map<BlockPos, Direction.Axis> extractGirders(Object subLevel) {
        // TODO[API-CHECK]
        return Map.of();
    }

    // ── Заглушка следующей функции TD_03 ────────────────────────────────────────

    /**
     * compute_rib_coverage() — доля exterior_surface, покрытая балками (multi-source BFS).
     * TODO[IMPL]: реализовать (следующая функция графа TD_03, после find_valid_girder_lines).
     * Граничный случай: exteriorSurface пуст → возвращать 0.0, не делить на 0.
     */
    private static float computeRibCoverage(Set<BlockPos> exteriorSurface,
                                            List<GirderLine> validGirderLines,
                                            float supportStep) {
        return 0f; // placeholder
    }
}