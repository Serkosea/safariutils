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
    private static dev.serko.safariutils.state.HotspotAllocation allocation = new dev.serko.safariutils.state.HotspotAllocation();
    private static SafariBiome assigned;
    private static boolean announced;
    private static long rosterGeneration = -1;
    private static final Pattern PARTY_HOTSPOT = Pattern.compile(
        "^Party\\s*>\\s*(?:\\[[^\\]]+]\\s*)*([A-Za-z0-9_]{1,16}):\\s*\\[SM] Hotspot: (Forest|Cavern|Icy|Haunted)$");

    public static SafariBiome assignedBiome() { return enabled() ? assigned : null; }
    private static boolean enabled() {
        var settings = ConfigManager.get().gameplay;
        return settings.assignHotspots || settings.mutualsCompatibility;
    }
    public static void tick() {
        if (!SafariLocation.inside() || !enabled()) {
            clearAssignment();
            return;
        }
        if (hotspot == null) return;
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.player == null) return;
        var party = dev.serko.safariutils.state.PartyState.snapshot();
        if (!party.known()) return;
        if (rosterGeneration >= 0 && rosterGeneration != party.generation()) {
            // Do not reuse another party's reports or spam a new report mid-run.
            allocation = new dev.serko.safariutils.state.HotspotAllocation(); assigned = null;
        }
        rosterGeneration = party.generation();
        long now = dev.serko.safariutils.state.MonotonicTime.nowMillis();
        allocation.begin(client.player.getName().getString(), hotspot, now);
        if (!announced) {
            announced = true;
            EncounterAlerts.post(SafariConfig.Broadcast.PARTY,
                ChatTemplateFormats.HOTSPOT.format(ConfigManager.get().party, "<BIOME>", hotspot.displayName()));
        }
        if (allocation.finished()) return;
        java.util.Set<String> names = new java.util.HashSet<>();
        java.util.Map<String, String> uuids = new java.util.HashMap<>();
        for (var member : party.members()) {
            String name = member.name().toLowerCase(java.util.Locale.ROOT);
            names.add(name); if (!member.uuid().isEmpty()) uuids.put(name, member.uuid());
        }
        String self = client.player.getName().getString().toLowerCase(java.util.Locale.ROOT);
        names.add(self); uuids.put(self, client.player.getUUID().toString());
        SafariBiome next = allocation.assignment(names, uuids, party.expectedPlayers(), now);
        if (next != null && next != assigned) {
            assigned = next;
            ClientMessages.send("Assigned Biome: " + next.displayName() + " (Hotspot: " + hotspot.displayName() + ")", ClientMessages.Tone.INFO);
            DebugLog.line("HOTSPOT/ASSIGN", "actual=" + hotspot + " assigned=" + next + " final=" + allocation.finished());
        }
    }
    private static void clearAssignment() {
        if (!announced && assigned == null && rosterGeneration == -1) return;
        allocation = new dev.serko.safariutils.state.HotspotAllocation();
        assigned = null; announced = false; rosterGeneration = -1;
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
            Matcher shared = PARTY_HOTSPOT.matcher(line);
            if (shared.matches() && dev.serko.safariutils.state.PartyState.snapshot().containsName(shared.group(1))) {
                allocation.report(shared.group(1), SafariBiome.fromDisplayName(shared.group(2)),
                    dev.serko.safariutils.state.MonotonicTime.nowMillis());
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

	private static void announce(SafariBiome biome) {
		SafariConfig config = ConfigManager.get();
		EncounterAlerts.fireHotspot(biome);
		if (!enabled()) EncounterAlerts.post(config.party.hotspot(), ChatTemplateFormats.HOTSPOT.format(config.party,
            "<BIOME>", biome.displayName()));
	}

	/** A hotspot belongs to one run; the next one is told its own. */
	public static void reset() {
		hotspot = null;
        allocation = new dev.serko.safariutils.state.HotspotAllocation();
        assigned = null; announced = false; rosterGeneration = -1;
	}
}
