package com.dap.dapurhomies.interaction;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DapSessionManager - ported from original Dap-ur-homie project.
 * Manages active dap sessions between pairs of players.
 */
public class DapSessionManager {
    private static final Map<UUID, DapSession> activeSessions = new ConcurrentHashMap<>();
    private static final Set<UUID> playersInSession = ConcurrentHashMap.newKeySet();

    public static DapSession createSession(UUID playerA, UUID playerB, double targetDistance, DapSession.DapType type) {
        if (isInSession(playerA) || isInSession(playerB)) {
            return null;
        }
        DapSession session = new DapSession(playerA, playerB, targetDistance, type);
        activeSessions.put(playerA, session);
        playersInSession.add(playerA);
        playersInSession.add(playerB);
        return session;
    }

    public static DapSession getSession(UUID playerId) {
        DapSession session = activeSessions.get(playerId);
        if (session != null) return session;
        for (DapSession s : activeSessions.values()) {
            if (s.getPlayerBId().equals(playerId)) {
                return s;
            }
        }
        return null;
    }

    public static boolean isInSession(UUID playerId) {
        return playersInSession.contains(playerId);
    }

    public static void removeSession(UUID playerA) {
        DapSession session = activeSessions.remove(playerA);
        if (session != null) {
            playersInSession.remove(session.getPlayerAId());
            playersInSession.remove(session.getPlayerBId());
        }
    }

    public static void removeSessionForPlayer(UUID playerId) {
        DapSession session = getSession(playerId);
        if (session != null) {
            removeSession(session.getPlayerAId());
        }
    }

    public static void tick(MinecraftServer server) {
        List<DapSession> sessionsToTick = new ArrayList<>(activeSessions.values());
        for (DapSession session : sessionsToTick) {
            session.tick(server);
            if (session.isPositioningComplete() || session.getTickCount() > 100) {
                UUID playerA = session.getPlayerAId();
                UUID playerB = session.getPlayerBId();
                activeSessions.remove(playerA);
                playersInSession.remove(playerA);
                playersInSession.remove(playerB);
            }
        }
    }

    public static void clearAll() {
        activeSessions.clear();
        playersInSession.clear();
    }

    public static void cleanup(UUID playerId) {
        removeSessionForPlayer(playerId);
    }
}

