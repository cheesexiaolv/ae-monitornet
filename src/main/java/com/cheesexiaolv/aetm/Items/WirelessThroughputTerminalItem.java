package com.cheesexiaolv.aetm.Items;

import com.cheesexiaolv.aetm.menu.ThroughputTerminalWirelessMenu;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item.Properties;

import appeng.items.tools.powered.WirelessTerminalItem;

/**
 * Wireless aggregated throughput terminal (item).
 *
 * <p>Extends AE2's {@link WirelessTerminalItem} directly: linking, power drain (0.5 AE per open),
 * range checks etc. are all inherited; only the menu is replaced. The menu and screen are shared with
 * the wired version, the only difference being where the row data's grid comes from
 * ({@code WirelessTerminalMenuHost} resolves the node the item is linked to).
 *
 * <p><b>1.20.1 → 1.21.1 changes</b>
 * <ul>
 *   <li>{@code IMenuItem#getMenuHost(Player, int, ItemStack, BlockPos)} became
 *       {@code getMenuHost(Player, ItemMenuHostLocator, @Nullable BlockHitResult)}; AE2's own
 *       {@code WirelessTerminalItem#getMenuHost} now creates exactly the host our 1.20.1 subclass
 *       hand-wrote (including the {@code returnToMainMenu} callback), so the override is gone
 *       (evidence: {@code reference/AE2-1.21.1-19.2.17/appeng/items/tools/powered/WirelessTerminalItem.java:139}).
 *       The dead {@code returnToMainMenu(Player, int, ISubMenu)} helper was dropped with it.</li>
 *   <li>{@code getMenuType()} is unchanged
 *       ({@code WirelessTerminalItem.java:134}), so the override still selects our menu.</li>
 * </ul>
 */
public class WirelessThroughputTerminalItem extends WirelessTerminalItem {

    /** Same order of magnitude as AE2's wireless terminal (1.6M AE). */
    private static final double POWER_CAPACITY = 1_600_000.0;

    public WirelessThroughputTerminalItem(Properties properties) {
        super(() -> POWER_CAPACITY, properties);
    }

    @Override
    public MenuType<?> getMenuType() {
        return ThroughputTerminalWirelessMenu.TYPE;
    }
}
