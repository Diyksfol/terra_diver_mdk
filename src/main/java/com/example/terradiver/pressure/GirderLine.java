package com.example.terradiver.pressure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;
import java.util.Objects;

/*
 * Валидная линия балки жёсткости. Спецификация — TD_06 v1.0, find_valid_girder_lines.
 *
 * Намеренно тонкий носитель данных: единственный потребитель (compute_rib_coverage)
 * читает только два торца как источники multi-source BFS; ось — метка формы возврата
 * по TD_06 (пригодится отладочному рендеру/Ponder). Логики держать здесь нечего, поэтому record.
 *
 * Единственное, что тип делает сам — не даёт себе быть невалидным: конструктор проверяет
 * инвариант, чтобы баг в find_valid_girder_lines падал сразу, а не молча искажал rib_coverage.
 * Инвариант: anchorA и anchorB коллинеарны вдоль axis (совпадают по двум другим координатам)
 * и anchorA строго раньше anchorB по этой оси (длина цепочки >= 2; A — минимальный конец, B — максимальный).
 */
public record GirderLine(Direction.Axis axis, BlockPos anchorA, BlockPos anchorB) {

    public GirderLine {
        Objects.requireNonNull(axis, "axis");
        Objects.requireNonNull(anchorA, "anchorA");
        Objects.requireNonNull(anchorB, "anchorB");

        // On-axis координаты торцов и совпадение по двум off-axis координатам.
        int onA, onB;
        boolean offAxisAligned;
        switch (axis) {
            case X -> {
                onA = anchorA.getX(); onB = anchorB.getX();
                offAxisAligned = anchorA.getY() == anchorB.getY() && anchorA.getZ() == anchorB.getZ();
            }
            case Y -> {
                onA = anchorA.getY(); onB = anchorB.getY();
                offAxisAligned = anchorA.getX() == anchorB.getX() && anchorA.getZ() == anchorB.getZ();
            }
            case Z -> {
                onA = anchorA.getZ(); onB = anchorB.getZ();
                offAxisAligned = anchorA.getX() == anchorB.getX() && anchorA.getY() == anchorB.getY();
            }
            default -> throw new IllegalStateException("unreachable axis: " + axis);
        }
        if (!offAxisAligned) {
            throw new IllegalArgumentException(
                "GirderLine anchors not collinear along " + axis + ": " + anchorA + " .. " + anchorB);
        }
        if (onA >= onB) {
            throw new IllegalArgumentException(
                "GirderLine anchorA must precede anchorB along " + axis
                    + " (chain length >= 2): " + anchorA + " .. " + anchorB);
        }
    }

    // Оба торца как источники multi-source BFS в compute_rib_coverage (шаг 2).
    // Удобно для lines.stream().flatMap(l -> l.anchors().stream()).
    public List<BlockPos> anchors() {
        return List.of(anchorA, anchorB);
    }
}