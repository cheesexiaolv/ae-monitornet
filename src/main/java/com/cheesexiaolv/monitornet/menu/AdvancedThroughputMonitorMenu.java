package com.cheesexiaolv.monitornet.menu;

import com.cheesexiaolv.monitornet.Parts.IAdvancedThroughputMonitorHost;

import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.FakeSlot;

/**
 * 高级吞吐量监控器的服务端菜单。
 *
 * <p>从宿主（{@link IAdvancedThroughputMonitorHost}）读取当前监控对象/存量/速率，
 * 通过 {@code @GuiSync} 同步到客户端屏幕。含一个 level-emitter 式配置槽（FakeSlot）用于选择监控对象。
 *
 * <p><b>1.20.1 → 1.21.1（AE2 15.4.10 → 19.2.17）</b>：<b>本类不需要功能性改动</b>，只核对过以下 API 仍在：
 * <ul>
 *   <li>{@code AEBaseMenu(MenuType<?>, int, Inventory, Object)}
 *       —— {@code reference/AE2-1.21.1-19.2.17/appeng/menu/AEBaseMenu.java:87}；</li>
 *   <li>{@code createPlayerInventorySlots(Inventory)} —— 同文件 {@code :156}；</li>
 *   <li>{@code @GuiSync(short)} —— {@code appeng/menu/guisync/GuiSync.java:11}；</li>
 *   <li>{@code SlotSemantics.CONFIG} 与 {@code FakeSlot(InternalInventory, int)}
 *       —— {@code appeng/menu/SlotSemantics.java:14}、{@code appeng/menu/slot/FakeSlot.java:17}；
 *       {@code ConfigInventory.createMenuWrapper()} 返回的 {@code ConfigMenuInventory implements InternalInventory}
 *       —— {@code appeng/util/ConfigInventory.java:126}、{@code appeng/util/ConfigMenuInventory.java:14}；</li>
 *   <li>{@code MenuTypeBuilder.create(MenuFactory, Class)} / {@code build(String)}
 *       —— {@code appeng/menu/implementations/MenuTypeBuilder.java:44-46,130}。</li>
 * </ul>
 */
public class AdvancedThroughputMonitorMenu extends AEBaseMenu {

    public static final MenuType<AdvancedThroughputMonitorMenu> TYPE = MenuTypeBuilder
            .create(AdvancedThroughputMonitorMenu::new, IAdvancedThroughputMonitorHost.class)
            .withMenuTitle(host -> Component.translatable("item.ae_monitornet.advanced_throughput_monitor"))
            .build("advanced_throughput_monitor");

    @GuiSync(1)
    public String displayedItemName = "";
    @GuiSync(2)
    public String amountText = "0";
    @GuiSync(3)
    public String rateText = "";
    @GuiSync(4)
    public String rateUnit = "";
    @GuiSync(5)
    public int routine = 0;
    @GuiSync(6)
    public boolean fluidUnitB = false;
    @GuiSync(7)
    public boolean fluidDisplayed = false;
    /** 折线图数据（按窗口/显示量编码，客户端解析后画图）。 */
    @GuiSync(8)
    public String chartData = "";
    /** 折线图时间窗口标签（"1m" 等，无需翻译）。 */
    @GuiSync(9)
    public String chartWindow = "1m";
    /** 折线图显示量序号（0=储量 1=流速 2=加速度，客户端翻译）。 */
    @GuiSync(10)
    public int chartMetric = 1;
    /** 折线图时间窗口秒数（用于画横轴时间刻度）。 */
    @GuiSync(11)
    public int chartWindowSeconds = 60;
    /** 数值单位（由 key 类型推导，如 "mB"/"B"/""；泛化支持其它 key 类型）。 */
    @GuiSync(12)
    public String valueUnit = "";

    private final IAdvancedThroughputMonitorHost host;

