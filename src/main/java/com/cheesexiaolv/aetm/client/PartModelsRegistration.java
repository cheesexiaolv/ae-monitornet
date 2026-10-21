package com.cheesexiaolv.aetm.client;

import com.cheesexiaolv.aetm.AeMonitorNet;
import com.cheesexiaolv.aetm.Parts.ThroughputMonitorPart;
import com.cheesexiaolv.aetm.Parts.ThroughputTerminalPart;

import appeng.api.parts.PartModels;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 把本模组的“自有部件模型”登记给 AE2 的部件模型烘焙器。
 *
 * <p>AE2 的部件在世界里不走 blockstate，而走它自己的烘焙管线：
 * <ol>
 *   <li>{@code appeng.init.client.InitAdditionalModels} 在 {@code ModelEvent.RegisterAdditional} 里先
 *       {@code PartModelsInternal.freeze()}，再把 {@code appeng.api.parts.PartModels} 中登记过的 id
 *       逐个 {@code event.register(...)}；</li>
 *   <li>{@code appeng.client.render.cablebus.CableBusModel} 烘焙时按这些 id 逐个
 *       {@code ModelBakery#bake}，供 {@code CableBusBakedModel} 渲染时按
 *       {@code IPartModel#getModels()} 取用。</li>
 * </ol>
 *
 * <p>所以：<b>自定义 part 模型的 id 不登记 = 世界里的部件不显示</b>，而且不会产生“缺失模型”
 * 之类的日志，只是渲染层找不到模型。{@code PartModels#registerModels} 只能在 {@code freeze()}
 * 之前调用（之后抛 {@code IllegalStateException}），而 {@code FMLClientSetupEvent} 一定早于
 * 第一次资源重载（模型烘焙），因此放在这里。AE2 本体是在建零件定义（{@code AEParts}）时登记的，
 * 时机等价。
 */
@Mod.EventBusSubscriber(modid = AeMonitorNet.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PartModelsRegistration {

    private static final Logger LOGGER = LogManager.getLogger(AeMonitorNet.MODID);

    private PartModelsRegistration() {
    }

    @SubscribeEvent
    public static void registerPartModels(FMLClientSetupEvent event) {
        PartModels.registerModels(
                // 存储监控终端（样板管理终端外观）
                ThroughputTerminalPart.MODEL_BASE,
                ThroughputTerminalPart.MODEL_OFF,
                ThroughputTerminalPart.MODEL_ON,
                // 吞吐量监控器（普通版与高级版共用同一套外观）
                ThroughputMonitorPart.MODEL_BASE,
                ThroughputMonitorPart.MODEL_OFF,
                ThroughputMonitorPart.MODEL_ON);

        // 这条管线失败时 AE2 不报错（找不到模型只是不渲染），所以留一行启动日志：
        // 世界里部件外观走的是不是自有模型，可直接从 latest.log 确认这次登记有没有发生。
        LOGGER.info("Registered 6 custom part models with AE2 "
                + "(terminal_base/terminal_off/terminal_on, monitor_base/monitor_off/monitor_on)");
    }
}
