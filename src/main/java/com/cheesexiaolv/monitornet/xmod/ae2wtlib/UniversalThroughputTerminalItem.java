package com.cheesexiaolv.monitornet.xmod.ae2wtlib;

import javax.annotation.Nullable;

import com.cheesexiaolv.monitornet.Items.WirelessThroughputTerminalItem;
import com.cheesexiaolv.monitornet.menu.ThroughputTerminalWirelessMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import appeng.api.implementations.menuobjects.ItemMenuHost;

import de.mari_023.ae2wtlib.terminal.IUniversalWirelessTerminalItem;

/**
 * 装了 AE2WTLib 时用的无线存储监控终端物品。
 *
 * <p>只比基类多做两件事，都在 AE2WTLib 这一侧：
 * <ol>
 *   <li>实现 {@link IUniversalWirelessTerminalItem} —— {@code WUTHandler.addTerminal(...)} 要求
 *       item 是它，并且 WUT 的打开流程会调 {@code tryOpen(...)}；</li>
 *   <li>把 {@code getMenuHost(...)} 换成 {@link ThroughputTerminalWirelessMenuHost}
 *       —— 它是 {@code WTMenuHost}，因此支持量子桥接卡，且「返回主菜单」图标是本终端。
 *       （AE2 的 {@code MenuItemLocator.locate()} 就是靠 item 的 {@code getMenuHost(...)}
 *       造宿主，再用菜单声明的宿主类型 {@code WirelessTerminalMenuHost} 做校验；
 *       {@code WTMenuHost} 正好是它的子类，所以两边都成立。）</li>
 * </ol>
 *
 * <p>⚠️ 本类只能被 {@link AE2WtLibHook} 引用（即只在装了 AE2WTLib 时加载）；
 * 没装时 {@code ModItems} 注册的是基类 {@link WirelessThroughputTerminalItem}，
 * 单机/普通 AE2 安全终端那条链路照旧。
 */
public class UniversalThroughputTerminalItem extends WirelessThroughputTerminalItem
        implements IUniversalWirelessTerminalItem {

    public UniversalThroughputTerminalItem(Properties properties) {
        super(properties);
    }

    /** WUT 里切到本终端时，它要知道该开哪个菜单。 */
    @Override
    public MenuType<?> getMenuType(ItemStack stack) {
        return ThroughputTerminalWirelessMenu.TYPE;
    }

    @Nullable
    @Override
    public ItemMenuHost getMenuHost(Player player, int inventorySlot, ItemStack stack, @Nullable BlockPos pos) {
        // 用 AE2WTLib 的宿主：支持量子桥接卡 + 「返回主菜单」图标是本终端
        return new ThroughputTerminalWirelessMenuHost(player, inventorySlot, stack,
                (p, subMenu) -> openFromInventory(p, inventorySlot, true));
    }
}
