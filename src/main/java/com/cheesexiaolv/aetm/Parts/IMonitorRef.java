package com.cheesexiaolv.aetm.Parts;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.AEKey;

/**
 * 终端的"一个吞吐量监控器"抽象 —— 让终端既能认 <b>aetm 自己的</b>监控器，
 * 也能认 <b>AdvancedAE（AAE）</b>的监控器（同一存档里两个 mod 共存时的兼容层）。
 *
 * <p>为什么要这一层：{@code ThroughputTerminalMenu} 原来是按
 * {@code ThroughputMonitorPart.class.isAssignableFrom(machineClass)} 枚举的，
 * 而 AAE 的部件是<b>另一个包里的另一个类</b>（{@code net.pedroksl.advanced_ae.common.parts.ThroughputMonitorPart}），
 * 既不是我们的子类、也不能 import（否则就变成硬依赖了）。
 *
 * <p>所以这里只留下终端真正需要的那点信息；两种实现分别见：
 * <ul>
 *   <li>{@code PartMonitorRef} —— 直接包 aetm 的 {@link ThroughputMonitorPart}（类型安全，无反射）；</li>
 *   <li>{@code AaeMonitorRef} —— 包 AAE 的部件，<b>全靠反射</b>（类名只在运行时用
 *       {@code Class.forName} 取，类里不出现对方的任何类型）。</li>
 * </ul>
 */
public interface IMonitorRef {

    /** 该零件正在监控的 key（{@code null} = 还没配置显示物，终端会跳过它）。 */
    @Nullable
    AEKey displayed();

    /** 网络内存量。 */
    long amount();

    /** 该零件最近一次上报的"每档位量"（要除以 {@link #routineTicks()} 才是每 tick 量）。 */
    double lastReportedValue();

    /** 当前档位代表多少 tick（0=t 1=s 2=m 3=10m → 1/20/1200/12000）。 */
    int routineTicks();

    /** 当前档位序号（0=t 1=s 2=m 3=10m）。 */
    int routineOrdinal();

    /** 零件所在方块坐标。 */
    BlockPos pos();

    /** 零件所在维度 id（取不到时返回 null）。 */
    @Nullable
    ResourceLocation dimension();

    /** 是否是"高级"监控器（有历史曲线的那一类）；AAE 的部件没有历史，恒为 false。 */
    boolean advanced();

    /**
     * 高级监控器实例（供终端按行取曲线用）；不是高级监控器时返回 {@code null}。
     *
     * <p>返回 {@code Object} 是为了不让这个接口反向依赖 {@code AdvancedThroughputMonitorPart}；
     * 菜单自己 cast —— 它本来就只对 {@code advanced() == true} 的行做这件事（只有 aetm 的高级版有历史曲线）。
     */
    @Nullable
    Object historySource();

    /**
     * 用"配置器"切换该零件的速率档位。
     *
     * <p>aetm 的零件直接调自己的 {@code onPartActivate}；AAE 的零件走反射调它的
     * {@code onPartActivate}（它的实现里认它自己的配置器、然后 cycle 档位）。
     *
     * @return 是否真的切换了
     */
    boolean cycleRoutineByConfigurator(net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.Vec3 clickLocation);
}
