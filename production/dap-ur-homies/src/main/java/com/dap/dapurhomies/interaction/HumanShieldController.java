package com.dap.dapurhomies.interaction;

import com.dap.dapurhomies.DapUrHomiesMod;
import com.dap.dapurhomies.Lang;
import com.dap.dapurhomies.util.TickClock;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class HumanShieldController {
    private static final Map<UUID, ArmorStandEntity> shieldStands = new HashMap<>(); // holder -> armorstand
    private static final Map<UUID, Boolean> shieldModeActive = new HashMap<>();     // holder -> active
    private static final Map<UUID, Long> shieldSwapCooldown = new HashMap<>();      // holder -> cooldown end time
    private static final long SHIELD_SWAP_COOLDOWN_TICKS = TickClock.msToTicks(1000); // 1 second cooldown

    private HumanShieldController() {
    }

    static ActionResult toggleShieldMode(ServerPlayerEntity holder) {
        if (holder == null) return ActionResult.PASS;
        UUID hid = holder.getUuid();
        if (GrabYeetHandler.getHeldId(hid) == null) return ActionResult.PASS;

        // Check shield swap cooldown
        Long cooldownEnd = shieldSwapCooldown.get(hid);
        if (cooldownEnd != null && TickClock.now() < cooldownEnd) {
            long remainingTicks = cooldownEnd - TickClock.now();
            long remaining = (remainingTicks + 19) / 20;
            Lang.sendActionBar(holder, "\u00a7c\ud83d\udee1 Shield swap cooldown! " + remaining + "s",
                "\u00a7c\ud83d\udee1 \u76fe\u724c\u5207\u6362\u51b7\u5374\u4e2d\uff01\u8fd8\u5269 " + remaining + "\u79d2");
            return ActionResult.FAIL;
        }

        if (Boolean.TRUE.equals(shieldModeActive.get(hid))) {
            disableShieldMode(holder, true);
        } else {
            enableShieldMode(holder);
        }
        return ActionResult.SUCCESS;
    }

    private static void enableShieldMode(ServerPlayerEntity holder) {
        UUID hid = holder.getUuid();
        UUID tid = GrabYeetHandler.getHeldId(hid);
        if (tid == null) return;

        var server = holder.getEntityWorld().getServer();
        if (server == null) return;
        ServerPlayerEntity held = server.getPlayerManager().getPlayer(tid);
        if (held == null) return;

        // Create armor stand as intermediate carrier
        ArmorStandEntity stand = new ArmorStandEntity(EntityType.ARMOR_STAND, holder.getEntityWorld());
        stand.setInvisible(true);
        // stand.setMarker(true); // requires accesswidener
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        // stand.setSmall(true); // requires accesswidener

        Vec3d pos = holder.getEntityPos();
        stand.setPos(pos.x, pos.y, pos.z);
        (holder.getEntityWorld()).spawnEntity(stand);

        // Move held player to stand
        held.stopRiding();
        if (!held.startRiding(stand, true, false)) {
            // If the stand cannot accept the passenger, discard it and abort shield mode.
            stand.discard();
            return;
        }

        shieldStands.put(hid, stand);
        shieldModeActive.put(hid, true);
        shieldSwapCooldown.put(hid, TickClock.now() + SHIELD_SWAP_COOLDOWN_TICKS);

        Lang.sendActionBar(holder, "\u00a7aHuman shield activated!");
    }

    static void disableShieldMode(ServerPlayerEntity holder, boolean notify) {
        UUID hid = holder.getUuid();
        UUID tid = GrabYeetHandler.getHeldId(hid);
        if (tid == null) return;

        var server = holder.getEntityWorld().getServer();
        if (server == null) return;
        ArmorStandEntity stand = shieldStands.get(hid);

        if (stand != null) {
            if (stand.getFirstPassenger() != null) stand.getFirstPassenger().stopRiding();
            stand.discard();
            shieldStands.remove(hid);
        }

        // Never re-ride the holder: passenger sync would override the authoritative
        // requestTeleport applied by tickCarryPositions().
        shieldModeActive.put(hid, false);
        shieldSwapCooldown.put(hid, TickClock.now() + SHIELD_SWAP_COOLDOWN_TICKS);
        if (notify) {
            Lang.sendActionBar(holder, "§cHuman shield deactivated!");
        }
    }

    static boolean isActive(UUID holderId) {
        return Boolean.TRUE.equals(shieldModeActive.get(holderId));
    }

    static ArmorStandEntity getStand(UUID holderId) {
        return shieldStands.get(holderId);
    }

    static void clearForEscape(UUID holderId) {
        ArmorStandEntity stand = shieldStands.remove(holderId);
        if (stand != null) stand.discard();
        shieldModeActive.remove(holderId);
    }

    static void cleanup(UUID playerId) {
        ArmorStandEntity stand = shieldStands.remove(playerId);
        if (stand != null) stand.discard();
        shieldModeActive.remove(playerId);
        shieldSwapCooldown.remove(playerId);
    }

    static void removeSwapCooldown(UUID playerId) {
        shieldSwapCooldown.remove(playerId);
    }

    static void fullCleanup() {
        for (ArmorStandEntity stand : shieldStands.values()) {
            if (stand != null) stand.discard();
        }
        shieldStands.clear();
        shieldModeActive.clear();
        shieldSwapCooldown.clear();
    }

    static void registerShieldDamageEvent() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!DapUrHomiesMod.isModEnabled()) return true;
            if (!(entity instanceof ServerPlayerEntity holder)) return true;
            UUID hid = holder.getUuid();
            if (!Boolean.TRUE.equals(shieldModeActive.get(hid))) return true;

            UUID heldId = GrabYeetHandler.getHeldId(hid);
            if (heldId == null) return true;
            var server = holder.getEntityWorld().getServer();
            if (server == null) return true;
            ServerPlayerEntity shield = server.getPlayerManager().getPlayer(heldId);
            if (shield != null && shield.isAlive()) {
                shield.damage(holder.getEntityWorld(), source, amount);
                return false; // Cancel damage to holder, redirect to held player
            }
            return true;
        });
    }
}
