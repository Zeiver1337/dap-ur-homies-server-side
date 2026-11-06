package com.dap.dapurhomies.interaction;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * DapSession - ported from original Dap-ur-homie project.
 * Manages a two-player dap session where both players are smoothly moved
 * to face each other at a specific distance, creating a cinematic interaction.
 */
public class DapSession {

    private final UUID playerAId;
    private final UUID playerBId;
    private Vec3d targetA;
    private Vec3d targetB;

    private int tickCount;
    private boolean positioningComplete;

    private final double targetDistance;
    private final double lerpSpeed;
    private final DapType type;

    private Runnable onPositioningComplete;

    public enum DapType {
        NORMAL_DAP,
        PERFECT_DAP,
        FIRE_DAP,
        FIRE_COMBO,
        DAP_HOLD
    }

    public DapSession(UUID playerA, UUID playerB, double targetDistance, DapType type) {
        this.playerAId = playerA;
        this.playerBId = playerB;
        this.targetDistance = targetDistance;
        this.type = type;
        this.tickCount = 0;
        this.positioningComplete = false;

        if (type == DapType.PERFECT_DAP) {
            this.lerpSpeed = 0.95;
        } else {
            this.lerpSpeed = 0.75;
        }
    }

    public void onComplete(Runnable callback) {
        this.onPositioningComplete = callback;
    }

    public void tick(MinecraftServer server) {
        ServerPlayerEntity playerA = server.getPlayerManager().getPlayer(playerAId);
        ServerPlayerEntity playerB = server.getPlayerManager().getPlayer(playerBId);

        // Safety checks - if either player is gone, force complete and end session
        if (playerA == null || playerB == null || playerA.isRemoved() || playerB.isRemoved()) {
            forceComplete(playerA, playerB);
            return;
        }

        // Timeout safety (5 seconds = 100 ticks)
        if (tickCount > 100) {
            forceComplete(playerA, playerB);
            return;
        }

        freezePlayers(playerA, playerB);
        computeTargets(playerA, playerB);
        smoothMoveToTargets(playerA, playerB);
        makeFaceEachOther(playerA, playerB);
        playerA.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
        playerB.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);

        if (!positioningComplete) {
            checkPositioningComplete(playerA, playerB);
        }

        tickCount++;
    }

    private void freezePlayers(ServerPlayerEntity playerA, ServerPlayerEntity playerB) {
        playerA.setVelocity(Vec3d.ZERO);
        playerB.setVelocity(Vec3d.ZERO);
        playerA.knockedBack = true; // sync velocity to client
        playerB.knockedBack = true;
        playerA.fallDistance = 0;
        playerB.fallDistance = 0;
    }

    private void computeTargets(ServerPlayerEntity playerA, ServerPlayerEntity playerB) {
        Vec3d posA = playerA.getEntityPos();
        Vec3d posB = playerB.getEntityPos();

        Vec3d midpoint = posA.add(posB).multiply(0.5);

        Vec3d direction = posB.subtract(posA);
        if (direction.lengthSquared() < 1e-6) {
            direction = new Vec3d(1, 0, 0);
        }
        direction = direction.normalize();

        double halfDistance = targetDistance / 2.0;
        targetA = midpoint.subtract(direction.multiply(halfDistance));
        targetB = midpoint.add(direction.multiply(halfDistance));

        double targetY = Math.max(posA.y, posB.y);

        ServerWorld world = (ServerWorld) playerA.getEntityWorld();
        BlockPos groundPos = BlockPos.ofFloored(midpoint.x, targetY - 1, midpoint.z);
        if (world.getBlockState(groundPos).isAir()) {
            targetY = Math.min(posA.y, posB.y);
        }

        targetA = new Vec3d(targetA.x, targetY, targetA.z);
        targetB = new Vec3d(targetB.x, targetY, targetB.z);
    }

    private void smoothMoveToTargets(ServerPlayerEntity playerA, ServerPlayerEntity playerB) {
        Vec3d currentA = playerA.getEntityPos();
        Vec3d currentB = playerB.getEntityPos();

        Vec3d newPosA = new Vec3d(
                lerp(currentA.x, targetA.x, lerpSpeed),
                lerp(currentA.y, targetA.y, lerpSpeed),
                lerp(currentA.z, targetA.z, lerpSpeed)
        );

        Vec3d newPosB = new Vec3d(
                lerp(currentB.x, targetB.x, lerpSpeed),
                lerp(currentB.y, targetB.y, lerpSpeed),
                lerp(currentB.z, targetB.z, lerpSpeed)
        );

        playerA.teleport((ServerWorld) playerA.getEntityWorld(), newPosA.x, newPosA.y, newPosA.z,
                java.util.Set.of(), playerA.getYaw(), playerA.getPitch(), false);
        playerB.teleport((ServerWorld) playerB.getEntityWorld(), newPosB.x, newPosB.y, newPosB.z,
                java.util.Set.of(), playerB.getYaw(), playerB.getPitch(), false);
    }

    private void makeFaceEachOther(ServerPlayerEntity playerA, ServerPlayerEntity playerB) {
        Vec3d posA = playerA.getEntityPos();
        Vec3d posB = playerB.getEntityPos();

        double dx = posB.x - posA.x;
        double dz = posB.z - posA.z;
        float yawA = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
        float yawB = yawA + 180;

        playerA.setYaw(yawA);
        playerA.setBodyYaw(yawA);
        playerA.setHeadYaw(yawA);
        playerA.lastYaw = yawA;
        playerA.lastBodyYaw = yawA;
        playerA.lastHeadYaw = yawA;

        playerB.setYaw(yawB);
        playerB.setBodyYaw(yawB);
        playerB.setHeadYaw(yawB);
        playerB.lastYaw = yawB;
        playerB.lastBodyYaw = yawB;
        playerB.lastHeadYaw = yawB;

        playerA.teleport((ServerWorld) playerA.getEntityWorld(), posA.x, posA.y, posA.z,
                java.util.Set.of(), yawA, playerA.getPitch(), false);
        playerB.teleport((ServerWorld) playerB.getEntityWorld(), posB.x, posB.y, posB.z,
                java.util.Set.of(), yawB, playerB.getPitch(), false);
    }

    private void checkPositioningComplete(ServerPlayerEntity playerA, ServerPlayerEntity playerB) {
        double distA = playerA.getEntityPos().distanceTo(targetA);
        double distB = playerB.getEntityPos().distanceTo(targetB);

        double threshold = (type == DapType.PERFECT_DAP) ? 0.35 : 0.25;
        if (distA < threshold && distB < threshold) {
            positioningComplete = true;

            if (onPositioningComplete != null) {
                onPositioningComplete.run();
            }

            this.tickCount = 99; // Force near-timeout so session ends next tick
        }
    }

    private void forceComplete(ServerPlayerEntity playerA, ServerPlayerEntity playerB) {
        if (!positioningComplete) {
            positioningComplete = true;
            if (onPositioningComplete != null) {
                onPositioningComplete.run();
            }
        }
    }

    private double lerp(double current, double target, double factor) {
        return current + (target - current) * factor;
    }

    public UUID getPlayerAId() { return playerAId; }
    public UUID getPlayerBId() { return playerBId; }
    public boolean isPositioningComplete() { return positioningComplete; }
    public int getTickCount() { return tickCount; }
}

