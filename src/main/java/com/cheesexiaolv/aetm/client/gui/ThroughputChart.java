package com.cheesexiaolv.aetm.client.gui;

import com.cheesexiaolv.aetm.Logic.ThroughputHistory;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;

/**
 * 吞吐量折线图绘制（坐标轴 + 纵向时间网格 + 刻度值 + 单位）。
 *
 * <p>要点：
 * <ul>
 *   <li>1.19.2 没有 {@code GuiGraphics}：背景/网格用 {@link GuiComponent#fill} 填充，<b>曲线用 Tesselator 提交 LINES 线段</b>；</li>
 *   <li>曲线按<b>时间</b>定位：最新点在右侧，数据不满窗口时只占右侧一段，左边留空（不会被拉伸）；</li>
 *   <li>纵轴上方留 10% 余量，避免顶到边框；</li>
 *   <li>值域用 Tukey 围栏 + 中位数兜底剔除离群尖峰。</li>
 * </ul>
 */
public final class ThroughputChart {

    private static final int COL_BORDER = 0xFF5A5A5A;
    private static final int COL_BG = 0xFF0C0C0C;
    private static final int COL_GRID = 0xFF242424;
    private static final int COL_ZERO = 0xFF3C5A3C;
    private static final int COL_POS = 0xFF4FD8FF;
    private static final int COL_NEG = 0xFFFF6B6B;
    private static final int COL_AXIS = 0xFF6E6E6E;
    private static final int COL_LABEL = 0xFF9A9A9A;

    /** 左侧给 Y 轴刻度值的宽度（0.75 倍字体）。 */
    private static final int LEFT_GUTTER = 19;
    /** 底部给横轴时间刻度的宽度。 */
    private static final int BOTTOM_GUTTER = 10;
    /** 坐标轴文字缩放。 */
    private static final float TEXT_SCALE = 0.75f;
    /** 纵轴顶部余量比例。 */
    private static final float HEADROOM = 1.10f;
    /** 曲线半线宽（GUI 像素）：0.25 → 线宽 0.5 px。想让线更细/更粗只改这一个数。 */
    private static final float LINE_HALF_WIDTH = 0.25f;
    private ThroughputChart() {}

    /**
     * @param series       曲线数据（值 + 实际覆盖秒数）
     * @param windowSeconds 时间窗口总秒数（用于横轴刻度）
     * @param unit         纵轴单位
     * @param includeZero  是否把 0 纳入值域：流量/加速度要（能看出正负），储量不要（否则曲线挤在顶端）
     */
    public static void draw(PoseStack poseStack, Font font, int x, int y, int w, int h,
            ThroughputHistory.Series series, int windowSeconds, String unit, boolean includeZero) {
        GuiComponent.fill(poseStack, x - 1, y - 1, x + w + 1, y + h + 1, COL_BORDER);
        GuiComponent.fill(poseStack, x, y, x + w, y + h, COL_BG);

        int plotX = x + LEFT_GUTTER;
        int plotY = y + 2;
        int plotW = w - LEFT_GUTTER - 4;
        int plotH = h - BOTTOM_GUTTER - 2;
        if (plotW < 8 || plotH < 8) {
            return;
        }

        // 坐标轴
        GuiComponent.fill(poseStack, plotX, plotY, plotX + 1, plotY + plotH, COL_AXIS);
        GuiComponent.fill(poseStack, plotX, plotY + plotH - 1, plotX + plotW, plotY + plotH, COL_AXIS);

        // 纵向时间网格 + 横轴刻度
        int labelY = plotY + plotH + 1;
        for (int i = 1; i <= 3; i++) {
            int gx = plotX + plotW * i / 4;
            GuiComponent.fill(poseStack, gx, plotY, gx + 1, plotY + plotH - 1, COL_GRID);
            String label = "-" + formatDuration((long) windowSeconds * (4 - i) / 4);
            text(poseStack, font, label, gx - Math.round(font.width(label) * TEXT_SCALE / 2f), labelY, COL_LABEL);
        }
        text(poseStack, font, "0", plotX + plotW - Math.round(font.width("0") * TEXT_SCALE), labelY, COL_LABEL);

        // 单位标注（绘图区右上角）
        if (unit != null && !unit.isEmpty()) {
            text(poseStack, font, unit,
                    plotX + plotW - Math.round(font.width(unit) * TEXT_SCALE), plotY + 1, COL_LABEL);
        }

        float[] values = series == null ? null : series.values();
        if (values == null || values.length < 2) {
            GuiComponent.fill(poseStack, plotX, plotY + plotH / 2, plotX + plotW, plotY + plotH / 2 + 1, COL_ZERO);
            return;
        }

        // 值域：Tukey 围栏（Q1/Q3 ± 1.5*IQR）剔离群尖峰，再用中位数兜底；最后加顶部余量
        float[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
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
                dataMin = rawMin;
                dataMax = rawMax;
            }
        } else {
            dataMin = rawMin;
            dataMax = rawMax;
        }
        float medAbs = Math.abs(sorted[cnt / 2]);
        if (medAbs > 1.0e-6f) {
            if (dataMax > 20f * medAbs) {
                dataMax = 20f * medAbs;
            }
            if (dataMin < -20f * medAbs) {
                dataMin = -20f * medAbs;
            }
        }

