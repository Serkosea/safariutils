package dev.serko.safariutils.client;

import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.state.HotspotAllocation;
import java.util.*;

public final class CoordinationRegression {
    private static int checks;
    public static int run() throws Exception {
        checks = 0;
        var reports = new LinkedHashMap<String, SafariBiome>();
        reports.put("first", SafariBiome.ICY);
        reports.put("second", SafariBiome.ICY);
        reports.put("third", SafariBiome.FOREST);
        reports.put("fourth", SafariBiome.ICY);
        var allocated = HotspotAllocation.allocate(reports, Map.of("second", "b", "fourth", "a"));
        check(allocated.get("first") == SafariBiome.ICY, "arrival order keeps actual hotspot");
        check(allocated.get("third") == SafariBiome.FOREST, "unique hotspot retained");
        check(allocated.get("fourth") == SafariBiome.CAVERN && allocated.get("second") == SafariBiome.HAUNTED,
            "UUID string hash orders duplicate holders into enum-order free biomes");
        for (SafariBiome biome : SafariBiome.values()) {
            var solo = new HotspotAllocation(); solo.begin("Self", biome, 100);
            check(solo.assignment(Set.of("Self"), Map.of(), 1, 100) == biome && solo.finished(), "solo keeps hotspot");
        }
        var partial = new HotspotAllocation();
        partial.report("friend", SafariBiome.ICY, 50);
        partial.report("outsider", SafariBiome.HAUNTED, 60);
        partial.begin("Self", SafariBiome.ICY, 100);
        check(partial.assignment(Set.of("self", "friend"), Map.of(), 2, 3599) == null, "wait for all reports or 3.5 seconds");
        check(partial.assignment(Set.of("self", "friend"), Map.of(), 2, 3600) == SafariBiome.FOREST, "partial assignment ignores nonmember");
        check(!partial.finished(), "partial assignment stays provisional until timeout");
        partial.report("self", SafariBiome.ICY, 3700);
        check(partial.assignment(Set.of("self", "friend"), Map.of(), 2, 3700) == SafariBiome.FOREST && partial.finished(), "all reports finalize");
        partial.report("friend", SafariBiome.CAVERN, 3800);
        check(partial.assignment(Set.of("self", "friend"), Map.of(), 2, 4000) == SafariBiome.FOREST, "duplicate/late report cannot churn final assignment");
        var timeout = new HotspotAllocation(); timeout.begin("self", SafariBiome.CAVERN, 100);
        check(timeout.assignment(Set.of("self", "missing"), Map.of(), 2, 10100) == SafariBiome.CAVERN && timeout.finished(), "missing reporter times out safely");
        var settings = new SafariConfig.GameplayConfig();
        for (var mode : RunMode.values()) check(TicketSelection.preference(settings, mode) == 0, "ticket default manual");
        settings.fullClearTicket = 4; settings.uniqueTicket = 2; settings.sparklingTicket = 1;
        check(TicketSelection.preference(settings, RunMode.FULL_CLEAR) == 4, "full clear selected ticket");
        check(TicketSelection.preference(settings, RunMode.UNIQUE) == 2, "unique selected ticket");
        check(TicketSelection.preference(settings, RunMode.SPARKLING) == 1, "sparkling selected ticket");
        settings.fullClearTicket = 99;
        check(TicketSelection.preference(settings, RunMode.FULL_CLEAR) == 0, "invalid ticket manual");
        check(TicketSelection.tier("§6First-Class Safari Ticket") == 4, "formatted tier");
        check(TicketSelection.tier("Premium Ticket") == 3 && TicketSelection.tier("Basic Ticket") == 1, "menu tiers");
        check(TicketSelection.tier("Not a Basic Ticket") == 0, "unrecognized item is not a ticket");
        check(Boolean.TRUE.equals(TicketSelection.available(List.of("You own: 1,024", "Click to enter!"))), "owned tickets");
        check(Boolean.FALSE.equals(TicketSelection.available(List.of("You own: 0", "Click to enter!"))), "zero overrides click prompt");
        check(Boolean.FALSE.equals(TicketSelection.available(List.of("You don't have this ticket!", "Click to use"))), "negative lore wins");
        check(Boolean.TRUE.equals(TicketSelection.available(List.of("Click to enter!"))), "positive prompt");
        check(TicketSelection.available(List.of("A fancy ticket")) == null, "unknown availability stays manual");
        check(TicketSelection.available(List.of("You own: 999999999999999999999999")) == null, "overflow fails closed");
        var clickCache = ClientCompat.class.getDeclaredField("CONTAINER_CLICK"); clickCache.setAccessible(true);
        var cached = ((ClassValue<?>)clickCache.get(null)).get(net.minecraft.client.multiplayer.MultiPlayerGameMode.class);
        var clickMethod = cached.getClass().getDeclaredMethod("method"); clickMethod.setAccessible(true);
        check(clickMethod.invoke(cached) != null, "this Minecraft profile exposes supported container input");
        var configField = ConfigManager.class.getDeclaredField("config"); configField.setAccessible(true);
        Object original = configField.get(null);
        try {
            var config = new SafariConfig(); configField.set(null, config);
            config.party.hotspotChatText = "my hotspot <BIOME>";
            config.party.biomeDoneChatText = "my clear <BIOME>";
            check(ChatTemplateFormats.HOTSPOT.format(config.party, "<BIOME>", "Icy").equals("my hotspot Icy"), "custom text by default");
            config.gameplay.mutualsCompatibility = true;
            check(ChatTemplateFormats.HOTSPOT.format(config.party, "<BIOME>", "Icy").equals("[SM] Hotspot: Icy"), "exact hotspot format");
            check(ChatTemplateFormats.BIOME_UNIQUES.format(config.party, "<BIOME>", "Forest").equals("fd"), "exact done format");
            check(config.party.hotspot() == SafariConfig.Broadcast.PARTY && config.party.biomeDone() == SafariConfig.Broadcast.PARTY
                && config.party.startingItems() == SafariConfig.Broadcast.PARTY, "compatible transport");
            check(ChatTemplateFormats.lockedValue(config.party, "hotspotChatText") != null && ChatTemplateFormats.lockedValue(config.party, "startingItemsMask") != null, "managed controls locked");
            check(ChatTemplateFormats.lockedValue(config.alerts, "hotspotText") == null, "banner controls remain editable");
            int[] items = new int[9]; items[4] = 1; items[6] = 2; items[0] = 1;
            check(ChatTemplateFormats.STARTING_ITEMS.format(config.party, "<ITEMS>", StartingItems.mutualsFormat(items))
                .equals("[SM] Starting items: 1x Shining Coin; 2x Yogi Berry"), "canonical item names and separator");
            config.gameplay.mutualsCompatibility = false;
            check(ChatTemplateFormats.HOTSPOT.format(config.party, "<BIOME>", "Icy").equals("my hotspot Icy"), "disabling restores saved custom text");
        } finally { configField.set(null, original); }
        return checks;
    }
    private static void check(boolean valid, String why) { checks++; if (!valid) throw new AssertionError(why); }
}
