package com.dap.dapurhomies.effect;

import eu.pb4.polymer.core.api.other.PolymerStatusEffect;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffects;
import xyz.nucleoid.packettweaker.PacketContext;

/**
 * Mahito status effect — soul manipulation distortion.
 * Actual effect logic is handled in DapUrHomiesMod server tick
 * to avoid method-signature compatibility issues across MC versions.
 */
public class MahitoStatusEffect extends StatusEffect implements PolymerStatusEffect {
    public MahitoStatusEffect() {
        super(StatusEffectCategory.NEUTRAL, 0x9b59b6);
    }

    @Override
    public StatusEffect getPolymerReplacement(StatusEffect effect, PacketContext context) {
        return StatusEffects.UNLUCK.value();
    }
}

