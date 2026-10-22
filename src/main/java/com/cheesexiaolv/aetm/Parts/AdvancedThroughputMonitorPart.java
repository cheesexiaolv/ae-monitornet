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

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Advanced throughput monitor.
 *
 * <p>Like the normal monitor it uses the base class's watcher to track the stored amount of the
 * monitored object (cheap, high-frequency sampling). On top of that it has a "config slot"
 * (level-emitter style) that selects the monitored object: right-clicking opens the
 * {@link AdvancedThroughputMonitorMenu} GUI; put an item/fluid into the config slot to make it the
 * monitored object.
 *
 * <p>It has none of the normal monitor's "lock" mechanism (shift+right-click also opens the GUI);
 * fluid amounts can be switched between mB/B.
 *
 * <p><b>1.20.1 → 1.21.1 changes</b>
 * <ul>
 *   <li>{@code IPart#onPartActivate}/{@code onPartShiftActivate} are gone; in 1.21.1 every click is
 *       routed through {@code onUseItemOn}/{@code onUseWithoutItem} (evidence:
 *       {@code reference/AE2-1.21.1-19.2.17/appeng/api/parts/IPart.java:126,130}). Both open the
 *       GUI here, which keeps the old behaviour of "shift is not special on the advanced monitor".
 *       (The memory-card branch of {@code AEBasePart#onUseItemOn} is not reachable from a subclass
 *       of the monitor; this monitor stores no part-level settings on the card anyway.)</li>
 *   <li>{@code ConfigInventory.configTypes(int, Runnable)} became a builder:
 *       {@code ConfigInventory.configTypes(1).changeListener(...).build()}; the builder defaults to
 *       all key types, which matches the old no-filter factory
 *       (evidence: {@code reference/AE2-1.21.1-19.2.17/appeng/util/ConfigInventory.java:45,149,196,206}
 *       vs. the 1.20.1 {@code ConfigInventory.java:52}).</li>
 *   <li>{@code writeToNBT}/{@code readFromNBT} gained a {@code HolderLookup.Provider}
 *       (evidence: {@code appeng/parts/reporting/AbstractMonitorPart.java:83,94}).</li>
 * </ul>
 */
public class AdvancedThroughputMonitorPart extends ThroughputMonitorPart implements IAdvancedThroughputMonitorHost {

    private final ConfigInventory config = ConfigInventory.configTypes(1)
            .changeListener(this::onConfigChanged)
            .build();

    /** Rate history (for the line chart). Capacity and sampling interval both come from the config. */
    private final ThroughputHistory history = new ThroughputHistory(capacityFromConfig());

    /** Tick accumulator until the next sample. */
    private int sampleAccumulator;

    /** Time window shown by the line chart. */
    private ThroughputHistory.Window chartWindow = ThroughputHistory.Window.M1;

    /** Quantity shown by the line chart (storage / rate / rate acceleration). */
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

        // Record at the configured sampling interval (amount + tick gap) for the line chart.
        //
        // ⚠️ The accumulator here counts "ticks actually elapsed" (ticksSinceLast); how fast the
        // part may tick is decided by the base class's getTickingRequest (minTickRate now follows the
        // sampling interval). With interval=10 and the part called every 10 ticks, this method adds
        // 10 per call -> exactly one point, accumulator resets: no double, no miss; if AE2 is a bit
        // late (diff slightly > interval), the real gap is pushed and ThroughputHistory converts it
        // correctly.
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
    // Line chart: time window / metric
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
     * Series for the given settings (does not change this part's own settings).
     *
     * <p>The aggregated terminal has to fetch each row's curve with that row's own window/metric/unit,
     * hence the conversion lives here:
     *
     * @param routineTicks rate routine in ticks (the terminal normalises rates to its own routine)
     * @param largeUnit    convert to the large unit (fluid B; types whose
     *                     {@code getAmountPerUnit()} is 1 are unaffected)
     */
    public ThroughputHistory.Series series(ThroughputHistory.Window window, ThroughputHistory.Metric metric,
            int routineTicks, boolean largeUnit) {
        var series = this.history.series(window, metric, routineTicks);
        float[] values = series.values();
        // Large-unit conversion: generic via the key type's getAmountPerUnit() (fluid 1000, items 1,
        // other types as they define it)
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

    // ------------------------------------------------------------------
    // Interaction: both click paths open the monitor GUI (no lock mechanism)
    // ------------------------------------------------------------------

    @Override
    public boolean onUseItemOn(ItemStack heldItem, Player player, InteractionHand hand, Vec3 pos) {
        return openMonitorMenu(player);
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        return openMonitorMenu(player);
    }

    private boolean openMonitorMenu(Player player) {
        if (!isClientSide()) {
            MenuOpener.open(AdvancedThroughputMonitorMenu.TYPE, player, MenuLocators.forPart(this));
        }
        return true;
    }

    /**
     * The advanced version has no "lock" mechanism: the base class's isLocked/setLocked are
     * permanently disabled (always unlocked).
     */
    @Override
    public boolean isLocked() {
        return false;
    }

    @Override
    public void setLocked(boolean locked) {
        // Ignore lock changes.
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        data.putBoolean("fluidUnitB", this.fluidUnitB);
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        this.fluidUnitB = data.getBoolean("fluidUnitB");
        // Config slot persistence: `configuredItem` is already persisted by the superclass; after
        // reading it back, sync the config slot display with the restored monitored object.
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
        // The unit/value on the panel changes -> bypass the base class's "broadcast only when the
        // content changed" collapse so the next tick always broadcasts.
        invalidateBroadcastCache();
        getHost().markForSave();
        getHost().markForUpdate();
    }

    /**
     * Config slot changed: update the monitored object and re-arm the watchers.
     *
     * <p>AE2 12.9.9's {@code AbstractMonitorPart#configureWatchers()} was <b>private</b> (reflection
     * needed on 1.19.2); <b>AE2 15.4.10 already made it protected</b>, and 19.2.17 still is
     * ({@code reference/AE2-1.21.1-19.2.17/appeng/parts/reporting/AbstractMonitorPart.java:191}), so
     * it is called directly here — no reflection, no failure path.
     */
    private void onConfigChanged() {
        var key = this.config.getKey(0);
        setConfiguredItem(key);

        // After switching the monitored object the watchers must watch the new one immediately
        // (the amount is refreshed along with it).
        configureWatchers();

        clearSampleCache();
        // Different monitored object -> restart the history curve from scratch
        this.history.clear();
        getHost().markForUpdate();
    }
}
