package com.dap.dapurhomies;

import com.dap.dapurhomies.config.ModConfig;
import com.dap.dapurhomies.permission.PlayerPermissionManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class Lang {
    public enum Language { EN, ZH }
    private static Language globalLang = Language.EN;

    public static void setGlobalLanguage(Language lang) {
        globalLang = lang;
        ModConfig cfg = ModConfig.get();
        cfg.globalLang = (lang == Language.ZH) ? "zh" : "en";
        ModConfig.save();
    }

    public static Language getGlobalLanguage() { return globalLang; }

    /** Restore the global language from the config file. */
    public static void loadGlobalLanguage() {
        String v = ModConfig.get().globalLang;
        globalLang = "zh".equalsIgnoreCase(v) ? Language.ZH : Language.EN;
    }

    /** Get a player's effective language. */
    public static Language getPlayerLang(ServerPlayerEntity player) {
        if (player == null) return globalLang;
        String pl = PlayerPermissionManager.getLang(player.getUuid());
        if ("en".equalsIgnoreCase(pl)) return Language.EN;
        if ("zh".equalsIgnoreCase(pl)) return Language.ZH;
        return globalLang; // "global" or unset
    }

    public static String get(ServerPlayerEntity player, String en, String zh) {
        return getPlayerLang(player) == Language.ZH ? zh : en;
    }

    public static Text t(ServerPlayerEntity player, String en, String zh) {
        return Text.literal(get(player, en, zh));
    }

    public static Text t(String en, String zh) {
        return Text.literal(globalLang == Language.ZH ? zh : en);
    }

    public static void sendActionBar(ServerPlayerEntity player, String en, String zh) {
        if (player == null) return;
        player.sendMessage(t(player, en, zh), true);
    }

    public static void sendActionBar(ServerPlayerEntity player, String message) {
        if (player == null) return;
        player.sendMessage(Text.literal(message), true);
    }

    public static void sendChat(ServerPlayerEntity player, String en, String zh) {
        if (player == null) return;
        player.sendMessage(t(player, en, zh), false);
    }

    // ===== Dap =====
    public static void dapSuccess(ServerPlayerEntity p, ServerPlayerEntity target) {
        sendActionBar(p, "§e✊ Dap!", "§e✊ 碰拳!");
        sendActionBar(target, "§e✊ Dapped by " + p.getName().getString() + "!",
            "§e✊ " + p.getName().getString() + " 和你碰拳了!");
    }

    public static void highFiveSuccess(ServerPlayerEntity p1, ServerPlayerEntity p2, int tier) {
        String[] tierNamesEN = {"", "Speedy", "Epic", "LEGENDARY"};
        String[] tierNamesZH = {"", "迅捷", "史诗", "传说"};
        String tierNameEN = tier >= 1 && tier <= 3 ? " " + tierNamesEN[tier] : "";
        String tierNameZH = tier >= 1 && tier <= 3 ? " " + tierNamesZH[tier] : "";
        sendActionBar(p1, "§6✋" + tierNameEN + " High Five!", "§6✋" + tierNameZH + " 击掌!");
        sendActionBar(p2, "§6✋" + tierNameEN + " High Five!", "§6✋" + tierNameZH + " 击掌!");
    }

    // ===== Grab =====
    public static void grabSuccess(ServerPlayerEntity holder, ServerPlayerEntity victim) {
        sendActionBar(holder, "§a Grabbed " + victim.getName().getString() + "! Right-click=charge, Left-click=throw!",
            "§a 抓取了 " + victim.getName().getString() + "! 右键蓄力，左键投出!");
        sendActionBar(victim, "§c Grabbed by " + holder.getName().getString() + "!",
            "§c 被 " + holder.getName().getString() + " 抓取了!");
    }

    public static void yeetSuccess(ServerPlayerEntity holder, ServerPlayerEntity victim) {
        sendActionBar(holder, "§6 YEET!", "§6 投掷!");
        sendActionBar(victim, "§c YEETED!", "§c 被投掷了!");
    }

    public static void dropSuccess(ServerPlayerEntity holder, ServerPlayerEntity target) {
        sendActionBar(holder, "§c Dropped!", "§c 放下了!");
        sendActionBar(target, "§c Dropped!", "§c 被放下了!");
    }

    public static void escapeSuccess(ServerPlayerEntity held) {
        sendActionBar(held, "§a Escaped!", "§a 逃脱了!");
    }

    public static void escapeFail(ServerPlayerEntity held) {
        sendActionBar(held, "§c Escape failed!", "§c 逃脱失败!");
    }

    // ===== Push =====
    public static void pushSuccess(ServerPlayerEntity pusher, ServerPlayerEntity target) {
        sendActionBar(pusher, "§e Push!", "§e 推!");
        sendActionBar(target, "§c Pushed!", "§c 被推了!");
    }

    // ===== Kick =====
    public static void kickSuccess(ServerPlayerEntity kicker, ServerPlayerEntity target) {
        sendActionBar(kicker, "§c Kick!", "§c 踢!");
        sendActionBar(target, "§c Kicked by " + kicker.getName().getString() + "!",
            "§c 被 " + kicker.getName().getString() + " 踢了!");
    }

    // ===== Hug =====
    public static void hugStart(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        sendActionBar(p1, "§d Hugging...", "§d 拥抱中...");
        sendActionBar(p2, "§d Hugging...", "§d 拥抱中...");
    }

    public static void hugEnd(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        sendActionBar(p1, "§e Hug ended", "§e 拥抱结束");
        sendActionBar(p2, "§e Hug ended", "§e 拥抱结束");
    }

    // ===== Slap =====
    public static void slapSuccess(ServerPlayerEntity slapper, ServerPlayerEntity target) {
        sendActionBar(slapper, "\u00a76 SLAP!", "\u00a76 扇巴掌!");
        sendActionBar(target, "§c§l I like ya cut G", "§c§l 这一巴掌打得好!");
    }

    // ===== Clap =====
    public static void clapSuccess(ServerPlayerEntity player) {
        sendActionBar(player, "§e Clap!", "§e 鼓掌!");
    }

    // ===== Mario Jump =====
    public static void marioJumpSuccess(ServerPlayerEntity player) {
        sendActionBar(player, "§a Mario Jump!", "§a 马里奥跳!");
    }

    // ===== Cooldown =====
    public static void cooldown(ServerPlayerEntity player) {
        sendActionBar(player, "§7 On cooldown...", "§7 冷却中...");
    }

    // ===== Charge messages =====
    public static void chargeStart(ServerPlayerEntity player) {
        sendChat(player, "§e⚡ Charging Dap... Hold to power up!",
            "§e⚡ 碰拳蓄力中... 持续按住增加威力!");
    }

    public static void chargeLevel(ServerPlayerEntity player, int level) {
        String[] levelsEN = {"Weak", "Nice", "Great", "Legendary", "Fire", "PERFECT"};
        String[] levelsZH = {"弱小", "不错", "很棒", "传奇", "烈火", "完美"};
        String bar = "█".repeat(level) + "░".repeat(6 - level);
        sendActionBar(player,
            "§e⚡ " + levelsEN[Math.min(level, 5)] + " " + bar,
            "§e⚡ " + levelsZH[Math.min(level, 5)] + " " + bar);
    }

    // ===== Toggle messages =====
    public static void modEnabled(ServerPlayerEntity player) {
        sendChat(player, "§a[DapUrHomies] All interactions enabled!", "§a[DapUrHomies] 所有互动功能已开启!");
    }

    public static void modDisabled(ServerPlayerEntity player) {
        sendChat(player, "§c[DapUrHomies] All interactions disabled!", "§c[DapUrHomies] 所有互动功能已关闭!");
    }

    public static void alreadyEnabled(ServerPlayerEntity player) {
        sendChat(player, "§e[DapUrHomies] Already enabled!", "§e[DapUrHomies] 已经是开启状态!");
    }

    public static void alreadyDisabled(ServerPlayerEntity player) {
        sendChat(player, "§e[DapUrHomies] Already disabled!", "§e[DapUrHomies] 已经是关闭状态!");
    }

    public static void modStatus(ServerPlayerEntity player, boolean enabled) {
        if (enabled) {
            sendChat(player, "§a[DapUrHomies] Status: ENABLED", "§a[DapUrHomies] 状态: 已开启");
        } else {
            sendChat(player, "§c[DapUrHomies] Status: DISABLED", "§c[DapUrHomies] 状态: 已关闭");
        }
    }

    public static void noPermission(ServerPlayerEntity player) {
        sendChat(player, "§c[DapUrHomies] You don't have permission!", "§c[DapUrHomies] 你没有权限!");
    }

    // ===== Player-level permission messages =====
    public static void playerEnabled(ServerPlayerEntity player) {
        sendChat(player, "§a[DapUrHomies] Your interactions are now enabled!", "§a[DapUrHomies] 你的互动功能已开启!");
    }

    public static void playerDisabled(ServerPlayerEntity player) {
        sendChat(player, "§c[DapUrHomies] Your interactions are now disabled! Others cannot interact with you.", "§c[DapUrHomies] 你的互动功能已关闭! 别人无法对你互动。");
    }

    public static void playerEnabledByAdmin(ServerPlayerEntity admin, ServerPlayerEntity target) {
        sendChat(admin, "§a[DapUrHomies] Enabled interactions for " + target.getName().getString(), "§a[DapUrHomies] 已开启 " + target.getName().getString() + " 的互动功能");
        sendChat(target, "§a[DapUrHomies] An admin has enabled your interactions!", "§a[DapUrHomies] 管理员已开启你的互动功能!");
    }

    public static void playerDisabledByAdmin(ServerPlayerEntity admin, ServerPlayerEntity target) {
        sendChat(admin, "§c[DapUrHomies] Disabled interactions for " + target.getName().getString(), "§c[DapUrHomies] 已关闭 " + target.getName().getString() + " 的互动功能");
        sendChat(target, "§c[DapUrHomies] An admin has disabled your interactions!", "§c[DapUrHomies] 管理员已关闭你的互动功能!");
    }

    public static void playerStatus(ServerPlayerEntity player, com.dap.dapurhomies.permission.PlayerPreferences perms) {
        String en = "§e[DapUrHomies] Your perms: " + formatPerms(perms);
        String zh = "§e[DapUrHomies] 你的权限: " + formatPermsZH(perms);
        sendChat(player, en, zh);
    }

    public static void playerStatus(ServerPlayerEntity viewer, ServerPlayerEntity target, com.dap.dapurhomies.permission.PlayerPreferences perms) {
        String en = "§e[DapUrHomies] " + target.getName().getString() + " perms: " + formatPerms(perms);
        String zh = "§e[DapUrHomies] " + target.getName().getString() + " 权限: " + formatPermsZH(perms);
        sendChat(viewer, en, zh);
    }

    public static void typeToggled(ServerPlayerEntity player, String type, boolean value) {
        String state = value ? "§aON" : "§cOFF";
        String stateZH = value ? "§a开启" : "§c关闭";
        sendChat(player, "§e[DapUrHomies] " + type + " set to " + state, "§e[DapUrHomies] " + type + " 已" + stateZH);
    }

    public static void typeToggledByAdmin(ServerPlayerEntity admin, ServerPlayerEntity target, String type, boolean value) {
        String state = value ? "§aON" : "§cOFF";
        String stateZH = value ? "§a开启" : "§c关闭";
        sendChat(admin, "§e[DapUrHomies] Set " + type + " to " + state + " for " + target.getName().getString(),
            "§e[DapUrHomies] 已将 " + target.getName().getString() + " 的 " + type + " " + stateZH);
        sendChat(target, "§e[DapUrHomies] Admin set your " + type + " to " + state,
            "§e[DapUrHomies] 管理员已将你的 " + type + " " + stateZH);
    }

    public static void playerTypeDisabled(ServerPlayerEntity player, String type) {
        sendChat(player, "§c[DapUrHomies] Your " + type + " is disabled!", "§c[DapUrHomies] 你的 " + type + " 功能已关闭!");
    }

    private static String formatPerms(com.dap.dapurhomies.permission.PlayerPreferences p) {
        return "master=" + bool(p.enabled) + " dap=" + bool(p.dap) + " grab=" + bool(p.grab) +
               " push=" + bool(p.push) + " kick=" + bool(p.kick) + " hug=" + bool(p.hug) +
               " slap=" + bool(p.slap) + " clap=" + bool(p.clap);
    }

    private static String formatPermsZH(com.dap.dapurhomies.permission.PlayerPreferences p) {
        return "总=" + boolZH(p.enabled) + " 碰拳=" + boolZH(p.dap) + " 抓取=" + boolZH(p.grab) +
               " 推=" + boolZH(p.push) + " 踢=" + boolZH(p.kick) + " 拥抱=" + boolZH(p.hug) +
               " 拍巴掌=" + boolZH(p.slap) + " 鼓掌=" + boolZH(p.clap);
    }

    private static String bool(boolean v) { return v ? "§aY" : "§cN"; }
    private static String boolZH(boolean v) { return v ? "§a开" : "§c关"; }

    // ===== Interaction blocking reasons =====
    public static void interactionBlocked(ServerPlayerEntity player, String reasonEN, String reasonZH) {
        sendChat(player, "§c[DapUrHomies] " + reasonEN, "§c[DapUrHomies] " + reasonZH);
    }

    public static void targetDisabledInteraction(ServerPlayerEntity player, ServerPlayerEntity target) {
        sendChat(player,
            "§c[DapUrHomies] " + target.getName().getString() + " has disabled interactions!",
            "§c[DapUrHomies] " + target.getName().getString() + " 已关闭互动功能！");
    }

    public static void targetDisabledType(ServerPlayerEntity player, ServerPlayerEntity target, String type) {
        sendChat(player,
            "§c[DapUrHomies] " + target.getName().getString() + " has disabled " + type + "!",
            "§c[DapUrHomies] " + target.getName().getString() + " 已关闭 " + type + " 功能！");
    }

    public static void danseDisabled(ServerPlayerEntity player) {
        sendChat(player,
            "§c[DapUrHomies] Danse animations are disabled for you!",
            "§c[DapUrHomies] 你的 Danse 动画已关闭！");
    }

    public static void chargeCancelled(ServerPlayerEntity player) {
        sendChat(player, "§c⚡ Target lost! Charge cancelled.", "§c⚡ 目标丢失！蓄力取消。");
    }

    public static void chargeRelease(ServerPlayerEntity player, int level) {
        String[] levelsEN = {"Weak", "Nice", "Great", "Legendary", "Fire", "PERFECT"};
        String[] levelsZH = {"弱小", "不错", "很棒", "传奇", "烈火", "完美"};
        sendChat(player,
            "§e⚡ " + levelsEN[Math.min(level, 5)] + " Dap!",
            "§e⚡ " + levelsZH[Math.min(level, 5)] + " 碰拳!");
    }

    public static void yeetChargeRelease(ServerPlayerEntity player, int level) {
        String[] levelsEN = {"Weak", "Nice", "Great", "Legendary", "Fire", "PERFECT"};
        String[] levelsZH = {"弱小", "不错", "很棒", "传奇", "烈火", "完美"};
        sendChat(player,
            "§e⚡ " + levelsEN[Math.min(level, 5)] + " Throw!",
            "§e⚡ " + levelsZH[Math.min(level, 5)] + " 投掷!");
    }

    // ===== Reload =====
    public static void configReloaded(ServerPlayerEntity player) {
        sendChat(player, "§a[DapUrHomies] Config reloaded!", "§a[DapUrHomies] 配置已重载!");
    }

    public static void configReloadFailed(ServerPlayerEntity player) {
        sendChat(player, "§c[DapUrHomies] Config reload failed!", "§c[DapUrHomies] 配置重载失败!");
    }

    // ===== Protection =====
    public static void protectedTarget(ServerPlayerEntity player, ServerPlayerEntity target) {
        sendChat(player,
            "§c[DapUrHomies] " + target.getName().getString() + " is in a protected area!",
            "§c[DapUrHomies] " + target.getName().getString() + " 处于受保护区域!");
    }

    // ===== Admin Cooldown =====
    public static void adminCooldownToggled(ServerPlayerEntity player, boolean enabled) {
        sendChat(player,
            enabled ? "§a[Dap] Admin cooldown bypass enabled!" : "§c[Dap] Admin cooldown bypass disabled!",
            enabled ? "§a[Dap] 管理员冷却绕过已开启!" : "§c[Dap] 管理员冷却绕过已关闭!");
    }

    public static void cooldownSet(ServerPlayerEntity player, String type, int ms) {
        sendChat(player,
            "§a[Dap] " + type + " cooldown set to " + ms + "ms",
            "§a[Dap] " + type + " 冷却已设置为 " + ms + "毫秒");
    }

    public static void invalidCooldownType(ServerPlayerEntity player) {
        sendChat(player,
            "§c[Dap] Invalid cooldown type! Use: dap, highFive, grab, push, kick, hug, slap",
            "§c[Dap] 无效的冷却类型! 可用: dap, highFive, grab, push, kick, hug, slap");
    }

    // ===== Language messages =====
    public static void langSetSelf(ServerPlayerEntity player, String lang) {
        if ("en".equalsIgnoreCase(lang)) {
            sendChat(player, "§a[Dap] Your language set to English", "§a[Dap] 你的语言已设置为 English");
        } else if ("zh".equalsIgnoreCase(lang)) {
            sendChat(player, "§a[Dap] Your language set to Chinese", "§a[Dap] 你的语言已设置为 中文");
        } else {
            sendChat(player, "§a[Dap] Your language follows global setting", "§a[Dap] 你的语言已跟随全局设置");
        }
    }

    public static void langSetGlobal(ServerPlayerEntity player, String lang) {
        if ("en".equalsIgnoreCase(lang)) {
            sendChat(player, "§a[Dap] Global language set to English", "§a[Dap] 全局语言已设置为 English");
        } else {
            sendChat(player, "§a[Dap] Global language set to Chinese", "§a[Dap] 全局语言已设置为 中文");
        }
    }

    public static void langCurrent(ServerPlayerEntity player) {
        Language g = getGlobalLanguage();
        String pl = PlayerPermissionManager.getLang(player.getUuid());
        String globalStr = g == Language.ZH ? "Chinese" : "English";
        String personalStr;
        if ("en".equalsIgnoreCase(pl)) personalStr = "English";
        else if ("zh".equalsIgnoreCase(pl)) personalStr = "Chinese";
        else personalStr = "Global (" + globalStr + ")";
        sendChat(player,
            "§e[Dap] Global: " + globalStr + " | Your: " + personalStr,
            "§e[Dap] 全局: " + (g == Language.ZH ? "中文" : "英文") + " | 你的: " + ("en".equalsIgnoreCase(pl) ? "英文" : "zh".equalsIgnoreCase(pl) ? "中文" : "跟随全局 (" + (g == Language.ZH ? "中文" : "英文") + ")"));
    }

    public static void chargedModeSet(ServerPlayerEntity player, int mode) {
        if (mode == 0) {
            sendChat(player, "§a[Dap] Charged Dap mode set to: HOLD (original)",
                "§a[Dap] 蓄力碰拳模式已设置为: 按住蓄力（原形式）");
        } else {
            sendChat(player, "§a[Dap] Charged Dap mode set to: CYCLE (8s looping charge)",
                "§a[Dap] 蓄力碰拳模式已设置为: 循环蓄力（8秒会话）");
        }
    }
}

