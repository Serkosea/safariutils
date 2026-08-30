package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.SafariBiome;

/** Detects, alerts, and retains Sparkling critters until an authoritative catch. */
public final class SparklingWatch {
	private static final long CAUGHT_THEME_MILLIS = 2_500L;
	private static final long REPLACEMENT_GRACE_MILLIS = 10_000L;
	private static final double REPLACEMENT_DISTANCE_SQ = 12.0 * 12.0;

	/** Labels already called out, so it announces once rather than every sweep. */
	private static final Set<UUID> announcedLabels = new HashSet<>();
	private static final Set<UUID> announcedBodies = new HashSet<>();
	/** Finds whose chat line has been sent after the critter became genuinely visible. */
	private static final Set<UUID> chatAnnounced = new HashSet<>();
	/** Detected individuals retained until an authoritative catch removes each one. */
	private static final Map<UUID, Outstanding> outstanding = new LinkedHashMap<>();
	/** Suppresses labels lingering or appearing briefly after an authoritative catch. */
	private static final Map<Critter, Long> justCaught = new LinkedHashMap<>();
	/** A throw/breakout commonly replaces the same critter's entity IDs. */
	private static final Map<Critter, Long> replacementExpectedUntil = new LinkedHashMap<>();
	private static long caughtThemeUntil;
	private static long lastScan = Long.MIN_VALUE;
	private record Outstanding(Critter critter, BlockPos pos) {}

	private SparklingWatch() {
	}

