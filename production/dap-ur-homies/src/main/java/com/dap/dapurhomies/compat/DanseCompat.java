package com.dap.dapurhomies.compat;

import com.dap.dapurhomies.DapUrHomiesMod;
import com.dap.dapurhomies.permission.PlayerPermissionManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public class DanseCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("dap-ur-homies");
    private static final boolean DANSE_LOADED = FabricLoader.getInstance().isModLoaded("danse");
    private static Method onStartMethod = null;
    private static Method onStopMethod = null;

    // Track failed animation names to avoid log spam.
    private static final Set<String> failedAnimations = new HashSet<>();

    static {
        if (DANSE_LOADED) {
            try {
                Class<?> gestureController = Class.forName("de.tomalbrc.danse.GestureController");
                onStartMethod = gestureController.getMethod("onStart", ServerPlayerEntity.class, String.class);
                onStopMethod = gestureController.getMethod("onStop", ServerPlayerEntity.class);
                LOGGER.info("[DapUrHomies] Danse mod detected via de.tomalbrc.danse.GestureController, animation integration enabled!");
            } catch (Exception e) {
                LOGGER.warn("[DapUrHomies] Danse detected but API not accessible: " + e.getMessage());
            }
        }
    }

    public static boolean isDanseLoaded() {
        return DANSE_LOADED && onStartMethod != null;
    }

    public static boolean canPlayDanse(ServerPlayerEntity initiator, ServerPlayerEntity target, String type) {
        if (!isDanseLoaded()) return false;
        if (!DapUrHomiesMod.isModEnabled()) return false;
        if (!PlayerPermissionManager.canInitiate(initiator, "danse")) return false;
        if (!PlayerPermissionManager.canReceive(target, "danse")) return false;
        return true;
    }

    public static void playAnimation(ServerPlayerEntity player, String animationName) {
        if (!isDanseLoaded() || player == null) return;
        try {
            onStartMethod.invoke(null, player, animationName);
        } catch (Exception e) {
            // Only log once per animation name to avoid console spam.
            if (failedAnimations.add(animationName)) {
                LOGGER.warn("[DapUrHomies] Failed to play Danse animation '{}'. This animation may not exist in Danse. Suppressing further warnings for this animation.", animationName);
            }
        }
    }

    public static void stopAnimation(ServerPlayerEntity player) {
        if (!isDanseLoaded() || player == null) return;
        try {
            onStopMethod.invoke(null, player);
        } catch (Exception e) {
            LOGGER.warn("[DapUrHomies] Failed to stop Danse animation: " + e.getMessage());
        }
    }

    public static void playDap(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        if (!canPlayDanse(p1, p2, "dap")) return;
        playAnimation(p1, "dap");
        playAnimation(p2, "dap");
    }

    public static void playHighFive(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        if (!canPlayDanse(p1, p2, "dap")) return;
        playAnimation(p1, "highfive");
        playAnimation(p2, "highfive");
    }

    public static void playHug(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        if (!canPlayDanse(p1, p2, "hug")) return;
        playAnimation(p1, "hug");
        playAnimation(p2, "hug");
    }

    public static void playGrab(ServerPlayerEntity holder, ServerPlayerEntity victim) {
        if (!canPlayDanse(holder, victim, "grab")) return;
        playAnimation(victim, "grabbed");
    }

    public static void playYeet(ServerPlayerEntity victim) {
        if (!isDanseLoaded()) return;
        if (!PlayerPermissionManager.canInitiate(victim, "danse")) return;
        playAnimation(victim, "yeet");
    }

    public static void playKick(ServerPlayerEntity kicker, ServerPlayerEntity victim) {
        if (!canPlayDanse(kicker, victim, "kick")) return;
        playAnimation(kicker, "kick");
        playAnimation(victim, "kicked");
    }

    public static void playPush(ServerPlayerEntity pusher, ServerPlayerEntity victim) {
        if (!canPlayDanse(pusher, victim, "push")) return;
        playAnimation(pusher, "push");
        playAnimation(victim, "pushed");
    }

    public static void playSlap(ServerPlayerEntity slapper, ServerPlayerEntity victim) {
        if (!canPlayDanse(slapper, victim, "slap")) return;
        playAnimation(slapper, "slap");
        playAnimation(victim, "slapped");
    }

    public static void playClap(ServerPlayerEntity player) {
        if (!isDanseLoaded()) return;
        if (!PlayerPermissionManager.canInitiate(player, "danse")) return;
        playAnimation(player, "clap");
    }

    public static void playMarioJump(ServerPlayerEntity player) {
        if (!isDanseLoaded()) return;
        if (!PlayerPermissionManager.canInitiate(player, "danse")) return;
        playAnimation(player, "mario_jump");
    }
}

