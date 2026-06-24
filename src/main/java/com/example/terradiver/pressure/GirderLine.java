package com.example.terradiver.pressure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;
import java.util.Objects;

/**
 * Валидная линия балки жёсткости (TD_03, {@code find_valid_girder_lines}).
 *
 * <p>Линия — непрерывная прямая цепочка блоков {@code create:metal_girder} вдоль одной оси,
 * оба торца которой упираются (перпендикулярно) во внешнюю стену корпуса. Работает как
 * распорка против давления, направленного поперёк её оси. Спецификация — TD_06 v1.0.
 *
 * <p><b>Это намеренно тонкий носитель данных.</b> Единственный потребитель,
 * {@code compute_rib_coverage}, читает из линии только два торца (источники multi-source BFS);
 * ось — метка формы возврата по TD_06 (пригодится отладочному рендеру/Ponder). Логики, которую
 * стоило бы держать здесь, у линии нет — поэтому {@code record}. Единственное, что тип делает
 * сам, — <b>не даёт себе быть невалидным</b>: компактный конструктор проверяет инвариант, чтобы
 * баг в {@code find_valid_girder_lines} падал сразу, а не молча искажал {@code rib_coverage}.
 *
 * <p><b>Инвариант:</b> {@code anchorA} и {@code anchorB} коллинеарны вдоль {@code axis}
 * (совпадают по двум другим координатам) и {@code anchorA} строго раньше {@code anchorB}
 * по этой оси (длина цепочки ≥ 2; торец A — минимальный конец, B — максимальный).
 *
 * @param axis    ось цепочки (X/Y/Z)
 * @param anchorA торцевой блок-балка с меньшей координатой вдоль оси
 * @param anchorB торцевой блок-балка с большей координатой вдоль оси
 */
public record GirderLine(Direction.Axis axis, BlockPos anchorA, BlockPos anchorB) {

    public GirderLine {
        Objects.requireNonNull(axis, "axis");
        Objects.requireNonNull(anchorA, "anchorA");
        Objects.requireNonNull(anchorB, "anchorB");

        // On-axis координаты торцов и проверка совпадения по двум off-axis координатам.
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

    /**
     * Оба торца как источники multi-source BFS в {@code compute_rib_coverage} (шаг 2).
     * Удобно для {@code lines.stream().flatMap(l -> l.anchors().stream())}.
     */
    public List<BlockPos> anchors() {
        return List.of(anchorA, anchorB);
    }
}