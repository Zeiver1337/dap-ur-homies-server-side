package com.dap.dapurhomies.interaction;

import com.dap.dapurhomies.DapUrHomiesMod;
import com.dap.dapurhomies.Lang;
import com.dap.dapurhomies.config.ModConfig;
import com.dap.dapurhomies.permission.PlayerPermissionManager;
import com.dap.dapurhomies.util.TickClock;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Fall Catch system - ported from original Dap-ur-homie project.
 * Players can enter "catch ready" mode to catch falling players within range.
 * Pure server-side implementation.
 */
public class FallCatchHandler {

    public static final double CATCH_RANGE_HORIZONTAL = 3.0;
    public static final double CATCH_RANGE_VERTICAL = 4.0;
    public static final long CATCH_WINDOW_TICKS = TickClock.msToTicks(500);
    public static final long CATCH_COOLDOWN_TICKS = TickClock.msToTicks(1000);

    // ===== STATE MAPS =====
    private static final Map<UUID, Long> catchReadyTime = new HashMap<>();   // playerId -> readyStartTime
    private static final Map<UUID, Long> catchCooldowns = new HashMap<>();     // playerId -> cooldownEndTime
    private static final Map<UUID, Boolean> successfulCatch = new HashMap<>(); // playerId -> caught someone this window

