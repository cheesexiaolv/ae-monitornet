package com.cheesexiaolv.monitornet.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 窗口 / 终端共用的一张控件精灵表（{@code textures/gui/widgets.png}，画布 80×48）。
 *
 * <p>表里的布局（坐标由画图的人给定，改图时同步改这里）：
 * <ul>
 *   <li><b>长按钮</b> 26×14：常态 {@code (0,0)}、悬停 {@code (26,0)}、按下 {@code (52,0)}（按下帧只有 13 行）；</li>
 *   <li><b>短按钮</b> 14×14：常态 {@code (0,14)}、悬停 {@code (14,14)}、按下 {@code (28,14)}（同样 13 行）；</li>
 *   <li><b>t / s / m 图标</b> 各 16×16 单元格：{@code (0,28)} / {@code (16,28)} / {@code (32,28)}，
 *       叠在按钮上时相对按钮左上角 {@code (+1,+1)}；</li>
 *   <li><b>定位图标</b> 6×10 @ {@code (53,31)}，相对短按钮左上角 {@code (+4,+1)}；</li>
 *   <li><b>关闭图标</b> 6×8 @ {@code (69,32)}，相对短按钮左上角 {@code (+4,+2)}。</li>
 * </ul>
 *
 * <p>按下态的画法：按下帧比常态少 1 行，所以整体<b>下移 1px</b> 贴——这样底边和常态对齐，
 * 看起来是"按下去"，而不是底边抬起来。文字也跟着 +1。
 */
public final class WidgetSprites {

    public static final ResourceLocation SHEET =
            ResourceLocation.fromNamespaceAndPath("ae_monitornet", "textures/gui/widgets.png");
    public static final int SHEET_W = 80;
    public static final int SHEET_H = 48;

    // ---- 长按钮 ----
    public static final int LONG_W = 26;
    public static final int LONG_H = 14;
    public static final int LONG_U_NORMAL = 0;
    public static final int LONG_U_HOVER = 26;
    public static final int LONG_U_PRESSED = 52;
    public static final int LONG_V = 0;

    // ---- 短按钮 ----
    public static final int SHORT_W = 14;
    public static final int SHORT_H = 14;
    public static final int SHORT_U_NORMAL = 0;
    public static final int SHORT_U_HOVER = 14;
    public static final int SHORT_U_PRESSED = 28;
    public static final int SHORT_V = 14;

    // ---- t / s / m 图标（16×16 单元格，叠在按钮上偏移 +1,+1）----
    public static final int ROUTINE_ICON_SIZE = 16;
    public static final int ROUTINE_ICON_DX = 0;
    public static final int ROUTINE_ICON_DY = 1;
    public static final int ROUTINE_T_U = 0;
    public static final int ROUTINE_S_U = 16;
    public static final int ROUTINE_M_U = 32;
    public static final int ROUTINE_ICON_V = 28;

    // ---- 叠在短按钮上的图标 ----
    public static final int ICON_LOCATE_U = 53;
    public static final int ICON_LOCATE_V = 31;
    public static final int ICON_LOCATE_W = 6;
    public static final int ICON_LOCATE_H = 10;
    public static final int ICON_LOCATE_DX = 4;
    public static final int ICON_LOCATE_DY = 1;

    public static final int ICON_CLOSE_U = 69;
    public static final int ICON_CLOSE_V = 32;
    public static final int ICON_CLOSE_W = 6;
    public static final int ICON_CLOSE_H = 8;
    public static final int ICON_CLOSE_DX = 4;
    public static final int ICON_CLOSE_DY = 2;

    /** 按钮状态：常态。 */
    public static final int STATE_NORMAL = 0;
    /** 按钮状态：悬停。 */
    public static final int STATE_HOVER = 1;
    /** 按钮状态：按下（比常态下移 1px）。 */
    public static final int STATE_PRESSED = 2;

    private WidgetSprites() {
    }

    /**
     * 画长/短按钮的底图。
     *
     * @param wide   true = 长按钮（26×14），false = 短按钮（14×14）
     * @param state  {@link #STATE_NORMAL} / {@link #STATE_HOVER} / {@link #STATE_PRESSED}
     */
    public static void drawButton(GuiGraphics guiGraphics, boolean wide, int state, int x, int y) {
        int u;
        int v;
        int w = wide ? LONG_W : SHORT_W;
        int h = wide ? LONG_H : SHORT_H;
        if (wide) {
            v = LONG_V;
            u = state == STATE_PRESSED ? LONG_U_PRESSED : state == STATE_HOVER ? LONG_U_HOVER : LONG_U_NORMAL;
        } else {
            v = SHORT_V;
            u = state == STATE_PRESSED ? SHORT_U_PRESSED : state == STATE_HOVER ? SHORT_U_HOVER : SHORT_U_NORMAL;
        }
        int dy = 0;
        if (state == STATE_PRESSED) {
            h -= 1;
            dy = 1;
        }
        guiGraphics.blit(SHEET, x, y + dy, u, v, w, h, SHEET_W, SHEET_H);
    }

    /** 短按钮 + 叠上去的小图标（定位 / 关闭）。 */
    public static void drawButtonWithIcon(GuiGraphics guiGraphics, int state, int x, int y,
            int iconU, int iconV, int iconW, int iconH, int iconDx, int iconDy) {
        drawButton(guiGraphics, false, state, x, y);
        // 图标跟着按钮一起下移，保持"按在图上"的观感
        int dy = state == STATE_PRESSED ? 1 : 0;
        guiGraphics.blit(SHEET, x + iconDx, y + iconDy + dy, iconU, iconV, iconW, iconH, SHEET_W, SHEET_H);
    }

    /**
     * 画 t / s / m 图标（16×16 单元格）到按钮左上角 {@code (+1,+1)}。
     *
     * <p>⚠️ AE2 的工具栏按钮底是用 {@code Blitter.zOffset(2)} 画的，这里必须把 z 抬到它之上
     * （AE2 自己的图标用 20），否则图标会被按钮底整个盖住、看起来"没显示"。
     *
     * @param routineOrdinal 0 = t、1 = s、2 = m（超出范围按 s 处理）
     */
    public static void drawRoutineIcon(GuiGraphics guiGraphics, int routineOrdinal, int buttonX, int buttonY,
            int yOffset) {
        int u = switch (routineOrdinal) {
            case 0 -> ROUTINE_T_U;
            case 2 -> ROUTINE_M_U;
            default -> ROUTINE_S_U;
        };
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 20.0F);
        guiGraphics.blit(SHEET, buttonX + ROUTINE_ICON_DX, buttonY + ROUTINE_ICON_DY + yOffset, u,
                ROUTINE_ICON_V, ROUTINE_ICON_SIZE, ROUTINE_ICON_SIZE, SHEET_W, SHEET_H);
        guiGraphics.pose().popPose();
    }
}
