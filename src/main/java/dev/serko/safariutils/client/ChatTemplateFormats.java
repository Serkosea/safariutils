package dev.serko.safariutils.client;

import java.util.function.Function;

/** Verified cross-mod formats and reversible effective overrides. Saved custom fields are never rewritten. */
enum ChatTemplateFormats {
    HOTSPOT("hotspotChatText", "[SU] Hotspot: <BIOME>", config -> config.hotspotChatText),
    BIOME_UNIQUES("biomeDoneChatText", "<BIOMELETTER>d", config -> config.biomeDoneChatText),
    STARTING_ITEMS("startingItemsChatText", "<ITEMS>", config -> config.startingItemsChatText);

    private final String setting, mutuals;
    private final Function<SafariConfig.PartyConfig, String> configured;
    ChatTemplateFormats(String setting, String mutuals, Function<SafariConfig.PartyConfig, String> configured) {
        this.setting = setting;
        this.mutuals = mutuals;
        this.configured = configured;
    }
    String setting() { return setting; }
    String mutualsTemplate() { return mutuals; }
    String format(SafariConfig.PartyConfig config, String... context) {
        return AlertText.format(locked() ? mutuals : configured.apply(config), context);
    }

    boolean locked() {
        return this == BIOME_UNIQUES && ConfigManager.get().gameplay.biomeReports
            || this == HOTSPOT && ConfigManager.get().gameplay.assignHotspots;
    }
    static String lockedValue(Object owner, String field) {
        if (!(owner instanceof SafariConfig.PartyConfig)) return null;
        for (var format : values()) {
            if (!format.locked()) continue;
            if (field.equals(format.setting)) return format.mutuals + (format == HOTSPOT ? " (Assign Hotspots)" : " (Biome Reports)");
            String channel = switch (format) {
                case HOTSPOT -> "hotspotBroadcast";
                case BIOME_UNIQUES -> "biomeDoneBroadcast";
                case STARTING_ITEMS -> "startingItemsBroadcast";
            };
            if (field.equals(channel)) return format == HOTSPOT ? "Party (Assign Hotspots)" : "Party (Biome Reports)";
        }
        return null;
    }
}
