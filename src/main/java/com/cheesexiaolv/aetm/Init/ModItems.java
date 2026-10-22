package com.cheesexiaolv.aetm.Init;

import com.cheesexiaolv.aetm.AeMonitorNet;
import com.cheesexiaolv.aetm.Items.ThroughputMonitorConfigurator;
import com.cheesexiaolv.aetm.Parts.ThroughputMonitorPart;
import appeng.items.parts.PartItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item registration (1.21.1 stage 1).
 *
 * <p><b>Ported in stage 1</b>: the throughput monitor part + its configurator.
 * <b>Still missing</b> (see {@code docs/port-1.21.1.md}): the advanced monitor, the aggregated
 * terminal and the wireless terminal. Their ids are intentionally NOT registered yet, because a
 * half-registered item under the same id as the old version could corrupt existing worlds.
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
}
