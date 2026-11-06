package com.dap.dapurhomies.command;

import com.dap.dapurhomies.DapUrHomiesMod;
import com.dap.dapurhomies.Lang;
import com.dap.dapurhomies.interaction.FallCatchHandler;
import com.dap.dapurhomies.interaction.SimpleInteractions;
import com.dap.dapurhomies.permission.PlayerPermissionManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class InteractionCommands {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // /hug
        dispatcher.register(CommandManager.literal("hug").executes(c -> {
            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
            Lang.sendChat(p, "\u00a7e[Hug] \u00a7fBoth players sneak + right-click to hug", "\u00a7e[\u62e5\u62b1] \u00a7f\u53cc\u65b9\u8e72\u4e0b+\u53f3\u952e\u62e5\u62b1");
            return 1;
        }));

        // /push
        dispatcher.register(CommandManager.literal("push").executes(c -> {
            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
            Lang.sendChat(p, "\u00a7e[Push] \u00a7fSprint + right-click player to push", "\u00a7e[\u63a8] \u00a7f\u75be\u8dd1+\u53f3\u952e\u73a9\u5bb6\u63a8");
            return 1;
        }));

        // /kick
        dispatcher.register(CommandManager.literal("kick").executes(c -> {
            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
            Lang.sendChat(p, "\u00a7e[Kick] \u00a7fLook at feet + right-click to kick; sneak + look at feet to charge a DropKick", "\u00a7e[\u8e22] \u00a7f\u770b\u811a\u90e8+\u53f3\u952e\u8e22\uff1b\u8e72\u4e0b+\u770b\u811a\u90e8\u53ef\u84c4\u529b\u98de\u8e22");
            return 1;
        }));

        // /grab
        dispatcher.register(CommandManager.literal("grab").executes(c -> {
            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
            Lang.sendChat(p, "\u00a7e[Grab] \u00a7fSneak + left-click = Grab, right-click = charge, left-click = throw", "\u00a7e[\u6293\u53d6] \u00a7f\u8e72\u4e0b+\u5de6\u952e=\u6293\u53d6, \u53f3\u952e=\u84c4\u529b, \u5de6\u952e=\u6295\u51fa");
            return 1;
        }));

        // /catchready
        dispatcher.register(CommandManager.literal("catchready").executes(c -> {
            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
            if (!DapUrHomiesMod.isModEnabled()) { Lang.modStatus(p, false); return 0; }
            FallCatchHandler.enterCatchReady(p);
            return 1;
        }));

        // /clap
        dispatcher.register(CommandManager.literal("clap").executes(c -> {
            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
            if (!DapUrHomiesMod.isModEnabled()) { Lang.modStatus(p, false); return 0; }
            if (!PlayerPermissionManager.canInitiate(p, "clap")) { Lang.playerTypeDisabled(p, "clap"); return 0; }
            SimpleInteractions.onClap(p);
            return 1;
        }));

        // /mariojump
        dispatcher.register(CommandManager.literal("mariojump").executes(c -> {
            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
            if (!DapUrHomiesMod.isModEnabled()) { Lang.modStatus(p, false); return 0; }
            SimpleInteractions.onMarioJump(p);
            return 1;
        }));

        // /daplang self <en|zh|global>
        var daplangRoot = CommandManager.literal("daplang");
        daplangRoot.then(CommandManager.literal("self")
            .then(CommandManager.argument("lang", com.mojang.brigadier.arguments.StringArgumentType.string())
                .suggests((ctx, builder) -> {
                    builder.suggest("en"); builder.suggest("zh"); builder.suggest("global");
                    return builder.buildFuture();
                })
                .executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    String lang = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "lang").toLowerCase();
                    if (!lang.equals("en") && !lang.equals("zh") && !lang.equals("global")) {
                        p.sendMessage(Text.literal("\u00a7c[Dap] Invalid language! Use: en, zh, global"), false);
                        return 0;
                    }
                    PlayerPermissionManager.setLang(p.getUuid(), lang);
                    Lang.langSetSelf(p, lang);
                    return 1;
                })));
        // /daplang global <en|zh> (admin only)
        daplangRoot.then(CommandManager.literal("global")
            .then(CommandManager.argument("lang", com.mojang.brigadier.arguments.StringArgumentType.string())
                .suggests((ctx, builder) -> {
                    builder.suggest("en"); builder.suggest("zh");
                    return builder.buildFuture();
                })
                .executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    if (!PlayerPermissionManager.hasPermission(p, "dap-ur-homies.admin")) {
                        Lang.noPermission(p); return 0;
                    }
                    String lang = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "lang").toLowerCase();
                    if (!lang.equals("en") && !lang.equals("zh")) {
                        p.sendMessage(Text.literal("\u00a7c[Dap] Invalid language! Use: en, zh"), false);
                        return 0;
                    }
                    Lang.setGlobalLanguage(lang.equals("zh") ? Lang.Language.ZH : Lang.Language.EN);
                    Lang.langSetGlobal(p, lang);
                    return 1;
                })));
        // /daplang (show current)
        daplangRoot.executes(c -> {
            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
            Lang.langCurrent(p);
            return 1;
        });
        dispatcher.register(daplangRoot);

        // /daphelp
        dispatcher.register(CommandManager.literal("daphelp").executes(c -> {
            String[] linesEN = {
                "\u00a7e===== Dap Ur Homies Help (v0.6.3) =====",
                "\u00a7fGrab: \u00a7eSneak + left-click player",
                "\u00a7fThrow: \u00a7eLeft-click while holding (uses charge)",
                "\u00a7fCharge: \u00a7eRight-click while holding",
                "\u00a7fDrop: \u00a7eLeft-click while holding (no charge)",
                "\u00a7fShield Mode: \u00a7eSneak + right-click while holding",
                "\u00a7fDap: \u00a7eRight-click player (empty hand)",
                "\u00a7fHighFive: \u00a7eBoth players right-click each other",
                "\u00a7fPush: \u00a7eSprint + right-click player",
                "\u00a7fKick: \u00a7eLook at feet + right-click",
                "\u00a7fDropKick: \u00a7eSneak + look at feet, right-click twice",
                "\u00a7fHug: \u00a7eBoth players sneak + right-click",
                "\u00a7fSlap: \u00a7eRight-click from behind",
                "\u00a7fClap: \u00a7e/clap command",
                "\u00a7fMarioJump: \u00a7e/mariojump command",
                "\u00a7fCatchReady: \u00a7e/catchready command",
                "\u00a7fToggle Self: \u00a7e/dap on|off",
                "\u00a7fToggle Other (admin): \u00a7e/dap on|off <player>",
                "\u00a7fToggle Module: \u00a7e/dap <type> on|off [player]",
                "\u00a7fTypes: dap, grab, push, kick, hug, slap, clap, danse",
                "\u00a7fStatus: \u00a7e/dap status [player]",
                "\u00a7fCharged Mode: \u00a7e/dap chargedmode <0|1> (0=hold, 1=cycle)",
                "\u00a7fLanguage: \u00a7e/daplang",
                "\u00a7e=================================="
            };
            String[] linesZH = {
                "\u00a7e===== Dap Ur Homies \u5e2e\u52a9 (v0.6.3) =====",
                "\u00a7f\u6293\u53d6: \u00a7e\u8e72\u4e0b+\u5de6\u952e\u73a9\u5bb6",
                "\u00a7f\u6295\u51fa: \u00a7e\u6293\u53d6\u540e\u5de6\u952e\uff08\u4f7f\u7528\u84c4\u529b\u503c\uff09",
                "\u00a7f\u84c4\u529b: \u00a7e\u6293\u53d6\u540e\u53f3\u952e",
                "\u00a7f\u653e\u4e0b: \u00a7e\u6293\u53d6\u540e\u5de6\u952e\uff08\u672a\u84c4\u529b\uff09",
                "\u00a7f\u76fe\u724c\u6a21\u5f0f: \u00a7e\u6293\u53d6\u540e\u8e72\u4e0b+\u53f3\u952e",
                "\u00a7f\u78b0\u62f3: \u00a7e\u7a7a\u624b\u53f3\u952e\u73a9\u5bb6",
                "\u00a7f\u51fb\u638c: \u00a7e\u53cc\u65b9\u4e92\u76f8\u53f3\u952e",
                "\u00a7f\u63a8: \u00a7e\u75be\u8dd1+\u53f3\u952e\u73a9\u5bb6",
                "\u00a7f\u8e22: \u00a7e\u770b\u811a\u90e8+\u53f3\u952e",
                "\u00a7f\u98de\u8e22: \u00a7e\u8e72\u4e0b+\u770b\u811a\u90e8\uff0c\u53f3\u952e\u4e24\u6b21",
                "\u00a7f\u62e5\u62b1: \u00a7e\u53cc\u65b9\u8e72\u4e0b+\u53f3\u952e",
                "\u00a7f\u62cd\u5df4\u638c: \u00a7e\u4ece\u80cc\u540e\u53f3\u952e",
                "\u00a7f\u9f13\u638c: \u00a7e/clap \u547d\u4ee4",
                "\u00a7f\u9a6c\u91cc\u5965\u8df3: \u00a7e/mariojump \u547d\u4ee4",
                "\u00a7f\u63a5\u4f4f\u51c6\u5907: \u00a7e/catchready \u547d\u4ee4",
                "\u00a7f\u5f00\u5173\u81ea\u5df1: \u00a7e/dap on|off",
                "\u00a7f\u5f00\u5173\u4ed6\u4eba(\u7ba1\u7406\u5458): \u00a7e/dap on|off <\u73a9\u5bb6>",
                "\u00a7f\u5f00\u5173\u6a21\u5757: \u00a7e/dap <\u7c7b\u578b> on|off [\u73a9\u5bb6]",
                "\u00a7f\u7c7b\u578b: dap, grab, push, kick, hug, slap, clap, danse",
                "\u00a7f\u72b6\u6001: \u00a7e/dap status [\u73a9\u5bb6]",
                "\u00a7f\u84c4\u529b\u6a21\u5f0f: \u00a7e/dap chargedmode <0|1> (0=\u6309\u4f4f\u84c4\u529b, 1=\u5faa\u73af\u84c4\u529b)",
                "\u00a7f\u8bed\u8a00: \u00a7e/daplang",
                "\u00a7e=================================="
            };
            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
            String[] lines = Lang.getPlayerLang(p) == Lang.Language.ZH ? linesZH : linesEN;
            for (String line : lines) {
                p.sendMessage(Text.literal(line), false);
            }
            return 1;
        }));

        // /daphow - How to trigger actions
        dispatcher.register(CommandManager.literal("daphow").executes(c -> {
            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
            boolean isZh = Lang.getPlayerLang(p) == Lang.Language.ZH;
            String[] howEN = {
                "\u00a7e===== How to Trigger Actions =====",
                "\u00a7fDap: \u00a7eRight-click player (empty hand)",
                "\u00a7fHighFive: \u00a7eBoth players right-click each other",
                "\u00a7fCharged Dap: \u00a7eShift+Right-click player",
                "\u00a7f  Mode 0: \u00a77Hold to charge, release to strike",
                "\u00a7f  Mode 1: \u00a778s cycle charge, click again to release",
                "\u00a7fGrab: \u00a7eSneak + Left-click player",
                "\u00a7fThrow: \u00a7eLeft-click while holding player",
                "\u00a7fCharge Throw: \u00a7eRight-click while holding, then Left-click",
                "\u00a7fDrop: \u00a7eLeft-click while holding (no charge)",
                "\u00a7fShield Mode: \u00a7eSneak + Right-click while holding",
                "\u00a7fPush: \u00a7eSprint + Right-click player",
                "\u00a7fKick: \u00a7eLook at feet + Right-click",
                "\u00a7fDropKick: \u00a7eSneak + look at feet, Right-click twice",
                "\u00a7fHug: \u00a7eBoth players Sneak + Right-click",
                "\u00a7fSlap: \u00a7eRight-click from behind",
                "\u00a7fClap: \u00a7e/clap command",
                "\u00a7fMarioJump: \u00a7e/mariojump command",
                "\u00a7fCatchReady: \u00a7e/catchready command",
                "\u00a7e=================================="
            };
            String[] howZH = {
                "\u00a7e===== \u52a8\u4f5c\u89e6\u53d1\u65b9\u5f0f =====",
                "\u00a7f\u78b0\u62f3: \u00a7e\u7a7a\u624b\u53f3\u952e\u73a9\u5bb6",
                "\u00a7f\u51fb\u638c: \u00a7e\u53cc\u65b9\u4e92\u76f8\u53f3\u952e",
                "\u00a7f\u84c4\u529b\u78b0\u62f3: \u00a7eShift+\u53f3\u952e\u73a9\u5bb6",
                "\u00a7f  \u6a21\u5f0f0: \u00a77\u6309\u4f4f\u84c4\u529b\uff0c\u677e\u5f00\u91ca\u653e",
                "\u00a7f  \u6a21\u5f0f1: \u00a778\u79d2\u5faa\u73af\u84c4\u529b\uff0c\u518d\u6b21\u70b9\u51fb\u91ca\u653e",
                "\u00a7f\u6293\u53d6: \u00a7e\u8e72\u4e0b+\u5de6\u952e\u73a9\u5bb6",
                "\u00a7f\u6295\u51fa: \u00a7e\u6293\u53d6\u540e\u5de6\u952e",
                "\u00a7f\u84c4\u529b\u6295\u51fa: \u00a7e\u53f3\u952e\u84c4\u529b\uff0c\u518d\u5de6\u952e",
                "\u00a7f\u653e\u4e0b: \u00a7e\u6293\u53d6\u540e\u8e72\u4e0b",
                "\u00a7f\u76fe\u724c\u6a21\u5f0f: \u00a7e\u6293\u53d6\u540e\u8e72\u4e0b+\u53f3\u952e",
                "\u00a7f\u63a8: \u00a7e\u8e72\u4e0b+\u53f3\u952e\u73a9\u5bb6",
                "\u00a7f\u8e22: \u00a7e\u770b\u811a\u90e8+\u53f3\u952e",
                "\u00a7f\u98de\u8e22: \u00a7e\u53f3\u952e\u84c4\u529b3\u79d2\uff0c\u677e\u5f00\u91ca\u653e",
                "\u00a7f\u62e5\u62b1: \u00a7e\u53cc\u65b9\u8e72\u4e0b+\u53f3\u952e",
                "\u00a7f\u62cd\u5df4\u638c: \u00a7e\u4ece\u80cc\u540e\u53f3\u952e",
                "\u00a7f\u9f13\u638c: \u00a7e/clap \u547d\u4ee4",
                "\u00a7f\u9a6c\u91cc\u5965\u8df3: \u00a7e/mariojump \u547d\u4ee4",
                "\u00a7f\u63a5\u4f4f\u51c6\u5907: \u00a7e/catchready \u547d\u4ee4",
                "\u00a7e=================================="
            };
            String[] lines = isZh ? howZH : howEN;
            for (String line : lines) {
                p.sendMessage(Text.literal(line), false);
            }
            return 1;
        }));
    }
}
