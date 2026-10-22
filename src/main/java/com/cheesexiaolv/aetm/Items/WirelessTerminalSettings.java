package com.cheesexiaolv.aetm.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.cheesexiaolv.aetm.Logic.TerminalSettings;
import com.cheesexiaolv.aetm.Logic.TerminalSettingsNbt;
import com.cheesexiaolv.aetm.Parts.ITerminalSettingsHost;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import appeng.api.stacks.AEKey;

/**
 * Display settings of the wireless terminal, stored on the item itself.
 *
 * <p>Format is identical to the part ({@link TerminalSettingsNbt}), so settings can be copied
 * between the wired/wireless terminals.
 *
 * <p><b>1.20.1 → 1.21.1 changes</b>
 * <ul>
 *   <li>{@code ItemStack#getOrCreateTag()} was removed in 1.20.5+: item NBT is now a data
 *       component. The settings live in the vanilla {@code minecraft:custom_data} component and are
 *       read/written through {@link CustomData#update} / {@link CustomData#copyTag()}
 *       (same pattern AE2 19.2.17 itself uses, e.g. {@code appeng/debug/MeteoritePlacerItem.java:44,61}).</li>
 *   <li>{@code AEKey#fromTagGeneric}/{@code toTagGeneric} need a {@link HolderLookup.Provider}
 *       (evidence: {@code reference/AE2-1.21.1-19.2.17/appeng/api/stacks/AEKey.java:109,120}), so the
 *       registry lookup is passed into the constructor by the menu that owns the item host
 *       ({@code host.getPlayer().level().registryAccess()}).</li>
 * </ul>
 */
public final class WirelessTerminalSettings implements ITerminalSettingsHost {

    private final ItemStack stack;
    private final HolderLookup.Provider registries;

    public WirelessTerminalSettings(ItemStack stack, HolderLookup.Provider registries) {
        this.stack = stack;
        this.registries = registries;
    }

    /** A copy of the settings tag (writes go through {@link #updateTag}). */
    private CompoundTag tag() {
        return this.stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    /** Read-modify-write the settings tag on the item stack. */
    private void updateTag(Consumer<CompoundTag> action) {
        CustomData.update(DataComponents.CUSTOM_DATA, this.stack, action);
    }

    @Override
    public TerminalSettings getGlobalSetting() {
        return TerminalSettingsNbt.readGlobal(tag());
    }

    @Override
    public TerminalSettings getSetting(AEKey key) {
        var override = TerminalSettingsNbt.readOverrides(tag(), this.registries).get(key);
        return override != null ? override : getGlobalSetting();
    }

    @Override
    public void setGlobalSetting(TerminalSettings setting) {
        updateTag(tag -> {
            TerminalSettingsNbt.writeGlobal(tag, setting);
            // Same as the wired version: the global button clears all per-row overrides.
            TerminalSettingsNbt.writeOverrides(tag, Map.of(), this.registries);
        });
    }

    @Override
    public void setRowSetting(AEKey key, TerminalSettings setting) {
        updateTag(tag -> {
            var overrides = new HashMap<>(TerminalSettingsNbt.readOverrides(tag, this.registries));
            overrides.put(key, setting);
            TerminalSettingsNbt.writeOverrides(tag, overrides, this.registries);
        });
    }

    @Override
    public int getViewMode() {
        return TerminalSettingsNbt.readViewMode(tag());
    }

    @Override
    public void setViewMode(int viewMode) {
        updateTag(tag -> TerminalSettingsNbt.writeViewMode(tag, viewMode));
    }

    @Override
    public int getTerminalStyle() {
        return TerminalSettingsNbt.readStyle(tag());
    }

    @Override
    public void setTerminalStyle(int styleOrdinal) {
        updateTag(tag -> TerminalSettingsNbt.writeStyle(tag, Math.max(0, Math.min(3, styleOrdinal))));
    }
}
