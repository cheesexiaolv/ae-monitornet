package com.cheesexiaolv.aetm.xmod.ae2wtlib;

import com.cheesexiaolv.aetm.Init.ModItems;
import com.cheesexiaolv.aetm.menu.ThroughputTerminalWirelessMenu;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.apache.logging.log4j.Level;

import java.util.ArrayList;
import java.util.List;

import appeng.api.features.GridLinkables;
import appeng.core.AELog;
import appeng.hotkeys.HotkeyActions;
import appeng.items.tools.powered.WirelessTerminalItem;

import de.mari_023.ae2wtlib.hotkeys.Ae2WTLibLocatingService;
import de.mari_023.ae2wtlib.terminal.IUniversalWirelessTerminalItem;
import de.mari_023.ae2wtlib.wut.WUTHandler;

/**
 * 所有 AE2WTLib API 调用集中在这里（只有装了 AE2WTLib 时才会被 {@link AE2WtLibPlugin} 加载）。
 *
 * <p>做两件事：
 * <ol>
 *   <li>把我们的无线存储监控终端登记成 <b>WUT（通用终端）的一个可切换终端</b>
 *       —— 在 WUT 里切到它就能打开我们的界面，"返回主菜单"图标回到本终端。
 *       这一步同时决定 {@code ae2wtlib:upgrade} / {@code ae2wtlib:combine} 这类融合配方能否解析：
 *       AE2WTLib 的 {@code Serializer.validateOutput} 会用 {@code WUTHandler.terminalNames}
 *       校验 {@code terminalName}，名字没登记 → 那条配方直接解析失败（表现为"合成不了"）；</li>
 *   <li>确认 AE2 安全终端链接登记（{@code GridLinkables}）在位 —— 决定终端能否放进
 *       <b>安全终端的链接槽</b>做无线绑定：准入判定就是
 *       {@code GridLinkables.get(item) != null && handler.canLink(stack)}。
 *       正常情况下 {@code CommonEvents.onCommonSetup} 已经登记过，这里只做幂等兜底。</li>
 * </ol>
 *
 * <p><b>登记时机分三层</b>（AE2 12.9.9 + AE2WTLib 12.9.7 的真实行为，用 {@code javap} 反汇编确认过）：
 * <ol>
 *   <li><b>热键占位</b> —— {@link #preRegisterHotkey()}，由 {@link AE2WtLibPlugin} 挂在
 *       {@code RegisterEvent(Registry.ITEM_REGISTRY)} 上（AE2WTLib 自己也在同一事件里登记它的三个终端）。
 *       原因：AE2 在<b>客户端 Minecraft 构造期</b>就用 {@code RegisterKeyMappingsEvent} 关闭热键注册表
 *       （{@code appeng.client.Hotkeys.createHotkey()} 抛
 *       {@code IllegalStateException: Hotkey registration already finalized!}），而
 *       {@code WUTHandler.addTerminal()} 的顺序是"先登记热键（第 158 行）→ 再写
 *       {@code wirelessTerminals} / {@code terminalNames}（第 160/161 行）"，
 *       热键那一步一抛，写表的两行就被整段跳过。提前把热键 id 占好，
 *       {@code HotkeyActions.register} 就走"id 已存在"的分支，不再碰 AE2 的热键注册表。</li>
 *   <li><b>终端登记</b> —— {@link #init()}，跑在 {@code FMLCommonSetupEvent} 的 {@code enqueueWork} 里
 *       （要读自己注册表里的物品与菜单类型，只能在这里）。</li>
 *   <li><b>兜底重试</b> —— {@link #reserveHotkeySlotOnly()}：万一第 1 步也没赶上，
 *       只把 id 放进 AE2 的热键动作表（不碰已关闭的 {@code appeng.client.Hotkeys}）再重试一次
 *       {@code addTerminal}。终端照样能在通用终端里切到、融合配方照样能解析，只是没有专属按键。</li>
 * </ol>
 *
 * <p>每一步都单独兜异常，并把最终状态打进日志：这些调用失败只体现为"游戏里某个功能静默不可用"，极难定位。
 *
 * <p>注册用的 {@code addTerminal} 签名（AE2WTLib 1.19.2 {@code de.mari_023.ae2wtlib.wut.WUTHandler}）：
 * <pre>
 * public static void addTerminal(String name, ContainerOpener open, WTMenuHostFactory WTMenuHostFactory,
 *         MenuType&lt;?&gt; menuType, IUniversalWirelessTerminalItem item, String hotkeyName)
 * // 5 参重载的热键名 = "wireless_" + name + "_terminal"，这里显式传第 6 个参数，保证与占位用的一致
 * </pre>
 * 注意第 3 个参数的类型是 {@code WTMenuHostFactory}，而它的 {@code create(...)} 返回
 * {@code WTMenuHost} —— 这就是为什么我们的宿主必须继承 AE2WTLib 的宿主基类
 * （{@link ThroughputTerminalWirelessMenuHost}）。
 */
