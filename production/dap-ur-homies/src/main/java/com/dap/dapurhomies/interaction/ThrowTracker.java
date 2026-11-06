package com.dap.dapurhomies.interaction;

import com.dap.dapurhomies.compat.ProtectionCompat;
import com.dap.dapurhomies.util.TickClock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

final class ThrowTracker {
    private static final Map<UUID, PendingThrow> pendingThrows = new HashMap<>();

    private static class PendingThrow {
        final Vec3d velocity;
        int ticksRemaining;

        PendingThrow(Vec3d velocity, int delayTicks) {
            this.velocity = velocity;
            this.ticksRemaining = delayTicks;
        }
    }

    private static final Map<UUID, ThrownPlayerData> thrownPlayers = new HashMap<>();

    private static class ThrownPlayerData {
        final long throwTick;
        boolean wasOnFire;
        boolean hitWall;
        boolean landed;
        int ticksFlying;

        ThrownPlayerData(long throwTick, boolean wasOnFire) {
            this.throwTick = throwTick;
            this.wasOnFire = wasOnFire;
            this.hitWall = false;
            this.landed = false;
            this.ticksFlying = 0;
        }
    }

    private static final Map<UUID, Long> throwImmunity = new HashMap<>();
    private static final long THROW_IMMUNITY_TICKS = 10;      // 500ms = 10 ticks

    private static final int PENDING_THROW_TICKS = 3;
    private static final double WALL_CHECK_DISTANCE = 1.5;
    private static final float FIRE_EXPLOSION_POWER = 3.0f;

    private ThrowTracker() {
    }

    static boolean isImmune(UUID playerId) {
        Long immuneTick = throwImmunity.get(playerId);
        return immuneTick != null && (TickClock.now() - immuneTick) < 20;
    }

    static void schedulePendingThrow(UUID playerId, Vec3d velocity) {
        pendingThrows.put(playerId, new PendingThrow(velocity, PENDING_THROW_TICKS));
    }

    static void registerThrown(UUID playerId, boolean wasOnFire) {
        thrownPlayers.put(playerId, new ThrownPlayerData(TickClock.now(), wasOnFire));
    }

    static void applyImmunity(UUID playerId) {
        throwImmunity.put(playerId, TickClock.now());
    }

    static boolean isThrownFlying(UUID playerId) {
        return thrownPlayers.containsKey(playerId);
    }

