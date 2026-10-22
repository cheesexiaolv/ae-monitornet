package com.cheesexiaolv.aetm.Logic;

import java.util.Locale;

/**
 * 吞吐量历史缓冲（服务端采样，客户端画图）。
 *
 * <p>每个样本记录 <b>存量</b>、<b>距上一样本的 tick 数</b>，以及<b>本步是否连续</b>。
 * 由这些可以推导三种显示量：
 * <ul>
 *   <li>{@link Metric#AMOUNT} 储量：直接是存量</li>
 *   <li>{@link Metric#RATE} 流速：Δ存量 / Δ秒（换算到当前档位周期）</li>
 *   <li>{@link Metric#ACCELERATION} 流速加速度：Δ流速 / Δ秒</li>
 * </ul>
 *
 * <p><b>不连续检测</b>：进存档/切维度时网络存储尚未加载完，存量会从 0 一下跳到真实值；
 * 这种一步巨变如果照常参与差分，就会产生一个"假尖峰"，并且会在历史窗口里赖着把纵轴撑爆。
 * 因此这里识别两类不连续（从 0 起跳、单步超过典型步长的 {@value #JUMP_FACTOR} 倍）并标记该步，
 * 计算速率时跨过这类边界（沿用上一点的值）。
 *
 * <p>⚠️ <b>只拒收一步</b>：被判成跳变的那一步会拒收，但"连续第二步仍然这么大"就说明这是新常态
 * （换机器、插加速卡、堆叠增产……），必须认下来并用它重建典型步长。旧实现只拒收、不更新典型步长，
 * 于是速率一旦跳变就<b>永远</b>跨不过去 —— 流速曲线冻在跳变前的值上（实测：速率 2400 mB/t 时
 * 图表还停在 25，Y 轴 28/14/0），插满加速卡与不插看不出区别。储量路径不受影响（它不做差分）。
 */
public final class ThroughputHistory {

    /** 图表最多绘制的点数（超出则分桶平均）。 */
    public static final int MAX_POINTS = 120;

    /** 单步变化超过"典型步长"的这个倍数，就视为不连续跳变。 */
    private static final double JUMP_FACTOR = 20.0;

    /** 图表时间窗口。 */
    public enum Window {

        S10("10s", 10),
        M1("1m", 60),
        M10("10m", 600),
        M60("60m", 3600);

        public final String label;
        public final int seconds;

        Window(String label, int seconds) {
            this.label = label;
            this.seconds = seconds;
        }

        public Window next() {
            var values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public static Window byOrdinal(int ordinal) {
            var values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : M1;
        }
    }

    /** 图表显示的量。 */
    public enum Metric {

        AMOUNT("storage"),
        RATE("flow"),
        ACCELERATION("accel");

        /** 语言键后缀：gui.ae_monitornet.metric.&lt;key&gt; */
        public final String key;

        Metric(String key) {
            this.key = key;
        }

        public Metric next() {
            var values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public static Metric byOrdinal(int ordinal) {
            var values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : RATE;
        }
    }

    private final long[] amounts;
    private final int[] deltas;
    private final boolean[] continuous;

    private int count;
    private int next;

    /** 上一个样本的存量（用于判断不连续）。 */
    private long lastAmount;
    /** |Δ存量| 的指数滑动平均，代表"典型步长"。 */
    private double typicalStep;
    /** 连续被判成"跳变"的步数：≥1 时的下一步当作新常态认下来（见类注释）。 */
    private int rejectStreak;
    private boolean hasLast;

    public ThroughputHistory(int capacity) {
        capacity = Math.max(2, capacity);
        this.amounts = new long[capacity];
        this.deltas = new int[capacity];
        this.continuous = new boolean[capacity];
    }

    /** 推入一个样本（存量 + 距上一样本的 tick 数）。 */
    public void push(long amount, int deltaTicks) {
        boolean ok = true;
        if (this.hasLast) {
            long step = Math.abs(amount - this.lastAmount);
            boolean jump = (this.lastAmount == 0L && amount != 0L)          // 从 0 起跳：读档/存储未就绪
                    || (this.typicalStep > 0.0 && step > JUMP_FACTOR * this.typicalStep);  // 离群跳变
            if (jump) {
                if (this.rejectStreak >= 1) {
                    // 连续两步都这么大 → 这是新常态，不是假尖峰：认下来并用它重建典型步长
                    this.typicalStep = step;
                    this.rejectStreak = 0;
                } else {
                    // 第一步先拒收（挡住读档/切维度的假尖峰），下一步再判
                    this.rejectStreak++;
                    ok = false;
                }
            } else {
                this.typicalStep = this.typicalStep <= 0.0
                        ? step
                        : this.typicalStep * 0.9 + step * 0.1;
                this.rejectStreak = 0;
            }
        }

        this.amounts[this.next] = amount;
        this.deltas[this.next] = Math.max(1, deltaTicks);
        this.continuous[this.next] = ok;
        this.next = (this.next + 1) % this.amounts.length;
        if (this.count < this.amounts.length) {
            this.count++;
        }

        this.lastAmount = amount;
        this.hasLast = true;
    }

