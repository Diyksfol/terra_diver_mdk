package com.example.terradiver.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Блок-корона на подшипнике буровой штуковины (диск/режущее колесо).
 * Позиция, грань (направление реза), материал, площадь, глубина вдоль heading.
 * Спецификация — TD_06 v1.0 / TD_02.
 */
public class CrownBlock {

    private final BlockPos position;
    /** Грань, которой корона "режет" — определяет выравнивание с heading. */
    private final Direction face;
    /** Фактор материала [1.0 медь … 3.0 незерит]. Для compute_avg_material_factor. */
    private final float materialFactor;
    /** Площадь вклада, блоки² (обычно 1.0 для кубического блока). */
    private final float area;
    /** Глубина от переднего края вдоль heading; "передний слой" = в пределах ~1 блока минимума. */
    private final float depthAlongHeading;
    /** Ссылка на родительский подшипник (тип зависит от Offroad; для проверки согласованности вращения). */
    private final Object bearingReference;

    public CrownBlock(BlockPos position, Direction face, float materialFactor,
                      float area, float depthAlongHeading, Object bearingReference) {
        this.position = position;
        this.face = face;
        this.materialFactor = materialFactor;
        this.area = area;
        this.depthAlongHeading = depthAlongHeading;
        this.bearingReference = bearingReference;
    }

    public BlockPos getPosition()        { return position; }
    public Direction getFace()           { return face; }
    public float getMaterialFactor()     { return materialFactor; }
    public float getArea()               { return area; }
    public float getDepthAlongHeading()  { return depthAlongHeading; }
    public Object getBearingReference()  { return bearingReference; }

    /** Грань короны как единичный вектор. */
    public Vec3 getFaceVector() {
        return new Vec3(face.getStepX(), face.getStepY(), face.getStepZ()).normalize();
    }

    /**
     * Выровнена ли корона с heading. Порог dot > 0.7 (~45° допуск) — учитывает
     * дискретность направлений и погрешность float. См. get_aligned_crowns (TD_06).
     */
    public boolean isAlignedWithHeading(Vec3 heading) {
        if (heading == null) {
            return false;
        }
        final double ALIGNMENT_THRESHOLD = 0.7;
        return getFaceVector().dot(heading) > ALIGNMENT_THRESHOLD;
    }

    // ПРИМЕЧАНИЕ: чтение скорости подшипника НЕ здесь.
    // Перенесено в PhysicsUtils.readBearingSpeed(bearing) — getSpeed() это
    // подтверждённый API, вызывается напрямую, без рефлексии и без fake-CrownBlock.

    @Override
    public String toString() {
        return String.format("CrownBlock{pos=%s, face=%s, material=%.1f, area=%.1f, depth=%.1f}",
            position, face.getSerializedName(), materialFactor, area, depthAlongHeading);
    }
}
