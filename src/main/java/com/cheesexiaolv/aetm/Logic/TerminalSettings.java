package com.cheesexiaolv.aetm.Logic;

/**
 * Display settings of the aggregated terminal (shared by the part and the wireless item).
 *
 * <p>Only affects "how to display", never sampling — the sampling interval is the server-wide
 * config value ({@code AetmConfig.SAMPLING_INTERVAL_TICKS}).
 *
 * <p>1.21.1 port: unchanged (plain record/enum arithmetic).
 *
 * @param window         chart time window
 * @param metric         displayed quantity (storage / flow / acceleration)
 * @param largeUnit      use large units (fluid B instead of mB)
 * @param routineOrdinal rate routine (0=t 1=s 2=m 3=10m)
 */
public record TerminalSettings(ThroughputHistory.Window window, ThroughputHistory.Metric metric,
        boolean largeUnit, int routineOrdinal) {

    /** View: text list. */
    public static final int VIEW_TEXT = 0;
    /** View: list + chart of the selected row. */
    public static final int VIEW_CHART = 1;

    /** How many ticks each routine represents (displayed rate = per-tick amount x this). */
    private static final int[] ROUTINE_TICKS = { 1, 20, 1200, 12000 };

    /** Defaults: 1 minute window / flow / small unit / seconds routine. */
    public static final TerminalSettings DEFAULT = new TerminalSettings(ThroughputHistory.Window.M1,
            ThroughputHistory.Metric.RATE, false, 1);

    /** Routine -> ticks (out of range falls back to the seconds routine). */
    public static int routineTicks(int routineOrdinal) {
        if (routineOrdinal < 0 || routineOrdinal >= ROUTINE_TICKS.length) {
            return ROUTINE_TICKS[1];
        }
        return ROUTINE_TICKS[routineOrdinal];
    }

    /** Routine -> rate unit suffix (t / s / m / 10m). */
    public static String routineUnit(int routineOrdinal) {
        return switch (routineOrdinal) {
            case 0 -> "t";
            case 2 -> "m";
            case 3 -> "10m";
            default -> "s";
        };
    }

    public TerminalSettings cycleWindow() {
        return new TerminalSettings(window.next(), metric, largeUnit, routineOrdinal);
    }

    /**
     * Toggle the displayed quantity: the GUI only keeps <b>storage ⇄ flow</b> (acceleration was
     * removed). {@link ThroughputHistory.Metric#ACCELERATION} can only come from an old save, so
     * it is normalised to flow here to guarantee "one click = one visible change".
     */
    public TerminalSettings cycleMetric() {
        ThroughputHistory.Metric next = switch (metric) {
            case AMOUNT -> ThroughputHistory.Metric.RATE;
            default -> ThroughputHistory.Metric.AMOUNT;
        };
        return new TerminalSettings(window, next, largeUnit, routineOrdinal);
    }

    public TerminalSettings toggleUnit() {
        return new TerminalSettings(window, metric, !largeUnit, routineOrdinal);
    }

    public TerminalSettings cycleRoutine() {
        return new TerminalSettings(window, metric, largeUnit, (routineOrdinal + 1) % ROUTINE_TICKS.length);
    }
}
