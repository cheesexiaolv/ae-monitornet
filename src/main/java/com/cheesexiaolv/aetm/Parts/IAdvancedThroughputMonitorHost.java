package com.cheesexiaolv.aetm.Parts;

import org.jetbrains.annotations.Nullable;

import com.cheesexiaolv.aetm.Logic.ThroughputHistory;

import appeng.api.stacks.AEKey;
import appeng.util.ConfigInventory;

/**
 * 高级吞吐量监控器菜单的宿主接口。
 *
 * <p>{@link AdvancedThroughputMonitorPart} 实现该接口，作为 {@code AdvancedThroughputMonitorMenu} 的 host，
 * 使 {@code MenuLocators.forPart(...)} 能定位到它，并把采样/显示状态暴露给菜单。
 */
public interface IAdvancedThroughputMonitorHost {

    /** 当前监控的物品/流体 key，可能为 null（未配置）。 */
    @Nullable
    AEKey getDisplayed();

    /** 该物品/流体在网格中的当前存量。 */
    long getAmount();

    /** 最近一次计算出的速率数值。 */
    double getLastReportedValue();

    /** 最近一次的人类可读速率文本（如 "1.2k"）。 */
    String getLastHumanReadableValue();

    /** 当前档位单位后缀（如 "/s"、"/m"）。 */
    String getWorkRoutineUnit();

    /** 切换到下一个显示档位（per t/s/m/10m）。 */
    void cycleRoutine();

    /** 当前档位序号（0=tick 1=second 2=minute 3=10min）。 */
    int getRoutineOrdinal();

    /** 流体数量单位是否用 B（桶）；false 表示 mB。 */
    boolean isFluidUnitB();

    /** 切换流体的数量单位 mB/B。 */
    void toggleFluidUnit();

    /** 配置槽（选择监控对象），供菜单的 FakeSlot 绑定。 */
    ConfigInventory getConfig();

    /** 折线图当前的时间窗口。 */
    ThroughputHistory.Window getChartWindow();

    /** 切换折线图时间窗口。 */
    void cycleChartWindow();

    /** 折线图当前的显示量（储量/流速/加速度）。 */
    ThroughputHistory.Metric getChartMetric();

    /** 切换折线图显示量。 */
    void cycleChartMetric();

    /** 当前档位代表多少 tick（折线图流速用它换算，保证与读数同单位）。 */
    int getRoutineTicks();

    /** 折线图数据（按当前窗口/显示量编码后的紧凑字符串）。 */
    String chartData();
}

