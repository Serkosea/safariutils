package dev.serko.safariutils.client;

import dev.serko.safariutils.data.*;
import dev.serko.safariutils.session.*;
import dev.serko.safariutils.state.*;
import dev.serko.safariutils.api.SharedSparklingProviders;
import net.minecraft.client.Minecraft;
import java.util.*;
import java.util.regex.Pattern;

/** Mode-specific coordination evidence, deliberately separate from catches and personal bests.
 * Consumes shared scans and objective ledgers; never creates catches from chat reports or missing entities.
 */
public final class BiomeCoordination {
    private static final Pattern REPORT = Pattern.compile(
        "^Party\\s*>\\s*(?:\\[[^\\]]+]\\s*)*([A-Za-z0-9_]{1,16}):\\s*([fcih])d\\.?$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> UNIQUE_CHECKS = Set.of("Honeybug", "Rockmite", "Snoozle", "Gazer", "Gimmiegold");
    private static final Set<String> BIRDS = Set.of("Bluebird", "Parakeet", "Macaw");
    private static final Set<SafariBiome> sent = EnumSet.noneOf(SafariBiome.class);
    private record Pending(RunMode mode, long due) { }
    private static final Map<SafariBiome, Pending> pendingChat = new EnumMap<>(SafariBiome.class);
    private static final Map<SafariBiome, Set<String>> reporters = new EnumMap<>(SafariBiome.class);
    private static final Set<SafariBiome> chatReceived = EnumSet.noneOf(SafariBiome.class);
    private static final Set<SafariBiome> received = EnumSet.noneOf(SafariBiome.class);
    private static final Map<Critter, Integer> population = new HashMap<>();
    private static final Survey survey = new Survey();
    /** Coverage is retained per run; separated regions need not overlap. */
    static final class Survey {
        private final Set<Critter> full = new HashSet<>(), sparkling = new HashSet<>();
        void observe(Critter critter, double x, double z, int accounted) {
            if (accounted < minimum(critter)) return;
            if (inZone(critter, x, z, false)) full.add(critter);
            if (minimum(critter) > 0 && inZone(critter, x, z, true)) sparkling.add(critter);
        }
        boolean checked(Critter critter, boolean sparkle) { return (sparkle ? sparkling : full).contains(critter); }
        void clear() { full.clear(); sparkling.clear(); }
    }
    private static final long SETTLE_MILLIS = 1500;
    private static final SoloDiagnostic soloDiagnostic = new SoloDiagnostic();
    /** Bounded dry-run latch. Never changes actual sent/received reports or command cooldowns. */
    static final class SoloDiagnostic {
        private SafariBiome biome;
        private RunMode mode;
        private long since;
        private boolean reported;
        boolean poll(SafariBiome nextBiome, RunMode nextMode, boolean ready, long now) {
            if (!ready) { clear(); return false; }
            if (biome != nextBiome || mode != nextMode) {
                biome = nextBiome; mode = nextMode; since = now; reported = false;
                return false;
            }
            if (reported || now - since < SETTLE_MILLIS) return false;
            reported = true;
            return true;
        }
        void clear() { biome = null; mode = null; since = 0; reported = false; }
    }
    private static SafariSession session;
    private static long nextCheck, lastSent;
    private static SafariBiome candidate;
    private static RunMode candidateMode;
    private static long candidateAt;
    private static long partyGeneration = -1;
    private static String lastDecision = "";
    private static String blocker = "";
    private BiomeCoordination() { }

    public static void tick() {
        SafariSession current = SessionManager.current();
        if (current != session) {
            session = current; sent.clear(); pendingChat.clear(); population.clear(); survey.clear();
            nextCheck = 0; candidate = null; lastSent = 0; lastDecision = ""; partyGeneration = -1; soloDiagnostic.clear();
        }
        if (session == null || !SafariLocation.inside()) {
            candidate = null; soloDiagnostic.clear(); lastDecision = ""; return;
        }
        long now = MonotonicTime.nowMillis();
        if (now < nextCheck) return;
        nextCheck = now + 250;
        SafariBiome biome = SafariLocation.biome();
        var player = Minecraft.getInstance().player;
        if (biome == null || player == null) { candidate = null; soloDiagnostic.clear(); lastDecision = ""; return; }
        Map<Critter, Integer> sparklings = SparklingWatch.outstandingCounts();
        for (Critter critter : Critters.inBiome(biome)) {
            int live = DetectedCritters.currentConcurrent(critter) + sparklings.getOrDefault(critter, 0);
            int caught = session.partyCatches(critter);
            // Max concurrent bodies + confirmed catches avoids counting breakout UUID replacements twice.
            population.merge(critter, Math.min(CritterSpawnRanges.maximum(critter), caught + live), Math::max);
            survey.observe(critter, player.getX(), player.getZ(), caught + live);
        }
        if (!ConfigManager.get().gameplay.biomeReports) return;
        var party = PartyState.snapshot();
        boolean soloTest = dev.serko.safariutils.BuildVersion.has(dev.serko.safariutils.BuildVersion.Capability.DEVELOPER_TOOLS)
            && DebugLog.isEnabled() && party.known() && !party.inParty();
        if (!soloTest) soloDiagnostic.clear();
        if (!soloTest && (!party.inParty() || !party.completeRoster())) {
            candidate = null; lastDecision = ""; return;
        }
        if (partyGeneration != party.generation()) {
            partyGeneration = party.generation(); candidate = null; soloDiagnostic.clear();
        }
        RunMode mode = RunMode.current();
        flushReports(now, mode, sparklings, player.getX(), player.getZ());
        boolean ready;
        if (sent.contains(biome)) { blocker = "already-reported"; ready = false; }
        else if (!reportAllowed(biome, mode,
                session.caughtByParty(Critters.byName("Wumpa")), needs(Critters.byName("Wumpa")),
                Critters.inBiome(SafariBiome.ICY).stream().anyMatch(c -> !c.name().equals("Wumpa") && needs(c)))) {
            blocker = "icy-report-not-needed"; ready = false;
        } else if (received.contains(biome) && (mode == RunMode.UNIQUE || biome == SafariBiome.FOREST || biome == SafariBiome.HAUNTED)) {
            blocker = "already-reported-by-party"; ready = false;
        } else {
            blocker = "no-survey-evidence";
            ready = complete(biome, mode, sparklings, player.getX(), player.getZ());
        }
        if (DebugLog.isEnabled()) {
            String decision = (soloTest ? "solo-test " : "") + biome + "/" + mode + "/" + (ready ? "ready" : blocker);
            if (!decision.equals(lastDecision)) {
                lastDecision = decision;
                DebugLog.line(soloTest ? "COORDINATION/BIOME_TEST" : "COORDINATION/BIOME", decision);
            }
        }
        if (soloTest) {
            candidate = null;
            if (soloDiagnostic.poll(biome, mode, ready, now))
                DebugLog.line("COORDINATION/BIOME_TEST", "would-send=" + word(biome) + " biome=" + biome
                    + " mode=" + mode + " settledMs=" + SETTLE_MILLIS + " sent=false");
            return; // No chat/network request and no mutation of the real report ledger.
        }
        if (!ready) { candidate = null; return; }
        if (candidate != biome || candidateMode != mode) {
            candidate = biome; candidateMode = mode; candidateAt = now; return;
        }
        // Re-evaluate after spawn/metadata settling, and again before each actual send.
        if (now - candidateAt < SETTLE_MILLIS || now - lastSent < 3000) return;
        var request = CommandCoordinator.request("pc " + word(biome), CommandCoordinator.Category.PARTY_CHAT,
            CommandCoordinator.Cooldown.CHAT, CommandCoordinator.Scope.RUN, false, true);
        boolean network = PartyHotspotSync.networkAuthority()
            && dev.serko.safariutils.api.PartySyncNetworks.coordinationSupported();
        if (network ? PartyHotspotSync.sendBiome(word(biome)) : CommandCoordinator.trySend(request)) {
            sent.add(biome); lastSent = now; candidate = null;
            if (network) pendingChat.put(biome, new Pending(mode, now + 1500));
            DebugLog.line("COORDINATION/BIOME", "sent=" + word(biome) + " mode=" + mode + " evidence=local");
        }
    }


    static boolean electedReporter(String self, Set<String> peers) {
        return peers.stream().noneMatch(peer -> peer.compareTo(self) < 0);
    }

    private static void flushReports(long now, RunMode mode, Map<Critter, Integer> outstanding, double x, double z) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        String self = player.getUUID().toString().replace("-", "");
        for (var iterator = pendingChat.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            SafariBiome biome = entry.getKey();
            Pending pending = entry.getValue();
            if (chatReceived.contains(biome) || pending.mode() != mode) { iterator.remove(); continue; }
            if (now < pending.due()) continue;
            // Stable UUID election for concurrently submitted reports.
            if (!electedReporter(self, reporters.getOrDefault(biome, Set.of()))) { iterator.remove(); continue; }
            if (!complete(biome, mode, outstanding, x, z)) continue;
            var request = CommandCoordinator.request("pc " + word(biome), CommandCoordinator.Category.PARTY_CHAT,
                CommandCoordinator.Cooldown.CHAT, CommandCoordinator.Scope.RUN, false, true);
            if (CommandCoordinator.trySend(request)) {
                DebugLog.line("COORDINATION/BIOME", "chat=" + word(biome) + " elected=true");
                iterator.remove();
            }
        }
    }

