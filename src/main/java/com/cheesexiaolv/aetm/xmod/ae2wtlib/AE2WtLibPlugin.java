package com.cheesexiaolv.aetm.xmod.ae2wtlib;

import com.cheesexiaolv.aetm.Aetm;

import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.RegisterEvent;

/**
 * AE2WTLib 软依赖入口。
 *
 * <p><b>这个类绝不能引用任何 AE2WTLib 的类型</b>：它会在没装 AE2WTLib 的整合包里被加载，
 * 一旦类里出现 {@code de.mari_023.*} 的字段/方法签名，缺类就会在类加载阶段直接崩。
 * 真正调用 AE2WTLib API 的代码全部放在 {@link AE2WtLibHook}，只有确认装了 mod 之后才会被加载
 * （与隔壁 aebackports 工程同一套隔离模式）。
 */
@Mod.EventBusSubscriber(modid = Aetm.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AE2WtLibPlugin {

    public static final String MOD_ID = "ae2wtlib";

    private AE2WtLibPlugin() {
    }

    /**
     * 挂"提前占热键 id"的监听器。
     *
     * <p>必须在 mod 构造函数里、自己的 {@code DeferredRegister} 之后调用一次
     * （见 {@link Aetm}）：Forge 的 mod 事件总线按监听器登记顺序派发，
     * 这样我们的物品注册表在事件里一定先于本监听器填好。
     *
     * <p>时机与被占的东西：AE2 在客户端 Minecraft 构造期就会关闭热键注册窗口，
     * 而物品注册事件仍在窗口打开期内（AE2WTLib 自己也是在这个时候登记它的终端的）。
     */
    public static void registerEarlyHook(IEventBus modEventBus) {
        if (ModList.get().isLoaded(MOD_ID)) {
            modEventBus.addListener(AE2WtLibPlugin::onRegisterItems);
        }
    }

    private static void onRegisterItems(RegisterEvent event) {
        // 1.20.1：Registry.ITEM_REGISTRY 已随 1.19.3 的注册表重构移除 → Registries.ITEM
        if (event.getRegistryKey().equals(Registries.ITEM)) {
            AE2WtLibHook.preRegisterHotkey();
        }
    }

    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded(MOD_ID)) {
            event.enqueueWork(AE2WtLibHook::init);
        }
    }
}
