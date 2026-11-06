package com.dap.dapurhomies.interaction;

import com.dap.dapurhomies.Lang;
import com.dap.dapurhomies.config.ModConfig;
import com.dap.dapurhomies.permission.PlayerPermissionManager;
import com.dap.dapurhomies.util.TickClock;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Push system - ported from original Dap-ur-homie project.
 * Features: push immunity, ceiling detection, tiered push power.
 */
public class PushHandler {

    private static final Map<UUID, Long> pushImmunity = new HashMap<>();
    private static final long PUSH_IMMUNITY_TICKS = TickClock.msToTicks(500);

    // Unified push velocity: ~5 blocks high, 8 blocks far
    private static final double PUSH_VELOCITY = 1.2;

    public static ActionResult handlePush(ServerPlayerEntity sp, ServerPlayerEntity target) {
        if (sp == null || target == null) return ActionResult.PASS;
        if (sp.equals(target)) return ActionResult.PASS;
        if (!ModConfig.get().enablePush) return ActionResult.PASS;

        double dist = sp.distanceTo(target);
        if (dist > ModConfig.get().pushRange) return ActionResult.PASS;

        if (!PlayerPermissionManager.canInitiate(sp, "push")) {
            Lang.interactionBlocked(sp, "Push is disabled for you!", "你的推功能已关闭！");
            return ActionResult.FAIL;
        }
        if (!PlayerPermissionManager.hasPermission(sp, "dap-ur-homies.admin")
                && !PlayerPermissionManager.canReceive(target, "push")) {
            Lang.targetDisabledType(sp, target, "push");
            return ActionResult.FAIL;
        }

        if (InteractionCooldowns.rejectIfBlocked(sp, "push")) return ActionResult.FAIL;

        if (isImmune(target.getUuid())) return ActionResult.PASS;

        executePush(sp, target);
        return ActionResult.SUCCESS;
    }

    private static void executePush(ServerPlayerEntity sp, ServerPlayerEntity target) {
        if (sp == null || target == null) return;
        applyPush(sp, target, PUSH_VELOCITY);
    }

    private static void applyPush(ServerPlayerEntity sp, ServerPlayerEntity target, double velocity) {
        if (sp == null || target == null) return;
        ServerWorld w = sp.getEntityWorld();
        if (w == null) return;

        // Original project: target.addVelocity(0, velocity, 0) - vertical launch
        double ceilingHeight = getCeilingHeight(target);
        double actualVelocity = velocity;
        if (ceilingHeight < 10 && ceilingHeight > 0) {
            double maxH = Math.max(2, ceilingHeight - 1);
            double v = Math.sqrt(2 * 0.08 * 20 * maxH);
            actualVelocity = Math.min(v, velocity);
        }

        UUID carriedId = GrabYeetHandler.getHeldId(target.getUuid());
        if (carriedId != null) {
            var srv = w.getServer();
            if (srv != null) {
                ServerPlayerEntity carried = srv.getPlayerManager().getPlayer(carriedId);
                if (carried != null) {
                    carried.addVelocity(0, actualVelocity + 0.2, 0);
                    carried.knockedBack = true;
                    pushImmunity.put(carriedId, TickClock.now() + PUSH_IMMUNITY_TICKS);
                }
            }
        }

        // Calculate horizontal away direction for knockback
        Vec3d awayDir = target.getEntityPos().subtract(sp.getEntityPos()).normalize();
        awayDir = new Vec3d(awayDir.x, 0, awayDir.z).normalize();
        if (awayDir.lengthSquared() < 0.001) awayDir = new Vec3d(1, 0, 0);

        target.addVelocity(awayDir.x * actualVelocity * 0.8, actualVelocity * 0.6, awayDir.z * actualVelocity * 0.8);
        target.knockedBack = true;

        pushImmunity.put(target.getUuid(), TickClock.now() + PUSH_IMMUNITY_TICKS);

        Vec3d mid = sp.getEntityPos().add(target.getEntityPos()).multiply(0.5).add(0, 1.2, 0);
        w.spawnParticles(ParticleTypes.CLOUD, mid.x, mid.y, mid.z, 6, 0.3, 0.2, 0.3, 0.05);
        w.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 1.0f, 1.0f);

        sp.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
        Lang.pushSuccess(sp, target);
        InteractionCooldowns.applyShared(sp.getUuid(), target.getUuid(), "push", ModConfig.get().pushCooldownMs);
        com.dap.dapurhomies.compat.DanseCompat.playPush(sp, target); // Danse animation
    }

    private static double getCeilingHeight(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        BlockPos pos = BlockPos.ofFloored(player.getEntityPos());
        int maxCheck = 20;
        for (int i = 1; i <= maxCheck; i++) {
            if (!world.getBlockState(pos.up(i)).isAir()) return i;
        }
        return maxCheck;
    }

    private static boolean isImmune(UUID playerId) {
        Long endTime = pushImmunity.get(playerId);
        return endTime != null && TickClock.now() < endTime;
    }

    public static void tick(MinecraftServer server) {
        cleanupExpired();
    }

    private static void cleanupExpired() {
        long now = TickClock.now();
        pushImmunity.entrySet().removeIf(e -> now >= e.getValue());
    }

    public static void cleanup(UUID playerId) {
        pushImmunity.remove(playerId);
    }

    public static void purgeExpiredCooldowns() {
        cleanupExpired();
    }
}

