package com.cheesexiaolv.aetm.client;

import java.lang.reflect.Field;
import java.util.List;

import com.cheesexiaolv.aetm.Parts.AdvancedThroughputMonitorPart;

import appeng.api.integrations.igtooltip.PartTooltips;
import appeng.api.integrations.igtooltip.TooltipBuilder;
import appeng.api.integrations.igtooltip.TooltipContext;
import appeng.api.integrations.igtooltip.providers.BodyProvider;
import appeng.core.AELog;
import appeng.core.localization.InGameTooltip;
import appeng.parts.reporting.AbstractMonitorPart;

/**
 * 去掉高级吞吐量监控器的"锁定"提示行。
 *
 * <p>AE2 的 {@code StorageMonitorDataProvider} 会<b>无条件</b>输出 {@code Locked}/{@code Unlocked} 一行，
 * 而 {@code PartTooltips} 只有添加没有移除，且所有匹配的 provider 都会执行。
 * 因此这里：
 * <ol>
 *   <li>反射把 AE2 那个 provider 从注册表里摘掉；</li>
 *   <li>注册我们自己的等价 provider —— 对普通监控器保留锁定行，对高级监控器不再输出。</li>
 * </ol>
 * 反射针对固定版本 AE2 12.9.9，失败时只记日志、不影响游戏。
 */
public final class MonitorTooltipLockFix implements BodyProvider<AbstractMonitorPart> {

    private static final String AE2_PROVIDER_CLASS = "appeng.integration.modules.igtooltip.parts.StorageMonitorDataProvider";

    public static void install() {
        try {
            var providersClass = Class.forName("appeng.integration.modules.igtooltip.parts.PartTooltipProviders");
            Field field = providersClass.getDeclaredField("bodyProviders");
            field.setAccessible(true);
            List<?> registrations = (List<?>) field.get(null);

            registrations.removeIf(registration -> {
                try {
                    var accessor = registration.getClass().getDeclaredMethod("provider");
                    accessor.setAccessible(true);
                    var provider = accessor.invoke(registration);
                    return provider != null && AE2_PROVIDER_CLASS.equals(provider.getClass().getName());
                } catch (ReflectiveOperationException e) {
                    return false;
                }
            });

            // 清掉 AE2 的"按类缓存"，否则已缓存的类不会重新收集 provider
            try {
                Field cacheField = providersClass.getDeclaredField("cache");
                cacheField.setAccessible(true);
                ((java.util.Map<?, ?>) cacheField.get(null)).clear();
            } catch (Throwable ignored) {
                // 缓存字段名变化时忽略：只要调用时机够早（首 tick），缓存本就是空的
            }

            PartTooltips.addBody(AbstractMonitorPart.class, new MonitorTooltipLockFix());
        } catch (Throwable t) {
            AELog.warn("AE MonitorNet: failed to strip AE2 monitor lock tooltip line", t);
        }
    }

    @Override
    public void buildTooltip(AbstractMonitorPart monitor, TooltipContext context, TooltipBuilder tooltip) {
        var displayed = monitor.getDisplayed();
        if (displayed != null) {
            tooltip.addLine(InGameTooltip.Showing.text().append(": ").append(displayed.getDisplayName()));
        }

        // 高级监控器没有"锁定"设定 —— 不输出 Locked/Unlocked；其它监控器保持 AE2 原行为
        if (!(monitor instanceof AdvancedThroughputMonitorPart)) {
            tooltip.addLine(monitor.isLocked() ? InGameTooltip.Locked.text() : InGameTooltip.Unlocked.text());
        }
    }
}
