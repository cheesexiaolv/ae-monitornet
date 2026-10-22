package com.cheesexiaolv.aetm.client.gui;

import com.cheesexiaolv.aetm.menu.ThroughputTerminalWirelessMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.style.ScreenStyle;

/**
 * 无线聚合吞吐终端的客户端界面。
 *
 * <p>版式与有线版完全相同（同一张贴图、同一份样式 JSON、同一套绘制代码），
 * 只是菜单类型不同，所以这里只做一层薄壳——与 AE2 的无线终端界面思路一致。
 */
public class WirelessThroughputTerminalScreen extends ThroughputTerminalScreen<ThroughputTerminalWirelessMenu> {

    public WirelessThroughputTerminalScreen(ThroughputTerminalWirelessMenu menu, Inventory playerInventory,
            Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
