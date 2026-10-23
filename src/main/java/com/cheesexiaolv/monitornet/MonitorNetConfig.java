package com.cheesexiaolv.monitornet;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * AE MonitorNet 配置（生成在 {@code <存档>/serverconfig/ae_monitornet-server.toml}）。
 */
public final class MonitorNetConfig {

    public static final ForgeConfigSpec SPEC;

    /** 采样间隔（tick）。越小曲线越细腻、开销越大。20 tick = 1 秒。 */
    public static final ForgeConfigSpec.IntValue SAMPLING_INTERVAL_TICKS;

    /** 历史缓冲保留的最大采样点数（决定最长可回看时长 = 点数 × 采样间隔）。 */
    public static final ForgeConfigSpec.IntValue HISTORY_CAPACITY;

    /** 图线绘制频率倍率：1.0 = 与服务端采样频率同速。 */
    public static final ForgeConfigSpec.DoubleValue CHART_REFRESH_MULTIPLIER;

    static {
        var builder = new ForgeConfigSpec.Builder();

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
