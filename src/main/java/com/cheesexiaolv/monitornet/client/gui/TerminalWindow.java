package com.cheesexiaolv.monitornet.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.cheesexiaolv.monitornet.Logic.TerminalSettings;
import com.cheesexiaolv.monitornet.Logic.ThroughputHistory;
import com.cheesexiaolv.monitornet.network.TerminalRow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.api.client.AEKeyRendering;

/**
 * 存储监控终端的<b>浮动子窗口</b>（Mekanism 那种可拖动的窗口，但配色/字体全走 AE 米色）。
 *
 * <p>定位：<b>纯客户端 + 纯绘制</b>——不注册控件。它是"某一行自己的小面板"：
 * 槽行左边一个「定位」长按钮 + 一个 AE 凹槽显示该行监控对象，右边是名称 / 存量 / 速率，
 * 下方是这一行自己的折线图，标题栏右侧一排长按钮可以独立切该行的曲线开关 / 显示量 / 时间窗 / 单位。
 *
 * <p>⚠️ <b>坐标约定</b>：本类的 x/y 是<b>面板相对坐标</b>（与 {@code ThroughputTerminalScreen.drawFG}
 * 里的坐标系一致），传进来的鼠标坐标也按面板相对算（屏幕负责减掉 {@code leftPos/topPos}）。
 * 窗口<b>可以拖到面板之外</b>：坐标允许为负、也允许超出面板，{@code GuiGraphics#fill} /
 * {@code GuiGraphics#drawString} 由渲染层自然裁剪，不需要额外处理。
 *
 * <p>⚠️ <b>窗口按钮改的是"选中行"</b>：菜单的 {@code rowWindow/rowMetric/rowUnit} 动作都只作用于当前
 * 选中行，所以每个按钮回调都先 {@code selectRow(rowIndex)} 再发对应动作，保证改的是本窗口这一行。
 */
public class TerminalWindow {

    // ---- 尺寸（标题栏 14 + 显示槽行 20 + 图表 64 + 内边距）----
    /** 窗口总宽。 */
    public static final int WIDTH = 180;
    /** 窗口总高。 */
    public static final int HEIGHT = 112;

    /** 标题栏高度。 */
    private static final int TITLE_H = 14;
    /** 三个长按钮的 y（窗口相对）：相对左上角再下移 3px。 */
    private static final int TITLE_BTN_Y = 3;
    /** 长按钮 26×14（精灵表 {@link WidgetSprites}）；按下时整体下移 1px，见 WidgetSprites。 */
    private static final int WIDE_BTN_W = WidgetSprites.LONG_W;
    private static final int WIDE_BTN_H = WidgetSprites.LONG_H;
    /** 长按钮从窗口左边缘算起的第一个 x。 */
    private static final int LONG_BTN_X = 3;
    /** 关闭键：短按钮 14×14 + 关闭图标；比长按钮低 3px（用户要求"叉叉下移 3 格"）。 */
    private static final int CLOSE_BTN_SIZE = WidgetSprites.SHORT_W;
    private static final int CLOSE_BTN_Y = 3;
    /** 「定位」短按钮：在关闭键左侧，中间空 2px（关闭键左边 163 → 定位右边 161 → x=147）。 */
    private static final int LOCATE_BTN_X = 147;
    private static final int LOCATE_BTN_Y = 3;
    /** 按钮之间的间隔。 */
    private static final int BTN_GAP = 2;
    /** 按钮与窗口右内边距的距离（用户要求整体右移 1px：4 → 3）。 */
    private static final int BTN_RIGHT_PAD = 3;
    /** 显示槽边长。 */
    private static final int SLOT_SIZE = 18;
    /**
     * 显示槽行的 x 起点与文字起点。
     *
     * <p>用户要求图标左沿对齐图表<b>外框</b>左沿：图表画在 {@code x+10}、外框再往外 1px = {@code x+9}；
     * 图标贴在槽内 +1px，所以槽 = 8、图标 = 9、文字 = 32。
     */
    private static final int SLOT_ROW_X = 9;
    private static final int SLOT_TEXT_X = 33;
    /** 显示槽行**高度（唯一真源）**：槽框 = 它 - 2，槽行 x 外的所有纵向位置都由它推导。 */
    private static final int SLOT_ROW_H = SLOT_SIZE + 2;
    /** 显示槽行**顶部 y**（标题栏下沿 +3）：槽、命中框、曲线区都以它为基准。 */
    private static final int SLOT_ROW_Y = TITLE_H + 3;
    /** 图表区尺寸。 */
    private static final int CHART_W = 160;
    private static final int CHART_H = 64;

