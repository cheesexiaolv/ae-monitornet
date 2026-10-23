package com.cheesexiaolv.monitornet.Parts;

import com.cheesexiaolv.monitornet.MonitorNetConfig;
import com.cheesexiaolv.monitornet.Logic.ThroughputHistory;
import com.cheesexiaolv.monitornet.menu.AdvancedThroughputMonitorMenu;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.GenericStack;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.util.ConfigInventory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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

    /**
     * 配置槽的 NBT 名。AE2 自己的 level emitter 也是这么存的
     * （AE2 15.4.10 {@code appeng/parts/automation/StorageLevelEmitterPart.java:275-283} 的
     * {@code writeToNBT} -> {@code config.writeToChildTag(data, "config")}）。
     *
     * <p>以前配置槽是靠 AE2 的 {@code configuredItem} 反推重建的，自己没有任何持久状态；
     * 让它自带一份 NBT 后 {@code writeToNBT}/{@code readFromNBT} 才真正对称。
     * 本改动之前的存档没有这个 tag，仍由 {@link #readFromNBT} 里的 {@code configuredItem} 回填。
     */
    private static final String TAG_CONFIG = "monitornetConfig";

    /** 折线图设置（以前每次重启都回到默认值）。 */
    private static final String TAG_CHART_WINDOW = "chartWindow";
    private static final String TAG_CHART_METRIC = "chartMetric";

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

    /**
     * {@code true} 表示正在 {@link #readFromNBT} 恢复状态：读档期间配置槽的变更监听同样会触发，
     * 而"读档"不能把 chunk 标脏（否则用来判断要不要落盘的脏标记就失去意义了）。
     */
    private boolean restoring;

    public AdvancedThroughputMonitorPart(IPartItem<?> partItem) {
        super(partItem);
    }

    private static int capacityFromConfig() {
        try {
            return MonitorNetConfig.HISTORY_CAPACITY.get();
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
        markSettingsChanged();
    }

    @Override
    public ThroughputHistory.Metric getChartMetric() {
        return this.chartMetric;
    }

    @Override
    public void cycleChartMetric() {
        this.chartMetric = this.chartMetric.next();
        markSettingsChanged();
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
        data.putInt(TAG_CHART_WINDOW, this.chartWindow.ordinal());
        data.putInt(TAG_CHART_METRIC, this.chartMetric.ordinal());
        // 配置槽自己写一份 NBT，写法与 AE2 的 level emitter 完全一致
        this.config.writeToChildTag(data, TAG_CONFIG);
    }

    @Override
    public void readFromNBT(CompoundTag data) {
        this.restoring = true;
        try {
            // 恢复 AE2 的 configuredItem（外加基类那份自有 key，见父类）
            super.readFromNBT(data);

            this.fluidUnitB = data.getBoolean("fluidUnitB");
            // 旧存档（本改动之前）没有折线图设置：保持默认值，而不是被钳到 ordinal 0
            if (data.contains(TAG_CHART_WINDOW)) {
                this.chartWindow = ThroughputHistory.Window.byOrdinal(data.getInt(TAG_CHART_WINDOW));
            }
            if (data.contains(TAG_CHART_METRIC)) {
                this.chartMetric = ThroughputHistory.Metric.byOrdinal(data.getInt(TAG_CHART_METRIC));
            }

            // 1) 现在的存档：配置槽本身。只有列表里真的有一格时才读 ——
            //    列表缺失/为空时 readFromChildTag() 会清空配置槽并触发监听，
            //    把上面刚恢复的 key 又抹掉。
            if (data.contains(TAG_CONFIG, Tag.TAG_LIST)
                    && !data.getList(TAG_CONFIG, Tag.TAG_COMPOUND).isEmpty()) {
                this.config.readFromChildTag(data, TAG_CONFIG);
            }

            // 2) 配置槽还没落盘之前的存档：AE2 的 "configuredItem" 是唯一副本
            //    -> 回填进配置槽，让 GUI 重新显示监控对象。
            if (this.config.getKey(0) == null) {
                var displayed = getDisplayed();
                if (displayed != null) {
                    this.config.setStack(0, new GenericStack(displayed, 1));
                }
            }
        } finally {
            this.restoring = false;
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
        markSettingsChanged();
    }

    /** 持久化的 GUI 设置变了：宿主必须重新落盘，而不只是重新同步客户端。 */
    private void markSettingsChanged() {
        if (getHost() != null) {
            getHost().markForSave();
            getHost().markForUpdate();
        }
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

        // 读档恢复配置槽时变更监听也会触发；而没挂载宿主的零件（例如游戏内自检）
        // 也完全不该去碰 host。
        if (getHost() == null) {
            return;
        }

        setConfiguredItem(key);

        // 监控对象切换后 watcher 立即盯新物品（amount 也会随之刷新）
        configureWatchers();

        clearSampleCache();
        // 换了监控对象，历史曲线清零重来
        this.history.clear();

        // ⚠️ "重启后监控对象不见了"的根因：配置槽是持久数据，变更时必须把宿主标脏存档；
        // 只调 markForUpdate() 仅重新同步客户端。MC 1.20.1 停服时只写"脏"区块
        // （MinecraftServer#stopServer -> saveAllChunks(..., forced=false) ->
        //  ChunkMap#save 对 !chunk.isUnsaved() 直接 return），所以 key 只留在内存里，
        //  重进世界后零件就没有监控对象了。
        if (!this.restoring) {
            getHost().markForSave();
        }
        getHost().markForUpdate();
    }
}
