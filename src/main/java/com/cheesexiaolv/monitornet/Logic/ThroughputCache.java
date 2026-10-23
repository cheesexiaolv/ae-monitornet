package com.cheesexiaolv.monitornet.Logic;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import appeng.hooks.ticking.TickHandler;

/**
 * 吞吐量采样缓存：保存 (数量, 服务器刻) 样本，用于估算一段时间内的平均每刻变化量。
 *
 * <p>与 Advanced AE 1.20.1 的 {@code ThroughputCache} 逻辑一致，仅将缓存容量改为可由构造参数传入
 * （Advanced AE 默认通过配置文件取 80）。
 */
public class ThroughputCache {

    private final int maxSize;
    private final LinkedList<CacheEntry> cache = new LinkedList<>();

    public ThroughputCache(int maxSize) {
        this.maxSize = maxSize;
    }

    public void push(long amount, long timestamp) {
        if (timestamp == 0) return;
        if (!cache.isEmpty() && cache.getFirst().timestamp == timestamp) return;

        cache.addFirst(new CacheEntry(amount, timestamp));
        if (cache.size() > maxSize) {
            cache.removeLast();
        }
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    /**
     * 计算最近 {@code timeLimit_s} 秒窗口内、按相邻样本时间间隔加权后的平均每刻变化量。
     */
    public double averagePerTick(int timeLimit_s) {
        long now = TickHandler.instance().getCurrentTick();
        long tLimit = now - timeLimit_s * 20L;

        long lastAmount = -1;
        long lastTimestamp = -1;
        List<Double> averages = new ArrayList<>();

        for (var entry : cache) {
            if (entry.timestamp < tLimit) break;

            if (lastTimestamp != -1) {
                long timestampDelta = lastTimestamp - entry.timestamp;
                long amountDelta = lastAmount - entry.amount;
                if (timestampDelta > 0) {
                    averages.add(amountDelta / (double) timestampDelta);
                }
            }

            lastAmount = entry.amount;
            lastTimestamp = entry.timestamp;
        }

        if (averages.isEmpty()) return 0;

        double sum = 0;
        for (double avg : averages) {
            sum += avg;
        }

        return sum / (double) averages.size();
    }

    private record CacheEntry(long amount, long timestamp) {}
}
