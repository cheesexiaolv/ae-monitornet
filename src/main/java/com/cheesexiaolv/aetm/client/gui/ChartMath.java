package com.cheesexiaolv.aetm.client.gui;

import java.util.Arrays;
import java.util.Locale;

/**
 * 折线图的<b>纯数学 / 纯格式化</b>部分：值域、刻度文字、索引换算、数值与时间文本。
 *
 * <p><b>为什么单独一个类</b>：这里的方法不碰任何 Minecraft / GUI 类型，可以用 {@code javac} 单独编译、
 * 在游戏外跑自检（值域是否盖住数据、三个刻度是否互不相同、鼠标 x → 最近采样点下标是否越界）。
 * {@link ThroughputChart} 只负责把这些结果画出来。
 *
 * <p><b>坐标约定</b>（与 {@link ThroughputChart} 一致，别改）：
 * 第 i 个采样点的 x = {@code left + i * (right - 1 - left) / (count - 1)}，即最后一个点落在 {@code right - 1}。
 */
public final class ChartMath {

    /** 流量类纵轴的顶部余量比例。 */
    public static final float HEADROOM = 1.10f;
    /** 中位数兜底：值域不超过 |中位数| 的这么多倍（压掉离群尖峰）。 */
    private static final float MEDIAN_LIMIT = 20f;
    /** 刻度最多保留的小数位：float 只有 ~7 位有效数字，再加位也分不开。 */
    private static final int MAX_DECIMALS = 7;
    /** 建议的小数位上限（超过它的加位只在"窄量程撞车"时才会用到）。 */
    private static final int SUGGESTED_MAX_DECIMALS = 3;

    private ChartMath() {}

    // ------------------------------------------------------------------
    // 值域
    // ------------------------------------------------------------------

    /**
     * 计算纵轴值域 {@code {min, max}}（已含余量，可直接做 y 映射）。
     *
     * <p>算法与旧实现一致（Tukey 围栏 Q1/Q3 ± 1.5·IQR 剔离群尖峰 → 中位数兜底 → 加余量），
     * 唯一的新增是 {@code flowStyle} 下<b>最新样本一定留在值域内</b>：
     * 流速图刚变成的新常态（例如插上加速卡后 25 → 2400）在窗口里的点数还没过半时，
     * 中位数兜底会把它压到 20×中位数以下，于是量程停在旧值、曲线贴顶 —— 那正是用户报的
     * "速率 2,400 但 Y 轴 28/14/0"。头部读数是"当前值"，纵轴必须至少能装下它。
     *
     * @param values    曲线值（≥ 1 个）
     * @param flowStyle true = 流速/加速度（值域含 0，且最新样本必留）；false = 储量（按数据自身范围铺满，
     *                  保留原有的离群尖峰压制，行为与修复前完全一致）
     */
    public static float[] range(float[] values, boolean flowStyle) {
        if (values == null || values.length == 0) {
            return new float[] {0f, 1f};
        }

        float[] sorted = values.clone();
        Arrays.sort(sorted);
        int cnt = sorted.length;
        float rawMin = sorted[0];
        float rawMax = sorted[cnt - 1];
        float dataMin;
        float dataMax;
        if (cnt >= 8) {
            float q1 = sorted[(int) (cnt * 0.25f)];
            float q3 = sorted[Math.min(cnt - 1, (int) (cnt * 0.75f))];
            float iqr = q3 - q1;
            dataMin = Math.max(rawMin, q1 - 1.5f * iqr);
            dataMax = Math.min(rawMax, q3 + 1.5f * iqr);
            if (dataMax - dataMin < 1.0e-6f) {
                // 围栏把值域削成一个点（全体相等 / IQR 为 0）→ 退回原始范围
                dataMin = rawMin;
                dataMax = rawMax;
            }
        } else {
            dataMin = rawMin;
            dataMax = rawMax;
        }

        // 中位数兜底：一步巨变（读档/切维度）不该把纵轴撑爆
        float medAbs = Math.abs(sorted[cnt / 2]);
        if (medAbs > 1.0e-6f) {
            if (dataMax > MEDIAN_LIMIT * medAbs) {
                dataMax = MEDIAN_LIMIT * medAbs;
            }
            if (dataMin < -MEDIAN_LIMIT * medAbs) {
                dataMin = -MEDIAN_LIMIT * medAbs;
            }
        }

        if (flowStyle) {
            float newest = values[values.length - 1];
            dataMin = Math.min(dataMin, newest);
            dataMax = Math.max(dataMax, newest);
        }

        float min;
        float max;
        if (flowStyle) {
            // 含 0：流量/加速度要能看出正负
            min = Math.min(0f, dataMin);
            max = Math.max(0f, dataMax);
            if (max > 0f) {
                max *= HEADROOM;
            } else if (min < 0f) {
                min *= HEADROOM;
            }
        } else {
            // 储量：按数据自身范围铺满，上下各留一点余量
            float pad = Math.max(1f, (dataMax - dataMin) * 0.1f);
            max = dataMax + pad;
            min = dataMin - pad * 0.5f;
        }
        if (max - min < 1.0e-6f) {
            max = min + Math.max(1f, Math.abs(max) * 0.01f);
        }
        return new float[] {min, max};
    }

