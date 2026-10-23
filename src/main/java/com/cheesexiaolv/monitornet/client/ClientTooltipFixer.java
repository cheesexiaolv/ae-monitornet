package com.cheesexiaolv.monitornet.client;

import com.cheesexiaolv.monitornet.AeMonitorNet;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 在**首个客户端 tick**执行 {@link MonitorTooltipLockFix}。
 *
 * <p>不能在 {@code FMLLoadCompleteEvent} 做：AE2 的 {@code TooltipProviders} 是在<b>静态初始化块</b>里
 * 注册那些 provider 的，而该类的加载晚于加载完成事件 —— 那时注册表还是空的，删不到东西，
 * 反而把我们自己的 provider 提前加进去，导致提示行重复。
 *
 * <p><b>1.20.1 → 1.21.1</b>：{@code net.minecraftforge.event.TickEvent.ClientTickEvent}（带
 * {@code Phase.END} 字段的单一事件类）在 NeoForge 21.1 被拆成
 * {@link ClientTickEvent.Pre}/{@link ClientTickEvent.Post} 两个独立事件类（包也从
 * {@code net.neoforged.neoforge.event.tick} 变成 {@code net.neoforged.neoforge.client.event}）——
 * 原来的 {@code event.phase != Phase.END} 判断因此由"订阅 Post 事件"直接表达。
 */
@EventBusSubscriber(modid = AeMonitorNet.MODID, value = Dist.CLIENT)
public final class ClientTooltipFixer {

    private static boolean installed;

    private ClientTooltipFixer() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (installed) {
            return;
        }
        installed = true;
        MonitorTooltipLockFix.install();
    }
}