    /** 拖动时至少留在屏幕里的宽度 / 高度（保证窗口还抓得住、能拖回来）。 */
    private static final int DRAG_KEEP_W = 24;
    private static final int DRAG_KEEP_H = 16;

    /**
     * 窗口底图（画布 256×256，图案在左上角 180×112，1:1 贴）。
     *
     * <p>1.20.1 的 {@code GuiGraphics#blit} 必须显式带贴图（{@code RenderSystem.setShaderTexture}
     * + 无参 {@code GuiComponent.blit} 那条路已随 {@code GuiComponent} 一起消失），所以把路径提成常量。
     */
    private static final ResourceLocation TEX_WINDOW =
            ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/ae_monitornet_window.png");

    // ---- AE 调色板（面板底/标题栏/槽框已改由贴图 ae_monitornet_window.png 绘制，这里只剩按钮与文字用的颜色）----
    /** 内下/右暗边（无数据提示的描边用它）。 */
    private static final int COL_SHADOW = 0xFF555555;
    /** 标题文字 / 按钮字符。 */
    private static final int COL_TITLE = 0xFF404040;
    /** 正文文字 / 暗一档的提示文字。 */
    private static final int COL_TEXT = 0xFF3C3C3C;
    private static final int COL_TEXT_DIM = 0xFF7A7A7A;
    /** 速率正负色。 */
    private static final int COL_POS = 0xFF1B7A1B;
    private static final int COL_NEG = 0xFFA02828;
    /** 无数据提示的底色（与折线图内部一致）。 */
    private static final int COL_HINT_BG = 0xFF0C0C0C;

    // ---- 位置与状态 ----
    /** 窗口左上角（面板相对坐标；可以为负 / 超出面板 —— 允许拖到面板外）。 */
    public int x;
    public int y;
    /** 本窗口盯着的行序号（打开时固定：列表滚动/翻页不会改变它盯哪一行）。 */
    public final int rowIndex;
    /** 是否正在被拖动。 */
    public boolean dragging;
    /** 拖动锚点：按下时鼠标与窗口左上角的偏移（拖动时保持这个偏移 → 跟手）。 */
    public double dragX;
    public double dragY;

    /** 标题栏按钮（布局固定，构造时建一次）。 */
    private final List<WindowButton> buttons = new ArrayList<>();

    /** 关闭键左侧的「定位」短按钮（坐标单独算，不参与长按钮的流式排布）。 */
    private final WindowButton locateButton;

    /** 关闭键本身（两个短按钮宽度相同，靠这个引用区分）。 */
    private final WindowButton closeButton;

    /** 当前被按住的按钮（按住时画"按下"帧；松开时指针仍在键内才触发动作）。 */
    @Nullable
    private WindowButton pressedButton;
    /** 点关闭按钮时的回调（屏幕负责把自己从窗口列表里摘掉）。 */
    private final Consumer<TerminalWindow> closeAction;

    /** 已经点过关闭（等屏幕把它摘掉）。 */
    private boolean closeRequested;

