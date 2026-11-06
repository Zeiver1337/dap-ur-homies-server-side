package com.dap.dapurhomies.interaction;

import com.dap.dapurhomies.DapUrHomiesMod;
import com.dap.dapurhomies.Lang;
import com.dap.dapurhomies.compat.ProtectionCompat;
import com.dap.dapurhomies.config.ModConfig;
import com.dap.dapurhomies.permission.PlayerPermissionManager;
import com.dap.dapurhomies.util.TickClock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Grab & Yeet system - ported from original Dap-ur-homie project.
 * Pure server-side implementation with physics, wall collision, fire explosion
 * and human-shield mode.
 */
public class GrabYeetHandler {

    // ===== STATE MAPS (bidirectional tracking) =====
    private static final Map<UUID, UUID> holding = new java.util.concurrent.ConcurrentHashMap<>();   // holder -> held
    private static final Map<UUID, UUID> heldBy  = new java.util.concurrent.ConcurrentHashMap<>();   // held -> holder

    // ===== CHARGE YEET SYSTEM =====
    private static final Map<UUID, Long> chargeStartTime = new HashMap<>(); // holder -> charge start timestamp
    private static final Map<UUID, ServerBossBar> chargeBossBars = new HashMap<>(); // holder -> boss bar
    // Charge durations use TickClock ticks (20/s) consistently.
    private static final long CHARGE_MAX_TICKS = 60;          // 3s full charge
    private static final long CHARGE_AUTO_RELEASE_TICKS = 60; // auto release at full

    // ===== CARRY TICK COUNTER (for teleport frequency throttling) =====
    private static int carryTickCounter = 0;

    // ===== GRAB GRACE WINDOW (ignore air-swing drop for 10 ticks after grab) =====
    private static final Map<UUID, Long> lastGrabTick = new HashMap<>();

    // ===== CONSTANTS =====
    private static final double THROW_RANGE = 3.0;
    private static final double MAX_THROW_POWER = 2.5;
    private static final double MIN_THROW_POWER = 0.5;

