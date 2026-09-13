package com.cheesexiaolv.aetm.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import com.cheesexiaolv.aetm.Logic.TerminalSettings;
import com.cheesexiaolv.aetm.Logic.ThroughputHistory;
import com.cheesexiaolv.aetm.menu.ThroughputTerminalMenu;
import com.cheesexiaolv.aetm.network.TerminalRow;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.api.client.AEStackRendering;
import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.Scrollbar;
import appeng.client.gui.widgets.SettingToggleButton;

/**
 * 聚合吞吐终端的客户端界面。
 *
 * <p>版式对齐 AE2 官方终端：面板 <b>195 宽</b>（AE 标准宽度），左侧工具栏放"短/中/长"终端尺寸切换
 * （{@code Settings.TERMINAL_STYLE}，改的是可见行数），列表右侧是滚动条轨道（轨道由本屏幕自绘，
 * 滑块用 AE2 的 {@link Scrollbar}），底部 97px 玩家背包（底部块只有 175 宽，滑槽列从列表右侧凸出来）。
 *
 * <p><b>主界面只有行列式列表</b>（每行：图标 / 名称 / 存量 / 速率）。
 * 曲线不再占主界面，而是每个浮动子窗口（{@link TerminalWindow}）自己画。
 * 空行也会画出分隔线，列表底部不会空落落。
 *
 * <p>⚠️ 坐标约定：{@code AbstractContainerScreen.render} 在调用 {@code renderLabels} 之前已经把 pose
 * 平移到面板左上角（vanilla 源码 96–119 行），所以 {@link #drawFG} 里用的是<b>面板相对坐标</b>；
 * {@code offsetX/offsetY} 只用来把绝对鼠标坐标换算成相对坐标。{@link #drawBG} 没有这层平移，要自己加。
 */
public class ThroughputTerminalScreen<C extends ThroughputTerminalMenu> extends AEBaseScreen<C> {

    // ---- 面板尺寸（与贴图切块一致）----
    private static final int GUI_WIDTH = 195;
    private static final int HEADER_H = 34;
    private static final int FOOTER_H = 97;
    /** 底部背包块的宽度：原版几何里底部只到 x=174（滑槽列从列表右侧凸出来）。 */
    private static final int FOOTER_W = 176;
    /**
     * 底部按钮条高度：<b>已经是 0</b>。
     *
     * <p>「窗口」按钮与全局速率单位按钮都挪走了（开窗入口只留"双击列表行"，全局速率单位挪到头部第二行），
     * 底部条不复存在，腾出的高度全部给列表。
     */
    private static final int STRIP_H = 0;
    private static final int ROW_H = 18;

    /** 贴图里头部块的 y。 */
    private static final int TEX_HEADER_Y = 0;
    /** 贴图里底部块的 y（紧跟在头部块下面）。 */
    private static final int TEX_FOOTER_Y = 51;
    /** 头部块里一条"纯主体 + 左右边框"的横带（用来平铺补出面板中段）。 */
    private static final int TEX_BODY_Y = 20;
    private static final int TEX_BODY_H = 17;

    private static final int LIST_X = 8;
    /** 列表宽度：右边界 172，滚动条轨道在 174..187（JSON 里 left=175）。 */
    private static final int LIST_W = 164;
    private static final int SCROLLBAR_X = 175;
    private static final int TRACK_W = 14;

    // ---- 行内列位置（面板相对）----
    private static final int ICON_X = LIST_X + 2;
    private static final int NAME_X = LIST_X + 20;
    /** 名称列可用宽度（超出截断加省略号）。 */
    private static final int NAME_W = 54;
    /** 存量列右边界。 */
    private static final int AMOUNT_RIGHT = 126;
    /** 速率列右边界（列表内右边缘）。 */
    private static final int RATE_RIGHT = LIST_X + LIST_W - 1;

    // ---- 头部第二行的全局速率单位按钮（底部条已删除）----
    /** 头部第二行的 y：标题在 y=6，这一行占 18..31，仍在 HEADER_H=34 之内（y=32/33 是头部块下沿）。 */
    private static final int GLOBAL_BTN_Y = 18;
    private static final int GLOBAL_BTN_W = 34;
    private static final int GLOBAL_BTN_H = 14;

    /** 双击列表行的判定窗口（毫秒）。 */
    private static final long DOUBLE_CLICK_MS = 250L;

    private static final int COL_TRACK_BODY = 0xFF8B8B8B;
    private static final int COL_TRACK_DARK = 0xFF373737;
    private static final int COL_TRACK_LIGHT = 0xFFFFFFFF;
    private static final int COL_TEXT = 0xFF3C3C3C;
    private static final int COL_TEXT_DIM = 0xFF7A7A7A;
    private static final int COL_POS = 0xFF1B7A1B;
    private static final int COL_NEG = 0xFFA02828;
    private static final int COL_SELECT = 0x66A8C8FF;
    private static final int COL_HOVER = 0x33FFFFFF;
    private static final int COL_SEP = 0xFFB4B4B4;
    private static final int COL_LIST_BG = 0xFFE0E0E0;
    private static final int COL_LIST_DARK = 0xFF373737;