    /**
     * @param rowIndex    本窗口对应的行序号
     * @param startX      开窗时的 x（面板相对）
     * @param startY      开窗时的 y（面板相对，负值会被抬到 0）
     * @param closeAction 点关闭按钮时的回调（屏幕负责把自己从窗口列表里摘掉）
     */
    public TerminalWindow(int rowIndex, int startX, int startY, Consumer<TerminalWindow> closeAction) {
        this.rowIndex = rowIndex;
        this.x = startX;
        this.y = Math.max(0, startY);
        this.closeAction = closeAction;

        // 标题栏按钮（从右往左排：× 方块 → 位 → 窗 → 量）。
        // 文字只写"当前值"（AE 风格短标），全称放悬停提示里；都改的是"选中行"，
        // 所以先 selectRow(rowIndex) 再发动作。
        this.buttons.add(new WindowButton("gui.ae_monitornet.tip.metric", WIDE_BTN_W, false,
                (row, window) -> metricLabel(row),
                (screen, window) -> {
                    screen.selectRow(window.rowIndex);
                    screen.cycleRowMetric();
                    screen.requestRowSeries(window.rowIndex);
                }));
        this.buttons.add(new WindowButton("gui.ae_monitornet.tip.window", WIDE_BTN_W, false,
                (row, window) -> windowLabel(row),
                (screen, window) -> {
                    screen.selectRow(window.rowIndex);
                    screen.cycleRowWindow();
                    screen.requestRowSeries(window.rowIndex);
                }));
        this.buttons.add(new WindowButton("gui.ae_monitornet.tip.fluid", WIDE_BTN_W, true,
                (row, window) -> unitLabel(row),
                (screen, window) -> {
                    screen.selectRow(window.rowIndex);
                    screen.toggleRowUnit();
                    screen.requestRowSeries(window.rowIndex);
                }));
        this.closeButton = new WindowButton("gui.ae_monitornet.window.close", CLOSE_BTN_SIZE, false,
                (row, window) -> Component.literal(""),
                (screen, window) -> window.requestClose());
        this.buttons.add(this.closeButton);

        // 「定位」：短按钮 + 定位图标，固定在关闭键左侧 2px 处；没有来源时置灰不可点。
        this.locateButton = new WindowButton("gui.ae_monitornet.window.tip.locate", CLOSE_BTN_SIZE, false,
                (row, window) -> Component.literal(""),
                (screen, window) -> screen.locateRow(window.rowIndex));
    }

    // ------------------------------------------------------------------
    // 绘制
    // ------------------------------------------------------------------

    /**
     * 画整个窗口。
     *
     * <p>调用前 pose 已被 vanilla 平移到面板左上角（见类注释的坐标约定），所以这里直接用面板相对坐标；
     * 拖到面板外时坐标为负 / 超出面板，交给渲染层裁剪。四角保持直角（不做削角）。
     *
     * @param mouseX 面板相对鼠标 x
     * @param mouseY 面板相对鼠标 y
     */
    public void draw(GuiGraphics guiGraphics, Font font, ThroughputTerminalScreen<?> screen,
            int mouseX, int mouseY) {
        int w = WIDTH;
        int h = HEIGHT;

        // 面板底图：来自贴图 guis/ae_monitornet_window.png（画布 256×256，图案在左上角 180×112，1:1 贴）
        guiGraphics.blit(TEX_WINDOW, x, y, 0, 0, w, h, 256, 256);

        TerminalRow row = rowOf(screen);

        drawSlotRow(guiGraphics, font, screen, row);

        // 曲线常显（「曲线 / 曲线×」按钮已按用户要求删掉）
        drawChart(guiGraphics, font, screen, row, mouseX, mouseY);

        // ⚠️ 按钮最后画（压在标题栏与槽行之上），而字符又是按钮里最后画的 —— 否则会被底/描边盖住
        drawButtons(guiGraphics, font, mouseX, mouseY, row);
    }

    /**
     * 标题栏按钮：贴图底（{@link WidgetSprites}）+ 文字。
     *
     * <p>按下帧比常态少 1 行，所以整体下移 1px 贴（"按下去"而不是"底边抬起来"），文字也 +1。
     */
    private void drawButtons(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY,
            @Nullable TerminalRow row) {
        for (int i = 0; i < this.buttons.size(); i++) {
            WindowButton button = this.buttons.get(i);
            if (!button.visible(row)) {
                continue;
            }
            int bx = buttonX(i);
            int by = y + buttonY(button);
            boolean hovered = isInside(mouseX, mouseY, bx, by, button.width(), button.height());
            int state = button == this.pressedButton ? WidgetSprites.STATE_PRESSED
                    : hovered ? WidgetSprites.STATE_HOVER : WidgetSprites.STATE_NORMAL;
            if (button == this.closeButton) {
                WidgetSprites.drawButtonWithIcon(guiGraphics, state, bx, by,
                        WidgetSprites.ICON_CLOSE_U, WidgetSprites.ICON_CLOSE_V,
                        WidgetSprites.ICON_CLOSE_W, WidgetSprites.ICON_CLOSE_H,
                        WidgetSprites.ICON_CLOSE_DX, WidgetSprites.ICON_CLOSE_DY);
            } else {
                WidgetSprites.drawButton(guiGraphics, true, state, bx, by);
                drawButtonLabel(guiGraphics, font, button, row, bx, by, state == WidgetSprites.STATE_PRESSED);
            }
        }

        // 「定位」：关闭键左侧 2px；没有来源时压暗且不可点
        boolean canLocate = row != null && !row.sources().isEmpty();
        int lx = x + LOCATE_BTN_X;
        int ly = y + LOCATE_BTN_Y;
        boolean locateHovered = canLocate
                && isInside(mouseX, mouseY, lx, ly, CLOSE_BTN_SIZE, CLOSE_BTN_SIZE);
        int locateState = this.pressedButton == this.locateButton ? WidgetSprites.STATE_PRESSED
                : locateHovered ? WidgetSprites.STATE_HOVER : WidgetSprites.STATE_NORMAL;
        WidgetSprites.drawButtonWithIcon(guiGraphics, locateState, lx, ly,
                WidgetSprites.ICON_LOCATE_U, WidgetSprites.ICON_LOCATE_V,
                WidgetSprites.ICON_LOCATE_W, WidgetSprites.ICON_LOCATE_H,
                WidgetSprites.ICON_LOCATE_DX, WidgetSprites.ICON_LOCATE_DY);
        if (!canLocate) {
            guiGraphics.fill(lx, ly, lx + CLOSE_BTN_SIZE, ly + CLOSE_BTN_SIZE, 0x66000000);
        }
    }

