package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;

import java.util.HashMap;
import java.util.Map;

/** Emits optional per-run concurrent species peaks into the ordinary debug log. */
public final class CritterCountLog {
	private static final Map<Critter, Integer> peak = new HashMap<>();
	private static long lastScan = Long.MIN_VALUE;

	private CritterCountLog() {
	}

	/** Records new peaks only when the debug category is enabled. */
	public static void tick() {
		boolean enabled = DebugLog.isEnabled() && ConfigManager.get().advanced.logCritterCounts;
		if (!enabled || !SafariLocation.inSafari()) return;
		long scan = CritterEntities.scannedAt();
		if (scan == lastScan) return;
		lastScan = scan;

		Map<Critter, Integer> present = new HashMap<>();
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			present.merge(sighting.critter(), 1, Integer::sum);
		}
		for (Map.Entry<Critter, Integer> entry : present.entrySet()) {
			Integer current = peak.get(entry.getKey());
			if (current != null && entry.getValue() <= current) continue;
			peak.put(entry.getKey(), entry.getValue());
			DebugLog.line("COUNT", entry.getKey().name() + " new peak: " + entry.getValue());
		}
	}

	/** A new run resets what counts as a peak, in memory — the file itself keeps every run's. */
	public static void reset() {
		peak.clear();
		lastScan = Long.MIN_VALUE;
		DebugLog.line("COUNT", "new run");
	}
}
