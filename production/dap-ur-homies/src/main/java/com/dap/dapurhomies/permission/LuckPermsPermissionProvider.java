package com.dap.dapurhomies.permission;

import com.dap.dapurhomies.config.ModConfig;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Method;

/**
 * LuckPerms / Fabric Permissions API bridge.
 * Uses reflection so LuckPerms is an optional dependency.
 *
 * Behaviour:
 *   - canInitiate:  LuckPerms node check  +  file preference fallback
 *   - canReceive:   file preference only by default;
 *                   if ModConfig.luckPermsControlsReceive = true,
 *                   also checks LuckPerms node "dap-ur-homies.receive.<type>"
 */
public class LuckPermsPermissionProvider implements PermissionProvider {
    private final FilePermissionProvider fallback = new FilePermissionProvider();
    private Method checkMethod;
    private boolean available = false;

    public LuckPermsPermissionProvider() {
        try {
            Class<?> permissionsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            checkMethod = permissionsClass.getMethod("check", net.minecraft.server.command.ServerCommandSource.class, String.class, boolean.class);
            available = true;
        } catch (Exception e) {
            available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    @Override
    public boolean hasPermission(ServerPlayerEntity player, String node) {
        if (!available) return fallback.hasPermission(player, node);
        try {
            return (boolean) checkMethod.invoke(null, player.getCommandSource(), node, fallback.hasPermission(player, node));
        } catch (Exception e) {
            return fallback.hasPermission(player, node);
        }
    }

    @Override
    public boolean canInitiate(ServerPlayerEntity player, String type) {
        // LuckPerms for permission check, fallback to file for preference
        String node = "dap-ur-homies." + type;
        boolean hasPerm = hasPermission(player, node);
        if (!hasPerm) return false;
        // Also check personal preference (file-based)
        return fallback.canInitiate(player, type);
    }

    @Override
    public boolean canReceive(ServerPlayerEntity player, String type) {
        // Default: file-based preference only (players control their own receive settings)
        if (!ModConfig.get().luckPermsControlsReceive) {
            return fallback.canReceive(player, type);
        }
        // Optional: LuckPerms can override receive permissions via "dap-ur-homies.receive.<type>"
        String node = "dap-ur-homies.receive." + type;
        boolean hasPerm = hasPermission(player, node);
        if (!hasPerm) return false;
        // Still respect personal file preference as final gate
        return fallback.canReceive(player, type);
    }

    @Override
    public void setPreference(ServerPlayerEntity player, String type, boolean value) {
        fallback.setPreference(player, type, value);
    }

    @Override
    public PlayerPreferences getPerms(ServerPlayerEntity player) {
        return fallback.getPerms(player);
    }

    @Override
    public PlayerPreferences getPerms(java.util.UUID playerId) {
        return fallback.getPerms(playerId);
    }

    @Override
    public String getLang(java.util.UUID playerId) {
        return fallback.getLang(playerId);
    }

    @Override
    public void setLang(java.util.UUID playerId, String lang) {
        fallback.setLang(playerId, lang);
    }
}

