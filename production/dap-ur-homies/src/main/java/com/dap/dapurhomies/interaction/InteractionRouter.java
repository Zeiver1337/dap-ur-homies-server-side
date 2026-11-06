package com.dap.dapurhomies.interaction;

import com.dap.dapurhomies.DapUrHomiesMod;
import com.dap.dapurhomies.compat.ProtectionCompat;
import com.dap.dapurhomies.config.ModConfig;
import com.dap.dapurhomies.permission.PlayerPermissionManager;
import com.dap.dapurhomies.util.TickClock;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

import java.util.HashMap;
import java.util.Map;

public class InteractionRouter {
    // Debounce repeated right-click interaction chains per player pair and tick.
    private static final Map<Long, Long> lastRightClickTick = new HashMap<>();
    private static long lastCleanupTick = 0;
    public static void register() {
        // ===== RIGHT-CLICK INTERACTIONS =====
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!(entity instanceof ServerPlayerEntity target)) return ActionResult.PASS;
            if (!DapUrHomiesMod.isModEnabled()) return ActionResult.PASS;
            if (sp.getEntityWorld() != target.getEntityWorld()) return ActionResult.PASS;

            boolean isAdmin = PlayerPermissionManager.hasPermission(sp, "dap-ur-homies.admin");
            if (!PlayerPermissionManager.canInitiate(sp, "enabled")) return ActionResult.FAIL;
            if (!isAdmin && !PlayerPermissionManager.canReceive(target, "enabled")) return ActionResult.FAIL;
            if (!isAdmin && !ProtectionCompat.canInteract(sp, target)) return ActionResult.FAIL;

            // P0: holding -> yeet charge
            if (PlayerInteractionState.isHolding(sp) && ModConfig.get().enableGrabYeet)
                return GrabYeetHandler.startCharge(sp);

            if (PlayerInteractionState.isBusyForUse(sp, target))
                return ActionResult.PASS;

            boolean sneaking = sp.isSneaking();
            boolean sprinting = sp.isSprinting();
            boolean hitAtFeet = hit != null && hit.getPos().y < target.getY() + 0.8;
            double dist = sp.distanceTo(target);

            long nowTick = TickClock.now();
            // Cleanup expired debounce entries (older than 5 seconds = 100 ticks) — throttled to every 20 ticks
            if (nowTick - lastCleanupTick > 20) {
                lastRightClickTick.entrySet().removeIf(e -> nowTick - e.getValue() > 100);
                hugClickTick.entrySet().removeIf(e -> nowTick - e.getValue() > HUG_WINDOW_TICKS * 2);
                lastCleanupTick = nowTick;
            }
            long pairKey = (((long) sp.getUuid().hashCode()) << 32) | (target.getUuid().hashCode() & 0xffffffffL);
            Long lastRc = lastRightClickTick.get(pairKey);
            if (lastRc != null && nowTick - lastRc < 5) return ActionResult.PASS;

            // Hug: BOTH players sneak, aim at each other's HEAD and right-click within the window.
            // Must run BEFORE the sneak branch. If the partner does not answer, fall through to
            // charged Dap instead of swallowing the click entirely.
            if (sneaking && target.isSneaking() && ModConfig.get().enableHug
                    && dist <= ModConfig.get().hugRange
                    && aimingAtHead(sp, target) && aimingAtHead(target, sp)) {
                long key = hugKey(sp, target);
                Long last = hugClickTick.get(key);
                if (last != null && nowTick - last <= HUG_WINDOW_TICKS) {
                    ActionResult r = HugHandler.handleHug(sp, target);
                    if (r != ActionResult.PASS) {
                        // A pending charged Dap from the first click must not fire after the hug
                        DapHandler.cancelCharge(sp.getUuid());
                        DapHandler.cancelCharge(target.getUuid());
                        lastRightClickTick.put(pairKey, nowTick);
                        clearHugWindow(sp, target);
                        return r;
                    }
                }
                hugClickTick.put(key, nowTick); // first click: remember and keep processing
            }

            // SNEAK = charged dap (蓄力击掌) / charged DropKick when aiming at the feet
            if (sneaking) {
                if (hitAtFeet && ModConfig.get().enableKick) {
                    if (KickHandler.isCharging(sp.getUuid())) {
                        ActionResult r = KickHandler.releaseCharge(sp, target);
                        if (r != ActionResult.PASS) { lastRightClickTick.put(pairKey, nowTick); return r; }
                    } else {
                        KickHandler.startCharge(sp);
                        lastRightClickTick.put(pairKey, nowTick);
                        return ActionResult.SUCCESS;
                    }
                }
                if (ModConfig.get().enableDap && sp.getMainHandStack().isEmpty()) {
                    ActionResult r = DapHandler.startCharge(sp, target);
                    if (r != ActionResult.PASS) { lastRightClickTick.put(pairKey, nowTick); return r; }
                }
                return ActionResult.PASS;
            }

