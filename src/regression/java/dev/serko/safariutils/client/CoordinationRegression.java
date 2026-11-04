package dev.serko.safariutils.client;

import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.state.HotspotAllocation;
import java.util.*;

public final class CoordinationRegression {
    private static int checks;
    public static int run() throws Exception {
        var soloProbe = new BiomeCoordination.SoloDiagnostic();
        check(!soloProbe.poll(SafariBiome.FOREST, RunMode.SPARKLING, true, 1000), "solo diagnostic waits for settling");
        check(!soloProbe.poll(SafariBiome.FOREST, RunMode.SPARKLING, true, 2499), "solo diagnostic cannot report early");
        check(!soloProbe.poll(SafariBiome.FOREST, RunMode.FULL_CLEAR, false, 2500), "incomplete mode cancels pending dry run");
        check(!soloProbe.poll(SafariBiome.FOREST, RunMode.SPARKLING, true, 3000), "mode switch restarts settling");
        check(soloProbe.poll(SafariBiome.FOREST, RunMode.SPARKLING, true, 4500), "ready solo diagnostic reports once");
        check(!soloProbe.poll(SafariBiome.FOREST, RunMode.SPARKLING, true, 9000), "no per-tick diagnostic spam");
        check(!soloProbe.poll(SafariBiome.FOREST, RunMode.UNIQUE, true, 10000), "completed second mode needs its own settle");
        check(soloProbe.poll(SafariBiome.FOREST, RunMode.UNIQUE, true, 11500), "same biome can test another mode");
        soloProbe.clear();
        check(!soloProbe.poll(SafariBiome.FOREST, RunMode.UNIQUE, true, 20000), "visit/logging reset clears latch");

        checks = dev.serko.safariutils.backend.WebSocketOutboxRegression.run();
        var rosterField = PartyHotspotSync.class.getDeclaredField("roster"); rosterField.setAccessible(true);
        var waitingField = PartyHotspotSync.class.getDeclaredField("waitingSince"); waitingField.setAccessible(true);
        PartyHotspotSync.reset();
        try {
            check(PartyHotspotSync.controlsAssignments(), "unresolved membership must wait");
            check(waitingField.getLong(null) == -1, "unknown roster started fallback timer");
            String a = "a".repeat(32), b = "b".repeat(32), outsider = "c".repeat(32);
            rosterField.set(null, List.of(a));
            check(!PartyHotspotSync.controlsAssignments(), "solo allocation must remain local");
            check(waitingField.getLong(null) == -1, "solo latched network fallback");
            rosterField.set(null, List.of(a,b));
            check(PartyHotspotSync.controlsAssignments(), "resolved party lost backend grace period");
            var self = new dev.serko.safariutils.state.PartySnapshot.Member("Self",a);
            var foreign = new dev.serko.safariutils.state.PartySnapshot.Member("Other",outsider);
            var solo = new dev.serko.safariutils.state.PartySnapshot(1,1,
                dev.serko.safariutils.state.PartySnapshot.Status.SOLO,true,1,List.of(self),1,List.of(self,foreign));
            var before = PartyHotspotSync.hello(solo,false,true);
            var after = PartyHotspotSync.hello(solo,true,true);
            check(!before.get("ticketed").getAsBoolean() && after.get("ticketed").getAsBoolean(),
                "disband blocked ticket activation hello");
            check(!after.get("leader").getAsBoolean(), "solo claimed old party leadership");
            check(after.getAsJsonArray("present").size()==1, "outsider leaked into visit attendance");
            check(after.get("display").getAsBoolean(), "disband disabled connected assignment display");
        } finally { PartyHotspotSync.reset(); }
        String member = "a".repeat(32);
        String snapshot = "{\"version\":1,\"run\":\"test\",\"revision\":3,\"assignments\":{\"" + member
            + "\":\"FOREST\"},\"fairness\":{\"" + member + "\":2},\"leader\":null,\"notifications\":[],\"conflict\":false}";
        var parsed = dev.serko.safariutils.state.HotspotSnapshot.parse(snapshot, List.of(member));
        check(parsed.assignments().get(member) == SafariBiome.FOREST && parsed.fairness().get(member) == 2, "bounded backend snapshot");
        for (String invalid : List.of(snapshot.replace("FOREST", "BOGUS"), snapshot.replace(":2}", ":-1}"))) {
            boolean rejected = false;
            try { dev.serko.safariutils.state.HotspotSnapshot.parse(invalid, List.of(member)); } catch (RuntimeException expected) { rejected = true; }
            check(rejected, "malformed authority rejected");
        }
        check(!parsed.conflictsWith(Map.of(member, SafariBiome.FOREST)), "same locked assignment survives recovery");
        check(parsed.conflictsWith(Map.of("b".repeat(32), SafariBiome.FOREST)), "partial snapshot cannot give a locked biome to another player");
        check(parsed.conflictsWith(Map.of(member, SafariBiome.ICY)), "finalized fallback cannot move");
        {
        var configField=ConfigManager.class.getDeclaredField("config"); configField.setAccessible(true);
        Object previousConfig=configField.get(null); var fixtureConfig=new SafariConfig(); configField.set(null,fixtureConfig);
        var developerSettings=fixtureConfig.advanced;
        boolean oldHud=developerSettings.partySyncDebugHud;
        try {
            developerSettings.partySyncDebugHud=false;
            check(!HudBox.PARTY_SYNC_DEBUG.enabled(), "debug HUD disabled by default");
            developerSettings.partySyncDebugHud=true;
            check(HudBox.PARTY_SYNC_DEBUG.enabled()==dev.serko.safariutils.BuildVersion.has(dev.serko.safariutils.BuildVersion.Capability.DEVELOPER_TOOLS),
                "public editions cannot enable developer HUD via imported config");
        } finally { developerSettings.partySyncDebugHud=oldHud; configField.set(null,previousConfig); }
        }
        var activityFeed = new dev.serko.safariutils.state.SyncActivityFeed();
        for(int i=0;i<1000;i++) activityFeed.add(i,"Update "+i,0);
        check(activityFeed.entries().size()==8 && activityFeed.entries().getFirst().text().equals("Update 992"), "debug feed bounded and chronological");
        activityFeed.add(1001,"Update 999",0);
        check(activityFeed.entries().size()==8 && activityFeed.entries().getLast().time()==999, "duplicate heartbeat cannot flood feed");
        activityFeed.add(1002,"Line\nBreak",0);
        check(!activityFeed.entries().getLast().text().contains("\n"), "feed entries cannot inject extra rows");
        activityFeed.clear(); check(activityFeed.entries().isEmpty(), "visit reset clears debug history");
        boolean outsider = false;
        try { dev.serko.safariutils.state.HotspotSnapshot.parse(snapshot, List.of("b".repeat(32))); } catch (RuntimeException expected) { outsider = true; }
        check(outsider, "snapshot cannot assign an outside player");
        for (RunMode mode : RunMode.values()) {
            var banner = new HotspotBannerState();
            check(banner.poll(null, null, true, 1000) == null, "no invented hotspot in " + mode);
            check(banner.poll(SafariBiome.ICY, null, true, 1000) == null, "wait for assignment in " + mode);
            var assignedBanner = banner.poll(SafariBiome.ICY, SafariBiome.HAUNTED, true, 3000);
            check(assignedBanner.assigned() && assignedBanner.biome() == SafariBiome.HAUNTED,
                "assigned destination wins in " + mode);
            check(banner.poll(SafariBiome.ICY, SafariBiome.HAUNTED, true, 9000) == null, "one assigned banner");
            banner.reset();
            check(!banner.poll(SafariBiome.ICY, null, false, 1000).assigned(), "actual hotspot without assignment");
            banner.reset();
            banner.poll(SafariBiome.ICY, null, true, 1000);
            check(banner.poll(SafariBiome.ICY, null, true, 7499) == null, "fallback does not race normal assignment");
            check(!banner.poll(SafariBiome.ICY, null, true, 7500).assigned(), "bounded actual-hotspot fallback");
            check(banner.poll(SafariBiome.ICY, null, true, 8000) == null, "fallback not repeated");
            check(banner.poll(SafariBiome.ICY, SafariBiome.FOREST, true, 9000).assigned(), "late assignment can correct fallback");
        }
        var alertSettings = new SafariConfig.AlertConfig();
        alertSettings.hotspotText = "Mine: <BIOME>";
        alertSettings.hotspotAssignmentText = "Go: <BIOME>";
        check(EncounterAlerts.hotspotBannerText(alertSettings, SafariBiome.ICY, false).equals("Mine: Icy"), "preserved actual-hotspot text");
        check(EncounterAlerts.hotspotBannerText(alertSettings, SafariBiome.FOREST, true).equals("Go: Forest"), "assignment-specific text");
        var reports = new LinkedHashMap<String, SafariBiome>();
        reports.put("first", SafariBiome.ICY);
        reports.put("second", SafariBiome.ICY);
        reports.put("third", SafariBiome.FOREST);
        reports.put("fourth", SafariBiome.ICY);
        var ids = Map.of("first", "c", "second", "b", "fourth", "a");
        var allocated = HotspotAllocation.allocate(reports, ids);
        check(allocated.get("fourth") == SafariBiome.ICY, "UUID hash chooses initial winner, not chat arrival");
        check(allocated.get("third") == SafariBiome.FOREST, "uncontested hotspot retained");
        check(allocated.get("second") == SafariBiome.CAVERN && allocated.get("first") == SafariBiome.HAUNTED,
            "losers use hash order and free biome enum order");
        var reversed = new LinkedHashMap<String, SafariBiome>();
        new ArrayList<>(reports.keySet()).reversed().forEach(n -> reversed.put(n, reports.get(n)));
        check(allocated.equals(HotspotAllocation.allocate(reversed, ids)), "arrival order does not change assignment");
        check(HotspotAllocation.allocate(reports, ids, Map.of("fourth", 1)).get("second") == SafariBiome.ICY,
            "fewer previous hotspot wins have priority");
        for (SafariBiome biome : SafariBiome.values()) {
            var solo = new HotspotAllocation(); solo.begin("Self", biome, 100);
            check(solo.assignment(Set.of("Self"), Map.of(), 1, 100) == biome && solo.finished(), "solo keeps hotspot");
        }
        var wins = new HashMap<String, Integer>();
        var partial = new HotspotAllocation(wins);
        partial.report("friend", SafariBiome.ICY, 50);
        partial.begin("Self", SafariBiome.ICY, 100);
        check(partial.assignment(Set.of("self", "friend"), Map.of(), 2, 5049) == null, "incomplete roster waits five seconds from first report");
        SafariBiome result = partial.assignment(Set.of("self", "friend"), Map.of(), 2, 5050);
        check(result != null && partial.finished(), "partial assignment finalizes once after timeout");
        var winSnapshot = Map.copyOf(wins);
        partial.report("self", SafariBiome.ICY, 5100);
        check(partial.assignment(Set.of("self", "friend"), Map.of(), 2, 5200) == result && wins.equals(winSnapshot),
            "late reports and repeated ticks neither churn assignments nor increment wins");
        var complete = new HotspotAllocation(); complete.begin("self", SafariBiome.CAVERN, 0);
        complete.report("self", SafariBiome.CAVERN, 100); complete.report("friend", SafariBiome.CAVERN, 150);
        check(complete.assignment(Set.of("self", "friend"), Map.of(), 2, 2099) == null, "complete roster still waits two seconds");
        check(complete.assignment(Set.of("self", "friend"), Map.of(), 2, 2100) != null, "complete roster resolves after two seconds");
        var silent = new HotspotAllocation(); silent.begin("self", SafariBiome.ICY, 0);
        check(silent.assignment(Set.of("self", "friend"), Map.of(), 2, 20000) == null, "no shared report means no speculative assignment");
        check(!BiomeCoordination.speciesComplete(RunMode.FULL_CLEAR, true, false, true, true, "Foxtrot"), "unique catch cannot send full-clear report");
        check(!BiomeCoordination.speciesComplete(RunMode.UNIQUE, true, false, false, true, "Honeybug"), "unique waits for required nest checks");
        check(BiomeCoordination.speciesComplete(RunMode.UNIQUE, true, false, false, false, "Honeybug"), "already shared Honeybug needs only unique");
        check(!BiomeCoordination.speciesComplete(RunMode.UNIQUE, true, false, false, false, "Macaw"), "birds still need feed exhaustion");
        check(BiomeCoordination.speciesComplete(RunMode.SPARKLING, false, false, true, true, "Foxtrot"), "sparkling checks need no ordinary catch");
        check(!BiomeCoordination.speciesComplete(RunMode.SPARKLING, true, false, false, true, "Foxtrot"), "one ordinary catch is not a sparkling check");
        check(!BiomeCoordination.reportAllowed(SafariBiome.ICY, RunMode.UNIQUE, true, true, true), "focused Wumpa catch suppresses id");
        check(BiomeCoordination.reportAllowed(SafariBiome.ICY, RunMode.FULL_CLEAR, true, true, true), "full-clear may send id");
        check(BiomeCoordination.reportAllowed(SafariBiome.ICY, RunMode.SPARKLING, false, false, true), "id allowed for needed Icy checks without needed Wumpa");
        check(!BiomeCoordination.reportAllowed(SafariBiome.ICY, RunMode.SPARKLING, false, true, true), "needed Wumpa blocks early id");
        check(!BiomeCoordination.inZone(dev.serko.safariutils.data.Critters.byName("Foxtrot"), 0, -60, true), "another biome is not survey proof");
        for (String prefix : List.of("[SU] ", "[SM] ", "[SafariUtils] ", "[CM] ", "[CritterMod] ", "")) {
            var report = HotspotWatch.parsePartyReport("Party > [MVP++] Example: " + prefix + "Hotspot: Forest");
            check(report != null && report.name().equals("Example") && report.biome() == SafariBiome.FOREST,
                "supported hotspot wire prefix " + prefix);
        }
        check(HotspotWatch.parsePartyReport("Party > Friend: My Hunting Hotspot: Ice Biome!").biome() == SafariBiome.ICY,
            "Mutuals alternate hotspot spelling");
        check(HotspotWatch.parsePartyReport("Guild > Friend: [SU] Hotspot: Forest") == null,
            "guild message cannot allocate a party biome");
        check(HotspotWatch.parsePartyReport("Party > Friend: someone said [SU] Hotspot: Forest") == null,
            "quoted hotspot cannot allocate a party biome");
        check(BiomeCoordination.captureTarget(dev.serko.safariutils.data.Critters.byName("Honeybug"), 3, 6) == 6,
            "six confirmed nests require six Honeybugs despite only three observed catches");
        check(BiomeCoordination.captureTarget(dev.serko.safariutils.data.Critters.byName("Gimmiegold"), 4, 6) == 6,
            "confirmed coin supply raises full-clear target");
        check(BiomeCoordination.captureTarget(dev.serko.safariutils.data.Critters.byName("Foxtrot"), 8, 0) == 8,
            "observed population exceeds minimum target");
        check(BiomeCoordination.captureTarget(dev.serko.safariutils.data.Critters.byName("Macaw"), 4, 4) == 1,
            "full clear needs one Macaw even when four spawn");
        check(HotspotWatch.rosterIdentity(Set.of("renamed"), Map.of("renamed", "AA-BB"))
            .equals(HotspotWatch.rosterIdentity(Set.of("oldname"), Map.of("oldname", "aabb"))), "UUID roster survives name changes");
        check(!HotspotWatch.rosterIdentity(Set.of("same"), Map.of("same", "aabb"))
            .equals(HotspotWatch.rosterIdentity(Set.of("same"), Map.of("same", "ccdd"))), "UUID replacement resets fairness");
        check(BirdfeederWatch.requiredForestFeed(2) == 11 && BirdfeederWatch.requiredForestFeed(0) == 9,
            "Forest floor food plus own starting food");
        var oldSettings = com.google.gson.JsonParser.parseString(
            "{\"settingsSchemaVersion\":9,\"gameplay\":{\"mutualsCompatibility\":true,\"assignHotspots\":false}}")
            .getAsJsonObject();
        var migrated = ConfigManager.validateImported(oldSettings);
        check(migrated.gameplay.biomeReports && migrated.gameplay.assignHotspots,
            "old combined setting preserves both enabled features");
        var disabledOld = com.google.gson.JsonParser.parseString(
            "{\"settingsSchemaVersion\":9,\"gameplay\":{\"mutualsCompatibility\":false,\"assignHotspots\":true}}")
            .getAsJsonObject();
        var migratedDisabled = ConfigManager.validateImported(disabledOld);
        check(!migratedDisabled.gameplay.biomeReports && migratedDisabled.gameplay.assignHotspots,
            "migration preserves independent hotspot preference");
        var survey = new BiomeCoordination.Survey();
        var fish = dev.serko.safariutils.data.Critters.byName("Cavernfish");
        var gemzie = dev.serko.safariutils.data.Critters.byName("Gemzie");
        survey.observe(fish, -85, 81, 0);
        check(!survey.checked(fish, false), "entering an empty detection area does not prove coverage");
        survey.observe(fish, -85, 81, 4);
        survey.observe(gemzie, -141, 51, 3);
        check(survey.checked(fish, false) && survey.checked(gemzie, false),
            "non-overlapping Cavern areas retain confirmed coverage across movement");
        survey.clear();
        check(!survey.checked(fish, false) && !survey.checked(gemzie, true), "new run clears both coverage modes");
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
        String[] liveNames = {"Basic Safari Experience", "Economy Safari Experience", "Premium Safari Experience", "First-Class Safari Experience"};
        for (int tier = 0; tier < liveNames.length; tier++)
            check(TicketSelection.tier(liveNames[tier]) == tier + 1, "recorded live ticket name");
        check(TicketSelection.tier("Basic Safari Experience Upgrade") == 0, "unrelated experience item rejected");

        check(Boolean.TRUE.equals(TicketSelection.available(List.of("You own: 1,024", "Click to enter!"))), "owned tickets");
        check(Boolean.FALSE.equals(TicketSelection.available(List.of("You own: 0", "Click to enter!"))), "zero overrides click prompt");
        check(Boolean.FALSE.equals(TicketSelection.available(List.of("You don't have this ticket!", "Click to use"))), "negative lore wins");
        check(Boolean.TRUE.equals(TicketSelection.available(List.of("Click to enter!"))), "positive prompt");
        check(TicketSelection.available(List.of("A fancy ticket")) == null, "unknown availability stays manual");
        check(TicketSelection.available(List.of("You own: 999999999999999999999999")) == null, "overflow fails closed");
        check(TicketSelection.tier("§aEconomy") == 2, "bare menu tier");
        check(TicketSelection.tier("First-Class Ticket (1,024)") == 4, "count decorated tier");
        check(TicketSelection.entryTitle(" §6Critter Safari Entry "), "formatted menu title");
        check(!TicketSelection.entryTitle("Critter Safari Rewards"), "unrelated menu rejected");
        var premium = new TicketSelection.Option(12, 3, true);
        check(TicketSelection.choose(List.of(premium), 3) == premium, "requested option does not wait for other tiers");
        check(TicketSelection.choose(List.of(premium), 4) == null, "never substitutes another ticket");
        check(TicketSelection.choose(List.of(premium, new TicketSelection.Option(13, 3, true)), 3) == null,
            "duplicate requested tier remains manual");
        check(TicketSelection.choose(List.of(new TicketSelection.Option(12, 3, null)), 3) == null,
            "incomplete lore waits");
        check(Boolean.FALSE.equals(TicketSelection.choose(List.of(new TicketSelection.Option(12, 3, false)), 3).usable()),
            "unavailable ticket is reported without substitution");
        var clickCache = ClientCompat.class.getDeclaredField("CONTAINER_CLICK"); clickCache.setAccessible(true);
        var cached = ((ClassValue<?>)clickCache.get(null)).get(net.minecraft.client.multiplayer.MultiPlayerGameMode.class);
        var clickMethod = cached.getClass().getDeclaredMethod("method"); clickMethod.setAccessible(true);
        check(clickMethod.invoke(cached) != null, "this Minecraft profile exposes supported container input");
        var configField = ConfigManager.class.getDeclaredField("config"); configField.setAccessible(true);
        Object original = configField.get(null);
        try {
            var config = new SafariConfig(); configField.set(null, config);
            config.alerts.birdfeederSoundMode = 0;
        BirdfeederWatch.reset();
        BirdfeederWatch.creditFeedFound(2, 0, 0);
        for (int feed = 0; feed < 9; feed++) BirdfeederWatch.onChatMessage("A Bluebird was attracted to the Birdfeeder!");
        check(!BirdfeederWatch.allForestFeedUsed(), "nine floor feeds cannot consume two own starting feeds");
        for (int feed = 0; feed < 2; feed++) BirdfeederWatch.onChatMessage("A Bluebird was attracted to the Birdfeeder!");
        check(BirdfeederWatch.allForestFeedUsed(), "own starting and floor feed all resolved");
        BirdfeederWatch.onChatMessage("Party > Friend: [SM] Starting items: 2x Yogi Berry");
        check(BirdfeederWatch.allForestFeedUsed(), "teammate announcement cannot increase bird demand");
        BirdfeederWatch.reset();

            config.party.hotspotChatText = "my hotspot <BIOME>";
            config.party.biomeDoneChatText = "my clear <BIOME>";
            int originalItemMask = config.party.startingItemsMask;
            var originalItemChannel = config.party.startingItems();
            check(ChatTemplateFormats.HOTSPOT.format(config.party, "<BIOME>", "Icy").equals("my hotspot Icy"), "custom text by default");
            config.gameplay.biomeReports = true;
            check(!ChatTemplateFormats.HOTSPOT.locked(), "biome reports do not enable hotspot assignment");
            config.gameplay.assignHotspots = true;
            check(ChatTemplateFormats.HOTSPOT.format(config.party, "<BIOME>", "Icy").equals("[SU] Hotspot: Icy"), "exact hotspot format");
            check(ChatTemplateFormats.BIOME_UNIQUES.format(config.party, "<BIOME>", "Forest").equals("fd"), "exact done format");
            check(config.party.hotspot() == SafariConfig.Broadcast.PARTY && config.party.biomeDone() == SafariConfig.Broadcast.PARTY
                && !ChatTemplateFormats.STARTING_ITEMS.locked(), "compatible transport");
            check(ChatTemplateFormats.lockedValue(config.party, "hotspotChatText") != null && ChatTemplateFormats.lockedValue(config.party, "startingItemsMask") == null, "managed controls locked");
            check(config.party.startingItems() == originalItemChannel && config.party.startingItemsMask == originalItemMask,
                "compatibility cannot enable or change starting-item alert");
            check(ChatTemplateFormats.lockedValue(config.alerts, "hotspotText") == null, "banner controls remain editable");
            int[] items = new int[9]; items[4] = 1; items[6] = 2; items[0] = 1;
            check(ChatTemplateFormats.STARTING_ITEMS.format(config.party, "<ITEMS>", StartingItems.mutualsFormat(items))
                .equals("1x Shining Coin; 2x Yogi Berry"), "starting item alert keeps custom unprefixed format");
            config.gameplay.biomeReports = false;
            check(ChatTemplateFormats.HOTSPOT.locked() && !ChatTemplateFormats.BIOME_UNIQUES.locked(),
                "hotspot assignment does not enable biome reports");
            config.gameplay.assignHotspots = false;
            check(ChatTemplateFormats.HOTSPOT.format(config.party, "<BIOME>", "Icy").equals("my hotspot Icy"), "disabling restores saved custom text");
        } finally { configField.set(null, original); }
        return checks;
    }
    private static void check(boolean valid, String why) { checks++; if (!valid) throw new AssertionError(why); }
}