    private Scrollbar scrollbar;

    /** 头部第二行的"全局速率单位"按钮（底部条已删除）。 */
    private FlatButton globalRoutineButton;

    /**
     * 浮动子窗口（Mek 那种可拖动小窗）。
     *
     * <p><b>列表顺序即层序</b>：末尾在最上层；点击置顶 = 先 remove 再 add。
     */
    private final List<TerminalWindow> windows = new ArrayList<>();

    /** 面板总高（init 时定，窗口拖动 clamp 用）。 */
    private int panelHeight = HEADER_H + FOOTER_H;

    /** 双击列表行的判定：上次点的是哪一行 / 什么时候点的。 */
    private int lastClickRow = -1;
    private long lastClickTime;

    /** 子窗口曲线的刷新节流（间隔由服务端随全量包下发，见 {@code menu.chartRefreshIntervalTicks()}）。 */
    private int seriesRequestCooldown;

    private int lastRowCount = -1;

    /** 可见行数（由 AE 的终端尺寸档位决定，短/中/长）。 */
    private int visibleRows = 4;

    /** 刚点击的尺寸档位（-1 = 用菜单同步过来的值）。 */
    private int styleOverride = -1;

    public ThroughputTerminalScreen(C menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.imageWidth = GUI_WIDTH;
        // 滚动条按样式 JSON 的 widgets.scrollbar 定位（必须在 init() 之前注册）
        this.scrollbar = this.widgets.addScrollBar("scrollbar");

        // AE 终端的"灵魂"：左侧工具栏的短/中/长终端尺寸切换。
        //
        // ⚠️⚠️ 必须在**构造函数**里加，不能放到 init() 里 —— 这是那个按钮"消失"的根因：
        //   AE2 的 AEBaseScreen.init() 会调用 widgets.populateScreen(...)，
        //   而 VerticalButtonBar.populateScreen(...) 才是把工具栏按钮 addRenderableWidget 进屏幕的地方；
        //   如果第一次 init() 时工具栏还是空的（按钮在 init() 里 super.init() **之后**才加进去），
        //   那一次就没有任何 renderable 被登记，按钮就成了一个"永远不会被画、也点不到"的孤儿
        //   （reinitialize()/窗口缩放会再跑 init()，那时才会补上）。
        //   AE2 自己的 PatternAccessTermScreen 也是在构造函数里 addToLeftToolbar 的。
        this.addToLeftToolbar(new SettingToggleButton<>(
                Settings.TERMINAL_STYLE, currentStyle(), this::toggleTerminalStyle));
    }

    @Override
    protected void init() {
        // imageHeight 必须先于 super.init() 定好（vanilla 用它算 topPos）
        this.visibleRows = computeVisibleRows();
        this.imageHeight = HEADER_H + this.visibleRows * ROW_H + STRIP_H + FOOTER_H;
        this.panelHeight = this.imageHeight;

        super.init();

        this.scrollbar.setHeight(listHeight() - 4);

        // 全局速率单位：挪到头部第二行（左对齐 x=8，34×14），按钮上只写当前值
        this.globalRoutineButton = addRenderableWidget(new FlatButton(
                this.leftPos + LIST_X, this.topPos + GLOBAL_BTN_Y, GLOBAL_BTN_W, GLOBAL_BTN_H,
                Component.empty(), button -> this.menu.sendSetAllRoutines(nextGlobalRoutine())));

        updateScrollbar();
    }

    /** 当前尺寸档位：点击后先本地生效（同步要等一个周期），否则 reinitialize 会用到旧值。 */
    private TerminalStyle currentStyle() {
        int ordinal = this.styleOverride >= 0 ? this.styleOverride : this.menu.terminalStyle;
        TerminalStyle[] values = TerminalStyle.values();
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }

    /** 终端尺寸档位 → 可见行数（短/中/长，公式同 AE2 的 TerminalStyle.getRows）。 */
    private int computeVisibleRows() {
        // STRIP_H 现在恒为 0（底部条已删）——保留这一项只是为了让"底部条如果回来"时行数能自动跟着变
        int available = this.height - HEADER_H - FOOTER_H - STRIP_H - 20;
        int maxRows = Math.max(1, available / ROW_H);
        int rows = currentStyle().getRows(maxRows);
        return Math.max(1, Math.min(rows, maxRows));
    }

    /** 列表区高度。 */
    private int listHeight() {
        return this.visibleRows * ROW_H;
    }