public final class AE2WtLibHook {

    /** WUT 里的终端 id（同时决定热键名 {@code key.ae2.wireless_<这个名字>_terminal}）。 */
    public static final String TERMINAL_ID = "throughput";

    /** 该终端的热键 id（= AE2WTLib 5 参重载拼出来的名字，逐字对齐，键位配置不会变）。 */
    public static final String HOTKEY_ID = "wireless_" + TERMINAL_ID + "_terminal";

    private AE2WtLibHook() {
    }

    /**
     * 在 AE2 关闭热键注册窗口之前把我们的热键 id 占好。
     *
     * <p>由 {@link AE2WtLibPlugin} 挂在 {@code RegisterEvent(Registry.ITEM_REGISTRY)} 上调用：
     * 那时 AE2 的物品注册表刚填完，AE2WTLib 也在同一事件里登记它自己的终端。
     * <b>本方法不需要物品/菜单/注册表对象</b>，所以不受各 mod 监听器先后顺序影响。
     */
    static void preRegisterHotkey() {
        if (HotkeyActions.REGISTRY.containsKey(HOTKEY_ID)) {
            return;
        }
        try {
            // 与 WUTHandler.addTerminal 内部完全相同的热键动作（AE2WTLib 的 Ae2WTLibLocatingService）。
            HotkeyActions.register(new Ae2WTLibLocatingService(TERMINAL_ID), HOTKEY_ID);
        } catch (Throwable t) {
            // 窗口没赶上不算致命：registerTerminal() 还有 reserveHotkeySlotOnly() 的兜底。
            AELog.info("AE MonitorNet: could not reserve hotkey id '%s' early (%s: %s); the terminal will still be "
                            + "registered, but it may end up without a dedicated keybind",
                    HOTKEY_ID, t.getClass().getSimpleName(), t.getMessage());
        }
    }

    /** 由 {@link AE2WtLibPlugin} 在确认装了 AE2WTLib 之后调用。 */
    static void init() {
        var item = ModItems.WIRELESS_THROUGHPUT_TERMINAL.get();

        try {
            registerTerminal(item);
        } catch (Throwable t) {
            AELog.log(Level.ERROR, t,
                    "AE MonitorNet: registering the wireless throughput terminal into AE2WTLib failed");
        }

        try {
            ensureLinkable(item);
        } catch (Throwable t) {
            AELog.log(Level.ERROR, t, "AE MonitorNet: %s could not be registered as grid-linkable; "
                    + "it will not be bindable through the security terminal", item.getDescriptionId());
        }

        if (!registeredForOurItem(item)) {
            AELog.warn("AE MonitorNet: the wireless throughput terminal was not registered into AE2WTLib's universal "
                    + "terminal (looking for terminal id '%s'); it will not be selectable there and the "
                    + "ae2wtlib upgrade/combine recipes for it will not load", TERMINAL_ID);
        }

        // 排障用的"单一事实来源"：游戏里"通用终端里切不到"看第一项，"放不进安全终端"看第二项，
        // "按键打不开"看第三项（keybind 为 false 说明热键占位没赶上，但终端本身可用）。
        // AELog 走的是 String.format，占位符是 %s（不是 log4j 的 {}）。
        var handler = GridLinkables.get(item);
        var probe = new ItemStack(item);
        AELog.info("AE MonitorNet: %s -> in universal terminal: %s, linkable in security terminal: %s (canLink: %s), keybind: %s",
                item.getDescriptionId(),
                registeredForOurItem(item),
                handler != null, handler != null && handler.canLink(probe),
                HotkeyActions.REGISTRY.containsKey(HOTKEY_ID));
    }

