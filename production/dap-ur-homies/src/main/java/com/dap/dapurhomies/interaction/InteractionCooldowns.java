package com.dap.dapurhomies.interaction;

import com.dap.dapurhomies.Lang;
import com.dap.dapurhomies.permission.PlayerPermissionManager;
import com.dap.dapurhomies.util.AdminManager;
import com.dap.dapurhomies.util.CooldownManager;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/** Shared cooldown plumbing; interaction-specific timing remains at the call site. */
public final class InteractionCooldowns {
    private InteractionCooldowns() {}

    public static boolean isBlocked(ServerPlayerEntity player, String type) {
        return !PlayerPermissionManager.bypassesCooldown(player)
                && CooldownManager.isOnCooldown(player.getUuid(), type);
    }

    public static boolean rejectIfBlocked(ServerPlayerEntity player, String type) {
        if (!isBlocked(player, type)) return false;
        Lang.cooldown(player);
        return true;
    }

    public static long apply(UUID playerId, String type, long configMs) {
        long ticks = AdminManager.resolveCooldownTicks(playerId, type, configMs);
        CooldownManager.setCooldown(playerId, type, ticks);
        return ticks;
    }

    /** Uses the initiator's override and applies the same duration to both participants. */
    public static void applyShared(UUID initiatorId, UUID targetId, String type, long configMs) {
        long ticks = apply(initiatorId, type, configMs);
        CooldownManager.setCooldown(targetId, type, ticks);
    }
}
