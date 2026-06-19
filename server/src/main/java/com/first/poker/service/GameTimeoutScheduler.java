package com.first.poker.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class GameTimeoutScheduler {

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(
        Math.max(4, Runtime.getRuntime().availableProcessors()));
    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
    private final BiConsumer<String, String> timeoutCallback;

    public GameTimeoutScheduler(BiConsumer<String, String> timeoutCallback) {
        this.timeoutCallback = timeoutCallback;
    }

    public void scheduleTimeout(String roomId, String playerId, int timeoutSec) {
        String key = roomId + ":" + playerId;
        // Cancel previous
        cancelTimeoutKey(key);
        var future = executor.schedule(() -> {
            tasks.remove(key);
            timeoutCallback.accept(roomId, playerId);
        }, timeoutSec, TimeUnit.SECONDS);
        tasks.put(key, future);
    }

    public void cancelTimeout(String roomId) {
        // Cancel all timeouts for this room
        var prefix = roomId + ":";
        for (var entry : Map.copyOf(tasks).entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                cancelTimeoutKey(entry.getKey());
            }
        }
    }

    private void cancelTimeoutKey(String key) {
        var future = tasks.remove(key);
        if (future != null && !future.isDone()) {
            future.cancel(false);
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
