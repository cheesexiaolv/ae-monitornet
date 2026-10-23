package com.cheesexiaolv.monitornet.xmod.ae2wtlib;

import com.cheesexiaolv.monitornet.client.gui.WirelessThroughputTerminalScreen;
import com.cheesexiaolv.monitornet.menu.ThroughputTerminalWirelessMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.style.ScreenStyle;
import appeng.core.AELog;

import de.mari_023.ae2wtlib.wut.CycleTerminalButton;
import de.mari_023.ae2wtlib.wut.IUniversalTerminalCapable;
import de.mari_023.ae2wtlib.wut.ItemWUT;

/**
 * 「感知通用无线终端（WUT）」的无线吞吐终端界面。
 *
 * <p>为什么需要它：把无线终端<b>合成进通用终端</b>后，界面上必须有一个「切换到下一个终端」的按钮
 * （AE2WTLib 自带的 {@code WETScreen} / {@code WATScreen} 都在构造器里做
 * {@code if (getMenu().isWUT()) addToLeftToolbar(new CycleTerminalButton(...))}）。
 * 本工程的无线终端界面是自己写的 {@link WirelessThroughputTerminalScreen}，没有那一步，
 * 于是从通用终端里打开后<b>卡在这个终端里出不去</b>。
 *
 * <p>{@code IUniversalTerminalCapable} 需要的另一个方法 {@code isHandlingRightClick()} 由 AE2 的
 * {@code AEBaseScreen} 提供（它带 {@code handlingRightClick} 字段），所以这里只需实现
 * {@link #storeState()}。
 *
 * <p>这类只对 AE2WTLib 有意义的代码放在 {@code xmod} 包里：只有确认装了 AE2WTLib 时才会加载
 * （见 {@link AE2WtLibClientHook}）；没装时用的仍是普通界面，软依赖不受影响。
 */
public class WutThroughputTerminalScreen extends WirelessThroughputTerminalScreen
        implements IUniversalTerminalCapable {

    /** 只报一次的排障日志：确认"从通用终端打开时确实加了切换按钮"。 */
    private static boolean loggedSwitchButton;

    public WutThroughputTerminalScreen(ThroughputTerminalWirelessMenu menu, Inventory playerInventory,
            Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        // 与 AE2WTLib 自带终端同一判定：手里这件物品是通用终端才给切换按钮
        if (menu.getTerminalStack().getItem() instanceof ItemWUT) {
            addToLeftToolbar(new CycleTerminalButton(btn -> cycleTerminal()));
            if (!loggedSwitchButton) {
                loggedSwitchButton = true;
                AELog.info("AE MonitorNet: the wireless throughput terminal was opened from the universal terminal; "
                        + "the 'cycle terminal' button has been added to the left toolbar");
            }
        }
    }

    /** 切走之前要保存的界面状态：本终端没有额外状态（与 AE2WTLib 的 {@code WATScreen#storeState} 一致）。 */
    @Override
    public void storeState() {
        // 无额外状态
    }
}
