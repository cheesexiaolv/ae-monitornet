package com.cheesexiaolv.aetm.xmod.ae2wtlib;

import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import com.cheesexiaolv.aetm.Init.ModItems;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.menu.ISubMenu;

import de.mari_023.ae2wtlib.terminal.WTMenuHost;

/**
 * WUT 用的菜单宿主：行为与 AE2WTLib 自带的宿主一致，只把「返回主菜单」的图标换成我们的存储监控终端。
 *
 * <p>为什么必须继承 AE2WTLib 的 {@link WTMenuHost}（而不是 AE2 的 {@code WirelessTerminalMenuHost}）：
 * {@code WUTHandler.addTerminal(...)} 的宿主工厂类型是 {@code WTMenuHostFactory}，它的
 * {@code create(...)} 返回 {@code WTMenuHost}；而且 {@code WTMenuHost} 才是
 * <ul>
 *   <li>{@code rangeCheck()} 支持量子桥接卡（{@code isQuantumLinked()}）的那个宿主，</li>
 *   <li>并且 {@code getMainMenuIcon()} 是它这条继承链上被"返回主菜单"按钮使用的那个方法。</li>
 * </ul>
 *
 * <p>继承链（AE2WTLib 1.19.2 源码）：
 * {@code WTMenuHost extends appeng.helpers.WirelessTerminalMenuHost}，
 * 而我们的菜单 {@code ThroughputTerminalWirelessMenu.TYPE} 声明的宿主类型正是
 * {@code WirelessTerminalMenuHost} → 所以这个宿主可以直接喂给 {@code MenuOpener}，不需要给菜单加构造器。
 *
 * <p>⚠️ 本类只能被 {@link AE2WtLibHook} 引用（即只在装了 AE2WTLib 时加载）。
 */
public class ThroughputTerminalWirelessMenuHost extends WTMenuHost {

    public ThroughputTerminalWirelessMenuHost(Player player, @Nullable Integer inventorySlot, ItemStack is,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(player, inventorySlot, is, returnToMainMenu);
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