        // 值域：包含 0 时（流量/加速度）正负都能看出来；不包含 0 时（储量）按数据自身范围铺满，
        // 上下各留一点余量，避免曲线贴边。
        float min;
        float max;
        if (includeZero) {
            min = Math.min(0f, dataMin);
            max = Math.max(0f, dataMax);
            if (max > 0f) {
                max *= HEADROOM;
            } else if (min < 0f) {
                min *= HEADROOM;
            }
        } else {
            float pad = Math.max(1f, (dataMax - dataMin) * 0.1f);
            max = dataMax + pad;
            min = dataMin - pad * 0.5f;
        }
        if (max - min < 1.0e-6f) {
            max = min + Math.max(1f, Math.abs(max) * 0.01f);
        }
        float span = max - min;

        // 中线 + 0 基线
        GuiComponent.fill(poseStack, plotX, plotY + plotH / 2, plotX + plotW, plotY + plotH / 2 + 1, COL_GRID);
        int zeroY = valueToY(0f, min, span, plotY, plotH);
        GuiComponent.fill(poseStack, plotX, zeroY, plotX + plotW, zeroY + 1, COL_ZERO);

        // 曲线：按时间定位 —— 最新点在右侧；窗口没填满时只占右侧一段。
        //
        // 线条绘制改用 Tesselator 提交为细四边形带（不再逐列 fill 像素）：
        //   · 顶点保留浮点坐标，斜线是真正连续的直线段，不需要旧的"陡坡填满"补丁；
        //   · 线宽 = LINE_HALF_WIDTH×2 = 0.5 GUI 像素，比旧的"每列 1 GUI 像素"细；
        //   · 每段的颜色沿用"按起点值的正负"规则（与旧实现一致）。
        double covered = Math.max(0.0, Math.min(series.spanSeconds(), windowSeconds));
        int lineLeft = plotX + plotW - (int) Math.round(plotW * covered / Math.max(1.0, windowSeconds));
        lineLeft = Math.max(plotX, Math.min(lineLeft, plotX + plotW - 1));
        int n = values.length;
        if (n < 2) {
            // 只有一个采样点时画不出线段，退回一个像素的点
            plot(poseStack, pointX(0, n, lineLeft, plotX + plotW), valueToY(values[0], min, span, plotY, plotH), values[0]);
        } else {
            drawLineQuads(poseStack, values, n, min, span, lineLeft, plotX, plotW, plotY, plotH);
        }

