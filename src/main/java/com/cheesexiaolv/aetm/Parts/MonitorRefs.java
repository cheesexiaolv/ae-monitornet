package com.cheesexiaolv.aetm.Parts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.neoforged.fml.ModList;

import appeng.parts.reporting.AbstractMonitorPart;

/**
 * <b>Bridge to AdvancedAE</b>: wraps "someone else's throughput monitor part" as an
 * {@link IMonitorRef}.
 *
 * <p>Design (both mods installed, both kinds of monitors must show up in the terminal):
 * <ul>
 *   <li><b>Soft dependency</b>: AdvancedAE's part class is only resolved with
 *       {@code Class.forName} when {@link ModList#isLoaded} is true; the class name is just a
 *       string, no {@code net.pedroksl.*} type appears in this package.</li>
 *   <li><b>Identify by name</b>: ownership is decided with the reflected {@code Class}, then the
 *       instance is cast to the shared AE2 base class {@link AbstractMonitorPart}, so reading
 *       {@code getDisplayed()/getAmount()} stays type-safe.</li>
 *   <li><b>Degrade on failure</b>: if the class or its fields/methods cannot be resolved, that part
 *       is skipped ({@link #of} returns null); the terminal keeps working.</li>
 * </ul>
 *
 * <p>Wrappers are cached per part instance ({@code WeakHashMap}) so the terminal does not recreate
 * them every tick.
 *
 * <p>1.21.1 port: only the {@code ModList} import moved ({@code net.minecraftforge.fml.ModList} ->
 * {@code net.neoforged.fml.ModList}). The reflected interaction method did change — see
 * {@link AaeMonitorRef}.
 */
public final class MonitorRefs {

    /**
     * Mod ids providing the "throughput monitor" niche (in priority order).
     *
     * <p>The 1.20.1/1.21.1 branch's counterpart is AdvancedAE ({@code advanced_ae});
     * {@code aebackports} / {@code aaemonitor} are historical ids of our own 1.19.2 addons, kept
     * because recognising them costs nothing.
     */
    public static final String[] PROVIDER_MODIDS = { "advanced_ae", "aebackports", "aaemonitor" };

    /** Whether the counterpart is present (any id matches). */
    public static boolean isProviderLoaded() {
        for (String modid : PROVIDER_MODIDS) {
            if (ModList.get().isLoaded(modid)) {
                return true;
            }
        }
        return false;
    }

    /** Routine -> tick count (matches our own {@code WorkRoutine} / {@code TerminalSettings}). */
    static final int[] ROUTINE_TICKS = { 1, 20, 1200, 12000 };

    /** Counterpart part classes (lazily resolved; empty = not installed / all failed). */
    @Nullable
    private static List<Class<?>> providerPartClasses;
    private static boolean providerPartClassesResolved;

    /** Part instance -> wrapper (weak keys so unloaded parts can be collected). */
    private static final java.util.Map<Object, IMonitorRef> CACHE = new java.util.WeakHashMap<>();

    private MonitorRefs() {
    }

    /**
     * Wrap a part as an {@link IMonitorRef}.
     *
     * @param part a part instance (ours, or AdvancedAE's; null for anything else)
     * @return the wrapper, or {@code null} when the type is unknown / AdvancedAE is unavailable
     */
    @Nullable
    public static IMonitorRef of(Object part) {
        if (part == null) {
            return null;
        }
        // Our own part: type-safe direct implementation
        if (part instanceof ThroughputMonitorPart own) {
            return new PartMonitorRef(own);
        }
        // The counterpart's part: only if the mod is loaded and its class resolves
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

    /** Whether this part belongs to the counterpart mod (for menu enumeration, no wrapping). */
    public static boolean isAaeMonitor(Object part) {
        return isProviderPart(part);
    }

    /** Whether {@code machineClass} is a (our or counterpart) throughput monitor. */
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
     * Counterpart part classes (lazy: never resolved when the mod is absent; resolution failure is
     * logged once).
     *
     * @return read-only list, may be empty
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

    /** Debug helper: all resolved counterpart part classes. */
    static List<Class<?>> resolvedProviderClasses() {
        return new ArrayList<>(providerPartClasses());
    }
}
