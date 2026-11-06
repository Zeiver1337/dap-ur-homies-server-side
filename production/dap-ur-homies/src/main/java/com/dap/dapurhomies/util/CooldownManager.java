package com.dap.dapurhomies.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified cooldown manager. Replaces scattered cooldown maps in each handler.
 * Thread-safe using ConcurrentHashMap.
 */
public class CooldownManager {
    private static final Map<String, Map<UUID, Long>> cooldowns = new ConcurrentHashMap<>();

    /**
     * Check if a player is on cooldown for a specific action type.
     */
    public static boolean isOnCooldown(UUID playerId, String type) {
        Map<UUID, Long> map = cooldowns.get(type);
        if (map == null) return false;
        Long end = map.get(playerId);
        if (end == null) return false;
        long now = TickClock.now();
        if (now >= end) {
            map.remove(playerId);
            return false;
        }
        return true;
    }

    /**
     * Set cooldown for a player. Duration is in ticks.
     */
    public static void setCooldown(UUID playerId, String type, long durationTicks) {
        cooldowns.computeIfAbsent(type, k -> new ConcurrentHashMap<>())
                .put(playerId, TickClock.now() + durationTicks);
    }

    /**
     * Cleanup all cooldowns for a player (call on disconnect).
     */
    public static void cleanup(UUID playerId) {
        for (Map<UUID, Long> map : cooldowns.values()) {
            map.remove(playerId);
        }
    }

    /**
     * Purge expired cooldowns to prevent memory growth.
     */
    public static void purgeExpired() {
        long now = TickClock.now();
        for (Map<UUID, Long> map : cooldowns.values()) {
            // Remove entries where the end time has been reached (now >= end).
            map.entrySet().removeIf(e -> now >= e.getValue());
        }
    }
}

