package com.dap.dapurhomies.interaction;

import com.dap.dapurhomies.Lang;
import com.dap.dapurhomies.config.ModConfig;
import com.dap.dapurhomies.permission.PlayerPermissionManager;
import com.dap.dapurhomies.util.CooldownManager;
import com.dap.dapurhomies.util.TickClock;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DapHandler {
    private static final Map<UUID, Long> pendingHighFive = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> chargeStart = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> chargeLevels = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> chargeTarget = new ConcurrentHashMap<>();
    private static final long HIGH_FIVE_WINDOW_TICKS = TickClock.msToTicks(800);
    private static final long[] CHARGE_THRESHOLDS = {0, 10, 20, 30, 40, 50};
    private static final long MAX_CHARGE_TICKS = TickClock.msToTicks(3000);
    private static final long CHARGE_TIMEOUT_TICKS = 200;
    private static final long CYCLE_CHARGE_TICKS = TickClock.msToTicks(8000); // 8s for cycle mode
    private static final long CYCLE_CHARGE_TIMEOUT_TICKS = 400; // 20s for cycle mode

    // Perfect Dap: a max-level release stays "answerable" for this long (1.5s).
    // Replaces the old 2-tick charge-START sync requirement which was humanly impossible.
    private static final long PERFECT_WINDOW_TICKS = TickClock.msToTicks(1500);
    private static final Map<UUID, PendingPerfect> pendingPerfect = new ConcurrentHashMap<>();

    private record PendingPerfect(UUID targetId, int level, long releaseTick) {}

    private static final int SPEED_HISTORY_TICKS = 30;
    private static final double SPEED_TIER_1 = 5.0, SPEED_TIER_2 = 7.5, SPEED_TIER_3 = 12.0;
    private static final Map<UUID, LinkedList<Double>> speedHistory = new ConcurrentHashMap<>();

    private static final Map<UUID, PendingHighFive> pendingEffects = new ConcurrentHashMap<>();
    private static final long HIT_EFFECT_DELAY_TICKS = TickClock.msToTicks(100);

    private static class PendingHighFive {
        final ServerPlayerEntity p1, p2;
        final Vec3d pos;
        final int tier;
        final long effectTime;
        PendingHighFive(ServerPlayerEntity p1, ServerPlayerEntity p2, Vec3d pos, int tier, long effectTime) {
            this.p1 = p1; this.p2 = p2; this.pos = pos; this.tier = tier; this.effectTime = effectTime;
        }
    }

    public static void tick(MinecraftServer server) {
        long now = TickClock.now();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            tickSpeedHistory(player);
            tickCharge(player);
        }
        Iterator<Map.Entry<UUID, PendingHighFive>> it = pendingEffects.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PendingHighFive> e = it.next();
            if (now >= e.getValue().effectTime) {
                PendingHighFive fx = e.getValue();
                boolean online = server.getPlayerManager().getPlayer(fx.p1.getUuid()) != null
                        && server.getPlayerManager().getPlayer(fx.p2.getUuid()) != null;
                if (online) {
                    DapVisualEffects.executeHighFiveEffects(fx.p1, fx.p2, fx.pos, fx.tier);
                }
                it.remove();
            }
        }
    }

    private static void tickSpeedHistory(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        double speed = player.getVelocity().length() * 20.0;
        LinkedList<Double> history = speedHistory.computeIfAbsent(id, k -> new LinkedList<>());
        history.addLast(speed);
        while (history.size() > SPEED_HISTORY_TICKS) history.removeFirst();
    }

    private static double getMaxRecentSpeed(UUID playerId) {
        LinkedList<Double> history = speedHistory.get(playerId);
        if (history == null || history.isEmpty()) return 0.0;
        double max = 0.0;
        for (Double s : history) if (s > max) max = s;
        return max;
    }

    public static ActionResult startCharge(ServerPlayerEntity sp, ServerPlayerEntity target) {
        if (sp == null || target == null) return ActionResult.PASS;
        if (!ModConfig.get().enableDap) return ActionResult.PASS;
        if (sp.getEntityWorld() != target.getEntityWorld()) return ActionResult.PASS;
        if (sp.distanceTo(target) > ModConfig.get().dapRange) return ActionResult.PASS;
        if (!PlayerPermissionManager.canInitiate(sp, "dap")) {
            Lang.interactionBlocked(sp, "Dap is disabled for you!", "你的碰拳功能已关闭！");
            return ActionResult.FAIL;
        }
        if (InteractionCooldowns.rejectIfBlocked(sp, "dap")) return ActionResult.FAIL;
        if (!PlayerPermissionManager.hasPermission(sp, "dap-ur-homies.admin")
                && !PlayerPermissionManager.canReceive(target, "dap")) {
            Lang.targetDisabledType(sp, target, "dap");
            return ActionResult.FAIL;
        }
        if (DapSessionManager.isInSession(sp.getUuid()) || DapSessionManager.isInSession(target.getUuid()))
            return ActionResult.PASS;

        // Mode 1 (cycle-charge): if already charging, release instead
        // In cycle mode, a second click releases the current charge; this must precede
        // the general charging check below, which would otherwise return PASS first.
        if (ModConfig.get().chargedDapMode == 1 && isCharging(sp.getUuid())) {
            releaseCharge(sp);
            return ActionResult.SUCCESS;
        }

        if (isCharging(sp.getUuid())) return ActionResult.PASS;
        // Mutual charging is allowed; reject only when the target is charging someone else.
        // Perfect Dap requires both players to be able to charge each other.
        UUID targetChargeTarget = chargeTarget.get(target.getUuid());
        if (targetChargeTarget != null && !targetChargeTarget.equals(sp.getUuid()))
            return ActionResult.PASS;

        chargeStart.put(sp.getUuid(), TickClock.now());
        chargeLevels.put(sp.getUuid(), 0);
        chargeTarget.put(sp.getUuid(), target.getUuid());
        Lang.chargeStart(sp);
        return ActionResult.SUCCESS;
    }

    public static ActionResult handleNormalDap(ServerPlayerEntity sp, ServerPlayerEntity target) {
        if (sp == null || target == null) return ActionResult.PASS;
        if (!ModConfig.get().enableDap) return ActionResult.PASS;
        if (sp.getEntityWorld() != target.getEntityWorld()) return ActionResult.PASS;
        if (sp.distanceTo(target) > ModConfig.get().dapRange) return ActionResult.PASS;
        if (!PlayerPermissionManager.canInitiate(sp, "dap")) {
            Lang.interactionBlocked(sp, "Dap is disabled for you!", "你的碰拳功能已关闭！");
            return ActionResult.FAIL;
        }
        long now = TickClock.now();
        if (InteractionCooldowns.rejectIfBlocked(sp, "dap")) return ActionResult.FAIL;
        if (!PlayerPermissionManager.hasPermission(sp, "dap-ur-homies.admin")
                && !PlayerPermissionManager.canReceive(target, "dap")) {
            Lang.targetDisabledType(sp, target, "dap");
            return ActionResult.FAIL;
        }
        if (DapSessionManager.isInSession(sp.getUuid()) || DapSessionManager.isInSession(target.getUuid()))
            return ActionResult.PASS;
        if (isCharging(sp.getUuid()) || isCharging(target.getUuid()))
            return ActionResult.PASS;
        UUID sid = sp.getUuid(), tid = target.getUuid();
        Long pending = pendingHighFive.get(tid);
        boolean highFiveAllowed = ModConfig.get().enableHighFive
                && sp.distanceTo(target) <= ModConfig.get().highFiveRange;
        if (highFiveAllowed && pending != null && now - pending < HIGH_FIVE_WINDOW_TICKS) {
            if (!PlayerPermissionManager.canInitiate(sp, "highFive")) {
                Lang.interactionBlocked(sp, "High-five is disabled for you!", "你的击掌功能已关闭！");
                return ActionResult.FAIL;
            }
            if (!PlayerPermissionManager.hasPermission(sp, "dap-ur-homies.admin")
                    && !PlayerPermissionManager.canReceive(target, "highFive")) {
                Lang.targetDisabledType(sp, target, "highFive");
                return ActionResult.FAIL;
            }
            if (InteractionCooldowns.rejectIfBlocked(sp, "highFive")) return ActionResult.FAIL;
            executeHighFive(sp, target);
            InteractionCooldowns.applyShared(sid, tid, "highFive", ModConfig.get().highFiveCooldownMs);
            pendingHighFive.remove(tid); pendingHighFive.remove(sid);
            return ActionResult.SUCCESS;
        }
        pendingHighFive.put(sid, now);
        executeDap(sp, target, 1);
        InteractionCooldowns.apply(sid, "dap", ModConfig.get().dapCooldownMs);
        return ActionResult.SUCCESS;
    }

    public static void tickCharge(ServerPlayerEntity player) {
        if (player == null) return;
        UUID id = player.getUuid();
        Long start = chargeStart.get(id);
        if (start == null) return;
        long elapsed = TickClock.now() - start;

        int mode = ModConfig.get().chargedDapMode;
        if (mode == 0) {
            // Mode 0: hold-charge (original) — release when not sneaking or exceeded max charge time
            if (!player.isSneaking() || elapsed > MAX_CHARGE_TICKS) { releaseCharge(player); return; }
            int newLevel = 0;
            for (int i = CHARGE_THRESHOLDS.length - 1; i >= 0; i--) {
                if (elapsed >= CHARGE_THRESHOLDS[i]) { newLevel = i; break; }
            }
            Integer oldLevel = chargeLevels.get(id);
            if (oldLevel == null || newLevel != oldLevel) {
                chargeLevels.put(id, newLevel);
                Lang.chargeLevel(player, newLevel);
            }
        } else {
            // Mode 1: cycle-charge — 8s session with looping levels 0->5->0 (triangular wave)
            if (elapsed > CYCLE_CHARGE_TICKS) { releaseCharge(player); return; }
            double progress = (double) elapsed / CYCLE_CHARGE_TICKS;
            double tri = progress < 0.5 ? progress * 2.0 : (1.0 - progress) * 2.0;
            int newLevel = (int) Math.round(tri * 5.0);
            newLevel = Math.max(0, Math.min(5, newLevel));
            Integer oldLevel = chargeLevels.get(id);
            if (oldLevel == null || newLevel != oldLevel) {
                chargeLevels.put(id, newLevel);
                Lang.chargeLevel(player, newLevel);
            }
        }

        if (TickClock.now() % 4 == 0) {
            ServerWorld w = (ServerWorld) player.getEntityWorld();
            if (w != null) {
                Vec3d pos = player.getEntityPos().add(0, 1, 0);
                w.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 2, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }

    public static boolean isCharging(UUID id) { return chargeStart.containsKey(id); }

    /** Abort a charge without firing any effect (used when another interaction wins). */
    public static void cancelCharge(UUID id) {
        if (id == null) return;
        chargeStart.remove(id);
        chargeLevels.remove(id);
        chargeTarget.remove(id);
    }

    public static void releaseCharge(ServerPlayerEntity player) {
        if (player == null) return;
        UUID id = player.getUuid();
        Long start = chargeStart.remove(id);
        Integer level = chargeLevels.remove(id);
        UUID targetId = chargeTarget.remove(id);
        if (start == null) return;
        int powerLevel = (level != null) ? level : 0;
        ServerPlayerEntity target = null;
        if (targetId != null) {
            var server = ((ServerWorld) player.getEntityWorld()).getServer();
            if (server != null) target = server.getPlayerManager().getPlayer(targetId);
        }
        if (target == null || target.getEntityWorld() != player.getEntityWorld()
                || player.distanceTo(target) > ModConfig.get().dapRange) {
            Lang.chargeCancelled(player);
            InteractionCooldowns.apply(id, "dap", ModConfig.get().dapCooldownMs);
            return;
        }

        int maxLevel = CHARGE_THRESHOLDS.length - 1;
        long now = TickClock.now();

        // Perfect Dap (v0.6.0 rework): I must release at max level, and the partner qualifies by EITHER
        //   a) still max-charging AT ME right now, OR
        //   b) having max-released AT ME within PERFECT_WINDOW_TICKS (pending-release window).
        // The old Mode-0 rule compared charge-START times with a 2-tick (0.1s) tolerance,
        // which was humanly impossible to satisfy.
        Long partnerStart = chargeStart.get(target.getUuid());
        Integer partnerLevel = chargeLevels.get(target.getUuid());
        UUID partnerTarget = chargeTarget.get(target.getUuid());
        boolean partnerIsChargingMe = partnerTarget != null && partnerTarget.equals(id);
        boolean partnerCurrentlyMax = partnerIsChargingMe && partnerStart != null
                && partnerLevel != null && partnerLevel >= maxLevel;

        PendingPerfect pp = pendingPerfect.get(target.getUuid());
        boolean partnerRecentlyReleasedMax = pp != null && pp.targetId().equals(id)
                && pp.level() >= maxLevel && now - pp.releaseTick() <= PERFECT_WINDOW_TICKS;

        boolean bothPerfect = powerLevel >= maxLevel && (partnerCurrentlyMax || partnerRecentlyReleasedMax);

        // Record my max-level release so the partner can answer within the window
        if (powerLevel >= maxLevel) {
            if (bothPerfect) {
                pendingPerfect.remove(target.getUuid());
                pendingPerfect.remove(id);
            } else {
                pendingPerfect.put(id, new PendingPerfect(target.getUuid(), powerLevel, now));
            }
        }

        if (bothPerfect) {
            // consume partner's charge (if still charging)
            chargeStart.remove(target.getUuid());
            chargeLevels.remove(target.getUuid());
            chargeTarget.remove(target.getUuid());
            executePerfectDap(player, target);
            Lang.chargeRelease(player, powerLevel);
            if (partnerLevel != null) Lang.chargeRelease(target, partnerLevel);
            InteractionCooldowns.apply(id, "dap", ModConfig.get().dapCooldownMs);
            InteractionCooldowns.apply(target.getUuid(), "dap", ModConfig.get().dapCooldownMs);
            return;
        }

        executeDap(player, target, powerLevel + 1);
        Lang.chargeRelease(player, powerLevel);
        InteractionCooldowns.apply(id, "dap", ModConfig.get().dapCooldownMs);
        InteractionCooldowns.apply(target.getUuid(), "dap", ModConfig.get().dapCooldownMs);
    }

    public static void cleanup(UUID playerId) {
        pendingHighFive.remove(playerId);
        pendingPerfect.remove(playerId);
        pendingPerfect.entrySet().removeIf(e -> e.getValue().targetId().equals(playerId));
        chargeStart.remove(playerId);
        chargeLevels.remove(playerId);
        chargeTarget.remove(playerId);
        Iterator<Map.Entry<UUID, UUID>> it = chargeTarget.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> e = it.next();
            if (e.getValue().equals(playerId)) {
                chargeStart.remove(e.getKey());
                chargeLevels.remove(e.getKey());
                it.remove();
            }
        }
        speedHistory.remove(playerId);
        pendingEffects.remove(playerId);
    }

    public static void purgeExpiredCooldowns() {
        long now = TickClock.now();
        pendingHighFive.entrySet().removeIf(e -> now - e.getValue() > HIGH_FIVE_WINDOW_TICKS * 2);
        pendingPerfect.entrySet().removeIf(e -> now - e.getValue().releaseTick() > PERFECT_WINDOW_TICKS * 2);
        int mode = ModConfig.get().chargedDapMode;
        long timeout = (mode == 0) ? CHARGE_TIMEOUT_TICKS : CYCLE_CHARGE_TIMEOUT_TICKS;
        chargeStart.entrySet().removeIf(e -> {
            if (now - e.getValue() > timeout) {
                chargeLevels.remove(e.getKey());
                chargeTarget.remove(e.getKey());
                return true;
            }
            return false;
        });
    }

    private static void executeDap(ServerPlayerEntity sp, ServerPlayerEntity target, int power) {
        if (sp == null || target == null) return;
        ServerWorld w = (ServerWorld) sp.getEntityWorld();
        if (w == null) return;
        DapSession session = DapSessionManager.createSession(
                sp.getUuid(), target.getUuid(), 1.2, DapSession.DapType.NORMAL_DAP);
        Runnable effects = () -> {
            Vec3d mid = sp.getEntityPos().add(target.getEntityPos()).multiply(0.5).add(0, 1.2, 0);
            int particleCount = 4 + power * 2;
            w.spawnParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, particleCount, 0.2, 0.2, 0.2, 0.1);
            w.spawnParticles(ParticleTypes.ENCHANTED_HIT, mid.x, mid.y, mid.z, power, 0.15, 0.15, 0.15, 0.08);
            float pitch = 1.0f + power * 0.1f;
            // Single clap sound for dap (not a 2-second loop)
            w.playSound(null, mid.x, mid.y, mid.z, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 0.9f, pitch);
            sp.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
            Lang.dapSuccess(sp, target);
            com.dap.dapurhomies.compat.DanseCompat.playDap(sp, target);
        };
        if (session != null) session.onComplete(effects);
        else effects.run();
    }

    private static void executeHighFive(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        if (p1 == null || p2 == null) return;
        ServerWorld w = (ServerWorld) p1.getEntityWorld();
        if (w == null) return;
        double speed1 = getMaxRecentSpeed(p1.getUuid());
        double speed2 = getMaxRecentSpeed(p2.getUuid());
        double maxSpeed = Math.max(speed1, speed2);
        int tier = 0;
        if (maxSpeed >= SPEED_TIER_3) tier = 3;
        else if (maxSpeed >= SPEED_TIER_2) tier = 2;
        else if (maxSpeed >= SPEED_TIER_1) tier = 1;
        DapSession session = DapSessionManager.createSession(
                p1.getUuid(), p2.getUuid(), 1.0, DapSession.DapType.NORMAL_DAP);
        Vec3d highFivePos = p1.getEntityPos().add(p2.getEntityPos()).multiply(0.5).add(0, 1.5, 0);
        speedHistory.remove(p1.getUuid());
        speedHistory.remove(p2.getUuid());
        final ServerPlayerEntity fp1 = p1, fp2 = p2;
        final Vec3d fPos = highFivePos;
        final int fTier = tier;
        Runnable onComplete = () -> {
            pendingEffects.put(fp1.getUuid(), new PendingHighFive(fp1, fp2, fPos, fTier, TickClock.now() + HIT_EFFECT_DELAY_TICKS));
            fp1.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
            fp2.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
            Lang.highFiveSuccess(fp1, fp2, fTier);
            // Perfect high-five (tier 3) plays Danse dap animation
            if (fTier >= 3) {
                com.dap.dapurhomies.compat.DanseCompat.playDap(fp1, fp2);
            } else {
                com.dap.dapurhomies.compat.DanseCompat.playHighFive(fp1, fp2);
            }
            // High-five has its own tier-based sound effects in executeHighFiveEffects(),
            // no need for a 2-second clap loop here
        };
        if (session != null) session.onComplete(onComplete);
        else onComplete.run();
    }

    // Perfect Dap (v0.6.0): dedicated tier-3-level celebration instead of a plain power-6 dap
    private static void executePerfectDap(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        if (p1 == null || p2 == null) return;
        ServerWorld w = (ServerWorld) p1.getEntityWorld();
        if (w == null) return;
        DapSession session = DapSessionManager.createSession(
                p1.getUuid(), p2.getUuid(), 1.2, DapSession.DapType.NORMAL_DAP);
        Vec3d mid = p1.getEntityPos().add(p2.getEntityPos()).multiply(0.5).add(0, 1.2, 0);
        Runnable effects = () -> {
            p1.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
            p2.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
            com.dap.dapurhomies.compat.DanseCompat.playDap(p1, p2);
            DapVisualEffects.executeTier3(w, mid, p1, p2);
            Lang.sendActionBar(p1, "§6§l⚡ PERFECT DAP! ⚡", "§6§l⚡ 完美击拳! ⚡");
            Lang.sendActionBar(p2, "§6§l⚡ PERFECT DAP! ⚡", "§6§l⚡ 完美击拳! ⚡");
        };
        if (session != null) session.onComplete(effects);
        else effects.run();
    }

    public static boolean isInBlockingState(UUID id) {
        return CooldownManager.isOnCooldown(id, "dap") || DapSessionManager.isInSession(id);
    }
}

