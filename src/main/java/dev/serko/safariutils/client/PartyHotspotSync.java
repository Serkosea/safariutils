package dev.serko.safariutils.client;

import com.google.gson.*;
import dev.serko.safariutils.api.PartySyncNetworks;
import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.state.*;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;

/** One visit-scoped authority over the existing bounded party transport. Client thread only. */
public final class PartyHotspotSync {
    private enum Authority { WAITING, BACKEND, FALLBACK }
    private static Authority authority = Authority.WAITING;
    private static long unavailableSince = -1;
    private static long waitingSince = -1, nextHello, nextReports, revision = -1;
    private static String run = "", lastHello = "";
    private static boolean conflict;
    private static BiConsumer<String, String> sender;
    private static List<String> roster = List.of();
    private static final Map<String, SafariBiome> reports = new LinkedHashMap<>(), locked = new LinkedHashMap<>();
    private static final Set<String> reported = new HashSet<>(), sentNotices = new HashSet<>(), acknowledged = new HashSet<>();
    private static HotspotSnapshot latest;
    private PartyHotspotSync() { }
    private static String self() {
        var player = Minecraft.getInstance().player;
        return player == null ? "" : player.getUUID().toString().replace("-", "");
    }
    public static boolean controlsAssignments() { return controlsAssignments(MonotonicTime.nowMillis()); }
    static boolean controlsAssignments(long now) {
        // Unknown membership must not start the fallback clock; solo allocation is local.
        if (roster.isEmpty()) return true;
        if (roster.size() == 1) return false;
        if (authority == Authority.BACKEND) {
            if (PartySyncNetworks.coordinationSupported() || !locked.isEmpty()) {
                unavailableSince = -1;
                return true; // Never reshuffle an already allocated party after disconnect.
            }
            if (unavailableSince < 0) unavailableSince = now;
            if (now - unavailableSince < 7000) return true;
            authority = Authority.FALLBACK;
            DebugLog.line("HOTSPOT/NETWORK", "fallback after disconnect before any assignment");
            return false;
        }
        if (authority == Authority.FALLBACK) return false;
        if (waitingSince < 0) waitingSince = now;
        if (now - waitingSince < 7000) return true;
        authority = Authority.FALLBACK;
        DebugLog.line("HOTSPOT/NETWORK", "fallback selected before backend authority");
        return false;
    }
    public static boolean conflicted() { return conflict; }
    public static String statusText() {
        if (!SafariLocation.inside()) return "Outside Safari";
        if (PartySyncNetworks.runSyncSupported() && !PartySyncNetworks.replayReady()) return "Reconciling run / replay";
        return conflict ? "Conflict · choices preserved" : switch(authority) {
            case WAITING -> "Waiting for authority";
            case BACKEND -> locked.isEmpty()?"Backend · awaiting allocation":"Backend · "+locked.size()+" locked";
            case FALLBACK -> locked.isEmpty()?"Local / chat fallback":"Fallback · "+locked.size()+" locked";
        };
    }
    public static boolean networkAuthority() { return authority == Authority.BACKEND; }
    public static boolean sendBiome(String code) {
        if (!networkAuthority() || !PartySyncNetworks.coordinationSupported() || sender == null) return false;
        sender.accept("B", code); return true;
    }
    public static void report(String name, SafariBiome biome) {
        if (biome == null) return;
        PartyState.snapshot().members().stream().filter(m -> m.name().equalsIgnoreCase(name))
            .filter(m -> !m.uuid().isBlank()).findFirst().ifPresent(m -> reports.putIfAbsent(m.uuid(), biome));
    }
    public static void lockFallback(Map<String, SafariBiome> assignments, Map<String, String> identities) {
        assignments.forEach((name, biome) -> {
            String id = identities.get(name);
            if (id != null) locked.putIfAbsent(id.replace("-", "").toLowerCase(Locale.ROOT), biome);
        });
        lastHello = "";
    }
    public static void tick(List<String> members, BiConsumer<String, String> output, boolean reconnected) {
        sender = output; roster = List.copyOf(members);
        if (members.isEmpty()) return;
        if (!SafariLocation.inside() || !visitEligible() || !PartySyncNetworks.coordinationSupported()) return;
        if (reconnected) { reported.clear(); acknowledged.clear(); lastHello = ""; }
        // A fallback already in progress must finish and upload its immutable choices first.
        // A recovering authority can still reconcile before fallback choices finalize.
        var party = PartyState.snapshot();
        // Keep the frozen room and assignments during disbands, but do not coordinate
        // a different actual party against the previous party's room.
        if (party.inParty() && (!party.completeRoster()
                || party.members().stream().anyMatch(member -> !roster.contains(member.uuid())))) return;
        long now = MonotonicTime.nowMillis();
        JsonObject hello = hello(party, assignmentEligible(), ConfigManager.get().gameplay.assignHotspots);
        String text = hello.toString();
        if (!text.equals(lastHello) || now >= nextHello) {
            output.accept("H", text); lastHello = text; nextHello = now + (PartySyncNetworks.runSyncSupported() && !PartySyncNetworks.replayReady()?2000:15000);
        }
        if (PartySyncNetworks.runSyncSupported() && !PartySyncNetworks.replayReady()) return;
        if (assignmentEligible() && locked.containsKey(self()) && ConfigManager.get().gameplay.assignHotspots)
            HotspotWatch.acceptAssignment(locked.get(self()));
        SafariBiome actual = assignmentEligible() ? HotspotWatch.biome() : null;
        if (actual != null) reports.putIfAbsent(self(), actual);
        if(now>=nextReports) {reported.clear();nextReports=now+2000;}
        for (var entry : reports.entrySet()) if (roster.contains(entry.getKey()) && !locked.containsKey(entry.getKey()) && reported.add(entry.getKey())) {
            JsonObject report = message("report"); report.addProperty("player", entry.getKey()); report.addProperty("biome", entry.getValue().name());
            output.accept("H", report.toString());
        }
        if (!assignmentEligible() || latest == null || conflict || !announcementLeader(party) || !ConfigManager.get().gameplay.assignHotspots
                || !self().equals(latest.leader())) return;
        for (var notice : latest.notifications()) {
            String id = notice.id(), player = notice.player();
            var target = party.members().stream().filter(m -> m.uuid().equals(player)).findFirst().orElse(null);
            if (target == null) continue;
            if (!sentNotices.contains(id)) {
                SafariBiome biome = notice.biome();
                var request = CommandCoordinator.request("pc " + target.name() + " you are assigned " + biome.displayName() + " biome!",
                    CommandCoordinator.Category.PARTY_CHAT, CommandCoordinator.Cooldown.CHAT, CommandCoordinator.Scope.VISIT, true, true);
                if (!CommandCoordinator.trySend(request)) break;
                sentNotices.add(id);
                if (PartySyncDebugHud.enabled()) PartySyncDebugHud.event("Sent assignment to "+target.name(),0xFFFFCE63);
            }
            if (acknowledged.add(id)) { JsonObject ack = message("ack"); ack.addProperty("id", id); output.accept("H", ack.toString()); }
        }
    }
    public static void accept(String text) {
        if (!SafariLocation.inside() || !visitEligible()) return;
        try {
            var value = HotspotSnapshot.parse(text, roster);
            if (!run.isEmpty() && !run.equals(value.run())) { conflict = true; return; }
            if (value.revision() <= revision) return;
            var parsed = value.assignments();
            conflict |= value.conflictsWith(locked);
            conflict |= value.conflict();
            run = value.run(); revision = value.revision(); latest = value;
            if (authority == Authority.WAITING) authority = Authority.BACKEND;
            HotspotWatch.acceptFairness(value.fairness());
            if (conflict) { DebugLog.line("HOTSPOT/NETWORK", "conflict: preserving finalized assignments"); return; }
            // Recovery adopts backend transport while preserving every imported finalized choice.
            authority = Authority.BACKEND;
            if (PartySyncDebugHud.enabled()) for (var entry : parsed.entrySet()) if (!locked.containsKey(entry.getKey())) {
                String name=PartyState.snapshot().members().stream().filter(m->m.uuid().equals(entry.getKey())).map(PartySnapshot.Member::name).findFirst().orElse("Party member");
                PartySyncDebugHud.event(name+": assigned "+entry.getValue().displayName(),0xFF77E59E);
            }
            locked.putAll(parsed);
            if (assignmentEligible() && authority == Authority.BACKEND && ConfigManager.get().gameplay.assignHotspots && locked.containsKey(self()))
                HotspotWatch.acceptAssignment(locked.get(self()));
        } catch (RuntimeException malformed) { DebugLog.line("HOTSPOT/NETWORK", "invalid assignment snapshot ignored"); }
    }
    // The visit roster is deliberately frozen; a disband must not stop ticket-state updates.
    static JsonObject hello(PartySnapshot party, boolean ticketed, boolean display) {
        JsonObject hello = message("hello");
        hello.addProperty("display", display);
        hello.addProperty("ticketed", ticketed);
        hello.addProperty("leader", announcementLeader(party));
        JsonArray present = new JsonArray();
        party.presentInSafari().stream().map(PartySnapshot.Member::uuid).filter(roster::contains).distinct().forEach(present::add);
        hello.add("present", present);
        JsonObject fixed = new JsonObject(); locked.forEach((id, biome) -> { if (roster.contains(id)) fixed.addProperty(id, biome.name()); });
        hello.add("locked", fixed);
        return hello;
    }
    static boolean announcementLeader(PartySnapshot party) {
        return party.completeRoster() && party.inParty() && party.localLeader()
            && party.members().stream().allMatch(member -> roster.contains(member.uuid()));
    }
    private static boolean visitEligible() {
        var phase=SafariRuntimeState.current().phase();
        return phase==SafariEpoch.Phase.VISIT || phase==SafariEpoch.Phase.ACTIVE;
    }
    static boolean assignmentEligible() {
        return SafariLocation.inside() && SafariRuntimeState.current().phase()==SafariEpoch.Phase.ACTIVE
            && dev.serko.safariutils.session.SessionManager.current()!=null;
    }
    private static JsonObject message(String action) { JsonObject value = new JsonObject(); value.addProperty("version",1); value.addProperty("action", action); return value; }
    public static void leave() {
        if (sender != null && PartySyncNetworks.coordinationSupported()) sender.accept("H", message("leave").toString());
    }
    public static void reset() {
        PartySyncDebugHud.resetVisit();
        authority = Authority.WAITING; unavailableSince = -1; waitingSince = -1; nextHello = 0; revision = -1; run = ""; lastHello = "";
        conflict = false; sender = null; latest = null; roster = List.of(); reports.clear(); locked.clear(); reported.clear(); sentNotices.clear(); acknowledged.clear();
    }
}
