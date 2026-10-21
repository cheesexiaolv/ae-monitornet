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
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Aetm.MODID);

    /**
     * 提供"吞吐量监控器"这一生态位的其它 mod。
     *
     * <p><b>1.20.1 分支的主角是 AdvancedAE（{@code advanced_ae}）</b>：它的
     * {@code net.pedroksl.advanced_ae.common.parts.ThroughputMonitorPart}
     * 就是同一套东西（本模组最早即据其移植）。<br>
     * 另两个 id 是历史遗留（{@code aebackports} / {@code aaemonitor}，1.19.2 时代的
     * 自家附属，见 {@code reference/}），1.20.1 分支已不连接它们，但保留识别没有任何代价：
     * 万一整合包里同时出现，行为仍然是"只有一种吞吐量监控器"。
     */
    private static final String[] PROVIDER_MODIDS = { "advanced_ae", "aebackports", "aaemonitor" };

    private static boolean isProviderLoaded() {
        var modList = ModList.get();
        if (modList == null) {
            // ModList 还没就绪（只可能发生在极早的类加载里）：按"对面不在场"处理，
            // 与 1.19.2 的默认行为一致；不要去猜。
            return false;
        }
        for (String modid : PROVIDER_MODIDS) {
            if (modList.isLoaded(modid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否由 aetm 提供"基础吞吐量监控器"。
     *
     * <p>用户要求：装了对面那个 mod 时，同一个存档里<b>不能出现两个一模一样的吞吐量监控器</b>。
     * 所以对面在场时 aetm 的 {@code throughput_monitor}：
     * <ul>
     *   <li><b>仍然注册</b>（否则老存档里已经放下的零件会因为物品消失而炸）；</li>
     *   <li>但<b>不进创造标签</b>（见 {@code Aetm.TAB} 的 displayItems）、也<b>没有配方</b>
     *       （配方侧见 {@code data/aetm/recipes/throughput_monitor.json} 的 {@code forge:not} +
     *       {@code forge:mod_loaded} 反条件）。</li>
     * </ul>
     * 对面不在场时行为与以前完全一致（进标签 + 有配方）。
     *
     * <p>刻意做成<b>惰性</b>（1.19.2 是静态 final 字段）：静态字段会在 mod 构造期求值，
     * 那时 {@code ModList} 未必已经就绪；创造标签的 {@code displayItems} 回调则一定在 mod 加载完成后才跑。
     */
    private static Boolean basicMonitorProvided;

    public static boolean providesBasicMonitor() {
        if (basicMonitorProvided == null) {
            basicMonitorProvided = !isProviderLoaded();
        }
        return basicMonitorProvided;
    }

    // 1. 吞吐量监控配置器
    public static final RegistryObject<Item> THROUGHPUT_MONITOR_CONFIGURATOR = ITEMS.register("throughput_monitor_configurator",
            () -> new ThroughputMonitorConfigurator(new Item.Properties()));

    // 2. 吞吐量监控器面板（对面 mod 在场时只注册、不进标签，见 PROVIDE_BASIC_MONITOR）
    public static final RegistryObject<PartItem<ThroughputMonitorPart>> THROUGHPUT_MONITOR = ITEMS.register("throughput_monitor",
            () -> new PartItem<>(
                    new Item.Properties(),
                    ThroughputMonitorPart.class,
                    ThroughputMonitorPart::new
            ));

    // 3. 高级吞吐量监控器（套壳阶段：行为与吞吐量监控器一致，后续叠加 GUI/图表/算法）
    public static final RegistryObject<PartItem<AdvancedThroughputMonitorPart>> ADVANCED_THROUGHPUT_MONITOR =
            ITEMS.register("advanced_throughput_monitor",
                    () -> new PartItem<>(
                            new Item.Properties(),
                            AdvancedThroughputMonitorPart.class,
                            AdvancedThroughputMonitorPart::new
                    ));

    // 4. 聚合吞吐终端：枚举本网络所有监控器，按 key 合并成列表（图表 / 单行档位）
    public static final RegistryObject<PartItem<ThroughputTerminalPart>> THROUGHPUT_TERMINAL =
            ITEMS.register("throughput_terminal",
                    () -> new PartItem<>(
                            new Item.Properties(),
                            ThroughputTerminalPart.class,
                            ThroughputTerminalPart::new
                    ));

    // 5. 无线存储监控终端：与有线版共用菜单/界面，靠 AE2 的无线终端机制链接与耗电。
    //    TODO(stage 2): 1.20.1 的 AE2WTLib（WUT / 通用终端）集成待补 —— 1.19.2 里这里会在装了
    //    ae2wtlib 时换用 UniversalThroughputTerminalItem（实现 IUniversalWirelessTerminalItem）。
    //    本机没有 1.20.1 的 AE2WTLib jar，xmod/ae2wtlib 整个包已在第一阶段删除，
    //    所以这里只保留不依赖它的基类；老存档里的同 id 物品不受影响（id 没变）。
    public static final RegistryObject<Item> WIRELESS_THROUGHPUT_TERMINAL =
            ITEMS.register("wireless_throughput_terminal", ModItems::createWirelessTerminal);

    private static Item createWirelessTerminal() {
        return new WirelessThroughputTerminalItem(new Item.Properties().stacksTo(1));
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
