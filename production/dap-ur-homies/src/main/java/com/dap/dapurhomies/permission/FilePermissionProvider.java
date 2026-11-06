package com.dap.dapurhomies.permission;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FilePermissionProvider implements PermissionProvider {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = FabricLoader.getInstance().getConfigDir().resolve("dap-ur-homies-perms.json").toFile();
    private final Map<UUID, PlayerPreferences> perms = new ConcurrentHashMap<>();

    public FilePermissionProvider() {
        load();
    }

    @Override
    public boolean hasPermission(ServerPlayerEntity player, String node) {
        if (node.equals("dap-ur-homies.admin")) {
            var perms = player.getCommandSource().getPermissions();
            if (perms instanceof net.minecraft.command.permission.LeveledPermissionPredicate) {
                return ((net.minecraft.command.permission.LeveledPermissionPredicate) perms).getLevel().getLevel() >= 2;
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean canInitiate(ServerPlayerEntity player, String type) {
        return get(player.getUuid()).allows(type);
    }

    @Override
    public boolean canReceive(ServerPlayerEntity player, String type) {
        return get(player.getUuid()).allows(type);
    }

    @Override
    public void setPreference(ServerPlayerEntity player, String type, boolean value) {
        get(player.getUuid()).set(type, value);
        save();
    }

    @Override
    public PlayerPreferences getPerms(ServerPlayerEntity player) {
        return get(player.getUuid());
    }

    @Override
    public PlayerPreferences getPerms(UUID playerId) {
        return get(playerId);
    }

    @Override
    public String getLang(UUID playerId) {
        PlayerPreferences p = get(playerId);
        return p.lang != null ? p.lang : "global";
    }

    @Override
    public void setLang(UUID playerId, String lang) {
        PlayerPreferences p = get(playerId);
        p.lang = lang;
        save();
    }

    private PlayerPreferences get(UUID playerId) {
        return perms.computeIfAbsent(playerId, k -> new PlayerPreferences());
    }

    public void load() {
        if (!FILE.exists()) return;
        try (FileReader r = new FileReader(FILE)) {
            Type type = new TypeToken<Map<String, PlayerPreferences>>(){}.getType();
            Map<String, PlayerPreferences> raw = GSON.fromJson(r, type);
            if (raw != null) {
                perms.clear();
                for (Map.Entry<String, PlayerPreferences> e : raw.entrySet()) {
                    if (e.getValue() == null) continue;
                    try {
                        perms.put(UUID.fromString(e.getKey()), e.getValue());
                    } catch (IllegalArgumentException ignored) {
                        System.err.println("[DapUrHomies] Skipping invalid UUID in permissions: " + e.getKey());
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[DapUrHomies] Failed to load permissions: " + e.getMessage());
        }
    }

    public void save() {
        try {
            FILE.getParentFile().mkdirs();
            Map<String, PlayerPreferences> raw = new ConcurrentHashMap<>();
            for (Map.Entry<UUID, PlayerPreferences> e : perms.entrySet()) {
                raw.put(e.getKey().toString(), e.getValue());
            }
            try (FileWriter w = new FileWriter(FILE)) {
                GSON.toJson(raw, w);
            }
        } catch (IOException e) {
            System.err.println("[DapUrHomies] Failed to save permissions: " + e.getMessage());
        }
    }
}

