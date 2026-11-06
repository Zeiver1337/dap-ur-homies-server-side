package com.dap.dapurhomies.compat;

import com.dap.dapurhomies.DapUrHomiesMod;
import com.dap.dapurhomies.config.ModConfig;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Method;

/**
 * Protection-mod compatibility layer.
 * Uses reflection to check common Fabric protection plugins (GriefPrevention, Lands, Flan)
 * without hard dependencies. Falls back to "allow" if no protection mod is detected.
 */
public class ProtectionCompat {

    // === GriefPrevention (Fabric) ===
    private static final Class<?> GP_CLASS;
    private static final Method GP_GET_DATA_STORE;
    private static final Method GP_GET_CLAIM_AT;
    private static final Method GP_CLAIM_ALLOW_BUILD;
    private static Object gpInstance;

    // === Lands ===
    private static final Class<?> LANDS_API_CLASS;
    private static final Method LANDS_GET_AREA_BP;
    private static final Method LANDS_GET_AREA_WBP;
    private static Object landsApi;
    private static boolean landsWarned = false;

    // === Flan ===
    private static final Class<?> FLAN_API_CLASS;
    private static final Method FLAN_CAN_INTERACT;

    static {
        // --- GriefPrevention ---
        Class<?> gp = null;
        Method gpGetDs = null, gpGetClaim = null, gpAllowBuild = null;
        Object gpInst = null;
        try {
            gp = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
            gpInst = gp.getMethod("getInstance").invoke(null);
            gpGetDs = gp.getMethod("getDataStore");
            Class<?> gpDs = Class.forName("me.ryanhamshire.GriefPrevention.DataStore");
            Class<?> gpClaim = Class.forName("me.ryanhamshire.GriefPrevention.Claim");
            gpGetClaim = gpDs.getMethod("getClaimAt", BlockPos.class, boolean.class, gpClaim);
            // Check if initiator has build permission in the claim
            gpAllowBuild = gpClaim.getMethod("allowBuild", net.minecraft.entity.player.PlayerEntity.class);
        } catch (Throwable ignored) {}
        GP_CLASS = gp;
        GP_GET_DATA_STORE = gpGetDs;
        GP_GET_CLAIM_AT = gpGetClaim;
        GP_CLAIM_ALLOW_BUILD = gpAllowBuild;
        gpInstance = gpInst;

        // --- Lands ---
        Class<?> lands = null;
        Method landsGetBp = null, landsGetWbp = null;
        Object landsApiObj = null;
        try {
            lands = Class.forName("me.angeschossen.lands.api.LandsIntegration");
            landsApiObj = lands.getMethod("of", String.class).invoke(null, "dap-ur-homies");
            // Try both signatures: getArea(BlockPos) and getArea(World, BlockPos)
            try {
                landsGetBp = lands.getMethod("getArea", BlockPos.class);
            } catch (NoSuchMethodException e) {
                landsGetBp = null;
            }
            try {
                landsGetWbp = lands.getMethod("getArea", World.class, BlockPos.class);
            } catch (NoSuchMethodException e) {
                landsGetWbp = null;
            }
        } catch (Throwable ignored) {}
        LANDS_API_CLASS = lands;
        LANDS_GET_AREA_BP = landsGetBp;
        LANDS_GET_AREA_WBP = landsGetWbp;
        landsApi = landsApiObj;

        // --- Flan ---
        Class<?> flan = null;
        Method flanCanInteract = null;
        try {
            flan = Class.forName("io.github.flemmli97.flan.api.permission.PermissionHelper");
            flanCanInteract = flan.getMethod("checkPermission",
                net.minecraft.server.network.ServerPlayerEntity.class,
                BlockPos.class,
                String.class); // "INTERACT_ENTITY" or similar
        } catch (Exception ignored) {
            // Try alternative Flan API class
            try {
                flan = Class.forName("io.github.flemmli97.flan.api.ClaimHandler");
                flanCanInteract = flan.getMethod("canInteract",
                    net.minecraft.server.network.ServerPlayerEntity.class,
                    BlockPos.class);
            } catch (Exception ignored2) {}
        }
        FLAN_API_CLASS = flan;
        FLAN_CAN_INTERACT = flanCanInteract;

        if (isAnyProtectionLoaded()) {
            DapUrHomiesMod.LOGGER.info("[DapUrHomies] Protection compat loaded. Detected: " + getLoadedMods());
        }
    }