    public static void register() {
        // Tick is driven by InteractionModules.tickAll(); do not register another server tick here.

        // Right-clicking air, blocks, or entities starts charging while holding.
        // UseItemCallback covers right-clicks not consumed by an entity, including when
        // the crosshair passes through the carried player overhead.
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!ModConfig.get().enableGrabYeet) return ActionResult.PASS;
            if (!isHolding(sp)) return ActionResult.PASS;
            if (sp.isSneaking()) return ActionResult.PASS;   // sneak = shield toggle, handled by Mixin
            startCharge(sp);
            return ActionResult.SUCCESS;
        });
    }

    // ===== PUBLIC API =====

    public static boolean isHolding(net.minecraft.entity.player.PlayerEntity player) {
        return player != null && holding.containsKey(player.getUuid());
    }

    public static boolean hasAnyHolding() {
        return !holding.isEmpty();
    }

    public static boolean isHeld(net.minecraft.entity.player.PlayerEntity player) {
        return player != null && heldBy.containsKey(player.getUuid());
    }

    public static boolean isHeld(UUID playerId) {
        return heldBy.containsKey(playerId);
    }

    public static boolean isCharging(UUID playerId) {
        return chargeStartTime.containsKey(playerId);
    }

    // ===== GRAB =====

    public static ActionResult tryGrab(ServerPlayerEntity holder, ServerPlayerEntity target) {
        if (holder == null || target == null) return ActionResult.PASS;
        if (holder.equals(target)) return ActionResult.PASS;
        if (!ModConfig.get().enableGrabYeet) return ActionResult.PASS;
        if (holder.getEntityWorld() != target.getEntityWorld()) return ActionResult.PASS;

        UUID hid = holder.getUuid();
        UUID tid = target.getUuid();

        // Already holding or being held
        if (holding.containsKey(hid) || heldBy.containsKey(hid)) {
            Lang.sendActionBar(holder, "\u00a7cYou are already holding someone!");
            return ActionResult.FAIL;
        }
        if (holding.containsKey(tid) || heldBy.containsKey(tid)) {
            Lang.sendActionBar(holder, "\u00a7cTarget is already in a grab!");
            return ActionResult.FAIL;
        }

        // Distance check
        if (holder.distanceTo(target) > ModConfig.get().grabRange) {
            Lang.sendActionBar(holder, "\u00a7cToo far to grab!");
            return ActionResult.FAIL;
        }

        // Throw immunity check (1 second after being thrown)
        if (ThrowTracker.isImmune(tid)) {
            Lang.sendActionBar(holder, "\u00a7cTarget is recovering from being thrown!");
            return ActionResult.FAIL;
        }

        // Permission check
        if (!PlayerPermissionManager.canInitiate(holder, "grab")) {
            Lang.interactionBlocked(holder, "Grab is disabled for you!", "\u4f60\u7684\u6293\u53d6\u529f\u80fd\u5df2\u5173\u95ed\uff01");
            return ActionResult.FAIL;
        }
        if (!PlayerPermissionManager.hasPermission(holder, "dap-ur-homies.admin")
                && !PlayerPermissionManager.canReceive(target, "grab")) {
            Lang.targetDisabledType(holder, target, "grab");
            return ActionResult.FAIL;
        }

        // Protection check
        if (!PlayerPermissionManager.hasPermission(holder, "dap-ur-homies.admin")
                && !ProtectionCompat.canInteract(holder, target)) {
            Lang.protectedTarget(holder, target);
            return ActionResult.FAIL;
        }

        // Cooldown check
        if (InteractionCooldowns.rejectIfBlocked(holder, "grab")) return ActionResult.FAIL;

        // Apply grab
        holding.put(hid, tid);
        heldBy.put(tid, hid);
        lastGrabTick.put(hid, TickClock.now());   // 记录抓取时刻，防止同一左键被误判为放下

        // No riding. tickCarryPositions positions the held player overhead each tick.
        // (Riding can't move a no-mod client's camera above the head; teleport can.)

        // Effects
        ServerWorld world = holder.getEntityWorld();
        Vec3d pos = target.getEntityPos();
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0f, 0.8f);
        world.spawnParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.5, pos.z, 5, 0.2, 0.2, 0.2, 0.02);

        Lang.grabSuccess(holder, target);
        InteractionCooldowns.apply(hid, "grab", ModConfig.get().grabCooldownMs);
        com.dap.dapurhomies.compat.DanseCompat.playGrab(holder, target); // Danse animation

        return ActionResult.SUCCESS;
    }

    // ===== DROP =====

    public static ActionResult tryDrop(ServerPlayerEntity holder) {
        if (holder == null) return ActionResult.PASS;
        UUID hid = holder.getUuid();
        UUID tid = holding.get(hid);
        if (tid == null) return ActionResult.PASS;

        var server = holder.getEntityWorld().getServer();
        if (server == null) {
            cleanup(hid);
            return ActionResult.FAIL;
        }
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(tid);
        if (target == null) {
            cleanup(hid);
            return ActionResult.FAIL;
        }

        // Remove passenger relationship
        target.stopRiding();

        // Clear shield mode if active
        if (HumanShieldController.isActive(hid)) {
            HumanShieldController.disableShieldMode(holder, false);
        }

        holding.remove(hid);
        heldBy.remove(tid);
        chargeStartTime.remove(hid);

        // Remove charge BossBar
        ServerBossBar dropBar = chargeBossBars.remove(hid);
        if (dropBar != null) dropBar.removePlayer(holder);

        // Broadcast passenger-clear so clients drop the riding-on-head visual.
        var clearServer = holder.getEntityWorld().getServer();
        if (clearServer != null) {
            net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket clearPkt =
                    new net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket(holder);
            for (ServerPlayerEntity p : clearServer.getPlayerManager().getPlayerList()) {
                p.networkHandler.sendPacket(clearPkt);
            }
        }

        // Small drop velocity
        target.setVelocity(0, -0.3, 0);
        target.knockedBack = true;

        Lang.dropSuccess(holder, target);
        lastGrabTick.remove(hid);
        return ActionResult.SUCCESS;
    }

    // ===== THROW / YEET =====

    public static ActionResult yeetCurrentHold(ServerPlayerEntity holder) {
        return yeetCurrentHold(holder, 1.0f);
    }

    public static ActionResult yeetCurrentHold(ServerPlayerEntity holder, float power) {
        if (holder == null) return ActionResult.PASS;
        UUID hid = holder.getUuid();
        UUID tid = holding.get(hid);
        if (tid == null) return ActionResult.PASS;

        var server = holder.getEntityWorld().getServer();
        if (server == null) {
            cleanup(hid);
            return ActionResult.FAIL;
        }
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(tid);
        if (target == null) {
            cleanup(hid);
            return ActionResult.FAIL;
        }

        // Hunger check (from original project)
        if (!holder.isCreative()) {
            if (holder.getHungerManager().getFoodLevel() < 6) {
                Lang.sendActionBar(holder, "Too hungry to throw!", "太饿了，无法投掷！");
                return ActionResult.FAIL;
            }
            holder.getHungerManager().addExhaustion(18.0f);
        }

        // Calculate throw direction
        Vec3d holderLook = holder.getRotationVec(1.0f);
        Vec3d throwDir = new Vec3d(holderLook.x, 0, holderLook.z).normalize();
        if (throwDir.lengthSquared() < 0.001) throwDir = new Vec3d(0, 0, 1);

        // Calculate power based on charge (0.0 - 1.0)
        double scaledPower = MIN_THROW_POWER + (MAX_THROW_POWER - MIN_THROW_POWER) * Math.max(0.0f, Math.min(1.0f, power));

        // Calculate velocity
        double horizontalSpeed = 1.3 * scaledPower;
        double verticalSpeed = Math.min(0.8 + scaledPower * 0.15, 1.5);

        Vec3d velocity = new Vec3d(
                throwDir.x * horizontalSpeed,
                verticalSpeed,
                throwDir.z * horizontalSpeed
        );

        // Remove passenger relationship
        target.stopRiding();

        // Broadcast passenger-clear so every client immediately drops the riding visual.
        var syncServer2 = holder.getEntityWorld().getServer();
        if (syncServer2 != null) {
            var clearPkt = new net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket(holder);
            for (ServerPlayerEntity p : syncServer2.getPlayerManager().getPlayerList()) {
                p.networkHandler.sendPacket(clearPkt);
            }
        }

        // Lock held player's rotation to holder's rotation for 1 tick (view correction)
        float throwYaw = holder.getYaw();
        float throwPitch = holder.getPitch();
        target.setYaw(throwYaw);
        target.setBodyYaw(throwYaw);
        target.setHeadYaw(throwYaw);
        target.setPitch(throwPitch);
        target.lastYaw = throwYaw;
        target.lastBodyYaw = throwYaw;
        target.lastHeadYaw = throwYaw;
        target.lastPitch = throwPitch;

        // Force teleport to sync rotation immediately
        Vec3d releasePos = holder.getEntityPos().add(holder.getRotationVec(1.0f).multiply(1.5).multiply(1, 0, 1)).add(0, 0.5, 0);
        target.refreshPositionAndAngles(releasePos.x, releasePos.y, releasePos.z, throwYaw, throwPitch);
        target.networkHandler.requestTeleport(releasePos.x, releasePos.y, releasePos.z, throwYaw, throwPitch);

        // Clear shield mode if active
        if (HumanShieldController.isActive(hid)) {
            HumanShieldController.disableShieldMode(holder, false);
        }

        // Remove from holding maps
        holding.remove(hid);
        heldBy.remove(tid);

        // Remove charge BossBar
        ServerBossBar throwBar = chargeBossBars.remove(hid);
        if (throwBar != null) {
            throwBar.removePlayer(holder);
        }

        // Add to pending throws (delayed velocity application)
        ThrowTracker.schedulePendingThrow(tid, velocity);

        // Track as thrown player
        boolean wasOnFire = target.isOnFire();
        ThrowTracker.registerThrown(tid, wasOnFire);

        // Mark for trail particle effects
        LaunchedPlayerTracker.markPlayerAsLaunched(tid);

        // Apply throw immunity after release.
        ThrowTracker.applyImmunity(tid);

        // Effects
        ServerWorld world = holder.getEntityWorld();
        Vec3d pos = target.getEntityPos();
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.2f, 0.9f);
        world.spawnParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.5, pos.z, 8, 0.3, 0.3, 0.3, 0.05);

        Lang.yeetSuccess(holder, target);
        InteractionCooldowns.apply(hid, "grab", ModConfig.get().grabCooldownMs);
        com.dap.dapurhomies.compat.DanseCompat.playYeet(target); // Danse animation
        lastGrabTick.remove(hid);
        return ActionResult.SUCCESS;
    }

    // ===== CHARGE YEET (right-click while holding) =====

    public static ActionResult startCharge(ServerPlayerEntity holder) {
        if (holder == null) return ActionResult.PASS;
        if (!isHolding(holder)) return ActionResult.PASS;

        UUID hid = holder.getUuid();

        // Already charging - ignore, left-click handles release
        if (chargeStartTime.containsKey(hid)) {
            Lang.sendActionBar(holder, "\u00a7eAlready charging! Left-click to throw!",
                "\u00a7e\u5df2\u5728\u84c4\u529b\u4e2d\uff01\u5de6\u952e\u6295\u51fa\uff01");
            return ActionResult.SUCCESS;
        }

        // Start charging
        chargeStartTime.put(hid, TickClock.now());

        // Create BossBar for charge progress (like Ender Dragon health bar)
        ServerBossBar bossBar = new ServerBossBar(
                com.dap.dapurhomies.Lang.t(holder, "\u26a1 Charging Throw \u26a1", "\u26a1 \u84c4\u529b\u6295\u63b7\u4e2d \u26a1"),
                BossBar.Color.YELLOW,
                BossBar.Style.PROGRESS);
        bossBar.setPercent(0.0f);
        bossBar.addPlayer(holder);
        chargeBossBars.put(hid, bossBar);

        Lang.sendActionBar(holder, "\u00a7e\u26a1 Charge started! Left-click to throw!",
            "\u00a7e\u26a1 \u84c4\u529b\u5f00\u59cb\uff01\u5de6\u952e\u6295\u51fa\uff01");
        return ActionResult.SUCCESS;
    }

    public static ActionResult releaseCharge(ServerPlayerEntity holder) {
        if (holder == null) return ActionResult.PASS;
        UUID hid = holder.getUuid();

        // Remove BossBar
        ServerBossBar bossBar = chargeBossBars.remove(hid);
        if (bossBar != null) {
            bossBar.removePlayer(holder);
        }

        Long start = chargeStartTime.remove(hid);
        if (start == null) {
            // Not charging - do instant throw with default power
            return yeetCurrentHold(holder, 0.5f);
        }

        long elapsed = TickClock.now() - start;
        float power = Math.min(1.0f, (float) elapsed / CHARGE_MAX_TICKS);

        return yeetCurrentHold(holder, power);
    }

    private static void tickCharge(MinecraftServer server) {
        long now = TickClock.now();
        Iterator<Map.Entry<UUID, Long>> it = chargeStartTime.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            UUID hid = entry.getKey();
            long start = entry.getValue();

            ServerPlayerEntity holder = server.getPlayerManager().getPlayer(hid);
            if (holder == null || !isHolding(holder)) {
                // Remove any BossBar left behind by an invalid charge.
                ServerBossBar bar = chargeBossBars.remove(hid);
                if (bar != null && holder != null) bar.removePlayer(holder);
                it.remove();
                continue;
            }

            // Auto-release at full charge inline because releaseCharge() would mutate
            // chargeStartTime while it is being iterated.
            if (now - start >= CHARGE_AUTO_RELEASE_TICKS) {
                ServerBossBar bar = chargeBossBars.remove(hid);
                if (bar != null) bar.removePlayer(holder);
                it.remove(); // safe removal via iterator
                yeetCurrentHold(holder, 1.0f);
                continue;
            }

            // Charge particles
            long elapsed = now - start;
            float progress = Math.min(1.0f, (float) elapsed / CHARGE_MAX_TICKS);
            if (server.getOverworld().getTime() % 5 == 0) {
                Vec3d pos = holder.getEntityPos().add(0, 1, 0);
                ServerWorld world = holder.getEntityWorld();
                world.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z,
                    1 + (int)(progress * 3), 0.2, 0.2, 0.2, 0.05);
            }

            // Update BossBar progress (like Ender Dragon health bar)
            ServerBossBar bossBar = chargeBossBars.get(hid);
            if (bossBar != null) {
                bossBar.setPercent(progress);
                // Change color based on progress
                if (progress >= 1.0f) {
                    bossBar.setColor(BossBar.Color.RED);
                    bossBar.setName(com.dap.dapurhomies.Lang.t(holder,
                        "\u26a1 MAX CHARGE - RELEASE! \u26a1",
                        "\u26a1 \u84c4\u529b\u5df2\u6ee1 - \u91ca\u653e! \u26a1"));
                } else if (progress >= 0.7f) {
                    bossBar.setColor(BossBar.Color.PINK);
                } else if (progress >= 0.4f) {
                    bossBar.setColor(BossBar.Color.GREEN);
                }
            }
        }
    }

    // ===== ESCAPE =====

    public static ActionResult tryEscape(ServerPlayerEntity held) {
        if (held == null) return ActionResult.PASS;
        UUID tid = held.getUuid();
        UUID hid = heldBy.get(tid);
        if (hid == null) return ActionResult.PASS;

        var server = held.getEntityWorld().getServer();
        if (server == null) {
            cleanup(tid);
            return ActionResult.FAIL;
        }
        ServerPlayerEntity holder = server.getPlayerManager().getPlayer(hid);
        if (holder == null) {
            cleanup(tid);
            return ActionResult.FAIL;
        }

        // 20% chance to escape
        if (held.getRandom().nextFloat() < 0.2f) {
            held.stopRiding();
            holding.remove(hid);
            heldBy.remove(tid);
            chargeStartTime.remove(hid);

            // Remove charge BossBar
            if (holder != null) {
                ServerBossBar escapeBar = chargeBossBars.remove(hid);
                if (escapeBar != null) {
                    escapeBar.removePlayer(holder);
                }
            }

            // Clear shield mode if active
            if (HumanShieldController.isActive(hid)) {
                HumanShieldController.clearForEscape(hid);
            }

            // Push both apart
            Vec3d dir = held.getEntityPos().subtract(holder.getEntityPos()).normalize();
            if (dir.lengthSquared() < 0.001) dir = new Vec3d(1, 0, 0);
            held.setVelocity(dir.x * 0.5, 0.3, dir.z * 0.5);
            holder.setVelocity(-dir.x * 0.3, 0.1, -dir.z * 0.3);
            held.knockedBack = true;
            holder.knockedBack = true;

            Lang.escapeSuccess(held);
            return ActionResult.SUCCESS;
        } else {
            Lang.escapeFail(held);
            return ActionResult.FAIL;
        }
    }

    // ===== SHIELD MODE =====

    public static ActionResult toggleShieldMode(ServerPlayerEntity holder) {
        return HumanShieldController.toggleShieldMode(holder);
    }

    // ===== TICK =====

    public static void tick(MinecraftServer server) {
        ThrowTracker.tickPendingThrows(server);
        ThrowTracker.tickThrownPlayers(server);
        tickHeldPlayers(server);
        tickCarryPositions(server);
        tickCharge(server);
        ThrowTracker.cleanupExpiredImmunity();
    }

    private static void tickCarryPositions(MinecraftServer server) {
        carryTickCounter++;
        boolean doTeleport = (carryTickCounter % 2 == 0);   // every 2 ticks

        for (var e : holding.entrySet()) {
            UUID hid = e.getKey();
            ServerPlayerEntity holder = server.getPlayerManager().getPlayer(hid);
            ServerPlayerEntity held = server.getPlayerManager().getPlayer(e.getValue());
            if (holder == null || held == null) continue;
            if (holder.getEntityWorld() != held.getEntityWorld()) {
                cleanup(hid);
                continue;
            }
            if (HumanShieldController.isActive(hid)) {
                // Keep the intermediate armor stand anchored to the holder
                ArmorStandEntity stand = HumanShieldController.getStand(hid);
                if (stand != null && stand.isAlive()) {
                    Vec3d hp = holder.getEntityPos();
                    stand.setPosition(hp.x, hp.y, hp.z);
                    continue;
                }
                // Stand was killed/removed externally: drop shield mode and fall back to direct carry,
                // otherwise the held player stays frozen forever (travel cancelled, never re-teleported).
                HumanShieldController.disableShieldMode(holder, false);
            }

            // Always zero velocity & fall distance so the held player never falls
            held.setVelocity(0, 0, 0);
            held.knockedBack = true; // sync the zeroed velocity to the client
            held.fallDistance = 0;

            if (doTeleport) {
                double x = holder.getX();
                double y = holder.getY() + holder.getHeight() + 0.1;
                double z = holder.getZ();
                // requestTeleport every 2 ticks —— held player never falls, position authoritative.
                // Pass held's OWN yaw/pitch (free look, no camera lock).
                held.refreshPositionAndAngles(x, y, z, held.getYaw(), held.getPitch());
                held.networkHandler.requestTeleport(x, y, z, held.getYaw(), held.getPitch());
            }
        }
    }

    private static void tickHeldPlayers(MinecraftServer server) {
        // ConcurrentHashMap supports removal during iteration
        for (Map.Entry<UUID, UUID> entry : holding.entrySet()) {
            UUID hid = entry.getKey();
            UUID tid = entry.getValue();

            ServerPlayerEntity holder = server.getPlayerManager().getPlayer(hid);
            ServerPlayerEntity held = server.getPlayerManager().getPlayer(tid);

            if (holder == null || held == null || !holder.isAlive() || !held.isAlive()) {
                cleanup(hid);
                continue;
            }

            // Check distance
            if (holder.distanceTo(held) > THROW_RANGE * 2) {
                // Auto-drop when the pair drifts apart; cleanup also removes shield stand/state
                cleanup(hid);
                continue;
            }

            // Freeze holder movement while carrying (user request: cannot move but can look around)
            holder.setVelocity(0, holder.getVelocity().y, 0);
            holder.knockedBack = true;

            // Carrying slowdown is handled by CarryingSlowdown handler via AttributeModifier
        }
    }

    // ===== CLEANUP =====

    public static void cleanup(UUID playerId) {
        MinecraftServer server = DapUrHomiesMod.getServer();

        // If player is a holder
        UUID heldId = holding.remove(playerId);
        if (heldId != null) {
            heldBy.remove(heldId);
            if (server != null) {
                ServerPlayerEntity held = server.getPlayerManager().getPlayer(heldId);
                if (held != null) {
                    held.stopRiding();
                }
            }
        }

        // Always release this player's own shield stand, even if the holding entry was already gone
        HumanShieldController.cleanup(playerId);

        // If player is being held
        UUID holderId = heldBy.remove(playerId);
        if (holderId != null) {
            holding.remove(holderId);
            if (server != null) {
                ServerPlayerEntity holder = server.getPlayerManager().getPlayer(holderId);
                if (holder != null) {
                    for (Entity passenger : new ArrayList<>(holder.getPassengerList())) {
                        if (passenger instanceof ServerPlayerEntity && passenger.getUuid().equals(playerId)) {
                            passenger.stopRiding();
                        }
                    }
                }
            }
            HumanShieldController.cleanup(holderId);
        }

        ThrowTracker.cleanup(playerId);
        chargeStartTime.remove(playerId);

        // Remove charge BossBar
        ServerBossBar bar = chargeBossBars.remove(playerId);
        if (bar != null) {
            bar.clearPlayers(); // Remove from all viewers
        }

        // Clean up shield swap cooldown
        HumanShieldController.removeSwapCooldown(playerId);

        lastGrabTick.remove(playerId);
    }

    // ===== HELPER METHODS (for disconnect cleanup) =====

    public static UUID getHeldId(UUID holderId) {
        return holding.get(holderId);
    }

    public static UUID getHolderId(UUID heldId) {
        return heldBy.get(heldId);
    }

    /** True while the player is mid-flight after being thrown. */
    public static boolean isThrownFlying(UUID playerId) {
        return ThrowTracker.isThrownFlying(playerId);
    }

    // ===== SHIELD DAMAGE TRANSFER =====

    public static void registerShieldDamageEvent() {
        HumanShieldController.registerShieldDamageEvent();
    }

    public static void purgeExpiredCooldowns() {
        ThrowTracker.purgeExpiredCooldowns();
    }

    public static void fullCleanup() {
        for (UUID hid : new ArrayList<>(holding.keySet())) {
            cleanup(hid);
        }
        for (UUID tid : new ArrayList<>(heldBy.keySet())) {
            cleanup(tid);
        }
        ThrowTracker.fullCleanup();
        chargeStartTime.clear();
        for (ServerBossBar bar : chargeBossBars.values()) {
            if (bar != null) bar.clearPlayers();
        }
        chargeBossBars.clear();
        HumanShieldController.fullCleanup();
        lastGrabTick.clear();
    }

    /** true if this player grabbed someone within the last 10 ticks (500ms). */
    public static boolean justGrabbed(UUID playerId) {
        Long t = lastGrabTick.get(playerId);
        return t != null && (TickClock.now() - t) < 10;
    }
}

