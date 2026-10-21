package com.cheesexiaolv.aetm;

import com.cheesexiaolv.aetm.Init.ModItems;
import com.cheesexiaolv.aetm.client.TerminalClientPackets;
import com.cheesexiaolv.aetm.client.gui.AdvancedThroughputMonitorScreen;
import com.cheesexiaolv.aetm.client.gui.ThroughputTerminalScreen;
import com.cheesexiaolv.aetm.client.gui.WirelessThroughputTerminalScreen;
import com.cheesexiaolv.aetm.menu.AdvancedThroughputMonitorMenu;
import com.cheesexiaolv.aetm.menu.ThroughputTerminalMenu;
import com.cheesexiaolv.aetm.menu.ThroughputTerminalWirelessMenu;
import com.cheesexiaolv.aetm.network.AetmNetwork;
import com.cheesexiaolv.aetm.xmod.ae2wtlib.AE2WtLibClientHook;
import com.cheesexiaolv.aetm.xmod.ae2wtlib.AE2WtLibPlugin;

import appeng.api.util.AEColor;
import appeng.init.client.InitScreens;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端注册。
 *
 * <p>AE2 会对自己所有的 {@code PartItem} 注册 item 颜色（用 {@code AEColor.TRANSPARENT} 解析
 * 显示模型里 tintindex 1–4 的多层高亮贴图）。第三方零件物品不会被 AE2 自动处理，若不手动注册，
 * {@code ae2:item/display_base} 那几层会按原色叠加渲染，导致物品图标异常。这里补齐同样的处理。
 *
 * <p>屏幕注册沿用 AE2 自己的 {@link InitScreens#register}（15.4.10 仍在
 * {@code appeng.init.client.InitScreens}，签名未变：{@code (MenuType<M>, StyledScreenFactory, String)}），
 * 时机仍是 {@code FMLClientSetupEvent#enqueueWork}——与 1.19.2 完全一致。
 */
@Mod.EventBusSubscriber(modid = Aetm.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.getItemColors().register(
                (stack, tintIndex) -> AEColor.TRANSPARENT.getVariantByTintIndex(tintIndex),
                ModItems.THROUGHPUT_MONITOR.get(),
                ModItems.ADVANCED_THROUGHPUT_MONITOR.get(),
                ModItems.THROUGHPUT_TERMINAL.get(),
                ModItems.WIRELESS_THROUGHPUT_TERMINAL.get());
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // 自建通道的客户端收包入口（行数据 → 当前打开的菜单；「定位」高亮 → 静态的 ClientHighlights）。
            // 通道本身在 Aetm 构造器里建立，这里只把"客户端做什么"接上去，网络类因此不必 import 客户端类型。
            AetmNetwork.setClientHandler(TerminalClientPackets::handle);

            InitScreens.register(
                    AdvancedThroughputMonitorMenu.TYPE,
                    AdvancedThroughputMonitorScreen::new,
                    "/screens/advanced_throughput_monitor.json");
            // 屏幕类是泛型的，必须像 AE2 注册样板管理终端那样显式给出类型见证，否则推断不出 M/U
            InitScreens.<ThroughputTerminalMenu, ThroughputTerminalScreen<ThroughputTerminalMenu>>register(
                    ThroughputTerminalMenu.TYPE,
                    ThroughputTerminalScreen::new,
                    "/screens/throughput_terminal.json");
            // 无线版复用同一份样式（版式完全一致，只是菜单类型不同）。
            // 装了 AE2WTLib 时改用「感知通用终端」的界面：从通用终端里打开时要有"切换到下一个终端"的按钮
            // （界面的 WUT 判定放在 xmod 包里，避免没装 AE2WTLib 时加载到 de.mari_023.* 类型）。
            if (ModList.get().isLoaded(AE2WtLibPlugin.MOD_ID)) {
                AE2WtLibClientHook.registerWirelessTerminalScreen("/screens/throughput_terminal.json");
            } else {
                InitScreens.register(
                        ThroughputTerminalWirelessMenu.TYPE,
                        WirelessThroughputTerminalScreen::new,
                        "/screens/throughput_terminal.json");
            }
        });
    }
}
