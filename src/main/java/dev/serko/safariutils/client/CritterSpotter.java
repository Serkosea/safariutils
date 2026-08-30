package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.session.SafariSession;
import dev.serko.safariutils.session.SessionManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Tallies the critters in the current {@link CritterEntities} sweep. These are nearby
 * counts, not run totals; entity IDs cannot safely extend them across ticks. Species
 * with a quota of one are capped to ignore brief capture-replacement overlap.
 */
public final class CritterSpotter {

	/** Last count logged per species, so only an actual change is written. */
	private static final Map<Critter, Integer> lastLogged = new HashMap<>();
	private static long lastScan = Long.MIN_VALUE;

	private CritterSpotter() {
	}

	public static void tick() {
		if (!ConfigManager.get().display.countSpawns) return;

		SafariSession session = SessionManager.current();
		if (session == null || !SafariLocation.inSafari()) return;
		long scan = CritterEntities.scannedAt();
		if (scan == lastScan) return;
		lastScan = scan;

		Map<Critter, Integer> present = new HashMap<>();
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (sighting.sparkling()) continue;
			present.merge(sighting.critter(), 1, Integer::sum);
		}

		boolean logging = DebugLog.isEnabled();
		if (logging) {
			for (Map.Entry<Critter, Integer> entry : present.entrySet()) {
				Integer last = lastLogged.get(entry.getKey());
				if (last != null && last.equals(entry.getValue())) continue;
				lastLogged.put(entry.getKey(), entry.getValue());
				DebugLog.line("NEARBY", entry.getKey().name() + " -> " + entry.getValue());
			}
			for (Critter critter : lastLogged.keySet()) {
				if (present.containsKey(critter)) continue;
				DebugLog.line("NEARBY", critter.name() + " -> 0");
			}
			lastLogged.keySet().retainAll(present.keySet());
		}

		// A species with a quota of exactly one — Hideyho, Wumpa, Doomspiral — never
		// legitimately has two around at once. Escaping a capsule hands a critter a new
		// entity id, and there is a brief window right at a catch where the old
		// entity and the new one both still count, so an accurate tally can show two
		// for a moment. For these three specifically that reading is never real, so it
		// is capped rather than shown.
		present.replaceAll((critter, count) -> {
			if (critter.spawnQuota() != 1 || count <= 1) return count;
			if (logging) DebugLog.line("NEARBY", "CAP " + critter.name() + " raw=" + count + " -> 1");
			return 1;
		});
		// Replaced wholesale rather than merged, so anything caught or despawned since
		// the last scan simply drops out.
		session.setNearby(present);
	}
}
