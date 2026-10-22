package com.cheesexiaolv.aetm.Parts;

import com.cheesexiaolv.aetm.Logic.TerminalSettings;

import appeng.api.stacks.AEKey;

/**
 * "Display settings host" of the aggregated terminal.
 *
 * <p>The wired part stores the settings on the part ({@link ThroughputTerminalPart}), the wireless
 * item stores them in item NBT ({@code WirelessTerminalSettings}); the menu only depends on this
 * interface, so both terminals share one menu/screen implementation.
 *
 * <p>1.21.1 port: unchanged.
 */
public interface ITerminalSettingsHost {

    /** Global defaults (new rows are displayed with them). */
    TerminalSettings getGlobalSetting();

    /** Effective settings of one row (row override if present, else the global default). */
    TerminalSettings getSetting(AEKey key);

    /** Global button: replace the default and clear all per-row overrides. */
    void setGlobalSetting(TerminalSettings setting);

    /** Per-row button: change only this row. */
    void setRowSetting(AEKey key, TerminalSettings setting);

    /** View: {@link TerminalSettings#VIEW_TEXT} or {@link TerminalSettings#VIEW_CHART}. */
    int getViewMode();

    void setViewMode(int viewMode);

    /**
     * Terminal size ordinal ({@code appeng.api.config.TerminalStyle} ordinal: 0=short 1=medium
     * 2=tall 3=full).
     *
     * <p>Deliberately <b>not</b> AE2's global {@code AEConfig.terminalStyle}: that is a client-wide
     * setting defaulting to "short", which would make the aggregated terminal open with two rows.
     * Stored per terminal, defaulting to "full".
     */
    int getTerminalStyle();

    void setTerminalStyle(int styleOrdinal);
}
