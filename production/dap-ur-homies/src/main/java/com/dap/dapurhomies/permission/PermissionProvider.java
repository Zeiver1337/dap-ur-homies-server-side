package com.dap.dapurhomies.permission;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 * Abstract permission provider. Implementations can be file-based (default)
 * or delegate to LuckPerms / other permission plugins.
 */
public interface PermissionProvider {

    /** Check if player has a specific permission node. */
    boolean hasPermission(ServerPlayerEntity player, String node);

    /** Check if player can initiate a specific interaction type. */
    boolean canInitiate(ServerPlayerEntity player, String type);

    /** Check if player can receive (be target of) a specific interaction type. */
    boolean canReceive(ServerPlayerEntity player, String type);

    /** Set player's preference for a type (only meaningful for file-based provider). */
    void setPreference(ServerPlayerEntity player, String type, boolean value);

    /** Get readable status of all preferences for a player. */
    PlayerPreferences getPerms(ServerPlayerEntity player);

    /** Get player's language preference ("global", "en", "zh"). */
    String getLang(UUID playerId);

    /** Set player's language preference. */
    void setLang(UUID playerId, String lang);

    /** Get perms by UUID (for offline players). */
    PlayerPreferences getPerms(UUID playerId);
}