    private void toggleTerminalStyle(SettingToggleButton<TerminalStyle> button, boolean backwards) {
        TerminalStyle next = button.getNextValue(backwards);
        button.set(next);
        this.styleOverride = next.ordinal();
        this.menu.sendSetTerminalStyle(next.ordinal());
        reinitialize();
    }

    /** 改完尺寸档位后重建界面（清掉旧的控件再跑一次 init）。 */
    private void reinitialize() {
        this.children().removeAll(this.renderables);
        this.renderables.clear();
        this.init();
    }

    // ------------------------------------------------------------------
    // 背景：头部块 + 中段平铺 + 底部块（绝对坐标系，要自己加 offset）
    // ------------------------------------------------------------------

    @Override
    public void drawBG(PoseStack poseStack, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(poseStack, offsetX, offsetY, mouseX, mouseY, partialTicks);

        this.bindTexture("guis/aetm_terminal.png");
        blit(poseStack, offsetX, offsetY, 0, TEX_HEADER_Y, GUI_WIDTH, HEADER_H);
        // 列表下沿 = 底部块的上沿：STRIP_H 现在是 0（底部条已删），所以就是 头部 + 列表
        int bottom = HEADER_H + listHeight() + STRIP_H;
        // ⚠️ 底部背包块只有 176 宽（原版几何：头部/列表 195 宽，底部到 x=175，滑槽列从列表右侧凸出来）
        blit(poseStack, offsetX, offsetY + bottom, 0, TEX_FOOTER_Y, FOOTER_W, FOOTER_H);

        // 面板中段：用头部块里的一条"纯主体 + 左右边框"横带平铺，保证左右边框连续
        int y = HEADER_H;
        while (y < bottom) {
            int h = Math.min(TEX_BODY_H, bottom - y);
            blit(poseStack, offsetX, offsetY + y, 0, TEX_BODY_Y, GUI_WIDTH, h);
            y += h;
        }

        // 悬挑段（滑槽列右边那截，x=176..194）在阶梯处收口：
        // 原版 AE 终端这里是 2 行 #555555 + 1 行 #000000（贴图里没法烘，因为中段横带要能竖向平铺）
        GuiComponent.fill(poseStack, offsetX + FOOTER_W, offsetY + bottom - 3,
                offsetX + GUI_WIDTH, offsetY + bottom - 1, 0xFF555555);
        GuiComponent.fill(poseStack, offsetX + FOOTER_W, offsetY + bottom - 1,
                offsetX + GUI_WIDTH, offsetY + bottom, 0xFF000000);

        // ⚠️ 列表凹槽 + 滚动条轨道必须画在 drawBG（widget 层之前）：
        //    AE2 的 Scrollbar 是在 widget 前景层画滑块的，画在 drawFG 里会把滑块整个盖掉。
        //    drawBG 没有 pose 平移，自己加 offsetX/offsetY。
        poseStack.pushPose();
        poseStack.translate(offsetX, offsetY, 0);
        drawListFrame(poseStack);
        drawScrollbarTrack(poseStack);
        poseStack.popPose();
    }

    // ------------------------------------------------------------------
    // 前景：列表行 + 浮动子窗口（pose 已平移到面板左上角 → 面板相对坐标）
    // ------------------------------------------------------------------

    @Override
    public void drawFG(PoseStack poseStack, int offsetX, int offsetY, int mouseX, int mouseY) {
        // offsetX/offsetY 是 leftPos/topPos：只用来把绝对鼠标坐标换算成面板相对坐标
        int mx = mouseX - offsetX;
        int my = mouseY - offsetY;

        // 主界面永远是行列式列表（曲线只在浮动子窗口里看）
        drawRows(poseStack, mx, my, mouseX, mouseY);

        // 浮动子窗口画在主界面之后（列表 / 滚动条 / 档位按钮之上）；坐标同样是面板相对坐标
        drawWindows(poseStack, mx, my);

        // 头部第二行按钮的悬停提示（1.19.2 的 AbstractWidget 没有 setTooltip，所以屏幕自己画）
        if (this.globalRoutineButton != null && this.globalRoutineButton.visible
                && mx >= LIST_X && mx < LIST_X + GLOBAL_BTN_W
                && my >= GLOBAL_BTN_Y && my < GLOBAL_BTN_Y + GLOBAL_BTN_H) {
            drawClampedTooltip(poseStack, mouseX, mouseY, routineGlobalTooltipLines());
        }
    }

    /**
     * 「全局速率单位」按钮的悬停提示：<b>拆成两行</b>。
     *
     * <p>原来是一整句（"全局速率单位：t / s / m / 10m（一次改所有行）"），一行太宽，
     * 鼠标靠右时整条提示会被屏幕右边界切掉；拆行后宽度只由最长那一行决定。
     */
    private static List<Component> routineGlobalTooltipLines() {
        return List.of(
                Component.translatable("gui.aetm.tip.routine_global"),
                Component.translatable("gui.aetm.tip.routine_global.hint"));
    }