    private static boolean complete(SafariBiome biome, RunMode mode, Map<Critter, Integer> outstanding, double x, double z) {
        if (dev.serko.safariutils.api.PartyItemSyncProviders.heldObjectivesPending(biome)
                && (biome != SafariBiome.HAUNTED || mode == RunMode.FULL_CLEAR || needs(Critters.byName("Gimmiegold")))) {
            blocker = "shared-supplies-pending"; return false;
        }
        // A failed/incomplete collection fetch must not mean 'everyone owns everything'.
        boolean collectionReady = SharedSparklingProviders.partySnapshot().apiManaged();
        if (mode == RunMode.SPARKLING && !collectionReady) { blocker = "party-collection-pending"; return false; }
        boolean target = false;
        for (Critter critter : Critters.inBiome(biome)) {
            if (outstanding.containsKey(critter)) { blocker = "sparkling-uncaught:" + critter.name(); return false; }
            boolean needed = !collectionReady || needs(critter);
            if (mode == RunMode.SPARKLING && !needed) continue;
            target = true;
            boolean capture = session.caughtByParty(critter) || exemptUnspawned(critter);
            // Evaluate only this mode's expensive objective checks, not all three policies.
            boolean full = mode == RunMode.FULL_CLEAR && fullCaught(critter, x, z);
            boolean checkRequired = mode == RunMode.SPARKLING || mode == RunMode.UNIQUE
                && (BIRDS.contains(critter.name()) || needed && UNIQUE_CHECKS.contains(critter.name()));
            boolean check = checkRequired && sparklingChecked(critter, x, z);
            boolean shared = !localContradiction(critter,mode) && PartyCompletionSync.completes(critter,mode,capture);
            if (!shared && !speciesComplete(mode, capture, full, check, needed, critter.name())) { blocker = "incomplete:" + critter.name(); return false; }
        }
        // No missing species is a valid empty target set, but entering the biome alone
        // before any entity/catch data arrives must not announce it as surveyed.
        return target || population.entrySet().stream().anyMatch(e -> e.getKey().biome() == biome && e.getValue() > 0);
    }

