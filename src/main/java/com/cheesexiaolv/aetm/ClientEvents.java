package com.cheesexiaolv.aetm;

import com.cheesexiaolv.aetm.Init.ModItems;

import appeng.api.util.AEColor;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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

    // ------------------------------------------------------------------
    // TODO(stage 2): 屏幕注册（1.20.1 第一阶段整体删除，重建时按下面的清单接回来）
    //
    // 1.19.2 的写法是 FMLClientSetupEvent#enqueueWork 里调
    //   appeng.init.client.InitScreens.register(MenuType, ScreenConstructor, "/screens/x.json")
    // 三个屏幕：
    //   - AdvancedThroughputMonitorMenu  TYPE -> client.gui.AdvancedThroughputMonitorScreen
    //                                          样式 /screens/advanced_throughput_monitor.json
    //   - ThroughputTerminalMenu         TYPE -> client.gui.ThroughputTerminalScreen
    //                                          样式 /screens/throughput_terminal.json
    //   - ThroughputTerminalWirelessMenu TYPE -> client.gui.WirelessThroughputTerminalScreen
    //                                          样式 /screens/throughput_terminal.json
    //
    // 1.20.1 的差别：屏幕方法收到的是 GuiGraphics（1.19.2 是 PoseStack 参数 + render 里自己开），
    // 字体绘制 drawInBatch 的第 8 个参数从 boolean 变成 Font.DisplayMode。
    // 详见 docs/port-1.20.1.md 里的"第二阶段清单"。
    // ------------------------------------------------------------------
}
