package com.cheesexiaolv.monitornet.network;

import java.util.List;

import net.minecraft.core.BlockPos;

import appeng.api.stacks.AEKey;

/**
 * 聚合终端的一行：同一 ME 网络内被监控的<b>同一个 key</b> 合并后的结果。
 *
 * <p>为什么同 key 只留一行：同一网络里，多个监控器盯同一个 key 时读到的存量与速率
 * 本来就是同一份数据（都取自网络存储的缓存），所以"合并"是无损的——多个监控器只作为
 * <b>来源</b>列出（坐标 / 档位 / 是否高级版）。
 *
 * <p>{@code ratePerTick} 统一成"每 tick 的量"（监控器自己的 {@code lastReportedValue} 是
 * "每档位的量"，除以 {@code getRoutineTicks()} 换算而来），客户端再乘以终端选定的档位。
 *
 * @param key            被监控的 key（行身份）
 * @param amount         网络内存量（同 key 各监控器一致）
 * @param ratePerTick    速率（每 tick 的量，带正负号）
 * @param hasHistory     该行是否有可用曲线（至少有一个高级监控器提供历史）
 * @param largeUnit      该行是否用大单位（流体 B 而非 mB）——每行可独立设置，所以随行下发
 * @param routineOrdinal 该行的速率档位（0=t 1=s 2=m 3=10m）——同上
 * @param windowOrdinal  该行的折线图时间窗口（0=10s 1=1m 2=10m 3=60m）——同上
 * @param metricOrdinal  该行的显示量（0=储量 1=流速 2=加速度）——同上
 * @param sources        来源监控器列表
 */
public record TerminalRow(AEKey key, long amount, double ratePerTick, boolean hasHistory, boolean largeUnit,
        int routineOrdinal, int windowOrdinal, int metricOrdinal, List<Source> sources) {

    /**
     * 提供该行数据的监控器来源。
     *
     * @param pos            方块坐标
     * @param dimension      维度 id（同网络可能跨维度，例如量子网络桥）
     * @param routineOrdinal 该监控器的显示档位（0=t 1=s 2=m 3=10m）
     * @param advanced       是否高级监控器（高级版才有历史曲线）
     */
    public record Source(BlockPos pos, String dimension, int routineOrdinal, boolean advanced) {
    }
}
