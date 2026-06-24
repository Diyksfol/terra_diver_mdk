package com.example.terradiver.pressure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.*;

/**
 * Кэш трёх взаимосвязанных характеристик корпуса штуковины (TD_03):
 * {@code exterior_surface}, {@code valid_girder_lines}, {@code rib_coverage}.
 *
 * <p>Единственный потребитель инвалидации — {@link #invalidate_hull_cache}.
 * Спецификация — TD_06 v1.0, TD_03.
 */
public class HullCache {

    private volatile Set<BlockPos> exteriorSurface = Set.of();
    private volatile List<Object>  validGirderLines = List.of(); // TODO: заменить Object на GirderLine
    private volatile float         ribCoverage = 0f;

    public Set<BlockPos> getExteriorSurface()  { return exteriorSurface; }
    public List<Object>  getValidGirderLines() { return validGirderLines; }
    public float         getRibCoverage()      { return ribCoverage; }

    // ── Провайдеры (изолируют неподтверждённый API и MC-зависимости) ─────────────

    /**
     * Читает блок по локальной позиции SubLevel.
     * Возвращает {@code null} если позиция пустая (воздух/вне структуры).
     *
     * <p>Возвращает {@code Object}, а не {@code BlockState}, чтобы тесты могли
     * передавать произвольные маркеры без MC Bootstrap (BlockState — final class,
     * нельзя замокировать без mockito-inline).
     * Production-реализация ({@link #DEFAULT_BLOCK_READER}) кастит результат к BlockState.
     *
     * <p>TODO[API-CHECK]: подтвердить реальный API SubLevel для чтения блоков.
     */
    @FunctionalInterface
    public interface ISubLevelBlockReader {
        Object readLocalBlock(Object subLevel, BlockPos localPos);
    }

    /**
     * Возвращает {min, max} bounding box SubLevel в локальных координатах.
     * TODO[API-CHECK]: подтвердить реальный API SubLevel.
     */
    @FunctionalInterface
    public interface ISubLevelBoundsProvider {
        BlockPos[] getBounds(Object subLevel);
    }

    /**
     * Определяет, имеет ли блок полную коллизию (≈ Shapes.block()).
     *
     * <p>Принимает {@code Object} (а не {@code BlockState}) по той же причине, что
     * {@link ISubLevelBlockReader}: тесты передают маркеры без MC Bootstrap.
     * Production-реализация ({@link #DEFAULT_COLLISION_CHECKER}) кастит к BlockState
     * и вызывает {@code getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)}.
     *
     * <p>Спецификация TD_06: неполные блоки (двери, лестницы, люки) исключаются
     * из exterior_surface — их CollisionShape ≠ Shapes.block().
     */
    @FunctionalInterface
    public interface IFullCollisionChecker {
        boolean isFullCollision(Object blockState);
    }

    // ── Production-реализации ─────────────────────────────────────────────────────

    /** Production-читалка блоков SubLevel. TODO[API-CHECK]. */
    private static final ISubLevelBlockReader DEFAULT_BLOCK_READER =
        (subLevel, localPos) -> null;

    /** Production-bounds SubLevel. TODO[API-CHECK]. */
    private static final ISubLevelBoundsProvider DEFAULT_BOUNDS_PROVIDER =
        subLevel -> new BlockPos[]{ BlockPos.ZERO, BlockPos.ZERO };

    /**
     * Production-проверка полной коллизии через MC API.
     *
     * {@code EmptyBlockGetter.INSTANCE} — стандартный null-safe getter MC 1.21,
     * не требует реального Level. Результат сравниваем с {@code Shapes.block()}.
     *
     * TODO[VERIFY]: протестировать на реальных Create-блоках (подшипник, балка, корона).
     */
    static final IFullCollisionChecker DEFAULT_COLLISION_CHECKER = blockState -> {
        if (!(blockState instanceof BlockState state)) return false;
        if (state.isAir()) return false;
        return Shapes.block().equals(
            state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
        );
    };

    // ── invalidate_hull_cache ─────────────────────────────────────────────────────

    /**
     * Атомарно пересчитывает все три кэша корпуса.
     * Порядок строгий: exterior_surface → girder_lines → rib_coverage.
     * Батчинг: один вызов на всю операцию сборки/разборки SubLevel.
     */
    public void invalidate_hull_cache(Object subLevel, float supportStep) {
        invalidate_hull_cache(subLevel, supportStep,
            DEFAULT_BLOCK_READER, DEFAULT_BOUNDS_PROVIDER, DEFAULT_COLLISION_CHECKER);
    }

    /** Перегрузка с явными провайдерами — для тестов и подключения реального API. */
    void invalidate_hull_cache(Object subLevel, float supportStep,
                               ISubLevelBlockReader blockReader,
                               ISubLevelBoundsProvider boundsProvider,
                               IFullCollisionChecker collisionChecker) {

        Set<BlockPos> newExteriorSurface =
            computeExteriorSurface(subLevel, blockReader, boundsProvider, collisionChecker);

        List<Object> newGirderLines =
            findValidGirderLines(subLevel, newExteriorSurface);

        float newRibCoverage =
            computeRibCoverage(newExteriorSurface, newGirderLines, supportStep);

        // Атомарная запись
        this.exteriorSurface  = newExteriorSurface;
        this.validGirderLines = newGirderLines;
        this.ribCoverage      = newRibCoverage;
    }

