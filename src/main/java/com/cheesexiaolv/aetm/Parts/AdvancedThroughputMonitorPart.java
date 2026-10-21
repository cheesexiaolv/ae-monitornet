package com.cheesexiaolv.aetm.Parts;

import com.cheesexiaolv.aetm.AetmConfig;
import com.cheesexiaolv.aetm.Logic.ThroughputHistory;
import com.cheesexiaolv.aetm.menu.AdvancedThroughputMonitorMenu;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.GenericStack;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.util.ConfigInventory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 高级吞吐量监控器。
 *
 * <p>与普通监控器一样沿用基类 watcher 追踪监控对象存量（便于高频采样，低成本）。
 * 新增一个"配置槽"（level-emitter 式）用于选择监控对象：右键打开 {@link AdvancedThroughputMonitorMenu} 的 GUI，
 * 在 GUI 的配置槽里放物品/流体即可设为监控对象。
 *
 * <p>无普通监控器的"锁定"机制（shift+右键同样打开 GUI）；流体的数量显示单位可切换 mB/B。
 */
public class AdvancedThroughputMonitorPart extends ThroughputMonitorPart implements IAdvancedThroughputMonitorHost {

    private final ConfigInventory config = ConfigInventory.configTypes(1, this::onConfigChanged);

    /** 速率历史（折线图用）。容量与采样间隔都来自配置文件。 */
    private final ThroughputHistory history = new ThroughputHistory(capacityFromConfig());

    /** 距离下一次采样的 tick 累计。 */
    private int sampleAccumulator;

    /** 折线图显示的时间窗口。 */
    private ThroughputHistory.Window chartWindow = ThroughputHistory.Window.M1;

    /** 折线图显示的量（储量 / 流速 / 流速加速度）。 */
    private ThroughputHistory.Metric chartMetric = ThroughputHistory.Metric.RATE;

    private boolean fluidUnitB;

    public AdvancedThroughputMonitorPart(IPartItem<?> partItem) {
        super(partItem);
    }

    private static int capacityFromConfig() {
        try {
            return AetmConfig.HISTORY_CAPACITY.get();
        } catch (Exception e) {
            return 3600;
        }
    }