	public static void tick() {
		long now = System.currentTimeMillis();
		justCaught.entrySet().removeIf(entry -> now - entry.getValue() > CAUGHT_THEME_MILLIS);
		replacementExpectedUntil.entrySet().removeIf(entry -> now > entry.getValue());
		long scan = CritterEntities.scannedAt();
		if (scan == lastScan) return;
		lastScan = scan;
		Set<UUID> visibleKeys = new HashSet<>();
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (sighting.sparkling()) visibleKeys.add(keyOf(sighting));
		}
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (!sighting.sparkling()) continue;
			if (SafeMode.sparklingCritters()) {
				boolean mobVisible = sighting.mob() != null && VisibilityCheck.canSee(sighting.mob());
				boolean labelVisible = sighting.label().isCustomNameVisible()
					&& VisibilityCheck.canSee(sighting.label());
				boolean hiddenSafe = SafeMode.hiddenCritter(sighting.critter(), true);
				// A dormant hidden species has no player-visible name tag. Its internal
				// label must not reveal a Sparkling through terrain before the body itself
				// is in direct view.
				if (hiddenSafe ? !mobVisible : !labelVisible && !mobVisible) continue;
			}
			// Keyed on the label rather than the mob: the label is what named it, and it
			// is the entity that survives the pairing being ambiguous.
			UUID labelId = sighting.label().getUUID();
			UUID bodyId = sighting.mob() == null ? null : sighting.mob().getUUID();
			UUID key = keyOf(sighting);
			BlockPos pos = sighting.body().blockPosition();
			if (outstanding.containsKey(key)) {
				outstanding.put(key, new Outstanding(sighting.critter(), pos));
				announcedLabels.add(labelId);
				if (bodyId != null) announcedBodies.add(bodyId);
				postVisibleChat(sighting, key);
				continue;
			}
			boolean knownId = announcedLabels.contains(labelId)
				|| bodyId != null && announcedBodies.contains(bodyId);
			announcedLabels.add(labelId);
			if (bodyId != null) announcedBodies.add(bodyId);
			Long caughtAt = justCaught.get(sighting.critter());
			if (caughtAt != null && now - caughtAt <= CAUGHT_THEME_MILLIS) continue;
			UUID replacement = nearestReplacement(sighting.critter(), pos, visibleKeys,
				replacementExpectedUntil.containsKey(sighting.critter()) || knownId);
			if (replacement != null) {
				boolean chatWasSent = chatAnnounced.remove(replacement);
				outstanding.remove(replacement);
				outstanding.put(key, new Outstanding(sighting.critter(), pos));
				if (chatWasSent) chatAnnounced.add(key);
				replacementExpectedUntil.remove(sighting.critter());
				postVisibleChat(sighting, key);
				continue;
			}
			if (knownId) continue;
			outstanding.put(key, new Outstanding(sighting.critter(), pos));
			EncounterAlerts.fireSparklingDetected(sighting.critter().name());
			postVisibleChat(sighting, key);
		}
	}

	private static UUID keyOf(CritterEntities.Sighting sighting) {
		return sighting.mob() == null ? sighting.label().getUUID() : sighting.mob().getUUID();
	}

	/** Finds the same nearby individual after Hypixel replaces its IDs during a throw. */
	private static UUID nearestReplacement(Critter critter, BlockPos pos,
			Set<UUID> visibleKeys, boolean replacementExpected) {
		UUID best = null;
		double bestDistance = REPLACEMENT_DISTANCE_SQ;
		for (Map.Entry<UUID, Outstanding> entry : outstanding.entrySet()) {
			if (entry.getValue().critter() != critter) continue;
			// Without a throw/known ID, preserve two genuinely simultaneous Sparklings.
			if (!replacementExpected && visibleKeys.contains(entry.getKey())) continue;
			double distance = entry.getValue().pos().distSqr(pos);
			if (distance > bestDistance) continue;
			best = entry.getKey();
			bestDistance = distance;
		}
		return best;
	}

	/** Marks the next nearby ID for this species as a breakout replacement, not a find. */
	public static void onCaptureInteraction(Critter critter) {
		if (critter == null || outstanding.values().stream()
			.noneMatch(entry -> entry.critter() == critter)) return;
		replacementExpectedUntil.put(critter,
			System.currentTimeMillis() + REPLACEMENT_GRACE_MILLIS);
	}

	/** Sends public chat only after the named critter itself is visible on screen. */
	private static void postVisibleChat(CritterEntities.Sighting sighting, UUID key) {
		if (chatAnnounced.contains(key) || sighting.critter().biome() != SafariLocation.biome()) return;
		boolean visible = sighting.label().isCustomNameVisible()
			&& VisibilityCheck.canSee(sighting.label())
			|| sighting.mob() != null && VisibilityCheck.canSee(sighting.mob());
		if (!visible) return;
		SafariConfig config = ConfigManager.get();
		chatAnnounced.add(key);
		EncounterAlerts.post(config.sparkling.detected(),
			AlertText.format(config.sparkling.sparklingDetectedChatText,
				"<CRITTER>", sighting.critter().name()));
	}

	/** Sends the lifetime-aware catch line through its independently selected chat. */
	public static void postCaught(String message) {
		EncounterAlerts.postDelayed(ConfigManager.get().sparkling.caught(), message, 250);
	}

	/** Keeps the special HUD frame briefly after the detected critter is caught. */
	public static void onCaught(Critter critter) {
		outstanding.entrySet().stream()
			.filter(entry -> entry.getValue().critter() == critter)
			.map(Map.Entry::getKey)
			.findFirst()
			.ifPresent(outstanding::remove);
		justCaught.put(critter, System.currentTimeMillis());
		replacementExpectedUntil.remove(critter);
		caughtThemeUntil = System.currentTimeMillis() + CAUGHT_THEME_MILLIS;
		FullScreenAlert.show("SPARKLING!", critter.name(), null, FullScreenAlert.SPARKLING);
	}

	/** Whether every visible HUD should use the shared Sparkling presentation. */
	public static boolean hudThemeActive() {
		return !outstanding.isEmpty() || System.currentTimeMillis() < caughtThemeUntil;
	}

	/** Kept as an alias for callers concerned specifically with the Missing HUD. */
	public static boolean missingHudThemeActive() {
		return hudThemeActive();
	}

	public static Map<Critter, Integer> outstandingCounts(SafariBiome biome) {
		Map<Critter, Integer> counts = new LinkedHashMap<>();
		outstanding.values().stream().map(Outstanding::critter)
			.filter(critter -> critter.biome() == biome)
			.forEach(critter -> counts.merge(critter, 1, Integer::sum));
		return counts;
	}

	/** How far the alerted critter is, for the player's own line. Unused when none. */
	public static double distanceTo(BlockPos pos) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return -1;
		return Math.sqrt(client.player.position()
			.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
	}

	/** A new run has its own sparklings; the last one's are gone. */
	public static void reset() {
		announcedLabels.clear();
		announcedBodies.clear();
		chatAnnounced.clear();
		outstanding.clear();
		justCaught.clear();
		replacementExpectedUntil.clear();
		caughtThemeUntil = 0;
		lastScan = Long.MIN_VALUE;
		FullScreenAlert.clear();
	}
}