    /**
     * 画悬停提示，并<b>把位置收敛在屏幕内</b>（三处 tooltip 共用）。
     *
     * <p>⚠️ <b>两套坐标必须分清</b>（本项目反复强调的那个约定）：
     * <ul>
     *   <li><b>收敛要在绝对坐标里算</b>：{@code mouseX/mouseY}、{@code this.width/height} 都是屏幕绝对量，
     *       所以"首选位置 + 上限 clamp"全部在绝对空间做；</li>
     *   <li><b>绘制要用面板相对坐标</b>：本方法是在 {@code drawFG} 里被调用的，此刻 GL 原点已被 vanilla
     *       平移到面板左上角（{@code AbstractContainerScreen.render} 里
     *       {@code modelViewStack.translate(leftPos, topPos)} + {@code applyModelViewMatrix()}，
     *       直到 super.render 末尾才 pop；AE2 的 {@code renderTooltips} 是在那之后才用绝对坐标画的），
     *       所以最后必须<b>减掉面板原点</b>再交给 {@code drawTooltip}。</li>
     * </ul>
     * 上一版把绝对坐标直接喂给绘制，等于多加了一次 {@code (leftPos, topPos)}，三处提示全被推出屏幕。
     *
     * @param mouseX 绝对坐标鼠标 x
     * @param mouseY 绝对坐标鼠标 y
     * @param lines  提示内容（可为多行）
     */
    private void drawClampedTooltip(PoseStack poseStack, int mouseX, int mouseY, List<Component> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        int widest = 0;
        for (Component line : lines) {
            widest = Math.max(widest, this.font.width(line));
        }
        // 先按"鼠标右下 12px"算首选位置，再收敛到屏幕内 —— 全在绝对坐标里做
        int maxX = Math.max(4, this.width - widest - 6);
        int maxY = Math.max(4, this.height - lines.size() * 10 - 6);
        int absX = Math.max(4, Math.min(mouseX + 12, maxX));
        int absY = Math.max(4, Math.min(mouseY + 12, maxY));
        // ⚠️ 绘制发生在"面板相对"坐标系里 → 减回面板原点
        this.drawTooltip(poseStack, absX - this.leftPos, absY - this.topPos, lines);
    }

    /** 按列表顺序逐个画浮动子窗口（末尾在最上层），顺带画按钮的悬停提示。 */
    private void drawWindows(PoseStack poseStack, int mx, int my) {
        Component hint = null;
        for (TerminalWindow window : this.windows) {
            window.draw(poseStack, this.font, this, mx, my);
            Component windowHint = window.hintAt(mx, my, this);
            if (windowHint != null) {
                hint = windowHint;
            }
        }
        if (hint != null) {
            // 绝对坐标（窗口内部用的是面板相对坐标，这里补回 offset 后交给统一的收敛方法）
            drawClampedTooltip(poseStack, mx + this.leftPos, my + this.topPos, List.of(hint));
        }
    }

    /**
     * 列表行：<b>每一行可见行位都画分隔线</b>（空行也算一行，列表底部才不空落落），
     * 但只有真有数据的行才画选中/悬停底色与图标文字。
     *
     * @param mx      面板相对鼠标 x（行内高亮/命中判定用）
     * @param my      面板相对鼠标 y
     * @param mouseX  绝对鼠标 x（只有画 tooltip 才需要绝对坐标）
     * @param mouseY  绝对鼠标 y
     */
    private void drawRows(PoseStack poseStack, int mx, int my, int mouseX, int mouseY) {
        List<TerminalRow> rows = this.menu.getRows();
        int scroll = this.scrollbar.getCurrentScroll();
        int hovered = rowAt(mx, my);
        List<Component> tooltip = null;

        for (int i = 0; i < this.visibleRows; i++) {
            int index = scroll + i;
            int rowTop = HEADER_H + i * ROW_H;

            if (index < rows.size()) {
                var row = rows.get(index);

                if (index == this.menu.selectedRow) {
                    GuiComponent.fill(poseStack, LIST_X, rowTop, LIST_X + LIST_W, rowTop + ROW_H, COL_SELECT);
                } else if (index == hovered) {
                    GuiComponent.fill(poseStack, LIST_X, rowTop, LIST_X + LIST_W, rowTop + ROW_H, COL_HOVER);
                }

                drawRowContent(poseStack, row, rowTop);

                if (index == hovered) {
                    tooltip = buildRowTooltip(row);
                }
            }

            // 每行下沿的分隔线：每个可见行位都画（空行也算一行，列表底部才不空落落）。
            // 最后一行那条停在凹槽亮边之上 1px，别把凹槽的亮下沿盖掉。
            int sepY = Math.min(rowTop + ROW_H, HEADER_H + listHeight() - 1);
            GuiComponent.fill(poseStack, LIST_X + 1, sepY - 1, LIST_X + LIST_W - 1, sepY, COL_SEP);
        }

        // 列表行 tooltip：鼠标落在任一已打开的子窗口上时不画 ——
        // 否则"行 tooltip"会和子窗口里的内容叠在同一片区域，两处文字互相压。
        // 这种情况下由子窗口自己的 hintAt(...)（按钮悬停提示）接管。
        // 位置同样走 drawClampedTooltip（行 tooltip 有 4~6 行，最容易往屏幕外跑）。
        if (tooltip != null && !isMouseOverAnyWindow(mx, my)) {
            drawClampedTooltip(poseStack, mouseX, mouseY, tooltip);
        }
    }

