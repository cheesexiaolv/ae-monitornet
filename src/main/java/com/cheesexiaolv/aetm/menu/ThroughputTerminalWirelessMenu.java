package com.cheesexiaolv.aetm.menu;

import com.cheesexiaolv.aetm.Items.WirelessTerminalSettings;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import appeng.helpers.WirelessTerminalMenuHost;
import appeng.menu.implementations.MenuTypeBuilder;

/**
 * 无线聚合吞吐终端的服务端菜单。
 *
 * <p>行枚举/合并/同步/图表逻辑全部继承自 {@link ThroughputTerminalMenu}——那套代码只依赖
 * {@code getActionHost().getActionableNode()} 拿网络，对"零件"和"无线终端物品"是一样的；
 * 显示设置则换成存在物品 NBT 上的 {@link WirelessTerminalSettings}。
 */
public class ThroughputTerminalWirelessMenu extends ThroughputTerminalMenu {

    public static final MenuType<ThroughputTerminalWirelessMenu> TYPE = MenuTypeBuilder
            .create(ThroughputTerminalWirelessMenu::new, WirelessTerminalMenuHost.class)
            .withMenuTitle(host -> Component.translatable("item.aetm.wireless_throughput_terminal"))
            .build("wireless_throughput_terminal");

    public ThroughputTerminalWirelessMenu(int id, Inventory playerInventory, WirelessTerminalMenuHost host) {
        super(TYPE, id, playerInventory, host, new WirelessTerminalSettings(host.getItemStack()));
    }

    /**
     * 打开本菜单的无线终端物品（普通无线终端，或装了本终端的通用无线终端）。
     *
     * <p>刻意只用 AE2 的类型：本工程对 AE2WTLib 是<b>软依赖</b>，这个菜单类在没装 AE2WTLib
     * 的整合包里也会被加载，"它是不是通用终端"的判断放在
     * {@code xmod.ae2wtlib.WutThroughputTerminalScreen} 里。
     */
    public ItemStack getTerminalStack() {
        return itemMenuHost == null ? ItemStack.EMPTY : itemMenuHost.getItemStack();
    }
}
