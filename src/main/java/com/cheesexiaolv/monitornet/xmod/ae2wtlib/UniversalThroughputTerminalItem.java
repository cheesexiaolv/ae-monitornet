package com.cheesexiaolv.monitornet.xmod.ae2wtlib;

import javax.annotation.Nullable;

import com.cheesexiaolv.monitornet.menu.ThroughputTerminalWirelessMenu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import appeng.core.AELog;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.menu.locator.ItemMenuHostLocator;

import de.mari_023.ae2wtlib.api.registration.WTDefinition;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;

/**
 * 装了 AE2WTLib 时用的无线存储监控终端物品。
 *
 * <p>19.5.1 的通用终端只认 {@link ItemWT} 的子类（{@code AddTerminalEvent#builder(...)} 的第 4 个
 * 参数类型就是 {@code ItemWT}，{@code WTDefinition#item()} 也是它），所以这里<b>不能</b>再像 1.20.1
 * 那样继承"自己的无线终端基类 + 实现 IUniversalWirelessTerminalItem"（那个接口在 19.x 已删除）：
 * Java 单继承，只能直接继承 {@code ItemWT}。
 *
 * <p>{@code ItemWT} 已经替我们做完了通用终端该做的事，因此本类只剩两处差异：
 * <ol>
 *   <li>{@link #getMenuType(ItemMenuHostLocator, Player)} / {@link #getMenuType()} 返回
 *       <b>我们自己的菜单</b>（{@code ThroughputTerminalWirelessMenu}）——
 *       {@code ItemWT#open} 走的是 2 参版本，AE2 的 {@code openFromInventory} 走的是无参版本；</li>
 *   <li>{@link #getMenuHost} 在"终端定义没登记上"的降级路径里直接造
 *       {@link ThroughputTerminalWirelessMenuHost}（而不是像 {@code ItemWT} 那样
 *       {@code WTDefinition.of(...)} 抛 NPE）—— 这样即使 WUT 登记失败，物品本身仍能打开，
 *       只是没有"切换终端"按钮。</li>
 * </ol>
 *
 * <p>电力：{@code ItemWT()} 无参构造器把容量交给 AE2 的
 * {@code AEConfig#getWirelessTerminalBattery()}（默认 1 600 000 AE，与没装 AE2WTLib 时
 * {@code WirelessThroughputTerminalItem} 的常量一致），并且自带 {@code stacksTo(1)}。
 *
 * <p>⚠️ 本类只能被 {@link AE2WtLibHook} 引用（即只在装了 AE2WTLib 时加载）；
 * 没装时 {@code ModItems} 注册的是基类 {@code WirelessThroughputTerminalItem}，
 * 单机/普通 AE2 安全终端那条链路照旧。
 */
public class UniversalThroughputTerminalItem extends ItemWT {

    /** 降级日志只报一次（打开界面会反复走 getMenuHost，不能刷屏）。 */
    private static boolean loggedMissingDefinition;

    /** WUT 里切到本终端时，它要知道该开哪个菜单（{@code ItemWT#open} 用这个）。 */
    @Override
    public MenuType<?> getMenuType(ItemMenuHostLocator locator, Player player) {
        return ThroughputTerminalWirelessMenu.TYPE;
    }

    /** 走 AE2 自己的 {@code openFromInventory} 路径时用这个（与没装 AE2WTLib 时的物品行为一致）。 */
    @Override
    public MenuType<?> getMenuType() {
        return ThroughputTerminalWirelessMenu.TYPE;
    }

    @Nullable
    @Override
    public WirelessTerminalMenuHost<?> getMenuHost(Player player, ItemMenuHostLocator locator,
            @Nullable BlockHitResult hitResult) {
        ItemStack stack = locator.locateItem(player);
        if (WTDefinition.ofOrNull(stack) == null) {
            // 降级：终端定义没登记（WUT 登记失败 / 数据包还没读到定义）。
            // 不走 super（它 WTDefinition.of(...) 会 Objects.requireNonNull 抛 NPE），
            // 直接造我们自己的宿主：界面照开，只是没有"切换终端"按钮。
            if (!loggedMissingDefinition) {
                loggedMissingDefinition = true;
                AELog.warn("AE MonitorNet: %s has no registered AE2WTLib terminal definition; opening it with the "
                        + "plain wireless menu host (no terminal switcher)", stack.getItem().getDescriptionId());
            }
            return new ThroughputTerminalWirelessMenuHost((ItemWT) stack.getItem(), player, locator,
                    (p, subMenu) -> tryOpen(p, locator, true));
        }
        // 正常路径：ItemWT 会用 WTDefinition 的宿主工厂造 ThroughputTerminalWirelessMenuHost
        // （支持量子桥接卡 + 「返回主菜单」图标是本终端）。
        return super.getMenuHost(player, locator, hitResult);
    }
}
