package com.cheesexiaolv.monitornet.network;

import java.util.ArrayList;
import java.util.List;

import com.cheesexiaolv.monitornet.AeMonitorNet;
import com.cheesexiaolv.monitornet.Logic.ThroughputHistory;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.AEKey;

/**
 * 服务端 → 客户端：聚合终端的行数据 / 窗口曲线 / 世界高亮。
 *
 * <p>四种载荷：
 * <ul>
 *   <li>{@link #MODE_FULL}：完整行结构（key / 来源坐标 / 是否有历史）＋当前数值。打开界面时与
 *       行集合变化时发；</li>
 *   <li>{@link #MODE_VALUES}：只发数值（按行序号对齐），结构没变时的每秒刷新走这条，省掉重复的 key；</li>
 *   <li>{@link #MODE_SERIES}：<b>某一行服务端算好的曲线</b>（列号 + {@link ThroughputHistory#encode} 字符串）。
 *       浮动子窗口要画图时按需请求（客户端自记的那份只作兜底——刚开窗时它只有一两个样本，会拉出夸张的斜线）；</li>
 *   <li>{@link #MODE_HIGHLIGHT}：<b>在世界里描边某个方块</b>（行序号 + 维度 + 坐标 + 持续 tick）。
 *       「定位」按钮用：点一下让该行第一个来源监控器在世界上被描边几秒（红色）。</li>
 * </ul>
 *
 * <p>为什么不走 {@code @GuiSync}：它只支持标量/String/枚举/GenericStack 等，不支持数组或列表，
 * 而 AE2 自己的包类型（{@code BasePacketHandler.PacketTypes}）是闭合枚举，第三方加不进去，
 * 所以聚合终端自建一条通道（见 {@link MonitorNetNetwork}）。
 *
 * <p><b>1.20.1 → 1.21.1（NeoForge）</b>：整类改成 {@link CustomPacketPayload}：
 * <ul>
 *   <li>Forge 的 {@code EventNetworkChannel}/{@code NetworkDirection.buildPacket} 已被删除，
 *       入口变成 {@link MonitorNetNetwork} 里的 {@code PayloadRegistrar}（证据：
 *       {@code reference/AE2-1.21.1-19.2.17/appeng/core/network/InitNetwork.java:40-45,82-84}）；</li>
 *   <li>编解码器统一用 {@link StreamCodec}，缓冲区类型必须是 {@link RegistryFriendlyByteBuf}
 *       ——因为 {@code AEKey.writeKey/readKey} 在 19.2 需要 registry lookup
 *       （{@code reference/AE2-1.21.1-19.2.17/appeng/api/stacks/AEKey.java:85,97}）；</li>
 *   <li>报文体内的字节布局<b>保持 1.20.1 原样</b>（4 种 MODE 的分支顺序不变），只是换了外层信封。</li>
 * </ul>
 */
public final class TerminalRowsPacket implements CustomPacketPayload {