    // ── compute_exterior_surface ──────────────────────────────────────────────────

    /**
     * Определяет множество блоков внешней поверхности корпуса SubLevel.
     *
     * <p><b>Алгоритм (TD_06 v1.0, TD_03):</b>
     * <ol>
     *   <li>Flood-fill от границы bounding box (запас 1 блок) по воздуху/null в
     *       локальных координатах SubLevel → «внешняя пустота» (externalAir).</li>
     *   <li>Блок входит в exterior_surface если: полная коллизия AND хотя бы один
     *       из 6 соседей ∈ externalAir.</li>
     * </ol>
     *
     * <p><b>Локальные координаты обязательны:</b> flood-fill по мировым координатам
     * под землёй даст пустой exterior_surface именно тогда, когда давление должно работать.
     *
     * <p>Внутренние полости не достигаются flood-fill снаружи — не входят в surface.
     */
    static Set<BlockPos> computeExteriorSurface(Object subLevel,
                                                ISubLevelBlockReader blockReader,
                                                ISubLevelBoundsProvider boundsProvider,
                                                IFullCollisionChecker collisionChecker) {

        // Шаг 0: bounding box
        BlockPos[] bounds = boundsProvider.getBounds(subLevel);
        if (bounds == null || bounds.length < 2 || bounds[0] == null || bounds[1] == null) {
            return Set.of();
        }
        BlockPos bbMin = bounds[0];
        BlockPos bbMax = bounds[1];
        if (bbMin.getX() > bbMax.getX() ||
            bbMin.getY() > bbMax.getY() ||
            bbMin.getZ() > bbMax.getZ()) {
            return Set.of();
        }

        // Шаг 1: flood-fill внешней пустоты
        final int xMin = bbMin.getX() - 1, xMax = bbMax.getX() + 1;
        final int yMin = bbMin.getY() - 1, yMax = bbMax.getY() + 1;
        final int zMin = bbMin.getZ() - 1, zMax = bbMax.getZ() + 1;

        final int[] DX = {1, -1, 0, 0, 0, 0};
        final int[] DY = {0, 0, 1, -1, 0, 0};
        final int[] DZ = {0, 0, 0, 0, 1, -1};

        Set<BlockPos> externalAir = new HashSet<>();
        Deque<BlockPos> queue     = new ArrayDeque<>();

        // Seed: граничные грани расширенного box — гарантированно снаружи
        for (int x = xMin; x <= xMax; x++) {
            for (int y = yMin; y <= yMax; y++) {
                for (int z = zMin; z <= zMax; z++) {
                    if (x != xMin && x != xMax &&
                        y != yMin && y != yMax &&
                        z != zMin && z != zMax) continue;
                    BlockPos p  = new BlockPos(x, y, z);
                    Object block = blockReader.readLocalBlock(subLevel, p);
                    if (isAirOrAbsent(block, collisionChecker) && externalAir.add(p)) {
                        queue.add(p);
                    }
                }
            }
        }

        // BFS
        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            int cx = cur.getX(), cy = cur.getY(), cz = cur.getZ();
            for (int d = 0; d < 6; d++) {
                int nx = cx + DX[d], ny = cy + DY[d], nz = cz + DZ[d];
                if (nx < xMin || nx > xMax ||
                    ny < yMin || ny > yMax ||
                    nz < zMin || nz > zMax) continue;
                BlockPos next = new BlockPos(nx, ny, nz);
                if (externalAir.contains(next)) continue;
                Object block = blockReader.readLocalBlock(subLevel, next);
                if (isAirOrAbsent(block, collisionChecker) && externalAir.add(next)) {
                    queue.add(next);
                }
            }
        }

        // Шаг 2: блоки корпуса, граничащие с externalAir
        Set<BlockPos> surface = new HashSet<>();
        for (int x = bbMin.getX(); x <= bbMax.getX(); x++) {
            for (int y = bbMin.getY(); y <= bbMax.getY(); y++) {
                for (int z = bbMin.getZ(); z <= bbMax.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Object block = blockReader.readLocalBlock(subLevel, pos);
                    if (!collisionChecker.isFullCollision(block)) continue;
                    for (int d = 0; d < 6; d++) {
                        if (externalAir.contains(new BlockPos(x+DX[d], y+DY[d], z+DZ[d]))) {
                            surface.add(pos);
                            break;
                        }
                    }
                }
            }
        }

        return Collections.unmodifiableSet(surface);
    }

    // ── Вспомогательный предикат ──────────────────────────────────────────────────

    /**
     * Клетка пустая — flood-fill проходит сквозь.
     * Null → вне SubLevel (пустота). Блок без полной коллизии → тоже проходим.
     * Логика: если не полный блок, то пустота для flood-fill.
     */
    private static boolean isAirOrAbsent(Object block, IFullCollisionChecker collisionChecker) {
        return block == null || !collisionChecker.isFullCollision(block);
    }

    // ── Заглушки (реализуются в рамках TD_03) ────────────────────────────────────

    private static List<Object> findValidGirderLines(Object subLevel, Set<BlockPos> exteriorSurface) {
        return List.of(); // TODO[IMPL]
    }

    private static float computeRibCoverage(Set<BlockPos> exteriorSurface,
                                             List<Object>  validGirderLines,
                                             float         supportStep) {
        return 0f; // TODO[IMPL]
    }
}