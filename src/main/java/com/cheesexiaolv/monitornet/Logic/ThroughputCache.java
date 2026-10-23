package com.cheesexiaolv.monitornet.Logic;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import appeng.hooks.ticking.TickHandler;

/**
 * Throughput sample cache: keeps (amount, server tick) samples and estimates the average
 * per-tick change over a window.
 *
 * <p>Same logic as Advanced AE's {@code ThroughputCache}; the capacity is a constructor argument.
 *
 * <p>1.21.1 port: unchanged — {@code appeng.hooks.ticking.TickHandler.instance()#getCurrentTick()}
 * still exists in AE2 19.2.17 ({@code appeng/hooks/ticking/TickHandler.java:57,312}).
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
     * Average per-tick change over the last {@code timeLimit_s} seconds, weighting each pair of
     * adjacent samples by their tick gap.
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