    public static void register() {
        // Tick is driven by InteractionModules.tickAll(); do not register another server tick here.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity fallingPlayer)) return true;
            if (DapUrHomiesMod.isModEnabled() == false) return true;
            if (!source.isOf(DamageTypes.FALL)) return true;
            if (fallingPlayer.fallDistance < 3.0f) return true;
            if (!ModConfig.get().enableGrabYeet) return true; // Use grab toggle for now

            ServerPlayerEntity catcher = findCatcher(fallingPlayer);
            if (catcher != null) {
                playCatchEffects(fallingPlayer, catcher);
                successfulCatch.put(catcher.getUuid(), true);
                return false; // Cancel fall damage
            }
            return true;
        });
    }

    // ===== PUBLIC API =====

    public static ActionResult enterCatchReady(ServerPlayerEntity player) {
        if (player == null) return ActionResult.PASS;
        if (!ModConfig.get().enableGrabYeet) return ActionResult.PASS;
        if (!PlayerPermissionManager.canInitiate(player, "grab")) {
            Lang.interactionBlocked(player, "Grab is disabled for you!", "你的抓取功能已关闭！");
            return ActionResult.FAIL;
        }

        UUID playerId = player.getUuid();

        if (isOnCatchCooldown(playerId)) {
            Lang.sendActionBar(player, "§c Catch on cooldown!", "§c 接住冷却中！");
            return ActionResult.FAIL;
        }

        if (catchReadyTime.containsKey(playerId)) {
            // Already ready, cancel it
            catchReadyTime.remove(playerId);
            successfulCatch.remove(playerId);
            Lang.sendActionBar(player, "§7 Catch ready cancelled.", "§7 取消接住准备。");
            return ActionResult.SUCCESS;
        }

        catchReadyTime.put(playerId, TickClock.now());
        successfulCatch.put(playerId, false);
        Lang.sendActionBar(player, "§a Catch ready! (500ms window)", "§a 准备接住！(500ms窗口)");
        return ActionResult.SUCCESS;
    }

    public static boolean isOnCatchCooldown(UUID playerId) {
        Long cooldownEnd = catchCooldowns.get(playerId);
        if (cooldownEnd == null) return false;
        if (TickClock.now() >= cooldownEnd) {
            catchCooldowns.remove(playerId);
            return false;
        }
        return true;
    }

    // ===== TICK =====

    public static void tick(MinecraftServer server) {
        long now = TickClock.now();

        Iterator<Map.Entry<UUID, Long>> it = catchReadyTime.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            UUID playerId = entry.getKey();
            long readyTime = entry.getValue();

            // Window expired
            if (now - readyTime > CATCH_WINDOW_TICKS) {
                if (!successfulCatch.getOrDefault(playerId, false)) {
                    // Missed - apply cooldown
                    catchCooldowns.put(playerId, now + CATCH_COOLDOWN_TICKS);

                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                    if (player != null) {
                        Lang.sendActionBar(player, "§c Catch missed! 1s cooldown", "§c 接住失败！1秒冷却");
                    }
                }
                it.remove();
                successfulCatch.remove(playerId);
            }
        }
    }

    // ===== FIND CATCHER =====

    private static ServerPlayerEntity findCatcher(ServerPlayerEntity fallingPlayer) {
        ServerWorld world = fallingPlayer.getEntityWorld();
        long now = TickClock.now();

        Box searchBox = new Box(
                fallingPlayer.getX() - CATCH_RANGE_HORIZONTAL,
                fallingPlayer.getY() - CATCH_RANGE_VERTICAL,
                fallingPlayer.getZ() - CATCH_RANGE_HORIZONTAL,
                fallingPlayer.getX() + CATCH_RANGE_HORIZONTAL,
                fallingPlayer.getY() + CATCH_RANGE_VERTICAL,
                fallingPlayer.getZ() + CATCH_RANGE_HORIZONTAL
        );

        for (PlayerEntity player : world.getPlayers()) {
            if (player == fallingPlayer) continue;
            if (!(player instanceof ServerPlayerEntity catcher)) continue;

            UUID catcherId = catcher.getUuid();

            // Must be in catch ready mode
            Long readyTime = catchReadyTime.get(catcherId);
            if (readyTime == null) continue;

            // Check window
            if (now - readyTime > CATCH_WINDOW_TICKS) continue;

            // Check cooldown
            if (isOnCatchCooldown(catcherId)) continue;

            // Check range
            if (!searchBox.contains(catcher.getEntityPos())) continue;

            // Permissions: catcher must be allowed to grab, falling player must accept grabs
            if (!PlayerPermissionManager.canInitiate(catcher, "grab")) continue;
            if (!PlayerPermissionManager.hasPermission(catcher, "dap-ur-homies.admin")
                    && !PlayerPermissionManager.canReceive(fallingPlayer, "grab")) continue;

            double dx = fallingPlayer.getX() - catcher.getX();
            double dz = fallingPlayer.getZ() - catcher.getZ();
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);

            if (horizontalDist <= CATCH_RANGE_HORIZONTAL) {
                return catcher;
            }
        }

        return null;
    }

    // ===== EFFECTS =====

    private static void playCatchEffects(ServerPlayerEntity caught, ServerPlayerEntity catcher) {
        ServerWorld world = catcher.getEntityWorld();
        double x = (caught.getX() + catcher.getX()) / 2;
        double y = catcher.getY() + 1.5;
        double z = (caught.getZ() + catcher.getZ()) / 2;

        world.playSound(null, x, y, z,
                SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.2f, 0.7f);
        world.playSound(null, x, y, z,
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.2f);
        world.playSound(null, x, y, z,
                SoundEvents.BLOCK_WOOL_FALL, SoundCategory.PLAYERS, 1.0f, 0.8f);

        world.spawnParticles(ParticleTypes.CLOUD, x, y, z, 12, 0.4, 0.3, 0.4, 0.02);
        world.spawnParticles(ParticleTypes.CRIT, x, y, z, 8, 0.3, 0.3, 0.3, 0.1);
        world.spawnParticles(ParticleTypes.WAX_ON, x, y, z, 6, 0.2, 0.2, 0.2, 0.02);

        caught.setVelocity(0, 0, 0);
        caught.knockedBack = true;

        Lang.sendActionBar(caught, "§a§l " + catcher.getName().getString() + " caught you!",
                "§a§l " + catcher.getName().getString() + " 接住了你！");
        Lang.sendActionBar(catcher, "§a§l PERFECT CATCH! " + caught.getName().getString(),
                "§a§l 完美接住！" + caught.getName().getString());
    }

    // ===== CLEANUP =====

    public static void cleanup(UUID playerId) {
        catchReadyTime.remove(playerId);
        catchCooldowns.remove(playerId);
        successfulCatch.remove(playerId);
    }
}

