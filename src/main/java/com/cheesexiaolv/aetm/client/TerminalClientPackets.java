package com.cheesexiaolv.aetm.client;

import com.cheesexiaolv.aetm.menu.ThroughputTerminalMenu;
import com.cheesexiaolv.aetm.network.TerminalRowsPacket;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 存储监控终端的客户端收包入口。
 *
 * <p>行数据/曲线直接塞进当前打开的菜单（客户端侧也持有同一个 {@link ThroughputTerminalMenu} 实例），
 * 屏幕每帧从菜单里读——与 AE2 终端把 repo 放在菜单里是同一个思路。
 *
 * <p>⚠️ 「定位」高亮是<b>例外</b>：它登记到静态的 {@link ClientHighlights}，不经过菜单 ——
 * 菜单会随界面关闭被丢掉，而高亮要求"关掉界面也能继续看到"。
 */
@OnlyIn(Dist.CLIENT)
public final class TerminalClientPackets {

    private TerminalClientPackets() {
    }

    public static void handle(TerminalRowsPacket message) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        // 世界高亮：与菜单无关，直接登记（这样关掉终端界面也照样画）
        if (message.mode() == TerminalRowsPacket.MODE_HIGHLIGHT) {
            ClientHighlights.show(message.highlightPos(),
                    ClientHighlights.parseDimension(message.highlightDimension()),
                    message.highlightTicks());
            return;
        }

        if (minecraft.player.containerMenu instanceof ThroughputTerminalMenu menu) {
            menu.applyRowsPacket(message);
        }
    }
}