    /**
     * 鼠标是否落在任一已打开的浮动子窗口上（取最上层那个；正在拖动的窗口当然也算）。
     *
     * @param mx 面板相对鼠标 x
     * @param my 面板相对鼠标 y
     */
    private boolean isMouseOverAnyWindow(double mx, double my) {
        return windowAt(mx, my) != null;
    }

    /** 一行里的图标 / 名称 / 存量 / 速率。 */
    private void drawRowContent(PoseStack poseStack, TerminalRow row, int rowTop) {
        if (AEStackRendering.get(row.key().getType()) != null) {
            AEStackRendering.drawInGui(Minecraft.getInstance(), poseStack,
                    ICON_X, rowTop + 1, getBlitOffset(), row.key());
        }

        String name = row.key().getDisplayName().getString();
        String clipped = this.font.plainSubstrByWidth(name, NAME_W - 6);
        if (!clipped.equals(name)) {
            clipped = clipped + "…";
        }
        this.font.draw(poseStack, clipped, NAME_X, rowTop + 5, row.hasHistory() ? COL_TEXT : COL_TEXT_DIM);

        String amount = amountText(row);
        this.font.draw(poseStack, amount, AMOUNT_RIGHT - this.font.width(amount), rowTop + 5, COL_TEXT);

        String rate = rowRateText(row);
        double perRoutine = row.ratePerTick() * TerminalSettings.routineTicks(row.routineOrdinal());
        int rateColor = perRoutine > 0 ? COL_POS : perRoutine < 0 ? COL_NEG : COL_TEXT_DIM;
        this.font.draw(poseStack, rate, RATE_RIGHT - this.font.width(rate), rowTop + 5, rateColor);
    }

    /**
     * 列表 + 滚动条轨道共用一个"凹槽"（原版 AE 终端就是这样：整块沉下去，上/左暗、下/右亮）。
     */
    private void drawListFrame(PoseStack poseStack) {
        int x1 = LIST_X - 1;
        int y1 = HEADER_H - 1;
        int x2 = SCROLLBAR_X - 1 + TRACK_W;
        int y2 = HEADER_H + listHeight() + 1;
        GuiComponent.fill(poseStack, x1, y1, x2, y2, COL_LIST_BG);
        GuiComponent.fill(poseStack, x1, y1, x2, y1 + 1, COL_LIST_DARK);       // 上沿
        GuiComponent.fill(poseStack, x1, y1, x1 + 1, y2, COL_LIST_DARK);       // 左沿
        GuiComponent.fill(poseStack, x1, y2 - 1, x2, y2, COL_TRACK_LIGHT);     // 下沿（亮）
        GuiComponent.fill(poseStack, x2 - 1, y1, x2, y2, COL_TRACK_LIGHT);     // 右沿（亮）
    }

    /**
     * 滚动条轨道：AE2 的 Scrollbar 只画滑块，轨道要自己画（官方终端是烘在贴图里的）。
     * 轨道只画上/左两条暗沿，右/下交给外层凹槽的亮沿。
     *
     * <p>⚠️ 由 {@link #drawBG} 调用（widget 层之前）——画在 drawFG 里会把滑块盖掉。
     */
    private void drawScrollbarTrack(PoseStack poseStack) {
        int x = SCROLLBAR_X - 1;
        int y = HEADER_H - 1;
        int w = TRACK_W;
        int h = listHeight() + 2;
        GuiComponent.fill(poseStack, x, y, x + w, y + h, COL_TRACK_BODY);
        GuiComponent.fill(poseStack, x, y, x + w, y + 1, COL_LIST_DARK);
        GuiComponent.fill(poseStack, x, y, x + 1, y + h, COL_LIST_DARK);
        // 轨道与列表之间补一条亮线，形成两根并列的凹槽
        GuiComponent.fill(poseStack, x - 1, y, x, y + h, COL_TRACK_LIGHT);
    }

