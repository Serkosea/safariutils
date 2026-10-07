package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
	/** Finds whose user-facing alerts have fired after genuine visual confirmation. */
	private static final Set<UUID> visuallyAnnounced = new HashSet<>();
	/** Detected individuals retained until an authoritative catch removes each one. */
	private static final Map<UUID, Outstanding> outstanding = new LinkedHashMap<>();
	/** Suppresses labels lingering or appearing briefly after an authoritative catch. */
	private static final Map<Critter, Long> justCaught = new LinkedHashMap<>();
	/** A throw/breakout commonly replaces the same critter's entity IDs. */
	private static final Map<Critter, Long> replacementExpectedUntil = new LinkedHashMap<>();
	private static long caughtThemeUntil;
	private static long lastScan = Long.MIN_VALUE;
	private static long lastConfigRevision = Long.MIN_VALUE;
	private record Outstanding(Critter critter, BlockPos pos, boolean visiblyConfirmed) {}

	private SparklingWatch() {
	}

	/** Intentionally runs for the whole Safari visit, including before ticket use. */
	public static void tick() {
		long now = System.currentTimeMillis();
		justCaught.entrySet().removeIf(entry -> now - entry.getValue() > CAUGHT_THEME_MILLIS);
		replacementExpectedUntil.entrySet().removeIf(entry -> now > entry.getValue());
		long scan = CritterEntities.scannedAt();
		long configRevision = ConfigManager.revision();
		if (scan == lastScan && configRevision == lastConfigRevision) return;
		lastScan = scan;
		lastConfigRevision = configRevision;
		Set<UUID> visibleKeys = new HashSet<>();
		Map<Critter, CritterEntities.Sighting> bestBySpecies = new LinkedHashMap<>();
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (!isSparkling(sighting)) continue;
			bestBySpecies.merge(sighting.critter(), sighting,
				(first, second) -> sightingScore(second) > sightingScore(first) ? second : first);
		}
		List<CritterEntities.Sighting> sparklingSightings = new ArrayList<>(bestBySpecies.values());
		for (CritterEntities.Sighting sighting : sparklingSightings) visibleKeys.add(keyOf(sighting));
		for (CritterEntities.Sighting sighting : sparklingSightings) {
			boolean visiblyConfirmed = visuallyConfirmed(sighting);
			if (SafeMode.sparklingCritters()) {
				// A dormant hidden species has no player-visible name tag. Its internal
				// label must not reveal a Sparkling through terrain before the body itself
				// is in direct view.
				if (!visiblyConfirmed) continue;
			}
			// Keyed on the label rather than the mob: the label is what named it, and it
			// is the entity that survives the pairing being ambiguous.
			UUID labelId = sighting.label().getUUID();
			UUID bodyId = sighting.mob() == null ? null : sighting.mob().getUUID();
			UUID key = keyOf(sighting);
			BlockPos pos = sighting.body().blockPosition();
			if (outstanding.containsKey(key)) {
				Outstanding previous = outstanding.get(key);
				outstanding.put(key, new Outstanding(sighting.critter(), pos,
					visiblyConfirmed || previous.visiblyConfirmed()));
				announcedLabels.add(labelId);
				if (bodyId != null) announcedBodies.add(bodyId);
				postVisibleFoundAlerts(sighting, key);
				continue;
			}
			boolean knownId = announcedLabels.contains(labelId)
				|| bodyId != null && announcedBodies.contains(bodyId);
			announcedLabels.add(labelId);
			if (bodyId != null) announcedBodies.add(bodyId);
			Long caughtAt = justCaught.get(sighting.critter());
			if (caughtAt != null && now - caughtAt <= CAUGHT_THEME_MILLIS) continue;
			// Hypixel can recreate a critter's label and body several times through one
			// capture. A second Sparkling of the same species in one run is not a real
			// Safari state, so fold every new identity into that species' existing find.
			UUID replacement = outstandingFor(sighting.critter());
			if (replacement == null) {
				replacement = nearestReplacement(sighting.critter(), pos, visibleKeys,
					replacementExpectedUntil.containsKey(sighting.critter()) || knownId);
			}
			if (replacement != null) {
				boolean alertsWereSent = visuallyAnnounced.remove(replacement);
				Outstanding previous = outstanding.remove(replacement);
				outstanding.put(key, new Outstanding(sighting.critter(), pos,
					visiblyConfirmed || previous != null && previous.visiblyConfirmed()));
				if (alertsWereSent) visuallyAnnounced.add(key);
				replacementExpectedUntil.remove(sighting.critter());
				DebugLog.line("SPARKLING", "replacement " + sighting.critter().name()
					+ " old=" + shortId(replacement) + " new=" + shortId(key)
					+ " pos=" + pos(pos));
				postVisibleFoundAlerts(sighting, key);
				continue;
			}
			if (knownId) continue;
			outstanding.put(key, new Outstanding(sighting.critter(), pos, visiblyConfirmed));
			DebugLog.line("SPARKLING", "found " + sighting.critter().name()
				+ " label=" + shortId(labelId) + " body=" + shortId(bodyId)
				+ " key=" + shortId(key) + " pos=" + pos(pos)
				+ " source=" + ParticleDiagnostics.source(sighting));
			TicketTrading.onSparklingDetected(sighting.critter());
			postVisibleFoundAlerts(sighting, key);
		}
	}

	/** Direct evidence retained independently from whichever mode is currently displayed. */
	private static boolean visuallyConfirmed(CritterEntities.Sighting sighting) {
		if ("Hideyho".equals(sighting.critter().name())) {
			return VisibilityCheck.canSee(sighting.label());
		}
		boolean mobVisible = bodyVisible(sighting);
		return SafeMode.hiddenSpecies(sighting.critter())
			? mobVisible : mobVisible || VisibilityCheck.canSeeVisibleName(sighting.label());
	}

	/** Duplico's body sits inside its disguise, so its prop-aware sight test is required. */
	private static boolean bodyVisible(CritterEntities.Sighting sighting) {
		if (sighting.mob() == null) return false;
		return "Duplico".equals(sighting.critter().name())
			? VisibilityCheck.canSeeDecoratedEntity(sighting.mob())
			: VisibilityCheck.canSee(sighting.mob());
	}

	private static boolean presentable(Outstanding entry) {
		return !SafeMode.sparklingCritters() || entry.visiblyConfirmed();
	}

	static UUID keyOf(CritterEntities.Sighting sighting) {
		return sighting.mob() == null ? sighting.label().getUUID() : sighting.mob().getUUID();
	}

	/** Sparkling status from the name tag, or from repeated matching particle packets. */
	static boolean isSparkling(CritterEntities.Sighting sighting) {
		return sighting != null && (sighting.sparkling() || ParticleDiagnostics.confirms(sighting));
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

	private static UUID outstandingFor(Critter critter) {
		return outstanding.entrySet().stream()
			.filter(entry -> entry.getValue().critter() == critter)
			.map(Map.Entry::getKey)
			.findFirst()
			.orElse(null);
	}

	/** Prefers a real, non-capture body over the duplicate labels used during transitions. */
	private static int sightingScore(CritterEntities.Sighting sighting) {
		boolean captureScaffolding = CritterEntities.isCaptureScaffolding(sighting);
		int score = !captureScaffolding && outstanding.containsKey(keyOf(sighting)) ? 100 : 0;
		if (!captureScaffolding) score += 40;
		else score -= 100;
		// Repeating particles follow the actual moving critter and therefore outweigh
		// an older nearest-body pairing when several same-species mobs overlap.
		if (ParticleDiagnostics.confirms(sighting)) score += 400;
		else if (sighting.sparkling()) score += 150;
		if (sighting.mob() != null) score += 20;
		if (sighting.mob() != null && CritterEntities.isVerifiedBody(sighting.critter(), sighting.mob())) {
			score += 10;
		}
		if (visuallyConfirmed(sighting)) score += 5;
		return score;
	}

	/** Marks the next nearby ID for this species as a breakout replacement, not a find. */
	public static void onCaptureInteraction(Critter critter) {
		if (critter == null || outstanding.values().stream()
			.noneMatch(entry -> entry.critter() == critter)) return;
		replacementExpectedUntil.put(critter,
			System.currentTimeMillis() + REPLACEMENT_GRACE_MILLIS);
	}

	/** A bodyless label is trustworthy except while Hypixel is replacing it with a capsule. */
	static boolean provisionalMarkerAllowed(Critter critter) {
		return critter != null && !replacementExpectedUntil.containsKey(critter);
	}

	/** Safe Mode waits for sight; Extra announces the first trustworthy remote detection. */
	private static void postVisibleFoundAlerts(CritterEntities.Sighting sighting, UUID key) {
		if (visuallyAnnounced.contains(key)
				|| sighting.critter().biome() != SafariLocation.biome()) return;
		boolean visible = bodyVisible(sighting)
			|| !SafeMode.hiddenSpecies(sighting.critter())
				&& VisibilityCheck.canSeeVisibleName(sighting.label())
			// Hideyho arrives as the named player entity itself rather than a separate
			// visible-name label/body pair.
			|| "Hideyho".equals(sighting.critter().name())
				&& VisibilityCheck.canSee(sighting.label());
		if (SafeMode.sparklingCritters() && !visible) return;
		SafariConfig config = ConfigManager.get();
		visuallyAnnounced.add(key);
		DebugLog.line("SPARKLING", (visible ? "visually announced " : "remotely announced ")
			+ sighting.critter().name()
			+ " key=" + shortId(key) + " biome=" + SafariLocation.biome());
		EncounterAlerts.fireSparklingDetected(sighting.critter().name());
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
		List<UUID> removed = outstanding.entrySet().stream()
			.filter(entry -> entry.getValue().critter() == critter)
			.map(Map.Entry::getKey)
			.toList();
		removed.forEach(outstanding::remove);
		DebugLog.line("SPARKLING", "caught " + critter.name() + " removed=" + shortId(removed)
			+ " remaining=" + outstanding.size());
		justCaught.put(critter, System.currentTimeMillis());
		replacementExpectedUntil.remove(critter);
		ParticleDiagnostics.onCaught(critter);
		caughtThemeUntil = System.currentTimeMillis() + CAUGHT_THEME_MILLIS;
		FullScreenAlert.show("SPARKLING!", critter.name(), null, FullScreenAlert.SPARKLING);
	}

	/** Whether every visible HUD should use the shared Sparkling presentation. */
	public static boolean hudThemeActive() {
		return outstanding.values().stream().anyMatch(SparklingWatch::presentable)
			|| System.currentTimeMillis() < caughtThemeUntil;
	}

	/** Kept as an alias for callers concerned specifically with the Missing HUD. */
	public static boolean missingHudThemeActive() {
		return hudThemeActive();
	}

	public static Map<Critter, Integer> outstandingCounts(SafariBiome biome) {
		Map<Critter, Integer> counts = new LinkedHashMap<>();
		outstanding.values().stream().filter(SparklingWatch::presentable).map(Outstanding::critter)
			.filter(critter -> critter.biome() == biome)
			.forEach(critter -> counts.merge(critter, 1, Integer::sum));
		return counts;
	}

	/** Whether this live sighting belongs to a Sparkling that has been announced but not caught. */
	static boolean isOutstanding(CritterEntities.Sighting sighting) {
		Outstanding entry = outstanding.get(keyOf(sighting));
		return isSparkling(sighting) && entry != null && presentable(entry);
	}

	/** Sparkling styling is active only while this individual remains outstanding. */
	static boolean presentsAsSparkling(CritterEntities.Sighting sighting) {
		return isOutstanding(sighting);
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
		visuallyAnnounced.clear();
		outstanding.clear();
		justCaught.clear();
		replacementExpectedUntil.clear();
		ParticleDiagnostics.reset();
		caughtThemeUntil = 0;
		lastScan = Long.MIN_VALUE;
		lastConfigRevision = Long.MIN_VALUE;
		FullScreenAlert.clear();
	}

	private static String shortId(UUID id) {
		return id == null ? "none" : id.toString().substring(0, 8);
	}

	private static String shortId(List<UUID> ids) {
		return ids.isEmpty() ? "none" : ids.stream().map(SparklingWatch::shortId)
			.reduce((first, second) -> first + "," + second).orElse("none");
	}

	private static String pos(BlockPos pos) {
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}
}
