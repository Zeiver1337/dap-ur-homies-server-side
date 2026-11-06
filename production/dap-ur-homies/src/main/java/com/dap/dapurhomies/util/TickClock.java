package com.dap.dapurhomies.util;

import net.minecraft.server.MinecraftServer;

/**
 * Server-tick based clock. Replaces System.currentTimeMillis() for all cooldowns,
 * charges, and timers to avoid issues with system time jumps (NTP, /time add, sleep).
 * 1 tick = 50ms at 20 TPS.
 */
public class TickClock {
    private static long currentTick = 0;

    public static void update(MinecraftServer server) {
        currentTick = server.getTicks();
    }

    public static long now() {
        return currentTick;
    }

    /** Convert milliseconds to ticks (rounded up). */
    public static long msToTicks(long ms) {
        return (ms + 49) / 50;
    }
}

