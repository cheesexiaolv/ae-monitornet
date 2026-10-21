package com.cheesexiaolv.aetm.Parts;

import com.cheesexiaolv.aetm.Items.ThroughputMonitorConfigurator;
import com.cheesexiaolv.aetm.Logic.ThroughputCache;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.orientation.BlockOrientation;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AmountFormat;
import appeng.api.util.AEColor;
import appeng.client.render.BlockEntityRenderHelper;
import appeng.core.AppEng;
import appeng.hooks.ticking.TickHandler;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractMonitorPart;

import java.util.Locale;

/**
 * ME 吞吐量监控器（AE2 1.19.2 移植版）
 *
 * <p>逻辑与外观参照 Advanced AE 1.20.1 的 {@code ThroughputMonitorPart}：
 * 继承 AE2 监控器基类获取“配置显示物/锁定/数量监控”能力，在网格中定时采样显示物数量，
 * 通过滑动时间窗计算平均速率，并在面板上同时渲染 物品图标 + 数量 + 速率文字。
 * 速率周期可用“吞吐量监控配置器”在 per tick / per second / per minute / per 10 minutes 间循环。
 *
 * <p>模型直接复用 AE2 自带的 Storage Monitor 模型与状态灯（Advanced AE 参考实现亦如此），
 * 因此无需自定义 part 模型资源，也不会再产生缺失模型的加载警告。
 */
public class ThroughputMonitorPart extends AbstractMonitorPart implements IGridTickable {

    // 💡 模型：与 Advanced AE 参考实现一致，直接使用 AE2 的 Storage Monitor 外观与状态灯
    public static final ResourceLocation MODEL_OFF = AppEng.makeId("part/storage_monitor_off");
    public static final ResourceLocation MODEL_ON = AppEng.makeId("part/storage_monitor_on");

    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    // 锁定状态复用同一套外观（行为上仍由基类处理锁定语义）
    public static final IPartModel MODELS_LOCKED_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_LOCKED_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_LOCKED_HAS_CHANNEL =
            new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    private static final int positiveColor = AEColor.GREEN.mediumVariant;
    private static final int negativeColor = AEColor.RED.mediumVariant;

    // 采样缓存容量：沿用 Advanced AE 的默认值（throughputMonitorCacheSize 默认 80）
    private static final int CACHE_SIZE = 80;

    private final ThroughputCache cache = new ThroughputCache(CACHE_SIZE);
    protected double lastReportedValue = 0;
    protected String lastHumanReadableValue = "";
    private WorkRoutine workRoutine = WorkRoutine.SECOND;
    private WorkRoutine lastWorkRoutine = WorkRoutine.SECOND;

    /**
     * 上次广播给客户端的"面板内容"（数量 + 速率）。
     *
     * <p>用途：把每 tick 的 {@code getHost().markForUpdate()} 收口成"内容真的变了才广播"。
     * 监控器面板同时渲染 <b>物品图标 + 数量 + 速率文字</b>，所以数量与速率<b>都要比</b>——
     * 只比速率会导致"数量变了但面板不刷新"。
     *
     * <p>初始值是两个"绝不可能相等"的哨兵值，保证第一次 tick 一定广播一次。
     */
    private long lastBroadcastAmount = Long.MIN_VALUE;
    private double lastBroadcastRate = Double.NaN;

    private enum WorkRoutine {
        // ticks = 展示值按多少 tick 放大（即“每 X tick 的量”）；timeLimit_s = 平均窗口秒数
        TICK(1, 10),
        SECOND(20, 20),
        MINUTE(1200, CACHE_SIZE / 2),
        TEN_MINUTE(12000, CACHE_SIZE * 5);

        public static WorkRoutine cycle(WorkRoutine routine) {
            return switch (routine) {
                case TICK -> SECOND;
                case SECOND -> MINUTE;
                case MINUTE -> TEN_MINUTE;
                case TEN_MINUTE -> TICK;
            };
        }

        public static WorkRoutine fromInt(int value) {
            return switch (value) {
                case 0 -> TICK;
                case 2 -> MINUTE;
                case 3 -> TEN_MINUTE;
                default -> SECOND;
            };
        }

