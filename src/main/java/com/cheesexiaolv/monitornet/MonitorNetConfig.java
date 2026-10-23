package com.cheesexiaolv.monitornet;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * AE MonitorNet config (written to {@code <world>/serverconfig/ae_monitornet-server.toml}).
 *
 * <p>1.21.1 port: {@code net.minecraftforge.common.ForgeConfigSpec} was renamed to
 * {@link ModConfigSpec}; the builder API itself is unchanged.
 */
public final class MonitorNetConfig {

    public static final ModConfigSpec SPEC;

    /** Sampling interval in ticks. Smaller = smoother chart, more server load. 20 ticks = 1s. */
    public static final ModConfigSpec.IntValue SAMPLING_INTERVAL_TICKS;

    /** Maximum number of samples kept per monitor (longest history = capacity x interval). */
    public static final ModConfigSpec.IntValue HISTORY_CAPACITY;

    /** Chart refresh rate multiplier: 1.0 = same speed as the server sampling interval. */
    public static final ModConfigSpec.DoubleValue CHART_REFRESH_MULTIPLIER;

    static {
        var builder = new ModConfigSpec.Builder();

        builder.comment("AE MonitorNet settings").push("throughput_monitor");

        SAMPLING_INTERVAL_TICKS = builder
                .comment("Sampling interval in ticks (20 ticks = 1 second).",
                        "Smaller = smoother chart but more server load. Range 1..1200.")
                .defineInRange("samplingIntervalTicks", 10, 1, 1200);

        HISTORY_CAPACITY = builder
                .comment("Maximum number of samples kept per monitor.",
                        "Longest history = capacity x samplingIntervalTicks.",
                        "e.g. 3600 samples @ 20 ticks = 1 hour. Range 60..7200.")
                .defineInRange("historyCapacity", 3600, 60, 7200);

        CHART_REFRESH_MULTIPLIER = builder
                .comment("How often the terminal's floating chart windows ask the server for a fresh curve,",
                        "as a multiple of the sampling interval.",
                        "1.0 = same speed as the server sampling interval (samplingIntervalTicks, default);",
                        "Above 1.0 the same curve is re-sent more often than new samples arrive, so it only",
                        "makes the redraw smoother, not the data fresher;",
                        "0.5 = refresh every two sampling periods;",
                        "0.1 = refresh every ten sampling periods (lightest on the network).",
                        "The terminal chart redraw interval = max(1, round(samplingIntervalTicks / this value)) ticks.",
                        "Range 0.1..4.0.")
                .defineInRange("chartRefreshMultiplier", 1.0, 0.1, 4.0);

        builder.pop();

        SPEC = builder.build();
    }

    private MonitorNetConfig() {}
}
