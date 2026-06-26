package com.example.terradiver.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/*
 * Поле сопротивления (TD_01, v1.2): откуда давит порода на корону и куда выталкивать.
 * См. TD_06 v1.2, compute_resistance_direction.
 *
 * Состоит из трёх частей:
 *   crownCenter / compute_resistance_direction — чистая геометрия, тестируется без Minecraft.
 *   scanContactSolids (ядро) — какие позиции вокруг короны считать контактом, чистое.
 *   scanContactSolids (адаптер) — чтение блоков из SubLevel, наш скан (декаплинг от Offroad),
 *                                 единственная MC-привязка, TODO[API-CHECK].
 */
public final class ResistanceField {

    private ResistanceField() {}

    // 6 граней — соседи, которые щупаем вокруг каждой короны на наличие породы.
    private static final int[][] FACE_OFFSETS = {
        { 1, 0, 0}, {-1, 0, 0},
        { 0, 1, 0}, { 0,-1, 0},
        { 0, 0, 1}, { 0, 0,-1},
    };

    // ── Геометрия (чистая) ──────────────────────────────────────────────────────

    /*
     * Геометрический центр короны — среднее позиций (в координатах блоков, как и contact_solids,
     * чтобы разность center − centroid была согласованной). Vec3.ZERO для пустого списка.
     */
    public static Vec3 crownCenter(List<BlockPos> crownPositions) {
        if (crownPositions == null || crownPositions.isEmpty()) {
            return Vec3.ZERO;
        }
        double sx = 0, sy = 0, sz = 0;
        for (BlockPos p : crownPositions) {
            sx += p.getX();
            sy += p.getY();
            sz += p.getZ();
        }
        int n = crownPositions.size();
        return new Vec3(sx / n, sy / n, sz / n);
    }

    /*
     * Единичное направление выталкивания: от центра породы к центру короны (наружу).
     * Vec3.ZERO, если контакта нет или порода симметрично охватывает центр.
     * См. TD_06 v1.2, compute_resistance_direction.
     */
    public static Vec3 compute_resistance_direction(Vec3 crownCenter, List<BlockPos> contactSolids) {
        if (contactSolids == null || contactSolids.isEmpty()) {
            return Vec3.ZERO;
        }
        double sx = 0, sy = 0, sz = 0;
        for (BlockPos p : contactSolids) {
            sx += p.getX();
            sy += p.getY();
            sz += p.getZ();
        }
        int n = contactSolids.size();
        Vec3 centroid = new Vec3(sx / n, sy / n, sz / n);
        // crownCenter − centroid: от породы к короне. normalize() сам вернёт ZERO при симметрии.
        return crownCenter.subtract(centroid).normalize();
    }

    // ── Скан контакта: ядро (чистое) ────────────────────────────────────────────

    /*
     * Чистое ядро скана: для каждой короны щупаем 6 соседей и оставляем те, что предикат
     * признал контактной породой. Дедуп — общий сосед двух корон учитывается один раз.
     * Предикат isContactRock абстрагирует MC-проверку (твёрдый добываемый блок, не часть штуковины).
     */
    public static List<BlockPos> scanContactSolids(List<BlockPos> crownPositions,
                                                   Predicate<BlockPos> isContactRock) {
        if (crownPositions == null || crownPositions.isEmpty()) {
            return List.of();
        }
        Set<BlockPos> seen = new LinkedHashSet<>();
        for (BlockPos crown : crownPositions) {
            for (int[] d : FACE_OFFSETS) {
                BlockPos nb = new BlockPos(crown.getX() + d[0], crown.getY() + d[1], crown.getZ() + d[2]);
                if (!seen.contains(nb) && isContactRock.test(nb)) {
                    seen.add(nb);
                }
            }
        }
        return new ArrayList<>(seen);
    }

    // ── Скан контакта: MC-адаптер (проверяется в игре) ──────────────────────────

    /*
     * Production-скан: позиции корон → щупаем породу вокруг через наш доступ к блокам SubLevel.
     * Это наш геометрический скан (не Offroad): мы сами решаем, где порода давит.
     */
    public static List<BlockPos> scanContactSolids(List<CrownBlock> crowns, Object subLevel) {
        if (crowns == null || crowns.isEmpty()) {
            return List.of();
        }
        List<BlockPos> positions = crowns.stream().map(CrownBlock::getPosition).collect(Collectors.toList());
        return scanContactSolids(positions, pos -> isContactRock(subLevel, pos));
    }

    /*
     * Контактная порода в позиции: твёрдый добываемый блок, не часть самой штуковины.
     * TODO[API-CHECK]: чтение блока из SubLevel (доступ к мировым блокам не подтверждён, TD_06).
     * Псевдокод: state = subLevel.getBlockState(pos); return state.getCollisionShape(...)==Shapes.block()
     *            && PhysicsUtils.is_diggable(state) && !partOfContraption(pos).
     */
    private static boolean isContactRock(Object subLevel, BlockPos pos) {
        // TODO[API-CHECK]
        return false;
    }
}