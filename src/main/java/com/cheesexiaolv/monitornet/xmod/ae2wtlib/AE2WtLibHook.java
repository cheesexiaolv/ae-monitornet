package com.cheesexiaolv.monitornet.xmod.ae2wtlib;

import com.cheesexiaolv.monitornet.Init.ModItems;
import com.cheesexiaolv.monitornet.menu.ThroughputTerminalWirelessMenu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import appeng.core.AELog;

import de.mari_023.ae2wtlib.api.gui.Icon;
import de.mari_023.ae2wtlib.api.registration.AddTerminalEvent;
import de.mari_023.ae2wtlib.api.registration.WTDefinition;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;

/**
 * 所有 AE2WTLib（无线通用终端 / WUT）API 调用集中在这里
 * （只有装了 AE2WTLib 时才会被 {@link AE2WtLibPlugin} 加载）。
 *
 * <p>做一件事：把我们的无线存储监控终端登记成 <b>WUT 里的一个可切换终端</b>
 * —— 在通用终端里切到它就能打开我们的界面，"返回主菜单"图标回到本终端。
 * 这一步同时决定 {@code ae2wtlib:upgrade} / {@code ae2wtlib:combine} 这四条配方能否解析：
 * 两个序列化器都用 {@code WTDefinition.CODEC} 按名字查表
 * （{@code WTDefinition.java:49-52}，即
 * {@code Codec.STRING.comapFlatMap(...)}，查不到就是
 * <b>{@code WTDefinition <name> does not exist}</b>），名字没登记 ⇒ 配方直接解析失败
 * （表现为日志里那 4 条 {@code WTDefinition throughput does not exist} + 游戏里合成不了）。
 *
 * <p><b>19.5.1 的注册模型与 1.20.1（15.3.3）完全不同</b>，下面每条都用
 * {@code javap} + Vineflower 反编译核对过（jar 在 {@code libs/ae2wtlib-19.5.1.jar}，
 * API 在它嵌套的 {@code META-INF/jarjar/de.mari_023.ae2wtlib_api-19.5.1.jar} 里，
 * 运行期由 NeoForge 作为独立 mod {@code ae2wtlib_api} 解出来）：
 *
 * <pre>
 * 15.3.3（1.20.1）                             19.5.1（1.21.1）
 * ------------------------------------------------- -------------------------------
 * WUTHandler.addTerminal(name, open, hostFactory, AddTerminalEvent.register(handler)
 *     menuType, item, displayNameKey)           → handler 里 event.builder(name, hostFactory,
 *                                                  menuType, ItemWT item, Icon icon)
 *                                                  .addTerminal()
 * WUTHandler.wirelessTerminals / terminalNames  WTDefinition.wirelessTerminals() /
 *                                               WTDefinition.exists(name) / .of(name)
 * IUniversalWirelessTerminalItem（物品实现它）   ItemWT（物品<b>继承</b>它）
 * WTMenuHost(player, slot, stack, returnToMain)  WTMenuHost(ItemWT, Player, ItemMenuHostLocator,
 *                                                  BiConsumer&lt;Player, ISubMenu&gt;)
 * --------------- -------------------------------
 * </pre>
 *
 * <p><b>登记时机</b>：{@link #registerTerminals()} 由 {@link AE2WtLibPlugin} 在
 * <b>mod 构造器</b>里调用，只挂回调、不碰任何注册表；回调本身在
 * {@code AddTerminalEvent#run()} 里执行，而那是嵌套 API mod {@code ae2wtlib_api} 的
 * 物品注册监听器（{@code AE2wtlibAPIEntrypoint}）在 {@code RegisterEvent(Registries.ITEM)}
 * 阶段打的一枪 —— 也就是说：
 * <ul>
 *   <li>回调执行时物品注册表正在填充（和 AE2WTLib 自己的三个终端同一时刻），
 *       所以 {@code HotkeyActions.register(...)} 还在 AE2 的热键注册窗口内
 *       （AE2 是客户端 {@code Minecraft.<init>} 里才 {@code finalizeRegistration} 的，
 *       见 {@code appeng/client/Hotkeys.java}），<b>不需要</b> 1.20.1 的"提前占热键 id"；</li>
 *   <li>它早于数据包（配方）解析，所以 {@code WTDefinition} 表在配方读之前就是满的 ——
 *       那 4 条 {@code WTDefinition throughput does not exist} 因此消失。</li>
 * </ul>
 *
 * <p><b>物品实例从哪来</b>：{@code WTDefinitionBuilder} 的构造参数就是 {@code ItemWT} 实例
 * （不是 {@code DeferredHolder}，19.5.1 里也没有"事后替换物品"的入口）。物品又不能在 mod 构造期
 * 造（NeoForge 的 {@code Item} 构造器会 {@code Registry#createIntrusiveHolder}，而原版物品注册表
 * 在注册事件之外是冻结的 —— 实测直接在 {@code @Mod} 构造器里 {@code new} 会以
 * {@code IllegalStateException: Registry is already frozen} 中止启动）。
 * 所以 {@code Init/ModItems} 用一个<b>记忆化</b>的惰性工厂 {@link ModItems#wirelessTerminal()}：
 * 谁先要就先造（两边的调用点都在物品注册事件内，那时注册表是解冻的），另一边复用同一实例 ——
 * 于是"我们的 DeferredRegister 监听器先跑还是 {@code ae2wtlib_api} 的监听器先跑"都不影响结果。
 */