    static CompletionSnapshot localCompletionSnapshot(java.util.function.Predicate<Critter> localSources) {
        int[][] rows=new int[37][3];
        var player=Minecraft.getInstance().player;
        if(session==null || player==null || session!=SessionManager.current())return new CompletionSnapshot(rows);
        var order=Critters.selectionOrder();
        for(int i=0;i<order.size();i++) {
            Critter critter=order.get(i);
            int captures=Math.min(256,session.partyCatches(critter));
            rows[i][2]=captures;
            rows[i][1]=Math.min(256,population.getOrDefault(critter,0));
            if(!localSources.test(critter) || SparklingWatch.outstandingCount(critter)>0)continue;
            boolean checked=sparklingChecked(critter,player.getX(),player.getZ());
            boolean unique=captures>0 || exemptUnspawned(critter);
            // Export the stricter qualified Unique proof, independent of the sender's collection/mode.
            if(unique && ((!BIRDS.contains(critter.name()) && !UNIQUE_CHECKS.contains(critter.name())) || checked))rows[i][0]|=1;
            if(checked)rows[i][0]|=2;
            if(fullCaught(critter,player.getX(),player.getZ()))rows[i][0]|=4;
        }
        return new CompletionSnapshot(rows);
    }
    static boolean localContradiction(Critter critter,RunMode mode) {
        if(SparklingWatch.outstandingCount(critter)>0)return true;
        if(mode==RunMode.FULL_CLEAR && DetectedCritters.currentConcurrent(critter)>0)return true;
        // Visible unresolved sources are positive evidence; an unvisited catalog slot is not.
        return switch(critter.name()) {
            case "Bluebird", "Parakeet", "Macaw" -> SafariObjectives.birdFeedHeld()>0;
            case "Gimmiegold" -> SafariObjectives.shiningCoinsHeld()>0;
            case "Snoozle" -> WallTracker.SNOOPER.walls().stream().anyMatch(w->w.state()==WallTracker.State.INTACT);
            case "Troodon" -> WallTracker.TROODON.walls().stream().anyMatch(w->w.state()==WallTracker.State.INTACT);
            case "Rockmite" -> SafariLocation.biome()==critter.biome() && !MoundSpotter.mounds().isEmpty();
            case "Honeybug" -> SafariLocation.biome()==critter.biome() && NestTracker.nests().stream().anyMatch(NestTracker.Nest::unpunched);
            default -> false;
        };
    }

