package com.dap.dapurhomies.permission;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerPreferencesTest {
    private static final String[] MODULE_TYPES = {
            "enabled", "dap", "grab", "push", "kick", "hug", "slap", "clap", "danse"
    };

    @Test
    void defaultsAllowEveryInteractionAndUnknownType() {
        PlayerPreferences preferences = new PlayerPreferences();

        for (String type : MODULE_TYPES) {
            assertTrue(preferences.allows(type), type);
        }
        assertTrue(preferences.allows("unknown"));
        assertEquals("global", preferences.lang);
    }

    @Test
    void masterSwitchOverridesEveryType() {
        PlayerPreferences preferences = new PlayerPreferences();
        preferences.set("enabled", false);

        for (String type : MODULE_TYPES) {
            assertFalse(preferences.allows(type), type);
        }
        assertFalse(preferences.allows("unknown"));

        preferences.set("enabled", true);
        assertTrue(preferences.allows("enabled"));
    }

    @Test
    void setAndAllowsUseTheSameModuleMapping() {
        for (String type : MODULE_TYPES) {
            PlayerPreferences preferences = new PlayerPreferences();
            preferences.set(type, false);
            assertFalse(preferences.allows(type), type);

            preferences.set(type, true);
            assertTrue(preferences.allows(type), type);
        }
    }

    @Test
    void unknownTypeDoesNotChangeStoredPreferences() {
        PlayerPreferences preferences = new PlayerPreferences();

        preferences.set("unknown", false);

        assertTrue(preferences.allows("unknown"));
        for (String type : MODULE_TYPES) {
            assertTrue(preferences.allows(type), type);
        }
    }
}