public final class AE2WtLibHook {

    /** WUT 里的终端 id：必须与 {@code data/ae_monitornet/recipe/wireless_universal_terminal/*} 的
     *  {@code terminalName} / {@code terminalAName} 逐字一致（都是 {@code throughput}）。 */
    public static final String TERMINAL_ID = "throughput";

    /** 该终端的热键 id：{@code WTDefinitionBuilder} 自动拼 {@code "wireless_" + name + "_terminal"}，
     *  与 {@code assets/ae_monitornet/lang/*.json} 里既有的 {@code key.ae2.wireless_throughput_terminal} 一致。 */
    public static final String HOTKEY_ID = "wireless_" + TERMINAL_ID + "_terminal";

    /** 只登记一次（构造器只调一次，这里再挡一层，重复调用不会抛"already exists"）。 */
    private static boolean registrationRequested;

    private AE2WtLibHook() {
    }

    /**
     * 造"装了 AE2WTLib 时"用的无线终端物品。
     *
     * <p>由 {@link ModItems} 调用（它在没装 AE2WTLib 时用只依赖 AE2 的基类）。返回类型刻意写成
     * AE2 的 {@link Item}，这样 {@code ModItems} 不需要 import 任何 {@code de.mari_023.*} 类型。
     */
    public static Item createUniversalTerminalItem() {
        return new UniversalThroughputTerminalItem();
    }

    /**
     * 挂上"把本终端登记进 WUT"的回调。
     *
     * <p>只登记回调，真正的登记动作在 {@code AddTerminalEvent#run()} 里由
     * {@code WTDefinitionBuilder#addTerminal()} 完成（见类注释）。
     */
    public static void registerTerminals() {
        if (registrationRequested) {
            return;
        }
        registrationRequested = true;

        if (AddTerminalEvent.didRun()) {
            // 正常永远走不到这里（我们在构造器里登记，run() 在物品注册事件里才发生）；
            // 万一别的 mod 在构造期就提前 run() 了，这里只能降级并说清楚后果。
            AELog.error("AE MonitorNet: AE2WTLib already ran terminal registration before we could hook into it; "
                    + "terminal '%s' will not be selectable in the universal terminal and the ae2wtlib "
                    + "upgrade/combine recipes for it will not load", TERMINAL_ID);
            return;
        }

        AddTerminalEvent.register(event -> {
            if (WTDefinition.exists(TERMINAL_ID)) {
                // 终端 id 已被别人（另一个附属）占用 —— 不覆盖对方，只明确报出来。
                // id 与 data/ 里的 terminalName 是绑死的，不能临时改名。
                AELog.warn("AE MonitorNet: AE2WTLib terminal id '%s' is already taken by another terminal (%s); "
                                + "not overwriting it. Our wireless throughput terminal will not be selectable in "
                                + "the universal terminal until the id conflict is resolved",
                        TERMINAL_ID, WTDefinition.of(TERMINAL_ID).item().getDescriptionId());
                return;
            }

            if (!(ModItems.wirelessTerminal() instanceof ItemWT item)) {
                // 只有"装了 AE2WTLib"时 ModItems 才会造 ItemWT 子类；走到这里说明两边的判断不一致。
                AELog.warn("AE MonitorNet: {} is not an AE2WTLib ItemWT (it should be created by "
                                + "ModItems#createWirelessTerminal() when ae2wtlib is present); skipping WUT registration",
                        ModItems.wirelessTerminal().getDescriptionId());
                return;
            }

            // 第 5 个参数是选择盘/终端名用的 16x16 图标。这里直接用物品自己的贴图
            // （我们的模型 layer0 就是 ae2:item/wireless_terminal），所以 WUT 切换面板里
            // 显示的图标与物品图标逐像素一致；名字用默认的 item.getDescriptionId()。
            Icon icon = new Icon(0, 0, 16, 16,
                    new Icon.Texture(ResourceLocation.fromNamespaceAndPath("ae2", "textures/item/wireless_terminal.png"),
                            16, 16));

            // 宿主工厂 -> ThroughputTerminalWirelessMenuHost（WTMenuHost 子类，支持量子桥接卡，
            // "返回主菜单"图标是我们的终端零件）；菜单仍是我们自己的无线终端菜单。
            // upgradeCount 用默认值 2（= AE2WTLib 默认的升级卡槽数，能量卡由
            // UpgradeHelper#addUpgrades() 紧接着自动挂上）。
            event.builder(TERMINAL_ID, ThroughputTerminalWirelessMenuHost::new,
                            ThroughputTerminalWirelessMenu.TYPE, item, icon)
                    .hotkeyName(HOTKEY_ID)
                    .addTerminal();

            // 排障用的"单一事实来源"：这条日志出现 = WUT 里能切到本终端、四条配方能解析。
            AELog.info("AE MonitorNet: registered terminal '%s' (hotkey '%s', menu %s) into AE2WTLib's universal "
                            + "terminal: %s", TERMINAL_ID, HOTKEY_ID, ThroughputTerminalWirelessMenu.TYPE,
                    WTDefinition.exists(TERMINAL_ID));
        });
    }
}
