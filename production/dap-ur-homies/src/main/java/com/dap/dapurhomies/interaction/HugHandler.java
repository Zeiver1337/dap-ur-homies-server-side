package com.dap.dapurhomies.interaction;

import com.dap.dapurhomies.Lang;
import com.dap.dapurhomies.config.ModConfig;
import com.dap.dapurhomies.permission.PlayerPermissionManager;
import com.dap.dapurhomies.util.TickClock;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HugHandler {
    private static final Map<UUID, UUID> hugPairs = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> hugStart = new ConcurrentHashMap<>();
    private static final long HUG_DURATION_TICKS = TickClock.msToTicks(3000);

    public static ActionResult handleHug(ServerPlayerEntity sp, ServerPlayerEntity target) {
        if (sp == null || target == null) return ActionResult.PASS;
        if (!ModConfig.get().enableHug) return ActionResult.PASS;
        if (!sp.isAlive() || !target.isAlive()) return ActionResult.PASS;
        if (sp.getEntityWorld() != target.getEntityWorld()) return ActionResult.PASS;
        if (!PlayerPermissionManager.canInitiate(sp, "hug")) {
            Lang.interactionBlocked(sp, "Hug is disabled for you!", "你的拥抱功能已关闭！");
            return ActionResult.FAIL;
        }
        if (InteractionCooldowns.rejectIfBlocked(sp, "hug")) return ActionResult.FAIL;
        if (!PlayerPermissionManager.hasPermission(sp, "dap-ur-homies.admin")
                && !PlayerPermissionManager.canReceive(target, "hug")) {
            Lang.targetDisabledType(sp, target, "hug");
            return ActionResult.FAIL;
        }
        double dist = sp.distanceTo(target);
        if (dist > ModConfig.get().hugRange) return ActionResult.PASS;
        if (!startHug(sp, target)) return ActionResult.PASS;
        return ActionResult.SUCCESS;
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        long now = TickClock.now();
        Iterator<Map.Entry<UUID, UUID>> it = hugPairs.entrySet().iterator();
        Set<UUID> processed = new HashSet<>();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> e = it.next();
            if (processed.contains(e.getKey())) continue;
            UUID id1 = e.getKey(), id2 = e.getValue();
            processed.add(id1); processed.add(id2);

            ServerPlayerEntity p1 = server.getPlayerManager().getPlayer(id1);
            ServerPlayerEntity p2 = server.getPlayerManager().getPlayer(id2);
            if (p1 == null || p2 == null || !p1.isAlive() || !p2.isAlive()
                    || p1.getEntityWorld() != p2.getEntityWorld()) {
                if (p1 != null && p2 != null) clearHugEffects(p1, p2);
                it.remove();
                hugPairs.remove(id2);
                hugStart.remove(id1);
                hugStart.remove(id2);
                continue;
            }

            long elapsed = now - hugStart.getOrDefault(id1, now);
            if (elapsed > HUG_DURATION_TICKS) {
                endHug(p1, p2);
                it.remove();
                hugPairs.remove(id2);
                hugStart.remove(id1);
                hugStart.remove(id2);
                processed.add(id2);
                continue;
            }

            // Apply slowness/mining fatigue with longer duration (refresh every 2 seconds instead of every tick)
            // This avoids creating 4 StatusEffectInstance objects per tick per hug pair
            if (now % 40 == 0 || p1.getStatusEffect(StatusEffects.SLOWNESS) == null) {
                p1.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 5, false, false));
                p1.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 60, 4, false, false));
            }
            if (now % 40 == 0 || p2.getStatusEffect(StatusEffects.SLOWNESS) == null) {
                p2.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 5, false, false));
                p2.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 60, 4, false, false));
            }

            if (now % 20 == 0) {
                p1.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 60, 0, false, false));
                p2.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 60, 0, false, false));
                Vec3d mid = p1.getEntityPos().add(p2.getEntityPos()).multiply(0.5);
                ServerWorld w = p1.getEntityWorld();
                if (w != null) {
                    w.spawnParticles(ParticleTypes.HEART, mid.x, mid.y + 1.2, mid.z, 2, 0.3, 0.3, 0.3, 0);
                    w.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.3f, 1.5f);
                }
            }
        }
    }

    public static boolean isHugging(UUID id) {
        return hugPairs.containsKey(id);
    }

    public static void cleanup(UUID id) {
        UUID partner = hugPairs.remove(id);
        if (partner != null) {
            hugPairs.remove(partner);
            hugStart.remove(partner);
        }
        hugStart.remove(id);
    }

    private static boolean startHug(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        if (hugPairs.containsKey(p1.getUuid()) || hugPairs.containsKey(p2.getUuid())) return false;
        long now = TickClock.now();
        hugPairs.put(p1.getUuid(), p2.getUuid());
        hugPairs.put(p2.getUuid(), p1.getUuid());
        hugStart.put(p1.getUuid(), now);
        hugStart.put(p2.getUuid(), now);
        Lang.hugStart(p1, p2);
        com.dap.dapurhomies.compat.DanseCompat.playHug(p1, p2); // Danse animation
        return true;
    }

    /** Remove only the amplified effects this handler applied, leaving other sources untouched. */
    private static void clearHugEffects(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        clearHugEffects(p1);
        clearHugEffects(p2);
    }

    private static void clearHugEffects(ServerPlayerEntity player) {
        StatusEffectInstance slow = player.getStatusEffect(StatusEffects.SLOWNESS);
        if (slow != null && slow.getAmplifier() >= 5) player.removeStatusEffect(StatusEffects.SLOWNESS);
        StatusEffectInstance fatigue = player.getStatusEffect(StatusEffects.MINING_FATIGUE);
        if (fatigue != null && fatigue.getAmplifier() >= 4) player.removeStatusEffect(StatusEffects.MINING_FATIGUE);
    }

    private static void endHug(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        clearHugEffects(p1, p2);
        Lang.hugEnd(p1, p2);
        InteractionCooldowns.applyShared(p1.getUuid(), p2.getUuid(), "hug", ModConfig.get().hugCooldownMs);
    }
}

