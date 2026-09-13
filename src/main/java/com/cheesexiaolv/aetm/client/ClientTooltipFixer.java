package com.cheesexiaolv.aetm.client;

import com.cheesexiaolv.aetm.Aetm;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 在**首个客户端 tick**执行 {@link MonitorTooltipLockFix}。
 *
 * <p>不能在 {@code FMLLoadCompleteEvent} 做：AE2 的 {@code TooltipProviders} 是在<b>静态初始化块</b>里
 * 注册那些 provider 的，而该类的加载晚于加载完成事件 —— 那时注册表还是空的，删不到东西，
 * 反而把我们自己的 provider 提前加进去，导致提示行重复。
 */
@Mod.EventBusSubscriber(modid = Aetm.MODID, value = Dist.CLIENT)
public final class ClientTooltipFixer {

    private static boolean installed;

    private ClientTooltipFixer() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (installed || event.phase != TickEvent.Phase.END) {
            return;
        }
        installed = true;
        MonitorTooltipLockFix.install();
    }
}
