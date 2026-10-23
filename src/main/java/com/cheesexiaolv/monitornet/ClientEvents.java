package com.cheesexiaolv.monitornet;

import com.cheesexiaolv.monitornet.Init.ModItems;
import com.cheesexiaolv.monitornet.client.TerminalClientPackets;
import com.cheesexiaolv.monitornet.client.gui.AdvancedThroughputMonitorScreen;
import com.cheesexiaolv.monitornet.client.gui.ThroughputTerminalScreen;
import com.cheesexiaolv.monitornet.client.gui.WirelessThroughputTerminalScreen;
import com.cheesexiaolv.monitornet.menu.AdvancedThroughputMonitorMenu;
import com.cheesexiaolv.monitornet.menu.ThroughputTerminalMenu;
import com.cheesexiaolv.monitornet.menu.ThroughputTerminalWirelessMenu;
import com.cheesexiaolv.monitornet.network.MonitorNetNetwork;
import com.cheesexiaolv.monitornet.xmod.ae2wtlib.AE2WtLibClientHook;
import com.cheesexiaolv.monitornet.xmod.ae2wtlib.AE2WtLibPlugin;

import appeng.api.util.AEColor;
import appeng.init.client.InitScreens;

import guideme.Guide;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端注册。
 *
 * <p>AE2 会对自己所有的 {@code PartItem} 注册 item 颜色（用 {@code AEColor.TRANSPARENT} 解析
 * 显示模型里 tintindex 1–4 的多层高亮贴图）。第三方零件物品不会被 AE2 自动处理，若不手动注册，
 * {@code ae2:item/display_base} 那几层会按原色叠加渲染，导致物品图标异常。这里补齐同样的处理。
 *
 * <p><b>1.20.1 → 1.21.1 的两处时机变化</b>（设计、样式路径、屏幕类都没动）：
 * <ul>
 *   <li>屏幕注册从 {@code FMLClientSetupEvent#enqueueWork} 里的
 *       {@code InitScreens.register(type, factory, stylePath)} 改成 NeoForge 的
 *       {@link RegisterMenuScreensEvent} 回调（AE2 自己也这么接：
 *       {@code reference/AE2-1.21.1-19.2.17/appeng/core/AppEngClient.java} 里
 *       {@code modEventBus.addListener(InitScreens::init)}），签名变成
 *       {@code InitScreens.register(event, type, factory, stylePath)}
 *       （{@code appeng/init/client/InitScreens.java:93,137}）。
 *       原版 1.21.1 已不允许在客户端 setup 阶段再往 {@code MenuScreens} 里塞东西；</li>
 *   <li>指南书不再靠 {@code assets/<ns>/guideme_guides/guide.json} 的"资源元数据自动发现"，
 *       改成代码里 {@code Guide.builder(id).folder("ae2guide")} 显式注册
 *       （GuideME 21.1.17：{@code guideme/GuideBuilder.java:43,160}）。页面因此从
 *       {@code assets/ae_monitornet/guides/ae_monitornet/guide/} 迁到
 *       {@code assets/ae_monitornet/ae2guide/}（与 AE2/AAE 的 {@code assets/ae2/ae2guide/} 同构）。</li>
 * </ul>
 */
@EventBusSubscriber(modid = AeMonitorNet.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {

    /** 指南书 id / 内容目录：与 {@code assets/ae_monitornet/ae2guide/} 一致。 */
    private static final ResourceLocation GUIDE_ID =
            ResourceLocation.fromNamespaceAndPath(AeMonitorNet.MODID, "guide");
    private static final String GUIDE_FOLDER = "ae2guide";

    private static final Logger LOGGER = LoggerFactory.getLogger(AeMonitorNet.MODID);

    /**
     * 指南书必须在<b>第一次资源重载之前</b>登记（GuideME 的 reload listener 只在重载时读页面）。
     *
     * <p>⚠️ 实测：{@code FMLClientSetupEvent} 的监听器是<b>并行派发</b>的，它们可能在
     * vanilla 的 {@code ReloadableResourceManager#createReload} 已经开始之后才执行（本次运行里
     * "Reloading ResourceManager" 在 21:46:25.938 打出来，而同一事件的 PartModels 日志在
     * 21:46:26.319）——把登记放进 setup 回调会让首轮重载漏掉这本指南（页面数 = 0，
     * 要等下一次 F3+T 才补上）。因此放在静态初始化里：NeoForge 扫描并注册
     * {@code @EventBusSubscriber} 类时就会加载本类，那时还在模组加载阶段、远早于首次重载
     * （AE2 自己是在 {@code @Mod} 构造器里登记，时机等价）。
     */
    static {
        registerGuide();
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.getItemColors().register(
                // AE2 的 AEColor#getVariantByTintIndex 只返回 RGB（alpha=0）；1.21.1 的物品渲染会读 alpha，
                // 直接返回会让三个灯光层整层透明（物品栏图标只剩边框）。这里显式补成不透明。
                (stack, tintIndex) -> tintIndex <= 0 ? -1 : (0xFF000000 | AEColor.TRANSPARENT.getVariantByTintIndex(tintIndex)),
                ModItems.THROUGHPUT_MONITOR.get(),
                ModItems.ADVANCED_THROUGHPUT_MONITOR.get(),
                ModItems.THROUGHPUT_TERMINAL.get(),
                ModItems.WIRELESS_THROUGHPUT_TERMINAL.get());
    }

    /**
     * 菜单 → 屏幕（含样式 JSON 路径）的注册表。
     *
     * <p>样式路径与 1.20.1 逐字一致：样式文件仍在 {@code assets/ae2/screens/}（AE2 的
     * {@code StyleManager} 硬编码 {@code ae2} 命名空间，见 1.19.2 起的既有约束）。
     */
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        InitScreens.register(
                event,
                AdvancedThroughputMonitorMenu.TYPE,
                AdvancedThroughputMonitorScreen::new,
                "/screens/advanced_throughput_monitor.json");
        // 屏幕类是泛型的，必须像 AE2 注册样板管理终端那样显式给出类型见证，否则推断不出 M/U
        InitScreens.<ThroughputTerminalMenu, ThroughputTerminalScreen<ThroughputTerminalMenu>>register(
                event,
                ThroughputTerminalMenu.TYPE,
                ThroughputTerminalScreen::new,
                "/screens/throughput_terminal.json");
        // 无线版复用同一份样式（版式完全一致，只是菜单类型不同）。
        // 装了 AE2WTLib 时换成「感知通用终端」的界面（左侧多一个"切换终端"按钮 + 热键切换）；
        // 没装时用普通薄壳版 —— xmod 的类只有确认装了 mod 才会被加载（软依赖不能硬 import）。
        if (AE2WtLibPlugin.isLoaded()) {
            AE2WtLibClientHook.registerWirelessTerminalScreen(event);
        } else {
            InitScreens.register(
                    event,
                    ThroughputTerminalWirelessMenu.TYPE,
                    WirelessThroughputTerminalScreen::new,
                    "/screens/throughput_terminal.json");
        }

        // 屏幕注册失败同样是"静默"的（菜单能开、但没有任何屏幕 → 客户端看不到界面，日志无痕），
        // 所以像 PartModelsRegistration / 指南那样留一行启动日志作为"登记确实发生过"的直接证据。
        LOGGER.info("Registered 3 menu screens (advanced monitor / terminal / wireless terminal)");
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // 自建通道的客户端收包入口（行数据 → 当前打开的菜单；「定位」高亮 → 静态的 ClientHighlights）。
            // 通道本身在 MonitorNetNetwork 里由 RegisterPayloadHandlersEvent 建立，这里只把"客户端做什么"接上去，
            // 网络类因此不必 import 客户端类型。
            MonitorNetNetwork.setClientHandler(TerminalClientPackets::handle);
        });
    }

    /**
     * 注册指南书（GuideME 21.x 的代码式注册）。
     *
     * <p>不传 {@code defaultLanguage(...)}：GuideME 的默认就是 {@code en_us}，与旧
     * {@code guideme_guides/guide.json} 里的 {@code "default_language": "en_us"} 等价。
     * 起始页沿用默认值 {@code ae_monitornet:index.md}（即 {@code ae2guide/index.md}），
     * 中文页面放 {@code ae2guide/_zh_cn/index.md}（GuideME 的语言子目录约定）。
     *
     * <p>留一行日志：这本指南登记发生得早（静态初始化），失败时不会产生"缺失模型"之类的痕迹，
     * latest.log 里这一行是"登记确实发生过"的唯一直接证据。
     */
    private static void registerGuide() {
        Guide.builder(GUIDE_ID).folder(GUIDE_FOLDER).build();
        LOGGER.info("Registered guide {} (folder {})", GUIDE_ID, GUIDE_FOLDER);
    }
}
