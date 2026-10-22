package com.cheesexiaolv.aetm.Parts;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.AEKey;

/**
 * One throughput monitor as seen by the terminal — so the terminal can enumerate both
 * <b>our own</b> monitors and <b>AdvancedAE's</b> (compatibility layer for packs that ship both).
 *
 * <p>Why this layer: {@code ThroughputTerminalMenu} used to enumerate by
 * {@code ThroughputMonitorPart.class.isAssignableFrom(machineClass)}, but AdvancedAE's part is a
 * different class in a different package that we cannot import (that would be a hard dependency).
 *
 * <p>Implementations: {@code PartMonitorRef} (our part, type-safe) and {@code AaeMonitorRef}
 * (AdvancedAE's part, all reflection, no reference to {@code net.pedroksl.*} types).
 *
 * <p>1.21.1 port: unchanged (pure MC/AE2 API).
 */
public interface IMonitorRef {

    /** The key this part monitors ({@code null} = not configured yet; the terminal skips it). */
    @Nullable
    AEKey displayed();

    /** Amount stored in the network. */
    long amount();

    /** Last reported "per routine" value (divide by {@link #routineTicks()} for per-tick). */
    double lastReportedValue();

    /** How many ticks the current routine represents (0=t 1=s 2=m 3=10m -> 1/20/1200/12000). */
    int routineTicks();

    /** Current routine ordinal (0=t 1=s 2=m 3=10m). */
    int routineOrdinal();

    /** Block position of the part. */
    BlockPos pos();

    /** Dimension id of the part (null if unavailable). */
    @Nullable
    ResourceLocation dimension();

    /** Whether this is the "advanced" monitor (with history); AdvancedAE's part has no history. */
    boolean advanced();

    /**
     * The advanced monitor instance (so the terminal can fetch per-row history); {@code null} when
     * this is not an advanced monitor.
     *
     * <p>Returned as {@code Object} so this interface does not depend on
     * {@code AdvancedThroughputMonitorPart}; the menu casts it (it only asks rows with
     * {@code advanced() == true}, which only our advanced part satisfies).
     */
    @Nullable
    Object historySource();

    /**
     * Cycle this part's rate routine, driven by the configurator item.
     *
     * <p>Our parts call their own {@code onPartActivate}; AdvancedAE's part is called through
     * reflection (its implementation recognises its own configurator and cycles).
     *
     * @return whether a change actually happened
     */
    boolean cycleRoutineByConfigurator(net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.Vec3 clickLocation);
}
