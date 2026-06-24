package com.example.terradiver.pressure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Кэш трёх взаимосвязанных характеристик корпуса штуковины (TD_03):
 * {@code exterior_surface}, {@code valid_girder_lines}, {@code rib_coverage}.
 *
 * <p>Единственный потребитель инвалидации — {@link #invalidate_hull_cache}.
 * Читатели ({@code compute_pressure_debuff_effective}, {@code compute_ambient_signal_volume},
 * {@code compute_pressure_gauge_needles}) обращаются к геттерам, не пересчитывая.
 * Спецификация — TD_06 v1.0, TD_03.
 */
public class HullCache {

    // Три кэша обновляются только атомарно через invalidate_hull_cache().
    // volatile — видимость между тиками (физический тик и потенциальные читатели).
    private volatile Set<BlockPos> exteriorSurface = Set.of();
    private volatile List<Object>  validGirderLines = List.of(); // TODO: заменить Object на GirderLine
    private volatile float         ribCoverage = 0f;

    /** Внешняя поверхность корпуса (локальные координаты SubLevel). */
    public Set<BlockPos> getExteriorSurface()  { return exteriorSurface; }

    /** Валидные линии балок жёсткости. */
    public List<Object>  getValidGirderLines() { return validGirderLines; }

    /** Доля внешней поверхности, покрытая балками жёсткости, [0.0, 1.0]. */
    public float         getRibCoverage()      { return ribCoverage; }

    // ── Интерфейс чтения SubLevel ────────────────────────────────────────────────

    /**
     * Читает структуру SubLevel в локальных координатах.
     * Изолирует неподтверждённый API SubLevel — вся неопределённость здесь.
     * TODO[API-CHECK]: подтвердить точный API для getBounds() и isFullBlock() с реальным SubLevel.
     */
    interface ISubLevelReader {

        /**
         * Bounding box SubLevel в локальных координатах.
         * null — если SubLevel пуст или тип неизвестен.
         * TODO[API-CHECK]: подтвердить метод получения bbox у реального SubLevel.
         */
        AABB getBounds(Object subLevel);

        /**
         * Содержит ли {@code localPos} блок с полной коллизией (hull-блок).
         * false — для воздуха, отсутствующих блоков и неполных коллизий (двери, лестницы).
         *
         * <p>Спецификация: {@code block.getCollisionShape() == Shapes.block()} (TD_06, TD_03).
         * TODO[API-CHECK]: getCollisionShape() требует BlockGetter + BlockPos;
         * подтвердить безопасный вызов из контекста SubLevel в локальных координатах.
         */
        boolean isFullBlock(Object subLevel, BlockPos localPos);
    }

    /** Production-читалка SubLevel (placeholder до подтверждения API). */
    private static final ISubLevelReader DEFAULT_SUB_LEVEL_READER = new ISubLevelReader() {
        @Override
        public AABB getBounds(Object subLevel) {
            // TODO[API-CHECK]: вернуть реальный bbox SubLevel в локальных координатах
            return null; // null → computeExteriorSurface вернёт Set.of() без NPE
        }

        @Override
        public boolean isFullBlock(Object subLevel, BlockPos localPos) {
            // TODO[API-CHECK]: subLevel.getBlockState(localPos).getCollisionShape(...) == Shapes.block()
            return false; // placeholder — все позиции считаются воздухом
        }
    };

    // ── 6 направлений для BFS ────────────────────────────────────────────────────

    private static final int[] DX = {1, -1, 0,  0,  0, 0};
    private static final int[] DY = {0,  0, 1, -1,  0, 0};
    private static final int[] DZ = {0,  0, 0,  0,  1, -1};

    // ── compute_exterior_surface ─────────────────────────────────────────────────

    /**
     * compute_exterior_surface() — production-версия.
     * Спецификация: TD_06 v1.0, TD_03.
     */
    static Set<BlockPos> computeExteriorSurface(Object subLevel) {
        return computeExteriorSurface(subLevel, DEFAULT_SUB_LEVEL_READER);
    }

    /**
     * compute_exterior_surface() — overload с явным {@code reader} для тестов.
     *
     * <p><b>Алгоритм (два шага):</b>
     * <ol>
     *   <li><b>Flood-fill внешней пустоты:</b> BFS стартует со всех клеток внешней оболочки
     *       расширенного (+1 блок) bounding box, распространяется только через клетки без
     *       полной коллизии. Результат — множество клеток {@code exteriorVoid}.
     *   <li><b>Граничные hull-блоки:</b> каждый блок с полной коллизией, у которого
     *       хотя бы один из 6 соседей входит в {@code exteriorVoid}, — на внешней поверхности.
     * </ol>
     *
     * <p><b>Критично — локальные координаты:</b> flood-fill работает в системе координат
     * SubLevel, а не мира. Flood-fill в мировых координатах схлопнул бы surface в пустое
     * множество под землёй (Dive Mode) — ровно когда механика давления и должна работать
     * (TD_06, граничные случаи). Это критическая ошибка реализации.
     *
     * <p><b>Внутренние полости</b> (кабина, отсеки) flood-fill снаружи не достигает:
     * они отделены hull-блоками. Их стены в {@code exterior_surface} не попадают.
     *
     * @param subLevel контекст SubLevel штуковины
     * @param reader   читалка SubLevel (инъекция для тестов)
     * @return неизменяемое множество позиций hull-блоков на внешней поверхности
     */
    static Set<BlockPos> computeExteriorSurface(Object subLevel, ISubLevelReader reader) {
        AABB bounds = reader.getBounds(subLevel);
        if (bounds == null) {
            return Set.of(); // SubLevel пуст или API не подтверждён — возвращаем пустое
        }

        // Расширенный bbox: +1 блок во все стороны.
        // Гарантирует, что BFS-семена окружают всю структуру снаружи,
        // а не упираются вплотную в её границу.
        int minX = (int) Math.floor(bounds.minX) - 1;
        int minY = (int) Math.floor(bounds.minY) - 1;
        int minZ = (int) Math.floor(bounds.minZ) - 1;
        int maxX = (int) Math.ceil(bounds.maxX)  + 1;
        int maxY = (int) Math.ceil(bounds.maxY)  + 1;
        int maxZ = (int) Math.ceil(bounds.maxZ)  + 1;

        // ── Шаг 1: flood-fill внешней пустоты ────────────────────────────────

        // Семена — все клетки на 6 гранях расширенного bbox.
        // BFS не выходит за его пределы — ограничение объёма обхода.
        Set<BlockPos> exteriorVoid = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (x == minX || x == maxX ||
                        y == minY || y == maxY ||
                        z == minZ || z == maxZ) {
                        BlockPos seed = new BlockPos(x, y, z);
                        if (exteriorVoid.add(seed)) {
                            queue.add(seed);
                        }
                    }
                }
            }
        }

        // BFS: распространяемся через пустые клетки (не hull-блоки).
        // Hull-блоки останавливают flood-fill — их соседи за ними не достигаются
        // (именно это защищает внутренние полости от попадания в exterior_surface).
        while (!queue.isEmpty()) {
            BlockPos curr = queue.poll();

            for (int i = 0; i < 6; i++) {
                int nx = curr.getX() + DX[i];
                int ny = curr.getY() + DY[i];
                int nz = curr.getZ() + DZ[i];

                // Не выходить за расширенный bbox
                if (nx < minX || nx > maxX ||
                    ny < minY || ny > maxY ||
                    nz < minZ || nz > maxZ) {
                    continue;
                }

                BlockPos neighbor = new BlockPos(nx, ny, nz);
                if (exteriorVoid.contains(neighbor)) {
                    continue; // уже посещена
                }

                // Hull-блок (полная коллизия) → граница flood-fill; не добавлять.
                // Неполные блоки (двери, лестницы) → пропускаем, как воздух (TD_06: исключены).
                if (reader.isFullBlock(subLevel, neighbor)) {
                    continue;
                }

                exteriorVoid.add(neighbor);
                queue.add(neighbor);
            }
        }

        // ── Шаг 2: hull-блоки, граничащие с внешней пустотой ─────────────────

        // Перебираем весь расширенный bbox: позиции вне SubLevel вернут isFullBlock=false,
        // поэтому сразу отфильтруются — отдельный inner-диапазон не нужен.
        Set<BlockPos> result = new HashSet<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);

                    if (!reader.isFullBlock(subLevel, pos)) {
                        continue; // не hull-блок — воздух, дверь, лестница и т.д.
                    }

                    // Хотя бы один сосед в exteriorVoid → блок на внешней поверхности
                    for (int i = 0; i < 6; i++) {
                        BlockPos neighbor = new BlockPos(
                                x + DX[i], y + DY[i], z + DZ[i]);
                        if (exteriorVoid.contains(neighbor)) {
                            result.add(pos);
                            break;
                        }
                    }
                }
            }
        }

        return Collections.unmodifiableSet(result);
    }

    // ── invalidate_hull_cache ────────────────────────────────────────────────────

    /**
     * invalidate_hull_cache() — production-версия.
     * Атомарно пересчитывает все три кэша корпуса. Спецификация: TD_06 v1.0, TD_03.
     *
     * <p><b>Порядок строгий:</b> exterior_surface → girder_lines → rib_coverage.
     * <b>Атомарность:</b> все три поля обновляются вместе в конце метода.
     *
     * @param subLevel    штуковина, чей корпус изменился
     * @param supportStep константа затухания поддержки балок (TerrraDiverConfig.SUPPORT_STEP)
     */
    public void invalidate_hull_cache(Object subLevel, float supportStep) {
        invalidate_hull_cache(subLevel, supportStep, DEFAULT_SUB_LEVEL_READER);
    }

    /**
     * invalidate_hull_cache() — overload с явным {@code reader} для тестов.
     *
     * @param subLevel    штуковина, чей корпус изменился
     * @param supportStep константа затухания поддержки балок (TerrraDiverConfig.SUPPORT_STEP)
     * @param reader      читалка SubLevel (инъекция для тестов)
     */
    void invalidate_hull_cache(Object subLevel, float supportStep, ISubLevelReader reader) {
        // Порядок топологический и не переставляемый: каждый шаг читает результат предыдущего.
        Set<BlockPos> newExteriorSurface = computeExteriorSurface(subLevel, reader);
        List<Object>  newGirderLines     = findValidGirderLines(subLevel, newExteriorSurface);
        float         newRibCoverage     = computeRibCoverage(newExteriorSurface, newGirderLines, supportStep);

        // Атомарная запись: читатели никогда не видят смесь «новый surface + старый coverage».
        this.exteriorSurface  = newExteriorSurface;
        this.validGirderLines = newGirderLines;
        this.ribCoverage      = newRibCoverage;
    }

    // ── Заглушки зависимых функций (реализуются в рамках TD_03) ──────────────────

    /**
     * find_valid_girder_lines() — поиск валидных линий балок жёсткости.
     * TODO[IMPL]: реализовать в рамках TD_03 (после compute_exterior_surface).
     * TODO: заменить {@code Object} на тип GirderLine после его определения.
     */
    private static List<Object> findValidGirderLines(Object subLevel,
                                                      Set<BlockPos> exteriorSurface) {
        // placeholder
        return List.of();
    }

    /**
     * compute_rib_coverage() — доля exterior_surface, покрытая балками (multi-source BFS).
     * TODO[IMPL]: реализовать в рамках TD_03 (после find_valid_girder_lines).
     * Граничный случай: exteriorSurface пуст → 0.0, не делить на ноль.
     */
    private static float computeRibCoverage(Set<BlockPos> exteriorSurface,
                                             List<Object>  validGirderLines,
                                             float         supportStep) {
        if (exteriorSurface.isEmpty()) {
            return 0f; // TD_06: открытая конструкция — деление на ноль запрещено
        }
        // placeholder
        return 0f;
    }
}