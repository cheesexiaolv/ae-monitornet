package com.cheesexiaolv.monitornet.xmod.ae2wtlib;

import com.cheesexiaolv.monitornet.menu.ThroughputTerminalWirelessMenu;

import appeng.init.client.InitScreens;

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * AE2WTLib 的<b>客户端</b>软依赖入口。
 *
 * <p>与 {@link AE2WtLibPlugin} 同一套隔离规矩：这个类允许引用 {@code de.mari_023.*}，
 * 但只能由"已经确认装了 AE2WTLib"的分支加载（见 {@code ClientEvents#registerScreens}）。
 */
public final class AE2WtLibClientHook {

    private AE2WtLibClientHook() {
    }

    /**
     * 给无线终端菜单换一个「感知通用终端」的界面：装了 AE2WTLib 时用它替换普通界面，
     * 这样从通用终端里打开时左侧工具栏会有"切换终端"按钮，热键切换也生效。
     *
     * <p>普通无线终端（物品不是通用终端）走的还是同一套界面代码，只是不加那个按钮
     * （见 {@link WutThroughputTerminalScreen} 构造器里的 {@code instanceof ItemWUT} 判定）。
     *
     * <p>1.21.1 的屏幕注册时机是 NeoForge 的 {@link RegisterMenuScreensEvent}
     * （与 {@code ClientEvents} 里其它三个屏幕完全一致；1.20.1 是
     * {@code FMLClientSetupEvent#enqueueWork} 里的无参 {@code InitScreens.register}）。
     */
    public static void registerWirelessTerminalScreen(RegisterMenuScreensEvent event) {
        InitScreens.register(
                event,
                ThroughputTerminalWirelessMenu.TYPE,
                WutThroughputTerminalScreen::new,
                "/screens/throughput_terminal.json");
    }
}