            // SPRINT = Push (疾跑推，不瞄脚部时)
            if (sprinting && !hitAtFeet) {
                if (ModConfig.get().enablePush) {
                    ActionResult r = PushHandler.handlePush(sp, target);
                    if (r != ActionResult.PASS) { lastRightClickTick.put(pairKey, nowTick); return r; }
                }
                return ActionResult.PASS;
            }

            // Kick (aim feet, not sneaking)
            if (hitAtFeet && ModConfig.get().enableKick) {
                ActionResult r = KickHandler.handleKick(sp, target);
                if (r != ActionResult.PASS) { lastRightClickTick.put(pairKey, nowTick); return r; }
            }
            // Slap (behind)
            if (ModConfig.get().enableSlap && isBehind(sp, target)) {
                ActionResult r = SimpleInteractions.handleSlap(sp, target);
                if (r != ActionResult.PASS) { lastRightClickTick.put(pairKey, nowTick); return r; }
            }
            // Right-click fallback = HighFive/Dap (击掌)
            if (sp.getMainHandStack().isEmpty() && ModConfig.get().enableDap) {
                ActionResult r = DapHandler.handleNormalDap(sp, target);
                if (r != ActionResult.PASS) { lastRightClickTick.put(pairKey, nowTick); return r; }
            }
            return ActionResult.PASS;
        });

        // ===== LEFT-CLICK INTERACTIONS (AttackEntityCallback) =====
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!(entity instanceof ServerPlayerEntity target)) return ActionResult.PASS;
            if (!DapUrHomiesMod.isModEnabled()) return ActionResult.PASS;
            if (sp.getEntityWorld() != target.getEntityWorld()) return ActionResult.PASS;

            boolean isAdmin = PlayerPermissionManager.hasPermission(sp, "dap-ur-homies.admin");

            // Master switch check
            if (!PlayerPermissionManager.canInitiate(sp, "enabled")) return ActionResult.PASS;
            if (!isAdmin && !PlayerPermissionManager.canReceive(target, "enabled")) return ActionResult.PASS;
            if (!isAdmin && !ProtectionCompat.canInteract(sp, target)) return ActionResult.PASS;

            // State exclusion
            if (PlayerInteractionState.isBusyForAttack(sp, target))
                return ActionResult.PASS;

            // Shift+左键 = Grab (if not holding)
            if (sp.isSneaking() && ModConfig.get().enableGrabYeet
                    && !GrabYeetHandler.isHolding(sp) && !GrabYeetHandler.isHeld(sp)) {
                ActionResult result = GrabYeetHandler.tryGrab(sp, target);
                if (result != ActionResult.PASS) return result;
            }

            // left-click held target = throw if charged, else drop
            if (GrabYeetHandler.isHolding(sp) && ModConfig.get().enableGrabYeet) {
                if (GrabYeetHandler.isCharging(sp.getUuid())) return GrabYeetHandler.releaseCharge(sp);
                ActionResult r = GrabYeetHandler.tryDrop(sp);
                return (r == ActionResult.PASS) ? ActionResult.FAIL : r;
            }

            // Left-click does NOT trigger dap/highfive (only right-click does).
            return ActionResult.PASS;
        });
    }

    private static boolean isBehind(ServerPlayerEntity attacker, ServerPlayerEntity target) {
        net.minecraft.util.math.Vec3d look = target.getRotationVec(1.0f);
        net.minecraft.util.math.Vec3d to = attacker.getEntityPos().subtract(target.getEntityPos()).normalize();
        return look.dotProduct(to) <= 0.0;
    }

    // ---- Hug mutual-detection helpers ----
    private static final Map<Long, Long> hugClickTick = new HashMap<>(); // pairKey -> last tick initiator clicked
    private static final long HUG_WINDOW_TICKS = 10; // 0.5s: enough for two humans to answer each other

    private static long hugKey(ServerPlayerEntity a, ServerPlayerEntity b) {
        // order-independent pair key
        int h1 = a.getUuid().hashCode(), h2 = b.getUuid().hashCode();
        long lo = Math.min(h1, h2), hi = Math.max(h1, h2);
        return (hi << 32) | (lo & 0xffffffffL);
    }
    private static void clearHugWindow(ServerPlayerEntity a, ServerPlayerEntity b) {
        hugClickTick.remove(hugKey(a, b));
    }
    private static boolean aimingAtHead(ServerPlayerEntity viewer, ServerPlayerEntity target) {
        net.minecraft.util.math.Vec3d eye = viewer.getCameraPosVec(1.0f);
        net.minecraft.util.math.Vec3d look = viewer.getRotationVec(1.0f);
        net.minecraft.util.math.Vec3d head = target.getEntityPos().add(0, target.getStandingEyeHeight(), 0);
        net.minecraft.util.math.Vec3d toHead = head.subtract(eye).normalize();
        return look.dotProduct(toHead) > 0.95;   // ~18° cone on head
    }
}

