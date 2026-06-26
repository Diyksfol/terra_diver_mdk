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
import java.util.HashMap;

/*
 * Кэш трёх характеристик корпуса (TD_03): exterior_surface, valid_girder_lines, rib_coverage.
 * Единственный оркестратор инвалидации — invalidate_hull_cache. Читатели обращаются к геттерам.
 *
 * Архитектура: тяжёлые алгоритмы разделены на чистые ядра (computeExteriorVoid,
 * computeExteriorSurface, findValidGirderLines — работают с BlockPos/Axis и коллекциями,
 * грузятся в тестах без MC) и тонкие MC-адаптеры (extractSolidFullCubes, extractGirders —
 * перебор блоков SubLevel, проверяются в игре). Спецификация — TD_06 v1.0, TD_03.
 */
public class HullCache {

    // 6-связность (грани): распространение пустоты и соседство.
    private static final int[][] FACE_OFFSETS = {
        { 1, 0, 0}, {-1, 0, 0},
        { 0, 1, 0}, { 0,-1, 0},
        { 0, 0, 1}, { 0, 0,-1},
    };

    // Максимальная дальность луча торца балки. TODO[CONFIG]: вынести в TerrraDiverConfig.
    private static final int DEFAULT_GIRDER_RAY_MAX_RANGE = 64;

    // Обновляются только атомарно через invalidate_hull_cache(). volatile — видимость между тиками.
    private volatile Set<BlockPos>     exteriorSurface  = Set.of();
    private volatile List<GirderLine>  validGirderLines = List.of();
    private volatile float             ribCoverage      = 0f;

    public Set<BlockPos>    getExteriorSurface()  { return exteriorSurface; }
    public List<GirderLine> getValidGirderLines() { return validGirderLines; }
    public float            getRibCoverage()      { return ribCoverage; }

    // ════════════════════════════════════════════════════════════════════════════
    //  compute_exterior_surface (+ внешняя пустота)
    // ════════════════════════════════════════════════════════════════════════════

    /*
     * Клетки «внешней пустоты» — пустота, достижимая снаружи структуры (flood-fill от угла
     * bounding box, расширенного на 1). Внутренние полости недостижимы и сюда не попадают.
     * Локальные координаты SubLevel.
     *
     * Промежуточный результат: computeExteriorSurface выводит из него поверхность,
     * findValidGirderLines использует его для проверки нормали торца (нужно знать, где ВНЕШНЯЯ
     * пустота, а не любой воздух). Не кэшируется — живёт в пределах одного пересчёта.
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

    /*
     * compute_exterior_surface — блоки корпуса, граничащие с внешней пустотой.
     * Назначение, граничные случаи, обоснование локальной системы координат — см. TD_06 v1.0,
     * compute_exterior_surface.
     */
    public static Set<BlockPos> computeExteriorSurface(Set<BlockPos> solidFullCubes) {
        if (solidFullCubes == null || solidFullCubes.isEmpty()) {
            return Set.of(); // открытая/пустая конструкция (см. TD_06)
        }
        return surfaceFromVoid(solidFullCubes, computeExteriorVoid(solidFullCubes));
    }

    // Блок поверхности, если хотя бы один из 6 соседей — внешняя пустота (шаг 2, TD_06).
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

