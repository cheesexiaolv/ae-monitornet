package com.cheesexiaolv.aetm.menu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cheesexiaolv.aetm.Logic.TerminalSettings;
import com.cheesexiaolv.aetm.Logic.TerminalSettingsNbt;
import com.cheesexiaolv.aetm.Logic.ThroughputHistory;
import com.cheesexiaolv.aetm.Parts.AdvancedThroughputMonitorPart;
import com.cheesexiaolv.aetm.Parts.IMonitorRef;
import com.cheesexiaolv.aetm.Parts.ITerminalSettingsHost;
import com.cheesexiaolv.aetm.Parts.MonitorRefs;
import com.cheesexiaolv.aetm.Parts.ThroughputTerminalPart;
import com.cheesexiaolv.aetm.network.AetmNetwork;
import com.cheesexiaolv.aetm.network.TerminalRow;
import com.cheesexiaolv.aetm.network.TerminalRowsPacket;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEKey;
import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;

/**
 * 存储监控终端的服务端菜单。
 *
 * <p>工作方式（参照 AE2 自己的样板管理终端）：
 * <ol>
 *   <li>每个 tick 遍历 {@code grid.getMachineClasses()}，把<b>本网络的吞吐量监控器</b>枚举出来
 *       —— 包括 aetm 自己的（普通/高级）以及装了 <b>AdvancedAE</b> 时它那边的监控器
 *       （兼容桥见 {@link MonitorRefs}/{@link IMonitorRef}；离线的、区块没加载的自然不在集合里）；</li>
 *   <li><b>按被监控的 key 合并成一行</b>——同一网络里多个监控器盯同一个 key 时读到的是同一份数据，
 *       所以合并无损，多出来的监控器只作为「来源」列出；</li>
 *   <li>行数据走自建包（结构变化发全量、否则只发数值），界面状态走 {@code @GuiSync}。</li>
 * </ol>
 *
 * <p>为什么不给每个监控器改设置：采样间隔是服务端全局配置；终端里的「窗口 / 显示量 / 单位 / 速率档」
 * 只是显示设置，存在终端零件上（全局默认 + 单行覆盖），不会去动别的零件。
 */
public class ThroughputTerminalMenu extends AEBaseMenu {

    /** 客户端历史容量：窗口式界面要同时画多条曲线，取够用又不占内存的值。 */
    private static final int CLIENT_HISTORY_CAPACITY = 1200;

    /** 「定位」高亮持续多少 tick（200 tick = 10 秒：够玩家边走边找那台机器）。 */
    private static final int LOCATE_HIGHLIGHT_TICKS = 200;

    public static final MenuType<ThroughputTerminalMenu> TYPE = MenuTypeBuilder
            .create(ThroughputTerminalMenu::new, ThroughputTerminalPart.class)
            .withMenuTitle(host -> Component.translatable("item.aetm.throughput_terminal"))
            .build("throughput_terminal");

    // ------------------------------------------------------------------
    // 同步给客户端的界面状态（@GuiSync 只支持标量 / String / 枚举）
    // ------------------------------------------------------------------

    /** 视图：0=文字列表 1=列表+折线图。 */
    @GuiSync(1)
    public int viewMode = ThroughputTerminalPart.VIEW_TEXT;
    /** 选中的行序号（-1 = 未选中）。 */
    @GuiSync(2)
    public int selectedRow = -1;
    @GuiSync(3)
    public String selectedName = "";
    /** 选中行的曲线数据（编码格式见 {@link ThroughputHistory#encode}）。 */
    @GuiSync(4)
    public String chartData = "";
    @GuiSync(5)
    public int chartWindowSeconds = 60;
    /** 选中行的纵轴单位。 */
    @GuiSync(6)
    public String chartUnit = "";
    @GuiSync(7)
    public boolean selectedHasHistory = false;
    @GuiSync(8)
    public int chartMetric = 1;

    /** 当前行数（供界面显示 / 滚动条范围）。 */
    @GuiSync(13)
    public int rowCount = 0;

    /** 终端尺寸档位（TerminalStyle ordinal：0=短 1=中 2=长 3=满；按终端各自记，默认满）。 */
    @GuiSync(9)
    public int terminalStyle = TerminalSettingsNbt.DEFAULT_STYLE;

    /**
     * 终端是否连上了网络。
     *
     * <p>有线版：零件没通电/没频道时为 false；无线版：不在无线访问点范围内时为 false
     * （AE2 的 {@code WirelessTerminalMenuHost#getActionableNode()} 就靠这个判定）。
     * 为 false 时列表必然是空的，界面要明确说出来，否则玩家只会看到"0 行"。
     */
    @GuiSync(20)
    public boolean networkAvailable = true;

