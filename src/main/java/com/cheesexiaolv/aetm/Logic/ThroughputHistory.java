package com.cheesexiaolv.aetm.Logic;

import java.util.Locale;

/**
 * Throughput history buffer (server samples, client draws).
 *
 * <p>Each sample records the <b>stored amount</b>, the <b>tick delta</b> since the previous
 * sample and whether the step is <b>continuous</b>. From those we derive three display metrics:
 * <ul>
 *   <li>{@link Metric#AMOUNT} storage: the raw amount</li>
 *   <li>{@link Metric#RATE} flow: delta amount / delta seconds (scaled to the current routine)</li>
 *   <li>{@link Metric#ACCELERATION} flow acceleration: delta rate / delta seconds</li>
 * </ul>
 *
 * <p><b>Discontinuity detection</b>: when entering a world or changing dimension the grid storage
 * is not ready yet, so the amount jumps from 0 to its real value. If such a step took part in the
 * difference normally it would produce a phantom spike and blow up the Y axis for the whole
 * window. Two kinds of discontinuity are recognised (jump from 0, or a single step larger than
 * {@value #JUMP_FACTOR} times the typical step) and the step is marked, so rate calculations skip
 * across it (reusing the previous value).
 *
 * <p>⚠️ <b>Only one step is rejected</b>: the step judged as a jump is dropped, but if the next
 * step is just as large, that is the new normal (new machine, more acceleration cards, stacking
 * upgrades ...) and it must be accepted, rebuilding the typical step. The old implementation only
 * rejected and never updated the typical step, so after a rate jump it could <b>never</b> cross it
 * and the flow curve froze at the pre-jump value.
 *
 * <p>1.21.1 port: no changes — plain Java, no Minecraft/AE2 imports.
 */
public final class ThroughputHistory {

    /** Maximum number of points drawn (bucketed average beyond that). */
    public static final int MAX_POINTS = 120;

    /** A single step larger than this multiple of the typical step is treated as discontinuous. */
    private static final double JUMP_FACTOR = 20.0;

    /** Chart time window. */
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

    /** Metric shown on the chart. */
    public enum Metric {

        AMOUNT("storage"),
        RATE("flow"),
        ACCELERATION("accel");

        /** Language key suffix: gui.ae_monitornet.metric.&lt;key&gt; */
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

    /** Amount of the previous sample (used for the discontinuity test). */
    private long lastAmount;
    /** Exponential moving average of |delta amount|, the "typical step". */
    private double typicalStep;
    /** Consecutive steps judged as jumps: from 1 on, the next one is accepted as the new normal. */
    private int rejectStreak;
    private boolean hasLast;

    public ThroughputHistory(int capacity) {
        capacity = Math.max(2, capacity);
        this.amounts = new long[capacity];
        this.deltas = new int[capacity];
        this.continuous = new boolean[capacity];
    }

    /** Push a sample (stored amount + tick delta since the previous sample). */
    public void push(long amount, int deltaTicks) {
        boolean ok = true;
        if (this.hasLast) {
            long step = Math.abs(amount - this.lastAmount);
            boolean jump = (this.lastAmount == 0L && amount != 0L)          // jump from 0: load/dimension change
                    || (this.typicalStep > 0.0 && step > JUMP_FACTOR * this.typicalStep);  // outlier jump
            if (jump) {
                if (this.rejectStreak >= 1) {
                    // Two consecutive large steps -> new normal, not a phantom spike
                    this.typicalStep = step;
                    this.rejectStreak = 0;
                } else {
                    // Reject the first one (blocks load/dimension spikes), judge the next one
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

    /** Chart data: value series + actual covered seconds (right-aligns a partially filled window). */
    public record Series(float[] values, double spanSeconds) {

        public static final Series EMPTY = new Series(new float[0], 0);
    }

    /** Build the chart series (window clipping + downsampling). routineTicks matches the readout unit. */
    public Series series(Window window, Metric metric, int routineTicks) {
        int n = this.count;
        if (n < 2) {
            return Series.EMPTY;
        }

        // Forward pass: display value per sample; reuse previous value across discontinuous steps
        float[] all = new float[n];
        double scale = Math.max(1, routineTicks) / 20.0;
        float lastRate = 0f;
        float lastAccel = 0f;
        for (int i = 0; i < n; i++) {
            if (metric == Metric.AMOUNT) {
                all[i] = (float) amountAt(i);
                continue;
            }
            // Rate of this step
            float rate;
            if (i == 0 || !continuousAt(i)) {
                rate = lastRate;
            } else {
                double seconds = deltaAt(i) / 20.0;
                rate = (float) ((amountAt(i) - amountAt(i - 1)) / seconds * scale);
            }
            // Acceleration of this step
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

        // Window clipping: accumulate ticks from the end (also tracking covered seconds)
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

        // Bucketed average downsampling
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
    // Encode / decode: @GuiSync only supports primitive types, so use a compact string
    // ------------------------------------------------------------------

    /** Encode as {@code "spanSeconds;v0,v1,..."}. */
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

    /** Parse the string produced by {@link #encode(Series)}. */
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
