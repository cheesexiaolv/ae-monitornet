package com.cheesexiaolv.aetm.network;

import java.util.function.Consumer;

import org.apache.commons.lang3.tuple.Pair;

import com.cheesexiaolv.aetm.Aetm;

import io.netty.buffer.Unpooled;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;

/**
 * 模组自己的网络通道。
 *
 * <p>AE2 的包类型是闭合枚举（{@code BasePacketHandler.PacketTypes}），第三方无法追加，
 * 所以聚合终端的行数据只能走自建通道。这里用 Forge 的
 * {@code NetworkRegistry.ChannelBuilder...eventNetworkChannel()}（与 AE2、ExtendedAE 同款写法）。
 *
 * <p>只需要服务端 → 客户端：客户端的操作按钮全部走 AE2 菜单的 {@code registerClientAction}。
 */
public final class AetmNetwork {

    private static final ResourceLocation CHANNEL_NAME = ResourceLocation.fromNamespaceAndPath(Aetm.MODID, "main");
    private static EventNetworkChannel channel;

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

    public static void init() {
        channel = NetworkRegistry.ChannelBuilder.named(CHANNEL_NAME)
                .networkProtocolVersion(() -> "1")
                .clientAcceptedVersions(version -> true)
                .serverAcceptedVersions(version -> true)
                .eventNetworkChannel();
        // Forge 的命名很反直觉：ServerCustomPayloadEvent = 服务端发来的载荷（客户端侧接收）
        channel.addListener(AetmNetwork::onPayloadFromServer);
    }

    /** 客户端登记包处理器（只在客户端调用；服务端保持空实现）。 */
    public static void setClientHandler(Consumer<TerminalRowsPacket> handler) {
        clientHandler = handler == null ? message -> {
        } : handler;
    }

    /** 把行数据发给指定玩家。 */
    public static void sendToPlayer(ServerPlayer player, TerminalRowsPacket message) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        message.encode(buf);
        Packet<?> packet = NetworkDirection.PLAY_TO_CLIENT.buildPacket(Pair.of(buf, 0), CHANNEL_NAME).getThis();
        player.connection.send(packet);
    }

    private static void onPayloadFromServer(NetworkEvent.ServerCustomPayloadEvent ev) {
        if (ev instanceof NetworkEvent.ServerCustomPayloadLoginEvent) {
            return;
        }

        var ctx = ev.getSource().get();
        ctx.setPacketHandled(true);

        // 在网络线程上先把数据解出来（与 AE2 的做法一致），再回到主线程应用到菜单
        var message = TerminalRowsPacket.decode(ev.getPayload());
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) {
                clientHandler.accept(message);
            }
        });
    }
}
