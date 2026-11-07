package com.dap.dapurhomies.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = FabricLoader.getInstance().getConfigDir().resolve("dap-ur-homies.json").toFile();
    private static ModConfig INSTANCE;

    // Module toggles
    public boolean enableDap = true;
    public boolean enableHighFive = true;
    public boolean enableGrabYeet = true;
    public boolean enablePush = true;
    public boolean enableKick = true;
    public boolean enableHug = true;
    public boolean enableSlap = true;
    public boolean enableClap = true;

    // Ranges
    public double dapRange = 2.5;
    public double highFiveRange = 3.0;
    public double grabRange = 3.0;
    public double pushRange = 2.5;
    public double kickRange = 2.0;
    public double hugRange = 2.0;
    public double slapRange = 1.5;

    // Cooldowns (ms)
    public long dapCooldownMs = 1500;
    public long highFiveCooldownMs = 1500;
    public long grabCooldownMs = 1000;
    public long pushCooldownMs = 3000;
    public long kickCooldownMs = 2000;
    public long hugCooldownMs = 5000;
    public long slapCooldownMs = 3000;

    // LuckPerms: whether LuckPerms permissions also control "canReceive" (default false = file-only)
    public boolean luckPermsControlsReceive = false;

    // Global language for players that use "global" ("en" or "zh")
    public String globalLang = "en";

    // Admin bypass cooldown (default true = admins ignore cooldowns)
    public boolean adminBypassCooldown = true;

    // Protection: whether to check protection-mod claims before interacting
    public boolean protectionCheckEnabled = true;

    // Update check on startup (download is always manual via /dap update download)
    public boolean updateCheckEnabled = true;

    // Auto download and apply the new version after a check finds one (default off)
    public boolean autoUpdateEnabled = false;

    // Sound / effects
    public int highFiveSoundTicks = 40;   // 击掌音效总时长（tick），默认40=2秒，可用指令改
    public int clapIntervalTicks = 5;     // 击掌音效播放间隔

    // Charged Dap mode: 0 = hold-charge (original), 1 = cycle-charge (8s session with looping levels)
    public int chargedDapMode = 0;

    public static ModConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    public static void load() {
        if (FILE.exists()) {
            try (FileReader r = new FileReader(FILE)) {
                INSTANCE = GSON.fromJson(r, ModConfig.class);
            } catch (IOException | RuntimeException e) {
                System.err.println("[DapUrHomies] Failed to read config, using defaults: " + e.getMessage());
                INSTANCE = new ModConfig();
            }
        } else {
            INSTANCE = new ModConfig();
            save();
        }
        if (INSTANCE == null) INSTANCE = new ModConfig();
    }

    public static void save() {
        try {
            FILE.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(FILE)) {
                GSON.toJson(INSTANCE, w);
            }
        } catch (IOException e) {
            System.err.println("[DapUrHomies] Failed to save config");
        }
    }
}

