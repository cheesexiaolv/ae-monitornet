package com.cheesexiaolv.aetm.Items;

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

public class ThroughputMonitorConfigurator extends Item {

    public ThroughputMonitorConfigurator(Properties properties) {
        super(properties.stacksTo(1)); // 配置器通常不堆叠
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction side = context.getClickedFace();

        // 检查点击的方块是否包含 AE2 的 Part（即 Cable Bus）
        if (level.getBlockEntity(pos) instanceof IPartHost host) {
            IPart part = host.getPart(side);

            // 如果点击的正是我们的吞吐量监控器
            if (part instanceof ThroughputMonitorPart throughputPart) {
                if (!level.isClientSide && context.getPlayer() != null) {
                    // 执行切换周期的逻辑（我们在 Part 类里实现的 cycle 逻辑）
                    throughputPart.onPartActivate(context.getPlayer(), context.getHand(), context.getClickLocation());
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        return InteractionResult.PASS;
    }
}