package com.cheesexiaolv.aetm.Init;

import com.cheesexiaolv.aetm.AeMonitorNet;
import com.cheesexiaolv.aetm.Items.ThroughputMonitorConfigurator;
import com.cheesexiaolv.aetm.Items.WirelessThroughputTerminalItem;
import com.cheesexiaolv.aetm.Parts.AdvancedThroughputMonitorPart;
import com.cheesexiaolv.aetm.Parts.ThroughputMonitorPart;
import com.cheesexiaolv.aetm.Parts.ThroughputTerminalPart;
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
    //    use AE2's wireless-terminal mechanics. Registered with the AE2-only base class for now; the
    //    AE2WTLib universal-terminal subclass (1.20.1 `xmod/ae2wtlib`) is part of the later WUT stage.
    //    Its GridLinkable handler is registered by CommonEvents (net-menus).
    public static final DeferredHolder<Item, Item> WIRELESS_THROUGHPUT_TERMINAL =
            ITEMS.register("wireless_throughput_terminal",
                    () -> new WirelessThroughputTerminalItem(new Item.Properties().stacksTo(1)));
}