    /** 选中行的有效档位（供「本行」按钮组显示）。 */
    @GuiSync(14)
    public int rowWindow = 0;
    @GuiSync(15)
    public int rowMetric = 1;
    @GuiSync(16)
    public boolean rowLargeUnit = false;
    @GuiSync(17)
    public int rowRoutine = 1;
    /** 选中行的 key 是否有大小单位之分（流体为 true，物品为 false）。 */
    @GuiSync(18)
    public boolean rowHasUnit = false;
    @GuiSync(19)
    public boolean rowSelected = false;

    /** 客户端：解码后的行数据（屏幕每帧读它）。 */
    private List<TerminalRow> clientRows = List.of();

    /**
     * 客户端：每行一份自己的历史，<b>用行的 key 作键</b>（不是行序号）。
     *
     * <p>窗口式界面要同时显示多条曲线，靠服务端算再逐行下发太重；改成服务端每秒只发行数值，
     * 客户端用与服务端同一个 {@link ThroughputHistory} 记账 —— 于是每个窗口的窗口长度/显示量/
     * 单位切换全部本地即时生效，不再走服务端往返。
     *
     * <p>⚠️ <b>为什么必须按 key 而不是按行序号存</b>：改某一行的档位/单位时，那些设置是随行数据下发的，
     * 所以服务端必须重发<b>全量包</b>（{@code lastStructure = null}）。如果历史按行序号存、收到全量包就整批
     * 重建，曲线会"从头开始"——表现就是点一下子窗口里的按钮，曲线被清空重画。按 key 复用已有历史后，
     * 全量包只重建<b>行数据</b>，历史一条都不会丢。
     *
     * <p>{@code AEKey} 的子类（{@code AEItemKey}/{@code AEFluidKey}）都实现了 equals/hashCode，
     * 可以安全作 Map 键。
     */
    private final Map<AEKey, ThroughputHistory> clientHistories = new HashMap<>();

    /**
     * 客户端：<b>服务端算好的曲线</b>（键同样是行的 key）。
     *
     * <p>浮动子窗口画图优先用它 —— 客户端自记的那份只作兜底：刚开窗时它只有一两个样本，
     * 直接拿去画会从 0 拉出一条夸张的斜线；服务端那份是完整采样历史，开窗即准。
     */
    private final Map<AEKey, String> clientSeries = new HashMap<>();

    /** 服务端的采样间隔（全量包带来），客户端记账要用。 */
    private int clientSampleIntervalTicks = 20;

    /** 图线刷新间隔（tick，全量包带来）：= max(1, round(采样间隔 / 配置的 chartRefreshMultiplier))。 */
    private int clientChartRefreshIntervalTicks = 20;

    /** 显示设置宿主：有线版是面板零件，无线版是物品 NBT 上的设置对象。 */
    private final ITerminalSettingsHost settings;

    /** 服务端：权威行（带零件引用，用于读历史曲线）。 */
    private final List<ServerRow> serverRows = new ArrayList<>();

    /** 上一次发出去的结构指纹（null = 还没发过全量）。 */
    @Nullable
    private String lastStructure;
    private long[] lastAmounts = new long[0];
    private double[] lastRates = new double[0];

    /** 曲线重算节流（每 4 tick 一次就够）。 */
    private int chartTickCounter;

