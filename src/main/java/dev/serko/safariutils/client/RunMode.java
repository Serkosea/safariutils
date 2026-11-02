package dev.serko.safariutils.client;

import java.util.List;

/** One effective run goal for settings, commands, marker relevance, and HUDs.
 * Existing saved toggles are the persistence adapter, not a second cached mode. */
public enum RunMode {
    FULL_CLEAR("Full Clear", "Show normal run targets using your display settings", List.of("fc", "fullclear")),
    UNIQUE("Unique", "Keep needed Sparklings and uncaught run uniques visible", List.of("unique", "uniques")),
    SPARKLING("Sparkling", "Focus on needed Sparklings and your visibility overrides", List.of("sparkling"));

    private final String title, description, hudTitle;
    private final List<String> commands;
    RunMode(String title, String description, List<String> commands) {
        this.title = title;
        this.description = description;
        this.hudTitle = title + " Mode";
        this.commands = commands;
    }
    public String title() { return title; }
    public String description() { return description; }
    public String hudTitle() { return hudTitle; }
    public List<String> commands() { return commands; }
    public boolean focused() { return this != FULL_CLEAR; }
    public boolean seeksRunUniques() { return this == UNIQUE; }

    public static RunMode current() { return from(ConfigManager.get().sparkling); }
    static RunMode from(SafariConfig.SparklingConfig settings) {
        return !settings.sparklingMode ? FULL_CLEAR : settings.sparklingIgnoreUniques ? SPARKLING : UNIQUE;
    }

    /** Changes only goal preferences; no capture, collection, PB, or party state is reset. */
    boolean applyTo(SafariConfig.SparklingConfig settings) {
        if (from(settings) == this) return false;
        settings.sparklingMode = focused();
        // Disabling Unique Mode retains its remembered Sparkling sub-option for the settings toggle.
        if (focused()) settings.sparklingIgnoreUniques = this == SPARKLING;
        return true;
    }

    public boolean select() {
        RunMode previous = current();
        if (!applyTo(ConfigManager.get().sparkling)) return false;
        ConfigManager.save();
        MarkerRegistry.invalidate();
        StructuredDiagnostics.decision("run-mode", "selected", previous.name() + " -> " + name());
        return true;
    }
}