    /**
     * 长按钮上的文字：水平居中、垂直居中；按下时跟着按钮 +1。
     *
     * <p>宽度超过 {@code 按钮宽 - 4} 就截断加 {@code …}，绝不越界压到隔壁按钮。
     */
    private void drawButtonLabel(GuiGraphics guiGraphics, Font font, WindowButton button,
            @Nullable TerminalRow row, int bx, int by, boolean pressed) {
        Component label = button.label(row, this);
        int maxTextW = Math.max(1, button.width() - 4);
        if (font.width(label) > maxTextW) {
            label = Component.literal(font.plainSubstrByWidth(label.getString(), maxTextW - 4) + "…");
        }
        int textW = font.width(label);
        int textX = bx + (button.width() - textW) / 2;
        textX = Math.max(bx + 1, Math.min(textX, bx + button.width() - 1 - textW));
        int textY = by + (button.height() - 8) / 2 + (pressed ? 1 : 0);
        guiGraphics.drawString(font, label, textX, textY, COL_TITLE, false);
    }

    /** 显示槽行：左边「定位」按钮 + AE 凹槽（18×18），右边名称 / 存量 / 速率。 */
    private void drawSlotRow(GuiGraphics guiGraphics, Font font, ThroughputTerminalScreen<?> screen,
            @Nullable TerminalRow row) {
        int slotX = x + SLOT_ROW_X;
        int slotY = y + SLOT_ROW_Y;

        // 槽框（外亮边 / 底色 / 上左暗沿）已删除：这一块由贴图 ae_monitornet_window.png 绘制，
        // 代码只把物品图标画在槽内（下面 drawInGui），不再画任何底/边。

        int textX = x + SLOT_TEXT_X;
        int textRight = x + WIDTH - 5;
        if (row == null) {
            guiGraphics.drawString(font, Component.translatable("gui.ae_monitornet.window.no_history"),
                    textX, slotY + 7, COL_TEXT_DIM, false);
            return;
        }

        // 图标：只有该 key 类型注册了渲染处理器才画（判断方式与列表行一致）
        if (AEKeyRendering.get(row.key().getType()) != null) {
            // 图标在槽内的偏移：x 留 1px，y 下移 4px（与贴图里的槽框对齐；曾偏上 3px）
            AEKeyRendering.drawInGui(Minecraft.getInstance(), guiGraphics, slotX + 1, slotY + 4,
                    row.key());
        }

        String name = row.key().getDisplayName().getString();
        String clipped = font.plainSubstrByWidth(name, Math.max(0, textRight - textX - 6));
        if (!clipped.equals(name)) {
            clipped = clipped + "…";
        }
        guiGraphics.drawString(font, clipped, textX, slotY + 3, row.hasHistory() ? COL_TEXT : COL_TEXT_DIM, false);

        String amount = ThroughputTerminalScreen.amountText(row);
        guiGraphics.drawString(font, amount, textRight - font.width(amount), slotY + 13, COL_TEXT, false);

        String rate = ThroughputTerminalScreen.rowRateText(row);
        double perRoutine = row.ratePerTick() * TerminalSettings.routineTicks(row.routineOrdinal());
        int rateColor = perRoutine > 0 ? COL_POS : perRoutine < 0 ? COL_NEG : COL_TEXT_DIM;
        guiGraphics.drawString(font, rate, textX, slotY + 13, rateColor, false);
    }

