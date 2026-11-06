package com.dap.dapurhomies.interaction;

import com.dap.dapurhomies.util.ClapLooper;
import com.dap.dapurhomies.util.CooldownManager;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Lifecycle registry for all interaction modules.
 * The main class no longer hardcodes every handler's register/tick/cleanup/purge calls;
 * modules are declared once here and driven uniformly. Tick/cleanup/purge ordering
 * preserves the historical execution order of the hardcoded calls.
 */
public final class InteractionModules {
    private InteractionModules() {}

    private static final Consumer<MinecraftServer> NO_TICK = server -> {};
    private static final Consumer<UUID> NO_CLEANUP = id -> {};
    private static final Runnable NO_PURGE = () -> {};
    private static final Runnable NO_REGISTER = () -> {};

    private record Module(Runnable onRegister, Consumer<MinecraftServer> onTick,
                          Consumer<UUID> onCleanup, Runnable onPurge) {}

    private static Module module(Runnable register, Consumer<MinecraftServer> tick,
                                 Consumer<UUID> cleanup, Runnable purge) {
        return new Module(register, tick, cleanup, purge);
    }

    private static final List<Module> MODULES = List.of(
            module(InteractionRouter::register, NO_TICK, NO_CLEANUP, NO_PURGE),
            module(NO_REGISTER, DapHandler::tick, DapHandler::cleanup, DapHandler::purgeExpiredCooldowns),
            module(NO_REGISTER, DapSessionManager::tick, DapSessionManager::cleanup, NO_PURGE),
            module(() -> {
                       GrabYeetHandler.register();
                       GrabYeetHandler.registerShieldDamageEvent();
                   },
                   GrabYeetHandler::tick, GrabYeetHandler::cleanup, GrabYeetHandler::purgeExpiredCooldowns),
            module(NO_REGISTER, HugHandler::tick, HugHandler::cleanup, NO_PURGE),
            module(NO_REGISTER, KickHandler::tick,
                   id -> {
                       KickHandler.cleanup(id);
                       KickHandler.cancelCharge(id);
                   },
                   KickHandler::purgeExpiredCooldowns),
            module(NO_REGISTER, PushHandler::tick, PushHandler::cleanup, PushHandler::purgeExpiredCooldowns),
            module(FallCatchHandler::register, FallCatchHandler::tick, FallCatchHandler::cleanup, NO_PURGE),
            module(NO_REGISTER, CarryingSlowdown::tick, NO_CLEANUP, NO_PURGE),
            module(NO_REGISTER, LaunchedPlayerTracker::tick, LaunchedPlayerTracker::cleanup, NO_PURGE),
            module(NO_REGISTER, SimpleInteractions::tick, SimpleInteractions::cleanup, NO_PURGE),
            // util modules sharing the same lifecycle
            module(NO_REGISTER, ClapLooper::tick, NO_CLEANUP, NO_PURGE),
            module(NO_REGISTER, NO_TICK, CooldownManager::cleanup, CooldownManager::purgeExpired)
    );

    public static void registerAll() {
        for (Module m : MODULES) m.onRegister().run();
    }

    public static void tickAll(MinecraftServer server) {
        for (Module m : MODULES) m.onTick().accept(server);
    }

    public static void cleanupAll(UUID playerId) {
        for (Module m : MODULES) m.onCleanup().accept(playerId);
    }

    public static void purgeAll() {
        for (Module m : MODULES) m.onPurge().run();
    }
}
