package com.cheesexiaolv.aetm.Items;

import java.util.HashMap;
import java.util.Map;

import com.cheesexiaolv.aetm.Logic.TerminalSettings;
import com.cheesexiaolv.aetm.Logic.TerminalSettingsNbt;
import com.cheesexiaolv.aetm.Parts.ITerminalSettingsHost;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEKey;

/**
 * 无线终端的显示设置：直接存在物品 NBT 上。
 *
 * <p>格式与面板零件完全相同（共用 {@link TerminalSettingsNbt}），所以有线的习惯可以直接搬过来。
 */
public final class WirelessTerminalSettings implements ITerminalSettingsHost {

    private final ItemStack stack;

    public WirelessTerminalSettings(ItemStack stack) {
        this.stack = stack;
    }

    private CompoundTag tag() {
        return this.stack.getOrCreateTag();
    }

    @Override
    public TerminalSettings getGlobalSetting() {
        return TerminalSettingsNbt.readGlobal(tag());
    }

    @Override
    public TerminalSettings getSetting(AEKey key) {
        var override = TerminalSettingsNbt.readOverrides(tag()).get(key);
        return override != null ? override : getGlobalSetting();
    }

    @Override
    public void setGlobalSetting(TerminalSettings setting) {
        var tag = tag();
        TerminalSettingsNbt.writeGlobal(tag, setting);
        // 与有线版一致：全局按钮会清空所有单行覆盖
        TerminalSettingsNbt.writeOverrides(tag, Map.of());
    }

    @Override
    public void setRowSetting(AEKey key, TerminalSettings setting) {
        var tag = tag();
        var overrides = new HashMap<>(TerminalSettingsNbt.readOverrides(tag));
        overrides.put(key, setting);
        TerminalSettingsNbt.writeOverrides(tag, overrides);
    }

    @Override
    public int getViewMode() {
        return TerminalSettingsNbt.readViewMode(tag());
    }

    @Override
    public void setViewMode(int viewMode) {
        TerminalSettingsNbt.writeViewMode(tag(), viewMode);
    }

    @Override
    public int getTerminalStyle() {
        return TerminalSettingsNbt.readStyle(tag());
    }

    @Override
    public void setTerminalStyle(int styleOrdinal) {
        TerminalSettingsNbt.writeStyle(tag(), Math.max(0, Math.min(3, styleOrdinal)));
    }
}
