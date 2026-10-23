package com.cheesexiaolv.monitornet.Parts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraftforge.fml.ModList;

import appeng.parts.reporting.AbstractMonitorPart;

/**
 * <b>本模组 ↔ AdvancedAE（AAE）的兼容桥入口</b>：把"别人家的吞吐量监控器零件"包成 {@link IMonitorRef}。
 *
 * <p>设计要点（两个 mod 同时装、同一存档里两边的监控器都要能在终端里看到）：
 * <ul>
 *   <li><b>软依赖</b>：只在 {@link ModList#isLoaded} 为真时才去 {@code Class.forName} AAE 的部件类；
 *       类名只是字符串，本包不出现 {@code net.pedroksl.*} 的任何类型 → 没装 AAE 时零成本、
 *       也不会因为 AAE 缺席而加载失败。</li>
 *   <li><b>认零件只认名字</b>：用反射拿到的 {@code Class} 做归属判断，再 cast 到两边共用的 AE2 基类
 *       {@link AbstractMonitorPart}（这样后续读取 {@code getDisplayed()/getAmount()} 都是类型安全的）。</li>
 *   <li><b>失败即降级</b>：类找不到 / 字段与方法都解析失败时，对应的零件会被跳过（{@link #of} 返回 null），
 *       终端照常工作，绝不会因为对方改了实现而崩。</li>
 * </ul>
 *
 * <p>每个零件的包装对象按部件实例缓存（{@code WeakHashMap}），避免终端每 tick 重建对象。
 */
public final class MonitorRefs {

    /**
     * 提供"吞吐量监控器"生态位的 modid（按优先级）。
     *
     * <p><b>1.20.1 分支目标是 AdvancedAE（{@code advanced_ae}）</b>；
     * {@code aebackports} / {@code aaemonitor} 是 1.19.2 时代的自家附属 id，保留识别不花任何代价，
     * 整合包把老 jar 带进来时行为依然正确（我们的基础监控器继续让位）。
     */
    public static final String[] PROVIDER_MODIDS = { "advanced_ae", "aebackports", "aaemonitor" };

    /** 对面是否在场（任一 id 命中即为真）。 */
    public static boolean isProviderLoaded() {
        for (String modid : PROVIDER_MODIDS) {
            if (ModList.get().isLoaded(modid)) {
                return true;
            }
        }
        return false;
    }

    /** 档位 → tick 数（与 本模组 自己的 {@code WorkRoutine} / {@code ThroughputTerminalPart.ROUTINE_TICKS} 一致）。 */
    static final int[] ROUTINE_TICKS = { 1, 20, 1200, 12000 };

    /** 对面部件类（惰性解析；空列表 = 没装 / 全部解析失败）。 */
    @Nullable
    private static List<Class<?>> providerPartClasses;
    private static boolean providerPartClassesResolved;

    /** 每个零件实例 → 包装对象（键用弱引用，零件卸载后能回收）。 */
    private static final java.util.Map<Object, IMonitorRef> CACHE = new java.util.WeakHashMap<>();

    private MonitorRefs() {
    }

    /**
     * 把零件包成 {@link IMonitorRef}。
     *
     * @param part 一个零件实例（本模组 的、或 AdvancedAE 的；其它类型返回 null）
     * @return 包装对象；不认识该类型、或 AAE 侧不可用时返回 {@code null}
     */
    @Nullable
    public static IMonitorRef of(Object part) {
        if (part == null) {
            return null;
        }
        // 自家零件：类型安全，走直接实现
        if (part instanceof ThroughputMonitorPart own) {
            return new PartMonitorRef(own);
        }
        // 对面的零件：只有装了它、且类能解析出来才认
        if (isProviderPart(part) && part instanceof AbstractMonitorPart monitorPart) {
            synchronized (CACHE) {
                IMonitorRef cached = CACHE.get(part);
                if (cached != null) {
                    return cached;
                }
                IMonitorRef created = new AaeMonitorRef(monitorPart);
                CACHE.put(part, created);
                return created;
            }
        }
        return null;
    }

    /** 该零件是否属于对面的 mod（供菜单枚举时判断，不做包装）。 */
    public static boolean isAaeMonitor(Object part) {
        return isProviderPart(part);
    }

    /** 该 {@code machineClass} 是不是（本模组 或对面的）吞吐量监控器。 */
    public static boolean isMonitorClass(Class<?> machineClass) {
        if (ThroughputMonitorPart.class.isAssignableFrom(machineClass)) {
            return true;
        }
        for (Class<?> providerClass : providerPartClasses()) {
            if (providerClass.isAssignableFrom(machineClass)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProviderPart(Object part) {
        for (Class<?> providerClass : providerPartClasses()) {
            if (providerClass.isInstance(part)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 对面的部件类（惰性解析：没装 mod 就永不解析；解析失败也只在第一次记一条日志）。
     *
     * @return 只读列表，可能为空
     */
    private static List<Class<?>> providerPartClasses() {
        if (!providerPartClassesResolved) {
            providerPartClassesResolved = true;
            if (isProviderLoaded()) {
                providerPartClasses = AaeMonitorRef.resolvePartClasses();
                if (providerPartClasses.isEmpty()) {
                    appeng.core.AELog.warn("AE MonitorNet: an addon providing a throughput monitor ({}) is loaded but its "
                            + "throughput monitor part could not be resolved; its monitors will not show up in the "
                            + "terminal", String.join(" / ", PROVIDER_MODIDS));
                }
            } else {
                providerPartClasses = List.of();
            }
        }
        return providerPartClasses == null ? Collections.emptyList() : providerPartClasses;
    }

    /** 供调试：所有已解析的对面部件类。 */
    static List<Class<?>> resolvedProviderClasses() {
        return new ArrayList<>(providerPartClasses());
    }
}