    @Override
    public ConfigInventory getConfig() {
        return this.config;
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLast) {
        var result = super.tickingRequest(node, ticksSinceLast);

        // 按配置文件里的采样间隔记录（存量 + 间隔 tick），供折线图使用。
        //
        // ⚠️ 这里累加的是"实际经过的 tick"（ticksSinceLast），而零件能 tick 多快由基类
        // getTickingRequest 决定（现在 minTickRate 跟着采样间隔走）。interval=10 且零件每 10 tick
        // 被调一次时，本方法每个 tick 累加 10 → 恰好推一笔、累加器归零，不会多记也不会漏记；
        // 万一 AE2 稍晚一点调（diff 略大于 interval），推入的也是真实间隔，ThroughputHistory 能正确换算。
        if (getDisplayed() != null) {
            this.sampleAccumulator += Math.max(1, ticksSinceLast);
            int interval = samplingIntervalFromConfig();
            if (this.sampleAccumulator >= interval) {
                this.history.push(getAmount(), this.sampleAccumulator);
                this.sampleAccumulator = 0;
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 折线图：时间窗口 / 显示量
    // ------------------------------------------------------------------

    @Override
    public ThroughputHistory.Window getChartWindow() {
        return this.chartWindow;
    }

    @Override
    public void cycleChartWindow() {
        this.chartWindow = this.chartWindow.next();
        getHost().markForUpdate();
    }

    @Override
    public ThroughputHistory.Metric getChartMetric() {
        return this.chartMetric;
    }

    @Override
    public void cycleChartMetric() {
        this.chartMetric = this.chartMetric.next();
        getHost().markForUpdate();
    }

    @Override
    public String chartData() {
        return ThroughputHistory.encode(series(this.chartWindow, this.chartMetric, getRoutineTicks(), this.fluidUnitB));
    }

    /**
     * 按指定设置取曲线（不改动本零件自己的设置）。
     *
     * <p>聚合终端要用「行自己的窗口/显示量/单位」取曲线，所以把换算逻辑抽到这里：
     *
     * @param routineTicks 速率档位对应的 tick 数（终端把速率统一到它自己的档位）
     * @param largeUnit    是否换算成大单位（流体 B；{@code getAmountPerUnit()} 为 1 的类型无影响）
     */
    public ThroughputHistory.Series series(ThroughputHistory.Window window, ThroughputHistory.Metric metric,
            int routineTicks, boolean largeUnit) {
        var series = this.history.series(window, metric, routineTicks);
        float[] values = series.values();
        // 大单位换算：泛化按 key 类型的 getAmountPerUnit()（流体 1000，物品 1，其它类型各自定义）
        var displayed = getDisplayed();
        if (largeUnit && displayed != null && values.length > 0) {
            var type = displayed.getType();
            int perUnit = Math.max(1, type.getAmountPerUnit());
            if (perUnit > 1 && type.getUnitSymbol() != null) {
                float factor = 1f / perUnit;
                for (int i = 0; i < values.length; i++) {
                    values[i] *= factor;
                }
            }
        }
        return series;
    }

    @Override
    public boolean onPartActivate(Player player, InteractionHand hand, Vec3 pos) {
        if (!isClientSide()) {
            // 右键打开高级吞吐量监控器 GUI（服务端）
            MenuOpener.open(AdvancedThroughputMonitorMenu.TYPE, player, MenuLocators.forPart(this));
        }
        return true;
    }

    /**
     * 高级版无"锁定"机制：基类的 isLocked/setLocked 在此被永久禁用（恒为未锁定）。
     */
    @Override
    public boolean isLocked() {
        return false;
    }

    @Override
    public void setLocked(boolean locked) {
        // 忽略锁定设置
    }

    @Override
    public boolean onPartShiftActivate(Player player, InteractionHand hand, Vec3 pos) {
        // 高级版无"锁定"机制：shift+右键同样打开 GUI（不切换锁定）
        return onPartActivate(player, hand, pos);
    }

    @Override
    public void writeToNBT(CompoundTag data) {
        super.writeToNBT(data);
        data.putBoolean("fluidUnitB", this.fluidUnitB);
    }

    @Override
    public void readFromNBT(CompoundTag data) {
        super.readFromNBT(data);
        this.fluidUnitB = data.getBoolean("fluidUnitB");
        // 配置槽持久化：configuredItem 已由父类 writeToNBT/readFromNBT 持久化，
        // 读回后把配置槽的显示也同步成已恢复的监控对象。
        var displayed = getDisplayed();
        if (displayed != null) {
            this.config.setStack(0, new GenericStack(displayed, 1));
        }
    }

    @Override
    public boolean isFluidUnitB() {
        return this.fluidUnitB;
    }

    @Override
    public void toggleFluidUnit() {
        this.fluidUnitB = !this.fluidUnitB;
        // 面板上的单位/数值随之改变 → 绕过基类"内容没变就不广播"的收口，保证下一次一定广播
        invalidateBroadcastCache();
        getHost().markForSave();
        getHost().markForUpdate();
    }

    /**
     * 配置槽变化：更新监控对象，并重配 watcher。
     *
     * <p>AE2 12.9.9 的 {@code AbstractMonitorPart#configureWatchers()} 是 <b>private</b>，1.19.2 只能反射调；
     * <b>AE2 15.4.10 已把它改成 protected</b>（{@code AbstractMonitorPart.java:270}
     * {@code protected void configureWatchers()}），所以这里直接调用，去掉整段反射与失败路径。
     */
    private void onConfigChanged() {
        var key = this.config.getKey(0);
        setConfiguredItem(key);

        // 监控对象切换后 watcher 立即盯新物品（amount 也会随之刷新）
        configureWatchers();

        clearSampleCache();
        // 换了监控对象，历史曲线清零重来
        this.history.clear();
        getHost().markForUpdate();
    }
}
