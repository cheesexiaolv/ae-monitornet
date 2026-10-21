package com.cheesexiaolv.aetm.Parts;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import appeng.api.stacks.AEKey;
import appeng.core.AELog;
import appeng.parts.reporting.AbstractMonitorPart;

/**
 * {@link IMonitorRef} 的"AdvancedAE 零件"实现 —— <b>整条桥都是软依赖</b>。
 *
 * <p>目标的部件是
 * {@code net.pedroksl.advanced_ae.common.parts.ThroughputMonitorPart}
 * （{@code extends appeng.parts.reporting.AbstractMonitorPart implements IGridTickable}，
 * 见 {@code reference/AdvancedAE-1.20.1-1.3.4/.../common/parts/ThroughputMonitorPart.java}）。
 * 本类<b>不 import 它的任何类型</b>：类名字符串只在运行时由 {@link MonitorRefs} 用
 * {@code Class.forName} 取；真正调用的方法全部落在两边共用的 <b>AE2</b> 基类
 * {@link AbstractMonitorPart} 上（{@code getDisplayed() / getAmount() / getBlockEntity() / getLevel()}），
 * 所以绝大部分读取是类型安全的。
 *
 * <p>只有三处必须反射（AAE 侧没有公开这些 API）：
 * <ol>
 *   <li>读它自己声明的 {@code protected double lastReportedValue}
 *       （AAE 源码第 61 行；1.19.2 时代的 aebackports 是 {@code protected float}，这里按
 *       {@link Number} 读，两种都能用）；</li>
 *   <li>读它的 {@code private WorkRoutine workRoutine} 枚举字段（源码第 63 行）拿 {@code ordinal()}
 *       当档位号 —— 它的枚举顺序 0=t 1=s 2=m 3=10m 与 aetm 的 {@code WorkRoutine} /
 *       {@code TerminalSettings} 完全一致；</li>
 *   <li>调它的 {@code onPartActivate(player, hand, pos)} 来切档位（供 aetm 的配置器使用）。</li>
 * </ol>
 *
 * <p>三处都在 try/catch 里：AAE 以后改了实现，最坏情况是"这个零件读不到/切不动"，
 * <b>不会</b>让终端或配置器崩掉（读失败返回 0 / 跳过该零件）。
 */
final class AaeMonitorRef implements IMonitorRef {

    /**
     * 对面部件类的全限定名（只作为字符串出现，不 import）。
     *
     * <p>第一项是 1.20.1 分支的目标（AdvancedAE）；后两项是 1.19.2 时代的旧 id，保留兜底。
     */
    static final String[] AAE_PART_CLASSES = {
            "net.pedroksl.advanced_ae.common.parts.ThroughputMonitorPart",
            "com.cheesexiaolv.aebackports.Parts.ThroughputMonitorPart",
            "com.cheesexiaolv.Aaemonitor.Parts.ThroughputMonitorPart",
    };

    /** 依次尝试所有类名，返回全部能加载的（都失败返回空列表）。 */
    static List<Class<?>> resolvePartClasses() {
        List<Class<?>> resolved = new ArrayList<>(AAE_PART_CLASSES.length);
        for (String className : AAE_PART_CLASSES) {
            try {
                resolved.add(Class.forName(className));
            } catch (ClassNotFoundException | LinkageError ignored) {
                // 试下一个名字
            }
        }
        return List.copyOf(resolved);
    }

    /** 读档位字段名（private 枚举）。 */
    private static final String ROUTINE_FIELD = "workRoutine";
    /** 读速率的字段名（protected，AAE 是 double / 旧版是 float）。 */
    private static final String LAST_VALUE_FIELD = "lastReportedValue";

    /**
     * 字段缓存：<b>按实际零件类</b>解析，而不是按"第一个能加载的类"。
     *
     * <p>为什么必须按实际类：如果整合包里同时有 AAE 和旧 id 的 jar，两边的字段声明不同
     * （double vs float、甚至字段名都可能不同），用错类的 {@code Field} 去 {@code get()} 会抛
     * {@code IllegalArgumentException}。{@code computeIfAbsent} 天然幂等，Map 里放 null 用一个
     * 哨兵对象占位（{@code ConcurrentHashMap} 不允许 null value）。
     *
     * <p>两个字段各用一张表（key 只有 Class，混用会串），见 {@link #resolveField}。
     */
    private static final Map<Class<?>, Object> ROUTINE_FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Object> LAST_VALUE_FIELDS = new ConcurrentHashMap<>();
    private static final Object MISSING = new Object();

