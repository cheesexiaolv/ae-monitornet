package com.cheesexiaolv.aetm.Parts;

import java.util.HashMap;
import java.util.Map;

import com.cheesexiaolv.aetm.AeMonitorNet;
import com.cheesexiaolv.aetm.Logic.TerminalSettings;
import com.cheesexiaolv.aetm.Logic.TerminalSettingsNbt;
import com.cheesexiaolv.aetm.menu.ThroughputTerminalMenu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractDisplayPart;

/**
 * 聚合吞吐终端（面板零件，有线版）。
 *
 * <p>它本身不盯任何物品，而是<b>枚举同一 ME 网络上的所有吞吐量监控器</b>
 * （遍历 {@code IGrid#getMachineClasses()} 再按可赋值性筛选，见菜单里的说明），
 * 按被监控的 key 合并成行，再把行数据交给 {@link ThroughputTerminalMenu} 同步给客户端。
 *
 * <p>显示设置分两层：全局默认（窗口 / 显示量 / 单位 / 速率档）+ 单行覆盖。全局按钮会改写全局默认
 * 并清空所有单行覆盖；单行按钮只改那一行。设置通过 {@link ITerminalSettingsHost} 暴露，
 * 无线终端（物品）用同样的接口实现，因此两者共用同一套菜单与界面。
 *
 * <p>世界里的外观是<b>自有</b>模型：几何继承 AE2 的 {@code part/display_base} 与
 * {@code part/pattern_access_terminal_*}，正面底图与三层高亮则换成自有贴图
 * （{@code ae_monitornet:part/terminal_front*}），不再露出 AE2 的花纹底图。
 */
public class ThroughputTerminalPart extends AbstractDisplayPart implements ITerminalSettingsHost {

    // 自有 part 模型（世界渲染用）。三个 id 都必须在客户端启动时交给 AE2 的烘焙器登记
    // （appeng.api.parts.PartModels#registerModels，见 PartModelsRegistration），
    // 否则 AE2 的 CableBus 取不到它们，世界里的面板会直接不显示（且不会报缺失模型）。
    // MODEL_BASE 有意遮蔽基类 AbstractDisplayPart 的同名常量（基类那份是 ae2:part/display_base）。
    public static final ResourceLocation MODEL_BASE = new ResourceLocation(AeMonitorNet.MODID, "part/terminal_base");
    public static final ResourceLocation MODEL_OFF = new ResourceLocation(AeMonitorNet.MODID, "part/terminal_off");
    public static final ResourceLocation MODEL_ON = new ResourceLocation(AeMonitorNet.MODID, "part/terminal_on");

    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    /** 视图常量（转发到 {@link TerminalSettings}，方便界面代码引用）。 */
    public static final int VIEW_TEXT = TerminalSettings.VIEW_TEXT;
    public static final int VIEW_CHART = TerminalSettings.VIEW_CHART;

    /** 档位 → tick 数。 */
    public static int routineTicks(int routineOrdinal) {
        return TerminalSettings.routineTicks(routineOrdinal);
    }

    /** 档位 → 速率单位后缀（t / s / m / 10m）。 */
    public static String routineUnit(int routineOrdinal) {
        return TerminalSettings.routineUnit(routineOrdinal);
    }

    // 全局默认显示设置
    private TerminalSettings global = TerminalSettings.DEFAULT;
    private int viewMode = TerminalSettings.VIEW_TEXT;

    /** 终端尺寸档位（TerminalStyle ordinal，默认满）。 */
    private int terminalStyle = TerminalSettingsNbt.DEFAULT_STYLE;

    /** 单行覆盖（key → 设置）。 */
    private final Map<AEKey, TerminalSettings> overrides = new HashMap<>();

    public ThroughputTerminalPart(IPartItem<?> partItem) {
        super(partItem, true);
    }

    @Override
    public boolean onPartActivate(Player player, InteractionHand hand, Vec3 pos) {
        if (!isClientSide()) {
            MenuOpener.open(ThroughputTerminalMenu.TYPE, player, MenuLocators.forPart(this));
        }
        return true;
    }

    @Override
    public IPartModel getStaticModels() {
        return this.selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    // ------------------------------------------------------------------
    // 显示设置（ITerminalSettingsHost）
    // ------------------------------------------------------------------

    @Override
    public TerminalSettings getGlobalSetting() {
        return this.global;
    }

    @Override
    public TerminalSettings getSetting(AEKey key) {
        return this.overrides.getOrDefault(key, this.global);
    }

    /** 全局按钮：改写全局默认，并清空所有单行覆盖（"统一修改每一行"）。 */
    @Override
    public void setGlobalSetting(TerminalSettings setting) {
        this.global = setting;
        this.overrides.clear();
        markDirty();
    }

    /** 单行按钮：只改这一行的覆盖。 */
    @Override
    public void setRowSetting(AEKey key, TerminalSettings setting) {
        this.overrides.put(key, setting);
        markDirty();
    }

    @Override
    public int getViewMode() {
        return this.viewMode;
    }

    @Override
    public void setViewMode(int viewMode) {
        this.viewMode = viewMode == TerminalSettings.VIEW_CHART ? TerminalSettings.VIEW_CHART
                : TerminalSettings.VIEW_TEXT;
        markDirty();
    }

    @Override
    public int getTerminalStyle() {
        return this.terminalStyle;
    }

    @Override
    public void setTerminalStyle(int styleOrdinal) {
        this.terminalStyle = Math.max(0, Math.min(3, styleOrdinal));
        markDirty();
    }

    private void markDirty() {
        getHost().markForSave();
        getHost().markForUpdate();
    }

    // ------------------------------------------------------------------
    // 持久化（键名与最早的实现一致，旧存档仍可读）
    // ------------------------------------------------------------------

    @Override
    public void writeToNBT(CompoundTag data) {
        super.writeToNBT(data);
        TerminalSettingsNbt.writeGlobal(data, this.global);
        TerminalSettingsNbt.writeViewMode(data, this.viewMode);
        TerminalSettingsNbt.writeStyle(data, this.terminalStyle);
        TerminalSettingsNbt.writeOverrides(data, this.overrides);
    }

    @Override
    public void readFromNBT(CompoundTag data) {
        super.readFromNBT(data);
        this.global = TerminalSettingsNbt.readGlobal(data);
        this.viewMode = TerminalSettingsNbt.readViewMode(data);
        this.terminalStyle = TerminalSettingsNbt.readStyle(data);
        this.overrides.clear();
        this.overrides.putAll(TerminalSettingsNbt.readOverrides(data));
    }
}
