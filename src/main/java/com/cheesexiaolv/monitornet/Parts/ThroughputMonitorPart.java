package com.cheesexiaolv.monitornet.Parts;

import com.cheesexiaolv.monitornet.AeMonitorNet;
import com.cheesexiaolv.monitornet.Items.ThroughputMonitorConfigurator;
import com.cheesexiaolv.monitornet.Logic.ThroughputCache;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.orientation.BlockOrientation;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.util.AEColor;
import appeng.client.render.BlockEntityRenderHelper;
import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.hooks.ticking.TickHandler;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractMonitorPart;

import java.util.Locale;

/**
 * ME throughput monitor.
 *
 * <p>Logic and looks follow Advanced AE's {@code ThroughputMonitorPart}: it inherits AE2's monitor
 * base class (configure the displayed key / lock / amount monitoring), samples the stored amount on
 * the grid, computes an average rate over a sliding window, and renders icon + amount + rate text on
 * the part face. The rate routine can be cycled with the throughput monitor configurator
 * (per tick / second / minute / 10 minutes).
 *
 * <p>Models reuse AE2's storage monitor lights and status strip, but the front plate is this mod's
 * own {@code ae_monitornet:part/monitor_base}: a child of AE2's {@code part/display_base} that only
 * replaces {@code front} (AE2 19.2's {@code ae2:part/monitor_front} is an outline-only ring, the
 * 1.20.1 filled plates are gone), so the panel is not see-through.
 *
 * <p><b>1.20.1 → 1.21.1 changes</b>
 * <ul>
 *   <li>{@code onPartActivate(Player, InteractionHand, Vec3)} is gone from {@code IPart}; it was
 *       split into {@code onUseItemOn(ItemStack, Player, InteractionHand, Vec3)} and
 *       {@code onUseWithoutItem(Player, Vec3)} (evidence:
 *       {@code reference/AE2-1.21.1-19.2.17/appeng/api/parts/IPart.java:126,130}, monitor override at
 *       {@code appeng/parts/reporting/AbstractMonitorPart.java:167}). The held stack is now passed
 *       explicitly, so the configurator check uses the parameter instead of
 *       {@code player.getItemInHand(hand)}.</li>
 *   <li>{@code writeToNBT}/{@code readFromNBT} gained a {@code HolderLookup.Provider} (registry
 *       lookup) parameter, and {@code writeToStream}/{@code readFromStream} take
 *       {@code RegistryFriendlyByteBuf} instead of {@code FriendlyByteBuf}.</li>
 *   <li>{@code TickingRequest} lost its {@code canBeAlerted} component: the 4-arg constructor
 *       {@code (min, max, sleeping, canBeAlerted)} became {@code (min, max, sleeping)}
 *       (AE2 15.4.10 record had 5 components incl. {@code canBeAlerted}; 19.2.17 has 4).</li>
 * </ul>
 */
public class ThroughputMonitorPart extends AbstractMonitorPart implements IGridTickable {

