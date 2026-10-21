package com.cheesexiaolv.aetm.Logic;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import appeng.api.stacks.AEKey;

/**
 * 终端显示设置的 NBT 读写（面板零件与无线终端物品共用）。
 *
 * <p>键名前缀自 1.1.0（modid 改为 {@code ae_monitornet}）起是 {@code ae_monitornet*}；
 * 改名前的 {@code aetm*} 旧键仍然<b>回退可读</b>，所以旧存档里的零件/物品不会丢设置。
 * 写入一律只写新键。
 *
 * <p>面板零件把设置写在自己的零件 NBT 上，无线终端写在物品 NBT 上，格式完全相同。
 */
public final class TerminalSettingsNbt {

    /** 当前键名前缀。 */
    private static final String PREFIX = "ae_monitornet";

    /** 改名前的旧键前缀，只用于读取回退。 */
    private static final String LEGACY_PREFIX = "aetm";

    private static final String KEY_WINDOW = PREFIX + "Window";
    private static final String KEY_METRIC = PREFIX + "Metric";
    private static final String KEY_LARGE_UNIT = PREFIX + "LargeUnit";
    private static final String KEY_ROUTINE = PREFIX + "Routine";
    private static final String KEY_VIEW = PREFIX + "View";
    private static final String KEY_OVERRIDES = PREFIX + "Overrides";
    private static final String KEY_STYLE = PREFIX + "Style";

    private static final String OLD_KEY_WINDOW = LEGACY_PREFIX + "Window";
    private static final String OLD_KEY_METRIC = LEGACY_PREFIX + "Metric";
    private static final String OLD_KEY_LARGE_UNIT = LEGACY_PREFIX + "LargeUnit";
    private static final String OLD_KEY_ROUTINE = LEGACY_PREFIX + "Routine";
    private static final String OLD_KEY_VIEW = LEGACY_PREFIX + "View";
    private static final String OLD_KEY_OVERRIDES = LEGACY_PREFIX + "Overrides";
    private static final String OLD_KEY_STYLE = LEGACY_PREFIX + "Style";

    /** 终端尺寸档位默认值：满（3 = TerminalStyle.FULL）。 */
    public static final int DEFAULT_STYLE = 3;

    private TerminalSettingsNbt() {
    }

    /** 读整型：新键优先，新键不存在时回退旧键（旧键也没有时返回 0，与改名前的行为一致）。 */
    private static int getInt(CompoundTag data, String key, String legacyKey) {
        if (data.contains(key)) {
            return data.getInt(key);
        }
        return data.getInt(legacyKey);
    }

    /** 读布尔：新键优先，新键不存在时回退旧键。 */
    private static boolean getBoolean(CompoundTag data, String key, String legacyKey) {
        if (data.contains(key)) {
            return data.getBoolean(key);
        }
        return data.getBoolean(legacyKey);
    }

    public static void writeGlobal(CompoundTag data, TerminalSettings settings) {
        data.putInt(KEY_WINDOW, settings.window().ordinal());
        data.putInt(KEY_METRIC, settings.metric().ordinal());
        data.putBoolean(KEY_LARGE_UNIT, settings.largeUnit());
        data.putInt(KEY_ROUTINE, settings.routineOrdinal());
    }

    public static TerminalSettings readGlobal(CompoundTag data) {
        return new TerminalSettings(
                ThroughputHistory.Window.byOrdinal(getInt(data, KEY_WINDOW, OLD_KEY_WINDOW)),
                ThroughputHistory.Metric.byOrdinal(getInt(data, KEY_METRIC, OLD_KEY_METRIC)),
                getBoolean(data, KEY_LARGE_UNIT, OLD_KEY_LARGE_UNIT),
                getInt(data, KEY_ROUTINE, OLD_KEY_ROUTINE));
    }

    public static void writeViewMode(CompoundTag data, int viewMode) {
        data.putInt(KEY_VIEW, viewMode);
    }

    public static int readViewMode(CompoundTag data) {
        return getInt(data, KEY_VIEW, OLD_KEY_VIEW) == TerminalSettings.VIEW_CHART
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
        if (data.contains(OLD_KEY_STYLE)) {
            return Math.max(0, Math.min(3, data.getInt(OLD_KEY_STYLE)));
        }
        return DEFAULT_STYLE;
    }

    public static void writeOverrides(CompoundTag data, Map<AEKey, TerminalSettings> overrides) {
        if (overrides.isEmpty()) {
            data.remove(KEY_OVERRIDES);
            // 清空时连旧键一起删，否则下次读取会从旧键里把已经清掉的覆盖项又读回来
            data.remove(OLD_KEY_OVERRIDES);
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
        if (list.isEmpty() && data.contains(OLD_KEY_OVERRIDES)) {
            list = data.getList(OLD_KEY_OVERRIDES, CompoundTag.TAG_COMPOUND);
        }
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
