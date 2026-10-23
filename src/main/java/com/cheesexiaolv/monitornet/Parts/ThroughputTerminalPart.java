package com.cheesexiaolv.monitornet.Parts;

import java.util.HashMap;
import java.util.Map;

import com.cheesexiaolv.monitornet.AeMonitorNet;
import com.cheesexiaolv.monitornet.Logic.TerminalSettings;
import com.cheesexiaolv.monitornet.Logic.TerminalSettingsNbt;
import com.cheesexiaolv.monitornet.menu.ThroughputTerminalMenu;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.core.AppEng;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractDisplayPart;

/**
 * Aggregated throughput terminal (wired panel part).
 *
 * <p>It does not watch a single item; it <b>enumerates every throughput monitor on the same ME
 * network</b> (walks {@code IGrid#getMachineClasses()} and filters by assignability, see the menu),
 * merges them into rows by monitored key and hands the row data to
 * {@link ThroughputTerminalMenu} for client sync.
 *
 * <p>Display settings have two layers: global defaults (window / metric / unit / rate routine) plus
 * per-row overrides. Global buttons rewrite the defaults and clear all row overrides; row buttons
 * change only that row. Settings are exposed through {@link ITerminalSettingsHost}; the wireless
 * terminal (item) implements the same interface, so both share one menu/screen.
 *
 * <p>In-world looks follow AE2's own reporting parts on the exact same model chain (the one AE2's
 * {@code StorageMonitorPart} uses for its monitor): geometry and front plate come from AE2's
 * {@code part/display_base} ({@code front} = {@code ae2:part/monitor_front}), and only the
 * highlight layers are this mod's own {@code ae_monitornet:part/terminal_on|_off} (children of
 * AE2's {@code part/pattern_access_terminal_on|_off}, replacing {@code lightsMedium} /
 * {@code lightsBright} / {@code lightsDark} with {@code ae_monitornet:part/terminal_front_*}),
 * plus AE2's status-strip models.
 *
 * <p><b>1.20.1 → 1.21.1 changes</b>
 * <ul>
 *   <li>{@code IPart#onPartActivate(Player, InteractionHand, Vec3)} is gone; it was split into
 *       {@code onUseItemOn(ItemStack, Player, InteractionHand, Vec3)} and
 *       {@code onUseWithoutItem(Player, Vec3)} (evidence:
 *       {@code reference/AE2-1.21.1-19.2.17/appeng/api/parts/IPart.java:126,130}). Both are
 *       implemented: the item-held path first lets AE2 handle its memory card (same order as
 *       {@code appeng/parts/AEBasePart.java:311}), then opens the terminal; the empty-hand path
 *       opens the terminal directly (this was also the 1.20.1 behaviour — the inherited
 *       wrench-rotate interaction of {@code AbstractReportingPart} was unreachable from our
 *       override then as well).</li>
 *   <li>{@code writeToNBT}/{@code readFromNBT} gained a {@code HolderLookup.Provider}
 *       (evidence: {@code appeng/api/parts/IPart.java:54,57}), which is forwarded to
 *       {@link TerminalSettingsNbt} (its {@code AEKey} serialisation is registry-aware since AE2 19).</li>
 * </ul>
 */
public class ThroughputTerminalPart extends AbstractDisplayPart implements ITerminalSettingsHost {