    static boolean speciesComplete(RunMode mode, boolean unique, boolean full, boolean checked,
            boolean needed, String name) {
        return switch (mode) {
            case FULL_CLEAR -> full;
            case SPARKLING -> !needed || checked;
            case UNIQUE -> unique && (!BIRDS.contains(name) || checked)
                && (!needed || !UNIQUE_CHECKS.contains(name) || checked);
        };
    }
    static boolean reportAllowed(SafariBiome biome, RunMode mode, boolean wumpaCaught,
            boolean wumpaNeeded, boolean otherIcyNeeded) {
        if (biome != SafariBiome.ICY || mode == RunMode.FULL_CLEAR) return true;
        if (wumpaCaught) return false;
        return mode == RunMode.UNIQUE || !wumpaNeeded && otherIcyNeeded;
    }
    static String word(SafariBiome biome) {
        return switch (biome) { case FOREST -> "fd"; case CAVERN -> "cd"; case ICY -> "id"; case HAUNTED -> "hd"; };
    }
    private static boolean needs(Critter critter) { return !SparklingMode.isShared(critter); }
    private static int minimum(Critter critter) { return CritterSpawnRanges.forCritter(critter).minimum(); }

    private static boolean sparklingChecked(Critter critter, double x, double z) {
        if (!BIRDS.contains(critter.name()) && PartySparklingDiscoveries.locallyFound(critter)) return true;
        if (critter.biome() == SafariBiome.ICY && session.caughtByParty(Critters.byName("Wumpa"))) return true;
        if (!sourcesReady(critter)) return false;
        return switch (critter.name()) {
            case "Wumpa", "Doomspiral" -> session.caughtByParty(critter);
            case "Gimmiegold" -> inZone(critter, x, z, true);
            case "Bluebird", "Parakeet", "Macaw", "Rockmite", "Snoozle", "Honeybug" -> true;
            default -> survey.checked(critter, true);
        };
    }
    private static boolean exemptUnspawned(Critter critter) {
        if (session.caughtByParty(critter) || DetectedCritters.everSeen(critter)) return false;
        if (BIRDS.contains(critter.name())) return BirdfeederWatch.allForestFeedUsed() && !BirdfeederWatch.everSpawned(critter);
        return switch (critter.name()) {
            case "Rockmite" -> MoundSpotter.sourcesExhausted();
            case "Snoozle" -> WallTracker.SNOOPER.allConfirmedBroken();
            default -> false;
        };
    }
    private static boolean fullCaught(Critter critter, double x, double z) {
        if (!sourcesReady(critter)) return false;
        // Extra Macaws never prolong a full clear once one is caught. Actual uncaught
        // Sparklings are checked separately before this rule is evaluated.
        if (critter.name().equals("Macaw")) return session.caughtByParty(critter) || exemptUnspawned(critter);
        if (DetectedCritters.currentConcurrent(critter) > 0) return false;
        int sourceCount = switch (critter.name()) {
            case "Honeybug" -> NestTracker.confirmedNestCount();
            case "Gimmiegold" -> ShiningCoinWatch.reportTarget(FloorDrops.knownCoinSupply());
            case "Bluebird", "Parakeet" -> BirdfeederWatch.spawnedCount(critter);
            default -> 0;
        };
        int target = captureTarget(critter, population.getOrDefault(critter, 0), sourceCount);
        if (session.partyCatches(critter) < target) return false;
        return survey.checked(critter, false) || critter.hasQuota();
    }
    static int captureTarget(Critter critter, int population, int sources) {
        if (critter.name().equals("Macaw")) return population > 0 || sources > 0 ? 1 : 0;
        return Math.max(minimum(critter), Math.max(population, sources));
    }
    private static boolean sourcesReady(Critter critter) {
        return switch (critter.name()) {
            case "Bluebird", "Parakeet", "Macaw" -> BirdfeederWatch.allForestFeedUsed();
            case "Honeybug" -> NestTracker.sourcesExhausted();
            case "Rockmite" -> MoundSpotter.sourcesExhausted();
            case "Snoozle" -> WallTracker.SNOOPER.allConfirmedBroken();
            case "Troodon" -> WallTracker.TROODON.allConfirmedBroken();
            case "Gemzie" -> SafariObjectives.gemzieDoorDisplayReady();
            case "Gimmiegold" -> SafariObjectives.shiningCoinsHeld() == 0 && ShiningCoinWatch.ownCoinsUsed()
                && (FloorDrops.knownCoinSupply() >= 0 ? !FloorDrops.coinsRemain()
                    : ShiningCoinWatch.floorFound() >= 3 && FloorDrops.everSeen(SafariBiome.HAUNTED) > 0
                        && FloorDrops.remaining(SafariBiome.HAUNTED) == 0)
                && population.getOrDefault(critter, 0) >= ShiningCoinWatch.reportTarget(FloorDrops.knownCoinSupply());
            default -> true;
        };
    }

