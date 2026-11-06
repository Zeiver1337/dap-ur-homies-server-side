package com.dap.dapurhomies.mixin;

import com.dap.dapurhomies.config.ModConfig;
import com.dap.dapurhomies.interaction.GrabYeetHandler;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RIGHT-click on air/block (with item or block targeted) = start charge / shield toggle
 */
@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onPlayerInteractItem", at = @At("HEAD"), cancellable = true)
    private void dapurhomies$chargeOnRightClick(PlayerInteractItemC2SPacket packet, CallbackInfo ci) {
        if (!ModConfig.get().enableGrabYeet) return;
        if (!GrabYeetHandler.isHolding(player)) return;
        if (player.isSneaking()) { GrabYeetHandler.toggleShieldMode(player); ci.cancel(); return; }
        GrabYeetHandler.startCharge(player);
        ci.cancel();
    }
}

