package com.dap.dapurhomies.interaction;

import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

final class DapVisualEffects {
    static void executeHighFiveEffects(ServerPlayerEntity p1, ServerPlayerEntity p2, Vec3d pos, int tier) {
        ServerWorld world = (ServerWorld) p1.getEntityWorld();
        switch (tier) {
            case 0 -> executeTier0(world, pos, p1, p2);
            case 1 -> executeTier1(world, pos, p1, p2);
            case 2 -> executeTier2(world, pos, p1, p2);
            case 3 -> executeTier3(world, pos, p1, p2);
        }
    }

    private static void executeTier0(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.2f, 1.1f);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.8f);
        spawnStarBurst(world, pos, 10, 0.3);
        world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 8, 0.1, 0.1, 0.1, 0.08);
        world.spawnParticles(ParticleTypes.WAX_ON, pos.x, pos.y, pos.z, 6, 0.2, 0.2, 0.2, 0.02);
    }

    private static void executeTier1(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.5f, 1.0f);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 2.0f);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE, SoundCategory.PLAYERS, 0.8f, 1.2f);
        spawnStarBurst(world, pos, 16, 0.5);
        world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 15, 0.15, 0.15, 0.15, 0.12);
        world.spawnParticles(ParticleTypes.WAX_ON, pos.x, pos.y, pos.z, 10, 0.25, 0.25, 0.25, 0.03);
        world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 5, 0.2, 0.2, 0.2, 0.05);
    }

    private static void executeTier2(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 2.0f, 0.9f);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 1.2f, 1.0f);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.2f, 2.0f);
        spawnStarBurst(world, pos, 24, 0.7);
        world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 25, 0.2, 0.2, 0.2, 0.18);
        world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 15, 0.3, 0.3, 0.3, 0.1);
        world.spawnParticles(ParticleTypes.WAX_ON, pos.x, pos.y, pos.z, 15, 0.3, 0.3, 0.3, 0.05);
    }

    static void executeTier3(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.2f, 1.3f);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.PLAYERS, 1.5f, 1.0f);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 2.0f, 0.5f);
        spawnStarBurst(world, pos, 32, 1.0);
        world.spawnParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 3, 0.5, 0.5, 0.5, 0);
        world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 40, 0.3, 0.3, 0.3, 0.25);
        world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 25, 0.5, 0.5, 0.5, 0.15);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 20, 0.4, 0.4, 0.4, 0.1);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y, pos.z, 30, 0.5, 0.5, 0.5, 0.3);
        createBattleShockwave(world, pos, p1, p2, 10.0);
        p1.sendMessage(net.minecraft.text.Text.literal("§6§l⚡ SHOCKWAVE! ⚡"), true);
        p2.sendMessage(net.minecraft.text.Text.literal("§6§l⚡ SHOCKWAVE! ⚡"), true);
    }

    private static void createBattleShockwave(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2, double radius) {
        for (int ring = 1; ring <= 5; ring++) {
            double r = ring * 2.0;
            int points = (int)(r * 8);
            for (int i = 0; i < points; i++) {
                double angle = Math.toRadians((360.0 / points) * i);
                double x = pos.x + Math.cos(angle) * r;
                double z = pos.z + Math.sin(angle) * r;
                if (ring <= 2) {
                    world.spawnParticles(ParticleTypes.CLOUD, x, pos.y, z, 1, 0.1, 0.2, 0.1, 0.02);
                } else if (ring <= 4) {
                    world.spawnParticles(ParticleTypes.SWEEP_ATTACK, x, pos.y + 0.5, z, 1, 0, 0, 0, 0);
                } else {
                    world.spawnParticles(ParticleTypes.CRIT, x, pos.y + 0.5, z, 2, 0.1, 0.1, 0.1, 0.05);
                }
            }
        }
        Box pushBox = new Box(
                pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius
        );
        for (Entity entity : world.getOtherEntities(null, pushBox)) {
            if (entity == p1 || entity == p2) continue;
            double dist = entity.getEntityPos().distanceTo(pos);
            if (dist > radius || dist < 0.5) continue;
            double strength = (1.0 - (dist / radius)) * 4.0 + 1.0;
            Vec3d dir = entity.getEntityPos().subtract(pos).normalize();
            if (dir.lengthSquared() < 0.01) {
                dir = new Vec3d(Math.random() - 0.5, 0, Math.random() - 0.5).normalize();
            }
            entity.addVelocity(dir.x * strength, strength * 0.6, dir.z * strength);
            entity.knockedBack = true;
            world.spawnParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + 1, entity.getZ(), 5, 0.2, 0.2, 0.2, 0.1);
        }
    }

    private static void spawnStarBurst(ServerWorld world, Vec3d pos, int rays, double spread) {
        for (int i = 0; i < rays; i++) {
            double angle = (2 * Math.PI * i) / rays;
            double dx = Math.cos(angle) * spread;
            double dz = Math.sin(angle) * spread;
            world.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 2, dx, 0.2, dz, 0.15);
        }
    }
}
