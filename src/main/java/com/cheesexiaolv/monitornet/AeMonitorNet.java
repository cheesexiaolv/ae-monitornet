package com.cheesexiaolv.monitornet;

import com.cheesexiaolv.monitornet.Init.ModItems;
import com.cheesexiaolv.monitornet.Parts.ThroughputTerminalPart;
import com.cheesexiaolv.monitornet.network.MonitorNetNetwork;
import com.cheesexiaolv.monitornet.xmod.ae2wtlib.AE2WtLibPlugin;

import appeng.api.parts.PartModels;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(AeMonitorNet.MODID)
public class AeMonitorNet {
    public static final String MODID = "ae_monitornet";

    private static final Logger LOGGER = LogManager.getLogger(MODID);

    /**
     * 创造模式物品栏。
     *
     * <p><b>1.19.2 → 1.20.1 的变化</b>：1.20 起创造标签是<b>注册表对象</b>
     * （{@code Registries.CREATIVE_MODE_TAB}），`Item.Properties#tab()` 已被删除，
     * 也不能再 `new CreativeModeTab(id){ makeIcon() }`。改为：
     * <ul>
     *   <li>用 {@link DeferredRegister} 注册一个 {@link CreativeModeTab}；</li>
     *   <li>标签内容由 {@code displayItems} 回调逐个 accept（不再是物品自己声明归属）。</li>
     * </ul>
     *
     * <p>这正好也让"装了 AdvancedAE 时不把基础监控器放进创造标签"变成一句普通判断
     * （见 {@link ModItems#providesBasicMonitor()}）——1.19.2 是靠"不给物品挂 .tab()" 实现的。
     */
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("ae_monitornet", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.ae_monitornet"))
            .icon(() -> new ItemStack(ModItems.THROUGHPUT_MONITOR.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.THROUGHPUT_MONITOR_CONFIGURATOR.get());
                // 装了 AdvancedAE 时它的吞吐量监控器已经占了这个生态位 → 我们的基础版只注册、不进标签
                if (ModItems.providesBasicMonitor()) {
                    output.accept(ModItems.THROUGHPUT_MONITOR.get());
                }
                output.accept(ModItems.ADVANCED_THROUGHPUT_MONITOR.get());
                output.accept(ModItems.THROUGHPUT_TERMINAL.get());
                output.accept(ModItems.WIRELESS_THROUGHPUT_TERMINAL.get());
            })
            .build());

    /**
     * 构造函数。
     *
     * <p>Forge 47 起推荐用<b>构造器注入</b>拿到语言加载器的上下文
     * （{@code FMLModContainer.constructMod()} 先找带 {@code FMLJavaModLoadingContext} 参数的构造器，
     * 找不到才退回无参构造器），这样不必再调已弃用的
     * {@code FMLJavaModLoadingContext.get()} / {@code ModLoadingContext.get()}；
     * 传进来的是同一个实例，行为不变。
     */
    public AeMonitorNet(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        ModItems.ITEMS.register(modEventBus);
        TABS.register(modEventBus);

        // AE2WTLib 的终端要在 AE2 关闭热键注册窗口之前"把热键 id 占好"。
        // 必须排在自己的 DeferredRegister 之后（mod 事件总线按监听器登记顺序派发）。
        AE2WtLibPlugin.registerEarlyHook(modEventBus);

        // 自建网络通道（AE2 的包类型是闭合枚举，聚合终端的行数据只能自己发）
        MonitorNetNetwork.init();

        // 配置文件：服务端类型 → <存档>/serverconfig/ae_monitornet-server.toml
        // Forge 会监听该文件，改完即时生效（热加载），无需重启
        context.registerConfig(ModConfig.Type.SERVER, MonitorNetConfig.SPEC);

        // AE2 的部件模型必须在它冻结列表之前登记：`PartModelsInternal.freeze()` 发生在第一次
        // `ModelEvent.RegisterAdditional`（AE2 15.4.10 同 19.x 行为），之后一律抛
        // `IllegalStateException: Cannot register models after the pre-initialization phase!`。
        //
        // ⚠️ 以前挂在 `FMLClientSetupEvent`（客户端事件）—— 在大型整合包里不够早：有些模组的
        // client-setup 监听器会先触发一次资源重载，模型提前烘焙、列表被冻结，等我们的监听器跑到时
        // 已经晚了（2026-11-21 实机复现：extraordinary_energy_modern 包，AE2 15.4.10 + Forge 47.4.20，
        // 整个 mod 加载直接失败）。改到 mod 构造器里登记，早于任何事件。
        //
        // `PartModels#registerModels` 只是往静态 Set 里塞 ResourceLocation，构造期调用安全
        //（不碰注册表、不需要客户端类）；`MODEL_OFF/ON` 是普通 ResourceLocation。
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
