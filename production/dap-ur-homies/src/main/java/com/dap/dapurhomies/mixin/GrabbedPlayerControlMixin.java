package com.dap.dapurhomies.mixin;

import com.dap.dapurhomies.interaction.GrabYeetHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While held overhead: ignore ALL movement input (server discards it), but DO NOT lock
 * rotation — the held player can freely look around.
 */
@Mixin(PlayerEntity.class)
public abstract class GrabbedPlayerControlMixin {

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void dapurhomies$ignoreMovement(Vec3d movementInput, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity)(Object)this;
        if (GrabYeetHandler.isHeld(self.getUuid())) {
            ci.cancel();   // discard movement; rotation (yaw/pitch packets) still apply
        }
    }
}

