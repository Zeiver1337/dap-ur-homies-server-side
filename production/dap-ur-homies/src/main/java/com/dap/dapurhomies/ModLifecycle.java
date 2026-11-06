package com.dap.dapurhomies;

import com.dap.dapurhomies.interaction.DapSessionManager;
import com.dap.dapurhomies.interaction.GrabYeetHandler;
import com.dap.dapurhomies.interaction.InteractionModules;
import com.dap.dapurhomies.interaction.PlayerInteractionState;
import com.dap.dapurhomies.util.TickClock;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ModLifecycle {
    private static int purgeTickCounter = 0;
    private static final Set<UUID> handSwingingLastTick = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> jumpHeldLastTick = ConcurrentHashMap.newKeySet();

    static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();

            UUID heldId = GrabYeetHandler.getHeldId(id);
            if (heldId != null) {
                ServerPlayerEntity held = server.getPlayerManager().getPlayer(heldId);
                if (held != null) {
                    held.stopRiding();
                    Lang.sendActionBar(held, "Your carrier disconnected!", "携带你的玩家已断开连接！");
                }
            }

            // If disconnecting player is being held, notify their holder
            UUID holderId = GrabYeetHandler.getHolderId(id);
            if (holderId != null) {
                ServerPlayerEntity holder = server.getPlayerManager().getPlayer(holderId);
                if (holder != null) {
                    Lang.sendActionBar(holder, "Your held player disconnected!", "你携带的玩家已断开连接！");
                }
            }

            InteractionModules.cleanupAll(id);
            handSwingingLastTick.remove(id);
            jumpHeldLastTick.remove(id);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            DapUrHomiesMod.serverInstance = server;
            TickClock.update(server);
            if (!DapUrHomiesMod.modEnabled) return;
            InteractionModules.tickAll(server);
            boolean hasAnyHolding = PlayerInteractionState.hasAnyHolding();
            for (var player : server.getPlayerManager().getPlayerList()) {
                // Detect a fresh jump while being held -> attempt escape (documented feature)
                if (GrabYeetHandler.isHeld(player)) {
                    boolean jumping = player.getPlayerInput().jump();
                    boolean wasJumping = jumpHeldLastTick.contains(player.getUuid());
                    if (jumping && !wasJumping) {
                        GrabYeetHandler.tryEscape(player);
                    }
                    if (jumping) jumpHeldLastTick.add(player.getUuid());
                    else jumpHeldLastTick.remove(player.getUuid());
                } else {
                    jumpHeldLastTick.remove(player.getUuid());
                }

                // Detect left-click while holding someone (handSwinging rising edge)
                // This catches left-clicks on AIR (AttackEntityCallback only fires on entities)
                // Skip check if nobody is holding anyone (performance optimization)
                if (hasAnyHolding && GrabYeetHandler.isHolding(player)) {
                    boolean isSwinging = player.handSwinging;
                    boolean wasSwinging = handSwingingLastTick.contains(player.getUuid());
                    if (isSwinging && !wasSwinging && !GrabYeetHandler.justGrabbed(player.getUuid())) {
                        if (GrabYeetHandler.isCharging(player.getUuid())) {
                            GrabYeetHandler.releaseCharge(player);   // charged -> yeet
                        } else {
                            GrabYeetHandler.tryDrop(player);          // not charged -> drop
                        }
                    }
                    if (isSwinging) handSwingingLastTick.add(player.getUuid());
                    else handSwingingLastTick.remove(player.getUuid());
                } else {
                    handSwingingLastTick.remove(player.getUuid());
                }

                // Mahito effect: random erratic soul-distortion movement
                if (player.hasStatusEffect(DapUrHomiesMod.MAHITO_EFFECT_ENTRY)) {
                    var inst = player.getStatusEffect(DapUrHomiesMod.MAHITO_EFFECT_ENTRY);
                    int amp = (inst != null) ? inst.getAmplifier() : 0;
                    if (player.getRandom().nextFloat() < 0.08f) {
                        double s = 0.15 + amp * 0.05;
                        player.addVelocity(
                            (player.getRandom().nextDouble() - 0.5) * s,
                            0.08 + player.getRandom().nextDouble() * 0.05,
                            (player.getRandom().nextDouble() - 0.5) * s
                        );
                        player.knockedBack = true; // Sync velocity to the client.
                    }
                }
            }
            if (++purgeTickCounter >= 200) {
                purgeTickCounter = 0;
                InteractionModules.purgeAll();
            }
        });
    }

    /**
     * Turning the mod off must also release every piece of live state. Otherwise a player
     * who is being carried stays frozen forever: tickAll() is skipped, but the client-side
     * travel cancellation mixin keeps suppressing their movement.
     */
    public static void disableModAndCleanup(ServerPlayerEntity actor) {
        DapUrHomiesMod.modEnabled = false;
        MinecraftServer server = actor != null ? actor.getEntityWorld().getServer() : DapUrHomiesMod.serverInstance;
        if (server != null) {
            for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
                UUID id = online.getUuid();
                InteractionModules.cleanupAll(id);
                handSwingingLastTick.remove(id);
                jumpHeldLastTick.remove(id);
            }
        }
        GrabYeetHandler.fullCleanup();
        DapSessionManager.clearAll();
        if (actor != null) Lang.modDisabled(actor);
    }
}
