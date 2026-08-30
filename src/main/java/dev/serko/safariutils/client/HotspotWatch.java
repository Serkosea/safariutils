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

	private HotspotWatch() {
	}

	/** The biome that is your hotspot this run, or {@code null} before it is announced. */
	public static SafariBiome biome() {
		return hotspot;
	}

	/** Feeds one cleaned chat line. */
	public static void onChatMessage(String line) {
		Matcher matcher = HOTSPOT.matcher(line);
		if (!matcher.matches()) return;

		SafariBiome found = SafariBiome.fromDisplayName(matcher.group(1));
		// An unrecognised biome name means Hypixel has changed something; better to
		// leave the panel blank than to report a guess.
		if (found == null) return;
		hotspot = found;
		announce(found);
	}

	private static void announce(SafariBiome biome) {
		SafariConfig config = ConfigManager.get();
		EncounterAlerts.fireHotspot(biome);
		EncounterAlerts.post(config.party.hotspot(), AlertText.format(config.party.hotspotChatText,
			"<BIOME>", biome.displayName()));
	}

	/** A hotspot belongs to one run; the next one is told its own. */
	public static void reset() {
		hotspot = null;
	}
}
