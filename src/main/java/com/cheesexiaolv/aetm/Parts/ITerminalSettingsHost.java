package com.cheesexiaolv.aetm.Parts;

import com.cheesexiaolv.aetm.Logic.TerminalSettings;

import appeng.api.stacks.AEKey;

/**
 * 聚合终端的「显示设置宿主」。
 *
 * <p>有线版把设置存在面板零件上（{@link ThroughputTerminalPart}），无线版存在物品 NBT 上
 * （{@code WirelessTerminalSettings}）；菜单只依赖这个接口，因此两种终端共用同一套菜单/界面逻辑。
 */
public interface ITerminalSettingsHost {

    /** 全局默认设置（新出现的行按它显示）。 */
    TerminalSettings getGlobalSetting();

    /** 某一行的有效设置（有单行覆盖用覆盖，否则用全局默认）。 */
    TerminalSettings getSetting(AEKey key);

    /** 全局按钮：改写全局默认并清空所有单行覆盖。 */
    void setGlobalSetting(TerminalSettings setting);

    /** 单行按钮：只改这一行。 */
    void setRowSetting(AEKey key, TerminalSettings setting);

    /** 视图：{@link TerminalSettings#VIEW_TEXT} 或 {@link TerminalSettings#VIEW_CHART}。 */
    int getViewMode();

    void setViewMode(int viewMode);

    /**
     * 终端尺寸档位（{@code appeng.api.config.TerminalStyle} 的 ordinal：0=短 1=中 2=长 3=满）。
     *
     * <p>刻意<b>不用</b> AE2 的全局 {@code AEConfig.terminalStyle}：那是客户端全局设置、默认是"短"，
     * 我们跟着它会让聚合终端一打开只有两行。这里按终端各自记，默认"满"。
     */
    int getTerminalStyle();

    void setTerminalStyle(int styleOrdinal);
}
