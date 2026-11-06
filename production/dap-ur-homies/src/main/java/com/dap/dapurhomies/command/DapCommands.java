package com.dap.dapurhomies.command;

import com.dap.dapurhomies.DapUrHomiesMod;
import com.dap.dapurhomies.Lang;
import com.dap.dapurhomies.ModLifecycle;
import com.dap.dapurhomies.config.ModConfig;
import com.dap.dapurhomies.permission.PlayerPermissionManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class DapCommands {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var dapRoot = CommandManager.literal("dap");

        // /dap on [player]
        dapRoot.then(CommandManager.literal("on")
            .executes(c -> {
                ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                if (PlayerPermissionManager.hasPermission(p, "dap-ur-homies.admin")) {
                    if (DapUrHomiesMod.isModEnabled()) Lang.alreadyEnabled(p);
                    else { DapUrHomiesMod.setModEnabled(true); Lang.modEnabled(p); }
                } else {
                    if (com.dap.dapurhomies.util.AdminManager.isForceDisabled(p.getUuid())) {
                        Lang.interactionBlocked(p, "An admin disabled this for you!", "管理员已关闭你的交互功能！");
                        return 0;
                    }
                    PlayerPermissionManager.set(p, "enabled", true);
                    Lang.playerEnabled(p);
                }
                return 1;
            })
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    if (!PlayerPermissionManager.hasPermission(p, "dap-ur-homies.admin")) {
                        Lang.noPermission(p); return 0;
                    }
                    ServerPlayerEntity t = EntityArgumentType.getPlayer(c, "player");
                    com.dap.dapurhomies.util.AdminManager.setForceDisabled(t.getUuid(), false);
                    PlayerPermissionManager.set(t, "enabled", true);
                    Lang.playerEnabledByAdmin(p, t);
                    return 1;
                })));

        // /dap off [player]
        dapRoot.then(CommandManager.literal("off")
            .executes(c -> {
                ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                if (PlayerPermissionManager.hasPermission(p, "dap-ur-homies.admin")) {
                    if (!DapUrHomiesMod.isModEnabled()) Lang.alreadyDisabled(p);
                    else ModLifecycle.disableModAndCleanup(p);
                } else {
                    PlayerPermissionManager.set(p, "enabled", false);
                    Lang.playerDisabled(p);
                }
                return 1;
            })
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    if (!PlayerPermissionManager.hasPermission(p, "dap-ur-homies.admin")) {
                        Lang.noPermission(p); return 0;
                    }
                    ServerPlayerEntity t = EntityArgumentType.getPlayer(c, "player");
                    com.dap.dapurhomies.util.AdminManager.setForceDisabled(t.getUuid(), true);
                    PlayerPermissionManager.set(t, "enabled", false);
                    Lang.playerDisabledByAdmin(p, t);
                    return 1;
                })));

        // /dap status [player]
        dapRoot.then(CommandManager.literal("status")
            .executes(c -> {
                ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                Lang.modStatus(p, DapUrHomiesMod.isModEnabled());
                Lang.playerStatus(p, PlayerPermissionManager.get(p));
                return 1;
            })
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    if (!PlayerPermissionManager.hasPermission(p, "dap-ur-homies.admin")) {
                        Lang.noPermission(p); return 0;
                    }
                    ServerPlayerEntity t = EntityArgumentType.getPlayer(c, "player");
                    Lang.modStatus(p, DapUrHomiesMod.isModEnabled());
                    Lang.playerStatus(p, t, PlayerPermissionManager.get(t));
                    return 1;
                })));

        // /dap reload
        dapRoot.then(CommandManager.literal("reload")
            .executes(c -> {
                ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                if (!PlayerPermissionManager.hasPermission(p, "dap-ur-homies.admin")) {
                    Lang.noPermission(p); return 0;
                }
                try {
                    ModConfig.load();
                    Lang.configReloaded(p);
                } catch (Exception e) {
                    Lang.configReloadFailed(p);
                }
                return 1;
            }));

        // /dap admincooldown on|off
        dapRoot.then(CommandManager.literal("admincooldown")
            .then(CommandManager.literal("on")
                .executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    if (!PlayerPermissionManager.hasPermission(p, "dap-ur-homies.admin")) {
                        Lang.noPermission(p); return 0;
                    }
                    ModConfig.get().adminBypassCooldown = true;
                    ModConfig.save();
                    Lang.adminCooldownToggled(p, true);
                    return 1;
                }))
            .then(CommandManager.literal("off")
                .executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    if (!PlayerPermissionManager.hasPermission(p, "dap-ur-homies.admin")) {
                        Lang.noPermission(p); return 0;
                    }
                    ModConfig.get().adminBypassCooldown = false;
                    ModConfig.save();
                    Lang.adminCooldownToggled(p, false);
                    return 1;
                })));

        String[] types = {"dap", "grab", "push", "kick", "hug", "slap", "clap", "danse"};
        for (String type : types) {
            var typeLit = CommandManager.literal(type);
            typeLit.then(CommandManager.literal("on")
                .executes(c -> toggleType(c.getSource().getPlayerOrThrow(), type, true, null))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes(c -> {
                        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                        if (!PlayerPermissionManager.hasPermission(p, "dap-ur-homies.admin")) {
                            Lang.noPermission(p); return 0;
                        }
                        return toggleType(p, type, true, EntityArgumentType.getPlayer(c, "player"));
                    })));
            typeLit.then(CommandManager.literal("off")
                .executes(c -> toggleType(c.getSource().getPlayerOrThrow(), type, false, null))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes(c -> {
                        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                        if (!PlayerPermissionManager.hasPermission(p, "dap-ur-homies.admin")) {
                            Lang.noPermission(p); return 0;
                        }
                        return toggleType(p, type, false, EntityArgumentType.getPlayer(c, "player"));
                    })));
            dapRoot.then(typeLit);
        }

        // /dap ban <player> <type|all> <minutes>
        dapRoot.then(CommandManager.literal("ban")

            .then(CommandManager.argument("player", EntityArgumentType.player())
            .then(CommandManager.argument("type", com.mojang.brigadier.arguments.StringArgumentType.word())
            .then(CommandManager.argument("minutes", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                .executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    if (!PlayerPermissionManager.hasPermission(p, "dap-ur-homies.admin")) {
                        Lang.noPermission(p); return 0;
                    }
                    ServerPlayerEntity t = EntityArgumentType.getPlayer(c, "player");
                    String type = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "type");
                    if (!com.dap.dapurhomies.util.AdminManager.isValidType(type)) {
                        Lang.invalidCooldownType(p); return 0;
                    }
                    int min = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "minutes");
                    com.dap.dapurhomies.util.AdminManager.ban(t.getUuid(), type, min * 1200L);
                    c.getSource().sendFeedback(() -> Text.literal("Banned " + t.getName().getString() + " from " + type + " for " + min + "min"), false);
                    return 1;
                })))));

        // /dap unban <player> <type|all>
        dapRoot.then(CommandManager.literal("unban")

            .then(CommandManager.argument("player", EntityArgumentType.player())
            .then(CommandManager.argument("type", com.mojang.brigadier.arguments.StringArgumentType.word())
                .executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    if (!PlayerPermissionManager.hasPermission(p, "dap-ur-homies.admin")) {
                        Lang.noPermission(p); return 0;
                    }
                    ServerPlayerEntity t = EntityArgumentType.getPlayer(c, "player");
                    String type = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "type");
                    if (!com.dap.dapurhomies.util.AdminManager.isValidType(type)) {
                        Lang.invalidCooldownType(p); return 0;
                    }
                    com.dap.dapurhomies.util.AdminManager.unban(t.getUuid(), type);
                    c.getSource().sendFeedback(() -> Text.literal("Unbanned " + t.getName().getString()), false);
                    return 1;
                }))));

        // /dap cooldown ms|set ...
        dapRoot.then(CommandManager.literal("cooldown")
            .then(CommandManager.literal("ms")
            .then(CommandManager.argument("type", com.mojang.brigadier.arguments.StringArgumentType.string())
                .suggests((ctx, builder) -> {
                    String[] suggest = {"dap", "highFive", "grab", "push", "kick", "hug", "slap"};
                    for (String t : suggest) builder.suggest(t);
                    return builder.buildFuture();
                })
                .then(CommandManager.argument("ms", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 300000))
                    .executes(c -> {
                        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                        if (!PlayerPermissionManager.hasPermission(p, "dap-ur-homies.admin")) {
                            Lang.noPermission(p); return 0;
                        }
                        String type = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "type");
                        int ms = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "ms");
                        switch (type) {
                            case "dap" -> ModConfig.get().dapCooldownMs = ms;
                            case "highFive" -> ModConfig.get().highFiveCooldownMs = ms;
                            case "grab" -> ModConfig.get().grabCooldownMs = ms;
                            case "push" -> ModConfig.get().pushCooldownMs = ms;
                            case "kick" -> ModConfig.get().kickCooldownMs = ms;
                            case "hug" -> ModConfig.get().hugCooldownMs = ms;
                            case "slap" -> ModConfig.get().slapCooldownMs = ms;
                            default -> { Lang.invalidCooldownType(p); return 0; }
                        }
                        ModConfig.save();
                        Lang.cooldownSet(p, type, ms);
                        return 1;
                    }))))
            .then(CommandManager.literal("set")
            .then(CommandManager.argument("type", com.mojang.brigadier.arguments.StringArgumentType.word())
            .then(CommandManager.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                .executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    if (!PlayerPermissionManager.hasPermission(p, "dap-ur-homies.admin")) {
                        Lang.noPermission(p); return 0;
                    }
                    String type = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "type");
                    if (!com.dap.dapurhomies.util.AdminManager.isValidType(type) || "all".equals(type)) {
                        Lang.invalidCooldownType(p); return 0;
                    }
                    long ticks = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "seconds") * 20L;
                    com.dap.dapurhomies.util.AdminManager.setGlobalCooldown(type, ticks);
                    c.getSource().sendFeedback(() -> Text.literal("Global " + type + " cooldown = " + (ticks/20) + "s"), false);
                    return 1;
                })
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes(c -> {
                        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                        if (!PlayerPermissionManager.hasPermission(p, "dap-ur-homies.admin")) { Lang.noPermission(p); return 0; }
                        String type = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "type");
                        if (!com.dap.dapurhomies.util.AdminManager.isValidType(type) || "all".equals(type)) {
                            Lang.invalidCooldownType(p); return 0;
                        }
                        long ticks = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "seconds") * 20L;
                        ServerPlayerEntity t = EntityArgumentType.getPlayer(c, "player");
                        com.dap.dapurhomies.util.AdminManager.setPlayerCooldown(t.getUuid(), type, ticks);
                        c.getSource().sendFeedback(() -> Text.literal(t.getName().getString() + " " + type + " cooldown = " + (ticks/20) + "s"), false);
                        return 1;
                    }))))));

        // /dap sound <ticks>
        dapRoot.then(CommandManager.literal("sound")

            .then(CommandManager.argument("ticks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                .executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    if (!PlayerPermissionManager.hasPermission(p, "dap-ur-homies.admin")) { Lang.noPermission(p); return 0; }
                    ModConfig.get().highFiveSoundTicks = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "ticks");
                    ModConfig.save();
                    c.getSource().sendFeedback(() -> Text.literal("HighFive sound = " + ModConfig.get().highFiveSoundTicks + " ticks"), false);
                    return 1;
                })));

        // /dap chargedmode <0|1>
        dapRoot.then(CommandManager.literal("chargedmode")
            .then(CommandManager.argument("mode", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 1))
                .executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    if (!PlayerPermissionManager.hasPermission(p, "dap-ur-homies.admin")) {
                        Lang.noPermission(p); return 0;
                    }
                    int mode = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "mode");
                    ModConfig.get().chargedDapMode = mode;
                    ModConfig.save();
                    Lang.chargedModeSet(p, mode);
                    return 1;
                })));

        // Default /dap
        dapRoot.executes(c -> {
            c.getSource().sendFeedback(() -> Lang.t(
                "\u00a7e[Dap] \u00a7f/dap on|off [player] | /dap <type> on|off [player] | /dap status [player]",
                "\u00a7e[\u78b0\u62f3] \u00a7f/dap on|off [\u73a9\u5bb6] | /dap <\u7c7b\u578b> on|off [\u73a9\u5bb6] | /dap status [\u73a9\u5bb6]"
            ), false);
            return 1;
        });

        dispatcher.register(dapRoot);
    }

    private static int toggleType(ServerPlayerEntity sender, String type, boolean value, ServerPlayerEntity target) {
        ServerPlayerEntity actualTarget = (target != null) ? target : sender;
        PlayerPermissionManager.set(actualTarget, type, value);
        if (target != null && !sender.equals(target)) {
            Lang.typeToggledByAdmin(sender, target, type, value);
        } else {
            Lang.typeToggled(sender, type, value);
        }
        return 1;
    }
}