    // ------------------------------------------------------------------
    // 刻度文字
    // ------------------------------------------------------------------

    /**
     * 一根纵轴的刻度格式：统一的分母（G / M / K / 1）+ 统一的小数位。
     *
     * <p><b>为什么需要它</b>：旧实现用固定的 "1 位小数" 打刻度，窄量程时会印出三个一模一样的
     * {@code 2.0K / 2.0K / 2.0K}（例如储量在 2,000,000 附近只波动 ±600）。这里的小数位按
     * <b>span</b> 自适应（{@code decimals = clamp(ceil(-log10(span/divisor)) + 1, 0, 3)}），
     * 如果三个刻度仍然撞车就继续加位，保证"相邻刻度字符串不同"。
     *
     * <p>悬停浮窗的数值也走 {@link #label(float)}，与轴刻度<b>共用一套格式</b>（不会出现
     * 轴上写 2.6K、浮窗写 2400 这种事）。
     */
    public record Axis(float min, float max, float span, double divisor, int decimals) {

        /** 单个数值的文本（本轴的分母与小数位）。 */
        public String label(float value) {
            if (value == 0f) {
                // 0 就是 0：不印 "0.0K" 这种带单位/小数的写法（流量类值域几乎总含 0）
                return "0";
            }
            double scaled = value / this.divisor;
            String text = String.format(Locale.ROOT, "%." + this.decimals + "f", scaled);
            // -0.0004 会印成 "-0"：数字位全是 0 的负数还原成 0
            if (text.startsWith("-") && text.chars().noneMatch(c -> c >= '1' && c <= '9')) {
                text = text.substring(1);
            }
            return text + suffix();
        }

        /** {顶部, 中间, 底部} 三个刻度，两两不同。 */
        public String[] labels() {
            return new String[] {label(this.max), label((this.max + this.min) / 2f), label(this.min)};
        }

        /** 单位后缀（由分母决定）。 */
        public String suffix() {
            if (this.divisor >= 1.0e9) {
                return "G";
            }
            if (this.divisor >= 1.0e6) {
                return "M";
            }
            if (this.divisor >= 1.0e3) {
                return "K";
            }
            return "";
        }

        /** 三个刻度是否两两不同。 */
        private boolean distinct() {
            String[] labels = labels();
            return !labels[0].equals(labels[1]) && !labels[1].equals(labels[2]);
        }
    }

    /** 由值域得到刻度格式（分母按量级、小数位按 span，撞车则继续加位）。 */
    public static Axis axis(float min, float max) {
        float span = Math.max(1.0e-6f, max - min);
        double scale = Math.max(Math.abs(min), Math.abs(max));
        double divisor = scale >= 1.0e9 ? 1.0e9 : scale >= 1.0e6 ? 1.0e6 : scale >= 1.0e3 ? 1.0e3 : 1.0;
        double scaledSpan = Math.max(1.0e-9, span / divisor);
        int start = clamp((int) Math.ceil(-Math.log10(scaledSpan)) + 1, 0, SUGGESTED_MAX_DECIMALS);

        Axis best = new Axis(min, max, span, divisor, start);
        for (int decimals = start; decimals <= MAX_DECIMALS; decimals++) {
            Axis candidate = new Axis(min, max, span, divisor, decimals);
            if (candidate.distinct()) {
                return candidate;
            }
            best = candidate;
        }
        return best;
    }

