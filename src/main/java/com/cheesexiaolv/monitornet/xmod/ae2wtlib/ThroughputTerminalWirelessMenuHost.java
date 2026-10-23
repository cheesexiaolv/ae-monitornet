package com.cheesexiaolv.monitornet.xmod.ae2wtlib;

import java.util.function.BiConsumer;

import com.cheesexiaolv.monitornet.Init.ModItems;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;

import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;

/**
 * WUT 用的菜单宿主：行为与 AE2WTLib 自带终端一致，只把「返回主菜单」的图标换成我们的存储监控终端。
 *
 * <p>为什么必须继承 AE2WTLib 的 {@link WTMenuHost}（而不是 AE2 的 {@code WirelessTerminalMenuHost}）：
 * {@code AddTerminalEvent#builder(...)} 的宿主工厂类型是
 * {@code WTDefinition.WTMenuHostFactory}，签名
 * {@code WTMenuHost create(ItemWT item, Player player, ItemMenuHostLocator locator,
 * BiConsumer<Player, ISubMenu> returnToMainMenu)}；而且 {@code WTMenuHost} 才是
 * <ul>
 *   <li>{@code getActionableNode()} 支持量子桥接卡（{@code isQuantumLinked()}）的那个宿主，</li>
 *   <li>它把 {@code getCloseHotkey()} 换成 {@code WTDefinition#hotkeyName()}（本终端是
 *       {@code wireless_throughput_terminal}，界面里"按热键关界面"才认得我们），</li>
 *   <li>并且 {@code getMainMenuIcon()} 是这条继承链上被"返回主菜单"按钮使用的那个方法。</li>
 * </ul>
 *
 * <p>继承链（19.5.1）：{@code WTMenuHost extends WirelessTerminalMenuHost<ItemWT> extends ItemMenuHost}，
 * 而我们的菜单 {@code ThroughputTerminalWirelessMenu.TYPE} 声明的宿主类型正是
 * {@code WirelessTerminalMenuHost}（{@code MenuTypeBuilder.create(..., WirelessTerminalMenuHost.class)}）
 * → 所以这个宿主可以直接喂给 {@code MenuOpener}，菜单类不用为 WUT 加任何构造器。
 *
 * <p>⚠️ 本类只能被 {@link AE2WtLibHook} / {@link UniversalThroughputTerminalItem} 引用
 * （即只在装了 AE2WTLib 时加载）。
 */
public class ThroughputTerminalWirelessMenuHost extends WTMenuHost {

    public ThroughputTerminalWirelessMenuHost(ItemWT item, Player player, ItemMenuHostLocator locator,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
    }

    /**
     * 「返回主菜单」按钮上显示的图标：用我们的存储监控终端（有线版零件物品），
     * 与 AE2WTLib 自带宿主返回各自终端物品的做法一致。
     */
    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(ModItems.THROUGHPUT_TERMINAL.get());
    }
}
