package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.SafariBiome;
import net.minecraft.world.phys.Vec3;
import java.util.Locale;
import java.util.HashMap;

/** Event-time, single-pass template expansion. Replacement text is never interpreted as a template. */
public final class AlertText {
    public static final String BIOME_HELP = "<BIOME>, <BIOMELETTER> (f/c/i/h), <BIOME_UPPER>";
    public static final String CRITTER_HELP = "<CRITTER>, <RARITY>";
    public static final String SPAWN_HELP = CRITTER_HELP + ", <X>, <Y>, <Z> (critter position; ? if unknown)";
    private AlertText() { }

    public static String format(String template, String... replacements) {
        if (template == null) return "";
        int start = template.indexOf('<');
        if (start < 0) return template;
        var values = new HashMap<String, String>();
        for (int i = 0; i + 1 < replacements.length; i += 2)
            if (replacements[i] != null && replacements[i + 1] != null)
                values.putIfAbsent(replacements[i], replacements[i + 1]);
        String critterName = values.get("<CRITTER>");
        Critter critter = critterName == null ? null : Critters.byName(critterName);
        if (critter != null) {
            values.putIfAbsent("<RARITY>", critter.rarity().name());
        }
        var resolved = new HashMap<String, String>();
        StringBuilder result = new StringBuilder(template.length() + 16);
        int cursor = 0;
        while (start >= 0) {
            int end = template.indexOf('>', start + 1);
            if (end < 0) break;
            start = template.lastIndexOf('<', end);
            result.append(template, cursor, start);
            String tag = template.substring(start, end + 1);
            String value;
            if (resolved.containsKey(tag)) value = resolved.get(tag);
            else {
                value = resolve(tag, values);
                resolved.put(tag, value);
            }
            result.append(value == null ? tag : value);
            cursor = end + 1;
            start = template.indexOf('<', cursor);
        }
        return result.append(template, cursor, template.length()).toString();
    }

    private static String resolve(String tag, java.util.Map<String, String> values) {
        if (values.containsKey(tag)) return values.get(tag);
        if (tag.equals("<BIOMELETTER>") || tag.equals("<BIOMELETTER_UPPER>") || tag.equals("<BIOME_UPPER>")) {
            String name = values.get("<BIOME>");
            if (name == null) return null;
            SafariBiome biome = SafariBiome.fromDisplayName(name);
            if (biome == null) return "?";
            return switch (tag) {
                case "<BIOMELETTER>" -> biome.name().substring(0, 1).toLowerCase(Locale.ROOT);
                case "<BIOMELETTER_UPPER>" -> biome.name().substring(0, 1);
                default -> biome.displayName().toUpperCase(Locale.ROOT);
            };
        }
        if (tag.equals("<CRITTER_UPPER>")) {
            String name = values.get("<CRITTER>");
            return name == null ? "?" : name.toUpperCase(Locale.ROOT);
        }
        return null; // Unsupported tags stay literal; no player/world fallback.
    }

    /** Coordinates belong to this spawn event, never the sender or a guessed nearby critter. */
    public static String spawn(String template, String critter, Vec3 position) {
        if (template == null || (!template.contains("<X>") && !template.contains("<Y>") && !template.contains("<Z>")))
            return format(template, "<CRITTER>", critter);
        return format(template, "<CRITTER>", critter,
            "<X>", coordinate(position == null ? Double.NaN : position.x),
            "<Y>", coordinate(position == null ? Double.NaN : position.y),
            "<Z>", coordinate(position == null ? Double.NaN : position.z));
    }

    static String coordinate(double value) {
        if (!Double.isFinite(value)) return "?";
        // Avoid negative zero after rounding and always use a decimal point.
        return String.format(Locale.ROOT, "%.1f", Math.abs(value) < 0.05 ? 0 : value);
    }
}
