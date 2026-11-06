package com.dap.dapurhomies.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Looping clap sound effect for high-five and dap interactions.
 * Plays a rhythmic clapping sound pattern over 2 seconds (40 ticks).
 * Uses snare drum sounds with varying pitch to simulate realistic clapping.
 */
public class ClapLooper {
    private static class Loop {
        ServerWorld w;
        Vec3d pos;
        int left;      // total ticks remaining
        int tickCounter; // counts down to next clap
        float basePitch; // base pitch for this clap sequence
        int rhythmIndex; // current position in rhythm pattern (per-loop, not global)
        int interval;    // configurable spacing offset (5 = original rhythm)
    }
    private static final List<Loop> loops = new ArrayList<>();

    // Clap rhythm pattern: ticks between each clap (creates a natural clapping rhythm)
    // Pattern: fast-fast-slow-fast-fast-slow = like real applause
    private static final int[] CLAP_RHYTHM = {2, 3, 2, 3, 2, 4, 2, 3, 2, 3, 2, 4};
    private static final float[] CLAP_PITCH = {1.0f, 1.1f, 0.9f, 1.2f, 1.0f, 0.85f, 1.15f, 1.0f, 1.05f, 0.95f, 1.1f, 0.9f};
    private static final float[] CLAP_VOLUME = {0.8f, 0.9f, 0.7f, 1.0f, 0.8f, 0.6f, 0.95f, 0.85f, 0.75f, 0.9f, 0.8f, 0.7f};

    public static void start(ServerWorld w, Vec3d pos, int totalTicks, int interval) {
        Loop l = new Loop();
        l.w = w;
        l.pos = pos;
        l.left = Math.max(totalTicks, 1);
        l.tickCounter = 0; // play immediately on first tick
        l.basePitch = 1.0f;
        l.interval = Math.max(1, interval);
        loops.add(l);
    }

    public static void tick(MinecraftServer server) {
        Iterator<Loop> it = loops.iterator();
        while (it.hasNext()) {
            Loop l = it.next();
            l.left--;
            if (l.left <= 0) { it.remove(); continue; }

            l.tickCounter--;
            if (l.tickCounter <= 0) {
                // Set next clap timing based on rhythm pattern
                int idx = l.rhythmIndex % CLAP_RHYTHM.length;
                // interval=5 keeps the original rhythm; larger values space claps further apart
                l.tickCounter = Math.max(1, CLAP_RHYTHM[idx] + (l.interval - 5));

                // Play clap sound with varying pitch and volume for realism
                float pitch = CLAP_PITCH[idx % CLAP_PITCH.length];
                float volume = CLAP_VOLUME[idx % CLAP_VOLUME.length];

                // Use snare drum sound for more realistic clapping
                l.w.playSound(null, l.pos.x, l.pos.y, l.pos.z,
                        SoundEvents.BLOCK_NOTE_BLOCK_SNARE, SoundCategory.PLAYERS, volume, pitch);

                l.rhythmIndex++;
            }
        }
    }
}