    private final AbstractMonitorPart part;

    /**
     * @param part 对面的部件实例（已在 {@link MonitorRefs} 里用反射到的 Class 做过 {@code isInstance} 校验）
     */
    AaeMonitorRef(AbstractMonitorPart part) {
        this.part = part;
    }

    @Override
    @Nullable
    public AEKey displayed() {
        // AE2 基类的公开方法 —— 类型安全，不需要反射
        return this.part.getDisplayed();
    }

    @Override
    public long amount() {
        return this.part.getAmount();
    }

    @Override
    public double lastReportedValue() {
        // AAE 是 double（1.19.2 的 aebackports 是 float）；语义与 aetm 的 getLastReportedValue() 相同
        Field field = resolveField(this.part.getClass(), LAST_VALUE_FIELD);
        if (field == null) {
            return 0;
        }
        try {
            Object value = field.get(this.part);
            return value instanceof Number number ? number.doubleValue() : 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    @Override
    public int routineTicks() {
        return MonitorRefs.ROUTINE_TICKS[clampRoutine(routineOrdinal())];
    }

    @Override
    public int routineOrdinal() {
        return readRoutineOrdinal(this.part);
    }

    @Override
    public BlockPos pos() {
        return this.part.getBlockEntity().getBlockPos();
    }

    @Override
    @Nullable
    public ResourceLocation dimension() {
        var level = this.part.getLevel();
        return level == null ? null : level.dimension().location();
    }

    @Override
    public boolean advanced() {
        // AAE 侧没有"高级监控器"这个概念，也就没有历史曲线 → 终端只显示数值，不给曲线
        return false;
    }

    @Override
    @Nullable
    public Object historySource() {
        // 没有历史曲线源（advanced() 恒为 false，终端也就不会来问）
        return null;
    }

    @Override
    public boolean cycleRoutineByConfigurator(Player player, InteractionHand hand, Vec3 clickLocation) {
        Method method = resolveOnPartActivate(this.part.getClass());
        if (method == null) {
            return false;
        }
        try {
            // 它的 onPartActivate 里认它自己的配置器、然后 cycle 档位（与 aetm 的实现同构）
            Object result = method.invoke(this.part, player, hand, clickLocation);
            return result instanceof Boolean changed && changed;
        } catch (ReflectiveOperationException | RuntimeException e) {
            AELog.warn("aetm: failed to cycle the AdvancedAE throughput monitor routine", e);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 反射细节（全部按实际类懒解析 + 失败即降级）
    // ------------------------------------------------------------------

    private static int clampRoutine(int ordinal) {
        return ordinal >= 0 && ordinal < MonitorRefs.ROUTINE_TICKS.length ? ordinal : 1;
    }

    /** 读 {@code workRoutine} 私有枚举字段的 ordinal；失败按 1（=秒档）处理。 */
    static int readRoutineOrdinal(AbstractMonitorPart part) {
        Field field = resolveField(part.getClass(), ROUTINE_FIELD);
        if (field == null) {
            return 1;
        }
        try {
            Object routine = field.get(part);
            return routine instanceof Enum<?> e ? e.ordinal() : 1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 1;
        }
    }

    @Nullable
    private static Method resolveOnPartActivate(Class<?> partClass) {
        try {
            // public 方法（基类 AbstractMonitorPart 声明），getMethod 会沿父类链找到
            return partClass.getMethod("onPartActivate", Player.class, InteractionHand.class, Vec3.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            AELog.warn("aetm: the AdvancedAE part is present but onPartActivate is unavailable", e);
            return null;
        }
    }

    /**
     * 沿类层次找字段（对面的 {@code lastReportedValue} 是 protected、{@code workRoutine} 是 private，
     * 两边都用 {@code getDeclaredField} + {@code setAccessible}）。
     */
    @Nullable
    private static Field resolveField(Class<?> partClass, String name) {
        Map<Class<?>, Object> cache = ROUTINE_FIELD.equals(name) ? ROUTINE_FIELDS : LAST_VALUE_FIELDS;
        Object cached = cache.computeIfAbsent(partClass, cls -> {
            for (Class<?> current = cls; current != null && current != Object.class;
                    current = current.getSuperclass()) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                    // 继续往父类找
                } catch (RuntimeException e) {
                    return MISSING;
                }
            }
            return MISSING;
        });
        return cached == MISSING ? null : (Field) cached;
    }
}
