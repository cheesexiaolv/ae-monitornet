package com.cheesexiaolv.aetm.client.gui;

import com.cheesexiaolv.aetm.Logic.ThroughputHistory;
import com.cheesexiaolv.aetm.menu.AdvancedThroughputMonitorMenu;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;

/**
 * 高级吞吐量监控器客户端界面（AE2 样式）。
 *
 * <p>布局：上方读数与配置 → 一行自绘按钮（时间窗口 / 显示量 / 流体单位 / 采样档位）→ 中段折线图（带坐标轴）
 * → 下方玩家背包（交互必需）。
 */
public class AdvancedThroughputMonitorScreen extends AEBaseScreen<AdvancedThroughputMonitorMenu> {

    // 折线图区域（面板内相对坐标）
    private static final int CHART_X = 8;
    private static final int CHART_Y = 106;
    private static final int CHART_W = 160;
    private static final int CHART_H = 64;

    // 按钮行（两行，每行 2 个；左右各留 6px，避免"快突出去"）
    private static final int BUTTON_W = 80;
    private static final int BUTTON_H = 18;
    private static final int BUTTON_ROW1_Y = 64;
    private static final int BUTTON_ROW2_Y = 84;
    private static final int BUTTON_COL1_X = 6;
    private static final int BUTTON_COL2_X = 90;

    private FlatButton windowButton;
    private FlatButton metricButton;
    private FlatButton fluidUnitButton;
    private FlatButton unitButton;

    /** 解码后的曲线数据（只在 chartData 变化时重新解析）。 */
    private ThroughputHistory.Series chartSeries = ThroughputHistory.Series.EMPTY;
    private String lastChartData = null;

    public AdvancedThroughputMonitorScreen(AdvancedThroughputMonitorMenu menu, Inventory playerInventory,
            Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    protected void init() {
        super.init();

        // 第一行：显示量 / 时间窗口
        this.metricButton = addRenderableWidget(new FlatButton(
                this.leftPos + BUTTON_COL1_X, this.topPos + BUTTON_ROW1_Y, BUTTON_W, BUTTON_H,
                Component.translatable("gui.aetm.btn.metric", metricLabel(this.menu.chartMetric)),
                b -> this.menu.sendCycleChartMetric()));

        this.windowButton = addRenderableWidget(new FlatButton(
                this.leftPos + BUTTON_COL2_X, this.topPos + BUTTON_ROW1_Y, BUTTON_W, BUTTON_H,
                Component.translatable("gui.aetm.btn.window", this.menu.chartWindow),
                b -> this.menu.sendCycleChartWindow()));

        // 第二行：速率档位 / 流体单位
        this.unitButton = addRenderableWidget(new FlatButton(
                this.leftPos + BUTTON_COL1_X, this.topPos + BUTTON_ROW2_Y, BUTTON_W, BUTTON_H,
                Component.translatable("gui.aetm.btn.unit", this.menu.rateUnit),
                b -> this.menu.sendCycleRoutine()));

        this.fluidUnitButton = addRenderableWidget(new FlatButton(
                this.leftPos + BUTTON_COL2_X, this.topPos + BUTTON_ROW2_Y, BUTTON_W, BUTTON_H,
                Component.translatable("gui.aetm.btn.fluid", this.menu.fluidUnitB ? "B" : "mB"),
                b -> this.menu.sendToggleFluidUnit()));
    }

    private static Component metricLabel(int ordinal) {
        return Component.translatable("gui.aetm.metric."
                + ThroughputHistory.Metric.byOrdinal(ordinal).key);
    }

    @Override
    public void drawBG(PoseStack poseStack, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(poseStack, offsetX, offsetY, mouseX, mouseY, partialTicks);

        // 面板底、槽框、图表凹槽都已烘进贴图（aetm_monitor.png），这里只画曲线
        ThroughputChart.draw(poseStack, this.font,
                offsetX + CHART_X, offsetY + CHART_Y, CHART_W, CHART_H,
                this.chartSeries, this.menu.chartWindowSeconds, chartUnit(),
                ThroughputHistory.Metric.byOrdinal(this.menu.chartMetric) != ThroughputHistory.Metric.AMOUNT);
    }

    /** 纵轴单位：数值单位（由 key 类型推导）+ 档位单位；加速度再除以秒。 */
    private String chartUnit() {
        String valueUnit = this.menu.valueUnit;
        return switch (ThroughputHistory.Metric.byOrdinal(this.menu.chartMetric)) {
            case AMOUNT -> valueUnit;
            case RATE -> valueUnit + this.menu.rateUnit;
            case ACCELERATION -> valueUnit + this.menu.rateUnit + "/s";
        };
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        if (!this.menu.chartData.equals(this.lastChartData)) {
            this.lastChartData = this.menu.chartData;
            this.chartSeries = ThroughputHistory.decode(this.lastChartData);
        }

        var watchedName = this.menu.displayedItemName.isEmpty()
                ? Component.translatable("gui.aetm.none")
                : Component.literal(this.menu.displayedItemName);
        this.setTextContent("watched_text", Component.translatable("gui.aetm.watched", watchedName));
        this.setTextContent("amount_text", Component.translatable("gui.aetm.amount", this.menu.amountText));
        this.setTextContent("rate_text",
                Component.translatable("gui.aetm.rate", this.menu.rateText + this.menu.rateUnit));

        if (this.windowButton != null) {
            this.windowButton.setMessage(Component.translatable("gui.aetm.btn.window", this.menu.chartWindow));
        }
        if (this.metricButton != null) {
            this.metricButton.setMessage(
                    Component.translatable("gui.aetm.btn.metric", metricLabel(this.menu.chartMetric)));
        }
        if (this.unitButton != null) {
            this.unitButton.setMessage(Component.translatable("gui.aetm.btn.unit", this.menu.rateUnit));
        }
        if (this.fluidUnitButton != null) {
            this.fluidUnitButton.visible = this.menu.fluidDisplayed;
            this.fluidUnitButton.setMessage(
                    Component.translatable("gui.aetm.btn.fluid", this.menu.fluidUnitB ? "B" : "mB"));
        }
    }
}