    /** 行的悬停提示：名称 / 存量 / 速率 / 各来源监控器。 */
    private List<Component> buildRowTooltip(TerminalRow row) {
        var lines = new ArrayList<Component>();
        lines.add(row.key().getDisplayName());
        lines.add(Component.translatable("gui.aetm.terminal.tip.amount", amountText(row)));
        lines.add(Component.translatable("gui.aetm.terminal.tip.rate", rateText(row)));
        for (var source : row.sources()) {
            String prefix = source.advanced() ? "★ " : "· ";
            lines.add(Component.literal(prefix + shortDimension(source.dimension()) + " "
                    + source.pos().toShortString()));
        }
        if (!row.hasHistory()) {
            lines.add(Component.translatable("gui.aetm.terminal.tip.no_history"));
        }
        return lines;
    }

    private static String shortDimension(String dimension) {
        int idx = dimension.indexOf(':');
        return idx < 0 ? dimension : dimension.substring(idx + 1);
    }

    // ------------------------------------------------------------------
    // 单位换算（与菜单里的推导保持一致，客户端按每行自己的设置显示）
    // ------------------------------------------------------------------

    /**
     * 该行的显示单位后缀（流体 mB/B，物品为空）。
     *
     * <p>{@code static} 包可见：浮动子窗口（{@link TerminalWindow}）复用同一套换算，保证两处显示一致。
     */
    static String unitOf(TerminalRow row) {
        var type = row.key().getType();
        String symbol = type.getUnitSymbol();
        if (symbol == null) {
            return "";
        }
        boolean divisible = type.getAmountPerUnit() > 1;
        return divisible ? (row.largeUnit() ? symbol : "m" + symbol) : symbol;
    }

    /**
     * 图表纵轴的单位（与菜单里 {@code unitFor} 的推导一致）。
     *
     * <p>存量 = 单位符号；流速 = 单位/档位；加速度 = 单位/档位/秒。
     */
    static String unitText(TerminalRow row, ThroughputHistory.Metric metric) {
        String valueUnit = unitOf(row);
        String rateUnit = TerminalSettings.routineUnit(row.routineOrdinal());
        return switch (metric) {
            case AMOUNT -> valueUnit;
            case RATE -> valueUnit + "/" + rateUnit;
            case ACCELERATION -> valueUnit + "/" + rateUnit + "/s";
        };
    }

    /** 该行的数值（按大小单位换算后）。 */
    private static double displayValue(TerminalRow row, double baseValue) {
        var type = row.key().getType();
        int perUnit = Math.max(1, type.getAmountPerUnit());
        boolean divisible = type.getUnitSymbol() != null && perUnit > 1;
        return divisible && row.largeUnit() ? baseValue / perUnit : baseValue;
    }

    static String amountText(TerminalRow row) {
        return compact(displayValue(row, row.amount())) + unitOf(row);
    }

    /** 列表里的速率：紧凑数值 + "/t|s|m"，不带单位符号（单位在存量列和悬停提示里）。 */
    static String rowRateText(TerminalRow row) {
        double value = displayValue(row, row.ratePerTick() * TerminalSettings.routineTicks(row.routineOrdinal()));
        return signedCompact(value) + "/" + TerminalSettings.routineUnit(row.routineOrdinal());
    }

    /** 悬停提示里的完整速率（带单位符号）。 */
    static String rateText(TerminalRow row) {
        double value = displayValue(row, row.ratePerTick() * TerminalSettings.routineTicks(row.routineOrdinal()));
        return signedCompact(value) + unitOf(row) + "/" + TerminalSettings.routineUnit(row.routineOrdinal());
    }

    private static String signedCompact(double value) {
        double abs = Math.abs(value);
        String text = abs >= 10 ? ThroughputChart.format((float) abs) : String.format(Locale.ENGLISH, "%.2f", abs);
        String sign = value > 0 ? "+" : value < 0 ? "-" : "";
        return sign + text;
    }

    /** 紧凑数值：1.2G / 45M / 302.2K / 1234（避免大数字把列撑爆）。 */
    private static String compact(double value) {
        double abs = Math.abs(value);
        if (abs >= 1.0e9) {
            return String.format(Locale.ENGLISH, "%.1fG", value / 1.0e9);
        }
        if (abs >= 1.0e6) {
            return String.format(Locale.ENGLISH, "%.1fM", value / 1.0e6);
        }
        if (abs >= 1.0e4) {
            return String.format(Locale.ENGLISH, "%.1fK", value / 1.0e3);
        }
        return String.format(Locale.ENGLISH, "%,.0f", value);
    }

    // ------------------------------------------------------------------
    // 浮动子窗口
    // ------------------------------------------------------------------