    private static void registerTerminal(Item item) {
        if (registeredForOurItem(item)) {
            return;
        }

        if (!(item instanceof IUniversalWirelessTerminalItem universal)) {
            // 这条以前是静默 return，结果"融合配方解析不了"查半天查不出原因。
            AELog.warn("AE MonitorNet: {} is not an IUniversalWirelessTerminalItem (it should be created by "
                    + "ModItems.createWirelessTerminal() when ae2wtlib is present); skipping WUT registration",
                    item.getDescriptionId());
            return;
        }

        if (WUTHandler.terminalNames.contains(TERMINAL_ID)) {
            // 终端 id 已被别人（另一个附属）占用 —— 不覆盖对方，只明确报出来。
            // 不做"给某个 mod 写硬编码兼容"：id 与 data/ 里的 terminalName 是绑死的，不能临时改名。
            AELog.warn("AE MonitorNet: terminal id '%s' is already taken by another terminal (%s); not overwriting it. "
                            + "Our wireless throughput terminal will not be selectable in the universal terminal "
                            + "until the id conflict is resolved",
                    TERMINAL_ID, WUTHandler.wirelessTerminals.get(TERMINAL_ID));
            return;
        }

        try {
            addTerminal(universal);
            return;
        } catch (Throwable t) {
            AELog.info("AE MonitorNet: AE2WTLib's addTerminal() for terminal '%s' threw %s: %s; reserving the hotkey id "
                            + "in AE2's hotkey action table (no keybind) and retrying once",
                    TERMINAL_ID, t.getClass().getSimpleName(), t.getMessage());
        }

        reserveHotkeySlotOnly();
        try {
            addTerminal(universal);
        } catch (Throwable t) {
            AELog.log(Level.ERROR, t, "AE MonitorNet: registering the wireless throughput terminal into AE2WTLib failed "
                    + "even after reserving the hotkey id; it will not be selectable in the universal terminal "
                    + "and its upgrade/combine recipes will not load");
        }
    }

    private static void addTerminal(IUniversalWirelessTerminalItem universal) {
        // 用我们自己的菜单（ThroughputTerminalWirelessMenu）与宿主（带"返回主菜单"图标）
        WUTHandler.addTerminal(
                TERMINAL_ID,
                universal::tryOpen,
                ThroughputTerminalWirelessMenuHost::new,
                ThroughputTerminalWirelessMenu.TYPE,
                universal,
                HOTKEY_ID);
    }

    /**
     * 兜底：只把 id 放进 AE2 的热键动作表 {@code HotkeyActions.REGISTRY}，
     * 不再碰已经关闭的 {@code appeng.client.Hotkeys}（那个注册窗口在客户端 Minecraft 构造期就关了）。
     *
     * <p>这样 {@code WUTHandler.addTerminal} 内部的 {@code HotkeyActions.register} 会走
     * "id 已存在"的分支，跳过 AE2 的热键注册，从而能继续写终端表 —— 终端可切换、配方可解析，
     * 代价只是这个终端没有专属按键（{@code Hotkeys} 里没有它的 KeyMapping）。
     */
    private static void reserveHotkeySlotOnly() {
        if (!HotkeyActions.REGISTRY.containsKey(HOTKEY_ID)) {
            HotkeyActions.REGISTRY.put(HOTKEY_ID,
                    new ArrayList<>(List.of(new Ae2WTLibLocatingService(TERMINAL_ID))));
        }
    }

    /** 该终端 id 是否已经登记在 AE2WTLib 的终端表里、且登记的就是我们这件物品。 */
    private static boolean registeredForOurItem(Item item) {
        var definition = WUTHandler.wirelessTerminals.get(TERMINAL_ID);
        return definition != null && ((Object) definition.item()) == item;
    }

    /**
     * 幂等兜底：{@code GridLinkables.register} 对同一个物品重复登记会
     * {@code Preconditions.checkState} 抛 {@code IllegalStateException}
     * （AE2 12.9.9 {@code GridLinkables.java}），所以先查再登记。
     */
    private static void ensureLinkable(Item item) {
        if (GridLinkables.get(item) == null) {
            GridLinkables.register(item, WirelessTerminalItem.LINKABLE_HANDLER);
        }
    }
}
