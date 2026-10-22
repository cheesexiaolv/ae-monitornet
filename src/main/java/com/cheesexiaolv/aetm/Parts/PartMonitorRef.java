package com.cheesexiaolv.aetm.Parts;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import appeng.api.stacks.AEKey;

/**
 * {@link IMonitorRef} for our own part: wraps {@link ThroughputMonitorPart} directly.
 *
 * <p>Type-safe, zero reflection (only AdvancedAE's part needs reflection, see
 * {@code AaeMonitorRef}). Package-private: the outside only gets {@link IMonitorRef} through
 * {@link MonitorRefs#of}.
 *
 * <p>1.21.1 port: unchanged.
 */
final class PartMonitorRef implements IMonitorRef {

    private final ThroughputMonitorPart part;

    PartMonitorRef(ThroughputMonitorPart part) {
        this.part = part;
    }

    @Override
    @Nullable
    public AEKey displayed() {
        return this.part.getDisplayed();
    }

    @Override
    public long amount() {
        return this.part.getAmount();
    }

    @Override
    public double lastReportedValue() {
        return this.part.getLastReportedValue();
    }

    @Override
    public int routineTicks() {
        return this.part.getRoutineTicks();
    }

    @Override
    public int routineOrdinal() {
        return this.part.getRoutineOrdinal();
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
        return this.part instanceof AdvancedThroughputMonitorPart;
    }

    @Override
    @Nullable
    public Object historySource() {
        return this.part instanceof AdvancedThroughputMonitorPart advanced ? advanced : null;
    }

    @Override
    public boolean cycleRoutineByConfigurator(Player player, InteractionHand hand, Vec3 clickLocation) {
        // Our part: onUseItemOn recognises our configurator and cycles the routine.
        // 1.21.1: IPart#onPartActivate(Player, InteractionHand, Vec3) was replaced by
        // onUseItemOn(ItemStack, Player, InteractionHand, Vec3) -- the held stack is now explicit.
        return this.part.onUseItemOn(player.getItemInHand(hand), player, hand, clickLocation);
    }
}
