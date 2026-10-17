package com.cheesexiaolv.aetm.Parts;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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
 * {@link IMonitorRef} 的"aebackports 零件"实现 —— <b>整条桥都是软依赖</b>。
 *
 * <p>aebackports 的部件是 {@code com.cheesexiaolv.aebackports.Parts.ThroughputMonitorPart}
 * （另一个包里的另一个类，与 aetm 的同名类毫无继承关系）。本类<b>不 import 它的任何类型</b>：
 * 类名字符串只在运行时由 {@link MonitorRefs} 用 {@code Class.forName} 取一次；
 * 而真正调用的方法全部落在两边共用的 <b>AE2</b> 基类 {@link AbstractMonitorPart} 上
 * （{@code getDisplayed() / getAmount() / getBlockEntity() / getLevel()}），所以绝大部分读取是类型安全的。
 *
 * <p>只有两处必须反射（aebackports 侧没有公开这些 API）：
 * <ol>
 *   <li>读它的私有字段 {@code workRoutine}（枚举）拿 {@code ordinal()} 当档位号
 *       —— 它的枚举顺序 0=t 1=s 2=m 3=10m 与 aetm 的 {@code WorkRoutine} / {@code TerminalSettings} 完全一致；</li>
 *   <li>调它的 {@code onPartActivate(player, hand, pos)} 来切档位（供 aetm 的配置器使用）。</li>
 * </ol>
 *
 * <p>两处都在 try/catch 里：aebackports 以后改了实现，最坏情况是"这个零件读不到/切不动"，
 * <b>不会</b>让终端或配置器崩掉（读失败返回 0 / 跳过该零件）。
 */
final class AaeMonitorRef implements IMonitorRef {

    /**
     * 对面部件类的全限定名（只作为字符串出现，不 import）。
     *
     * <p>该 mod 在 v2.0.0 由 {@code aaemonitor} 改名为 {@code aebackports}，包名一并变成小写
     * {@code com.cheesexiaolv.aebackports}。这里新名在前、旧名兜底，两个 jar 都能用。
     */
    static final String[] AAE_PART_CLASSES = {
            "com.cheesexiaolv.aebackports.Parts.ThroughputMonitorPart",
            "com.cheesexiaolv.Aaemonitor.Parts.ThroughputMonitorPart",
    };

    /** 依次尝试两个类名，返回第一个能加载的（都失败返回 null）。 */
    @Nullable
    static Class<?> resolvePartClass() {
        for (String className : AAE_PART_CLASSES) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException | LinkageError ignored) {
                // 试下一个名字
            }
        }
        return null;
    }

    /** 该部件的档位字段名（private 枚举），以及读取它的句柄（首次用到时解析并缓存）。 */
    private static final String ROUTINE_FIELD = "workRoutine";
    private static volatile Field routineField;
    private static volatile boolean routineFieldResolved;

    /** {@code onPartActivate(Player, InteractionHand, Vec3)} 的句柄（同样懒解析）。 */
    private static volatile Method onPartActivate;
    private static volatile boolean onPartActivateResolved;

    private final AbstractMonitorPart part;

    /**
     * @param part aebackports 的部件实例（已在 {@link MonitorRefs} 里用反射到的 Class 做过 {@code isInstance} 校验）
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
        // float，不是 double；语义与 aetm 的 getLastReportedValue() 相同（"每档位量"）
        return reflectLastReportedValue(this.part);
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
        // aebackports 没有"高级监控器"，也就没有历史曲线 → 终端只显示数值，不给曲线
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
        Method method = resolveOnPartActivate();
        if (method == null) {
            return false;
        }
        try {
            // 它的 onPartActivate 里认它自己的配置器、然后 cycle 档位（与 aetm 的实现同构）
            Object result = method.invoke(this.part, player, hand, clickLocation);
            return result instanceof Boolean changed && changed;
        } catch (ReflectiveOperationException | RuntimeException e) {
            AELog.warn("aetm: failed to cycle aaemonitor throughput monitor routine", e);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 反射细节（全部懒解析 + 失败即降级）
    // ------------------------------------------------------------------

    private static int clampRoutine(int ordinal) {
        return ordinal >= 0 && ordinal < MonitorRefs.ROUTINE_TICKS.length ? ordinal : 1;
    }

    /** 读 {@code workRoutine} 私有枚举字段的 ordinal；失败按 1（=秒档）处理。 */
    static int readRoutineOrdinal(AbstractMonitorPart part) {
        Field field = resolveRoutineField();
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

    /** 读 {@code lastReportedValue} 字段（aebackports 是 protected float，没有公开 getter）。 */
    static double reflectLastReportedValue(AbstractMonitorPart part) {
        Field field = resolveField("lastReportedValue");
        if (field == null) {
            return 0;
        }
        try {
            Object value = field.get(part);
            return value instanceof Number number ? number.doubleValue() : 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    @Nullable
    private static Field resolveRoutineField() {
        if (!routineFieldResolved) {
            routineField = resolveField(ROUTINE_FIELD);
            routineFieldResolved = true;
        }
        return routineField;
    }

    @Nullable
    private static Method resolveOnPartActivate() {
        if (!onPartActivateResolved) {
            Class<?> partClass = resolvePartClass();
            if (partClass != null) {
                try {
                    onPartActivate = partClass.getMethod("onPartActivate", Player.class, InteractionHand.class,
                            Vec3.class);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    AELog.warn("aetm: aebackports/aaemonitor part is present but onPartActivate is unavailable", e);
                    onPartActivate = null;
                }
            }
            onPartActivateResolved = true;
        }
        return onPartActivate;
    }

    /**
     * 沿类层次找字段（对面的 {@code lastReportedValue} 是它自己声明的 protected，
     * {@code workRoutine} 是 private；两边都用 {@code getDeclaredField} + {@code setAccessible}）。
     */
    @Nullable
    private static Field resolveField(String name) {
        Class<?> partClass = resolvePartClass();
        if (partClass == null) {
            return null;
        }
        try {
            Field field = partClass.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