        public final int ticks;
        public final int timeLimit_s;

        WorkRoutine(int ticks, int timeLimit_s) {
            this.ticks = ticks;
            this.timeLimit_s = timeLimit_s;
        }
    }

    public ThroughputMonitorPart(IPartItem<?> partItem) {
        super(partItem, false);

        getMainNode().addService(IGridTickable.class, this);
    }

    // ---------------------------------------------------------------
    // 持久化 / 网络同步（服务端字段 -> 客户端渲染）
    // ---------------------------------------------------------------

    @Override
    public void writeToNBT(CompoundTag data) {
        super.writeToNBT(data);
        data.putDouble("lastValue", this.lastReportedValue);
        data.putString("throughput", this.lastHumanReadableValue);
        data.putInt("routine", this.workRoutine.ordinal());
    }

    @Override
    public void readFromNBT(CompoundTag data) {
        super.readFromNBT(data);
        this.lastReportedValue = data.getDouble("lastValue");
        this.lastHumanReadableValue = data.getString("throughput");
        this.workRoutine = WorkRoutine.fromInt(data.getInt("routine"));
    }

    @Override
    public void writeVisualStateToNBT(CompoundTag data) {
        super.writeVisualStateToNBT(data);
        data.putDouble("lastValue", this.lastReportedValue);
        data.putString("throughput", this.lastHumanReadableValue);
        data.putInt("routine", this.workRoutine.ordinal());
    }

    @Override
    public void readVisualStateFromNBT(CompoundTag data) {
        super.readVisualStateFromNBT(data);
        this.lastReportedValue = data.getDouble("lastValue");
        this.lastHumanReadableValue = data.getString("throughput");
        this.workRoutine = WorkRoutine.fromInt(data.getInt("routine"));
    }

    @Override
    public void writeToStream(FriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeDouble(this.lastReportedValue);
        data.writeUtf(this.lastHumanReadableValue);
        data.writeInt(this.workRoutine.ordinal());
    }

    @Override
    public boolean readFromStream(FriendlyByteBuf data) {
        boolean needRedraw = super.readFromStream(data);

        double reportedValue = data.readDouble();
        needRedraw |= reportedValue != this.lastReportedValue;
        this.lastReportedValue = reportedValue;

        this.lastHumanReadableValue = data.readUtf(64);

        int routine = data.readInt() % WorkRoutine.values().length;
        needRedraw |= routine != this.workRoutine.ordinal();
        this.workRoutine = WorkRoutine.values()[routine];

        return needRedraw;
    }

    // ---------------------------------------------------------------
    // 交互：配置器切换周期；其余（配置物品/清空/锁定）交给 AE2 监控器基类
    // ---------------------------------------------------------------

    @Override
    public boolean onPartActivate(Player player, InteractionHand hand, Vec3 pos) {
        if (!isClientSide()) {
            if (player.getItemInHand(hand).getItem() instanceof ThroughputMonitorConfigurator) {
                cycleRoutine();
                return true;
            }
        }

        boolean result = super.onPartActivate(player, hand, pos);

        // 基类可能刚设置了/清空了显示物；根据当前状态让设备进入或退出休眠，
        // 保证能及时开始/停止采样。
        if (!isClientSide()) {
            syncTickingState();
        }

        return result;
    }

    /** 切换到下一个显示档位（per t/s/m/10m）。供 GUI 等单位切换调用。 */
    public void cycleRoutine() {
        this.workRoutine = WorkRoutine.cycle(this.workRoutine);

        // 档位变了 → 面板文字要立刻换，绕过"内容没变就不广播"的收口
        invalidateBroadcastCache();

        getHost().markForSave();
        getHost().markForUpdate();

        // 周期变化后立刻请求一次采样，让读数尽快刷新
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    /** 当前档位的序号（WorkRoutine ordinal，0=tick 1=second 2=minute 3=10min）。 */
    public int getRoutineOrdinal() {
        return this.workRoutine.ordinal();
    }

    /** 当前档位代表多少 tick（展示值 = 每 tick 量 × 该值），供折线图统一单位。 */
    public int getRoutineTicks() {
        return this.workRoutine.ticks;
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));

