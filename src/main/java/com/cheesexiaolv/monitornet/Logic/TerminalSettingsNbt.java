package com.cheesexiaolv.monitornet.Logic;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import appeng.api.stacks.AEKey;

/**
 * 终端显示设置的 NBT 读写（面板零件与无线终端物品共用）。
 *
 * <p>面板零件把设置写在自己的零件 NBT 上，无线终端写在物品 NBT 上，格式完全相同。
 */
public final class TerminalSettingsNbt {

    /** 当前键名前缀。 */
    private static final String PREFIX = "ae_monitornet";

    private static final String KEY_WINDOW = PREFIX + "Window";
    private static final String KEY_METRIC = PREFIX + "Metric";
    private static final String KEY_LARGE_UNIT = PREFIX + "LargeUnit";
    private static final String KEY_ROUTINE = PREFIX + "Routine";
    private static final String KEY_VIEW = PREFIX + "View";
    private static final String KEY_OVERRIDES = PREFIX + "Overrides";
    private static final String KEY_STYLE = PREFIX + "Style";


    /** 终端尺寸档位默认值：满（3 = TerminalStyle.FULL）。 */
    public static final int DEFAULT_STYLE = 3;

    private TerminalSettingsNbt() {
    }

    public static void writeGlobal(CompoundTag data, TerminalSettings settings) {
        data.putInt(KEY_WINDOW, settings.window().ordinal());
        data.putInt(KEY_METRIC, settings.metric().ordinal());
        data.putBoolean(KEY_LARGE_UNIT, settings.largeUnit());
        data.putInt(KEY_ROUTINE, settings.routineOrdinal());
    }

    public static TerminalSettings readGlobal(CompoundTag data) {
        return new TerminalSettings(
                ThroughputHistory.Window.byOrdinal(data.getInt(KEY_WINDOW)),
                ThroughputHistory.Metric.byOrdinal(data.getInt(KEY_METRIC)),
                data.getBoolean(KEY_LARGE_UNIT),
                data.getInt(KEY_ROUTINE));
    }

    public static void writeViewMode(CompoundTag data, int viewMode) {
        data.putInt(KEY_VIEW, viewMode);
    }

    public static int readViewMode(CompoundTag data) {
        return data.getInt(KEY_VIEW) == TerminalSettings.VIEW_CHART
                ? TerminalSettings.VIEW_CHART
                : TerminalSettings.VIEW_TEXT;
    }

    public static void writeStyle(CompoundTag data, int styleOrdinal) {
        data.putInt(KEY_STYLE, styleOrdinal);
    }

    public static int readStyle(CompoundTag data) {
        if (data.contains(KEY_STYLE)) {
            return Math.max(0, Math.min(3, data.getInt(KEY_STYLE)));
        }
        return DEFAULT_STYLE;
    }

    public static void writeOverrides(CompoundTag data, Map<AEKey, TerminalSettings> overrides) {
        if (overrides.isEmpty()) {
            data.remove(KEY_OVERRIDES);
            return;
        }
        var list = new ListTag();
        for (var entry : overrides.entrySet()) {
            var tag = new CompoundTag();
            tag.put("key", entry.getKey().toTagGeneric());
            tag.putInt("w", entry.getValue().window().ordinal());
            tag.putInt("m", entry.getValue().metric().ordinal());
            tag.putBoolean("u", entry.getValue().largeUnit());
            tag.putInt("r", entry.getValue().routineOrdinal());
            list.add(tag);
        }
        data.put(KEY_OVERRIDES, list);
    }

    public static Map<AEKey, TerminalSettings> readOverrides(CompoundTag data) {
        var list = data.getList(KEY_OVERRIDES, CompoundTag.TAG_COMPOUND);
        var result = new HashMap<AEKey, TerminalSettings>();
        for (int i = 0; i < list.size(); i++) {
            var tag = list.getCompound(i);
            var key = AEKey.fromTagGeneric(tag.getCompound("key"));
            if (key == null) {
                continue;
            }
            result.put(key, new TerminalSettings(
                    ThroughputHistory.Window.byOrdinal(tag.getInt("w")),
                    ThroughputHistory.Metric.byOrdinal(tag.getInt("m")),
                    tag.getBoolean("u"),
                    tag.getInt("r")));
        }
        return result;
    }
}
