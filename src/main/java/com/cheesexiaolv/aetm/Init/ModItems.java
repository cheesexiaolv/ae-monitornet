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
     * 提供"基础吞吐量监控器"的 modid。
     *
     * <p>该 mod 在 v2.0.0 由 {@code aaemonitor} 改名为 {@code aebackports}（包名也从
     * {@code com.cheesexiaolv.Aaemonitor} 变成 {@code com.cheesexiaolv.aebackports}）。
     * 为了同时兼容新旧两个 jar，这里两个 id 都认。
     */
    private static final String[] PROVIDER_MODIDS = { "aebackports", "aaemonitor" };

    private static boolean isProviderLoaded() {
        for (String modid : PROVIDER_MODIDS) {
            if (ModList.get().isLoaded(modid)) {
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
     *   <li>但<b>不进创造标签</b>、也<b>没有配方</b>（配方侧见
     *       {@code data/aetm/recipes/throughput_monitor.json} 的 {@code forge:not} + {@code forge:mod_loaded} 反条件）。</li>
     * </ul>
     * 对面不在场时行为与以前完全一致（进标签 + 有配方）。
     */
    private static final boolean PROVIDE_BASIC_MONITOR = !isProviderLoaded();

    /** 给物品属性按需挂上创造标签（对面在场时不挂 → 不出现在创造模式物品栏里）。 */
    private static Item.Properties tabProperties() {
        Item.Properties properties = new Item.Properties();
        return PROVIDE_BASIC_MONITOR ? properties.tab(Aetm.TAB) : properties;
    }

    // 1. 吞吐量监控配置器
    public static final RegistryObject<Item> THROUGHPUT_MONITOR_CONFIGURATOR = ITEMS.register("throughput_monitor_configurator",
            () -> new ThroughputMonitorConfigurator(new Item.Properties().tab(Aetm.TAB)));

    // 2. 吞吐量监控器面板（对面 mod 在场时只注册、不进标签，见 PROVIDE_BASIC_MONITOR）
    public static final RegistryObject<PartItem<ThroughputMonitorPart>> THROUGHPUT_MONITOR = ITEMS.register("throughput_monitor",
            () -> new PartItem<>(
                    tabProperties(),
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

    // 5. 无线存储监控终端：与有线版共用菜单/界面，靠 AE2 的无线终端机制链接与耗电。
    //    装了 AE2WTLib 时换用它的子类（实现 IUniversalWirelessTerminalItem），这样同一个物品
    //    既能当普通无线终端用（安全终端链接 + WAP 范围），也能在「通用终端 / WUT」里被切换到。
    //    ⚠️ 子类只在装了 ae2wtlib 时才会被类加载：判断写在 createWirelessTerminal() 里，
    //       没装时那个类名永远不会被 JVM 解析（软依赖不能硬 import）。
    public static final RegistryObject<Item> WIRELESS_THROUGHPUT_TERMINAL =
            ITEMS.register("wireless_throughput_terminal", ModItems::createWirelessTerminal);

    /** AE2WTLib 的 modid（软依赖）。 */
    private static final String AE2WTLIB_MODID = "ae2wtlib";

    /**
     * 无线终端物品的工厂：装了 AE2WTLib 就用它的通用终端子类，否则用不依赖它的基类。
     *
     * <p>两条路都注册在同一个 id {@code aetm:wireless_throughput_terminal} 上，
     * 所以既有配方、既有存档里的物品都不受影响（换环境只是换了个行为更全的子类）。
     */
    private static Item createWirelessTerminal() {
        Item.Properties properties = new Item.Properties().tab(Aetm.TAB).stacksTo(1);
        if (ModList.get().isLoaded(AE2WTLIB_MODID)) {
            return new com.cheesexiaolv.aetm.xmod.ae2wtlib.UniversalThroughputTerminalItem(properties);
        }
        return new WirelessThroughputTerminalItem(properties);
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
