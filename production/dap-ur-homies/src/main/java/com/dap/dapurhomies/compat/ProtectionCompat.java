package com.dap.dapurhomies.compat;

import com.dap.dapurhomies.DapUrHomiesMod;
import com.dap.dapurhomies.config.ModConfig;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Method;

/**
 * 领地保护软依赖。只有完整解析到对应 API 时才启用检查，解析失败则保持放行。
 */
public class ProtectionCompat {

    private static final Class<?> GP_CLASS;
    private static final Method GP_GET_DATA_STORE;
    private static final Method GP_GET_CLAIM_AT;
    private static final Method GP_CLAIM_ALLOW_BUILD;
    private static Object gpInstance;

    private static final Class<?> LANDS_API_CLASS;
    private static final Method LANDS_GET_AREA_BP;
    private static final Method LANDS_GET_AREA_WBP;
    private static Object landsApi;
    private static boolean landsWarned = false;

    private static final Class<?> FLAN_API_CLASS;
    private static final Method FLAN_CAN_INTERACT;
    private static final Object FLAN_BREAK_PERMISSION;
    private static final Object FLAN_HURT_PLAYER_PERMISSION;

    static {
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
            gpAllowBuild = gpClaim.getMethod("allowBuild", net.minecraft.entity.player.PlayerEntity.class);
        } catch (Throwable ignored) {}
        GP_CLASS = gp;
        GP_GET_DATA_STORE = gpGetDs;
        GP_GET_CLAIM_AT = gpGetClaim;
        GP_CLAIM_ALLOW_BUILD = gpAllowBuild;
        gpInstance = gpInst;

        Class<?> lands = null;
        Method landsGetBp = null, landsGetWbp = null;
        Object landsApiObj = null;
        try {
            lands = Class.forName("me.angeschossen.lands.api.LandsIntegration");
            landsApiObj = lands.getMethod("of", String.class).invoke(null, "dap-ur-homies");
            try {
                landsGetBp = lands.getMethod("getArea", BlockPos.class);
            } catch (NoSuchMethodException ignored) {}
            try {
                landsGetWbp = lands.getMethod("getArea", World.class, BlockPos.class);
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable ignored) {}
        LANDS_API_CLASS = lands;
        LANDS_GET_AREA_BP = landsGetBp;
        LANDS_GET_AREA_WBP = landsGetWbp;
        landsApi = landsApiObj;

        Class<?> flan = null;
        Method flanCanInteract = null;
        Object flanBreak = null, flanHurtPlayer = null;
        try {
            flan = Class.forName("io.github.flemmli97.flan.api.ClaimHandler");
            flanCanInteract = flan.getMethod("canInteract", ServerPlayerEntity.class, BlockPos.class, Identifier.class);
            Class<?> builtinPermission = Class.forName("io.github.flemmli97.flan.api.permission.BuiltinPermission");
            flanBreak = builtinPermission.getField("BREAK").get(null);
            flanHurtPlayer = builtinPermission.getField("HURTPLAYER").get(null);
        } catch (Throwable ignored) {
            flanCanInteract = null;
            flanBreak = null;
            flanHurtPlayer = null;
        }
        FLAN_API_CLASS = flan;
        FLAN_CAN_INTERACT = flanCanInteract;
        FLAN_BREAK_PERMISSION = flanBreak;
        FLAN_HURT_PLAYER_PERMISSION = flanHurtPlayer;

        if (isAnyProtectionLoaded()) {
            DapUrHomiesMod.LOGGER.info("[DapUrHomies] Protection compat loaded. Detected: " + getLoadedMods());
        }
    }

    /** 玩家之间的交互使用 Flan 的 hurt_player 权限。 */
    public static boolean canInteract(ServerPlayerEntity initiator, ServerPlayerEntity target) {
        return check(initiator, target.getBlockPos(), FLAN_HURT_PLAYER_PERMISSION);
    }

    /** 撞墙破坏方块使用 Flan 的 break 权限。 */
    public static boolean canBuildAt(ServerPlayerEntity initiator, BlockPos pos) {
        return check(initiator, pos, FLAN_BREAK_PERMISSION);
    }

    private static boolean check(ServerPlayerEntity initiator, BlockPos pos, Object flanPermission) {
        if (!ModConfig.get().protectionCheckEnabled) return true;

        if (gpInstance != null && GP_GET_DATA_STORE != null && GP_GET_CLAIM_AT != null && GP_CLAIM_ALLOW_BUILD != null) {
            try {
                Object dataStore = GP_GET_DATA_STORE.invoke(gpInstance);
                Object claim = GP_GET_CLAIM_AT.invoke(dataStore, pos, false, null);
                if (claim != null) {
                    Object result = GP_CLAIM_ALLOW_BUILD.invoke(claim, initiator);
                    if (result != null) return false;
                }
            } catch (Throwable ignored) {}
        }

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
                DapUrHomiesMod.LOGGER.warn(
                    "[DapUrHomies] Lands detected but detailed flag checking is not available. "
                    + "Interactions inside Lands areas are allowed by default."
                );
                landsWarned = true;
            }
        }

        if (FLAN_CAN_INTERACT != null && flanPermission != null) {
            try {
                Object result = FLAN_CAN_INTERACT.invoke(null, initiator, pos, flanPermission);
                if (result instanceof Boolean && !(Boolean) result) return false;
            } catch (Throwable ignored) {}
        }

        return true;
    }

    public static boolean isAnyProtectionLoaded() {
        return gpInstance != null || landsApi != null || isFlanLoaded();
    }

    private static boolean isFlanLoaded() {
        return FLAN_API_CLASS != null && FLAN_CAN_INTERACT != null
                && FLAN_BREAK_PERMISSION != null && FLAN_HURT_PLAYER_PERMISSION != null;
    }

    public static String getLoadedMods() {
        StringBuilder sb = new StringBuilder();
        if (gpInstance != null) sb.append("GriefPrevention ");
        if (landsApi != null) sb.append("Lands ");
        if (isFlanLoaded()) sb.append("Flan ");
        return sb.toString().trim();
    }
}
