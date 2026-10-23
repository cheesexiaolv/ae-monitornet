package com.cheesexiaolv.monitornet.Logic;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import appeng.api.stacks.AEKey;

/**
 * NBT read/write for the terminal display settings (shared by the part and the wireless item).
 *
 * <p>Key prefix is {@code ae_monitornet*} since 1.1.0 (modid rename); keys with the
 * {@link #LEGACY_PREFIX} prefix stay <b>readable as a fallback</b>, so old parts/items keep their settings.
 * Writes only use the new keys.
 *
 * <p>1.21.1 port: AE2 19.x made generic-key serialisation registry-aware —
 * {@code AEKey.fromTagGeneric(HolderLookup.Provider, CompoundTag)} and
 * {@code AEKey#toTagGeneric(HolderLookup.Provider)} both take a registry lookup now
 * (evidence: {@code reference/AE2-1.21.1-19.2.17/appeng/api/stacks/AEKey.java:109,120}).
 * Callers pass {@code level.registryAccess()} / {@code player.level().registryAccess()}.
 */
public final class TerminalSettingsNbt {

    /** Current key prefix. */
    private static final String PREFIX = "ae_monitornet";

    /** Pre-rename key prefix, read-only fallback. */
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

    /** Default terminal style ordinal: full (3 = TerminalStyle.FULL). */
    public static final int DEFAULT_STYLE = 3;

    private TerminalSettingsNbt() {
    }

    /** Read int: new key first, old key as fallback (missing keys read as 0, as before the rename). */
    private static int getInt(CompoundTag data, String key, String legacyKey) {
        if (data.contains(key)) {
            return data.getInt(key);
        }
        return data.getInt(legacyKey);
    }

    /** Read boolean: new key first, old key as fallback. */
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

    public static void writeOverrides(CompoundTag data, Map<AEKey, TerminalSettings> overrides,
            HolderLookup.Provider registries) {
        if (overrides.isEmpty()) {
            data.remove(KEY_OVERRIDES);
            // Also drop the legacy key, otherwise the next read resurrects the cleared overrides
            data.remove(OLD_KEY_OVERRIDES);
            return;
        }
        var list = new ListTag();
        for (var entry : overrides.entrySet()) {
            var tag = new CompoundTag();
            tag.put("key", entry.getKey().toTagGeneric(registries));
            tag.putInt("w", entry.getValue().window().ordinal());
            tag.putInt("m", entry.getValue().metric().ordinal());
            tag.putBoolean("u", entry.getValue().largeUnit());
            tag.putInt("r", entry.getValue().routineOrdinal());
            list.add(tag);
        }
        data.put(KEY_OVERRIDES, list);
    }

    public static Map<AEKey, TerminalSettings> readOverrides(CompoundTag data, HolderLookup.Provider registries) {
        var list = data.getList(KEY_OVERRIDES, CompoundTag.TAG_COMPOUND);
        if (list.isEmpty() && data.contains(OLD_KEY_OVERRIDES)) {
            list = data.getList(OLD_KEY_OVERRIDES, CompoundTag.TAG_COMPOUND);
        }
        var result = new HashMap<AEKey, TerminalSettings>();
        for (int i = 0; i < list.size(); i++) {
            var tag = list.getCompound(i);
            var key = AEKey.fromTagGeneric(registries, tag.getCompound("key"));
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
