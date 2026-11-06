package com.dap.dapurhomies.interaction;

import com.dap.dapurhomies.Lang;
import com.dap.dapurhomies.config.ModConfig;
import com.dap.dapurhomies.permission.PlayerPermissionManager;
import com.dap.dapurhomies.util.TickClock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kick system - ported from original Dap-ur-homie project.
 * Features: charge system, DropKick, speed slowdown, push window.
 */
public class KickHandler {

    private static final double NORMAL_KICK_DAMAGE = 2.0;
    private static final double NORMAL_KICK_KNOCKBACK = 1.0; // 3 blocks high, 4 blocks far

    private static final double DROPKICK_RANGE = 3.0;
    private static final double DROPKICK_DAMAGE = 10.0;
    private static final double DROPKICK_KNOCKBACK = 3.5;

    private static final long CHARGE_DURATION_MS = 3000;
    private static final long PUSH_WINDOW_MS = 540;
    private static final double SLOWDOWN_AMOUNT = -0.4;
    private static final double DROPKICK_SLOWDOWN = -0.9;

    // ===== CHARGE STATE (using TickClock ticks) =====
    private static final Map<UUID, Long> chargeStartTime = new HashMap<>();

    // ===== PUSH WINDOW (using TickClock ticks) =====
    private static final Map<UUID, Long> pushWindowEnd = new HashMap<>();

    // ===== SLOWDOWN MODIFIERS =====
    private static final Identifier KICK_SLOWDOWN_ID = Identifier.of("dap-ur-homies", "kick_slowdown");
    private static final Identifier DROPKICK_SLOWDOWN_ID = Identifier.of("dap-ur-homies", "dropkick_slowdown");

    // ===== SLOWDOWN EXPIRY TRACKING (using TickClock ticks) =====
    private static final Map<UUID, Long> slowdownExpiry = new HashMap<>();
    private static final long SLOWDOWN_DURATION_TICKS = TickClock.msToTicks(5000); // 5 seconds = 100 ticks

    // ===== COOLDOWN: unified via CooldownManager only =====

    // ===== PUBLIC API =====

    public static ActionResult handleKick(ServerPlayerEntity sp, ServerPlayerEntity target) {
        if (sp == null || target == null) return ActionResult.PASS;
        if (sp.equals(target)) return ActionResult.PASS;
        if (!ModConfig.get().enableKick) return ActionResult.PASS;
        if (sp.getEntityWorld() != target.getEntityWorld()) return ActionResult.PASS;

        double dist = sp.distanceTo(target);
        if (dist > ModConfig.get().kickRange) return ActionResult.PASS;

        // Look angle check: must be looking down at target's feet
        Vec3d look = sp.getRotationVec(1.0f);
        if (look.y > -0.25) return ActionResult.PASS;

        if (!PlayerPermissionManager.canInitiate(sp, "kick")) {
            Lang.interactionBlocked(sp, "Kick is disabled for you!", "你的踢功能已关闭！");
            return ActionResult.FAIL;
        }
        if (!PlayerPermissionManager.hasPermission(sp, "dap-ur-homies.admin")
                && !PlayerPermissionManager.canReceive(target, "kick")) {
            Lang.targetDisabledType(sp, target, "kick");
            return ActionResult.FAIL;
        }

        if (InteractionCooldowns.rejectIfBlocked(sp, "kick")) return ActionResult.FAIL;

        executeNormalKick(sp, target);
        return ActionResult.SUCCESS;
    }

    // ===== CHARGE SYSTEM =====

    public static void startCharge(ServerPlayerEntity player) {
        if (player == null) return;
        if (!ModConfig.get().enableKick) return;
        if (!PlayerPermissionManager.canInitiate(player, "kick")) {
            Lang.interactionBlocked(player, "Kick is disabled for you!", "你的踢功能已关闭！");
            return;
        }
        chargeStartTime.put(player.getUuid(), TickClock.now());
        Lang.sendActionBar(player, "DropKick charging... aim at feet and right-click again after 3s",
                "飞踢蓄力中……3 秒后再次右键释放");
    }

    public static ActionResult releaseCharge(ServerPlayerEntity player) {
        return releaseCharge(player, null);
    }

    public static ActionResult releaseCharge(ServerPlayerEntity player, ServerPlayerEntity target) {
        if (player == null) return ActionResult.PASS;
        Long start = chargeStartTime.remove(player.getUuid());
        if (start == null) return ActionResult.PASS;
        if (!ModConfig.get().enableKick) return ActionResult.PASS;

        // The release must pass exactly the same gates as a normal kick / dropkick
        if (!PlayerPermissionManager.canInitiate(player, "kick")) {
            Lang.interactionBlocked(player, "Kick is disabled for you!", "你的踢功能已关闭！");
            return ActionResult.FAIL;
        }
        if (InteractionCooldowns.rejectIfBlocked(player, "kick")) return ActionResult.FAIL;

        long chargeTicks = TickClock.now() - start;
        if (chargeTicks >= TickClock.msToTicks(CHARGE_DURATION_MS)) {
            return executeDropKick(player);
        }
        // Not fully charged: behave like a normal kick, reusing its validation
        if (target != null) {
            return handleKick(player, target);
        }
        return ActionResult.PASS;
    }

