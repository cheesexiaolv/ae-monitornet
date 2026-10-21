package com.cheesexiaolv.aetm.Parts;

import javax.annotation.Nullable;

import net.minecraftforge.fml.ModList;

import appeng.parts.reporting.AbstractMonitorPart;

/**
 * <b>本模组 ↔ aebackports 的兼容桥入口</b>：把"任意一个吞吐量监控器零件"包成 {@link IMonitorRef}。
 *
 * <p>设计要点（用户硬性要求：两个 mod 同时装、同一存档里两边的监控器都要能用终端看到）：
 * <ul>
 *   <li><b>软依赖</b>：只在 {@link ModList#isLoaded} 为真时才去 {@code Class.forName} aebackports 的部件类；
 *       类名只是字符串，本包不出现它的任何类型 → 没装 aebackports 时零成本、也不会加载失败。</li>
 *   <li><b>认零件只认名字</b>：用反射拿到的 {@code Class} 做归属判断，再 cast 到两边共用的 AE2 基类
 *       {@link AbstractMonitorPart}（这样后续读取都是类型安全的）。</li>
 *   <li><b>失败即降级</b>：类找不到 / 字段与方法都解析失败时，对应的零件会被跳过（{@link #of} 返回 null），
 *       终端照常工作，绝不会因为对方改了实现而崩。</li>
 * </ul>
 *
 * <p>每个零件的包装对象按部件实例缓存（{@code WeakHashMap}），避免终端每 tick 重建对象。
 */
public final class MonitorRefs {

    /**
     * 对面的 modid。
     *
     * <p>它在 v2.0.0 由 {@code aaemonitor} 改名为 {@code aebackports}，包名也从
     * {@code com.cheesexiaolv.aebackports} 变成 {@code com.cheesexiaolv.aebackports}；
     * 为了同时兼容新旧 jar，类名与 modid 都是"新名在前、旧名兜底"。
     */
    public static final String[] PROVIDER_MODIDS = { "aebackports", "aaemonitor" };

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

    /** aebackports 部件类（惰性解析；null = 没装 / 解析失败）。 */
    @Nullable
    private static Class<?> aaePartClass;
    private static boolean aaePartClassResolved;

    /** 每个零件实例 → 包装对象（键用弱引用，零件卸载后能回收）。 */
    private static final java.util.Map<Object, IMonitorRef> CACHE = new java.util.WeakHashMap<>();

    private MonitorRefs() {
    }

    /**
     * 把零件包成 {@link IMonitorRef}。
     *
     * @param part 一个零件实例（本模组 的、或 aebackports 的；其它类型返回 null）
     * @return 包装对象；不认识该类型、或 aebackports 侧不可用时返回 {@code null}
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
        // aebackports 的零件：只有装了它、且类能解析出来才认
        Class<?> aaeClass = aaePartClass();
        if (aaeClass != null && aaeClass.isInstance(part) && part instanceof AbstractMonitorPart monitorPart) {
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

    /** 该零件是否属于 aebackports（供菜单枚举时判断，不做包装）。 */
    public static boolean isAaeMonitor(Object part) {
        Class<?> aaeClass = aaePartClass();
        return aaeClass != null && aaeClass.isInstance(part);
    }

    /** 该 {@code machineClass} 是不是（本模组 或 aebackports 的）吞吐量监控器。 */
    public static boolean isMonitorClass(Class<?> machineClass) {
        if (ThroughputMonitorPart.class.isAssignableFrom(machineClass)) {
            return true;
        }
        Class<?> aaeClass = aaePartClass();
        return aaeClass != null && aaeClass.isAssignableFrom(machineClass);
    }

    /**
     * aebackports 的部件类（惰性解析：没装 mod 就永不解析；解析失败也只在第一次记一条日志）。
     *
     * @return null = 不可用
     */
    @Nullable
    private static Class<?> aaePartClass() {
        if (!aaePartClassResolved) {
            aaePartClassResolved = true;
            if (isProviderLoaded()) {
                aaePartClass = AaeMonitorRef.resolvePartClass();
                if (aaePartClass == null) {
                    appeng.core.AELog.warn("AE MonitorNet: the throughput-monitor addon (aebackports / old aaemonitor) "
                            + "is loaded but its throughput monitor part could not be resolved; "
                            + "its monitors will not show up in the terminal");
                }
            }
        }
        return aaePartClass;
    }
}