    /**
     * 为某一行开一个浮动子窗口；该行已经有窗口就关掉它（档位条的「窗口」按钮与双击行都走这里）。
     *
     * @param rowIndex 行序号（-1 = 没有选中行，什么都不做）
     */
    private void toggleWindowFor(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= this.menu.getRows().size()) {
            return;
        }
        TerminalWindow existing = findWindow(rowIndex);
        if (existing != null) {
            closeWindow(existing);
            return;
        }
        openWindow(rowIndex);
    }

    private void openWindow(int rowIndex) {
        // 开窗位置错开：按已开窗口数偏移，避免叠在一起（构造里已经 clamp 回面板内）
        int slot = this.windows.size() % 4;
        TerminalWindow window = new TerminalWindow(rowIndex,
                8 + slot * 12, 34 + slot * 12, this::closeWindow);
        TerminalRow row = this.menu.getRows().get(rowIndex);
        window.title = row.key().getDisplayName().getString();
        this.windows.add(window);

        // 开窗立刻要一份服务端算好的曲线：客户端自记的那份刚开窗时只有一两个点，画出来是条夸张的斜线
        this.menu.sendRequestRowSeries(rowIndex);
    }

    /** 关闭并摘掉一个窗口（X 按钮与再次点「窗口」按钮都走这里）。 */
    private void closeWindow(TerminalWindow window) {
        this.windows.remove(window);
    }

    /** 该行是否已经有子窗口。 */
    @Nullable
    private TerminalWindow findWindow(int rowIndex) {
        for (TerminalWindow window : this.windows) {
            if (window.rowIndex == rowIndex) {
                return window;
            }
        }
        return null;
    }

    /** 置顶：列表末尾就是最上层（先摘掉再追加）。 */
    private void bringToFront(TerminalWindow window) {
        if (this.windows.remove(window)) {
            this.windows.add(window);
        }
    }

    /** 正在被拖动的窗口（最多只会有一个）。 */
    @Nullable
    private TerminalWindow draggingWindow() {
        for (int i = this.windows.size() - 1; i >= 0; i--) {
            if (this.windows.get(i).dragging) {
                return this.windows.get(i);
            }
        }
        return null;
    }

    /** 鼠标下的最上层窗口（从末尾往前找）。 */
    @Nullable
    private TerminalWindow windowAt(double mx, double my) {
        for (int i = this.windows.size() - 1; i >= 0; i--) {
            if (this.windows.get(i).isMouseOver(mx, my)) {
                return this.windows.get(i);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 给浮动子窗口用的门面
    //
    // 菜单字段是 AbstractContainerScreen 的 protected 成员（只有子类能碰），而子窗口是同包的另一
    // 个类，所以这里开几个包可见的小门面给它用——子窗口不必知道菜单/网络的细节。
    // ⚠️ 窗口按钮改档位时都是先 selectRow(rowIndex) 再发动作（那些动作只作用于"选中行"）。
    // ------------------------------------------------------------------

    List<TerminalRow> rows() {
        return this.menu.getRows();
    }

    /**
     * 这一行要画的曲线：<b>优先服务端算好的那份</b>，没有才退回客户端自记的兜底
     * （见 {@code ThroughputTerminalMenu.seriesFor}）。
     */
    ThroughputHistory.Series seriesFor(int rowIndex) {
        return this.menu.seriesFor(rowIndex);
    }

    /** 向服务端索取某一行的曲线（服务端会用该行自己的设置现算一份回发）。 */
    void requestRowSeries(int rowIndex) {
        this.menu.sendRequestRowSeries(rowIndex);
    }

    /**
     * 「定位」：让服务端把该行第一个来源监控器的坐标发回来，客户端在世界里给它画红色描边
     * （描边渲染在 {@code ClientHighlights} 的渲染钩子里）。
     */
    void locateRow(int rowIndex) {
        this.menu.sendLocateRow(rowIndex);
    }

    /** 选中某一行（窗口按钮改档位之前必须先调它，否则改的是别的行）。 */
    void selectRow(int rowIndex) {
        this.menu.sendSelectRow(rowIndex);
    }

    void cycleRowMetric() {
        this.menu.sendCycleRowMetric();
    }

    void cycleRowWindow() {
        this.menu.sendCycleRowWindow();
    }

    void toggleRowUnit() {
        this.menu.sendToggleRowUnit();
    }

    // ------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------

    /**
     * ⚠️ 顺序很重要：<b>先给最上层的浮动子窗口</b>，被它消费掉就不再传给档位按钮 / 列表 / 滚动条。
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX - this.leftPos;
        int my = (int) mouseY - this.topPos;

        TerminalWindow window = windowAt(mx, my);
        if (window != null) {
            bringToFront(window);
            return window.mouseClicked(mx, my, this);
        }

        // 列表行：点一下 = 选中（双击 = 开关该行的浮动子窗口）。
        // 列表是唯一的视图，所以这里不再判断 menu.viewMode（那个字段只为兼容老存档保留）。
        if (button == 0) {
            int index = rowAt(mx, my);
            if (index >= 0) {
                long now = System.currentTimeMillis();
                boolean doubleClick = index == this.lastClickRow && now - this.lastClickTime <= DOUBLE_CLICK_MS;
                this.lastClickRow = index;
                this.lastClickTime = now;
                this.menu.sendSelectRow(index);
                if (doubleClick) {
                    toggleWindowFor(index);
                }
                return true;
            }
            this.lastClickRow = -1;
        }

        if (draggingWindow() != null) {
            // 拖动中：按下事件不再往下传（避免误触滚动条/按钮）
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 拖动：只交给正在拖的那个窗口。
     *
     * <p>窗口可以拖到面板外，所以这里传的是<b>屏幕尺寸</b>（{@code this.width/height}），
     * 而不是面板尺寸 —— 窗口只在"还能抓住"的范围内被 clamp。
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        TerminalWindow window = draggingWindow();
        if (window != null) {
            window.mouseDragged(mouseX - this.leftPos, mouseY - this.topPos, this.width, this.height);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /** 松开：结束拖动（拖动中的窗口由它自己收尾，不再往下传）。 */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        TerminalWindow window = draggingWindow();
        if (window != null) {
            window.mouseReleased();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * 滚轮：鼠标停在浮动子窗口上时不让列表跟着滚（窗口自己的内容暂时不滚动）；
     * 其余情况交给滚动条翻页 —— 列表是唯一的视图，没有"图线视图换行"那套了。
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int mx = (int) mouseX - this.leftPos;
        int my = (int) mouseY - this.topPos;
        TerminalWindow window = windowAt(mx, my);
        if (window != null && window.mouseScrolled(mx, my)) {
            bringToFront(window);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** 面板相对坐标 → 行序号（-1 = 没点到行）。 */
    private int rowAt(int mx, int my) {
        if (mx < LIST_X || mx > LIST_X + LIST_W) {
            return -1;
        }
        if (my < HEADER_H || my >= HEADER_H + listHeight()) {
            return -1;
        }
        int index = this.scrollbar.getCurrentScroll() + (my - HEADER_H) / ROW_H;
        return index < this.menu.getRows().size() ? index : -1;
    }

    /**
     * 滚动条：列表是唯一的视图，所以它<b>永远可见、永远接管滚轮</b>
     * （视图切换已经删掉，曲线只在浮动子窗口里看）。
     */
    private void updateScrollbar() {
        this.scrollbar.setVisible(true);
        this.scrollbar.setCaptureMouseWheel(true);
        this.scrollbar.setRange(0, Math.max(0, this.menu.rowCount - this.visibleRows), 2);
    }

    // ------------------------------------------------------------------
    // 每帧刷新
    // ------------------------------------------------------------------

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        if (this.menu.rowCount != this.lastRowCount) {
            this.lastRowCount = this.menu.rowCount;
            updateScrollbar();
        }

        // 状态行只留"出错"类提示（未连网 / 网络里没有监控器）；行数提示已按要求去掉
        var rows = this.menu.getRows();
        if (!this.menu.networkAvailable) {
            this.setTextContent("status_text", Component.translatable("gui.aetm.terminal.no_network"));
        } else if (rows.isEmpty()) {
            this.setTextContent("status_text", Component.translatable("gui.aetm.terminal.no_monitors"));
        } else {
            this.setTextContent("status_text", Component.empty());
        }

        // 头部第二行：全局速率单位（一次改所有行；按钮上只写当前值 t/s/m/10m，全称在 tooltip 里）
        this.globalRoutineButton.setMessage(Component.translatable("gui.aetm.tbtn.routine_global",
                TerminalSettings.routineUnit(globalRoutine())));
        this.globalRoutineButton.active = !rows.isEmpty();

        // 有窗口开着就按服务端下发的间隔（配置项「图线绘制频率」换算而来）索要一次曲线：
        // 服务端那份是完整的采样历史，随开随新；间隔默认 = 2 × 采样间隔
        if (!this.windows.isEmpty()
                && ++this.seriesRequestCooldown >= this.menu.chartRefreshIntervalTicks()) {
            this.seriesRequestCooldown = 0;
            for (TerminalWindow window : this.windows) {
                this.menu.sendRequestRowSeries(window.rowIndex);
            }
        }
    }

    /** 全局速率单位显示用：取第一行的档位（点过全局按钮后各行一致）。 */
    private int globalRoutine() {
        var rows = this.menu.getRows();
        return rows.isEmpty() ? 1 : rows.get(0).routineOrdinal();
    }

    /** 下一个全局速率档位（t → s → m → 10m → t）。 */
    private int nextGlobalRoutine() {
        return (globalRoutine() + 1) % 4;
    }

}
