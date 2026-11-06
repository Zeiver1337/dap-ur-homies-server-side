package com.dap.dapurhomies.permission;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 * Facade for permission checks. Auto-detects LuckPerms and falls back to file-based.
 * Each player can toggle their own module preferences; if a module is disabled,
 * others cannot trigger that interaction on this player.
 */
public class PlayerPermissionManager {
    private static PermissionProvider provider;

    public static void init() {
        LuckPermsPermissionProvider lp = new LuckPermsPermissionProvider();
        provider = lp.isAvailable() ? lp : new FilePermissionProvider();
    }

    /** True when LuckPerms (Fabric Permissions API) is backing permission checks. */
    public static boolean isLuckPermsActive() {
        return provider instanceof LuckPermsPermissionProvider;
    }

    public static boolean hasPermission(ServerPlayerEntity player, String node) {
        return provider.hasPermission(player, node);
    }

    /** Can this player INITIATE an interaction of the given type? */
    public static boolean canInitiate(ServerPlayerEntity player, String type) {
        // Admin-forced disable cannot be undone by the player and beats the admin bypass
        if (com.dap.dapurhomies.util.AdminManager.isForceDisabled(player.getUuid())) return false;
        // Admin bypass
        if (hasPermission(player, "dap-ur-homies.admin")) return true;
        // Temp ban check (total or per-module)
        if (com.dap.dapurhomies.util.AdminManager.isBanned(player.getUuid(), type)) return false;
        return provider.canInitiate(player, type);
    }

    /** Can this player RECEIVE (be target of) an interaction of the given type? */
    public static boolean canReceive(ServerPlayerEntity player, String type) {
        if (com.dap.dapurhomies.util.AdminManager.isForceDisabled(player.getUuid())) return false;
        if (com.dap.dapurhomies.util.AdminManager.isBanned(player.getUuid(), type)) return false;
        return provider.canReceive(player, type);
    }

    /** Admins may bypass cooldowns only while the config switch is enabled. */
    public static boolean bypassesCooldown(ServerPlayerEntity player) {
        return hasPermission(player, "dap-ur-homies.admin")
                && com.dap.dapurhomies.config.ModConfig.get().adminBypassCooldown;
    }

    /** Toggle a player's preference for a module. */
    public static void set(ServerPlayerEntity player, String type, boolean value) {
        provider.setPreference(player, type, value);
    }

    public static PlayerPreferences get(ServerPlayerEntity player) {
        return provider.getPerms(player);
    }

    public static PlayerPreferences get(UUID playerId) {
        return provider.getPerms(playerId);
    }

    public static String getLang(UUID playerId) {
        return provider.getLang(playerId);
    }

    public static void setLang(UUID playerId, String lang) {
        provider.setLang(playerId, lang);
    }

    public static String[] getTypes() {
        return new String[]{"enabled", "dap", "grab", "push", "kick", "hug", "slap", "clap", "danse"};
    }
}

