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
    private static Method onStartMethod;
    private static Method onStopMethod;
    private static Method getModelMethod;

    private static final Set<String> FAILED_ANIMATIONS = new HashSet<>();

    static {
        if (DANSE_LOADED) {
            try {
                Class<?> gestureController = Class.forName("de.tomalbrc.danse.GestureController");
                onStartMethod = gestureController.getMethod("onStart", ServerPlayerEntity.class, String.class);
                onStopMethod = gestureController.getMethod("onStop", ServerPlayerEntity.class);
                try {
                    Class<?> registry = Class.forName("de.tomalbrc.danse.registry.PlayerModelRegistry");
                    getModelMethod = registry.getMethod("getModel", String.class);
                } catch (Exception e) {
                    LOGGER.warn("[DapUrHomies] Danse animation registry is unavailable; custom animation names will be used unchanged.");
                }
                LOGGER.info("[DapUrHomies] Danse integration enabled.");
            } catch (Exception e) {
                LOGGER.warn("[DapUrHomies] Danse detected but GestureController API is incompatible: " + e.getMessage());
            }
        }
    }

    public static boolean isDanseLoaded() {
        return DANSE_LOADED && onStartMethod != null;
    }

    private static boolean canPlayPair(ServerPlayerEntity initiator, ServerPlayerEntity target) {
        return isDanseLoaded()
                && DapUrHomiesMod.isModEnabled()
                && PlayerPermissionManager.canInitiate(initiator, "danse")
                && PlayerPermissionManager.canReceive(target, "danse");
    }

    private static String resolveAnimation(String... candidates) {
        if (getModelMethod == null) return candidates[0];
        for (String candidate : candidates) {
            try {
                // 优先使用服务器安装的同名自定义动画，缺失时使用 Danse 内置动画。
                if (getModelMethod.invoke(null, candidate) != null) return candidate;
            } catch (Exception e) {
                if (FAILED_ANIMATIONS.add(candidate)) {
                    LOGGER.warn("[DapUrHomies] Failed to query Danse animation '{}': {}", candidate, e.getMessage());
                }
                return null;
            }
        }
        if (FAILED_ANIMATIONS.add(candidates[0])) {
            LOGGER.warn("[DapUrHomies] No registered Danse animation found for '{}'. Tried: {}",
                    candidates[0], String.join(", ", candidates));
        }
        return null;
    }

    public static void playAnimation(ServerPlayerEntity player, String... candidates) {
        if (!isDanseLoaded() || player == null || candidates.length == 0) return;
        String animationName = resolveAnimation(candidates);
        if (animationName == null) return;
        try {
            onStartMethod.invoke(null, player, animationName);
        } catch (Exception e) {
            if (FAILED_ANIMATIONS.add(animationName)) {
                LOGGER.warn("[DapUrHomies] Failed to play Danse animation '{}'.", animationName, e);
            }
        }
    }

    public static void stopAnimation(ServerPlayerEntity player) {
        if (!isDanseLoaded() || onStopMethod == null || player == null) return;
        try {
            onStopMethod.invoke(null, player);
        } catch (Exception e) {
            LOGGER.warn("[DapUrHomies] Failed to stop Danse animation.", e);
        }
    }

    public static void playDap(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        if (!canPlayPair(p1, p2)) return;
        playAnimation(p1, "dap", "dab");
        playAnimation(p2, "dap", "dab");
    }

    public static void playHighFive(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        if (!canPlayPair(p1, p2)) return;
        playAnimation(p1, "highfive", "high_five", "wave");
        playAnimation(p2, "highfive", "high_five", "wave");
    }

    public static void playHug(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        if (!canPlayPair(p1, p2)) return;
        playAnimation(p1, "hug", "fold");
        playAnimation(p2, "hug", "fold");
    }

    public static void playGrab(ServerPlayerEntity holder, ServerPlayerEntity victim) {
        if (!canPlayPair(holder, victim)) return;
        playAnimation(victim, "grabbed", "fall");
    }

    public static void playYeet(ServerPlayerEntity victim) {
        if (!isDanseLoaded() || !PlayerPermissionManager.canInitiate(victim, "danse")) return;
        playAnimation(victim, "yeet", "ascend");
    }

    public static void playKick(ServerPlayerEntity kicker, ServerPlayerEntity victim) {
        if (!canPlayPair(kicker, victim)) return;
        playAnimation(kicker, "kick", "bits");
        playAnimation(victim, "kicked", "fall");
    }

    public static void playPush(ServerPlayerEntity pusher, ServerPlayerEntity victim) {
        if (!canPlayPair(pusher, victim)) return;
        playAnimation(pusher, "push", "bits");
        playAnimation(victim, "pushed", "fall");
    }

    public static void playSlap(ServerPlayerEntity slapper, ServerPlayerEntity victim) {
        if (!canPlayPair(slapper, victim)) return;
        playAnimation(slapper, "slap", "facepalm");
        playAnimation(victim, "slapped", "facepalm");
    }

    public static void playClap(ServerPlayerEntity player) {
        if (!isDanseLoaded() || !PlayerPermissionManager.canInitiate(player, "danse")) return;
        playAnimation(player, "clap", "wave");
    }

    public static void playMarioJump(ServerPlayerEntity player) {
        if (!isDanseLoaded() || !PlayerPermissionManager.canInitiate(player, "danse")) return;
        playAnimation(player, "mario_jump", "ascend");
    }
}
