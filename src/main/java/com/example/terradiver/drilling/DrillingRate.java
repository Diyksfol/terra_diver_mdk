package com.example.terradiver.drilling;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/*
 * compute_drilling_rate (TD_02) — центральная формула: темп бурения вперёд за тик (forward_rate,
 * неотрицательный) с учётом материала короны, размера, давления и торможения «киселя».
 * Выталкивание здесь НЕ считается — оно в отдельной compute_pushback() и складывается с этим
 * темпом на уровне тик-цикла. См. TD_06 v1.0, compute_drilling_rate.
 *
 * Развязка: единственное касание мира — твёрдость блоков diggable_set. Ядро принимает уже
 * извлечённые твёрдости (float[]) и тестируется без Minecraft; обёртка с дословной сигнатурой
 * TD_06 извлекает твёрдость из BlockState (block.defaultDestroyTime(), та же конвенция, что is_diggable).
 */
public final class DrillingRate {

    private DrillingRate() {}

    /*
     * Чистое ядро. n берётся как длина массива твёрдостей. См. TD_06 v1.0, шаги 1-7.
     * resistance_multiplier = 1/(1+x) — гарантированно в (0,1] при любом неотрицательном pending,
     * без явного клампа. Кламп только сверху по RATE_MAX (нижний [0] обеспечен неотрицательностью
     * входов; двусторонний кламп net_rate — в тик-цикле, не здесь).
     */
    public static float compute_drilling_rate_core(float avgMaterialFactor, float crownFaceArea,
                                                   float pressureDebuffEffective, float[] hardnesses,
                                                   float rpmInput, float minHardness, float rateMax,
                                                   float pendingResistance, float pendingResistanceFactor) {
        int n = hardnesses == null ? 0 : hardnesses.length;
        if (n == 0) {
            return 0f; // бурить нечего; выталкивание считает compute_pushback() отдельно в тик-цикле
        }
        // weighted_average твёрдостей: весов в спеке нет → среднее по блокам сечения.
        float sum = 0f;
        for (float h : hardnesses) {
            sum += h;
        }
        float avgH = Math.max(sum / n, minHardness); // пол: мягкое (hardness~0) не делит на ноль

        float effectiveSpeed = rpmInput / 4f;
        float resistanceMultiplier = 1f / (1f + pendingResistance * pendingResistanceFactor);

        float forwardRate = (effectiveSpeed * avgMaterialFactor * pressureDebuffEffective
            * crownFaceArea * resistanceMultiplier) / (n * avgH);

        return Math.min(forwardRate, rateMax);
    }

    /*
     * Production-сигнатура (дословно TD_06 v1.0). Извлекает твёрдости из diggable_set и зовёт ядро.
     * diggable_set приходит от родного движка Offroad (updateMiningBlocks), отфильтрован is_diggable.
     */
    public static float compute_drilling_rate(float avgMaterialFactor, float crownFaceArea,
                                              float pressureDebuffEffective, List<BlockState> diggableSet,
                                              float rpmInput, float minHardness, float rateMax,
                                              float pendingResistance, float pendingResistanceFactor) {
        int n = diggableSet == null ? 0 : diggableSet.size();
        float[] hardnesses = new float[n];
        for (int i = 0; i < n; i++) {
            // Твёрдость как block.defaultDestroyTime() (та же конвенция, что is_diggable/is_bedrock_blocking).
            hardnesses[i] = diggableSet.get(i).getBlock().defaultDestroyTime();
        }
        return compute_drilling_rate_core(avgMaterialFactor, crownFaceArea, pressureDebuffEffective,
            hardnesses, rpmInput, minHardness, rateMax, pendingResistance, pendingResistanceFactor);
    }
}
