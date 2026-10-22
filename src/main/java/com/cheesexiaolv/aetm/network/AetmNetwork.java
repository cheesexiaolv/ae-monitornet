package com.cheesexiaolv.aetm.network;

import java.util.function.Consumer;

import com.cheesexiaolv.aetm.AeMonitorNet;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 模组自己的网络通道。
 *
 * <p>AE2 的包类型是闭合枚举（{@code BasePacketHandler.PacketTypes}），第三方无法追加，
 * 所以聚合终端的行数据只能走自建通道。
 *
 * <p>只需要服务端 → 客户端：客户端的操作按钮全部走 AE2 菜单的 {@code registerClientAction}。
 *
 * <p><b>1.20.1 → 1.21.1（NeoForge）整段重写</b>：Forge 的
 * {@code NetworkRegistry.ChannelBuilder...eventNetworkChannel()}、
 * {@code NetworkEvent.ServerCustomPayloadEvent} 与 {@code NetworkDirection.PLAY_TO_CLIENT.buildPacket(...)}
 * 全部被删除，改为：
 * <ul>
 *   <li>注册：{@link RegisterPayloadHandlersEvent} + {@link PayloadRegistrar#playToClient}
 *       （证据：{@code reference/AE2-1.21.1-19.2.17/appeng/core/network/InitNetwork.java:40-45,82-84}）；</li>
 *   <li>发送：{@link PacketDistributor#sendToPlayer(ServerPlayer, net.minecraft.network.protocol.common.custom.CustomPacketPayload, net.minecraft.network.protocol.common.custom.CustomPacketPayload...)}
 *       （同文件 {@code appeng/menu/AEBaseMenu.java:765} 的用法）。</li>
 * </ul>
 *
 * <p>注册用 {@link EventBusSubscriber} 挂在 MOD 总线上（<b>不改 {@code AeMonitorNet}</b>）：
 * 注解形状与 AE2 自己的写法一致（{@code appeng/datagen/AE2DataGenerators.java:54-57}）。
 *
 * <p><b>可插拔的客户端处理器保留</b>：{@link #setClientHandler(Consumer)} 的签名与 1.20.1 完全相同，
 * 客户端初始化（{@code ClientEvents}）照旧调用
 * {@code AetmNetwork.setClientHandler(TerminalClientPackets::handle)}，
 * 于是这个共同类不必 import 任何客户端类型，服务端也不会加载客户端类。
 */
@EventBusSubscriber(modid = AeMonitorNet.MODID, bus = Bus.MOD)
public final class AetmNetwork {

    /** 协议版本。1.20.1 用的是 {@code networkProtocolVersion(() -> "1")}，这里沿用。 */
    private static final String PROTOCOL_VERSION = "1";

    /**
     * 客户端侧的包处理入口。
     *
     * <p>真正的实现由客户端初始化登记进来：{@code ClientEvents} 里调用
     * {@link #setClientHandler} 把 {@code client.TerminalClientPackets::handle}
     * （把行数据塞进当前打开的 {@code ThroughputTerminalMenu}）接上 ——
     * 这样这个共同类不必 import 任何客户端类型，服务端也不会加载客户端类。
     */
    private static Consumer<TerminalRowsPacket> clientHandler = message -> {
    };

    private AetmNetwork() {
    }

    /**
     * 1.20.1 的初始化入口，<b>在 1.21.1 已无实际动作</b>。
     *
     * <p>NeoForge 的载荷注册是事件驱动（见 {@link #onRegisterPayloads}），不再有"建通道"这一步。
     * 保留这个方法是<b>为了兼容 1.20.1 形状的调用点</b>（旧入口类里 {@code AetmNetwork.init()} 一行原样可留），
     * 避免移植时为了删一行而改动入口类。
     */
    public static void init() {
        // 无需操作：PayloadRegistrar 注册由 RegisterPayloadHandlersEvent 触发。
    }

    /** 客户端登记包处理器（只在客户端调用；服务端保持空实现）。 */
    public static void setClientHandler(Consumer<TerminalRowsPacket> handler) {
        clientHandler = handler == null ? message -> {
        } : handler;
    }

    /** 把行数据发给指定玩家。 */
    public static void sendToPlayer(ServerPlayer player, TerminalRowsPacket message) {
        PacketDistributor.sendToPlayer(player, message);
    }

    /**
     * 向 NeoForge 注册本模组的载荷。
     *
     * <p>{@link PayloadRegistrar#optional()}：服务端装了本模组、客户端没装时<b>不拒绝连接</b>
     * （1.20.1 的 {@code clientAcceptedVersions(version -> true)} 是同样的宽松语义）。
     * 客户端收到不认识的载荷只会被原版忽略，而不是掉线。
     */
    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();
        registrar.playToClient(TerminalRowsPacket.TYPE, TerminalRowsPacket.STREAM_CODEC,
                AetmNetwork::handleClientPayload);
    }

    /**
     * 客户端收到行数据包。
     *
     * <p>1.20.1 在<b>网络线程</b>上先 decode、再 {@code enqueueWork} 回主线程应用；
     * 1.21.1 的 decode 由 {@code StreamCodec} 在管线里完成，{@code PayloadRegistrar} 默认已把
     * handler 包成 {@code MainThreadPayloadHandler}，这里再 {@code enqueueWork}（主线程上等于立即执行）
     * 只为保持"只在客户端、只在主线程动菜单"的旧约束。
     */
    private static void handleClientPayload(TerminalRowsPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) {
                clientHandler.accept(payload);
            }
        });
    }
}
