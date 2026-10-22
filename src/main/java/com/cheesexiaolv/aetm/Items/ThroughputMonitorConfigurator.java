package com.cheesexiaolv.aetm.Items;

import com.cheesexiaolv.aetm.Parts.IMonitorRef;
import com.cheesexiaolv.aetm.Parts.MonitorRefs;
import com.cheesexiaolv.aetm.Parts.ThroughputMonitorPart;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Throughput monitor configurator: right-click a monitor to cycle its rate routine
 * (per tick / second / minute / 10 minutes).
 *
 * <p>One configurator should work for both mods:
 * <ul>
 *   <li>our own monitors (basic / advanced) -> calls the part directly;</li>
 *   <li>AdvancedAE's monitor (if installed) -> goes through the reflection bridge in
 *       {@link MonitorRefs}.</li>
 * </ul>
 * If the other mod is absent, or its implementation changed so reflection fails,
 * {@link IMonitorRef#cycleRoutineByConfigurator} returns false and this item falls through to
 * {@code InteractionResult.PASS} instead of crashing.
 *
 * <p><b>1.21.1 change</b>: the part interaction entry point is
 * {@code IPart#onUseItemOn(ItemStack, Player, InteractionHand, Vec3)} instead of
 * {@code onPartActivate(Player, InteractionHand, Vec3)} (AE2 19.2,
 * {@code appeng/api/parts/IPart.java:130}).
 */
public class ThroughputMonitorConfigurator extends Item {

    public ThroughputMonitorConfigurator(Properties properties) {
        super(properties.stacksTo(1)); // configurators usually do not stack
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction side = context.getClickedFace();

        // Is the clicked block an AE2 cable bus (contains parts)?
        if (level.getBlockEntity(pos) instanceof IPartHost host) {
            IPart part = host.getPart(side);
            if (part == null) {
                return InteractionResult.PASS;
            }

            // Our own part: type-safe path; AdvancedAE's part is wrapped reflectively by MonitorRefs
            if (part instanceof ThroughputMonitorPart own) {
                if (!level.isClientSide && context.getPlayer() != null) {
                    own.onUseItemOn(context.getItemInHand(), context.getPlayer(), context.getHand(),
                            context.getClickLocation());
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            IMonitorRef ref = MonitorRefs.of(part);
            if (ref != null && !level.isClientSide && context.getPlayer() != null) {
                ref.cycleRoutineByConfigurator(context.getPlayer(), context.getHand(), context.getClickLocation());
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        return InteractionResult.PASS;
    }
}
