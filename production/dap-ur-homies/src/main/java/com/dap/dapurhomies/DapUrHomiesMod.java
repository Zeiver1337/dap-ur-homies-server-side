package com.dap.dapurhomies;

import com.dap.dapurhomies.command.DapCommands;
import com.dap.dapurhomies.command.InteractionCommands;
import com.dap.dapurhomies.compat.DanseCompat;
import com.dap.dapurhomies.config.ModConfig;
import com.dap.dapurhomies.effect.MahitoStatusEffect;
import com.dap.dapurhomies.interaction.InteractionModules;
import com.dap.dapurhomies.item.MahitoPotionItem;
import com.dap.dapurhomies.permission.PlayerPermissionManager;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DapUrHomiesMod implements ModInitializer {
    public static final String MOD_ID = "dap-ur-homies";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static StatusEffect MAHITO_EFFECT;
    public static RegistryEntry<StatusEffect> MAHITO_EFFECT_ENTRY;
    public static Item MAHITO_POTION;
    static boolean modEnabled = true;
    static MinecraftServer serverInstance;

    public static MinecraftServer getServer() { return serverInstance; }

    @Override
    public void onInitialize() {
        LOGGER.info("[DapUrHomies] Server-side mod initializing for MC 1.21.11...");

        ModConfig.load();
        Lang.loadGlobalLanguage();
        PlayerPermissionManager.init();

        Identifier effectId = Identifier.of(MOD_ID, "mahito");
        MAHITO_EFFECT = new MahitoStatusEffect();
        Registry.register(Registries.STATUS_EFFECT, effectId, MAHITO_EFFECT);
        MAHITO_EFFECT_ENTRY = Registries.STATUS_EFFECT.getEntry(effectId).orElseThrow();

        Identifier itemId = Identifier.of(MOD_ID, "mahito_potion");
        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, itemId);
        MAHITO_POTION = new MahitoPotionItem(new Item.Settings().maxCount(1).registryKey(itemKey));
        Registry.register(Registries.ITEM, itemId, MAHITO_POTION);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries -> entries.add(MAHITO_POTION));

        InteractionModules.registerAll();

        // Register Polymer resource pack for Mahito potion textures
        PolymerResourcePackUtils.addModAssets(MOD_ID);

        registerCommands();

        ModLifecycle.register();

        LOGGER.info("[DapUrHomies] Initialized successfully! Danse integration: " + DanseCompat.isDanseLoaded());
    }

    public static boolean isModEnabled() { return modEnabled; }
    public static void setModEnabled(boolean enabled) { modEnabled = enabled; }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, reg, env) -> {
            DapCommands.register(dispatcher);
            InteractionCommands.register(dispatcher);
        });
    }
}
