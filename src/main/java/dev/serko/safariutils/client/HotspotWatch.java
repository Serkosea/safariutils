package dev.serko.safariutils.client;

import dev.serko.safariutils.data.SafariBiome;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tracks the player's personal Hunting Hotspot from Hypixel's one-time message. */
public final class HotspotWatch {

	/** Anchored so a quoted hotspot message cannot match inside a longer line. */
	private static final Pattern HOTSPOT =
		Pattern.compile("^HOTSPOT! Your Hunting Hotspot is the (.+?) Biome!$");

	private static SafariBiome hotspot;
    private static final java.util.Map<String, Integer> hotspotWins = new java.util.HashMap<>();
    private static java.util.Set<String> sessionRoster = java.util.Set.of();
    private static dev.serko.safariutils.state.HotspotAllocation allocation = new dev.serko.safariutils.state.HotspotAllocation(hotspotWins);
    private static SafariBiome assigned;
    private static boolean announced;
    private static final HotspotBannerState bannerState = new HotspotBannerState();

    private static final Pattern PARTY_HOTSPOT = Pattern.compile(
        "^Party\\s*>\\s*(?:\\[[^\\]]+]\\s*)*([A-Za-z0-9_]{1,16}):\\s*"
        + "(?:\\[(?:SM|CritterMod|SafariUtils|CM|SU)]\\s*)?(?:My\\s+)?(?:Hunting\\s+)?Hotspot:\\s*"
        + "(Forest|Cavern|Icy|Ice|Haunted)(?:\\s+Biome)?[!.]?$", Pattern.CASE_INSENSITIVE);

    public static SafariBiome assignedBiome() { return enabled() ? assigned : null; }
    private static boolean enabled() {
        var settings = ConfigManager.get().gameplay;
        return settings.assignHotspots;
    }
    public static void tick() {
        if (!HypixelConnection.active() || !enabled()) { hotspotWins.clear(); sessionRoster = java.util.Set.of(); }
        if (!SafariLocation.inside() || !enabled()) {
            if (SafariLocation.inside()) showBanner();
            clearAssignment();
            return;
        }
        if (hotspot == null || !PartyHotspotSync.assignmentEligible()) return;
        showBanner();
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.player == null) return;
        var party = dev.serko.safariutils.state.PartyState.snapshot();
        if (!party.known()) return;
        java.util.Set<String> names = new java.util.HashSet<>();
        java.util.Map<String, String> uuids = new java.util.HashMap<>();
        for (var member : party.members()) {
            String name = member.name().toLowerCase(java.util.Locale.ROOT);
            names.add(name); if (!member.uuid().isEmpty()) uuids.put(name, member.uuid());
        }
        String self = client.player.getName().getString().toLowerCase(java.util.Locale.ROOT);
        names.add(self); uuids.put(self, client.player.getUUID().toString());
        java.util.Set<String> identities = rosterIdentity(names, uuids);
        if (!sessionRoster.equals(identities)) {
            sessionRoster = identities;
            hotspotWins.clear();
            // Preserve this run's reports and frozen assignment; identity resolution
            // can complete after the first chat echo. Only fairness history resets.
        }
        long now = dev.serko.safariutils.state.MonotonicTime.nowMillis();
        if (PartyHotspotSync.controlsAssignments()) return;
        allocation.begin(client.player.getName().getString(), hotspot, now);
        if (!announced && dev.serko.safariutils.session.SessionManager.current() != null
                && dev.serko.safariutils.session.SessionManager.current().personalBestElapsed() >= 500) {
            announced = true;
            EncounterAlerts.post(SafariConfig.Broadcast.PARTY,
                ChatTemplateFormats.HOTSPOT.format(ConfigManager.get().party, "<BIOME>", hotspot.displayName()));
        }

