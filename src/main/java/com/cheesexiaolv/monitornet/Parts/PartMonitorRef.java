package com.cheesexiaolv.monitornet.Parts;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import appeng.api.stacks.AEKey;

/**
 * {@link IMonitorRef} 的"自家零件"实现：直接包 本模组 的 {@link ThroughputMonitorPart}。
 *
 * <p>类型安全、零反射（AAE 的部件才需要反射，见 {@code AaeMonitorRef}）。
 *
 * <p>包私有：外面只通过 {@link MonitorRefs#of} 拿 {@link IMonitorRef}。
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
        // 自家零件：它的 onPartActivate 认得 本模组 的配置器并 cycle 档位
        return this.part.onPartActivate(player, hand, clickLocation);
    }
}