    // ------------------------------------------------------------------
    // 鼠标 ↔ 采样点
    // ------------------------------------------------------------------

    /**
     * 鼠标 x → 最近的采样点下标（吸附，不做线性插值）。
     *
     * @param left  曲线左端（第一个点）x
     * @param right 绘图区右边界（不含）；最后一个点固定落在 {@code right - 1}
     * @return 一定落在 {@code [0, count - 1]}；{@code count <= 1} 或横向宽度为 0 时返回 0
     */
    public static int nearestIndex(double mouseX, int left, int right, int count) {
        if (count <= 1) {
            return 0;
        }
        int span = right - 1 - left;
        if (span <= 0) {
            return 0;
        }
        long index = Math.round((mouseX - left) * (count - 1) / span);
        return (int) Math.max(0, Math.min(count - 1, index));
    }

    /** 第 {@code index} 个采样点的 x（浮点，线段顶点 / 十字准线都用它）。 */
    public static float pointX(int index, int count, int left, int right) {
        return left + (float) index * (right - 1 - left) / Math.max(1, count - 1);
    }

    // ------------------------------------------------------------------
    // 数值 / 时间文本
    // ------------------------------------------------------------------

    /** 单值紧凑数值：1.2G / 45M / 7.8K / 123（最多 4 字符）。 */
    public static String format(float value) {
        float abs = Math.abs(value);
        if (abs >= 1.0e9f) {
            return trim(value / 1.0e9f) + "G";
        }
        if (abs >= 1.0e6f) {
            return trim(value / 1.0e6f) + "M";
        }
        if (abs >= 1.0e3f) {
            return trim(value / 1.0e3f) + "K";
        }
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private static String trim(float scaled) {
        float abs = Math.abs(scaled);
        return abs >= 100f ? String.format(Locale.ROOT, "%.0f", scaled)
                : String.format(Locale.ROOT, "%.1f", scaled);
    }

    /**
     * 终端列表用的紧凑数值：1.2G / 45.0M / 302.2K / 1,234。
     *
     * <p>阈值（1e9 / 1e6 / 1e4）与旧 {@code ThroughputTerminalScreen.compact} 完全一致 —— 列宽是固定的，
     * 这里只把实现收到一处，不改变输出。
     */
    public static String formatCompact(double value) {
        double abs = Math.abs(value);
        if (abs >= 1.0e9) {
            return String.format(Locale.ENGLISH, "%.1fG", value / 1.0e9);
        }
        if (abs >= 1.0e6) {
            return String.format(Locale.ENGLISH, "%.1fM", value / 1.0e6);
        }
        if (abs >= 1.0e4) {
            return String.format(Locale.ENGLISH, "%.1fK", value / 1.0e3);
        }
        return String.format(Locale.ENGLISH, "%,.0f", value);
    }

    /** 相对"现在"的时间：-3.2s / -45s / -1m20s（悬停点总在"现在"之前，所以恒为负）。 */
    public static String formatRelative(double seconds) {
        double abs = Math.abs(seconds);
        if (abs < 10.0) {
            return "-" + String.format(Locale.ROOT, "%.1fs", abs);
        }
        if (abs < 60.0) {
            return "-" + String.format(Locale.ROOT, "%.0fs", abs);
        }
        return "-" + formatDuration(Math.round(abs));
    }

    /** 把秒数写成 45s / 10m / 1m20s / 1h。 */
    public static String formatDuration(long seconds) {
        if (seconds <= 0) {
            return "0";
        }
        if (seconds >= 3600 && seconds % 3600 == 0) {
            return (seconds / 3600) + "h";
        }
        if (seconds >= 60) {
            long m = seconds / 60;
            long s = seconds % 60;
            return s == 0 ? m + "m" : m + "m" + s + "s";
        }
        return seconds + "s";
    }

    /** 把 value 夹到 [a, b]（a > b 时也成立）。 */
    public static int clamp(int value, int a, int b) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        return Math.max(lo, Math.min(hi, value));
    }
}