    public static void cancelCharge(UUID playerId) {
        chargeStartTime.remove(playerId);
    }

    public static boolean isCharging(UUID playerId) {
        return chargeStartTime.containsKey(playerId);
    }

    // ===== NORMAL KICK =====

    private static void executeNormalKick(ServerPlayerEntity sp, ServerPlayerEntity target) {
        if (sp == null || target == null) return;
        ServerWorld w = sp.getEntityWorld();
        if (w == null) return;

        UUID id = sp.getUuid();
        // Cooldown was checked by handleKick.

        Vec3d dir = target.getEntityPos().subtract(sp.getEntityPos()).normalize();
        if (dir.lengthSquared() < 0.001) dir = new Vec3d(1, 0, 0);

        target.addVelocity(dir.x * NORMAL_KICK_KNOCKBACK, 0.35, dir.z * NORMAL_KICK_KNOCKBACK); // ~3 blocks high, 4 blocks far
        target.knockedBack = true;
        target.damage(w, w.getDamageSources().create(DamageTypes.GENERIC, sp), (float) NORMAL_KICK_DAMAGE);

        // Apply speed slowdown
        applySlowdown(target, SLOWDOWN_AMOUNT, KICK_SLOWDOWN_ID);
        slowdownExpiry.put(target.getUuid(), TickClock.now() + SLOWDOWN_DURATION_TICKS);

        // Start push window
        pushWindowEnd.put(sp.getUuid(), TickClock.now() + TickClock.msToTicks(PUSH_WINDOW_MS));

        Vec3d mid = sp.getEntityPos().add(target.getEntityPos()).multiply(0.5).add(0, 0.5, 0);
        w.spawnParticles(ParticleTypes.SWEEP_ATTACK, mid.x, mid.y, mid.z, 3, 0.2, 0.1, 0.2, 0.05);
        w.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 1.0f);