    /**
     * 图表区：<b>优先画服务端算好的那条曲线</b>。
     *
     * <p>为什么不用客户端自记那份：刚开窗时它只有一两个样本，直接画会从 0 拉出一条夸张的斜线。
     * 服务端那份（{@code screen.seriesFor}）是完整采样历史，开窗即准；拿不到才退回客户端自记兜底。
     *
     * <p>鼠标坐标是<b>面板相对</b>的（与图表坐标同一套），悬停层用它吸附采样点并画浮窗。
     */
    private void drawChart(GuiGraphics guiGraphics, Font font, ThroughputTerminalScreen<?> screen,
            @Nullable TerminalRow row, int mouseX, int mouseY) {
        int chartX = x + 10;
        int chartY = y + SLOT_ROW_Y + SLOT_ROW_H + 4;
        if (chartY + CHART_H > y + HEIGHT) {
            return;
        }

        ThroughputHistory.Series series = row == null ? null : screen.seriesFor(this.rowIndex);
        if (row == null || !row.hasHistory() || series == null || series.values().length < 2) {
            drawHint(guiGraphics, font, chartX, chartY, CHART_W, CHART_H,
                    Component.translatable("gui.ae_monitornet.window.no_history"));
            return;
        }

        // 坐标轴的时间刻度 / 单位都用"这一行自己的档位"（与服务端算曲线时用的是同一套设置）
        ThroughputHistory.Window windowValue = ThroughputHistory.Window.byOrdinal(row.windowOrdinal());
        ThroughputHistory.Metric metric = ThroughputHistory.Metric.byOrdinal(row.metricOrdinal());
        ThroughputChart.draw(guiGraphics, font, chartX, chartY, CHART_W, CHART_H, series,
                windowValue.seconds, ThroughputTerminalScreen.unitText(row, metric),
                metric != ThroughputHistory.Metric.AMOUNT,
                screen.hoverContext(mouseX, mouseY));
    }

    /** 没有曲线数据时的灰字提示（自带深色底，与折线图观感一致）。 */
    private void drawHint(GuiGraphics guiGraphics, Font font, int hx, int hy, int hw, int hh, Component text) {
        guiGraphics.fill(hx - 1, hy - 1, hx + hw + 1, hy + hh + 1, COL_SHADOW);
        guiGraphics.fill(hx, hy, hx + hw, hy + hh, COL_HINT_BG);
        // 1.19.2 这里传的是浮点 x（居中）；1.20.1 的 drawString 只吃 int，
        // 用 pose 平移保住同一个浮点位置，避免居中出现 ±1px 的偏差。
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(hx + (hw - font.width(text)) / 2f, hy + hh / 2 - 4, 0f);
        guiGraphics.drawString(font, text, 0, 0, COL_TEXT_DIM, false);
        guiGraphics.pose().popPose();
    }

    // ------------------------------------------------------------------
    // 交互（全部按面板相对坐标判断）
    // ------------------------------------------------------------------

    /**
     * 鼠标按下：点关闭按钮 → 关窗并返回 false（窗口已经没了，屏幕不必再置顶/拖动）；点其它按钮 → 触发回调；
     * <b>点在按钮与显示槽之外 → 从任意位置开始拖动</b>（Mek 的"拖整窗"策略，比只拖标题栏自然）。
     *
     * @return true 表示事件已被本窗口消费（屏幕不应再传给列表 / 滚动条 / 档位按钮）
     */
    public boolean mouseClicked(double mx, double my, ThroughputTerminalScreen<?> screen) {
        if (!isMouseOver(mx, my)) {
            return false;
        }
        TerminalRow row = rowOf(screen);

        WindowButton hit = buttonAt(mx, my, row);
        if (hit != null) {
            // 所有按钮都是"按下记状态、松开触发"——这样才看得到贴图的按下帧
            this.pressedButton = hit;
            return true;
        }
        // 按钮之外：整窗任意位置都能拖（显示槽区域除外，那里留给图标）
        if (!isOverSlot(mx, my)) {
            this.dragging = true;
            this.dragX = mx - x;
            this.dragY = my - y;
        }
        return true;
    }