    static void tickPendingThrows(MinecraftServer server) {
        Iterator<Map.Entry<UUID, PendingThrow>> it = pendingThrows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PendingThrow> entry = it.next();
            UUID playerId = entry.getKey();
            PendingThrow pt = entry.getValue();
            pt.ticksRemaining--;

            if (pt.ticksRemaining <= 0) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player != null && player.isAlive()) {
                    player.setVelocity(pt.velocity);
                    player.knockedBack = true;
                }
                it.remove();
            }
        }
    }

    static void tickThrownPlayers(MinecraftServer server) {
        long nowTick = TickClock.now();
        Iterator<Map.Entry<UUID, ThrownPlayerData>> it = thrownPlayers.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, ThrownPlayerData> entry = it.next();
            UUID playerId = entry.getKey();
            ThrownPlayerData data = entry.getValue();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);

            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }

            Vec3d pos = player.getEntityPos();
            ServerWorld world = player.getEntityWorld();
            data.ticksFlying++;

            // Wall collision check (every 2 ticks to reduce block state queries)
            if (!data.hitWall && data.ticksFlying % 2 == 0) {
                Vec3d vel = player.getVelocity();
                Vec3d checkDir = (vel.lengthSquared() > 0.001) ? vel.normalize() : player.getRotationVec(1.0f);
                Vec3d checkPos = pos.add(checkDir.multiply(WALL_CHECK_DISTANCE));
                BlockPos blockPos = BlockPos.ofFloored(checkPos);
                BlockState state = world.getBlockState(blockPos);

                if (!state.isAir() && isBreakable(state)) {
                    // Ignore the first 3 ticks, while velocity direction is still unreliable.
                    // Break walls only at flight speeds of at least 1.2 blocks/tick.
                    if (data.ticksFlying > 3 && player.getVelocity().length() >= 1.2) {
                        data.hitWall = true;
                        // Respect claims: the thrown player must not be able to breach protected blocks
                        if (ProtectionCompat.canBuildAt(player, blockPos)) {
                            world.breakBlock(blockPos, true);
                        }
                        float hardness = state.getHardness(world, blockPos);
                        double speed = player.getVelocity().length();
                        float damage = calculateWallDamage(hardness, speed);
                        player.damage(world, world.getDamageSources().flyIntoWall(), damage);
                        player.setVelocity(player.getVelocity().multiply(0.7));
                        player.knockedBack = true;
                        world.spawnParticles(ParticleTypes.EXPLOSION, checkPos.x, checkPos.y, checkPos.z,
                                3, 0.3, 0.3, 0.3, 0.1);
                        world.playSound(null, checkPos.x, checkPos.y, checkPos.z,
                                SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 1.0f, 0.8f);
                    }
                }
            }

            // Landing detection
            if (!data.landed) {
                boolean onGround = player.isOnGround();
                boolean inWater = player.isTouchingWater();
                boolean nearGround = !world.getBlockState(BlockPos.ofFloored(pos.x, pos.y - 0.5, pos.z)).isAir();

                if (onGround || inWater || nearGround) {
                    data.landed = true;

                    // Fire explosion if was on fire
                    if (data.wasOnFire) {
                        world.createExplosion(null, pos.x, pos.y, pos.z,
                                FIRE_EXPLOSION_POWER, World.ExplosionSourceType.NONE);
                        world.spawnParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z,
                                20, 0.5, 0.5, 0.5, 0.1);
                        world.playSound(null, pos.x, pos.y, pos.z,
                                SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 1.5f, 1.0f);
                    }

                    // Landing particles
                    world.spawnParticles(ParticleTypes.CLOUD, pos.x, pos.y, pos.z,
                            5, 0.3, 0.1, 0.3, 0.05);

                    it.remove();
                    continue;
                }
            }

            // Expire throws after 10 seconds (200 ticks).
            if (nowTick - data.throwTick > 200) {
                it.remove();
            }
        }
    }

    static void cleanupExpiredImmunity() {
        long nowTick = TickClock.now();
        throwImmunity.entrySet().removeIf(e -> nowTick - e.getValue() > THROW_IMMUNITY_TICKS);
    }

    private static float calculateWallDamage(float hardness, double speed) {
        float baseDamage = hardness * 0.8f;
        float speedMultiplier = (float) Math.min(2.0, speed);
        return Math.max(1.0f, Math.min(10.0f, baseDamage * speedMultiplier));
    }

    private static boolean isBreakable(BlockState state) {
        // Exclude unbreakable blocks
        return !state.isOf(Blocks.BEDROCK)
                && !state.isOf(Blocks.OBSIDIAN)
                && !state.isOf(Blocks.CRYING_OBSIDIAN)
                && !state.isOf(Blocks.REINFORCED_DEEPSLATE)
                && !state.isOf(Blocks.CHEST)
                && !state.isOf(Blocks.TRAPPED_CHEST)
                && !state.isOf(Blocks.ENDER_CHEST)
                && !state.isOf(Blocks.BARREL)
                && !state.isOf(Blocks.SHULKER_BOX)
                && !state.isOf(Blocks.BEACON)
                && !state.isOf(Blocks.COMMAND_BLOCK)
                && !state.isOf(Blocks.CHAIN_COMMAND_BLOCK)
                && !state.isOf(Blocks.REPEATING_COMMAND_BLOCK);
    }

    static void cleanup(UUID playerId) {
        pendingThrows.remove(playerId);
        thrownPlayers.remove(playerId);
        throwImmunity.remove(playerId);
    }

    static void fullCleanup() {
        pendingThrows.clear();
        thrownPlayers.clear();
        throwImmunity.clear();
    }

    static void purgeExpiredCooldowns() {
        cleanupExpiredImmunity();
    }
}
