package com.cheesexiaolv.aetm;

import com.cheesexiaolv.aetm.Init.ModItems;
import com.cheesexiaolv.aetm.client.gui.AdvancedThroughputMonitorScreen;
import com.cheesexiaolv.aetm.client.gui.ThroughputTerminalScreen;
import com.cheesexiaolv.aetm.client.gui.WirelessThroughputTerminalScreen;
import com.cheesexiaolv.aetm.menu.AdvancedThroughputMonitorMenu;
import com.cheesexiaolv.aetm.menu.ThroughputTerminalMenu;
import com.cheesexiaolv.aetm.menu.ThroughputTerminalWirelessMenu;

import appeng.api.util.AEColor;
import appeng.init.client.InitScreens;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端注册。
 *
 * <p>AE2 会对自己所有的 {@code PartItem} 注册 item 颜色（用 {@code AEColor.TRANSPARENT} 解析
 * 显示模型里 tintindex 1–4 的多层高亮贴图）。第三方零件物品不会被 AE2 自动处理，若不手动注册，
 * {@code ae2:item/display_base} 那几层会按原色叠加渲染，导致物品图标异常。这里补齐同样的处理。
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
            InitScreens.register(
                    AdvancedThroughputMonitorMenu.TYPE,
                    AdvancedThroughputMonitorScreen::new,
                    "/screens/advanced_throughput_monitor.json");
            // 屏幕类是泛型的，必须像 AE2 注册样板管理终端那样显式给出类型见证，否则推断不出 M/U
            InitScreens.<ThroughputTerminalMenu, ThroughputTerminalScreen<ThroughputTerminalMenu>>register(
                    ThroughputTerminalMenu.TYPE,
                    ThroughputTerminalScreen::new,
                    "/screens/throughput_terminal.json");
            // 无线版复用同一份样式（版式完全一致，只是菜单类型不同）
            InitScreens.register(
                    ThroughputTerminalWirelessMenu.TYPE,
                    WirelessThroughputTerminalScreen::new,
                    "/screens/throughput_terminal.json");
        });
    }
}
