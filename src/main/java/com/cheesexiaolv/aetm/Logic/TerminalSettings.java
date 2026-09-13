package com.cheesexiaolv.aetm.Logic;

/**
 * 聚合终端的显示设置（面板零件与无线终端物品共用同一种结构）。
 *
 * <p>只影响"怎么显示"，不影响采样——采样间隔是服务端全局配置
 * （{@code AetmConfig.SAMPLING_INTERVAL_TICKS}）。
 *
 * @param window         折线图时间窗口
 * @param metric         显示量（储量 / 流速 / 加速度）
 * @param largeUnit      是否用大单位（流体 B 而非 mB）
 * @param routineOrdinal 速率档位（0=t 1=s 2=m 3=10m）
 */
public record TerminalSettings(ThroughputHistory.Window window, ThroughputHistory.Metric metric,
        boolean largeUnit, int routineOrdinal) {

    /** 视图：文字列表。 */
    public static final int VIEW_TEXT = 0;
    /** 视图：列表 + 选中行折线图。 */
    public static final int VIEW_CHART = 1;

    /** 各档位代表多少 tick（展示速率 = 每 tick 量 × 该值）。 */
    private static final int[] ROUTINE_TICKS = { 1, 20, 1200, 12000 };

    /** 默认设置：1 分钟窗口 / 流速 / 小单位 / 秒档。 */
    public static final TerminalSettings DEFAULT = new TerminalSettings(ThroughputHistory.Window.M1,
            ThroughputHistory.Metric.RATE, false, 1);

    /** 档位 → tick 数（越界回落到 1 秒档）。 */
    public static int routineTicks(int routineOrdinal) {
        if (routineOrdinal < 0 || routineOrdinal >= ROUTINE_TICKS.length) {
            return ROUTINE_TICKS[1];
        }
        return ROUTINE_TICKS[routineOrdinal];
    }

    /** 档位 → 速率单位后缀（t / s / m / 10m）。 */
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
     * 切换显示量：界面只保留<b>储量 ⇄ 流速</b>两档（加速度已从界面撤掉）。
     *
     * <p>{@link ThroughputHistory.Metric#ACCELERATION} 只可能来自老存档的既有设置，这里顺手把它归一成流速，
     * 保证"点一下一定有变化"。
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
