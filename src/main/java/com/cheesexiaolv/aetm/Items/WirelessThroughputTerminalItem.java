package com.cheesexiaolv.aetm.Items;

import javax.annotation.Nullable;

import com.cheesexiaolv.aetm.menu.ThroughputTerminalWirelessMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.ISubMenu;

/**
 * 无线聚合吞吐终端（物品）。
 *
 * <p>直接继承 AE2 的 {@link WirelessTerminalItem}：链接、耗电（每次打开 0.5 AE）、距离判定等
 * 全部沿用 AE2 的既有行为，我们只替换"打开哪个菜单"。菜单元/界面与有线版共用，
 * 唯一区别是行数据的网络来源——由 {@link WirelessTerminalMenuHost} 解析出终端所连的网络节点。
 */
public class WirelessThroughputTerminalItem extends WirelessTerminalItem {

    /** 电量与 AE2 无线终端同量级（1.6M AE）。 */
    private static final double POWER_CAPACITY = 1_600_000.0;

    public WirelessThroughputTerminalItem(Properties properties) {
        super(() -> POWER_CAPACITY, properties);
    }

    @Override
    public MenuType<?> getMenuType() {
        return ThroughputTerminalWirelessMenu.TYPE;
    }

    @Nullable
    @Override
    public ItemMenuHost getMenuHost(Player player, int inventorySlot, ItemStack stack, @Nullable BlockPos pos) {
        return new WirelessTerminalMenuHost(player, inventorySlot, stack,
                (p, subMenu) -> openFromInventory(p, inventorySlot, true));
    }

    /** 子菜单返回主菜单时用（例如将来从终端打开某个监控器界面）。 */
    public void returnToMainMenu(Player player, int inventorySlot, ISubMenu subMenu) {
        openFromInventory(player, inventorySlot, true);
    }
}