    /**
     * Check if the initiator can interact with the target at the target's location.
     * Returns true if interaction is allowed (no protection blocking it).
     *
     * <p>Logic: checks whether the <b>initiator</b> has permission to build/interact
     * at the target's position. This is more accurate than checking claim ownership.
     */
    public static boolean canInteract(ServerPlayerEntity initiator, ServerPlayerEntity target) {
        return canBuildAt(initiator, target.getBlockPos());
    }

    /**
     * Check whether {@code initiator} may modify/interact at {@code pos}.
     * Returns true when no protection mod blocks it (or protection checks are disabled).
     */
    public static boolean canBuildAt(ServerPlayerEntity initiator, BlockPos pos) {
        if (!ModConfig.get().protectionCheckEnabled) return true;

        // === GriefPrevention: check if initiator can build in the claim ===
        if (gpInstance != null && GP_GET_DATA_STORE != null && GP_GET_CLAIM_AT != null && GP_CLAIM_ALLOW_BUILD != null) {
            try {
                Object dataStore = GP_GET_DATA_STORE.invoke(gpInstance);
                Object claim = GP_GET_CLAIM_AT.invoke(dataStore, pos, false, null);
                if (claim != null) {
                    // allowBuild returns null if allowed, or a String reason if denied
                    Object result = GP_CLAIM_ALLOW_BUILD.invoke(claim, initiator);
                    if (result != null) return false;
                }
            } catch (Throwable ignored) {}
        }

        // === Lands: check if target is in a protected area ===
        if (landsApi != null) {
            Object area = null;
            try {
                if (LANDS_GET_AREA_WBP != null) {
                    area = LANDS_GET_AREA_WBP.invoke(landsApi, initiator.getEntityWorld(), pos);
                } else if (LANDS_GET_AREA_BP != null) {
                    area = LANDS_GET_AREA_BP.invoke(landsApi, pos);
                }
            } catch (Throwable ignored) {}

            if (area != null && !landsWarned) {
                // Lands area detected but we can't reliably check flags across versions.
                // Log once so admins know it's a best-effort check.
                DapUrHomiesMod.LOGGER.warn(
                    "[DapUrHomies] Lands detected but detailed flag checking is not available. " +
                    "Interactions inside Lands areas are allowed by default. " +
                    "Consider disabling protectionCheckEnabled in config if this is unwanted."
                );
                landsWarned = true;
            }
        }

        // === Flan: check interaction permission ===
        if (FLAN_API_CLASS != null && FLAN_CAN_INTERACT != null) {
            try {
                Object result;
                if (FLAN_CAN_INTERACT.getParameterCount() == 3) {
                    // PermissionHelper.checkPermission(player, pos, permissionNode)
                    result = FLAN_CAN_INTERACT.invoke(null, initiator, pos, "INTERACT_ENTITY");
                } else {
                    // ClaimHandler.canInteract(player, pos)
                    result = FLAN_CAN_INTERACT.invoke(null, initiator, pos);
                }
                if (result instanceof Boolean && !(Boolean) result) return false;
            } catch (Throwable ignored) {}
        }

        return true;
    }

    public static boolean isAnyProtectionLoaded() {
        return gpInstance != null || landsApi != null || FLAN_API_CLASS != null;
    }

    private static String getLoadedMods() {
        StringBuilder sb = new StringBuilder();
        if (gpInstance != null) sb.append("GriefPrevention ");
        if (landsApi != null) sb.append("Lands ");
        if (FLAN_API_CLASS != null) sb.append("Flan ");
        return sb.toString().trim();
    }
}

