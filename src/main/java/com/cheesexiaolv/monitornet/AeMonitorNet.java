package com.cheesexiaolv.monitornet;

import com.cheesexiaolv.monitornet.Init.ModItems;
import com.cheesexiaolv.monitornet.Parts.ThroughputTerminalPart;
import com.cheesexiaolv.monitornet.xmod.ae2wtlib.AE2WtLibPlugin;

import appeng.api.parts.PartModels;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Mod entry point for MC 1.21.1 / NeoForge 21.1.
 *
 * <p><b>1.20.1 → 1.21.1 changes</b>:
 * <ul>
 *   <li>{@code @Mod} moved to {@code net.neoforged.fml.common.Mod}. Constructor injection now
 *       supplies the mod event bus and the {@link ModContainer} directly — the
 *       {@code FMLJavaModLoadingContext} class is gone. (AE2 19.2.17 does the same:
 *       {@code appeng/core/AppEngBase.java:77}.)</li>
 *   <li>Config registration moved from {@code context.registerConfig(...)} to
 *       {@code modContainer.registerConfig(...)}.</li>
 *   <li>{@code ForgeRegistries} is gone: registries are addressed through
 *       {@link Registries} keys and {@code DeferredRegister} lives in
 *       {@code net.neoforged.neoforge.registries}.</li>
 *   <li>{@code RegistryObject} was replaced by {@link DeferredHolder}.</li>
 * </ul>
 */
@Mod(AeMonitorNet.MODID)
public class AeMonitorNet {
    public static final String MODID = "ae_monitornet";

    private static final Logger LOGGER = LogManager.getLogger(MODID);

    /**
     * Creative tab (a registry object since 1.20; unchanged in 1.21.1 apart from the
     * {@link DeferredRegister}/{@link DeferredHolder} types).
     */
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("ae_monitornet",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ae_monitornet"))
                    .icon(() -> new ItemStack(ModItems.THROUGHPUT_MONITOR_CONFIGURATOR.get()))
                    .displayItems((params, output) -> {
                        // 配置器与基础监控器都受"别的模组已提供"开关控制（AAE 自带同名配置器，
                        // 两个都显示就会出现两把同名的工具）
                        if (ModItems.providesConfigurator()) {
                            output.accept(ModItems.THROUGHPUT_MONITOR_CONFIGURATOR.get());
                        }
                        if (ModItems.providesBasicMonitor()) {
                            output.accept(ModItems.THROUGHPUT_MONITOR.get());
                        }
                        output.accept(ModItems.ADVANCED_THROUGHPUT_MONITOR.get());
                        output.accept(ModItems.THROUGHPUT_TERMINAL.get());
                        output.accept(ModItems.WIRELESS_THROUGHPUT_TERMINAL.get());
                    })
                    .build());

    public AeMonitorNet(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.ITEMS.register(modEventBus);
        TABS.register(modEventBus);

        // AE2WTLib (universal terminal) soft hook: must run AFTER our DeferredRegister above so
        // the AE2WTLib terminal callback is queued on the same mod event bus we are already part
        // of, and BEFORE any registry event (AE2WTLib collects the terminal callbacks and runs
        // them once from `ae2wtlib_api`'s ITEM listener; see xmod/ae2wtlib). This only queues a
        // callback -- it never touches a registry, so it is safe during mod construction.
        // No-op when AE2WTLib is absent (the `de.mari_023.*` classes are never loaded).
        AE2WtLibPlugin.registerTerminals();

        // Server config: <world>/serverconfig/ae_monitornet-server.toml (hot-reloaded by NeoForge).
        modContainer.registerConfig(ModConfig.Type.SERVER, MonitorNetConfig.SPEC);

        // AE2 的部件模型必须在它冻结列表之前登记：`PartModelsInternal.freeze()` 发生在第一次
        // `ModelEvent.RegisterAdditional`（AE2 19.2.18 `InitAdditionalModels#init` 里），之后一律抛
        // `IllegalStateException: Cannot register models after the pre-initialization phase!`。
        //
        // ⚠️ 以前挂在 `FMLClientSetupEvent`（客户端事件）—— 在大型整合包里不够早：有些模组的
        // client-setup 监听器会先触发一次资源重载，模型烘焙随之发生、列表被冻结，等我们的监听器
        // 跑到时已经晚了（2026-11-15 实机复现：AE2 19.2.18 + AQS030 包，结果是终端部件模型没登记、
        // 该 mod 报 "Mod loading issue"）。改到 mod 构造器里登记，早于任何事件。
        //
        // `PartModels#registerModels` 只是往一个静态 Set 里塞 ResourceLocation，构造期调用安全
        // （不碰注册表、不需要客户端类）；`MODEL_OFF/ON` 是普通 ResourceLocation。
        try {
            PartModels.registerModels(ThroughputTerminalPart.MODEL_OFF, ThroughputTerminalPart.MODEL_ON);
            LOGGER.info("Registered 2 custom part models with AE2 (terminal_off/terminal_on)");
        } catch (IllegalStateException e) {
            // 只有"别的 mod 在构造期之前就把模型列表冻结了"才会走到这里；部件仍然能用，
            // 只是终端部件表面会退回默认模型，所以只记一条明确的警告、不让整个 mod 加载失败。
            LOGGER.warn("Could not register custom part models with AE2 (models were already frozen): {}",
                    e.getMessage());
        }
    }
}
