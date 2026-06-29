package com.example.terradiver.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import com.example.terradiver.physics.TickCycle;

public class ModConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // ==================== WORLD GEOMETRY & PRESSURE ====================
    public static final ModConfigSpec.IntValue DEEPSLATE_Y;
    public static final ModConfigSpec.IntValue BEDROCK_Y;
    public static final ModConfigSpec.DoubleValue PRESSURE_CURVE_K;
    public static final ModConfigSpec.DoubleValue PRESSURE_SCALE;
    public static final ModConfigSpec.DoubleValue SUPPORT_STEP;
    public static final ModConfigSpec.DoubleValue SOUND_DAMPING;
    public static final ModConfigSpec.DoubleValue MAX_COMPENSATION;

    // ==================== DRILL SYSTEM ====================
    public static final ModConfigSpec.DoubleValue CROWN_MATERIAL_FACTOR_COPPER;
    public static final ModConfigSpec.DoubleValue CROWN_MATERIAL_FACTOR_IRON;
    public static final ModConfigSpec.DoubleValue CROWN_MATERIAL_FACTOR_BRASS;
    public static final ModConfigSpec.DoubleValue CROWN_MATERIAL_FACTOR_NETHERITE;
    public static final ModConfigSpec.DoubleValue MIN_HARDNESS;
    public static final ModConfigSpec.DoubleValue RATE_MAX;

    // ==================== MODES & MOVEMENT ====================
    public static final ModConfigSpec.DoubleValue DIVE_TRIGGER_SPEED;
    public static final ModConfigSpec.DoubleValue DIVE_HOLD_SPEED;
    public static final ModConfigSpec.DoubleValue CLEARANCE_MARGIN;

    // ==================== PRESSURE JELLY & PUSHBACK ====================
    public static final ModConfigSpec.DoubleValue PENDING_MAX;
    public static final ModConfigSpec.DoubleValue PENDING_GROWTH;
    public static final ModConfigSpec.DoubleValue PENDING_DECAY;
    public static final ModConfigSpec.DoubleValue PENDING_RESISTANCE_FACTOR;
    public static final ModConfigSpec.DoubleValue PUSHBACK_THRESHOLD;
    public static final ModConfigSpec.DoubleValue PUSHBACK_FACTOR;
    public static final ModConfigSpec.IntValue MIN_BLOCKS_PER_TICK;
    public static final ModConfigSpec.DoubleValue RATE_SMOOTHING;
    public static final ModConfigSpec.DoubleValue PUSHBACK_SMOOTHING;

    // ==================== NAVIGATION & INSTRUMENTS ====================
    public static final ModConfigSpec.LongValue SAVED_DATA_TTL_TICKS;
    public static final ModConfigSpec.DoubleValue MAP_SCALE;
    public static final ModConfigSpec.DoubleValue MAP_HEIGHT_SCALE;
    public static final ModConfigSpec.IntValue TERRADAR_SU_CONSUMPTION;
    public static final ModConfigSpec.IntValue TERRADAR_PULSE_INTERVAL;

    // ==================== CAPACITIES ====================
    public static final ModConfigSpec.IntValue BEARING_BUFFER_ANDESITE;
    public static final ModConfigSpec.IntValue BEARING_BUFFER_STURDY;

    static {
        // World Geometry & Pressure
        BUILDER.push("world_geometry_pressure");
        BUILDER.comment("World Geometry & Pressure Configuration");
        DEEPSLATE_Y = BUILDER
                .comment("Y level where pressure depth calculation starts (DEEPSLATE in Minecraft 1.21.1)")
                .defineInRange("deepslate_y", 8, -64, 256);
        BEDROCK_Y = BUILDER
                .comment("Bedrock bottom level (world bottom)")
                .defineInRange("bedrock_y", -64, -64, -10);
        PRESSURE_CURVE_K = BUILDER
                .comment("Pressure curve logarithmic coefficient. Derived from 3-point fit: Y=8→1.0, Y=-15→0.5, Y=-60→0.05")
                .defineInRange("pressure_curve_k", 0.653, 0.0, 1.0);
        PRESSURE_SCALE = BUILDER
                .comment("Pressure curve depth scale (units)")
                .defineInRange("pressure_scale", 20.0, 1.0, 100.0);
        SUPPORT_STEP = BUILDER
                .comment("Support decay step in BFS for edge support (per tick)")
                .defineInRange("support_step", 0.22, 0.0, 1.0);
        SOUND_DAMPING = BUILDER
                .comment("Ambient signal attenuation multiplier")
                .defineInRange("sound_damping", 0.5, 0.0, 1.0);
        MAX_COMPENSATION = BUILDER
                .comment("Pressure compensation ceiling by edges (additive, clamped [0.1, 1.0])")
                .defineInRange("max_compensation", 0.5, 0.0, 1.0);
        BUILDER.pop();

        // Drill System
        BUILDER.push("drill_system");
        BUILDER.comment("Drill System Configuration");
        CROWN_MATERIAL_FACTOR_COPPER = BUILDER
                .comment("Copper drill crown material factor (tier 1)")
                .defineInRange("crown_material_factor_copper", 1.0, 0.1, 10.0);
        CROWN_MATERIAL_FACTOR_IRON = BUILDER
                .comment("Iron drill crown material factor (tier 2)")
                .defineInRange("crown_material_factor_iron", 1.5, 0.1, 10.0);
        CROWN_MATERIAL_FACTOR_BRASS = BUILDER
                .comment("Brass drill crown material factor (tier 3)")
                .defineInRange("crown_material_factor_brass", 2.0, 0.1, 10.0);
        CROWN_MATERIAL_FACTOR_NETHERITE = BUILDER
                .comment("Netherite drill crown material factor (tier 4)")
                .defineInRange("crown_material_factor_netherite", 3.0, 0.1, 10.0);
        MIN_HARDNESS = BUILDER
                .comment("Minimum average hardness threshold for mining")
                .defineInRange("min_hardness", 0.15, 0.0, 1.0);
        RATE_MAX = BUILDER
                .comment("Maximum drilling rate (blocks/tick), used as [-RATE_MAX, RATE_MAX]")
                .defineInRange("rate_max", 1.0, 0.1, 10.0);
        BUILDER.pop();

        // Modes & Movement
        BUILDER.push("modes_movement");
        BUILDER.comment("Modes & Movement Configuration");
        DIVE_TRIGGER_SPEED = BUILDER
                .comment("Speed threshold to ENTER Dive Mode (blocks/tick, ~0.4 ≈ 8 m/s)")
                .defineInRange("dive_trigger_speed", 0.4, 0.0, 1.0);
        DIVE_HOLD_SPEED = BUILDER
                .comment("Speed threshold to HOLD Dive Mode (hysteresis lower bound)")
                .defineInRange("dive_hold_speed", 0.15, 0.0, 1.0);
        CLEARANCE_MARGIN = BUILDER
                .comment("Clearance margin around hull (blocks)")
                .defineInRange("clearance_margin", 1.0, 0.0, 5.0);
        BUILDER.pop();

        // Pressure Jelly & Pushback
        BUILDER.push("pressure_jelly_pushback");
        BUILDER.comment("Pressure Jelly & Pushback System (interconnected set - calibrate together)");
        PENDING_MAX = BUILDER
                .comment("Pressure accumulator ceiling (dimensionless). Anchor value of the system")
                .defineInRange("pending_max", 100.0, 10.0, 1000.0);
        PENDING_GROWTH = BUILDER
                .comment("Growth per unit deficit/tick. At hard impact (~deficit=40) reaches threshold in ~0.5s")
                .defineInRange("pending_growth", 0.2, 0.0, 1.0);
        PENDING_DECAY = BUILDER
                .comment("Decay per tick (linear). From max cools to 0 in ~3.3s")
                .defineInRange("pending_decay", 1.5, 0.1, 10.0);
        PENDING_RESISTANCE_FACTOR = BUILDER
                .comment("Resistance force multiplier: mult = 1 / (1 + pending * factor). pending=25 → ×0.5, max → ×0.2")
                .defineInRange("pending_resistance_factor", 0.04, 0.0, 0.1);
        PUSHBACK_THRESHOLD = BUILDER
                .comment("Pushback activation threshold. Only top 20% of accumulation triggers pushback at default 80.0")
                .defineInRange("pushback_threshold", 80.0, 0.0, 100.0);
        PUSHBACK_FACTOR = BUILDER
                .comment("Pushback force: pushback = (pending - threshold) * factor. At max pending, equals -RATE_MAX")
                .defineInRange("pushback_factor", 0.05, 0.0, 0.2);
        MIN_BLOCKS_PER_TICK = BUILDER
                .comment("Minimum power limit floor (prevents crown stalling forever)")
                .defineInRange("min_blocks_per_tick", 1, 0, 10);
        RATE_SMOOTHING = BUILDER
                .comment("Lerp factor for drilling rate smoothing. ~95% of target in ~8 ticks, responsive but smooth")
                .defineInRange("rate_smoothing", 0.3, 0.0, 1.0);
        PUSHBACK_SMOOTHING = BUILDER
                .comment("Lerp factor for pushback smoothing. Slightly softer than drilling (hard push, not jerk)")
                .defineInRange("pushback_smoothing", 0.2, 0.0, 1.0);
        BUILDER.pop();

        // Navigation & Instruments
        BUILDER.push("navigation_instruments");
        BUILDER.comment("Navigation & Instruments Configuration");
        SAVED_DATA_TTL_TICKS = BUILDER
                .comment("TTL for old saved navigation data (ticks). Default 30 days = 43200000 ticks")
                .defineInRange("saved_data_ttl_ticks", 43200000L, 1000L, Long.MAX_VALUE);
        MAP_SCALE = BUILDER
                .comment("Map pixels per block in isometric view (placeholder - depends on GUI)")
                .defineInRange("map_scale", 4.0, 0.5, 16.0);
        MAP_HEIGHT_SCALE = BUILDER
                .comment("Map vertical scale in isometric view (placeholder)")
                .defineInRange("map_height_scale", 2.0, 0.5, 8.0);
        TERRADAR_SU_CONSUMPTION = BUILDER
                .comment("Terradar (seismic probe) constant SU consumption (placeholder)")
                .defineInRange("terradar_su_consumption", 8, 1, 128);
        TERRADAR_PULSE_INTERVAL = BUILDER
                .comment("Terradar pulse interval (ticks). Default 10 = 0.5s")
                .defineInRange("terradar_pulse_interval", 10, 1, 100);
        BUILDER.pop();

        // Capacities
        BUILDER.push("bearing_capacities");
        BUILDER.comment("Bearing Buffer Capacities");
        BEARING_BUFFER_ANDESITE = BUILDER
                .comment("Andesite bearing buffer capacity (slots)")
                .defineInRange("bearing_buffer_andesite", 16, 1, 256);
        BEARING_BUFFER_STURDY = BUILDER
                .comment("Sturdy bearing buffer capacity (slots)")
                .defineInRange("bearing_buffer_sturdy", 80, 1, 256);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
    /*
     * Фабрика констант тик-цикла из значений конфига. Зовётся, когда конфиг загружен (не в static-блоке).
     * Кэшировать на стороне BlockEntity, не пересобирать каждый тик.
     */
    public static TickCycle.Constants tickConstants() {
        return new TickCycle.Constants(
            DIVE_TRIGGER_SPEED.get().floatValue(), DIVE_HOLD_SPEED.get().floatValue(),
            MIN_BLOCKS_PER_TICK.get(),
            PENDING_GROWTH.get().floatValue(), PENDING_DECAY.get().floatValue(), PENDING_MAX.get().floatValue(),
            MAX_COMPENSATION.get().floatValue(),
            MIN_HARDNESS.get().floatValue(), RATE_MAX.get().floatValue(), PENDING_RESISTANCE_FACTOR.get().floatValue(),
            PUSHBACK_THRESHOLD.get().floatValue(), PUSHBACK_FACTOR.get().floatValue(),
            SOUND_DAMPING.get().floatValue(),
            (float) DEEPSLATE_Y.get().intValue(), (float) BEDROCK_Y.get().intValue(),
            PRESSURE_CURVE_K.get().floatValue(), PRESSURE_SCALE.get().floatValue());
    }
}