        sp.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
        Lang.kickSuccess(sp, target);
        InteractionCooldowns.apply(sp.getUuid(), "kick", ModConfig.get().kickCooldownMs);
        com.dap.dapurhomies.compat.DanseCompat.playKick(sp, target); // Danse animation
    }

    // ===== DROP KICK =====

    private static ActionResult executeDropKick(ServerPlayerEntity sp) {
        if (sp == null) return ActionResult.PASS;
        ServerWorld w = sp.getEntityWorld();
        if (w == null) return ActionResult.PASS;

        UUID id = sp.getUuid();
        // Cooldown was checked by releaseCharge.

        // DropKick affects all eligible living entities in front of the player.
        Vec3d eyePos = sp.getEyePos();
        Vec3d look = sp.getRotationVec(1.0f);

        boolean hit = false;
        Box box = sp.getBoundingBox().expand(DROPKICK_RANGE + 0.5);
        for (Entity entity : sp.getEntityWorld().getOtherEntities(sp, box,
                e -> e instanceof LivingEntity && !e.isRemoved() && !e.isSpectator())) {
            LivingEntity target = (LivingEntity) entity;
            if (target == sp) continue;
            if (target.getEntityWorld() != sp.getEntityWorld()) continue;
            if (target.getEntityPos().squaredDistanceTo(eyePos) > DROPKICK_RANGE * DROPKICK_RANGE) continue;

            // Player targets respect their receive toggle and protection claims
            if (target instanceof ServerPlayerEntity playerTarget
                    && !PlayerPermissionManager.hasPermission(sp, "dap-ur-homies.admin")) {
                if (!PlayerPermissionManager.canReceive(playerTarget, "kick")) continue;
                if (!com.dap.dapurhomies.compat.ProtectionCompat.canInteract(sp, playerTarget)) continue;
            }

            Vec3d toTarget = target.getEntityPos().subtract(eyePos).normalize();
            double dot = look.dotProduct(toTarget);
            if (dot < 0.5) continue; // Must be roughly in front

            Vec3d dir = target.getEntityPos().subtract(sp.getEntityPos()).normalize();
            if (dir.lengthSquared() < 0.001) dir = new Vec3d(1, 0, 0);

            target.addVelocity(dir.x * DROPKICK_KNOCKBACK, 0.5, dir.z * DROPKICK_KNOCKBACK); // ~5 blocks high
            target.knockedBack = true;
            target.damage(w, w.getDamageSources().create(DamageTypes.GENERIC, sp), (float) DROPKICK_DAMAGE);

            // Apply heavy slowdown (only for players)
            if (target instanceof ServerPlayerEntity playerTarget) {
                applySlowdown(playerTarget, DROPKICK_SLOWDOWN, DROPKICK_SLOWDOWN_ID);
                slowdownExpiry.put(playerTarget.getUuid(), TickClock.now() + SLOWDOWN_DURATION_TICKS);
            }

            // Start push window
            pushWindowEnd.put(sp.getUuid(), TickClock.now() + TickClock.msToTicks(PUSH_WINDOW_MS));

            Vec3d mid = sp.getEntityPos().add(target.getEntityPos()).multiply(0.5).add(0, 0.5, 0);
            w.spawnParticles(ParticleTypes.EXPLOSION, mid.x, mid.y, mid.z, 5, 0.3, 0.3, 0.3, 0.1);
            w.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.5f, 0.8f);

            hit = true;
        }

        if (hit) {
            sp.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
            Lang.sendActionBar(sp, "§c DROP KICK!", "§c 飞踢!");
            InteractionCooldowns.apply(sp.getUuid(), "kick", ModConfig.get().kickCooldownMs * 2);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    // ===== PUSH WINDOW TICK =====

    private static void tickPushWindow(MinecraftServer server) {
        long now = TickClock.now();
        Iterator<Map.Entry<UUID, Long>> it = pushWindowEnd.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (now >= entry.getValue()) {
                it.remove();
                continue;
            }

            ServerPlayerEntity kicker = server.getPlayerManager().getPlayer(entry.getKey());
            if (kicker == null || !kicker.isAlive()) {
                it.remove();
                continue;
            }

            // Apply small continuous push to entities in front
            Vec3d eyePos = kicker.getEyePos();
            Vec3d look = kicker.getRotationVec(1.0f);
            for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
                if (target == kicker) continue;
                if (target.getEntityWorld() != kicker.getEntityWorld()) continue;
                if (target.getEntityPos().squaredDistanceTo(eyePos) > 4.0) continue;

                Vec3d toTarget = target.getEntityPos().subtract(eyePos).normalize();
                if (look.dotProduct(toTarget) < 0.7) continue;

                Vec3d pushDir = target.getEntityPos().subtract(kicker.getEntityPos()).normalize();
                target.addVelocity(pushDir.x * 0.15, 0.05, pushDir.z * 0.15);
                target.knockedBack = true;
            }
        }
    }

    // ===== SLOWDOWN =====

    private static void applySlowdown(ServerPlayerEntity player, double amount, Identifier id) {
        EntityAttributeInstance speedAttr = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            // Remove existing modifier
            speedAttr.removeModifier(id);
            // Add new modifier
            EntityAttributeModifier modifier = new EntityAttributeModifier(
                    id, amount, EntityAttributeModifier.Operation.ADD_VALUE
            );
            speedAttr.addTemporaryModifier(modifier);
        }
    }

    public static void removeSlowdown(ServerPlayerEntity player, Identifier id) {
        EntityAttributeInstance speedAttr = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.removeModifier(id);
    }

    // ===== TICK =====

    /** Auto-release charges once full so a stale charge can never fire later. */
    private static void tickCharges(MinecraftServer server) {
        long now = TickClock.now();
        long fullTicks = TickClock.msToTicks(CHARGE_DURATION_MS);
        List<UUID> ready = new ArrayList<>();
        for (Map.Entry<UUID, Long> e : chargeStartTime.entrySet()) {
            if (now - e.getValue() >= fullTicks) ready.add(e.getKey());
        }
        for (UUID id : ready) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player == null) {
                cancelCharge(id);
                continue;
            }
            releaseCharge(player);
        }
    }

    public static void tick(MinecraftServer server) {
        tickPushWindow(server);
        tickCharges(server);
        long now = TickClock.now();
        Iterator<Map.Entry<UUID, Long>> slowIt = slowdownExpiry.entrySet().iterator();
        while (slowIt.hasNext()) {
            Map.Entry<UUID, Long> e = slowIt.next();
            if (now >= e.getValue()) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
                if (p != null) {
                    removeSlowdown(p, KICK_SLOWDOWN_ID);
                    removeSlowdown(p, DROPKICK_SLOWDOWN_ID);
                }
                slowIt.remove();
            }
        }
    }

    // ===== CLEANUP =====

    public static void cleanup(UUID playerId) {
        chargeStartTime.remove(playerId);
        pushWindowEnd.remove(playerId);
        slowdownExpiry.remove(playerId);
    }

    public static void purgeExpiredCooldowns() {
        // Handled by CooldownManager
    }
}

