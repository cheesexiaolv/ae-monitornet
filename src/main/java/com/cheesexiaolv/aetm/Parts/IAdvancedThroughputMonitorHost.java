package com.cheesexiaolv.aetm.Parts;

import com.cheesexiaolv.aetm.Logic.ThroughputHistory;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEKey;
import appeng.util.ConfigInventory;

/**
 * Host interface of the advanced throughput monitor menu.
 *
 * <p>{@link AdvancedThroughputMonitorPart} implements it as the {@code host} of
 * {@code AdvancedThroughputMonitorMenu}, so {@code MenuLocators.forPart(...)} can locate it and the
 * menu can expose sampling/display state.
 *
 * <p>1.21.1 port: unchanged.
 */
public interface IAdvancedThroughputMonitorHost {

    /** Currently monitored item/fluid key, may be null (unconfigured). */
    @Nullable
    AEKey getDisplayed();

    /** Current stored amount of that key in the grid. */
    long getAmount();

    /** Last computed rate value. */
    double getLastReportedValue();

    /** Last human readable rate text (e.g. "1.2k"). */
    String getLastHumanReadableValue();

    /** Unit suffix of the current routine (e.g. "/s", "/m"). */
    String getWorkRoutineUnit();

    /** Cycle to the next display routine (per t/s/m/10m). */
    void cycleRoutine();

    /** Current routine ordinal (0=tick 1=second 2=minute 3=10min). */
    int getRoutineOrdinal();

    /** Whether fluid amounts use B (buckets); false = mB. */
    boolean isFluidUnitB();

    /** Toggle fluid unit mB/B. */
    void toggleFluidUnit();

    /** Config inventory (the monitored object), bound to the menu's FakeSlot. */
    ConfigInventory getConfig();

    /** Current chart time window. */
    ThroughputHistory.Window getChartWindow();

    /** Cycle the chart time window. */
    void cycleChartWindow();

    /** Current chart metric (storage/flow/acceleration). */
    ThroughputHistory.Metric getChartMetric();

    /** Cycle the chart metric. */
    void cycleChartMetric();

    /** How many ticks the current routine represents (used to keep the chart unit consistent). */
    int getRoutineTicks();

    /** Chart data encoded as a compact string for the current window/metric. */
    String chartData();
}