    /** 载荷 id：{@code ae_monitornet:terminal_rows}。 */
    public static final CustomPacketPayload.Type<TerminalRowsPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(AeMonitorNet.MODID, "terminal_rows"));

    /** NeoForge 的编解码器（写入 / 读取），由 {@link MonitorNetNetwork} 注册给 {@code PayloadRegistrar}。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalRowsPacket> STREAM_CODEC =
            StreamCodec.ofMember(TerminalRowsPacket::write, TerminalRowsPacket::decode);

    public static final byte MODE_FULL = 0;
    public static final byte MODE_VALUES = 1;
    public static final byte MODE_SERIES = 2;
    public static final byte MODE_HIGHLIGHT = 3;

    private final byte mode;
    private final List<TerminalRow> rows;
    private final long[] amounts;
    private final double[] rates;
    /** 服务端的采样间隔（tick）。客户端用同一个 ThroughputHistory 自己记账时要用它。 */
    private final int sampleIntervalTicks;
    /**
     * 图线刷新间隔（tick）：{@code max(1, round(samplingIntervalTicks / chartRefreshMultiplier))}。
     *
     * <p>只在全量包里发（服务端权威），客户端屏幕按它节流"请求窗口曲线"，单机/联机表现一致。
     */
    private final int chartRefreshIntervalTicks;
    /** {@link #MODE_SERIES}：这一份曲线属于哪一行。 */
    private final int seriesRowIndex;
    /** {@link #MODE_SERIES}：编码后的曲线（空串 = 该行没有历史源）。 */
    private final String seriesData;
    /** {@link #MODE_HIGHLIGHT}：被描边的方块。 */
    private final BlockPos highlightPos;
    /** {@link #MODE_HIGHLIGHT}：方块所在维度。 */
    private final String highlightDimension;
    /** {@link #MODE_HIGHLIGHT}：描边持续多少 tick。 */
    private final int highlightTicks;

    private TerminalRowsPacket(byte mode, List<TerminalRow> rows, long[] amounts, double[] rates,
            int sampleIntervalTicks, int chartRefreshIntervalTicks, int seriesRowIndex, String seriesData,
            BlockPos highlightPos, String highlightDimension, int highlightTicks) {
        this.mode = mode;
        this.rows = rows;
        this.amounts = amounts;
        this.rates = rates;
        this.sampleIntervalTicks = sampleIntervalTicks;
        this.chartRefreshIntervalTicks = chartRefreshIntervalTicks;
        this.seriesRowIndex = seriesRowIndex;
        this.seriesData = seriesData;
        this.highlightPos = highlightPos;
        this.highlightDimension = highlightDimension;
        this.highlightTicks = highlightTicks;
    }

    public static TerminalRowsPacket full(List<TerminalRow> rows, int sampleIntervalTicks,
            int chartRefreshIntervalTicks) {
        long[] amounts = new long[rows.size()];
        double[] rates = new double[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            amounts[i] = rows.get(i).amount();
            rates[i] = rows.get(i).ratePerTick();
        }
        return new TerminalRowsPacket(MODE_FULL, List.copyOf(rows), amounts, rates, sampleIntervalTicks,
                chartRefreshIntervalTicks, -1, "", BlockPos.ZERO, "", 0);
    }

    public static TerminalRowsPacket values(long[] amounts, double[] rates) {
        return new TerminalRowsPacket(MODE_VALUES, List.of(), amounts, rates, 0, 0, -1, "",
                BlockPos.ZERO, "", 0);
    }

    /**
     * 某一行服务端算好的曲线。
     *
     * @param rowIndex    行序号（客户端收到的顺序与服务端一致）
     * @param encodedData {@link ThroughputHistory#encode} 的结果（空串 = 该行没有历史源）
     */
    public static TerminalRowsPacket series(int rowIndex, String encodedData) {
        return new TerminalRowsPacket(MODE_SERIES, List.of(), new long[0], new double[0], 0, 0, rowIndex,
                encodedData == null ? "" : encodedData, BlockPos.ZERO, "", 0);
    }

    /**
     * 让客户端在世界里描边一个方块（「定位」按钮用）。
     *
     * @param rowIndex    行序号（客户端只用它记"这是哪一行被定位了"）
     * @param pos         方块坐标
     * @param dimension   维度 id（{@code Level.dimension().location().toString()}）
     * @param ticks       描边持续多少 tick
     */
    public static TerminalRowsPacket highlight(int rowIndex, BlockPos pos, String dimension, int ticks) {
        return new TerminalRowsPacket(MODE_HIGHLIGHT, List.of(), new long[0], new double[0], 0, 0, -1, "",
                pos, dimension == null ? "" : dimension, ticks);
    }

    /** {@inheritDoc} NeoForge 用它把载荷路由到对应的 {@code PayloadRegistrar} 条目。 */
    @Override
    public CustomPacketPayload.Type<TerminalRowsPacket> type() {
        return TYPE;
    }

    /** 采样间隔（只有全量包带；数值包用客户端记住的值）。 */
    public int sampleIntervalTicks() {
        return this.sampleIntervalTicks;
    }

    /** 图线刷新间隔（tick，只有全量包带）——屏幕按它节流"请求窗口曲线"。 */
    public int chartRefreshIntervalTicks() {
        return this.chartRefreshIntervalTicks;
    }

    public byte mode() {
        return this.mode;
    }

    public List<TerminalRow> rows() {
        return this.rows;
    }

    public long[] amounts() {
        return this.amounts;
    }

    public double[] rates() {
        return this.rates;
    }

    /** {@link #MODE_SERIES}：曲线属于哪一行（其它载荷为 -1）。 */
    public int seriesRowIndex() {
        return this.seriesRowIndex;
    }

    /** {@link #MODE_SERIES}：编码后的曲线（其它载荷为空串）。 */
    public String seriesData() {
        return this.seriesData;
    }

    /** {@link #MODE_HIGHLIGHT}：被描边的方块。 */
    public BlockPos highlightPos() {
        return this.highlightPos;
    }

    /** {@link #MODE_HIGHLIGHT}：方块所在维度（其它载荷为空串）。 */
    public String highlightDimension() {
        return this.highlightDimension;
    }

    /** {@link #MODE_HIGHLIGHT}：描边持续多少 tick。 */
    public int highlightTicks() {
        return this.highlightTicks;
    }

    // ------------------------------------------------------------------
    // StreamCodec：1.20.1 的 encode/decode 原样搬过来，只把缓冲区换成
    // RegistryFriendlyByteBuf（AEKey 在 19.2 需要 registry lookup）
    // ------------------------------------------------------------------

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeByte(this.mode);
        if (this.mode == MODE_FULL) {
            buf.writeVarInt(this.sampleIntervalTicks);
            buf.writeVarInt(this.chartRefreshIntervalTicks);
            buf.writeVarInt(this.rows.size());
            for (var row : this.rows) {
                AEKey.writeKey(buf, row.key());
                buf.writeVarLong(row.amount());
                buf.writeDouble(row.ratePerTick());
                buf.writeBoolean(row.hasHistory());
                buf.writeBoolean(row.largeUnit());
                buf.writeByte(row.routineOrdinal());
                buf.writeByte(row.windowOrdinal());
                buf.writeByte(row.metricOrdinal());
                buf.writeVarInt(row.sources().size());
                for (var source : row.sources()) {
                    buf.writeBlockPos(source.pos());
                    buf.writeUtf(source.dimension(), 128);
                    buf.writeByte(source.routineOrdinal());
                    buf.writeBoolean(source.advanced());
                }
            }
        } else if (this.mode == MODE_SERIES) {
            buf.writeVarInt(this.seriesRowIndex);
            buf.writeUtf(this.seriesData);
        } else if (this.mode == MODE_HIGHLIGHT) {
            buf.writeVarInt(this.seriesRowIndex);
            buf.writeBlockPos(this.highlightPos);
            buf.writeUtf(this.highlightDimension, 128);
            buf.writeVarInt(this.highlightTicks);
        } else {
            buf.writeVarInt(this.amounts.length);
            for (long amount : this.amounts) {
                buf.writeVarLong(amount);
            }
            for (double rate : this.rates) {
                buf.writeDouble(rate);
            }
        }
    }

    public static TerminalRowsPacket decode(RegistryFriendlyByteBuf buf) {
        byte mode = buf.readByte();
        if (mode == MODE_FULL) {
            int sampleIntervalTicks = buf.readVarInt();
            int chartRefreshIntervalTicks = buf.readVarInt();
            int count = buf.readVarInt();
            List<TerminalRow> rows = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                AEKey key = AEKey.readKey(buf);
                long amount = buf.readVarLong();
                double rate = buf.readDouble();
                boolean hasHistory = buf.readBoolean();
                boolean largeUnit = buf.readBoolean();
                int rowRoutine = buf.readByte();
                int rowWindow = buf.readByte();
                int rowMetric = buf.readByte();
                int sourceCount = buf.readVarInt();
                List<TerminalRow.Source> sources = new ArrayList<>(sourceCount);
                for (int s = 0; s < sourceCount; s++) {
                    var pos = buf.readBlockPos();
                    String dimension = buf.readUtf(128);
                    int routine = buf.readByte();
                    boolean advanced = buf.readBoolean();
                    sources.add(new TerminalRow.Source(pos, dimension, routine, advanced));
                }
                rows.add(new TerminalRow(key, amount, rate, hasHistory, largeUnit, rowRoutine,
                        rowWindow, rowMetric, List.copyOf(sources)));
            }
            return full(rows, sampleIntervalTicks, chartRefreshIntervalTicks);
        }

        if (mode == MODE_SERIES) {
            int rowIndex = buf.readVarInt();
            String data = buf.readUtf();
            return series(rowIndex, data);
        }

        if (mode == MODE_HIGHLIGHT) {
            int rowIndex = buf.readVarInt();
            var pos = buf.readBlockPos();
            String dimension = buf.readUtf(128);
            int ticks = buf.readVarInt();
            return highlight(rowIndex, pos, dimension, ticks);
        }

        int count = buf.readVarInt();
        long[] amounts = new long[count];
        double[] rates = new double[count];
        for (int i = 0; i < count; i++) {
            amounts[i] = buf.readVarLong();
        }
        for (int i = 0; i < count; i++) {
            rates[i] = buf.readDouble();
        }
        return values(amounts, rates);
    }
}
