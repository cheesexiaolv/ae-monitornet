package com.cheesexiaolv.aetm.network;

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
 * 所以聚合终端的行数据只能走自建通道。这里用 Forge 1.19.2 的
 * {@code NetworkRegistry.ChannelBuilder...eventNetworkChannel()}（与 AE2、ExtendedAE 同款写法）。
 *
 * <p>只需要服务端 → 客户端：客户端的操作按钮全部走 AE2 菜单的 {@code registerClientAction}。
 */
public final class AetmNetwork {

    private static final ResourceLocation CHANNEL_NAME = new ResourceLocation(Aetm.MODID, "main");
    private static EventNetworkChannel channel;

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
                com.cheesexiaolv.aetm.client.TerminalClientPackets.handle(message);
            }
        });
    }
}
