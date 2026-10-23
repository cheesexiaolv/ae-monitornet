package com.cheesexiaolv.monitornet.Items;

import com.cheesexiaolv.monitornet.Parts.IMonitorRef;
import com.cheesexiaolv.monitornet.Parts.MonitorRefs;
import com.cheesexiaolv.monitornet.Parts.ThroughputMonitorPart;
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
 * 吞吐量监控配置器：右键监控器切换它的速率档位（per tick / 秒 / 分 / 10 分）。
 *
 * <p>装了两个 mod 时要能<b>一把配置器通吃</b>：
 * <ul>
 *   <li>本模组 自家的监控器（普通 / 高级）→ 直接调它的 {@code onPartActivate}；</li>
 *   <li>AdvancedAE 的监控器（如果装了）→ 走 {@link MonitorRefs} 的<b>反射桥</b>，
 *       调它的 {@code onPartActivate}（对方实现里认它自己的配置器，然后 cycle 档位）。</li>
 * </ul>
 * AAE 的配置器只认它自己的部件类（{@code instanceof}），所以想同时管两类监控器时用 本模组 这把即可。
 * 对方 mod 不存在、或它的实现变了导致反射失败时，{@link IMonitorRef#cycleRoutineByConfigurator} 返回 false，
 * 这里就当作"没点到监控器"继续往下走（{@code InteractionResult.PASS}），不会崩。
 */
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
            if (part == null) {
                return InteractionResult.PASS;
            }

            // 自家零件走类型安全路径；AAE 的零件由 MonitorRefs 用反射包装
            if (part instanceof ThroughputMonitorPart own) {
                if (!level.isClientSide && context.getPlayer() != null) {
                    own.onPartActivate(context.getPlayer(), context.getHand(), context.getClickLocation());
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