    // Model: same as Advanced AE's implementation -- AE2's storage monitor lights and status strip
    // on top of our own base plate (see MODEL_BASE_PLATE below; the lights models cannot override
    // the front texture, that one lives in AE2's part/display_base).
    public static final ResourceLocation MODEL_BASE_PLATE =
            ResourceLocation.fromNamespaceAndPath(AeMonitorNet.MODID, "part/monitor_base");
    public static final ResourceLocation MODEL_OFF = AppEng.makeId("part/storage_monitor_off");
    public static final ResourceLocation MODEL_ON = AppEng.makeId("part/storage_monitor_on");

    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE_PLATE, MODEL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE_PLATE, MODEL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL =
            new PartModel(MODEL_BASE_PLATE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    // Locked state reuses the same look (the base class still handles lock semantics)
    public static final IPartModel MODELS_LOCKED_OFF = new PartModel(MODEL_BASE_PLATE, MODEL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_LOCKED_ON = new PartModel(MODEL_BASE_PLATE, MODEL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_LOCKED_HAS_CHANNEL =
            new PartModel(MODEL_BASE_PLATE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    private static final int positiveColor = AEColor.GREEN.mediumVariant;
    private static final int negativeColor = AEColor.RED.mediumVariant;

    // Sampling cache size: Advanced AE's default (throughputMonitorCacheSize = 80)
    private static final int CACHE_SIZE = 80;

    /**
     * Mod-owned copy of the monitored key (written by {@link #writeToNBT}).
     *
     * <p>AE2's {@code AbstractMonitorPart} already persists the key as {@code "configuredItem"}
     * through {@code AEKey#toTagGeneric(HolderLookup.Provider)}
     * (AE2 19.2.17 {@code appeng/parts/reporting/AbstractMonitorPart.java:94-100}); that field stays
     * the primary and remains fully backwards compatible. This second copy makes
     * {@code writeToNBT}/{@code readFromNBT} symmetric for this part on its own: the base class'
     * key field is private, so if AE2's tag is missing (saves written by builds that predate the
     * 1.21 port, or a future AE2 that stops writing it) the part would silently come back with no
     * monitored object. The copy is only read when AE2's own field did not restore a key.
     */
    private static final String TAG_KEY = "monitornetKey";

    private final ThroughputCache cache = new ThroughputCache(CACHE_SIZE);
    protected double lastReportedValue = 0;
    protected String lastHumanReadableValue = "";
    private WorkRoutine workRoutine = WorkRoutine.SECOND;
    private WorkRoutine lastWorkRoutine = WorkRoutine.SECOND;

    /**
     * Last "panel content" (amount + rate) broadcast to clients.
     *
     * <p>Collapses the per-tick {@code getHost().markForUpdate()} into "broadcast only when the
     * content really changed". The panel renders icon + amount + rate text, so both amount and rate
     * must be compared — comparing only the rate would leave the amount stale.
     *
     * <p>The initial values are two sentinels that can never compare equal, guaranteeing one
     * broadcast on the first tick.
     */
    private long lastBroadcastAmount = Long.MIN_VALUE;
    private double lastBroadcastRate = Double.NaN;

    private enum WorkRoutine {
        // ticks = how many ticks the displayed value is scaled by; timeLimit_s = averaging window
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
    // Persistence / network sync (server fields -> client rendering)
    // ---------------------------------------------------------------

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        writeConfiguredKey(data, registries);
        data.putDouble("lastValue", this.lastReportedValue);
        data.putString("throughput", this.lastHumanReadableValue);
        data.putInt("routine", this.workRoutine.ordinal());
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        restoreConfiguredKey(data, registries);
        this.lastReportedValue = data.getDouble("lastValue");
        this.lastHumanReadableValue = data.getString("throughput");
        this.workRoutine = WorkRoutine.fromInt(data.getInt("routine"));
    }

    /**
     * Persist the monitored key a second time under {@link #TAG_KEY}.
     *
     * <p>A key that cannot be encoded must never abort a world save (that would lose a whole chunk
     * instead of one field), so failures only log.
     */
    private void writeConfiguredKey(CompoundTag data, HolderLookup.Provider registries) {
        var key = getDisplayed();
        if (key == null) {
            data.remove(TAG_KEY);
            return;
        }
        try {
            data.put(TAG_KEY, key.toTagGeneric(registries));
        } catch (RuntimeException e) {
            data.remove(TAG_KEY);
            AELog.warn("AE MonitorNet: could not persist the monitored key of %s: %s", this, e);
        }
    }

    /**
     * Fallback read of {@link #TAG_KEY}: only used when AE2's own {@code configuredItem} did not
     * restore a key (tag absent, or present but not decodable). Re-arms the watchers so the readout
     * resumes with the restored object.
     */
    private void restoreConfiguredKey(CompoundTag data, HolderLookup.Provider registries) {
        if (getDisplayed() != null || !data.contains(TAG_KEY, Tag.TAG_COMPOUND)) {
            return;
        }

        AEKey restored = null;
        try {
            restored = AEKey.fromTagGeneric(registries, data.getCompound(TAG_KEY));
        } catch (RuntimeException e) {
            AELog.warn("AE MonitorNet: could not read the monitored key of %s: %s", this, e);
        }

        if (restored == null) {
            AELog.warn("AE MonitorNet: monitored key of %s could not be decoded (tag: %s); the part "
                    + "starts without a monitored object", this, data.getCompound(TAG_KEY));
            return;
        }

        // setConfiguredItem() marks the host for a client update, so it needs an attached host;
        // when the part is loaded from disk the host is always set before readFromNBT runs.
        if (getHost() != null) {
            setConfiguredItem(restored);
            configureWatchers();
        }
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
    public void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeDouble(this.lastReportedValue);
        data.writeUtf(this.lastHumanReadableValue);
        data.writeInt(this.workRoutine.ordinal());
    }

    @Override
    public boolean readFromStream(RegistryFriendlyByteBuf data) {
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
    // Interaction: the configurator cycles the routine; everything else (configure key / clear /
    // lock) is handled by AE2's monitor base class.
    // ---------------------------------------------------------------

    @Override
    public boolean onUseItemOn(ItemStack heldItem, Player player, InteractionHand hand, Vec3 pos) {
        if (!isClientSide()) {
            if (heldItem.getItem() instanceof ThroughputMonitorConfigurator) {
                cycleRoutine();
                return true;
            }
        }

        boolean result = super.onUseItemOn(heldItem, player, hand, pos);

        // The base class may have just set/cleared the displayed key; wake or sleep the device so
        // sampling starts/stops immediately.
        if (!isClientSide()) {
            syncTickingState();
        }

        return result;
    }

    /** Cycle to the next display routine (per t/s/m/10m). Also called by the GUI unit switches. */
    public void cycleRoutine() {
        this.workRoutine = WorkRoutine.cycle(this.workRoutine);

        // The routine changed -> the panel text must change now, bypass the broadcast collapse
        invalidateBroadcastCache();

        getHost().markForSave();
        getHost().markForUpdate();

        // Ask for one sample right away so the readout refreshes quickly
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    /** Ordinal of the current routine (0=tick 1=second 2=minute 3=10min). */
    public int getRoutineOrdinal() {
        return this.workRoutine.ordinal();
    }

    /** How many ticks the current routine represents (displayed value = per-tick amount x this). */
    public int getRoutineTicks() {
        return this.workRoutine.ticks;
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));

        super.onMainNodeStateChanged(reason);
    }

    private void syncTickingState() {
        // The displayed key was just set/cleared -> the panel must change now
        invalidateBroadcastCache();

        getMainNode().ifPresent((grid, node) -> {
            var tickManager = grid.getTickManager();
            if (getDisplayed() == null) {
                tickManager.sleepDevice(node);
            } else {
                tickManager.wakeDevice(node);
                // Sample the current amount immediately to avoid a stale first screen
                long now = TickHandler.instance().getCurrentTick();
                this.cache.push(getAmount(), now);
            }
        });
    }

    // ---------------------------------------------------------------
    // Grid ticking / rate computation (follows Advanced AE)
    // ---------------------------------------------------------------

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        // ⚠️ How fast a part may tick directly bounds the sampling rate: AE2's TickManagerService
        // uses TickingRequest.minTickRate() as "minimum ticks between two ticks". Hard-coding 20
        // here used to lock sampling to >= 1 sample/second, which is why lowering
        // samplingIntervalTicks to 10 had no effect.
        int sampling = samplingIntervalFromConfig();
        int minTicks = Math.max(1, Math.min(20, sampling));
        int maxTicks = Math.max(100, minTicks);
        // 1.21.1: TickingRequest(min, max, isSleeping) -- `canBeAlerted` was removed in AE2 19.x
        return new TickingRequest(minTicks, maxTicks, !isActive() || getDisplayed() == null);
    }

    /**
     * Sampling interval (ticks) — server-wide config, also used by
     * {@link AdvancedThroughputMonitorPart} for its accounting.
     *
     * @return the configured {@code samplingIntervalTicks} (falls back to 20 = 1 second)
     */
    protected static int samplingIntervalFromConfig() {
        try {
            return Math.max(1, com.cheesexiaolv.monitornet.MonitorNetConfig.SAMPLING_INTERVAL_TICKS.get());
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

        // Long time without sampling (just powered / just configured): push one point and come
        // back soon
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
            // Routine just changed: clear the readout until the new window has stable data
            this.lastHumanReadableValue = "";
        }

        updateState(currentAmount, currentTick);

        // Broadcast collapse: only markForUpdate() when amount or rate really changed.
        if (shouldBroadcast(currentAmount)) {
            getHost().markForUpdate();
        }

        // Sampling-pace self correction: never return SLOWER unconditionally (SLOWER = +1 tick
        // towards maxTickRate, FASTER = -2 towards minTickRate, SAME = keep).
        int interval = samplingIntervalFromConfig();
        if (ticksSinceLast > interval) {
            return TickRateModulation.FASTER;
        }
        return TickRateModulation.SAME;
    }

    /**
     * Whether the panel content differs from what was last broadcast. Amount and rate are both
     * compared; on success the new content is remembered for the caller.
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
     * Invalidate the "panel content" cache so the next {@code tickingRequest} always broadcasts.
     * Used for one-off events (new key, new routine, cleared cache).
     */
    protected void invalidateBroadcastCache() {
        this.lastBroadcastAmount = Long.MIN_VALUE;
        this.lastBroadcastRate = Double.NaN;
    }

    /**
     * Format a rate for display: above 10 or exactly 0 use AE2's amount abbreviation (e.g. 1.2k),
     * otherwise keep two decimals (the magnitude is always positive; the sign is added by the caller).
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
        // Power loss / cleared key: the readout is zero, so the next lit-up must broadcast once
        invalidateBroadcastCache();
    }

    // ---------------------------------------------------------------
    // Dynamic rendering: icon + amount + rate text (follows Advanced AE)
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
        BlockOrientation orientation = BlockOrientation.get(getSide(), getSpin());
        BlockEntityRenderHelper.rotateToFace(poseStack, orientation);

        // Icon and amount
        poseStack.translate(0, 0.1, 0.5);
        BlockEntityRenderHelper.renderItem2dWithAmount(
                poseStack, buffers, getDisplayed(), getAmount(), canCraft(),
                0.3F, -0.15F, getColor().contrastTextColor, getLevel());

        // Rate text
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
     * Unit suffix derived from the key type.
     *
     * <p>Note: AE2's {@code formatAmount} already converts types whose
     * {@code getAmountPerUnit() > 1} into display units (2400 mB -> 2.4), so only the unit symbol
     * itself ("B") is appended here — adding an "m" prefix would be a magnitude error.
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

    /** Draw one centred line of small text on the block face. */
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
    // Readout accessors for the GUI (advanced monitor menu/screen)
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

    /** Clear the sampling cache (used externally, e.g. when the advanced monitor's slot changes). */
    protected void clearSampleCache() {
        this.cache.clear();
        this.lastHumanReadableValue = "";
        invalidateBroadcastCache();
    }
}
