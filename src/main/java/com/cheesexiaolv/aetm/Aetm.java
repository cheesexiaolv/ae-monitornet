package com.cheesexiaolv.aetm;

import com.cheesexiaolv.aetm.Init.ModItems;
import com.cheesexiaolv.aetm.network.AetmNetwork;
import com.cheesexiaolv.aetm.xmod.ae2wtlib.AE2WtLibPlugin;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

@Mod(Aetm.MODID)
public class Aetm {
    public static final String MODID = "aetm";

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

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("aetm", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.aetm"))
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

    public Aetm() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modEventBus);
        TABS.register(modEventBus);

        // AE2WTLib 的终端要在 AE2 关闭热键注册窗口之前"把热键 id 占好"。
        // 必须排在自己的 DeferredRegister 之后（mod 事件总线按监听器登记顺序派发）。
        AE2WtLibPlugin.registerEarlyHook(modEventBus);

        // 自建网络通道（AE2 的包类型是闭合枚举，聚合终端的行数据只能自己发）
        AetmNetwork.init();

        // 配置文件：服务端类型 → <存档>/serverconfig/aetm-server.toml
        // Forge 会监听该文件，改完即时生效（热加载），无需重启
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, AetmConfig.SPEC);
    }
}