    // In-world rendering: AE2's part/display_base (AE2's own AbstractDisplayPart.MODEL_BASE, which
    // carries @PartModels and is therefore already registered by AE2) plus our two lights models
    // drawn on top of it. Only MODEL_OFF / MODEL_ON are ids we register with AE2's part-model baker
    // on client startup (appeng.api.parts.PartModels#registerModels, see PartModelsRegistration),
    // otherwise AE2's CableBus cannot look them up and the panel simply does not render (without any
    // "missing model" error).
    public static final ResourceLocation MODEL_BASE_PLATE = AppEng.makeId("part/display_base");
    public static final ResourceLocation MODEL_OFF =
            ResourceLocation.fromNamespaceAndPath(AeMonitorNet.MODID, "part/terminal_off");
    public static final ResourceLocation MODEL_ON =
            ResourceLocation.fromNamespaceAndPath(AeMonitorNet.MODID, "part/terminal_on");

    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE_PLATE, MODEL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE_PLATE, MODEL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL =
            new PartModel(MODEL_BASE_PLATE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    /** View constants (forwarded to {@link TerminalSettings}, used by the screen code). */
    public static final int VIEW_TEXT = TerminalSettings.VIEW_TEXT;
    public static final int VIEW_CHART = TerminalSettings.VIEW_CHART;

    /** Routine ordinal -> tick count. */
    public static int routineTicks(int routineOrdinal) {
        return TerminalSettings.routineTicks(routineOrdinal);
    }

    /** Routine ordinal -> rate unit suffix (t / s / m / 10m). */
    public static String routineUnit(int routineOrdinal) {
        return TerminalSettings.routineUnit(routineOrdinal);
    }

    // Global default display settings
    private TerminalSettings global = TerminalSettings.DEFAULT;
    private int viewMode = TerminalSettings.VIEW_TEXT;

    /** Terminal size ordinal (TerminalStyle ordinal, defaults to full). */
    private int terminalStyle = TerminalSettingsNbt.DEFAULT_STYLE;

    /** Per-row overrides (key -> settings). */
    private final Map<AEKey, TerminalSettings> overrides = new HashMap<>();

    public ThroughputTerminalPart(IPartItem<?> partItem) {
        super(partItem, true);
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    @Override
    public boolean onUseItemOn(ItemStack heldItem, Player player, InteractionHand hand, Vec3 pos) {
        // AE2 gets the first shot (memory-card import/export lives there, see AEBasePart).
        if (super.onUseItemOn(heldItem, player, hand, pos)) {
            return true;
        }
        return openTerminal(player);
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        return openTerminal(player);
    }

    /** Open the terminal menu on the server; always report the click as handled. */
    private boolean openTerminal(Player player) {
        if (!isClientSide()) {
            MenuOpener.open(ThroughputTerminalMenu.TYPE, player, MenuLocators.forPart(this));
        }
        return true;
    }

    @Override
    public IPartModel getStaticModels() {
        return this.selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    // ------------------------------------------------------------------
    // Display settings (ITerminalSettingsHost)
    // ------------------------------------------------------------------

    @Override
    public TerminalSettings getGlobalSetting() {
        return this.global;
    }

    @Override
    public TerminalSettings getSetting(AEKey key) {
        return this.overrides.getOrDefault(key, this.global);
    }

    /** Global buttons: rewrite the default and clear every per-row override. */
    @Override
    public void setGlobalSetting(TerminalSettings setting) {
        this.global = setting;
        this.overrides.clear();
        markDirty();
    }

    /** Per-row button: change only this row's override. */
    @Override
    public void setRowSetting(AEKey key, TerminalSettings setting) {
        this.overrides.put(key, setting);
        markDirty();
    }

    @Override
    public int getViewMode() {
        return this.viewMode;
    }

    @Override
    public void setViewMode(int viewMode) {
        this.viewMode = viewMode == TerminalSettings.VIEW_CHART ? TerminalSettings.VIEW_CHART
                : TerminalSettings.VIEW_TEXT;
        markDirty();
    }

    @Override
    public int getTerminalStyle() {
        return this.terminalStyle;
    }

    @Override
    public void setTerminalStyle(int styleOrdinal) {
        this.terminalStyle = Math.max(0, Math.min(3, styleOrdinal));
        markDirty();
    }

    private void markDirty() {
        getHost().markForSave();
        getHost().markForUpdate();
    }

    // ------------------------------------------------------------------
    // Persistence (key names unchanged from the earliest implementation: old saves still load)
    // ------------------------------------------------------------------

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        TerminalSettingsNbt.writeGlobal(data, this.global);
        TerminalSettingsNbt.writeViewMode(data, this.viewMode);
        TerminalSettingsNbt.writeStyle(data, this.terminalStyle);
        TerminalSettingsNbt.writeOverrides(data, this.overrides, registries);
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        this.global = TerminalSettingsNbt.readGlobal(data);
        this.viewMode = TerminalSettingsNbt.readViewMode(data);
        this.terminalStyle = TerminalSettingsNbt.readStyle(data);
        this.overrides.clear();
        this.overrides.putAll(TerminalSettingsNbt.readOverrides(data, registries));
    }
}
