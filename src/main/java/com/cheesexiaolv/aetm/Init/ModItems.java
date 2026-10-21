package com.cheesexiaolv.aetm.Init;

import com.cheesexiaolv.aetm.AeMonitorNet;
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
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AeMonitorNet.MODID);

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
     * 是否由 本模组 提供"基础吞吐量监控器"。
     *
     * <p>用户要求：装了对面那个 mod 时，同一个存档里<b>不能出现两个一模一样的吞吐量监控器</b>。
     * 所以对面在场时 本模组 的 {@code throughput_monitor}：
     * <ul>
     *   <li><b>仍然注册</b>（否则老存档里已经放下的零件会因为物品消失而炸）；</li>
     *   <li>但<b>不进创造标签</b>（见 {@code AeMonitorNet.TAB} 的 displayItems）、也<b>没有配方</b>
     *       （配方侧见 {@code data/ae_monitornet/recipes/throughput_monitor.json} 的 {@code forge:not} +
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
    //    装了 AE2WTLib 时换用它的子类（实现 IUniversalWirelessTerminalItem），这样同一个物品
    //    既能当普通无线终端用（安全终端链接 + WAP 范围），也能在「通用终端 / WUT」里被切换到。
    //    ⚠️ 子类只在装了 ae2wtlib 时才会被类加载：判断写在 createWirelessTerminal() 里，
    //       没装时那个类名永远不会被 JVM 解析（软依赖不能硬 import）——与 1.19.2 同一套隔离方式。
    public static final RegistryObject<Item> WIRELESS_THROUGHPUT_TERMINAL =
            ITEMS.register("wireless_throughput_terminal", ModItems::createWirelessTerminal);

    /** AE2WTLib 的 modid（软依赖）。 */
    private static final String AE2WTLIB_MODID = "ae2wtlib";

    /**
     * 无线终端物品的工厂：装了 AE2WTLib 就用它的通用终端子类，否则用不依赖它的基类。
     *
     * <p>两条路都注册在同一个 id {@code ae_monitornet:wireless_throughput_terminal} 上，
     * 所以既有配方、既有存档里的物品都不受影响（换环境只是换了个行为更全的子类）。
     */
    private static Item createWirelessTerminal() {
        Item.Properties properties = new Item.Properties().stacksTo(1);
        var modList = ModList.get();
        if (modList != null && modList.isLoaded(AE2WTLIB_MODID)) {
            // 全限定名 = 不 import，避免没装 AE2WTLib 的整合包在类加载期解析 de.mari_023.* 而崩
            return new com.cheesexiaolv.aetm.xmod.ae2wtlib.UniversalThroughputTerminalItem(properties);
        }
        return new WirelessThroughputTerminalItem(properties);
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
