package com.cheesexiaolv.aetm;

import com.cheesexiaolv.aetm.Init.ModItems;
import com.cheesexiaolv.aetm.network.AetmNetwork;
import com.cheesexiaolv.aetm.xmod.ae2wtlib.AE2WtLibPlugin;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.jetbrains.annotations.NotNull;

@Mod(Aetm.MODID)
public class Aetm {
    public static final String MODID = "aetm";

    public static final CreativeModeTab TAB = new CreativeModeTab(Aetm.MODID) {
        @Override
        public @NotNull ItemStack makeIcon() {
            // 修复：RegistryObject 需要先 .get() 才能拿到 Item 对象
            return new ItemStack(ModItems.THROUGHPUT_MONITOR.get());
        }
    };

    public Aetm() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modEventBus);

        // AE2WTLib 的终端要在"AE2 关闭热键注册窗口之前"把热键 id 占好；
        // 必须排在自己的 DeferredRegister 之后（mod 事件总线按监听器登记顺序派发）。
        AE2WtLibPlugin.registerEarlyHook(modEventBus);

        // 自建网络通道（AE2 的包类型是闭合枚举，聚合终端的行数据只能自己发）
        AetmNetwork.init();

        // 配置文件：服务端类型 → <存档>/serverconfig/aetm-server.toml
        // Forge 会监听该文件，改完即时生效（热加载），无需重启
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, AetmConfig.SPEC);
    }
}