    /**
     * 拖动：保持按下时的偏移（跟手），并且只保证"至少还能抓住"——
     * x∈[-width+24, screenWidth-24]、y∈[0, screenHeight-16]，也就是<b>可以拖出面板</b>。
     *
     * @param screenWidth  屏幕宽（屏幕层传 {@code this.width}）
     * @param screenHeight 屏幕高（屏幕层传 {@code this.height}）
     */
    public void mouseDragged(double mx, double my, int screenWidth, int screenHeight) {
        if (!this.dragging) {
            return;
        }
        int newX = (int) (mx - this.dragX);
        int newY = (int) (my - this.dragY);
        this.x = clampTo(newX, -WIDTH + DRAG_KEEP_W, screenWidth - DRAG_KEEP_W);
        this.y = clampTo(newY, 0, screenHeight - DRAG_KEEP_H);
    }

    /**
     * 松开鼠标：结束拖动；如果之前按住了某个按钮，只有在指针仍落在同一个键上时才触发它的动作。
     *
     * @param mx     面板相对鼠标 x
     * @param my     面板相对鼠标 y
     * @param screen 用来取当前行（按钮可见性依赖行数据）
     */
    public void mouseReleased(double mx, double my, ThroughputTerminalScreen<?> screen) {
        this.dragging = false;
        WindowButton pressed = this.pressedButton;
        this.pressedButton = null;
        if (pressed != null && buttonAt(mx, my, rowOf(screen)) == pressed) {
            pressed.action().accept(screen, this);
        }
    }

    /** 滚轮落在窗口内就吃掉事件（返回 true = 不让列表跟着滚）。 */
    public boolean mouseScrolled(double mx, double my) {
        return isMouseOver(mx, my);
    }

    public boolean isMouseOver(double mx, double my) {
        return isInside(mx, my, x, y, WIDTH, HEIGHT);
    }

    /** 请求关窗：回调让屏幕把自己从窗口列表里摘掉，并记下"我已经关了"。 */
    public void requestClose() {
        this.closeRequested = true;
        this.closeAction.accept(this);
    }

    /** 是否已经请求关闭。 */
    public boolean isCloseRequested() {
        return this.closeRequested;
    }

    /** 是否有按钮正被按住（屏幕在 mouseReleased 里用它决定要不要把事件吃掉）。 */
    public boolean isPressing() {
        return this.pressedButton != null;
    }

    /**
     * 鼠标悬停在按钮上时的提示文本（屏幕层用它画 tooltip；没有悬停返回 null）。
     *
     * @param mouseX 面板相对鼠标 x
     * @param mouseY 面板相对鼠标 y
     */
    @Nullable
    public Component hintAt(double mouseX, double mouseY, ThroughputTerminalScreen<?> screen) {
        TerminalRow row = rowOf(screen);
        if (isInside(mouseX, mouseY, x + LOCATE_BTN_X, y + LOCATE_BTN_Y, CLOSE_BTN_SIZE, CLOSE_BTN_SIZE)) {
            return this.locateButton.hint();
        }
        WindowButton button = buttonAt(mouseX, mouseY, row);
        return button == null ? null : button.hint();
    }

    // ------------------------------------------------------------------
    // 按钮文字（AE 风格：按钮上只写"当前值/功能"，全称在悬停提示里）
    // ------------------------------------------------------------------

    /** 「定位」按钮已移到总终端列表行（见 {@code ThroughputTerminalScreen}）。 */

    /**
     * 显示量：储量 / 流速。
     *
     * <p>加速度已从界面撤掉（枚举还在，但服务端下发的行数据里已经把 ACCELERATION 归一成 RATE，
     * 见 {@code ThroughputTerminalMenu.displayMetric}），所以这里理论上只会出现前两种。
     */
    private static Component metricLabel(@Nullable TerminalRow row) {
        if (row == null) {
            return Component.literal("-");
        }
        return switch (ThroughputHistory.Metric.byOrdinal(row.metricOrdinal())) {
            case AMOUNT -> Component.translatable("gui.ae_monitornet.window.btn.metric.storage");
            default -> Component.translatable("gui.ae_monitornet.window.btn.metric.flow");
        };
    }

    /** 时间窗：直接用枚举自带的短标（10s / 1m / 10m / 60m）——这是"值"，不改。 */
    private static Component windowLabel(@Nullable TerminalRow row) {
        return Component.literal(
                row == null ? "-" : ThroughputHistory.Window.byOrdinal(row.windowOrdinal()).label);
    }

