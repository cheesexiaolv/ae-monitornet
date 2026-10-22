package com.cheesexiaolv.aetm;

import com.cheesexiaolv.aetm.Init.ModItems;
import com.cheesexiaolv.aetm.menu.AdvancedThroughputMonitorMenu;
import com.cheesexiaolv.aetm.menu.ThroughputTerminalMenu;
import com.cheesexiaolv.aetm.menu.ThroughputTerminalWirelessMenu;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import appeng.api.features.GridLinkables;
import appeng.core.AppEng;
import appeng.items.tools.powered.WirelessTerminalItem;

/**
 * 公共（双端）注册。
 *
 * <p>AE2 的 {@code MenuTypeBuilder.build()} 会把菜单类型排进 AE2 自己的注册队列
 * （{@code appeng.init.InitMenuTypes#queueRegistration}），而那个队列只在 AE2 的
 * {@code RegisterEvent} 处理 <b>MENU</b> 注册表时被冲刷一次
 * （{@code reference/AE2-1.21.1-19.2.17/appeng/core/AppEngBase.java:113-114}）。
 * 我们的菜单类多半是在<b>之后</b>（MENU 注册表事件里）才被类加载的，所以队列不会被 AE2 冲刷，
 * 必须自己往 MENU 注册表里注册一次。
 *
 * <p>⚠️ 顺序敏感：万一我们的菜单类<b>先</b>被加载（例如某个部件类在构造期就引用了它），
 * AE2 会在 MENU 阶段把队列冲刷掉，这里再注册就会 "Adding duplicate key"。
 * 所以下面先 {@code containsKey} 再注册 —— 只在 AE2 没管过的时候补一刀。
 *
 * <p><b>1.20.1（Forge 47）→ 1.21.1（NeoForge 21.1）</b>：
 * <ul>
 *   <li>{@code @Mod.EventBusSubscriber(modid=..., bus = Mod.EventBusSubscriber.Bus.MOD)}
 *       → {@link EventBusSubscriber}{@code (modid = ..., bus = Bus.MOD)}
 *       （注解在 {@code net.neoforged.fml.common}；证据：{@code appeng/datagen/AE2DataGenerators.java:47-48,54-57}）；</li>
 *   <li>{@code net.minecraftforge.registries.RegisterEvent} → {@link RegisterEvent}
 *       （{@code net.neoforged.neoforge.registries}），事件 API 改为
 *       {@code event.getRegistryKey()} + {@code event.getRegistry(ResourceKey)}
 *       （{@code RegisterEvent.java:69,87}）；</li>
 *   <li>{@code ForgeRegistries.MENU_TYPES.containsKey/register} → {@code BuiltInRegistries.MENU}
 *       的同名方法；注册动作走 {@code Registry.register(registry, id, value)}（与 AE2 的
 *       {@code InitMenuTypes#registerAll} 同款）。</li>
 * </ul>
 */
@EventBusSubscriber(modid = AeMonitorNet.MODID, bus = Bus.MOD)
public class CommonEvents {

    @SubscribeEvent
    public static void onRegisterMenu(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.MENU)) {
            return;
        }
        // 用 event.getRegistry()（而不是 getRegistry(Registries.MENU)）：后者的实现是
        // `key == this.registryKey` 的**引用**比较（RegisterEvent.java:88），换一个等值的
        // ResourceKey 实例就会返回 null；这里已经确认过 registryKey，直接取当前注册表更稳。
        @SuppressWarnings("unchecked")
        Registry<MenuType<?>> registry = (Registry<MenuType<?>>) event.getRegistry();
        registerMenu(registry, AppEng.makeId("advanced_throughput_monitor"), AdvancedThroughputMonitorMenu.TYPE);
        registerMenu(registry, AppEng.makeId("throughput_terminal"), ThroughputTerminalMenu.TYPE);
        registerMenu(registry, AppEng.makeId("wireless_throughput_terminal"), ThroughputTerminalWirelessMenu.TYPE);
    }

    private static void registerMenu(Registry<MenuType<?>> registry, ResourceLocation id, MenuType<?> type) {
        if (registry.containsKey(id)) {
            // AE2 的 InitMenuTypes 已经替我们注册过了（见类注释）
            return;
        }
        Registry.register(registry, id, type);
    }

    /**
     * 无线终端要能被安全终端"链接"，必须登记进 AE2 的 {@code GridLinkables}
     * （AE2 自己在 {@code AppEngBase} 里对无线终端做同样的事）。
     *
     * <p>⚠️ 必须放在 {@code FMLCommonSetupEvent}：这里要读自己注册表里的物品
     * （{@code DeferredHolder.get()}），在 mod 构造器里读会抛
     * "Registry Object not present"。
     *
     * <p><b>幂等</b>：{@code GridLinkables.register} 对同一个物品重复登记会
     * {@code Preconditions.checkState} 抛 {@code IllegalStateException}，所以这里先查再登记。
     *
     * <p>1.21.1 核对：{@code FMLCommonSetupEvent} + {@code event.enqueueWork(...)} 仍在
     * （FML 4.0.39 {@code ParallelDispatchEvent#enqueueWork}）；{@code GridLinkables}
     * 仍在 {@code appeng/api/features}（{@code GridLinkables.java:17,31}）；
     * {@code WirelessTerminalItem.LINKABLE_HANDLER} 仍在
     * （{@code appeng/items/tools/powered/WirelessTerminalItem.java:53}）。
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
