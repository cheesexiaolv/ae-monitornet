package com.cheesexiaolv.aetm;

import com.cheesexiaolv.aetm.Init.ModItems;
import com.cheesexiaolv.aetm.menu.AdvancedThroughputMonitorMenu;
import com.cheesexiaolv.aetm.menu.ThroughputTerminalMenu;
import com.cheesexiaolv.aetm.menu.ThroughputTerminalWirelessMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
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
 * <p>AE2 的 {@code MenuTypeBuilder.build()} 会把菜单类型排进 AE2 自己的注册队列
 * （{@code appeng.init.InitMenuTypes#queueRegistration}），而那个队列只在 AE2 的
 * {@code RegisterEvent} 处理 <b>BLOCK</b> 注册表时被冲刷一次（{@code AppEngBase.java:149}）。
 * 我们的菜单类多半是在<b>之后</b>（MENU 注册表事件里）才被类加载的，所以队列不会被 AE2 冲刷，
 * 必须自己往 Forge 的 MENU 注册表里注册一次。
 *
 * <p>⚠️ 顺序敏感：万一我们的菜单类<b>先</b>被加载（例如某个部件类在构造期就引用了它），
 * AE2 会在 BLOCK 阶段把队列冲刷掉，这里再注册就会 "Adding duplicate key"。
 * 所以下面先 {@code containsKey} 再注册 —— 只在 AE2 没管过的时候补一刀。
 */
@Mod.EventBusSubscriber(modid = Aetm.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CommonEvents {

    @SubscribeEvent
    public static void onRegisterMenu(RegisterEvent event) {
        // 1.20.1: 原版注册表键是 net.minecraft.core.registries.Registries.MENU
        // （1.19.2 里用的 Registry.MENU_REGISTRY 已不存在）
        if (event.getRegistryKey().equals(Registries.MENU)) {
            registerMenu(AppEng.makeId("advanced_throughput_monitor"), AdvancedThroughputMonitorMenu.TYPE);
            registerMenu(AppEng.makeId("throughput_terminal"), ThroughputTerminalMenu.TYPE);
            registerMenu(AppEng.makeId("wireless_throughput_terminal"), ThroughputTerminalWirelessMenu.TYPE);
        }
    }

    private static void registerMenu(ResourceLocation id, MenuType<?> type) {
        if (ForgeRegistries.MENU_TYPES.containsKey(id)) {
            // AE2 的 InitMenuTypes 已经替我们注册过了（见类注释）
            return;
        }
        ForgeRegistries.MENU_TYPES.register(id, type);
    }

    /**
     * 无线终端要能被安全终端"链接"，必须登记进 AE2 的 {@code GridLinkables}
     * （AE2 自己在 {@code AppEngBase} 里对无线终端做同样的事）。
     *
     * <p>⚠️ 必须放在 {@code FMLCommonSetupEvent}：这里要读自己注册表里的物品
     * （{@code RegistryObject.get()}），在 mod 构造器里读会抛
     * "Registry Object not present"。
     *
     * <p><b>幂等</b>：{@code GridLinkables.register} 对同一个物品重复登记会
     * {@code Preconditions.checkState} 抛 {@code IllegalStateException}，
     * 所以这里先查再登记。（1.19.2 里 AE2WTLib 的 {@code AE2WtLibHook.init()} 会兜一次底；
     * 1.20.1 第一阶段该集成已删，这段保证将来补回 WUT 时仍然安全。）
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