    public AdvancedThroughputMonitorMenu(int id, Inventory playerInventory, IAdvancedThroughputMonitorHost host) {
        super(TYPE, id, playerInventory, (Object) host);
        this.host = host;

        // 玩家背包必须保留：没有它就无法把物品放进配置槽（GUI 会变得不可操作）
        createPlayerInventorySlots(playerInventory);

        // 配置槽：选择监控对象
        this.addSlot(new FakeSlot(host.getConfig().createMenuWrapper(), 0), SlotSemantics.CONFIG);

        // 单位切换：客户端发意图，服务端切档位后由 @GuiSync 回传
        registerClientAction("cycleRoutine", () -> host.cycleRoutine());
        // 流体单位 mB/B 切换
        registerClientAction("toggleFluidUnit", () -> host.toggleFluidUnit());
        // 折线图：时间窗口 / 显示量切换
        registerClientAction("cycleChartWindow", () -> host.cycleChartWindow());
        registerClientAction("cycleChartMetric", () -> host.cycleChartMetric());

        refreshFromHost();
    }

    @Override
    public void broadcastChanges() {
        refreshFromHost();
        super.broadcastChanges();
    }

    private void refreshFromHost() {
        var displayed = host.getDisplayed();
        // 无监控对象时置空，客户端用翻译键显示（如 "无"/"None"）
        this.displayedItemName = displayed == null ? "" : displayed.getDisplayName().getString();
        this.fluidUnitB = host.isFluidUnitB();
        this.valueUnit = "";

        if (displayed != null) {
            // 单位完全由 key 类型推导，不写死流体：
            //   getAmountPerUnit() = 1 个显示单位含多少基础单位（流体 1000，物品 1）
            //   getUnitSymbol()    = 显示单位符号（流体 "B"，物品 null）
            // → 于是"小单位"= m+符号（mB）、"大单位"= 符号（B）；其它类型（魔力/FE/气体等）同样适用。
            var type = displayed.getType();
            int perUnit = Math.max(1, type.getAmountPerUnit());
            String symbol = type.getUnitSymbol();
            boolean divisible = symbol != null && perUnit > 1;
            boolean useLargeUnit = divisible && this.fluidUnitB;

            this.fluidDisplayed = divisible;
            this.valueUnit = symbol == null ? ""
                    : useLargeUnit ? symbol : (divisible ? "m" + symbol : symbol);

            long amount = host.getAmount();
            double rate = Math.abs(host.getLastReportedValue());
            // 小单位显示成 m+符号（mB）；不可分类型直接用符号（如 FE）；物品无符号
            String smallSuffix = symbol == null ? "" : (divisible ? "m" + symbol : symbol);
            if (useLargeUnit) {
                this.amountText = String.format(Locale.ENGLISH, "%,d", amount / perUnit) + symbol;
                this.rateText = String.format(Locale.ENGLISH, "%,.2f", rate / perUnit) + symbol;
            } else {
                this.amountText = String.format(Locale.ENGLISH, "%,d", amount) + smallSuffix;
                this.rateText = String.format(Locale.ENGLISH, "%,d", Math.round(rate)) + smallSuffix;
            }
        } else {
            this.amountText = "0";
            this.rateText = "";
            this.fluidDisplayed = false;
        }

        this.rateUnit = host.getWorkRoutineUnit();
        this.routine = host.getRoutineOrdinal();
        // 折线图数据：按当前窗口/显示量编码（变化时 @GuiSync 才发）
        this.chartData = host.chartData();
        this.chartWindow = host.getChartWindow().label;
        this.chartWindowSeconds = host.getChartWindow().seconds;
        this.chartMetric = host.getChartMetric().ordinal();
    }

    public IAdvancedThroughputMonitorHost getHost() {
        return this.host;
    }

    /** 供屏幕调用：发送"切换档位"客户端动作（sendClientAction 是 protected，屏幕非子类不能直接调）。 */
    public void sendCycleRoutine() {
        sendClientAction("cycleRoutine");
    }

    /** 供屏幕调用：发送"切换流体 mB/B"客户端动作。 */
    public void sendToggleFluidUnit() {
        sendClientAction("toggleFluidUnit");
    }

    /** 供屏幕调用：发送"切换折线图时间窗口"客户端动作。 */
    public void sendCycleChartWindow() {
        sendClientAction("cycleChartWindow");
    }

    /** 供屏幕调用：发送"切换折线图显示量"客户端动作。 */
    public void sendCycleChartMetric() {
        sendClientAction("cycleChartMetric");
    }
}