        // Y 轴刻度（数值，单位已在右上角标注）
        text(poseStack, font, format(max), x + 1, plotY, COL_LABEL);
        text(poseStack, font, format((max + min) / 2f), x + 1, plotY + plotH / 2 - 3, COL_LABEL);
        text(poseStack, font, format(min), x + 1, plotY + plotH - 7, COL_LABEL);
    }

    /** 以 {@value #TEXT_SCALE} 倍字号在 (x,y) 画一行小字。 */
    private static void text(PoseStack poseStack, Font font, String s, int x, int y, int color) {
        poseStack.pushPose();
        poseStack.translate(x, y, 0);
        poseStack.scale(TEXT_SCALE, TEXT_SCALE, 1f);
        font.draw(poseStack, s, 0, 0, color);
        poseStack.popPose();
    }

    /** 紧凑数值：1.2G / 45M / 7.8K / 123（最多 4 字符） */
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
        return String.format(java.util.Locale.ROOT, "%.0f", value);
    }

    private static String trim(float scaled) {
        float abs = Math.abs(scaled);
        return abs >= 100f ? String.format(java.util.Locale.ROOT, "%.0f", scaled)
                : String.format(java.util.Locale.ROOT, "%.1f", scaled);
    }

    /** 把秒数写成 45s / 10m / 1h。 */
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

    private static void plot(PoseStack poseStack, int px, int py, float value) {
        GuiComponent.fill(poseStack, px, py, px + 1, py + 1, value >= 0f ? COL_POS : COL_NEG);
    }

    private static int pointX(int index, int count, int left, int right) {
        return left + (int) Math.round((double) index * (right - 1 - left) / (count - 1));
    }

    private static int valueToY(float value, float min, float span, int y, int h) {
        float t = (value - min) / span;
        int py = y + h - 1 - Math.round(t * (h - 1));
        return Math.max(y, Math.min(y + h - 1, py));
    }

    /**
     * 用 Tesselator 把折线画成一条**细四边形带**（{@link VertexFormat.Mode#QUADS} + {@code POSITION_COLOR}）。
     *
     * <p><b>为什么不用 {@code Mode.LINES}：</b>1.19.2 的 GUI 阶段用 {@code GL_LINES} 提交时线段不出现在
     * 画面上（无异常、日志无痕迹，已实测一次），而 {@code QUADS} 是 vanilla 自己在本阶段用的路径
     * （{@code GuiComponent.fill} 与 {@code DebugScreenOverlay} 都是 {@code QUADS + POSITION_COLOR +
     * BufferUploader.drawWithShader}）。
     *
     * <p>线宽 = {@value #LINE_HALF_WIDTH}×2 = 0.5 GUI 像素（guiScale 3 下约 1.5 个物理像素），比旧的
     * "每列 1 GUI 像素"细；顶点保留浮点坐标，斜线是连续直线段，因此不需要旧的"陡坡填满"补丁。
     */
    private static void drawLineQuads(PoseStack poseStack, float[] values, int n, float min, float span,
                                      int lineLeft, int plotX, int plotW, int plotY, int plotH) {
        PoseStack.Pose pose = poseStack.last();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float prevX = pointXf(0, n, lineLeft, plotX + plotW);
        // +0.5：让线压在像素中心，而不是正好落在像素边界上
        float prevY = valueToYf(values[0], min, span, plotY, plotH) + 0.5f;
        for (int i = 1; i < n; i++) {
            float nextX = pointXf(i, n, lineLeft, plotX + plotW);
            float nextY = valueToYf(values[i], min, span, plotY, plotH) + 0.5f;
            int color = values[i - 1] >= 0f ? COL_POS : COL_NEG;
            float dx = nextX - prevX;
            float dy = nextY - prevY;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1.0e-4f) {
                // 纯垂直段（同一列上的重复采样点）：按竖直方向给宽度，避免退化成 0 宽而看不见
                quadVertex(builder, pose, prevX - LINE_HALF_WIDTH, prevY, color);
                quadVertex(builder, pose, prevX + LINE_HALF_WIDTH, prevY, color);
                quadVertex(builder, pose, prevX + LINE_HALF_WIDTH, nextY, color);
                quadVertex(builder, pose, prevX - LINE_HALF_WIDTH, nextY, color);
            } else {
                // 沿线段法线方向偏移半个线宽 → 一个旋转过的细长四边形
                float nx = -dy / len * LINE_HALF_WIDTH;
                float ny = dx / len * LINE_HALF_WIDTH;
                quadVertex(builder, pose, prevX + nx, prevY + ny, color);
                quadVertex(builder, pose, nextX + nx, nextY + ny, color);
                quadVertex(builder, pose, nextX - nx, nextY - ny, color);
                quadVertex(builder, pose, prevX - nx, prevY - ny, color);
            }
            prevX = nextX;
            prevY = nextY;
        }
        BufferUploader.drawWithShader(builder.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** 往 {@code POSITION_COLOR} 缓冲里写一个顶点（把 ARGB 拆成 4 个 0-255 分量）。 */
    private static void quadVertex(BufferBuilder builder, PoseStack.Pose pose, float x, float y, int color) {
        builder.vertex(pose.pose(), x, y, 0f)
                .color((color >>> 24) & 0xFF, (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF)
                .endVertex();
    }

    /** {@link #pointX} 的浮点版本（线段顶点不取整）。 */
    private static float pointXf(int index, int count, int left, int right) {
        return left + (float) index * (right - 1 - left) / Math.max(1, count - 1);
    }

    /** {@link #valueToY} 的浮点版本（线段顶点不取整，且不做像素取整）。 */
    private static float valueToYf(float value, float min, float span, int y, int h) {
        float t = (value - min) / span;
        float py = y + h - 1 - t * (h - 1);
        return Math.max(y, Math.min(y + h - 1, py));
    }
}