        super.onMainNodeStateChanged(reason);
    }

    private void syncTickingState() {
        // 显示物刚被设置/清空 → 面板要立刻换内容，绕过"内容没变就不广播"的收口
        invalidateBroadcastCache();

        getMainNode().ifPresent((grid, node) -> {
            var tickManager = grid.getTickManager();
            if (getDisplayed() == null) {
                tickManager.sleepDevice(node);
            } else {
                tickManager.wakeDevice(node);
                // 立即采样一次当前数量，避免首屏一直显示旧值
                long now = TickHandler.instance().getCurrentTick();
                this.cache.push(getAmount(), now);
            }
        });
    }

    // ---------------------------------------------------------------
    // 网格定时采样与速率计算（参照 Advanced AE 1.20.1 参考实现）
    // ---------------------------------------------------------------

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        // ⚠️ 零件能 tick 多快，直接决定采样能有多快：
        //   AE2 的 TickManagerService 用 TickingRequest.minTickRate() 做"两次 tick 之间的最少 tick 数"
        //   （TickTracker.setCurrentRate -> Mth.clamp(rate, minTickRate, maxTickRate)，
        //     nextTick = lastTick + currentRate）。这里原来写死 20，等于把采样锁在 ≥1 秒一个点，
        //   所以配置把 samplingIntervalTicks 调到 10（0.5 秒）也毫无效果。
        // 现在下限跟着采样间隔走：配置比 1 秒更快时零件也允许 tick 得更快；配置 ≥20 时仍是 20，行为不变。
        int sampling = samplingIntervalFromConfig();
        int minTicks = Math.max(1, Math.min(20, sampling));
        int maxTicks = Math.max(100, minTicks);
        return new TickingRequest(minTicks, maxTicks, !isActive() || getDisplayed() == null, true);
    }

    /**
     * 采样间隔（tick）—— 服务端全局配置，{@code AdvancedThroughputMonitorPart} 记账时也用它。
     *
     * <p>{@code protected static}：基类与高级版共用这一份实现，别在子类里再抄一遍。
     *
     * @return 配置里的 {@code samplingIntervalTicks}（读不到时兜底 20 = 1 秒）
     */
    protected static int samplingIntervalFromConfig() {
        try {
            return Math.max(1, com.cheesexiaolv.aetm.AetmConfig.SAMPLING_INTERVAL_TICKS.get());
        } catch (Exception e) {
            return 20;
        }
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLast) {
        if (!getMainNode().isActive() || getDisplayed() == null) {
            resetState();
            return TickRateModulation.SLEEP;
        }

        long currentTick = TickHandler.instance().getCurrentTick();
        long currentAmount = getAmount();

        // 长时间没有采样（如刚上电/刚配置），先推入一个点并尽快再采样
        if (cache.size() == 0) {
            updateState(currentAmount, currentTick);
            this.lastHumanReadableValue = "-";
            return TickRateModulation.URGENT;
        }

        if (this.workRoutine == this.lastWorkRoutine) {
            double amountPerTick = cache.averagePerTick(this.workRoutine.timeLimit_s);
            this.lastReportedValue = amountPerTick * this.workRoutine.ticks;
            this.lastHumanReadableValue = formatValue(this.lastReportedValue);
        } else {
            // 周期刚被切换，本帧先清空显示，等新窗口数据稳定后再读数
            this.lastHumanReadableValue = "";
        }

        updateState(currentAmount, currentTick);

        // 面板广播收口：只有"数量或速率"真的变了才 markForUpdate()。
        // 这条原来是无条件每 tick 调一次 —— 采样间隔调到 10 tick 就翻倍、调到 1 tick 就是 20 倍，
        // 而 markForUpdate() 会让宿主方块实体向周围客户端做一次状态同步（主线程序列化 + 广播），
        // 是整条采样链上唯一"有外部影响"的开销。
        if (shouldBroadcast(currentAmount)) {
            getHost().markForUpdate();
        }

        // 采样节奏自校正 —— 这里 <b>不能</b> 无条件返回 SLOWER：
        //   AE2 里 SLOWER = 当前间隔 +1（朝 maxTickRate 靠）、FASTER = -2（朝 minTickRate 靠）、SAME = 不变，
        //   而我们的 maxTickRate 是 100。无条件 SLOWER 会让零件间隔从 minTicks 一路爬到 100 tick（5 秒）并停住——
        //   采样累加器再准也没用，因为零件 5 秒才被叫一次（用户看到的"图线 3 秒才动一次"就是这个）。
        // 另外两个"起点偏高"的坑也靠它兜住：
        //   · TickingRequest 的初始速率 = (min + max) / 2 = (10 + 100) / 2 = 55；
        //   · AE2 的 sleepDevice 会把 currentRate 直接设成 maxTickRate = 100。
        //   这两种情况都会被下面的 FASTER（每次 -2）快速拉回 minTickRate。
        //
        // ⚠️ 阈值必须是 interval 本身，不能放宽到 2*interval —— 放宽后平衡点就落在 2*interval，
        //    配置 10 tick 时零件仍是每 ~20 tick 才被调一次（采样累加器跨过 10 只 push 一笔）＝还是 1 秒一个点。
        // 平衡点：currentRate == interval == minTickRate。此时 ticksSinceLast == interval，
        // 既不 > interval（不触发 FASTER）也没有更快的分支（不 SLOWER），返回 SAME 稳定不抖。
        int interval = samplingIntervalFromConfig();
        if (ticksSinceLast > interval) {
            return TickRateModulation.FASTER;
        }
        return TickRateModulation.SAME;
    }

    /**
     * 面板内容是否与上次广播过的一致。
     *
     * <p>数量与速率都要比：面板上既有数量也有速率文字，只比速率会让"数量变了但面板不刷新"。
     * 判定通过的同时顺手记住这次广播的内容（避免调用方忘记更新）。
     */
    private boolean shouldBroadcast(long currentAmount) {
        if (currentAmount == this.lastBroadcastAmount
                && Double.compare(this.lastReportedValue, this.lastBroadcastRate) == 0) {
            return false;
        }
        this.lastBroadcastAmount = currentAmount;
        this.lastBroadcastRate = this.lastReportedValue;
        return true;
    }

    /**
     * 让"面板内容缓存"失效，保证下一次 {@code tickingRequest} 一定会广播一次。
     *
     * <p>给"一次性事件"用：换监控对象、换显示档位、清空采样缓存之后，
     * 光比数量/速率可能恰好相等而漏掉刷新，所以这里主动把哨兵值放回去。
     */
    protected void invalidateBroadcastCache() {
        this.lastBroadcastAmount = Long.MIN_VALUE;
        this.lastBroadcastRate = Double.NaN;
    }

    /**
     * 把速率值格式化为展示文本：大于 10 或等于 0 时用 AE2 的数量缩写（如 1.2k），
     * 否则保留两位小数（小数部分始终为正，正负号由调用方处理）。
     */
    private String formatValue(double value) {
        if (value > 10 || value == 0) {
            return getDisplayed().formatAmount(Math.round(Math.abs(value)), AmountFormat.SLOT);
        }
        return String.format(Locale.ENGLISH, "%.2f", Math.abs(value));
    }

    private void updateState(long amount, long tick) {
        cache.push(amount, tick);
        this.lastWorkRoutine = this.workRoutine;
    }

    private void resetState() {
        cache.clear();
        this.lastHumanReadableValue = "";
        // 掉电/清空显示物：读数已归零，重新亮起来时一定要广播一次，别被"内容没变"挡住
        invalidateBroadcastCache();
    }

    // ---------------------------------------------------------------
    // 动态渲染：物品图标 + 数量 + 速率文字（参照 Advanced AE）
    // ---------------------------------------------------------------

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderDynamic(
            float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int combinedLightIn, int combinedOverlayIn) {
        if (!isActive() || getDisplayed() == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        // AE2 15.x: rotateToFace(PoseStack, BlockOrientation)。1.19.2 是 (PoseStack, Direction, byte spin)，
        // 现在先把 (side, spin) 合成一个 BlockOrientation（AE2 的 StorageMonitorPart 也是这么写的）。
        BlockOrientation orientation = BlockOrientation.get(getSide(), getSpin());
        BlockEntityRenderHelper.rotateToFace(poseStack, orientation);

        // 物品图标与数量
        // AE2 15.x: renderItem2dWithAmount 多了 canCraft 与 Level 两个参数（1.19.2 是 7 个参数）。
        poseStack.translate(0, 0.1, 0.5);
        BlockEntityRenderHelper.renderItem2dWithAmount(
                poseStack, buffers, getDisplayed(), getAmount(), canCraft(),
                0.3F, -0.15F, getColor().contrastTextColor, getLevel());

        // 速率文字
        poseStack.translate(0, -0.23F, 0);
        String sign = lastReportedValue > 0 ? "+" : lastReportedValue == 0 ? "" : "-";
        String text = sign + lastHumanReadableValue + valueUnitSuffix() + "/" + unitSuffix();
        int color = lastReportedValue > 0
                ? positiveColor
                : lastReportedValue == 0 ? getColor().contrastTextColor : negativeColor;
        renderString(poseStack, buffers, text, color);

        poseStack.popPose();
    }

    /**
     * 数值单位后缀（由 key 类型推导）。
     *
     * <p>注意：AE2 的 {@code formatAmount} 对流体这类 {@code getAmountPerUnit() > 1} 的类型
     * <b>已经换算到显示单位</b>（如 2400mB → 2.4），所以这里只补单位符号本身（"B"），
     * 不能再加 "m" 前缀（否则就成了"2.4 mB"这种量级错误）。
     */
    protected String valueUnitSuffix() {
        var displayed = getDisplayed();
        if (displayed == null) {
            return "";
        }
        String symbol = displayed.getType().getUnitSymbol();
        return symbol == null ? "" : symbol;
    }

    private String unitSuffix() {
        return switch (this.workRoutine) {
            case TICK -> "t";
            case SECOND -> "s";
            case MINUTE -> "m";
            case TEN_MINUTE -> "10m";
        };
    }

    /**
     * 在方块表面绘制一行居中的小字（从 Advanced AE 的 AAEBlockEntityRenderHelper 移植）。
     */
    @OnlyIn(Dist.CLIENT)
    private static void renderString(PoseStack poseStack, MultiBufferSource buffers, String text, int textColor) {
        Font font = Minecraft.getInstance().font;
        int width = font.width(text);

        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, 0.02F);
        poseStack.scale(0.016129032F, -0.016129032F, 0.016129032F);
        poseStack.scale(0.5F, 0.5F, 0.0F);
        poseStack.translate(-0.5F * width, 0.0F, 0.5F);
        font.drawInBatch(
                text,
                0.0F,
                0.0F,
                textColor,
                false,
                poseStack.last().pose(),
                buffers,
                // 1.20.1: 第 8 个参数从 boolean(transparent) 变成 Font.DisplayMode
                Font.DisplayMode.NORMAL,
                0,
                15728880);
        poseStack.popPose();
    }

    @Override
    public IPartModel getStaticModels() {
        return this.selectModel(
                MODELS_OFF,
                MODELS_ON,
                MODELS_HAS_CHANNEL,
                MODELS_LOCKED_OFF,
                MODELS_LOCKED_ON,
                MODELS_LOCKED_HAS_CHANNEL);
    }

    // ---------------------------------------------------------------
    // 供 GUI（高级吞吐量监控器菜单/屏幕）读取采样状态
    // ---------------------------------------------------------------

    public double getLastReportedValue() {
        return this.lastReportedValue;
    }

    public String getLastHumanReadableValue() {
        return this.lastHumanReadableValue;
    }

    public String getWorkRoutineUnit() {
        return "/" + unitSuffix();
    }

    /** 清空采样缓存（供外部（如高级版配置槽变化时）重置读数）。 */
    protected void clearSampleCache() {
        this.cache.clear();
        this.lastHumanReadableValue = "";
        // 读数被清空 → 面板内容必然要变，绕过"内容没变就不广播"的收口
        invalidateBroadcastCache();
    }
}
