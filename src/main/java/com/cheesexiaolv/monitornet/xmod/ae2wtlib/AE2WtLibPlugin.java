package com.cheesexiaolv.monitornet.xmod.ae2wtlib;

import com.cheesexiaolv.monitornet.AeMonitorNet;

import appeng.core.AELog;

import net.neoforged.fml.ModList;

/**
 * AE2WTLib（无线通用终端 / WUT）软依赖入口。
 *
 * <p><b>这个类绝不能引用任何 {@code de.mari_023.ae2wtlib.*} 的类型</b>：它会在<b>没装</b>
 * AE2WTLib 的整合包里被加载（{@code Init/ModItems} 用它决定注册哪个物品类），一旦类里有
 * AE2WTLib 的字段/方法签名，缺类就会在类加载阶段直接崩。真正调用 AE2WTLib API 的代码全部放在
 * {@link AE2WtLibHook}，只有确认装了 mod 之后才会被加载。
 *
 * <p>这与 1.20.1 分支的 {@code xmod/ae2wtlib/AE2WtLibPlugin} 是同一套隔离模式，差别只有：
 * <ol>
 *   <li>1.20.1 那个 {@code @Mod.EventBusSubscriber} 静态注册没了 —— 1.21.1 的
 *       AE2WTLib 19.5.1 把终端注册改成了 <b>由它自己派发的一次性事件</b>
 *       （{@code AddTerminalEvent}，见 {@link AE2WtLibHook}），必须在<b>mod 构造器里</b>
 *       就把"我要登记终端"的回调挂上去，所以这里暴露的是
 *       {@link #registerTerminals()} 而不是事件监听器；</li>
 *   <li>热键不再需要"提前占 id"（1.20.1 的 {@code preRegisterHotkey()}）：
 *       我们的终端现在和 AE2WTLib 自己的三个终端在<b>同一个时刻</b>登记（物品注册事件），
 *       远早于 AE2 关闭热键注册窗口的客户端 {@code Minecraft.<init>}。</li>
 * </ol>
 */
public final class AE2WtLibPlugin {

    /** AE2WTLib 主 mod 的 modid（API 在嵌套的 {@code ae2wtlib_api} 里，随主 jar 一起分发）。 */
    public static final String MOD_ID = "ae2wtlib";

    /** 三态缓存：{@code null} = 还没问过（不要在静态初始化里读 {@code ModList}）。 */
    private static Boolean loaded;

    private AE2WtLibPlugin() {
    }

    /**
     * 是否装了 AE2WTLib。
     *
     * <p>惰性读取 + 缓存：{@code ModList} 在 mod 构造期已可用（1.21.1/NeoForge 21.1 实测），
     * 但静态初始化早于它的场景（单测/工具链）会返回 {@code null}，这里统一按"没装"处理。
     */
    public static boolean isLoaded() {
        if (loaded == null) {
            var modList = ModList.get();
            loaded = modList != null && modList.isLoaded(MOD_ID);
        }
        return loaded;
    }

    /**
     * 把我们的无线存储监控终端登记成通用终端（WUT）里的一个可切换终端。
     *
     * <p>由 {@link AeMonitorNet} 在 mod 构造器里调用一次（自己的 {@code DeferredRegister}
     * 之后；这里只挂回调，不碰任何注册表，也不实例化物品 —— 物品由
     * {@code Init/ModItems#wirelessTerminal()} 在物品注册事件里惰性创建）。
     *
     * <p>为什么必须在构造器里：AE2WTLib 19.5.1 的终端注册是一次性的 ——
     * {@code de.mari_023.ae2wtlib.api.registration.AddTerminalEvent#register(Consumer)}
     * 收集回调，随后由 <b>嵌套 API mod（{@code ae2wtlib_api}）</b> 自己的物品注册监听器
     * {@code AE2wtlibAPIEntrypoint} 调用 {@code AddTerminalEvent#run()} 一次性执行；
     * 之后再 {@code register(...)} 会抛
     * {@code IllegalStateException: Cannot register terminal registration handler after terminal
     * registration already happened}。在构造器里登记则<b>与两个 mod 的监听器派发顺序无关</b>。
     */
    public static void registerTerminals() {
        if (!isLoaded()) {
            // 没装 AE2WTLib：物品是只依赖 AE2 的基类，四个 ae2wtlib:* 配方由
            // neoforge:mod_loaded 条件挡掉，这里什么都不用做（也不加载 xmod 的类）。
            return;
        }
        try {
            AE2WtLibHook.registerTerminals();
        } catch (Throwable t) {
            // 软依赖失败必须降级而不是崩启动：物品本身照旧可用（自己的无线终端功能不受影响），
            // 只是通用终端里切不到、ae2wtlib:upgrade/combine 四条配方解析失败。
            AELog.warn("AE MonitorNet: registering the wireless throughput terminal into AE2WTLib failed (%s: %s); "
                            + "it will not be selectable in the universal terminal",
                    t.getClass().getSimpleName(), t.getMessage());
        }
    }
}
