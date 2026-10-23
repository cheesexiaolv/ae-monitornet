package com.cheesexiaolv.monitornet.xmod.ae2wtlib;

import javax.annotation.Nullable;

import com.cheesexiaolv.monitornet.client.gui.WirelessThroughputTerminalScreen;
import com.cheesexiaolv.monitornet.menu.ThroughputTerminalWirelessMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.style.ScreenStyle;
import appeng.core.AELog;

import de.mari_023.ae2wtlib.api.terminal.IUniversalTerminalCapable;
import de.mari_023.ae2wtlib.api.terminal.ItemWUT;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;

/**
 * 「感知通用无线终端（WUT）」的无线存储监控终端界面。
 *
 * <p>为什么需要它：把无线终端<b>合成进通用终端</b>后，界面上必须有一个「切换终端」按钮
 * （AE2WTLib 自带的 {@code WETScreen} / {@code WCTScreen} 都在构造器里做
 * {@code if (isWUT()) addToLeftToolbar(cycleTerminalButton())}）。
 * 本工程的无线终端界面是自己写的 {@link WirelessThroughputTerminalScreen}，没有那一步，
 * 于是从通用终端里打开后<b>卡在这个终端里出不去</b>。
 *
 * <p>与 1.20.1 蓝本的差别（19.5.1 API）：
 * <ul>
 *   <li>{@code CycleTerminalButton} 类已删除，改成接口上的工厂方法
 *       {@code IUniversalTerminalCapable#cycleTerminalButton()}（内部就是新的
 *       {@code TerminalSelectionButton}：点一下弹出"已装终端"选择盘，而不是逐个轮换）；</li>
 *   <li>{@code IUniversalTerminalCapable} 多了一个 {@link #getHost()}（返回 {@code WTMenuHost}），
 *       因为热键关闭/切换都要从宿主拿 {@code getCloseHotkey()} 与终端列表；</li>
 *   <li>顺手补上 {@code checkForTerminalKeys}：AE2WTLib 的终端界面都允许用各自热键直接切换，
 *       不给的话"在通用终端里按热键换终端"这个动作在本界面无效。</li>
 * </ul>
 *
 * <p>{@code IUniversalTerminalCapable} 需要的 {@code isHandlingRightClick()} 由 AE2 的
 * {@code AEBaseScreen} 提供（它带 {@code handlingRightClick} 字段），所以这里只需实现
 * {@link #storeState()} 与 {@link #getHost()}。
 *
 * <p>这类只对 AE2WTLib 有意义的代码放在 {@code xmod} 包里：只有确认装了 AE2WTLib 时才会加载
 * （见 {@link AE2WtLibClientHook}）；没装时用的仍是普通界面，软依赖不受影响。
 */
public class WutThroughputTerminalScreen extends WirelessThroughputTerminalScreen
        implements IUniversalTerminalCapable {

    /** 只报一次的排障日志：确认"从通用终端打开时确实加了切换按钮"。 */
    private static boolean loggedSwitchButton;

    /** 本界面的宿主（{@code WTMenuHost}）；只有物品不是通用终端/宿主不是 WT 宿主时为 {@code null}。 */
    @Nullable
    private final WTMenuHost host;

    public WutThroughputTerminalScreen(ThroughputTerminalWirelessMenu menu, Inventory playerInventory,
            Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        var wirelessHost = menu.getWirelessMenuHost();
        this.host = wirelessHost instanceof WTMenuHost wtMenuHost ? wtMenuHost : null;

        // 与 AE2WTLib 自带终端同一判定：手里这件物品是通用终端才给切换按钮
        // （普通无线终端也复用这个屏幕类，那时不该出现"切换终端"）。
        if (this.host != null && menu.getTerminalStack().getItem() instanceof ItemWUT) {
            addToLeftToolbar(cycleTerminalButton());
            if (!loggedSwitchButton) {
                loggedSwitchButton = true;
                AELog.info("AE MonitorNet: the wireless throughput terminal was opened from the universal terminal; "
                        + "the 'select terminal' button has been added to the left toolbar");
            }
        }
    }

    /**
     * 本界面的宿主。
     *
     * <p>非空保证：装了 AE2WTLib 时物品一定是 {@link UniversalThroughputTerminalItem}
     * （{@code ItemWT} 子类），它造出来的宿主必是 {@link ThroughputTerminalWirelessMenuHost}；
     * {@code null} 只可能出现在"定义没登记 + 物品被换掉"这种不该发生的组合里，
     * 那时 {@link #keyPressed} 会跳过热键处理、也不加切换按钮，界面本身照常能用。
     */
    @Override
    public WTMenuHost getHost() {
        if (this.host == null) {
            throw new IllegalStateException("WutThroughputTerminalScreen was opened without a WTMenuHost");
        }
        return this.host;
    }

    /** 切走之前要保存的界面状态：本终端没有额外状态（与 AE2WTLib 的 {@code WATScreen#storeState} 一致）。 */
    @Override
    public void storeState() {
        // 无额外状态
    }

    /**
     * 让 AE2WTLib 的热键在本界面生效（按别的终端热键 = 切过去，按本终端热键 = 关界面）。
     *
     * <p>与 {@code WETScreen#keyPressed} 等价：先让原版/父类处理，没吃掉才查终端热键。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return this.host != null && checkForTerminalKeys(keyCode, scanCode);
    }
}
