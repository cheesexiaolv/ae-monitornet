package com.cheesexiaolv.aetm;

import com.cheesexiaolv.aetm.Init.ModItems;

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
                        output.accept(ModItems.THROUGHPUT_MONITOR_CONFIGURATOR.get());
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

        // Server config: <world>/serverconfig/ae_monitornet-server.toml (hot-reloaded by NeoForge).
        modContainer.registerConfig(ModConfig.Type.SERVER, AetmConfig.SPEC);
    }
}