    /** Shared-protocol survey radii; being outside entity range is never a completed check. */
    static boolean inZone(Critter critter, double x, double z, boolean sparkling) {
        double cx, cz, radius = sparkling ? 40 : 30;
        switch (critter.name()) {
            case "Foxtrot", "Fluffling", "Honeybug", "Bluebird", "Parakeet", "Macaw" -> { cx=3; cz=47; }
            case "Woodchucker", "Treefrog", "Hideonfloor" -> { cx=3; cz=47; radius=sparkling?55:50; }
            case "Cavernfish" -> { cx=-85; cz=81; }
            case "Flitter" -> { cx=-84; cz=62; }
            case "Shyworm" -> { cx=-119; cz=43; }
            case "Driftling" -> { cx=-120; cz=55; radius=sparkling?60:40; }
            case "Chuckwalla" -> { cx=-100; cz=45; radius=sparkling?60:40; }
            case "Gemzie" -> { cx=-141; cz=51; }
            case "Rockmite", "Snoozle", "Scrappy" -> { cx=-114; cz=49; }
            case "Tepid", "Nozzlenose", "Mantis Shrimp" -> { cx=-73; cz=-46; }
            case "Strongarm", "Troodon" -> { cx=-107; cz=-56; }
            case "Polaris", "Wumpa" -> { cx=-110; cz=-78; }
            case "Shuddersquid" -> { cx=-127; cz=-48; }
            case "Billygoat" -> { cx=-121; cz=-55; }
            default -> { cx=-3; cz=-63; }
        }
        return Double.isFinite(x) && Double.isFinite(z) && (x-cx)*(x-cx)+(z-cz)*(z-cz) <= radius*radius;
    }
    public static void resetVisit() { received.clear(); reporters.clear(); pendingChat.clear(); chatReceived.clear(); soloDiagnostic.clear(); lastDecision = ""; }
    public static void onNetworkReport(String code, String sender) {
        if (!SafariLocation.inside()) return;
        for (SafariBiome biome : SafariBiome.values()) if (word(biome).equals(code)) {
            received.add(biome);
            reporters.computeIfAbsent(biome, ignored -> new TreeSet<>()).add(sender);
        }
    }
    public static void onChatMessage(String line) {
        if (SessionManager.current() == null || !SafariLocation.inside()) return;
        var matcher = REPORT.matcher(line);
        if (!matcher.matches() || !PartyState.snapshot().containsName(matcher.group(1))) return;
        // Chat supplies coordination only; never a fabricated catch, PB, or collection entry.
        for (SafariBiome biome : SafariBiome.values()) if (word(biome).equalsIgnoreCase(matcher.group(2) + "d")) { received.add(biome); chatReceived.add(biome); }
    }
}
