package com.cheesexiaolv.monitornet.xmod.ae2wtlib;

import com.cheesexiaolv.monitornet.menu.ThroughputTerminalWirelessMenu;

import appeng.init.client.InitScreens;

/**
 * AE2WTLib 的<b>客户端</b>软依赖入口。
 *
 * <p>与 {@link AE2WtLibPlugin} 同一套隔离规矩：这个类允许引用 {@code de.mari_023.*}，
 * 但只能由"已经确认装了 AE2WTLib"的分支加载（见 {@code ClientEvents}）。
 */
public final class AE2WtLibClientHook {

    private AE2WtLibClientHook() {
    }

    /**
     * 注册「感知通用终端」的无线终端界面：装了 AE2WTLib 时用它替换普通界面，
     * 这样从通用终端里打开时左侧工具栏会有"切换到下一个终端"按钮。
     *
     * <p>普通无线终端（物品不是通用终端）走的还是同一套界面代码，只是不加那个按钮。
     */
    public static void registerWirelessTerminalScreen(String stylePath) {
        InitScreens.register(
                ThroughputTerminalWirelessMenu.TYPE,
                WutThroughputTerminalScreen::new,
                stylePath);
    }
}
