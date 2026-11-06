package com.dap.dapurhomies.permission;

public class PlayerPreferences {
    public boolean enabled = true;   // master switch
    public boolean dap = true;
    public boolean grab = true;
    public boolean push = true;
    public boolean kick = true;
    public boolean hug = true;
    public boolean slap = true;
    public boolean clap = true;
    public boolean danse = true;     // danse animation toggle
    public String lang = "global";   // "global", "en", "zh"

    public boolean allows(String type) {
        if (!enabled) return false;
        return switch (type) {
            case "enabled" -> enabled;
            case "dap" -> dap;
            case "grab" -> grab;
            case "push" -> push;
            case "kick" -> kick;
            case "hug" -> hug;
            case "slap" -> slap;
            case "clap" -> clap;
            case "danse" -> danse;
            default -> true;
        };
    }

    public void set(String type, boolean value) {
        switch (type) {
            case "enabled" -> enabled = value;
            case "dap" -> dap = value;
            case "grab" -> grab = value;
            case "push" -> push = value;
            case "kick" -> kick = value;
            case "hug" -> hug = value;
            case "slap" -> slap = value;
            case "clap" -> clap = value;
            case "danse" -> danse = value;
        }
    }
}
