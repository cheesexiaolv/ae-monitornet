package com.cheesexiaolv.monitornet.menu;

import com.cheesexiaolv.monitornet.Items.WirelessTerminalSettings;

import javax.annotation.Nullable;

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
 *
 * <p><b>1.20.1 → 1.21.1（AE2 19.2.17）</b>：源文件<b>逐字节保留</b>（含原始类型写法）。
 * {@code WirelessTerminalMenuHost} 在 19.2 变成了泛型 {@code WirelessTerminalMenuHost<T extends WirelessTerminalItem>}
 * （{@code reference/AE2-1.21.1-19.2.17/appeng/helpers/WirelessTerminalMenuHost.java:37}），
 * 但 AE2 自己的无线终端菜单也是同样的原始类型写法：
 * {@code MenuTypeBuilder.create(WirelessCraftingTermMenu::new, WirelessCraftingTerminalMenuHost.class)}
 * （{@code appeng/menu/me/items/WirelessCraftingTermMenu.java:8-12}），所以这里<b>无需 cast / 无需改签名</b>。
 * {@code ItemMenuHost#getItemStack()} 仍在（{@code appeng/api/implementations/menuobjects/ItemMenuHost.java:53}）。
 *
 * <p>唯一的功能性改动：{@code new WirelessTerminalSettings(stack)} → {@code (stack, HolderLookup.Provider)}，
 * 因为 19.2 的 {@code AEKey.fromTagGeneric/toTagGeneric} 需要 registry lookup
 * （{@code appeng/api/stacks/AEKey.java:109,120}）。
 */
public class ThroughputTerminalWirelessMenu extends ThroughputTerminalMenu {

    public static final MenuType<ThroughputTerminalWirelessMenu> TYPE = MenuTypeBuilder
            .create(ThroughputTerminalWirelessMenu::new, WirelessTerminalMenuHost.class)
            .withMenuTitle(host -> Component.translatable("item.ae_monitornet.wireless_throughput_terminal"))
            .build("wireless_throughput_terminal");

    public ThroughputTerminalWirelessMenu(int id, Inventory playerInventory, WirelessTerminalMenuHost host) {
        // 1.21.1: AEKey.fromTagGeneric/toTagGeneric 需要 registry lookup
        // （reference/AE2-1.21.1-19.2.17/appeng/api/stacks/AEKey.java:109,120），
        // 所以设置宿主多带一个 HolderLookup.Provider —— 与 Items.WirelessTerminalSettings 的 2 参构造约定一致。
        super(TYPE, id, playerInventory, host,
                new WirelessTerminalSettings(host.getItemStack(), host.getPlayer().level().registryAccess()));
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

    /**
     * 本菜单的无线宿主（{@code WirelessTerminalMenuHost} 或其子类）。
     *
     * <p>给 xmod 的 WUT 界面用：{@code IUniversalTerminalCapable#getHost()} 要的是
     * AE2WTLib 的 {@code WTMenuHost}，那个类型只能由 {@code xmod} 包里的类去转
     * （本类保持"只用 AE2 类型"，软依赖才不会硬绑）。没装 AE2WTLib 时宿主就是 AE2 的
     * {@code WirelessTerminalMenuHost}，正常返回；只有宿主缺失（理论上的非法菜单）才返回 {@code null}。
     */
    @Nullable
    public WirelessTerminalMenuHost<?> getWirelessMenuHost() {
        return itemMenuHost instanceof WirelessTerminalMenuHost<?> host ? host : null;
    }
}
