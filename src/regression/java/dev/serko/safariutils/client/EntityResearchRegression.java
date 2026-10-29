package dev.serko.safariutils.client;

import java.util.UUID;

public final class EntityResearchRegression {
    public static int run() {
        var index = new EntityResearchIndex(2);
        UUID first = UUID.randomUUID(), replacement = UUID.randomUUID();
        var unnamed = new EntityResearchIndex.Identity(first, "minecraft:item_display", "", "", 0);
        var named = new EntityResearchIndex.Identity(first, "minecraft:item_display", "Floor", "minecraft:string", 1);
        var reused = new EntityResearchIndex.Identity(replacement, "minecraft:armadillo", "Scrappy", "", 0);
        require(index.observe(42, unnamed) == EntityResearchIndex.Change.NEW, "first sighting");
        for (int i = 0; i < 10000; i++) require(index.observe(42, unnamed) == EntityResearchIndex.Change.SAME, "stable suppression");
        require(index.observe(42, named) == EntityResearchIndex.Change.METADATA, "late metadata");
        require(index.observe(42, reused) == EntityResearchIndex.Change.REUSED, "network ID reuse");
        index.unload(42, first);
        require(index.observe(42, reused) == EntityResearchIndex.Change.SAME, "old unload cannot erase reused ID");
        require(index.observe(43, named) == EntityResearchIndex.Change.NEW, "second identity");
        require(index.observe(44, unnamed) == EntityResearchIndex.Change.FULL && index.size() == 2, "hard bound");
        index.unload(43, first);
        require(index.observe(44, unnamed) == EntityResearchIndex.Change.NEW, "capacity reclaimed");
        index.clear();
        require(index.size() == 0 && index.observe(42, reused) == EntityResearchIndex.Change.NEW, "world reset");
        var epoch = new dev.serko.safariutils.state.SafariEpoch(11, 22, 33, "lobby", dev.serko.safariutils.state.SafariEpoch.Phase.ACTIVE);
        var deferred = StructuredDiagnostics.decisionRecord(epoch, 1234, 5678, "test", "observe", "detail");
        var json = com.google.gson.JsonParser.parseString(deferred.get()).getAsJsonObject();
        require(json.get("t").getAsLong() == 1234 && json.get("n").getAsLong() == 5678, "deferred observation clocks");
        require(json.get("c").getAsLong() == 11 && json.get("v").getAsLong() == 22 && json.get("r").getAsLong() == 33, "deferred epoch");
        return 10010;
    }
    private static void require(boolean condition, String name) {
        if (!condition) throw new AssertionError("Entity research: " + name);
    }
}
