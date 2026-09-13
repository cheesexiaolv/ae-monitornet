package com.cheesexiaolv.aetm.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 自绘扁平按钮（不用原版贴图）。
 *
 * <p>深灰底 + 亮边，悬停高亮，文字白色。
 */
public class FlatButton extends Button {

    public FlatButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress);
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }
        boolean hovered = this.isHoveredOrFocused();
        int border = hovered ? 0xFFD9D9D9 : 0xFF8C8C8C;
        int body = hovered ? 0xFF5C5C5C : 0xFF454545;

        int x = this.x;
        int y = this.y;
        int w = this.width;
        int h = this.height;

        GuiComponent.fill(poseStack, x, y, x + w, y + h, border);
        GuiComponent.fill(poseStack, x + 1, y + 1, x + w - 1, y + h - 1, body);

        var font = Minecraft.getInstance().font;
        var text = this.getMessage();
        int tx = x + (w - font.width(text)) / 2;
        int ty = y + (h - 8) / 2;
        font.draw(poseStack, text, tx, ty, 0xFFFFFFFF);
    }
}
