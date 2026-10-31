package com.cheesexiaolv.monitornet.client.gui;

import appeng.client.gui.Icon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.IntSupplier;

/**
 * 左侧工具栏里的「全局速率档位」按钮（挂在「终端风格」下方）。
 *
 * <p>底版<b>直接引用 AE2 原生</b>的工具栏按钮底：{@code ae2:textures/guis/states.png} 里
 * {@code Icon.TOOLBAR_BUTTON_BACKGROUND}（u=176,v=128,18×20）、聚焦 {@code ..._FOCUS}（194,128）、
 * 悬停 {@code ..._HOVER}（212,128）——和「指南 / 终端风格」那两颗按钮逐像素一致。
 *
 * <p>按钮里叠 t / s / m 图标（{@link WidgetSprites} 的 16×16 单元格），相对按钮左上角偏移 {@code (+1,+1)}。
 * 位置由 AE2 的 {@code VerticalButtonBar} 计算。
 */
public class RoutineToolbarButton extends Button {

    /** 当前档位序号（0 = t、1 = s、2 = m），每帧取。 */
    private final IntSupplier routineOrdinal;

    public RoutineToolbarButton(Component narration, OnPress onPress, IntSupplier routineOrdinal) {
        super(0, 0, 16, 16, narration, onPress, Button.DEFAULT_NARRATION);
        this.routineOrdinal = routineOrdinal;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.isHovered();
        int yOffset = hovered ? 1 : 0;

        // AE2 原生工具栏按钮底（18×20，左移 1px 让它在 16px 的 widget 里居中）
        Icon background = hovered ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                : this.isFocused() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS
                        : Icon.TOOLBAR_BUTTON_BACKGROUND;
        background.getBlitter().dest(getX() - 1, getY() + yOffset, 18, 20).zOffset(2).blit(guiGraphics);

        // t / s / m 图标：16×16 单元格，贴到按钮 (+1,+1)
        WidgetSprites.drawRoutineIcon(guiGraphics, this.routineOrdinal.getAsInt(), getX(), getY(), yOffset);
    }
}
