package com.cheesexiaolv.aetm.client.gui;

import com.cheesexiaolv.aetm.Logic.ThroughputHistory;

import javax.annotation.Nullable;

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
 * 吞吐量折线图绘制（坐标轴 + 纵向时间网格 + 刻度值 + 单位 + 悬停十字准线/浮窗）。
 *
 * <p>要点：
 * <ul>
 *   <li>1.19.2 没有 {@code GuiGraphics}：背景/网格用 {@link GuiComponent#fill} 填充，
 *       <b>曲线与悬停准线都用 Tesselator 提交 QUADS</b>（{@code Mode.LINES} 在 GUI 阶段不渲染）；</li>
 *   <li>曲线按<b>时间</b>定位：最新点在右侧，数据不满窗口时只占右侧一段，左边留空（不会被拉伸）；</li>
 *   <li>纵轴上方留 10% 余量，避免顶到边框；</li>
 *   <li>值域 / 刻度 / 索引换算全在 {@link ChartMath}（纯逻辑，可离线自检）：Tukey 围栏 + 中位数兜底，
 *       且流量类<b>最新样本一定在值域内</b>（否则量程会停在旧值、与头部读数对不上）；</li>
 *   <li>鼠标在绘图区里时叠加悬停层：吸附到最近采样点的十字准线 + 轴上读数 + 跟随鼠标的数值浮窗。
 *       数据只用本地 {@code series}，不发网络包。</li>
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

    /** 悬停十字准线（半透明白，1px）。 */
    private static final int COL_HOVER_LINE = 0x80FFFFFF;
    /** 悬停时轴上的读数（比静态刻度亮一档）。 */
    private static final int COL_HOVER_TEXT = 0xFFE8E8E8;
    /** 浮窗：半透明深底 / 细边框 / 数值 / 时间。 */
    private static final int COL_TIP_BG = 0xE0101010;
    private static final int COL_TIP_BORDER = 0x80FFFFFF;
    private static final int COL_TIP_VALUE = 0xFFFFFFFF;
    private static final int COL_TIP_TIME = 0xFFB0B0B0;

    /** 左侧给 Y 轴刻度值的宽度（0.75 倍字体）。 */
    private static final int LEFT_GUTTER = 19;
    /** 底部给横轴时间刻度的宽度。 */
    private static final int BOTTOM_GUTTER = 10;
    /** 坐标轴文字缩放。 */
    private static final float TEXT_SCALE = 0.75f;
    /** 曲线半线宽（GUI 像素）：0.25 → 线宽 0.5 px。想让线更细/更粗只改这一个数。 */
    private static final float LINE_HALF_WIDTH = 0.25f;
    /** 浮窗行高（1.0 倍字体）。 */
    private static final int TIP_LINE_H = 10;
    /** 浮窗与鼠标的距离。 */
    private static final int TIP_GAP = 8;

    private ThroughputChart() {}

    /**
     * 悬停上下文。
     *
     * <p>⚠️ {@code mouseX/mouseY} 必须与图表的 {@code x/y} <b>同一坐标系</b>：高级监控器里图表画在
     * 屏幕绝对坐标上（offset 0,0）；浮动子窗口里图表画在面板相对坐标上（offset = leftPos, topPos）。
     * 屏幕尺寸用来把浮窗夹在屏幕内（子窗口可以被拖到屏幕外）。
     */
    public record Hover(int mouseX, int mouseY, int screenW, int screenH, int offsetX, int offsetY) {}

    /**
     * @param series        曲线数据（值 + 实际覆盖秒数）
     * @param windowSeconds 时间窗口总秒数（用于横轴刻度）
     * @param unit          纵轴单位
     * @param flowChart     是否是"流量类"（流速/加速度）：值域含 0、最新样本必留；储量传 false
     * @param hover         悬停上下文（null = 不画悬停层）
     */
    public static void draw(PoseStack poseStack, Font font, int x, int y, int w, int h,
            ThroughputHistory.Series series, int windowSeconds, String unit, boolean flowChart,
            @Nullable Hover hover) {
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
            String label = "-" + ChartMath.formatDuration((long) windowSeconds * (4 - i) / 4);
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

        // 值域（Tukey 围栏 + 中位数兜底 + 余量，流量类还要保证最新样本在值域内）与刻度格式
        float[] range = ChartMath.range(values, flowChart);
        float min = range[0];
        float max = range[1];
        float span = max - min;
        ChartMath.Axis axis = ChartMath.axis(min, max);

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
        drawLineQuads(poseStack, values, n, min, span, lineLeft, plotX, plotW, plotY, plotH);

        // Y 轴刻度（数值，单位已在右上角标注）—— 小数位按 span 自适应，三个刻度保证互不相同
        String[] ticks = axis.labels();
        text(poseStack, font, ticks[0], x + 1, plotY, COL_LABEL);
        text(poseStack, font, ticks[1], x + 1, plotY + plotH / 2 - 3, COL_LABEL);
        text(poseStack, font, ticks[2], x + 1, plotY + plotH - 7, COL_LABEL);

        // 悬停层最后画（压在曲线与刻度之上）；鼠标不在绘图区时什么都不画
        if (hover != null) {
            drawHover(poseStack, font, hover, values, min, span, axis, x, y, w, h,
                    lineLeft, plotX, plotY, plotW, plotH, windowSeconds, covered, unit);
        }
    }

    /**
     * 悬停层：十字准线（吸附到最近采样点）+ Y 轴左侧数值 + X 轴下方时间 + 跟随鼠标的浮窗。
     *
     * <p>鼠标离开绘图区时整层都不会被调用 ⇒ 悬停元素自然消失。
     */
    private static void drawHover(PoseStack poseStack, Font font, Hover hover, float[] values,
            float min, float span, ChartMath.Axis axis, int x, int y, int w, int h,
            int lineLeft, int plotX, int plotY, int plotW, int plotH, int windowSeconds, double covered,
            String unit) {
        int n = values.length;
        if (n < 2) {
            return;
        }
        // 只在绘图区里响应（不含坐标轴/标签区）；窗口没填满时左侧留白没有采样点，也不响应
        if (hover.mouseX() < lineLeft || hover.mouseX() > plotX + plotW
                || hover.mouseY() < plotY || hover.mouseY() >= plotY + plotH) {
            return;
        }

        int index = ChartMath.nearestIndex(hover.mouseX(), lineLeft, plotX + plotW, n);
        float value = values[index];
        int px = Math.round(ChartMath.pointX(index, n, lineLeft, plotX + plotW));
        int py = valueToY(value, min, span, plotY, plotH);

        // 十字准线：1px，走 fill（内部就是 QUADS + POSITION_COLOR；
        // Mode.LINES 在 GUI 阶段的 1.19.2/1.20.1 都不渲染，别用）
        GuiComponent.fill(poseStack, px, plotY, px + 1, plotY + plotH, COL_HOVER_LINE);
        GuiComponent.fill(poseStack, plotX, py, plotX + plotW, py + 1, COL_HOVER_LINE);

        String valueLabel = axis.label(value);
        String timeLabel = ChartMath.formatRelative(timeAt(index, n, covered));

        // Y 轴左侧的悬停读数：会压到静态刻度就不画（"塞不下就只保留浮窗"）
        if (Math.abs(py - plotY) > 8 && Math.abs(py - (plotY + plotH / 2)) > 8
                && Math.abs(py - (plotY + plotH - 1)) > 8) {
            text(poseStack, font, valueLabel, x + 1, py - 4, COL_HOVER_TEXT);
        }

        // X 轴下方的时间：会压到静态时间刻度就不画
        int timeW = Math.round(font.width(timeLabel) * TEXT_SCALE);
        int timeX = px - timeW / 2;
        if (timeX >= plotX && timeX + timeW <= plotX + plotW
                && !overlapsTimeTicks(font, timeX, timeW, plotX, plotW, windowSeconds)) {
            text(poseStack, font, timeLabel, timeX, plotY + plotH + 1, COL_HOVER_TEXT);
        }

        // 跟随鼠标的浮窗
        drawTip(poseStack, font, hover, x, y, w, h, valueLabel, unit, timeLabel);
    }

    /** 第 {@code index} 个采样点相对"现在"的秒数（负值）。 */
    private static double timeAt(int index, int count, double coveredSeconds) {
        if (count <= 1) {
            return 0.0;
        }
        return -coveredSeconds * (count - 1 - index) / (count - 1.0);
    }

    /** 时间读数会不会和静态时间刻度（1/4、1/2、3/4 与右端 "0"）叠在一起。 */
    private static boolean overlapsTimeTicks(Font font, int labelX, int labelW, int plotX, int plotW,
            int windowSeconds) {
        for (int i = 1; i <= 3; i++) {
            int gx = plotX + plotW * i / 4;
            String tick = "-" + ChartMath.formatDuration((long) windowSeconds * (4 - i) / 4);
            int tickW = Math.round(font.width(tick) * TEXT_SCALE);
            int tickX = gx - tickW / 2;
            if (labelX < tickX + tickW && tickX < labelX + labelW) {
                return true;
            }
        }
        int zeroW = Math.round(font.width("0") * TEXT_SCALE);
        int zeroX = plotX + plotW - zeroW;
        return labelX < zeroX + zeroW && zeroX < labelX + labelW;
    }

    /**
     * 浮窗：半透明深底 + 细边框，跟随鼠标；靠近图表右/下边缘自动翻到另一侧，
     * 并夹在图表与屏幕的交集里（浮动子窗口可以被拖到屏幕外，所以要按屏幕再夹一次）。
     */
    private static void drawTip(PoseStack poseStack, Font font, Hover hover, int x, int y, int w, int h,
            String valueLabel, String unit, String timeLabel) {
        String line1 = unit == null || unit.isEmpty() ? valueLabel : valueLabel + " " + unit;
        String line2 = timeLabel;
        int textW = Math.max(font.width(line1), font.width(line2));
        int boxW = textW + 8;
        int boxH = 2 * TIP_LINE_H + 4;

        int bx = hover.mouseX() + TIP_GAP;
        int by = hover.mouseY() + TIP_GAP;
        if (bx + boxW > x + w) {
            bx = hover.mouseX() - TIP_GAP - boxW;
        }
        if (by + boxH > y + h) {
            by = hover.mouseY() - TIP_GAP - boxH;
        }
        // 先在图表坐标系里夹一次（图表本身在界面里），再换到屏幕坐标夹一次
        bx = ChartMath.clamp(bx, x + 2, x + w - boxW - 2);
        by = ChartMath.clamp(by, y + 2, y + h - boxH - 2);
        int absX = ChartMath.clamp(bx + hover.offsetX(), 2, hover.screenW() - boxW - 2);
        int absY = ChartMath.clamp(by + hover.offsetY(), 2, hover.screenH() - boxH - 2);
        bx = absX - hover.offsetX();
        by = absY - hover.offsetY();

        GuiComponent.fill(poseStack, bx, by, bx + boxW, by + boxH, COL_TIP_BG);
        GuiComponent.fill(poseStack, bx, by, bx + boxW, by + 1, COL_TIP_BORDER);
        GuiComponent.fill(poseStack, bx, by + boxH - 1, bx + boxW, by + boxH, COL_TIP_BORDER);
        GuiComponent.fill(poseStack, bx, by, bx + 1, by + boxH, COL_TIP_BORDER);
        GuiComponent.fill(poseStack, bx + boxW - 1, by, bx + boxW, by + boxH, COL_TIP_BORDER);

        font.draw(poseStack, line1, bx + 4, by + 3, COL_TIP_VALUE);
        font.draw(poseStack, line2, bx + 4, by + 3 + TIP_LINE_H, COL_TIP_TIME);
    }

    /** 以 {@value #TEXT_SCALE} 倍字号在 (x,y) 画一行小字。 */
    private static void text(PoseStack poseStack, Font font, String s, int x, int y, int color) {
        poseStack.pushPose();
        poseStack.translate(x, y, 0);
        poseStack.scale(TEXT_SCALE, TEXT_SCALE, 1f);
        font.draw(poseStack, s, 0, 0, color);
        poseStack.popPose();
    }

    /** 紧凑数值（单值）：1.2G / 45M / 7.8K / 123。实现见 {@link ChartMath#format(float)}。 */
    public static String format(float value) {
        return ChartMath.format(value);
    }

    /** 把秒数写成 45s / 10m / 1h。实现见 {@link ChartMath#formatDuration(long)}。 */
    public static String formatDuration(long seconds) {
        return ChartMath.formatDuration(seconds);
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
        float prevX = ChartMath.pointX(0, n, lineLeft, plotX + plotW);
        // +0.5：让线压在像素中心，而不是正好落在像素边界上
        float prevY = valueToYf(values[0], min, span, plotY, plotH) + 0.5f;
        for (int i = 1; i < n; i++) {
            float nextX = ChartMath.pointX(i, n, lineLeft, plotX + plotW);
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

    /** {@link #valueToY} 的浮点版本（线段顶点不取整，且不做像素取整）。 */
    private static float valueToYf(float value, float min, float span, int y, int h) {
        float t = (value - min) / span;
        float py = y + h - 1 - t * (h - 1);
        return Math.max(y, Math.min(y + h - 1, py));
    }
}