    public void clear() {
        this.count = 0;
        this.next = 0;
        this.lastAmount = 0L;
        this.typicalStep = 0.0;
        this.rejectStreak = 0;
        this.hasLast = false;
    }

    public int size() {
        return this.count;
    }

    /** 图表数据：值序列 + 实际覆盖的秒数（不满窗口时用来把曲线右对齐）。 */
    public record Series(float[] values, double spanSeconds) {

        public static final Series EMPTY = new Series(new float[0], 0);
    }

    /** 生成图表用的数值序列（按窗口裁剪 + 降采样）。routineTicks 让"流速"与读数同单位。 */
    public Series series(Window window, Metric metric, int routineTicks) {
        int n = this.count;
        if (n < 2) {
            return Series.EMPTY;
        }

        // 前向计算每个样本的显示值；跨不连续边界时沿用上一点，避免假尖峰
        float[] all = new float[n];
        double scale = Math.max(1, routineTicks) / 20.0;
        float lastRate = 0f;
        float lastAccel = 0f;
        for (int i = 0; i < n; i++) {
            if (metric == Metric.AMOUNT) {
                all[i] = (float) amountAt(i);
                continue;
            }
            // 本步速率
            float rate;
            if (i == 0 || !continuousAt(i)) {
                rate = lastRate;
            } else {
                double seconds = deltaAt(i) / 20.0;
                rate = (float) ((amountAt(i) - amountAt(i - 1)) / seconds * scale);
            }
            // 本步加速度
            float accel;
            if (i == 0 || !continuousAt(i)) {
                accel = lastAccel;
            } else {
                double seconds = deltaAt(i) / 20.0;
                accel = (rate - lastRate) / (float) seconds;
            }
            all[i] = metric == Metric.RATE ? rate : accel;
            lastRate = rate;
            lastAccel = accel;
        }

        // 窗口裁剪：自末尾向前累加 tick（同时统计实际覆盖的秒数）
        long windowTicks = (long) window.seconds * 20L;
        long acc = 0L;
        int from = 0;
        for (int i = n - 1; i >= 1; i--) {
            acc += deltaAt(i);
            from = i - 1;
            if (acc >= windowTicks) {
                break;
            }
        }
        int span = n - from;
        if (span < 2) {
            return Series.EMPTY;
        }
        double coveredSeconds = acc / 20.0;

        // 分桶平均降采样
        int points = Math.min(MAX_POINTS, span);
        float[] out = new float[points];
        for (int p = 0; p < points; p++) {
            int b0 = from + (int) ((long) p * span / points);
            int b1 = from + (int) ((long) (p + 1) * span / points);
            if (b1 <= b0) {
                b1 = Math.min(n, b0 + 1);
            }
            double sum = 0;
            int cnt = 0;
            for (int i = b0; i < b1 && i < n; i++) {
                sum += all[i];
                cnt++;
            }
            out[p] = cnt == 0 ? 0f : (float) (sum / cnt);
        }
        return new Series(out, coveredSeconds);
    }

    private long amountAt(int i) {
        return this.amounts[(start() + i) % this.amounts.length];
    }

    private int deltaAt(int i) {
        return this.deltas[(start() + i) % this.deltas.length];
    }

    private boolean continuousAt(int i) {
        return this.continuous[(start() + i) % this.continuous.length];
    }

    private int start() {
        return (this.next - this.count + this.amounts.length) % this.amounts.length;
    }

    // ------------------------------------------------------------------
    // 编码 / 解码：@GuiSync 只支持原生类型，这里用紧凑字符串
    // ------------------------------------------------------------------

    /** 编码为 {@code "跨度秒;v0,v1,..."}。 */
    public static String encode(Series series) {
        float[] values = series.values();
        var sb = new StringBuilder(values.length * 8 + 12);
        sb.append(String.format(Locale.ROOT, "%.4g", series.spanSeconds())).append(';');
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.ROOT, "%.4g", values[i]));
        }
        return sb.toString();
    }

    /** 解析 {@link #encode(Series)} 的字符串。 */
    public static Series decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return Series.EMPTY;
        }
        int sep = encoded.indexOf(';');
        if (sep < 0) {
            return Series.EMPTY;
        }
        double span;
        try {
            span = Double.parseDouble(encoded.substring(0, sep));
        } catch (NumberFormatException e) {
            span = 0;
        }
        String rest = encoded.substring(sep + 1);
        if (rest.isEmpty()) {
            return new Series(new float[0], span);
        }
        String[] parts = rest.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Float.parseFloat(parts[i]);
            } catch (NumberFormatException e) {
                result[i] = 0f;
            }
        }
        return new Series(result, span);
    }
}
