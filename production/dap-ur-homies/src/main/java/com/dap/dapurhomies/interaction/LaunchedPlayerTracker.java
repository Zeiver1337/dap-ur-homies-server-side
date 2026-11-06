package com.dap.dapurhomies.interaction;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks launched/thrown players and plays trail particle effects.
 * Ported from original Dap-ur-homie project.
 */
public class LaunchedPlayerTracker {

    private static final Map<UUID, Integer> launchedTicks = new HashMap<>();
    private static final int TRAIL_DURATION = 20; // 1 second of particle trail

    public static void markPlayerAsLaunched(UUID playerId) {
        launchedTicks.put(playerId, 0);
    }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Integer>> it = launchedTicks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            UUID id = entry.getKey();
            int ticks = entry.getValue();

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }

            if (ticks < TRAIL_DURATION) {
                playLaunchTrailEffects(player);
                entry.setValue(ticks + 1);
            } else {
                it.remove();
            }
        }
    }

    private static void playLaunchTrailEffects(ServerPlayerEntity player) {
        var world = player.getEntityWorld();
        var pos = player.getEntityPos();

        // Cloud trail
        world.spawnParticles(ParticleTypes.CLOUD,
                pos.x, pos.y + 0.5, pos.z,
                2, 0.15, 0.15, 0.15, 0.02);

        // Crit sparkles for high-speed flight
        if (player.getVelocity().lengthSquared() > 1.0) {
            world.spawnParticles(ParticleTypes.CRIT,
                    pos.x, pos.y + 0.5, pos.z,
                    1, 0.1, 0.1, 0.1, 0.05);
        }
    }

    public static void cleanup(UUID playerId) {
        launchedTicks.remove(playerId);
    }
}

