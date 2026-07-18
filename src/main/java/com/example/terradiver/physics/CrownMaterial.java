package com.example.terradiver.physics;

import com.example.terradiver.config.ModConfig;

/*
 * Материал буровой короны. Задаёт множитель темпа грызни (tier): медь — базовый, дальше быстрее.
 * Само число живёт в конфиге (ModConfig.CROWN_MATERIAL_FACTOR_*), здесь только связка материал->значение,
 * чтобы балансить не трогая код. Раньше материал был зашит лишь в ИМЯ блока и нигде не читался —
 * отсюда «множители в конфиге есть, а связать не с чем». Теперь короны несут его как поле.
 */
public enum CrownMaterial {
    COPPER,
    IRON,
    BRASS,
    NETHERITE;

    // Множитель темпа грызни этого материала. Читаем из конфига каждый раз — балансовые правки
    // подхватываются без переустановки блоков. Значения по умолчанию: 1.0 / 1.5 / 2.0 / 3.0.
    public double factor() {
        return switch (this) {
            case COPPER -> ModConfig.CROWN_MATERIAL_FACTOR_COPPER.get();
            case IRON -> ModConfig.CROWN_MATERIAL_FACTOR_IRON.get();
            case BRASS -> ModConfig.CROWN_MATERIAL_FACTOR_BRASS.get();
            case NETHERITE -> ModConfig.CROWN_MATERIAL_FACTOR_NETHERITE.get();
        };
    }
}
