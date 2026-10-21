package com.cheesexiaolv.aetm;

import com.cheesexiaolv.aetm.Init.ModItems;
import com.cheesexiaolv.aetm.menu.AdvancedThroughputMonitorMenu;
import com.cheesexiaolv.aetm.menu.ThroughputTerminalMenu;
import com.cheesexiaolv.aetm.menu.ThroughputTerminalWirelessMenu;

import net.minecraft.core.Registry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import appeng.api.features.GridLinkables;
import appeng.core.AppEng;
import appeng.items.tools.powered.WirelessTerminalItem;

/**
 * 公共（双端）注册。
 *
 * <p>AE2 的 {@code MenuTypeBuilder.build()} 会把菜单类型排入 AE2 自己的注册队列，
 * 但该队列只对 AE2 自己的菜单在正确时机 flush。第三方按 ae2wtlib 的做法，
 * 在 {@code RegisterEvent(MENU_REGISTRY)} 里用 Forge 注册表真注册一次。
 */
@Mod.EventBusSubscriber(modid = AeMonitorNet.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CommonEvents {

    @SubscribeEvent
    public static void onRegisterMenu(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registry.MENU_REGISTRY)) {
            ForgeRegistries.MENU_TYPES.register(
                    AppEng.makeId("advanced_throughput_monitor"),
                    AdvancedThroughputMonitorMenu.TYPE);
            ForgeRegistries.MENU_TYPES.register(
                    AppEng.makeId("throughput_terminal"),
                    ThroughputTerminalMenu.TYPE);
            ForgeRegistries.MENU_TYPES.register(
                    AppEng.makeId("wireless_throughput_terminal"),
                    ThroughputTerminalWirelessMenu.TYPE);
        }
    }

    /**
     * 无线终端要能被安全终端"链接"，必须登记进 AE2 的 {@code GridLinkables}
     * （AE2 自己在 {@code AppEngBase} 里对无线终端做同样的事）。
     *
     * <p>⚠️ 必须放在 {@code FMLCommonSetupEvent}：这里要读自己注册表里的物品
     * （{@code RegistryObject.get()}），在 mod 构造器里读会抛
     * "Registry Object not present"。
     *
     * <p><b>幂等</b>：装了 AE2WTLib 时 {@code AE2WtLibHook.init()} 也会兜一次底，
     * 而 {@code GridLinkables.register} 对同一个物品重复登记会
     * {@code Preconditions.checkState} 抛 {@code IllegalStateException}
     * （两次注册都在 {@code enqueueWork} 的延迟任务里，执行顺序不确定），
     * 所以这里也先查再登记。
     */
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            var item = ModItems.WIRELESS_THROUGHPUT_TERMINAL.get();
            if (GridLinkables.get(item) == null) {
                GridLinkables.register(item, WirelessTerminalItem.LINKABLE_HANDLER);
            }
        });
    }
}