    /** 大小单位：mB / B。 */
    private static Component unitLabel(@Nullable TerminalRow row) {
        return Component.literal(row == null ? "-" : (row.largeUnit() ? "B" : "mB"));
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 本窗口对应的行（行数变少/结构变了会返回 null → 绘制与图表都要容错）。 */
    @Nullable
    private TerminalRow rowOf(ThroughputTerminalScreen<?> screen) {
        var rows = screen.rows();
        return this.rowIndex >= 0 && this.rowIndex < rows.size() ? rows.get(this.rowIndex) : null;
    }

    /** 鼠标是否落在显示槽上（槽位区域不触发拖动）。 */
    private boolean isOverSlot(double mx, double my) {
        return isInside(mx, my, x + SLOT_ROW_X, y + SLOT_ROW_Y, SLOT_SIZE, SLOT_SIZE);
    }

    /** 按钮的 y（窗口相对）：长按钮贴顶，关闭键比长按钮低 3px。 */
    private int buttonY(WindowButton button) {
        return button == this.closeButton ? CLOSE_BTN_Y : TITLE_BTN_Y;
    }

    /**
     * 第 i 个按钮的 x（面板相对）：
     * 长按钮从 {@link #LONG_BTN_X} 起从左往右流式排布；关闭键贴右内边距。
     */
    private int buttonX(int index) {
        WindowButton button = this.buttons.get(index);
        if (button == this.closeButton) {
            return x + WIDTH - BTN_RIGHT_PAD - CLOSE_BTN_SIZE;
        }
        return x + LONG_BTN_X + index * (WIDE_BTN_W + BTN_GAP);
    }

    /** 鼠标落在哪个按钮上（null = 没有；「位」在当前行没有单位可选时不算命中）。 */
    @Nullable
    private WindowButton buttonAt(double mx, double my, @Nullable TerminalRow row) {
        // 「定位」：只有本行有来源时才可点
        if (row != null && !row.sources().isEmpty()
                && isInside(mx, my, x + LOCATE_BTN_X, y + LOCATE_BTN_Y, CLOSE_BTN_SIZE, CLOSE_BTN_SIZE)) {
            return this.locateButton;
        }
        for (int i = 0; i < this.buttons.size(); i++) {
            WindowButton button = this.buttons.get(i);
            if (!button.visible(row)) {
                continue;
            }
            if (isInside(mx, my, buttonX(i), y + buttonY(button), button.width(), button.height())) {
                return button;
            }
        }
        return null;
    }

    private static boolean isInside(double mx, double my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    private static int clampTo(int value, int min, int max) {
        int lo = Math.min(min, max);
        int hi = Math.max(min, max);
        return Math.max(lo, Math.min(hi, value));
    }

    /**
     * 窗口里的按钮（标题栏的和槽行的共用这个结构）。
     *
     * @param langKey  悬停提示的本地化键
     * @param width    按钮宽度（长按钮 25，关闭键 12，定位 30）
     * @param unitOnly 是否只在"有大小单位之分"的行（流体等）上出现
     * @param label    按钮文字（只写当前值/功能，AE 风格短标；可翻译文本）
     * @param action   点击行为
     */
    private record WindowButton(String langKey, int width, boolean unitOnly,
            LabelSource label, BiConsumer<ThroughputTerminalScreen<?>, TerminalWindow> action) {

        int height() {
            return this.width <= CLOSE_BTN_SIZE ? CLOSE_BTN_SIZE : WIDE_BTN_H;
        }

        Component label(@Nullable TerminalRow row, TerminalWindow window) {
            return this.label.text(row, window);
        }

        boolean visible(@Nullable TerminalRow row) {
            if (!this.unitOnly) {
                return true;
            }
            // 与菜单 hasUnitChoice 的推导一致：有单位符号且 1 单位含多个量（流体 mB/B，物品没有）
            if (row == null) {
                return false;
            }
            var type = row.key().getType();
            return type.getUnitSymbol() != null && type.getAmountPerUnit() > 1;
        }

        Component hint() {
            return Component.translatable(this.langKey);
        }
    }

    /** 按钮文字来源（要同时看行数据和窗口自己状态，例如「曲线」按钮）。 */
    @FunctionalInterface
    private interface LabelSource {

        Component text(@Nullable TerminalRow row, TerminalWindow window);
    }
}
