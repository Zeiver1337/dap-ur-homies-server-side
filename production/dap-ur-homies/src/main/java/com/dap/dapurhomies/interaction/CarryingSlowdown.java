package com.dap.dapurhomies.interaction;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Applies speed slowdown to players who are holding/grabbing other players.
 * Ported from original Dap-ur-homie project.
 */
public class CarryingSlowdown {

    public static final float CARRY_SPEED_MULTIPLIER = 0.6f;
    private static final double SLOWDOWN_AMOUNT = -(1.0 - CARRY_SPEED_MULTIPLIER) * 0.1;
    private static final Identifier MODIFIER_ID = Identifier.of("dap-ur-homies", "carrying_slowdown");

    public static void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            updateSlowdown(player);
        }
    }

    private static void updateSlowdown(ServerPlayerEntity player) {
        boolean isCarrying = GrabYeetHandler.isHolding(player);

        EntityAttributeInstance speedAttr = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speedAttr == null) return;

        var existingModifier = speedAttr.getModifier(MODIFIER_ID);

        if (isCarrying) {
            if (existingModifier == null) {
                speedAttr.addTemporaryModifier(new EntityAttributeModifier(
                        MODIFIER_ID,
                        SLOWDOWN_AMOUNT,
                        EntityAttributeModifier.Operation.ADD_VALUE
                ));
            }
        } else {
            if (existingModifier != null) {
                speedAttr.removeModifier(MODIFIER_ID);
            }
        }
    }
}

