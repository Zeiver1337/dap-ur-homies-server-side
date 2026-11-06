package com.dap.dapurhomies.interaction;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 * Facade for cross-module player interaction state queries.
 * Callers (router, main tick loop) depend on this single entry point
 * instead of reaching into every handler's static state maps.
 */
public final class PlayerInteractionState {
    private PlayerInteractionState() {}

    public static boolean isHeld(UUID id) {
        return GrabYeetHandler.isHeld(id);
    }

    public static boolean isHolding(ServerPlayerEntity player) {
        return GrabYeetHandler.isHolding(player);
    }

    public static boolean hasAnyHolding() {
        return GrabYeetHandler.hasAnyHolding();
    }

    public static boolean isThrownFlying(UUID id) {
        return GrabYeetHandler.isThrownFlying(id);
    }

    public static boolean isHugging(UUID id) {
        return HugHandler.isHugging(id);
    }

    public static boolean isInDapSession(UUID id) {
        return DapSessionManager.isInSession(id);
    }

    /** Right-click exclusion set (mirrors the router's historical semantics). */
    public static boolean isBusyForUse(ServerPlayerEntity self, ServerPlayerEntity target) {
        return isHeld(target.getUuid()) || isHeld(self.getUuid())
                || isThrownFlying(target.getUuid())
                || isHugging(target.getUuid())
                || isInDapSession(self.getUuid()) || isInDapSession(target.getUuid());
    }

    /** Left-click (attack) exclusion set (mirrors the router's historical semantics). */
    public static boolean isBusyForAttack(ServerPlayerEntity self, ServerPlayerEntity target) {
        return isHeld(target.getUuid()) || isHeld(self.getUuid())
                || isThrownFlying(target.getUuid()) || isThrownFlying(self.getUuid())
                || isHugging(target.getUuid())
                || isInDapSession(self.getUuid()) || isInDapSession(target.getUuid());
    }
}
