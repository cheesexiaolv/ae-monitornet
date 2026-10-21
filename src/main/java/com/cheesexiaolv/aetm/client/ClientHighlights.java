package com.cheesexiaolv.aetm.client;

import javax.annotation.Nullable;

import com.cheesexiaolv.aetm.Aetm;
import com.cheesexiaolv.aetm.network.TerminalRowsPacket;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 「定位」在世界里画的那个<b>红色描边方块</b>（客户端渲染 + 高亮状态）。
 *
 * <p>机制来源：ExtendedAE 的样板管理终端定位按钮 —— 服务端算出目标坐标 + 维度 + 持续时长，
 * 发一个 block-highlight 包给客户端，客户端在渲染阶段把那个方块描边。
 * ExtendedAE 那套是通过 Glodium 的 {@code HighlightHandler}/{@code HighlightRender}
 * （{@code RenderLevelStageEvent} + 自定义线宽 RenderType）实现的；本模组没有 Glodium 依赖，
 * 所以用等价的公开 API 复刻同一效果：
 * <ul>
 *   <li>钩子：Forge 的 {@link RenderLevelStageEvent}（与 Glodium 的 {@code HighlightRender.hook} 同一个事件）；</li>
 *   <li>画线：{@code LevelRenderer.renderLineBox(poseStack, consumer, aabb, r, g, b, a)}，
 *       这正是原版方块选中框（以及 AE2 {@code RenderBlockOutlineHook} 画零件框）用的那个方法；</li>
 *   <li>颜色：<b>纯红</b> {@code (1.0, 0.0, 0.0)} —— 反编译 ExtendedAE 的 {@code EAEHighlightHandler}
 *       静态初始化确认 {@code RED = new ColorData(1f, 0f, 0f)}；这里用 {@code RenderType.lines()} 线宽 1，
 *       <b>不画任何箭头/模型</b>（EAE 那个扎眼的红箭头图标不抄）。</li>
 * </ul>
 *
 * <p>⚠️ <b>高亮状态放在这里（静态），不放在菜单里</b>：菜单随界面开关生灭，状态放菜单里就会
 * "关掉终端 → 高亮立刻消失"。放静态字段后，关掉界面高亮会<b>继续存在到期</b>，
 * 玩家可以边走边找那台机器。到期用 {@code level.getGameTime()} 判断，不需要"取消高亮"包。
 */
@Mod.EventBusSubscriber(modid = Aetm.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientHighlights {

    /** 描边颜色（纯红）：与 ExtendedAE 的 {@code ColorData.RED = (1, 0, 0)} 完全一致。 */
    private static final float RED = 1.0F;
    private static final float GREEN = 0.0F;
    private static final float BLUE = 0.0F;
    /** 描边的外扩量：比方块本身大一点点，线条不会跟方块面重叠导致闪烁（原版选中框是 0.002）。 */
    private static final double INFLATE = 0.002D;

    // ---- 高亮状态（静态：与界面生命周期无关）----

    @Nullable
    private static BlockPos pos;
    @Nullable
    private static ResourceKey<Level> dimension;
    /** 到期时的游戏时间（tick）；{@code pos == null} 表示没有高亮。 */
    private static long expireGameTime;

    private ClientHighlights() {
    }

    /**
     * 登记一个世界高亮（客户端收到 {@link TerminalRowsPacket#MODE_HIGHLIGHT} 时调用）。
     *
     * @param target 要描边的方块
     * @param dim    方块所在维度（null = 解析不出来，不校验维度）
     * @param ticks  持续多少 tick（由服务端决定，200 tick = 10 秒）
     */
    public static void show(BlockPos target, @Nullable ResourceKey<Level> dim, int ticks) {
        var level = Minecraft.getInstance().level;
        long now = level == null ? 0L : level.getGameTime();
        pos = target;
        dimension = dim;
        expireGameTime = now + Math.max(1, ticks);
    }

    /** 立刻清掉高亮（留给以后"取消定位"用；正常到期会自动失效）。 */
    public static void clear() {
        pos = null;
        dimension = null;
        expireGameTime = 0L;
    }

    /** 当前是否有一个还没过期的高亮。 */
    public static boolean isActive() {
        var level = Minecraft.getInstance().level;
        return pos != null && level != null && level.getGameTime() < expireGameTime;
    }

    /**
     * 解析服务端发来的维度 id。
     *
     * @return 解析不出来时返回 null（调用方就不校验维度，照样画）
     */
    @Nullable
    public static ResourceKey<Level> parseDimension(String dimensionId) {
        if (dimensionId == null || dimensionId.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(dimensionId);
        // 1.20.1：Registry.DIMENSION_REGISTRY 已随 1.19.3 的注册表重构移除，改用 Registries.DIMENSION
        return id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        // 状态是静态的，所以关掉界面之后依然会画（这正是这次要的效果）
        BlockPos target = pos;
        if (target == null || minecraft.level.getGameTime() >= expireGameTime) {
            return;
        }
        // 维度要一致，否则量子网络桥那类跨维度网络上会画出"幽灵框"
        if (dimension != null && !minecraft.level.dimension().equals(dimension)) {
            return;
        }
        if (!minecraft.level.hasChunkAt(target)) {
            return;
        }

        // ⚠️ 与参考实现一致：事件给的 poseStack 已经是"相机相对"坐标系，所以坐标要减掉相机位置
        var camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        AABB box = new AABB(target).inflate(INFLATE).move(-camera.x, -camera.y, -camera.z);

        var bufferSource = minecraft.renderBuffers().bufferSource();
        LevelRenderer.renderLineBox(poseStack, bufferSource.getBuffer(RenderType.lines()), box,
                RED, GREEN, BLUE, 1.0F);
        bufferSource.endBatch(RenderType.lines());
    }
}
