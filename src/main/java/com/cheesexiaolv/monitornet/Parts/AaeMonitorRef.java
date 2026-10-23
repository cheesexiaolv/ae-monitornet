package com.cheesexiaolv.monitornet.Parts;

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
 * {@link IMonitorRef} for AdvancedAE's part — the whole bridge is a <b>soft dependency</b>.
 *
 * <p>Target class: {@code net.pedroksl.advanced_ae.common.parts.ThroughputMonitorPart} (extends
 * AE2's {@link AbstractMonitorPart}). This class <b>does not import</b> any of its types: the class
 * name is only a runtime {@code Class.forName} string; everything actually called lands on AE2's
 * shared base class (type-safe).
 *
 * <p>Only three things need reflection (AdvancedAE exposes no API): its
 * {@code protected double lastReportedValue}, its {@code private WorkRoutine workRoutine} (ordinal
 * used as the routine number), and its {@code onPartActivate(...)} (to cycle the routine from our
 * configurator). All three are wrapped in try/catch: if AdvancedAE changes, the worst case is "that
 * part cannot be read/cycled" — never a crash.
 *
 * <p>1.21.1 port: unchanged (pure reflection + AE2 base-class calls).
 */
final class AaeMonitorRef implements IMonitorRef {

    /** Fully qualified names of the counterpart part classes (strings only, never imported). */
    static final String[] AAE_PART_CLASSES = {
            "net.pedroksl.advanced_ae.common.parts.ThroughputMonitorPart",
            "com.cheesexiaolv.aebackports.Parts.ThroughputMonitorPart",
            "com.cheesexiaolv.Aaemonitor.Parts.ThroughputMonitorPart",
    };

    /** Try every name, return all that load (empty if none). */
    static List<Class<?>> resolvePartClasses() {
        List<Class<?>> resolved = new ArrayList<>(AAE_PART_CLASSES.length);
        for (String className : AAE_PART_CLASSES) {
            try {
                resolved.add(Class.forName(className));
            } catch (ClassNotFoundException | LinkageError ignored) {
                // try the next name
            }
        }
        return List.copyOf(resolved);
    }

    /** Field name of the routine (private enum). */
    private static final String ROUTINE_FIELD = "workRoutine";
    /** Field name of the rate (protected; AdvancedAE uses double, the old backport used float). */
    private static final String LAST_VALUE_FIELD = "lastReportedValue";

    /**
     * Field cache: resolved per <b>actual part class</b>, not per "first loadable class".
     *
     * <p>If a pack ships both AdvancedAE and an old id jar, their field declarations differ
     * (double vs float). Using the wrong class's {@code Field} would throw
     * {@code IllegalArgumentException}. {@code computeIfAbsent} is idempotent; a sentinel object
     * stands in for "missing" because {@code ConcurrentHashMap} forbids null values.
     */
    private static final Map<Class<?>, Object> ROUTINE_FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Object> LAST_VALUE_FIELDS = new ConcurrentHashMap<>();
    private static final Object MISSING = new Object();

    private final AbstractMonitorPart part;

    AaeMonitorRef(AbstractMonitorPart part) {
        this.part = part;
    }

    @Override
    @Nullable
    public AEKey displayed() {
        // AE2 base-class public method -- type-safe, no reflection needed
        return this.part.getDisplayed();
    }

    @Override
    public long amount() {
        return this.part.getAmount();
    }

    @Override
    public double lastReportedValue() {
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
        // AdvancedAE has no "advanced monitor" with history -> the terminal shows values only
        return false;
    }

    @Override
    @Nullable
    public Object historySource() {
        return null;
    }

    @Override
    public boolean cycleRoutineByConfigurator(Player player, InteractionHand hand, Vec3 clickLocation) {
        Method method = resolveOnUseItemOn(this.part.getClass());
        if (method == null) {
            return false;
        }
        try {
            // 1.21.1: IPart#onPartActivate was replaced by onUseItemOn(ItemStack, Player,
            // InteractionHand, Vec3), so the held stack has to be passed explicitly.
            Object result = method.invoke(this.part, player.getItemInHand(hand), player, hand, clickLocation);
            return result instanceof Boolean changed && changed;
        } catch (ReflectiveOperationException | RuntimeException e) {
            AELog.warn("AE MonitorNet: failed to cycle the AdvancedAE throughput monitor routine", e);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Reflection details (lazy per actual class, degrade on failure)
    // ------------------------------------------------------------------

    private static int clampRoutine(int ordinal) {
        return ordinal >= 0 && ordinal < MonitorRefs.ROUTINE_TICKS.length ? ordinal : 1;
    }

    /** Read the ordinal of the private {@code workRoutine} field; on failure assume 1 (seconds). */
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

    /**
     * Resolve {@code onUseItemOn(ItemStack, Player, InteractionHand, Vec3)} — the 1.21 replacement
     * for the old {@code onPartActivate(Player, InteractionHand, Vec3)}.
     *
     * <p>Verified against the shipped AdvancedAE 1.21.1 jar: the class still declares
     * {@code public boolean onUseItemOn(ItemStack, Player, InteractionHand, Vec3)}.
     */
    @Nullable
    private static Method resolveOnUseItemOn(Class<?> partClass) {
        try {
            // public method (declared by AbstractMonitorPart); getMethod walks the superclass chain
            return partClass.getMethod("onUseItemOn", net.minecraft.world.item.ItemStack.class,
                    Player.class, InteractionHand.class, Vec3.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            AELog.warn("AE MonitorNet: the AdvancedAE part is present but onUseItemOn is unavailable", e);
            return null;
        }
    }

    /**
     * Walk the class hierarchy for a field (AdvancedAE's {@code lastReportedValue} is protected,
     * {@code workRoutine} is private; both use {@code getDeclaredField + setAccessible}).
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
                    // keep walking up
                } catch (RuntimeException e) {
                    return MISSING;
                }
            }
            return MISSING;
        });
        return cached == MISSING ? null : (Field) cached;
    }
}
