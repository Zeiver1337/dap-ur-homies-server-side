package com.dap.dapurhomies.interaction;

import com.dap.dapurhomies.Lang;
import com.dap.dapurhomies.config.ModConfig;
import com.dap.dapurhomies.permission.PlayerPermissionManager;
import com.dap.dapurhomies.util.CooldownManager;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple interactions: slap, clap, mario jump.
 * Uses ConcurrentHashMap for thread safety and CooldownManager for unified cooldowns.
 */
public class SimpleInteractions {
    private static final Map<UUID, Integer> marioJumpTicks = new ConcurrentHashMap<>();

    public static void tick(net.minecraft.server.MinecraftServer server) {
        Iterator<Map.Entry<UUID, Integer>> it = marioJumpTicks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            if (p == null || !p.isAlive()) { it.remove(); continue; }
            int tick = e.getValue();
            if (tick >= 30) { it.remove(); continue; }
            if (tick < 8) { p.addVelocity(0, 0.35, 0); }
            else if (tick < 15) { p.addVelocity(0, 0.15, 0); }
            if (tick % 2 == 0) {
                Vec3d pos = p.getEntityPos().add(0, 0.5, 0);
                ServerWorld pw = p.getEntityWorld();
                if (pw != null) {
                    pw.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 2, 0.2, 0.2, 0.2, 0.02);
                }
            }
            e.setValue(tick + 1);
        }
    }

    public static ActionResult handleSlap(ServerPlayerEntity sp, ServerPlayerEntity target) {
        if (sp == null || target == null) return ActionResult.PASS;
        if (!ModConfig.get().enableSlap) return ActionResult.PASS;
        if (sp.distanceTo(target) > ModConfig.get().slapRange) return ActionResult.PASS;
        if (!PlayerPermissionManager.canInitiate(sp, "slap")) {
            Lang.interactionBlocked(sp, "Slap is disabled for you!", "你的拍巴掌功能已关闭！");
            return ActionResult.FAIL;
        }
        if (InteractionCooldowns.rejectIfBlocked(sp, "slap")) return ActionResult.FAIL;
        if (!PlayerPermissionManager.hasPermission(sp, "dap-ur-homies.admin")
                && !PlayerPermissionManager.canReceive(target, "slap")) {
            Lang.targetDisabledType(sp, target, "slap");
            return ActionResult.FAIL;
        }
        Vec3d targetLook = target.getRotationVec(1.0f);
        Vec3d toAttacker = sp.getEntityPos().subtract(target.getEntityPos()).normalize();
        double dot = targetLook.dotProduct(toAttacker);
        // A slap must come from behind: dot <= 0 means the target is facing away.
        // A positive dot means the target faces the attacker, so this is not a slap.
        if (dot > 0.0) return ActionResult.PASS;
        executeSlap(sp, target);
        return ActionResult.SUCCESS;
    }

    private static void executeSlap(ServerPlayerEntity sp, ServerPlayerEntity target) {
        if (sp == null || target == null) return;
        ServerWorld w = sp.getEntityWorld();
        if (w == null) return;
        Vec3d hitPos = target.getEntityPos().add(0, 1.6, 0);
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 3, false, false));
        w.spawnParticles(ParticleTypes.CRIT, hitPos.x, hitPos.y, hitPos.z, 8, 0.15, 0.1, 0.15, 0.15);
        w.spawnParticles(ParticleTypes.SWEEP_ATTACK, hitPos.x, hitPos.y, hitPos.z, 3, 0.1, 0.05, 0.1, 0.05);
        w.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0f, 1.8f);
        sp.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
        Lang.slapSuccess(sp, target);
        InteractionCooldowns.apply(sp.getUuid(), "slap", ModConfig.get().slapCooldownMs);
        com.dap.dapurhomies.compat.DanseCompat.playSlap(sp, target); // Danse animation
    }

    public static void onClap(ServerPlayerEntity player) {
        if (player == null) return;
        if (!ModConfig.get().enableClap) return;
        if (!PlayerPermissionManager.canInitiate(player, "clap")) {
            Lang.interactionBlocked(player, "Clap is disabled for you!", "你的鼓掌功能已关闭！");
            return;
        }
        if (InteractionCooldowns.isBlocked(player, "clap")) return;
        ServerWorld w = player.getEntityWorld();
        if (w == null) return;
        Vec3d pos = player.getEntityPos().add(0, 1.2, 0);
        w.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 4, 0.1, 0.1, 0.1, 0.03);
        // Use ClapLooper for a rhythmic 2-second clap sound effect
        com.dap.dapurhomies.util.ClapLooper.start(w, pos,
                com.dap.dapurhomies.config.ModConfig.get().highFiveSoundTicks,
                com.dap.dapurhomies.config.ModConfig.get().clapIntervalTicks);
        player.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
        Lang.clapSuccess(player);
        InteractionCooldowns.apply(player.getUuid(), "clap", 500);
        com.dap.dapurhomies.compat.DanseCompat.playClap(player); // Danse animation
    }

    public static void onMarioJump(ServerPlayerEntity player) {
        if (player == null) return;
        if (!PlayerPermissionManager.canInitiate(player, "dap")) return;
        if (InteractionCooldowns.isBlocked(player, "mario")) return;
        ServerWorld w = player.getEntityWorld();
        if (w == null) return;
        Vec3d pos = player.getEntityPos();
        w.spawnParticles(ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 10, 0.4, 0.1, 0.4, 0.08);
        w.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.5, pos.z, 5, 0.2, 0.3, 0.2, 0.03);
        w.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.PLAYERS, 1.0f, 1.5f);
        w.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_PLAYER_SMALL_FALL, SoundCategory.PLAYERS, 0.6f, 1.8f);
        marioJumpTicks.put(player.getUuid(), 0);
        CooldownManager.setCooldown(player.getUuid(), "mario", 60); // 60 ticks = 3s
        Lang.marioJumpSuccess(player);
        com.dap.dapurhomies.compat.DanseCompat.playMarioJump(player); // Danse animation
    }

    public static void cleanup(UUID playerId) {
        marioJumpTicks.remove(playerId);
    }
}