        SafariBiome next = allocation.assignment(names, uuids, party.expectedPlayers(), now);
        if(next!=null)PartyHotspotSync.lockFallback(allocation.finalizedAssignments(), uuids);
        if (next != null && next != assigned) {
            assigned = next;
            showBanner();
            ClientMessages.sendLines(java.util.List.of(assignmentMessage(next, hotspot)));
            DebugLog.line("HOTSPOT/ASSIGN", "actual=" + hotspot + " assigned=" + next + " final=" + allocation.finished());
        }
    }
    public static void acceptFairness(java.util.Map<String, Integer> fairness) {
        if (fairness == null || fairness.size() > 4) return;
        for (var member : dev.serko.safariutils.state.PartyState.snapshot().members()) {
            int wins = fairness.getOrDefault(member.uuid(), 0);
            if (wins >= 0 && wins <= 1_000_000) hotspotWins.put(member.name().toLowerCase(java.util.Locale.ROOT), wins);
        }
    }
    public static void acceptAssignment(SafariBiome destination) {
        if (assigned != null || destination == null || !PartyHotspotSync.assignmentEligible()) return;
        assigned = destination; showBanner();
        ClientMessages.sendLines(java.util.List.of(assignmentMessage(destination, hotspot)));
        DebugLog.line("HOTSPOT/ASSIGN", "backend assigned=" + destination);
    }
    static java.util.Set<String> rosterIdentity(java.util.Set<String> names, java.util.Map<String, String> uuids) {
        java.util.Set<String> result = new java.util.HashSet<>();
        for (String name : names) {
            String uuid = uuids.get(name.toLowerCase(java.util.Locale.ROOT));
            result.add(uuid == null || uuid.isBlank() ? "name:" + name.toLowerCase(java.util.Locale.ROOT)
                : uuid.replace("-", "").toLowerCase(java.util.Locale.ROOT));
        }
        return java.util.Set.copyOf(result);
    }
    static net.minecraft.network.chat.Component assignmentMessage(SafariBiome destination, SafariBiome actual) {
        var title = net.minecraft.network.chat.Component.literal("Assigned Biome: ").withColor(StatColours.INFO)
            .append(net.minecraft.network.chat.Component.literal(destination.displayName()).withColor(destination.colour()).withStyle(net.minecraft.ChatFormatting.BOLD));
        if(actual==null)return title;
        return title.append(net.minecraft.network.chat.Component.literal("  \u2502  ").withColor(0xFFFFFF))
            .append(net.minecraft.network.chat.Component.literal("Your Hotspot: ").withColor(StatColours.INFO))
            .append(net.minecraft.network.chat.Component.literal(actual.displayName()).withColor(actual.colour()));
    }
    private static void clearAssignment() {
        if (!announced && assigned == null) return;
        allocation = new dev.serko.safariutils.state.HotspotAllocation(hotspotWins);
        assigned = null; announced = false;
    }

	private HotspotWatch() {
	}

	/** The biome that is your hotspot this run, or {@code null} before it is announced. */
	public static SafariBiome biome() {
		return hotspot;
	}

	/** Feeds one cleaned chat line. */
	public static void onChatMessage(String line) {
		if (enabled() && SafariLocation.inside()) {
            PartyReport shared = parsePartyReport(line);
            if (shared != null && (PARTY_HOTSPOT.matcher(line).matches() || (dev.serko.safariutils.session.SessionManager.current()==null
                    ? System.currentTimeMillis()-SafariLocation.insideSinceMillis()<=30000
                    : dev.serko.safariutils.session.SessionManager.current().personalBestElapsed()<=30000)) && dev.serko.safariutils.state.PartyState.snapshot().containsName(shared.name())) {
                PartyHotspotSync.report(shared.name(), shared.biome());
                allocation.report(shared.name(), shared.biome(), dev.serko.safariutils.state.MonotonicTime.nowMillis());
                return;
            }
        }
        Matcher matcher = HOTSPOT.matcher(line);
		if (!matcher.matches()) return;

		SafariBiome found = SafariBiome.fromDisplayName(matcher.group(1));
		// An unrecognised biome name means Hypixel has changed something; better to
		// leave the panel blank than to report a guess.
		if (found == null) return;
		if (hotspot == found) return;
		hotspot = found;
		announce(found);
	}

    record PartyReport(String name, SafariBiome biome) { }
    static PartyReport parsePartyReport(String line) {
        if (line == null) return null;
        Matcher matcher = PARTY_HOTSPOT.matcher(line);
        if (!matcher.matches()) {
            var manual=Pattern.compile("^Party\\s*>\\s*(?:\\[[^\\]]+]\\s*)*([A-Za-z0-9_]{1,16}):\\s*(?:HOTSPOT! Your Hunting Hotspot is the )?(Forest|fprest|for|Cavern|cav|Icy|Ice|Haunted|haunt|haunte|hvant|f|c|i|h)(?: Biome!)?$",Pattern.CASE_INSENSITIVE).matcher(line);
            if(!manual.matches())return null;
            String name=manual.group(2).toLowerCase(java.util.Locale.ROOT);
            SafariBiome biome=switch(name) {case "f","forest","fprest","for"->SafariBiome.FOREST;case "c","cavern","cav"->SafariBiome.CAVERN;case "i","ice","icy"->SafariBiome.ICY;default->SafariBiome.HAUNTED;};
            return new PartyReport(manual.group(1),biome);
        }
        return new PartyReport(matcher.group(1), SafariBiome.fromDisplayName(
            matcher.group(2).equalsIgnoreCase("Ice") ? "Icy" : matcher.group(2)));
    }

	private static void announce(SafariBiome biome) {
		SafariConfig config = ConfigManager.get();
		showBanner();
		if (!enabled() && !dev.serko.safariutils.api.PartySyncNetworks.coordinationSupported()) EncounterAlerts.post(config.party.hotspot(), ChatTemplateFormats.HOTSPOT.format(config.party,
            "<BIOME>", biome.displayName()));
	}

    private static void showBanner() {
        var notice = bannerState.poll(hotspot, assigned, enabled(),
            dev.serko.safariutils.state.MonotonicTime.nowMillis());
        if (notice != null) EncounterAlerts.fireHotspot(notice.biome(), notice.assigned());
    }

	/** A hotspot belongs to one run; the next one is told its own. */
    public static void resetConnection() {
        hotspotWins.clear(); sessionRoster = java.util.Set.of(); reset();
    }

	public static void reset() {
        PartyHotspotSync.reset();
		hotspot = null;
        bannerState.reset();
        allocation = new dev.serko.safariutils.state.HotspotAllocation(hotspotWins);
        assigned = null; announced = false;
	}
}
