package com.cheesexiaolv.aetm.Init;

import com.cheesexiaolv.aetm.Aetm;
import com.cheesexiaolv.aetm.Items.ThroughputMonitorConfigurator;
import com.cheesexiaolv.aetm.Items.WirelessThroughputTerminalItem;
import com.cheesexiaolv.aetm.Parts.AdvancedThroughputMonitorPart;
import com.cheesexiaolv.aetm.Parts.ThroughputMonitorPart;
import com.cheesexiaolv.aetm.Parts.ThroughputTerminalPart;
import appeng.items.parts.PartItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Aetm.MODID);

    // 1. 吞吐量监控配置器
    public static final RegistryObject<Item> THROUGHPUT_MONITOR_CONFIGURATOR = ITEMS.register("throughput_monitor_configurator",
            () -> new ThroughputMonitorConfigurator(new Item.Properties().tab(Aetm.TAB)));

    // 2. 吞吐量监控器面板
    public static final RegistryObject<PartItem<ThroughputMonitorPart>> THROUGHPUT_MONITOR = ITEMS.register("throughput_monitor",
            () -> new PartItem<>(
                    new Item.Properties().tab(Aetm.TAB),
                    ThroughputMonitorPart.class,
                    ThroughputMonitorPart::new
            ));

    // 3. 高级吞吐量监控器（套壳阶段：行为与吞吐量监控器一致，后续叠加 GUI/图表/算法）
    public static final RegistryObject<PartItem<AdvancedThroughputMonitorPart>> ADVANCED_THROUGHPUT_MONITOR =
            ITEMS.register("advanced_throughput_monitor",
                    () -> new PartItem<>(
                            new Item.Properties().tab(Aetm.TAB),
                            AdvancedThroughputMonitorPart.class,
                            AdvancedThroughputMonitorPart::new
                    ));

    // 4. 聚合吞吐终端：枚举本网络所有监控器，按 key 合并成列表（图表 / 单行档位）
    public static final RegistryObject<PartItem<ThroughputTerminalPart>> THROUGHPUT_TERMINAL =
            ITEMS.register("throughput_terminal",
                    () -> new PartItem<>(
                            new Item.Properties().tab(Aetm.TAB),
                            ThroughputTerminalPart.class,
                            ThroughputTerminalPart::new
                    ));

    // 5. 无线聚合吞吐终端：与有线版共用菜单/界面，靠 AE2 的无线终端机制链接与耗电
    public static final RegistryObject<Item> WIRELESS_THROUGHPUT_TERMINAL =
            ITEMS.register("wireless_throughput_terminal",
                    () -> new WirelessThroughputTerminalItem(
                            new Item.Properties().tab(Aetm.TAB).stacksTo(1)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