    // Совершённый индекс клетки внутри расширенного box: коллизий нет по построению.
    private static int idx(int x, int y, int z, int sizeY, int sizeZ) {
        return (x * sizeY + y) * sizeZ + z;
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  find_valid_girder_lines
    // ════════════════════════════════════════════════════════════════════════════

    /*
     * find_valid_girder_lines — валидные линии балок жёсткости. Назначение, шаги логики
     * и граничные случаи — см. TD_06 v1.0, find_valid_girder_lines.
     *
     * Отступление от спеки (не в TD_06): на вход идёт exteriorVoid, а не exterior_surface.
     * Проверка «нормаль торца к ближайшей ВНЕШНЕЙ пустоте» по одному множеству поверхности
     * невыполнима — нельзя отличить внешнюю грань от грани во внутреннюю полость. Это различает
     * настоящую распорку (кабина→внешняя стена) от ложной (во внутреннюю перегородку). Торец
     * валиден, если клетка за первым полным кубом W (W+d) — внешняя пустота; это разом проверяет
     * и «W в exterior_surface», и «нормаль W вдоль оси линии».
     */
    public static List<GirderLine> findValidGirderLines(Map<BlockPos, Direction.Axis> girders,
                                                        Set<BlockPos> solidFullCubes,
                                                        Set<BlockPos> exteriorVoid,
                                                        int maxRayRange) {
        if (girders == null || girders.isEmpty()) {
            return List.of();
        }

        // Индекс «ось → её балки» — группировка по блокстейт-оси за O(1) на блок (не O(n^2)).
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

    /*
     * Луч от торца наружу: прозрачен для всего, кроме полных кубов; останавливается на первом
     * полном кубе W; торец валиден, если клетка за W (W+d) — внешняя пустота. См. TD_06 v1.0,
     * find_valid_girder_lines, шаги 3b–3c.
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
        return false; // стены в пределах дальности нет → торец ни на что не опирается
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  invalidate_hull_cache
    // ════════════════════════════════════════════════════════════════════════════

    /*
     * invalidate_hull_cache — атомарно пересчитать три кэша корпуса. Порядок, атомарность,
     * батчинг — см. TD_06 v1.0, invalidate_hull_cache.
     *
     * MC-данные (полные кубы, балки) извлекаются по разу; внешняя пустота считается один раз
     * и переиспользуется для поверхности и для проверки торцов балок.
     */
    public void invalidate_hull_cache(Object subLevel, float supportStep) {
        // MC-извлечение (по разу).
        Set<BlockPos> solidFullCubes = extractSolidFullCubes(subLevel);
        Map<BlockPos, Direction.Axis> girders = extractGirders(subLevel);

        // Чистые ядра (строгий порядок: surface → girder_lines → rib_coverage).
        Set<BlockPos> exteriorVoid    = computeExteriorVoid(solidFullCubes);
        Set<BlockPos> exteriorSurface = surfaceFromVoid(solidFullCubes, exteriorVoid);
        List<GirderLine> girderLines  = findValidGirderLines(
            girders, solidFullCubes, exteriorVoid, DEFAULT_GIRDER_RAY_MAX_RANGE);
        float ribCov = computeRibCoverage(exteriorSurface, girderLines, supportStep);

        // Атомарная запись.
        this.exteriorSurface  = exteriorSurface;
        this.validGirderLines = girderLines;
        this.ribCoverage      = ribCov;
    }

    // ── MC-адаптеры (проверяются в игре, не юнит-тестом) ────────────────────────

    /*
     * Извлечь позиции блоков с полной кубической коллизией из SubLevel.
     * TODO[API-CHECK]: перебор блоков SubLevel в ЛОКАЛЬНЫХ координатах и сравнение
     * state.getCollisionShape(...) == Shapes.block().
     */
    private static Set<BlockPos> extractSolidFullCubes(Object subLevel) {
        // TODO[API-CHECK]
        return Set.of();
    }

    /*
     * Извлечь балки create:metal_girder из SubLevel: позиция → осевой блокстейт.
     * TODO[API-CHECK]: перебор блоков SubLevel, фильтр по типу metal_girder, чтение свойства
     * axis (BlockStateProperties.AXIS) в локальных координатах. Допущение TD_06 (Проверка A):
     * metal_girder имеет осевой блокстейт и НЕполную коллизию (не попадает в solidFullCubes).
     */
    private static Map<BlockPos, Direction.Axis> extractGirders(Object subLevel) {
        // TODO[API-CHECK]
        return Map.of();
    }

    /*
    * compute_rib_coverage — доля exterior_surface, эффективно подкреплённая балками (multi-source BFS).
    * См. TD_06 v1.0, compute_rib_coverage.
    *
    * Отступление от буквы спеки: торцы балок (anchors) — это блоки-балки, НЕ блоки поверхности.
    * Они используются как источники BFS, но в "covered" считаются ТОЛЬКО блоки exterior_surface —
    * иначе доля вышла бы за пределы [0,1] (деление-то на |exterior_surface|).
    */
    public static float computeRibCoverage(Set<BlockPos> exteriorSurface,
                                        List<GirderLine> validGirderLines,
                                        float supportStep) {
        if (exteriorSurface == null || exteriorSurface.isEmpty()) {
            return 0f; // нет поверхности → 0.0 (TD_06, защита от деления на ноль)
        }
        Map<BlockPos, Float> support = new HashMap<>();
        for (BlockPos b : exteriorSurface) {
            support.put(b, 0f);
        }
        // Шаг 2: активировать оба торца каждой линии (max, не сумма).
        Deque<BlockPos> queue = new ArrayDeque<>();
        if (validGirderLines != null) {
            for (GirderLine line : validGirderLines) {
                for (BlockPos anchor : line.anchors()) {
                    Float prev = support.get(anchor);
                    if (prev == null || prev < 1f) {
                        support.put(anchor, 1f);
                        queue.add(anchor);
                    }
                }
            }
        }
        // Шаг 3: multi-source BFS, распространение только по exterior_surface.
        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            float ns = support.get(cur) - supportStep;
            if (ns <= 0f) {
                continue; // дальше поддержка иссякла — затухание (естественный ранний выход)
            }
            for (int[] d : FACE_OFFSETS) {
                BlockPos nb = new BlockPos(cur.getX() + d[0], cur.getY() + d[1], cur.getZ() + d[2]);
                if (!exteriorSurface.contains(nb)) {
                    continue; // BFS только по поверхности
                }
                Float curNb = support.get(nb);
                if (curNb == null || ns > curNb) {
                    support.put(nb, ns);
                    queue.add(nb);
                }
            }
        }
        // Шаги 4-5: covered — ТОЛЬКО блоки поверхности с support>0.
        int covered = 0;
        for (BlockPos b : exteriorSurface) {
            Float v = support.get(b);
            if (v != null && v > 0f) {
                covered++;
            }
        }
        return (float) covered / exteriorSurface.size();
    }
}