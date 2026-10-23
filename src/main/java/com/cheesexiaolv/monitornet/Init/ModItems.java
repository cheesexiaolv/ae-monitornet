package com.cheesexiaolv.monitornet.Init;

import com.cheesexiaolv.monitornet.AeMonitorNet;
import com.cheesexiaolv.monitornet.Items.ThroughputMonitorConfigurator;
import com.cheesexiaolv.monitornet.Items.WirelessThroughputTerminalItem;
import com.cheesexiaolv.monitornet.Parts.AdvancedThroughputMonitorPart;
import com.cheesexiaolv.monitornet.Parts.ThroughputMonitorPart;
import com.cheesexiaolv.monitornet.Parts.ThroughputTerminalPart;
import com.cheesexiaolv.monitornet.xmod.ae2wtlib.AE2WtLibHook;
import com.cheesexiaolv.monitornet.xmod.ae2wtlib.AE2WtLibPlugin;
import appeng.items.parts.PartItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item registration (1.21.1).
 *
 * <p><b>All 5 item ids are registered here</b>: the configurator, the basic/advanced throughput
 * monitors, the aggregated terminal part and the wireless terminal item. Ids must never be left
 * unregistered: parts/items already placed in an old world would silently turn into the wrong type
 * (see {@code docs/port-1.21.1.md} §5).
 *
 * <p>1.21.1 changes: {@code ForgeRegistries.ITEMS} -> {@link Registries#ITEM},
 * {@code RegistryObject} -> {@link DeferredHolder}.
 */
public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, AeMonitorNet.MODID);

    /**
     * Other mods that already provide the "throughput monitor" niche.
     *
     * <p>1.20.1/1.21.1 counterpart is AdvancedAE ({@code advanced_ae}); {@code aebackports} /
     * {@code aaemonitor} are historical ids of our own 1.19.2 addons. Recognising them costs
     * nothing: if a pack ships several at once, only one kind of throughput monitor ends up
     * craftable/visible.
     */
    private static final String[] PROVIDER_MODIDS = { "advanced_ae", "aebackports", "aaemonitor" };

    private static boolean isProviderLoaded() {
        var modList = ModList.get();
        if (modList == null) {
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
     * Whether THIS mod provides the basic throughput monitor.
     *
     * <p>When the other mod is present our {@code throughput_monitor} is still registered (old
     * worlds keep their parts) but is not shown in the creative tab and has no recipe
     * ({@code data/ae_monitornet/recipes/throughput_monitor.json} uses the mod_loaded condition).
     * Lazy evaluation on purpose: static initialisers run during mod construction, before
     * {@code ModList} is guaranteed to be ready.
     */
    private static Boolean basicMonitorProvided;

    public static boolean providesBasicMonitor() {
        if (basicMonitorProvided == null) {
            basicMonitorProvided = !isProviderLoaded();
        }
        return basicMonitorProvided;
    }

    // 1. throughput monitor configurator
    public static final DeferredHolder<Item, Item> THROUGHPUT_MONITOR_CONFIGURATOR =
            ITEMS.register("throughput_monitor_configurator",
                    () -> new ThroughputMonitorConfigurator(new Item.Properties()));

    // 2. throughput monitor panel (only registered, not in the tab, when the other mod is present)
    public static final DeferredHolder<Item, PartItem<ThroughputMonitorPart>> THROUGHPUT_MONITOR =
            ITEMS.register("throughput_monitor",
                    () -> new PartItem<>(
                            new Item.Properties(),
                            ThroughputMonitorPart.class,
                            ThroughputMonitorPart::new
                    ));

    // 3. advanced throughput monitor (config slot + history chart)
    public static final DeferredHolder<Item, PartItem<AdvancedThroughputMonitorPart>> ADVANCED_THROUGHPUT_MONITOR =
            ITEMS.register("advanced_throughput_monitor",
                    () -> new PartItem<>(
                            new Item.Properties(),
                            AdvancedThroughputMonitorPart.class,
                            AdvancedThroughputMonitorPart::new
                    ));

    // 4. aggregated throughput terminal: enumerates every monitor on the network, merges rows by key
    public static final DeferredHolder<Item, PartItem<ThroughputTerminalPart>> THROUGHPUT_TERMINAL =
            ITEMS.register("throughput_terminal",
                    () -> new PartItem<>(
                            new Item.Properties(),
                            ThroughputTerminalPart.class,
                            ThroughputTerminalPart::new
                    ));

    // 5. wireless storage monitoring terminal: shares the wired menu/screen; linking and power drain
    //    use AE2's wireless-terminal mechanics.
    //
    //    ⚠️ 1.21.1, two constraints meet here:
    //    (a) An `Item` instance may NOT be created during mod construction: NeoForge's `Item`
    //        constructor calls `Registry#createIntrusiveHolder`, and the vanilla ITEM registry is
    //        frozen outside its RegisterEvent (Measured: constructing one in the @Mod constructor
    //        aborts startup with `IllegalStateException: Registry is already frozen`).
    //    (b) AE2WTLib 19.5.1's terminal registration needs the *item instance* while that very
    //        ITEM event is running (`WTDefinition` holds an `ItemWT`; there is no "swap the item
    //        in later" hook), and our DeferredRegister's listener may run before OR after
    //        `ae2wtlib_api`'s listener that actually fires the registration.
    //    So the instance is created lazily and memoized: whichever side asks first builds it
    //    (both run inside the unfrozen ITEM event), and the other side reuses it.
    //
    //    Which class is used is decided by createWirelessTerminal(): with AE2WTLib present it is
    //    `xmod.ae2wtlib.UniversalThroughputTerminalItem` (extends AE2WTLib's `ItemWT`), otherwise
    //    the AE2-only base class. Same registry id either way, so recipes/saves are unaffected.
    private static Item wirelessTerminal;

    /**
     * The wireless terminal item instance (created on first call, always the same object).
     *
     * <p>Called by the DeferredRegister lambda below and by {@code AE2WtLibHook}'s terminal
     * registration; both run during the ITEM registry event, where item construction is legal.
     */
    public static Item wirelessTerminal() {
        if (wirelessTerminal == null) {
            wirelessTerminal = createWirelessTerminal();
        }
        return wirelessTerminal;
    }

    public static final DeferredHolder<Item, Item> WIRELESS_THROUGHPUT_TERMINAL =
            ITEMS.register("wireless_throughput_terminal", ModItems::wirelessTerminal);

    /**
     * 无线终端物品的工厂：装了 AE2WTLib 就用它的通用终端子类（{@code ItemWT}），否则用不依赖它的基类。
     *
     * <p>⚠️ 软依赖不能硬 import：子类的全限定名只出现在 {@code AE2WtLibHook} 里，而那个类
     * 只有在 {@link AE2WtLibPlugin#isLoaded()} 为真时才会被 JVM 解析/加载。
     * 没装 AE2WTLib 的整合包里 {@code de.mari_023.*} 一次都不会被碰到。
     */
    private static Item createWirelessTerminal() {
        if (AE2WtLibPlugin.isLoaded()) {
            return AE2WtLibHook.createUniversalTerminalItem();
        }
        return new WirelessThroughputTerminalItem(new Item.Properties().stacksTo(1));
    }
}
