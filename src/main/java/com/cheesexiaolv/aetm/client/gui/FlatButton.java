package com.cheesexiaolv.aetm.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 自绘扁平按钮（不用原版贴图）。
 *
 * <p>深灰底 + 亮边，悬停高亮，文字白色。
 *
 * <p>1.20.1 的绘制入口从 {@code renderButton(PoseStack, …)} 改成了
 * {@code renderWidget(GuiGraphics, …)}（{@code AbstractWidget#render} 转调它），
 * 填充/文字分别换成 {@code GuiGraphics#fill} / {@code GuiGraphics#drawString}。
 */
public class FlatButton extends Button {

    public FlatButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        // 1.20.1 的 Button 只保留了 (x, y, w, h, message, onPress, narration) 这个 protected 构造器
        // （1.19.2 的 6 参构造器已删除），narration 传原版的 DEFAULT_NARRATION。
        super(x, y, width, height, message, onPress, Button.DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }
        boolean hovered = this.isHoveredOrFocused();
        int border = hovered ? 0xFFD9D9D9 : 0xFF8C8C8C;
        int body = hovered ? 0xFF5C5C5C : 0xFF454545;

        int x = this.getX();
        int y = this.getY();
        int w = this.width;
        int h = this.height;

        guiGraphics.fill(x, y, x + w, y + h, border);
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, body);

        var font = Minecraft.getInstance().font;
        var text = this.getMessage();
        int tx = x + (w - font.width(text)) / 2;
        int ty = y + (h - 8) / 2;
        // 1.19.2 的 Font#draw(PoseStack, Component, …) 不带阴影 → 1.20.1 用 drawString(…, false)
        guiGraphics.drawString(font, text, tx, ty, 0xFFFFFFFF, false);
    }
}
