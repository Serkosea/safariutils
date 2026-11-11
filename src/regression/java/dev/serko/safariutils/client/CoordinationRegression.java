package dev.serko.safariutils.client;

import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.state.HotspotAllocation;
import java.util.*;

public final class CoordinationRegression {
    private static int checks;
    public static int run() throws Exception {
        check(BiomeCoordination.electedReporter("a", Set.of("b", "c")), "lowest simultaneous reporter sends chat");
        check(!BiomeCoordination.electedReporter("b", Set.of("a", "c")), "other simultaneous reporters suppress duplicate chat");
        check(BiomeCoordination.electedReporter("b", Set.of()), "sole reporter sends chat for nonmod members");
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
            var authorityField = PartyHotspotSync.class.getDeclaredField("authority"); authorityField.setAccessible(true);
            Object backend = java.util.Arrays.stream(authorityField.getType().getEnumConstants())
                .filter(value -> value.toString().equals("BACKEND")).findFirst().orElseThrow();
            var unavailableField = PartyHotspotSync.class.getDeclaredField("unavailableSince"); unavailableField.setAccessible(true);
            authorityField.set(null, backend);
            unavailableField.setLong(null, 1);
            check(!PartyHotspotSync.controlsAssignments(9000), "lost backend before allocation must fall back");
            var lockedField = PartyHotspotSync.class.getDeclaredField("locked"); lockedField.setAccessible(true);
            @SuppressWarnings("unchecked") var fixed = (Map<String, SafariBiome>) lockedField.get(null);
            fixed.put(a, SafariBiome.HAUNTED);
            authorityField.set(null, backend);
            unavailableField.setLong(null, 1);
            check(PartyHotspotSync.controlsAssignments(9000) && fixed.get(a) == SafariBiome.HAUNTED,
                "disconnect must never reshuffle locked assignments");
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
            PartyHotspotSync.reconcile("{\"status\":\"joined\",\"runId\":\"expanded\"}");
            check(fixed.get(a)==SafariBiome.HAUNTED,"reconciled roster expansion must preserve finalized roles");
            var runField=PartyHotspotSync.class.getDeclaredField("run");runField.setAccessible(true);
            check("expanded".equals(runField.get(null)),"reconciled party run must replace singleton authority identity");

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
        check(partial.assignment(Set.of("self", "friend"), Map.of(), 2, 30049) == null, "incomplete roster waits thirty seconds from first report");
        SafariBiome result = partial.assignment(Set.of("self", "friend"), Map.of(), 2, 30050);
        check(result != null && partial.finished(), "partial assignment finalizes once after timeout");
        var winSnapshot = Map.copyOf(wins);
        partial.report("self", SafariBiome.ICY, 5100);
        check(partial.assignment(Set.of("self", "friend"), Map.of(), 2, 5200) == result && wins.equals(winSnapshot),
            "late reports and repeated ticks neither churn assignments nor increment wins");
        var complete = new HotspotAllocation(); complete.begin("self", SafariBiome.CAVERN, 0);
        complete.report("self", SafariBiome.CAVERN, 100); complete.report("friend", SafariBiome.CAVERN, 150);
        check(complete.assignment(Set.of("self", "friend"), Map.of(), 2, 150) != null, "complete admitted roster resolves immediately");
        check(complete.assignment(Set.of("self", "friend"), Map.of(), 2, 2100) != null, "completed assignment stays fixed");
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
        // Recorded Manager label from the 2026-11-04 failed debug run: ID 149735 at -51.5/68/21.5.
        var managerLabel = new SafariManagerTarget.Candidate(149735, new net.minecraft.world.phys.Vec3(-51.5, 68, 21.5), "Safari Manager", false, false, false);
        var npcBody = new SafariManagerTarget.Candidate(20, new net.minecraft.world.phys.Vec3(-51.5, 66.5, 21.5), "anonymous-npc", true, false, false);
        var nearbyPlayer = new SafariManagerTarget.Candidate(21, npcBody.position(), "Party Member", true, false, true);
        var proxy = new SafariManagerTarget.Candidate(22, npcBody.position(), "", false, true, false);
        check(SafariManagerTarget.choose(List.of(managerLabel)) == null, "label-only snapshot waits for clickable body");
        check(SafariManagerTarget.choose(List.of(npcBody)) == null, "body-only snapshot does not guess identity");
        check(Objects.equals(SafariManagerTarget.choose(List.of(managerLabel, npcBody, nearbyPlayer)), 20), "separate label resolves unnamed NPC and excludes real player");
        check(Objects.equals(SafariManagerTarget.choose(List.of(managerLabel, proxy)), 22), "interaction-only NPC supported");
        check(Objects.equals(SafariManagerTarget.choose(List.of(managerLabel, npcBody, proxy)), 20), "player body preferred over duplicate interaction proxy");
        check(SafariManagerTarget.choose(List.of(managerLabel, npcBody, new SafariManagerTarget.Candidate(23, npcBody.position(), "another-npc", true, false, false))) == null, "ambiguous bodies never clicked");
        check(SafariManagerTarget.choose(List.of(managerLabel, new SafariManagerTarget.Candidate(24, npcBody.position().add(1,0,0), "other-npc", true, false, false))) == null, "adjacent NPC outside label column excluded");
        var smoothTurn = new TicketTurnSmoother();
        {
            var owned=TicketTrading.class.getDeclaredField("hostOwnedRun");owned.setAccessible(true);
            var active=TicketTrading.class.getDeclaredField("tradeRunActive");active.setAccessible(true);
            var joined=TicketTrading.class.getDeclaredField("joinedTargets");joined.setAccessible(true);
            var baseline=TicketTrading.class.getDeclaredField("baselineMembers");baseline.setAccessible(true);
            @SuppressWarnings("unchecked") var guests=(Set<String>)joined.get(null);
            @SuppressWarnings("unchecked") var original=(Set<String>)baseline.get(null);
            try {
                owned.setBoolean(null,true);active.setBoolean(null,true);
                check(!TicketTrading.ownsCleanup(),"unaccepted invitations alone must not trigger exit cleanup");
                guests.add("guest");
                TicketTrading.onDisconnect();
                check(TicketTrading.ownsCleanup(),"joined guest cleanup must survive transfer before any warp");
                original.add("guest");
                check(!TicketTrading.ownsCleanup(),"pre-existing members alone must not become cleanup targets");
                original.clear();owned.setBoolean(null,false);
                check(!TicketTrading.ownsCleanup(),"guest cannot claim host cleanup authority");
            } finally {guests.clear();original.clear();owned.setBoolean(null,false);active.setBoolean(null,false);}
        }
        check(TicketTrading.arrivingPlayer("⚔ [MVP++] Stuffy warped to your instance").equals("stuffy"), "ranked warp arrival parsed");
        check(TicketTrading.arrivingPlayer("ᛜ _cj joined the Critter Safari.").equals("_cj"), "non-sync Safari arrival parsed");
        check(TicketTrading.arrivingPlayer("[MVP+] _cj joined the party.").isEmpty(), "party join is not Safari arrival");
        check(TicketTrading.arrivingPlayer("Party > Someone: _cj joined the Critter Safari.").isEmpty(), "party chat cannot confirm arrival");
        check(!TicketTrading.guestsArrived(java.util.Set.of("stuffy", "_cj"), java.util.Set.of("stuffy")), "one synced guest does not release mixed-party announcement");
        check(TicketTrading.guestsArrived(java.util.Set.of("stuffy", "_cj"), java.util.Set.of("stuffy", "_cj")), "all guests release announcement regardless of sync");
        float referenceYaw = 0;
        for (int fps : new int[]{30, 60, 144}) {
            smoothTurn.reset(0, 0);
            for (int frame = 0; frame < fps / 2; frame++) smoothTurn.update(120, -30, 1.0 / fps);
            if (fps == 30) referenceYaw = smoothTurn.yaw();
            else check(Math.abs(smoothTurn.yaw() - referenceYaw) < .001, "aim trajectory independent of frame rate");
        }
        smoothTurn.reset(0, 0);
        float firstStep = 0, peakStep = 0, lastStep = 0, previousYaw = 0;
        for (int frame = 0; frame < 120; frame++) {
            smoothTurn.update(90, 25, 1.0 / 120);
            float step = smoothTurn.yaw() - previousYaw;
            check(step >= 0 && smoothTurn.yaw() <= 90 && smoothTurn.pitch() <= 25, "turn remains monotonic without overshoot");
            if (frame == 0) firstStep = step;
            peakStep = Math.max(peakStep, step); lastStep = step; previousYaw = smoothTurn.yaw();
        }
        check(peakStep > firstStep * 4 && lastStep < peakStep / 10, "turn accelerates and decelerates instead of fixed-speed steps");
        check(smoothTurn.settled(90, 25), "normal turn settles within one second");
        smoothTurn.reset(179, 0);
        for (int frame = 0; frame < 60; frame++) smoothTurn.update(-179, 0, 1.0 / 60);
        check(smoothTurn.yaw() > 179 && smoothTurn.yaw() < 181 && smoothTurn.settled(-179, 0), "yaw boundary takes shortest two-degree turn");
        smoothTurn.reset(0, 0); smoothTurn.update(120, 30, 5);
        check(smoothTurn.yaw() < 15, "stalled frame cannot jump to target");
        float beforeInvalid = smoothTurn.yaw(); smoothTurn.update(Double.NaN, 0, .01);
        check(smoothTurn.yaw() == beforeInvalid, "invalid target leaves aim intact");
        check(!AutomaticTicketEntry.idleInput(null, net.minecraft.world.phys.Vec2.ZERO), "null input from another hook is not idle");
        check(!AutomaticTicketEntry.idleInput(net.minecraft.world.entity.player.Input.EMPTY, null), "null movement is not idle");
        check(!AutomaticTicketEntry.idleInput(net.minecraft.world.entity.player.Input.EMPTY, new net.minecraft.world.phys.Vec2(.1f, 0)), "analog movement from another mod wins");
        check(!AutomaticTicketEntry.idleInput(net.minecraft.world.entity.player.Input.EMPTY, new net.minecraft.world.phys.Vec2(Float.NaN, 0)), "invalid movement is rejected");
        check(AutomaticTicketEntry.idleInput(new net.minecraft.world.entity.player.Input(false,false,false,false,false,false,true),
            net.minecraft.world.phys.Vec2.ZERO), "toggle sprint alone does not cancel entry");
        check(!AutomaticTicketEntry.idleInput(new net.minecraft.world.entity.player.Input(true,false,false,false,false,false,true),
            net.minecraft.world.phys.Vec2.ZERO), "real directional input still cancels with toggle sprint");
        check(AutomaticTicketEntry.idleInput(net.minecraft.world.entity.player.Input.EMPTY, net.minecraft.world.phys.Vec2.ZERO), "only intact idle input permits automation");
        check(AutomaticTicketEntry.available() == !dev.serko.safariutils.BuildVersion.SAFE, "automatic entry is Extra only");
        if (dev.serko.safariutils.BuildVersion.SAFE) {
            var configField = ConfigManager.class.getDeclaredField("config");
            configField.setAccessible(true);
            Object previousConfig = configField.get(null);
            var importedConfig = new SafariConfig();
            configField.set(null, importedConfig);
            importedConfig.gameplay.autoSparklingTicket = true; // Simulate importing an enabled Extra config into Normal.
            try {
                AutomaticTicketEntry.tick(); // Must return before accessing the live Minecraft client.
                check(AutomaticTicketEntry.forwardInput(null, null) == 0, "Normal cannot inject movement");
                check(AutomaticTicketEntry.requestedTier(4) == 4 && !AutomaticTicketEntry.blocksSelection(), "normal menu selection remains independent");
            } finally { configField.set(null, previousConfig); }
        }
        check(!AutomaticTicketEntry.inWindow(-1) && !AutomaticTicketEntry.inWindow(0)
            && !AutomaticTicketEntry.inWindow(1500) && AutomaticTicketEntry.inWindow(1501), "entry requires known time for submission");
        check(!AutomaticTicketEntry.movementStalled(0), "unfocused frames without input cannot trip movement guard");
        check(!AutomaticTicketEntry.movementStalled(13.99f), "partial turning input gets its actual movement allowance");
        check(AutomaticTicketEntry.movementStalled(14), "fourteen blocked full movement ticks stop approach");
        check(AutomaticTicketEntry.approachSpeed(2, 0) == 0, "approach starts without a movement jump");
        check(AutomaticTicketEntry.approachSpeed(2, 100) < AutomaticTicketEntry.approachSpeed(2, 200), "approach eases in promptly");
        check(AutomaticTicketEntry.approachSpeed(.2, 500) < AutomaticTicketEntry.approachSpeed(2, 500), "approach slows near reach");
        check(AutomaticTicketEntry.approachSpeed(99, 99999) == 1, "approach reaches normal walking speed without sprinting");
        check(AutomaticTicketEntry.approachSpeed(0, 500) == 0, "approach stops at reach");
        check(AutomaticTicketEntry.headingSpeed(0) == 1 && AutomaticTicketEntry.headingSpeed(30) > .5,
            "walking overlaps turning before exact alignment");
        check(AutomaticTicketEntry.headingSpeed(90) == 0 && AutomaticTicketEntry.headingSpeed(180) == 0,
            "never walk sideways or away from Manager");
        check(AutomaticTicketEntry.headingSpeed(-30) == AutomaticTicketEntry.headingSpeed(30), "heading response symmetric");
        check(!AutomaticTicketEntry.retryDue(0, 999, false) && !AutomaticTicketEntry.retryDue(1, 449, false), "no premature NPC retries");
        check(AutomaticTicketEntry.retryDue(1, 450, false) && AutomaticTicketEntry.retryDue(2, 450, false), "two paced NPC retries");
        check(!AutomaticTicketEntry.retryDue(3, 999, false) && !AutomaticTicketEntry.retryDue(1, 999, true), "retry cap and menu arrival stop retries");
        var settings = new SafariConfig.GameplayConfig();
        check(!settings.autoSparklingTicket, "full entry defaults off");
        check(AutomaticTicketEntry.entryPreference(0) == 1 && AutomaticTicketEntry.entryPreference(4) == 4, "Manual entry starts with Basic; selected tier preserved");
        for (int preferred = 1; preferred <= 4; preferred++) {
            int[] order = switch (preferred) {
                case 1 -> new int[]{1, 2, 3, 4}; case 2 -> new int[]{2, 1, 3, 4};
                case 3 -> new int[]{3, 2, 1, 4}; default -> new int[]{4, 3, 2, 1};
            };
            for (int mask = 0; mask < 16; mask++) {
                List<TicketSelection.Option> options = new ArrayList<>();
                for (int candidate = 1; candidate <= 4; candidate++) options.add(new TicketSelection.Option(candidate + 10, candidate, (mask & (1 << (candidate - 1))) != 0));
                var selected = TicketSelection.chooseAvailable(options, preferred);
                int expected = 0;
                for (int candidate : order) if ((mask & (1 << (candidate - 1))) != 0) { expected = candidate; break; }
                check(selected != null && (expected == 0 ? !selected.usable() : selected.usable() && selected.tier() == expected), "all inventory combinations follow fallback priority");
            }
        }
        check(TicketSelection.chooseAvailable(List.of(new TicketSelection.Option(11, 1, null), new TicketSelection.Option(12, 2, true)), 1) == null, "unknown preferred tier cannot trigger an upgrade");
        check(TicketSelection.chooseAvailable(List.of(new TicketSelection.Option(14, 4, false), new TicketSelection.Option(11, 1, true)), 4) == null, "missing middle tiers wait for menu completion");
        check(TicketSelection.chooseAvailable(List.of(new TicketSelection.Option(11, 1, false), new TicketSelection.Option(12, 1, false), new TicketSelection.Option(13, 2, true)), 1) == null, "ambiguous preferred tier cannot trigger fallback");
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
    private static void completionContracts() {
        var store=new dev.serko.safariutils.state.CompletionSnapshot.Store();
        int[][] rows=new int[37][3];rows[14]=new int[]{7,4,3};
        var populated=new dev.serko.safariutils.state.CompletionSnapshot(rows);
        store.accept("a",populated,5000,10000);
        store.accept("b",populated,0,10000);
        check(store.value(14,2,10000)==3,"overlapping catches use max, not sum");
        store.accept("b",new dev.serko.safariutils.state.CompletionSnapshot(new int[37][3]),0,11000);
        check(store.value(14,0,49999)==7,"uncleared reporter remains valid until its own expiry");
        check(store.value(14,0,50000)==0,"expiry removes flags; lower replacement does not preserve OR history");
        check(store.size(56000)==0,"all expired reporter state pruned");
        String run=java.util.UUID.randomUUID().toString(),visit=java.util.UUID.randomUUID().toString();
        var json=com.google.gson.JsonParser.parseString(populated.payload()).getAsJsonObject();json.addProperty("runId",run);json.addProperty("visitId",visit);
        check(dev.serko.safariutils.state.CompletionSnapshot.parse(json.toString(),run)!=null,"matching complete wire snapshot accepted");
        check(dev.serko.safariutils.state.CompletionSnapshot.parse(json.toString(),visit)==null,"wrong run rejected");
        json.getAsJsonArray("rows").get(14).getAsJsonArray().set(1,new com.google.gson.JsonPrimitive(1.5));
        check(dev.serko.safariutils.state.CompletionSnapshot.parse(json.toString(),run)==null,"fractional count rejected");
    }

    private static void check(boolean valid, String why) { checks++; if (!valid) throw new AssertionError(why); }
}