    private static final Comparator<ServerRow> ROW_ORDER = Comparator
            .comparing((ServerRow row) -> row.key.getDisplayName().getString(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(row -> row.key.getId().toString());

    public ThroughputTerminalMenu(int id, Inventory playerInventory, ThroughputTerminalPart part) {
        this(TYPE, id, playerInventory, part, part);
    }

    /**
     * 有线/无线共用的构造：{@code menuHost} 交给 AE2（决定网络节点与权限），
     * {@code settings} 只负责显示设置的读写。
     */
    protected ThroughputTerminalMenu(MenuType<?> type, int id, Inventory playerInventory, Object menuHost,
            ITerminalSettingsHost settings) {
        super(type, id, playerInventory, menuHost);
        this.settings = settings;

        // 玩家背包：终端本身没有槽，但保留背包才像"终端"
        createPlayerInventorySlots(playerInventory);

        // 视图切换
        registerClientAction("setView", Integer.class, this::setView);
        registerClientAction("selectRow", Integer.class, this::selectRow);
        registerClientAction("setTerminalStyle", Integer.class, this::setTerminalStyle);
        registerClientAction("setAllRoutines", Integer.class, this::setAllRoutines);
        // 档位只作用于选中行（图线视图一次只看一行曲线，所以不需要全局档位）
        // 单行档位（只改选中行）
        registerClientAction("rowWindow", this::cycleRowWindow);
        registerClientAction("rowMetric", this::cycleRowMetric);
        registerClientAction("rowUnit", this::toggleRowUnit);
        registerClientAction("rowRoutine", this::cycleRowRoutine);
        // 浮动子窗口要画曲线时按需索取"该行服务端算好的那份"
        registerClientAction("requestRowSeries", Integer.class, this::requestRowSeries);
        // 浮动子窗口的「定位」：在世界里描边该行的第一个来源监控器
        registerClientAction("locateRow", Integer.class, this::locateRow);

        this.viewMode = settings.getViewMode();
        this.terminalStyle = settings.getTerminalStyle();
    }

    // ------------------------------------------------------------------
    // 每 tick 刷新（服务端）
    // ------------------------------------------------------------------

    @Override
    public void broadcastChanges() {
        if (isClientSide()) {
            return;
        }

        refreshRows();
        refreshSelection();
        if (++this.chartTickCounter >= 4) {
            this.chartTickCounter = 0;
            refreshChart();
        }

        super.broadcastChanges();
        pushRows();
    }

    /** 枚举本网络的监控器并按 key 合并成行。 */
    private void refreshRows() {
        IGrid grid = getGrid();
        this.serverRows.clear();
        this.networkAvailable = grid != null;

        if (grid != null) {
            Map<AEKey, ServerRow> byKey = new LinkedHashMap<>();
            collectMonitors(grid, byKey);
            this.serverRows.addAll(byKey.values());
            // 稳定排序：客户端用行序号索引，顺序必须两边一致
            this.serverRows.sort(ROW_ORDER);
        }

        for (var row : this.serverRows) {
            row.computeValues();
        }
        this.rowCount = this.serverRows.size();
    }

    /**
     * 收集本网络上的所有吞吐量监控器：
     * <b>aetm 自己的</b>（普通 + 高级）以及<b>装了 AdvancedAE 时它的</b>监控器。
     *
     * <p>⚠️ 必须像 AE2 的样板管理终端那样<b>遍历 {@code getMachineClasses()} 再按可赋值性筛选</b>：
     * AE2 的 {@code Grid} 是按节点宿主的<b>运行时类</b>登记机器的（{@code Grid.java:105}
     * {@code gridNode.getOwner().getClass()}），所以直接
     * {@code grid.getActiveMachines(ThroughputMonitorPart.class)} 只会命中普通监控器，
     * 派生出来的高级监控器（{@code AdvancedThroughputMonitorPart}）会漏掉。
     *
     * <p>AAE 的部件是另一个包里的另一个类（不认识我们的基类），所以这里改走
     * {@link MonitorRefs#isMonitorClass} + {@link MonitorRefs#of}：它内部对 AAE 那侧
     * 只在"装了该 mod"时才反射解析，并统一包装成 {@link IMonitorRef} 给 {@link ServerRow} 用。
     */
    private void collectMonitors(IGrid grid, Map<AEKey, ServerRow> byKey) {
        for (var machineClass : grid.getMachineClasses()) {
            if (!MonitorRefs.isMonitorClass(machineClass)) {
                continue;
            }
            collectFromClass(grid, machineClass, byKey);
        }
    }

    /**
     * 把一个具体零件类下的所有活动零件收进行表。
     *
     * <p>这里用的是"原始 Class + getActiveMachines(raw)"：AAE 的类我们拿不到泛型类型，
     * 也不能 import，所以统一按 {@code Class<?>} 处理，再交给 {@link MonitorRefs#of} 包装。
     */
    private void collectFromClass(IGrid grid, Class<?> monitorClass, Map<AEKey, ServerRow> byKey) {
        for (var monitor : grid.getActiveMachines(monitorClass)) {
            IMonitorRef ref = MonitorRefs.of(monitor);
            if (ref == null) {
                continue;
            }
            AEKey key = ref.displayed();
            if (key == null) {
                continue;
            }
            byKey.computeIfAbsent(key, ServerRow::new).addMonitor(ref);
        }
    }

    /** 选中行的档位同步（"本行"按钮组显示用）。 */
    private void refreshSelection() {
        var row = selectedServerRow();
        if (row == null) {
            this.rowSelected = false;
            this.rowHasUnit = false;
            return;
        }
        var setting = this.settings.getSetting(row.key);
        this.rowSelected = true;
        this.rowWindow = setting.window().ordinal();
        // 显示量归一：加速度已从界面撤掉，老存档里存着 ACCELERATION 也按流速处理
        this.rowMetric = displayMetric(setting).ordinal();
        this.rowLargeUnit = setting.largeUnit();
        this.rowRoutine = setting.routineOrdinal();
        this.rowHasUnit = hasUnitChoice(row.key);
        this.selectedName = row.key.getDisplayName().getString();


    }

    /** 选中行的曲线（服务端算好再同步，客户端只负责画）。 */
    private void refreshChart() {


        var row = selectedServerRow();
        if (row == null) {
            this.chartData = "";
            this.selectedHasHistory = false;
            this.selectedName = "";
            return;
        }

        var setting = this.settings.getSetting(row.key);
        var metric = displayMetric(setting);
        this.chartWindowSeconds = setting.window().seconds;
        this.chartMetric = metric.ordinal();
        this.chartUnit = unitFor(row.key, setting, metric);
        this.selectedName = row.key.getDisplayName().getString();

        var history = row.historySource;
        if (history == null) {
            this.chartData = "";
            this.selectedHasHistory = false;
            return;
        }

        var series = history.series(setting.window(), metric,
                ThroughputTerminalPart.routineTicks(setting.routineOrdinal()), setting.largeUnit());
        this.chartData = ThroughputHistory.encode(series);
        this.selectedHasHistory = true;
    }

    // ------------------------------------------------------------------
    // 发包：结构变化发全量，否则只发数值
    // ------------------------------------------------------------------

    private void pushRows() {
        if (!(getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        long[] amounts = new long[this.serverRows.size()];
        double[] rates = new double[this.serverRows.size()];
        for (int i = 0; i < this.serverRows.size(); i++) {
            amounts[i] = this.serverRows.get(i).amount;
            rates[i] = this.serverRows.get(i).ratePerTick;
        }

        String structure = structureSignature();
        boolean structureChanged = !structure.equals(this.lastStructure);
        boolean valuesChanged = !Arrays.equals(amounts, this.lastAmounts) || !Arrays.equals(rates, this.lastRates);
        if (!structureChanged && !valuesChanged) {
            return;
        }

        this.lastStructure = structure;
        this.lastAmounts = amounts;
        this.lastRates = rates;

        if (structureChanged) {
            var rows = new ArrayList<TerminalRow>(this.serverRows.size());
            for (var row : this.serverRows) {
                rows.add(row.toTerminalRow(this.settings));
            }
            AetmNetwork.sendToPlayer(player,
                    TerminalRowsPacket.full(rows, sampleIntervalFromConfig(), chartRefreshIntervalFromConfig()));
        } else {
            AetmNetwork.sendToPlayer(player, TerminalRowsPacket.values(amounts, rates));
        }
    }

    /** 服务端采样间隔（客户端要按同一个节奏记账）。 */
    private static int sampleIntervalFromConfig() {
        try {
            return Math.max(1, com.cheesexiaolv.aetm.AetmConfig.SAMPLING_INTERVAL_TICKS.get());
        } catch (Exception e) {
            return 20;
        }
    }

    /**
     * 图线刷新间隔（tick）= {@code max(1, round(采样间隔 / chartRefreshMultiplier))}。
     *
     * <p>{@code chartRefreshMultiplier} 就是配置里的「图线绘制频率」：1.0 = 与服务端采样同速，
     * 0.5 = 每两个采样周期刷一次（默认）。算好后随全量包下发给客户端（单机/联机一致）。
     */
    private static int chartRefreshIntervalFromConfig() {
        int sampling = sampleIntervalFromConfig();
        try {
            double multiplier = com.cheesexiaolv.aetm.AetmConfig.CHART_REFRESH_MULTIPLIER.get();
            if (multiplier <= 0.0) {
                multiplier = 0.5;
            }
            return Math.max(1, (int) Math.round(sampling / multiplier));
        } catch (Exception e) {
            return Math.max(1, sampling * 2);
        }
    }

    /** 结构指纹：key + 来源零件身份 + 设置（设置变了就重发全量，客户端才能刷新每行的单位/档位）。 */
    private String structureSignature() {
        var sb = new StringBuilder();
        for (var row : this.serverRows) {
            var setting = this.settings.getSetting(row.key);
            sb.append(row.key.getId()).append('/').append(row.key.getType().getId()).append('|')
                    .append(row.monitors.size()).append('|')
                    .append(setting.largeUnit() ? 'B' : 'b').append(setting.routineOrdinal())
                    .append('|');
            for (var monitor : row.monitors) {
                sb.append(System.identityHashCode(monitor)).append(',');
            }
            sb.append(';');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 客户端收到的行数据
    // ------------------------------------------------------------------

    /** 客户端：应用服务端推来的行数据（同时喂给每行自己的历史）。 */
    public void applyRowsPacket(TerminalRowsPacket message) {
        if (message.mode() == TerminalRowsPacket.MODE_FULL) {
            this.clientRows = message.rows();
            this.clientSampleIntervalTicks = Math.max(1, message.sampleIntervalTicks());
            this.clientChartRefreshIntervalTicks = Math.max(1, message.chartRefreshIntervalTicks());

            // ⚠️ 全量包只重建"行数据"，历史按 key 复用：设置变更（档位/单位/窗口/显示量）也必须走全量包，
            //    若在这里重建历史，曲线就会在每次点按钮时被清空 —— 这正是"点按钮曲线从头开始"的根因。
            for (var row : message.rows()) {
                var history = this.clientHistories.computeIfAbsent(row.key(),
                        key -> new ThroughputHistory(CLIENT_HISTORY_CAPACITY));
                history.push(row.amount(), this.clientSampleIntervalTicks);
            }

            // 行消失时把它的历史一起丢掉，避免 Map 无限膨胀（曲线缓存同理）
            this.clientHistories.keySet().removeIf(key -> !containsKey(message.rows(), key));
            this.clientSeries.keySet().removeIf(key -> !containsKey(message.rows(), key));
        } else if (message.mode() == TerminalRowsPacket.MODE_SERIES) {
            // 服务端算好的曲线：按行的 key 存下来（行序号只在收包这一刻有意义）
            int index = message.seriesRowIndex();
            if (index >= 0 && index < this.clientRows.size()) {
                this.clientSeries.put(this.clientRows.get(index).key(), message.seriesData());
            }
        } else if (message.mode() == TerminalRowsPacket.MODE_HIGHLIGHT) {
            // 「定位」高亮不走菜单（由 TerminalClientPackets 直接登记到静态的 ClientHighlights），
            // 这里只为兜底不做事：万一还有别的调用方把高亮包塞进来，忽略即可。
            return;
        } else {
            // 数值包按行序号对齐：结构没变，只替换存量/速率
            var updated = new ArrayList<TerminalRow>(this.clientRows.size());
            for (int i = 0; i < this.clientRows.size(); i++) {
                var old = this.clientRows.get(i);
                long amount = i < message.amounts().length ? message.amounts()[i] : old.amount();
                double rate = i < message.rates().length ? message.rates()[i] : old.ratePerTick();
                updated.add(new TerminalRow(old.key(), amount, rate, old.hasHistory(), old.largeUnit(),
                        old.routineOrdinal(), old.windowOrdinal(), old.metricOrdinal(), old.sources()));
            }
            this.clientRows = List.copyOf(updated);

            // 每行自己的历史：按同一个采样间隔记一笔（曲线由客户端自己算）。
            // 按 key 取历史 —— 行序即便变化也不会串行。
            for (int i = 0; i < this.clientRows.size(); i++) {
                var history = this.clientHistories.get(this.clientRows.get(i).key());
                if (history == null) {
                    continue;
                }
                long amount = i < message.amounts().length ? message.amounts()[i] : 0;
                history.push(amount, this.clientSampleIntervalTicks);
            }
        }
    }

    /** 行列表里是否已经有这个 key。 */
    private static boolean containsKey(List<TerminalRow> rows, AEKey key) {
        for (var row : rows) {
            if (row.key().equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 客户端：取某一行的历史（没有就返回 null）。
     *
     * <p>窗口式界面用它自己算曲线：{@code history.series(window, metric, routineTicks)}。
     * 内部按<b>行的 key</b> 查表，所以服务端重发全量包不会让曲线归零。
     */
    @Nullable
    public ThroughputHistory historyFor(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= this.clientRows.size()) {
            return null;
        }
        return this.clientHistories.get(this.clientRows.get(rowIndex).key());
    }

    /**
     * 客户端：取某一行要画的曲线 —— <b>优先用服务端算好的那份</b>（见 {@link #requestRowSeries}）。
     *
     * <p>服务端还没回过（或该行没有高级监控器）时，退回客户端自记的历史现算一份：
     * 那份刚开窗时点很少、曲线会有点夸张，但至少不会空着。
     *
     * @return 一定不为 null（都没有就是空 Series）
     */
    public ThroughputHistory.Series seriesFor(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= this.clientRows.size()) {
            return ThroughputHistory.Series.EMPTY;
        }
        TerminalRow row = this.clientRows.get(rowIndex);

        String encoded = this.clientSeries.get(row.key());
        if (encoded != null && !encoded.isEmpty()) {
            return ThroughputHistory.decode(encoded);
        }

        // 兜底：客户端自记的历史（显示量已归一，加速度按流速算）
        ThroughputHistory fallback = this.clientHistories.get(row.key());
        if (fallback == null) {
            return ThroughputHistory.Series.EMPTY;
        }
        return fallback.series(ThroughputHistory.Window.byOrdinal(row.windowOrdinal()),
                displayMetric(ThroughputHistory.Metric.byOrdinal(row.metricOrdinal())),
                ThroughputTerminalPart.routineTicks(row.routineOrdinal()));
    }

    /** 客户端：服务端的采样间隔（tick）。 */
    public int sampleIntervalTicks() {
        return this.clientSampleIntervalTicks;
    }

    /**
     * 客户端：图线刷新间隔（tick）——屏幕按它节流"请求窗口曲线"。
     *
     * <p>由服务端在全量包里下发（{@code max(1, round(采样间隔 / 配置的 chartRefreshMultiplier))}），
     * 所以单机和联机行为一致，客户端不需要读配置。
     */
    public int chartRefreshIntervalTicks() {
        return this.clientChartRefreshIntervalTicks;
    }

    /** 客户端：屏幕每帧读它。 */
    public List<TerminalRow> getRows() {
        return this.clientRows;
    }

    // ------------------------------------------------------------------
    // 客户端动作（服务端执行）
    // ------------------------------------------------------------------

    private void setView(Integer mode) {
        int value = mode == null ? ThroughputTerminalPart.VIEW_TEXT : mode;
        this.settings.setViewMode(value);
        this.viewMode = this.settings.getViewMode();
    }

    /**
     * 全局速率单位：把**所有行**的速率档位统一成一个值（0=t 1=s 2=m 3=10m）。
     *
     * <p>图线视图/子窗口里的"显示量、时间窗、单位"都是每行各自的，只有速率单位是全局的
     * （用户明确要求：全局按钮仅切换速度单位）。
     */
    private void setAllRoutines(Integer routineOrdinal) {
        int value = routineOrdinal == null ? 1 : Math.max(0, Math.min(3, routineOrdinal));
        for (var row : this.serverRows) {
            var setting = this.settings.getSetting(row.key);
            this.settings.setRowSetting(row.key, new TerminalSettings(
                    setting.window(), setting.metric(), setting.largeUnit(), value));
        }
        this.lastStructure = null; // 每行的档位都变了 → 强制重发全量
        refreshSelection();
        refreshChart();
    }

    private void setTerminalStyle(Integer styleOrdinal) {
        int value = styleOrdinal == null ? TerminalSettingsNbt.DEFAULT_STYLE : styleOrdinal;
        this.settings.setTerminalStyle(value);
        this.terminalStyle = this.settings.getTerminalStyle();
    }

    private void selectRow(Integer index) {
        int value = index == null ? -1 : index;
        this.selectedRow = value >= 0 && value < this.serverRows.size() ? value : -1;
        refreshSelection();
        refreshChart();
    }

    /**
     * 浮动子窗口请求"这一行服务端算好的曲线"。
     *
     * <p>用<b>该行自己的设置</b>算：窗口长度 / 显示量 / 速率档 / 大单位 —— 与客户端行数据里带的那份一致，
     * 所以点按钮改档位之后立刻再请求一次就能拿到新档位的曲线。
     *
     * <p>曲线源是该行的 {@link AdvancedThroughputMonitorPart}（{@link ServerRow#historySource}）：
     * 没有（该行只有普通监控器）就回一个空串，客户端会退回自己记的那份兜底。
     */
    private void requestRowSeries(Integer rowIndex) {
        if (!(getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        int index = rowIndex == null ? -1 : rowIndex;
        String encoded = "";
        if (index >= 0 && index < this.serverRows.size()) {
            var row = this.serverRows.get(index);
            var historySource = row.historySource;
            if (historySource != null) {
                var setting = this.settings.getSetting(row.key);
                var series = historySource.series(setting.window(), setting.metric(),
                        ThroughputTerminalPart.routineTicks(setting.routineOrdinal()), setting.largeUnit());
                encoded = ThroughputHistory.encode(series);
            }
        }
        AetmNetwork.sendToPlayer(player, TerminalRowsPacket.series(index, encoded));
    }



    /**
     * 浮动子窗口的「定位」：把该行<b>第一个来源监控器</b>的方块坐标发给客户端去描边。
     *
     * <p>机制取自 ExtendedAE 的样板管理终端定位按钮：服务端解析目标（这里是
     * {@link TerminalRow.Source} 列表的第一个，与服务端 {@code MONITOR_ORDER} 的排序一致），
     * 把坐标 + 维度 + 持续时长发给客户端，客户端再调自己的渲染钩子画描边
     * （见 {@code ClientHighlights}）。<b>只借机制</b>：EAE 那套箭头/图标一律不抄。
     */
    private void locateRow(Integer rowIndex) {
        if (!(getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        int index = rowIndex == null ? -1 : rowIndex;
        if (index < 0 || index >= this.serverRows.size()) {
            return;
        }
        var row = this.serverRows.get(index);
        if (row.monitors.isEmpty()) {
            return;
        }
        // 与悬停提示里的来源列表同序（ServerRow.computeValues 已按 维度→x→y→z 排好）
        var monitor = row.monitors.get(0);
        var pos = monitor.pos();
        var dimension = monitor.dimension();
        AetmNetwork.sendToPlayer(player,
                TerminalRowsPacket.highlight(index, pos, dimension == null ? "" : dimension.toString(),
                        LOCATE_HIGHLIGHT_TICKS));
    }

    private void cycleRowWindow() {
        updateSelectedSetting(setting -> setting.cycleWindow());
    }

    private void cycleRowMetric() {
        updateSelectedSetting(setting -> setting.cycleMetric());
    }

    private void toggleRowUnit() {
        updateSelectedSetting(setting -> setting.toggleUnit());
    }

    private void cycleRowRoutine() {
        updateSelectedSetting(setting -> setting.cycleRoutine());
    }

    private void updateSelectedSetting(java.util.function.UnaryOperator<TerminalSettings> change) {
        var row = selectedServerRow();
        if (row == null) {
            return;
        }
        this.settings.setRowSetting(row.key, change.apply(this.settings.getSetting(row.key)));
        this.lastStructure = null;
        refreshSelection();
        refreshChart();
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    @Nullable
    private ServerRow selectedServerRow() {
        if (this.selectedRow < 0 || this.selectedRow >= this.serverRows.size()) {
            return null;
        }
        return this.serverRows.get(this.selectedRow);
    }

    @Nullable
    private IGrid getGrid() {
        IActionHost actionHost = getActionHost();
        if (actionHost != null) {
            IGridNode node = actionHost.getActionableNode();
            if (node != null && node.isActive()) {
                return node.getGrid();
            }
        }
        return null;
    }

    /** 该 key 是否有大小单位之分（与菜单里的显示推导保持一致）。 */
    private static boolean hasUnitChoice(AEKey key) {
        var type = key.getType();
        return type.getUnitSymbol() != null && type.getAmountPerUnit() > 1;
    }

    /**
     * 界面上的"显示量"：<b>加速度已经从界面撤掉了</b>，有效值只可能是储量 / 流速。
     *
     * <p>{@link ThroughputHistory.Metric} 枚举本身保留（老存档里可能存着 ACCELERATION，删枚举会破坏兼容），
     * 这里把它<b>统一归一成 RATE</b>：读数、按钮文字、曲线三处都用同一个值，
     * 就不会出现"按钮显示加速、按了却没反应"这种怪状。
     */
    public static ThroughputHistory.Metric displayMetric(ThroughputHistory.Metric metric) {
        return metric == ThroughputHistory.Metric.ACCELERATION ? ThroughputHistory.Metric.RATE : metric;
    }

    /** 某个 {@link TerminalSettings} 的有效显示量（已归一）。 */
    private static ThroughputHistory.Metric displayMetric(TerminalSettings setting) {
        return displayMetric(setting.metric());
    }

    /** 纵轴 / 速率单位（由 key 类型与档位推导；{@code metric} 传归一后的显示量）。 */
    private static String unitFor(AEKey key, TerminalSettings setting, ThroughputHistory.Metric metric) {
        var type = key.getType();
        int perUnit = Math.max(1, type.getAmountPerUnit());
        String symbol = type.getUnitSymbol();
        boolean divisible = symbol != null && perUnit > 1;
        String valueUnit = symbol == null ? ""
                : (divisible && setting.largeUnit() ? symbol : (divisible ? "m" + symbol : symbol));
        String rateUnit = ThroughputTerminalPart.routineUnit(setting.routineOrdinal());
        return switch (metric) {
            case AMOUNT -> valueUnit;
            case RATE -> valueUnit + "/" + rateUnit;
            case ACCELERATION -> valueUnit + "/" + rateUnit + "/s";
        };
    }

    /**
     * 服务端一行（可能由多个监控器供给）。
     *
     * <p>持的是 {@link IMonitorRef} 而不是具体的零件类型 —— 这样 aetm 自家的监控器与
     * AAE 的监控器能混在同一张表里（同 key 合并、按坐标排序、选最细档位当数值来源，
     * 这些逻辑都与实现无关）。
     */
    private static final class ServerRow {

        /** 来源排序：维度 → x → y → z（AE2 的 Set 顺序不保证，排一下才稳定）。 */
        private static final Comparator<IMonitorRef> MONITOR_ORDER = Comparator
                .comparing((IMonitorRef monitor) -> {
                    var dimension = monitor.dimension();
                    return dimension == null ? "" : dimension.toString();
                })
                .thenComparingInt(monitor -> monitor.pos().getX())
                .thenComparingInt(monitor -> monitor.pos().getY())
                .thenComparingInt(monitor -> monitor.pos().getZ());

        final AEKey key;
        final List<IMonitorRef> monitors = new ArrayList<>();
        /** 有历史曲线的高级监控器（aetm 专属；AAE 的部件没有历史，这里恒为 null）。 */
        @Nullable
        AdvancedThroughputMonitorPart historySource;
        @Nullable
        IMonitorRef valueSource;
        long amount;
        double ratePerTick;

        ServerRow(AEKey key) {
            this.key = key;
        }

        void addMonitor(IMonitorRef monitor) {
            this.monitors.add(monitor);
            if (this.historySource == null && monitor.historySource() instanceof AdvancedThroughputMonitorPart advanced) {
                this.historySource = advanced;
            }
        }

        /**
         * 数值来源：优先有历史的高级监控器（它按全局采样间隔走），否则取档位最细的那个；
         * 档位相同就按坐标取第一个。同一网络里同 key 的数据本来就是同一份，取谁都不会改变结果。
         */
        void computeValues() {
            // AE2 给的是 Set，迭代顺序不保证 → 先按（维度, x, y, z）排序：
            // 这样数值来源、悬停提示里的来源列表、以及结构指纹（决定要不要重发全量）每次都是一致的
            this.monitors.sort(MONITOR_ORDER);

            IMonitorRef source = null;
            if (this.historySource != null) {
                // 高级监控器是"自家零件"，找到与它对应的那一个包装
                for (var monitor : this.monitors) {
                    if (monitor.historySource() == this.historySource) {
                        source = monitor;
                        break;
                    }
                }
            }
            if (source == null) {
                source = this.monitors.get(0);
                for (var monitor : this.monitors) {
                    if (monitor.routineOrdinal() < source.routineOrdinal()) {
                        source = monitor;
                    }
                }
            }
            this.valueSource = source;
            this.amount = source.amount();
            this.ratePerTick = source.lastReportedValue() / Math.max(1, source.routineTicks());
        }

        TerminalRow toTerminalRow(ITerminalSettingsHost settings) {
            var setting = settings.getSetting(this.key);
            var sources = new ArrayList<TerminalRow.Source>(this.monitors.size());
            for (var monitor : this.monitors) {
                var dimension = monitor.dimension();
                sources.add(new TerminalRow.Source(
                        monitor.pos(),
                        dimension == null ? "" : dimension.toString(),
                        monitor.routineOrdinal(),
                        monitor.advanced()));
            }
            return new TerminalRow(this.key, this.amount, this.ratePerTick, this.historySource != null,
                    setting.largeUnit(), setting.routineOrdinal(),
                    setting.window().ordinal(), setting.metric().ordinal(), List.copyOf(sources));
        }
    }

    // ------------------------------------------------------------------
    // 供屏幕调用的发送入口（sendClientAction 是 protected）
    // ------------------------------------------------------------------

    public void sendSetView(int mode) {
        sendClientAction("setView", mode);
    }

    /** 供界面调用：全局速率单位（作用于所有行）。 */
    public void sendSetAllRoutines(int routineOrdinal) {
        sendClientAction("setAllRoutines", routineOrdinal);
    }

    public void sendSetTerminalStyle(int styleOrdinal) {
        sendClientAction("setTerminalStyle", styleOrdinal);
    }

    public void sendSelectRow(int index) {
        sendClientAction("selectRow", index);
    }

    /**
     * 供界面调用：请求某一行"服务端算好的曲线"（浮动子窗口画图用）。
     *
     * <p>服务端会用该行自己的设置现算一份回发（见 {@link #requestRowSeries}），
     * 客户端缓存到 {@link #clientSeries}，{@link #seriesFor(int)} 优先读它。
     */
    public void sendRequestRowSeries(int rowIndex) {
        sendClientAction("requestRowSeries", rowIndex);
    }

    /**
     * 供界面调用：在世界里描边该行的第一个来源监控器（浮动子窗口的「定位」按钮）。
     *
     * <p>服务端解析坐标后回发 {@link TerminalRowsPacket#highlight}，客户端在渲染阶段画红色描边。
     */
    public void sendLocateRow(int rowIndex) {
        sendClientAction("locateRow", rowIndex);
    }



    public void sendCycleRowWindow() {
        sendClientAction("rowWindow");
    }

    public void sendCycleRowMetric() {
        sendClientAction("rowMetric");
    }

    public void sendToggleRowUnit() {
        sendClientAction("rowUnit");
    }

    public void sendCycleRowRoutine() {
        sendClientAction("rowRoutine");
    }
}
