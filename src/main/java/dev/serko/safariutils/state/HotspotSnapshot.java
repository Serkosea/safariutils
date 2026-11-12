package dev.serko.safariutils.state;

import com.google.gson.*;
import dev.serko.safariutils.data.SafariBiome;
import java.util.*;

/** Validated bounded wire snapshot; no game/world or transport dependencies. */
public record HotspotSnapshot(String run, long revision, Map<String, SafariBiome> assignments,
        Map<String, Integer> fairness, String leader, List<Notice> notifications, boolean conflict, Map<String, SafariBiome> reports) {
    public record Notice(String id, String player, SafariBiome biome) { }
    public static HotspotSnapshot parse(String text, Collection<String> roster) {
        if (text == null || text.length() > 4096) throw new IllegalArgumentException("snapshot size");
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        if (root.get("version").getAsInt() != 1) throw new IllegalArgumentException("version");
        String run = root.get("run").getAsString();
        if (!run.matches("[a-zA-Z0-9-]{1,64}")) throw new IllegalArgumentException("run");
        long revision = root.get("revision").getAsLong();
        if (revision < 0) throw new IllegalArgumentException("revision");
        JsonObject assigned = root.getAsJsonObject("assignments"), history = root.getAsJsonObject("fairness");
        JsonArray notices = root.getAsJsonArray("notifications");
        if (assigned.size() > 4 || history.size() > 4 || notices.size() > 4) throw new IllegalArgumentException("count");
        Map<String, SafariBiome> assignments = new LinkedHashMap<>();
        for (var entry : assigned.entrySet()) {
            requireMember(entry.getKey(), roster);
            SafariBiome biome = SafariBiome.valueOf(entry.getValue().getAsString());
            if (assignments.containsValue(biome)) throw new IllegalArgumentException("duplicate biome");
            assignments.put(entry.getKey(), biome);
        }
        Map<String, SafariBiome> reports = new LinkedHashMap<>();
        if (root.has("reports")) {
            JsonObject accepted = root.getAsJsonObject("reports");
            if (accepted.size() > 4) throw new IllegalArgumentException("reports");
            for (var entry : accepted.entrySet()) {
                requireMember(entry.getKey(), roster);
                reports.put(entry.getKey(), SafariBiome.valueOf(entry.getValue().getAsString()));
            }
        }
        Map<String, Integer> fairness = new LinkedHashMap<>();
        for (var entry : history.entrySet()) {
            requireMember(entry.getKey(), roster);
            int count = entry.getValue().getAsInt();
            if (count < 0 || count > 1000000) throw new IllegalArgumentException("wins");
            fairness.put(entry.getKey(), count);
        }
        String leader = root.get("leader").isJsonNull() ? "" : root.get("leader").getAsString();
        if (!leader.isEmpty()) requireMember(leader, roster);
        List<Notice> notifications = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonElement element : notices) {
            JsonObject notice = element.getAsJsonObject();
            String player = notice.get("player").getAsString(), id = notice.get("id").getAsString();
            SafariBiome biome = SafariBiome.valueOf(notice.get("biome").getAsString());
            if (assignments.get(player) != biome || !id.equals(run + ":" + player) || !ids.add(id))
                throw new IllegalArgumentException("notification");
            notifications.add(new Notice(id, player, biome));
        }
        return new HotspotSnapshot(run, revision, Map.copyOf(assignments), Map.copyOf(fairness),
            leader, List.copyOf(notifications), root.get("conflict").getAsBoolean(), Map.copyOf(reports));
    }
    /** Partial snapshots may omit a lock, but cannot move it or give its biome to another member. */
    public boolean conflictsWith(Map<String, SafariBiome> locked) {
        for (var fixed : locked.entrySet()) for (var incoming : assignments.entrySet()) {
            if (fixed.getKey().equals(incoming.getKey()) ? fixed.getValue()!=incoming.getValue()
                    : fixed.getValue()==incoming.getValue()) return true;
        }
        return false;
    }
    private static void requireMember(String id, Collection<String> roster) {
        if (!id.matches("[a-f0-9]{32}") || !roster.contains(id)) throw new IllegalArgumentException("member");
    }
}
