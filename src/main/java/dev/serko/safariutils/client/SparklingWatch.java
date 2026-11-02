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


	/** Labels already called out, so it announces once rather than every sweep. */
	private static final Set<UUID> announcedLabels = new HashSet<>();
	private static final Set<UUID> announcedBodies = new HashSet<>();
	/** Exact label ownership survives temporary loss/re-pairing of a body. */
	private static final Map<UUID, UUID> individualByLabel = new LinkedHashMap<>();
	private static final Set<UUID> deferredCapsuleLabels = new HashSet<>();
	private static final Map<UUID, Long> pendingBodylessLabels = new LinkedHashMap<>();
	/** Finds whose user-facing alerts have fired after genuine visual confirmation. */
	private static final Set<UUID> visuallyAnnounced = new HashSet<>();
	/** Detected individuals retained until an authoritative catch removes each one. */
	private static final Map<UUID, Outstanding> outstanding = new LinkedHashMap<>();
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
		// The shared entity scan can still contain the last visit until its next sweep.
		if (!SafariLocation.inside()) return;
		long now = dev.serko.safariutils.state.MonotonicTime.nowMillis();
		replacementExpectedUntil.entrySet().removeIf(entry -> now > entry.getValue());
		long scan = CritterEntities.scanRevision();
		long configRevision = ConfigManager.revision();
		if (scan == lastScan && configRevision == lastConfigRevision) return;
		lastScan = scan;
		lastConfigRevision = configRevision;
		Set<UUID> visibleKeys = new HashSet<>();
		Map<UUID, CritterEntities.Sighting> bestByIndividual = new LinkedHashMap<>();
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (!isSparkling(sighting)) continue;
			if (CritterEntities.isRemoteCaptureLabel(sighting)) {
				if (deferredCapsuleLabels.add(sighting.label().getUUID()) && DebugLog.isEnabled())
					DebugLog.line("SPARKLING", "deferred capsule label " + sighting.critter().name()
						+ " label=" + shortId(sighting.label().getUUID()));
				continue;
			}
			if (sighting.mob() == null) {
				UUID label = sighting.label().getUUID();
				long firstSeen = pendingBodylessLabels.computeIfAbsent(label, ignored -> now);
				// Metadata and the item display can arrive in different packets/scans.
				// Once recognized, a capsule-only label stays suppressed until a real body returns.
				if (!bodylessReady(deferredCapsuleLabels.contains(label), now - firstSeen)) continue;
			} else pendingBodylessLabels.remove(sighting.label().getUUID());
			if (CritterEntities.isCaptureScaffolding(sighting)) continue;
			bestByIndividual.merge(keyOf(sighting), sighting,
				(first, second) -> sightingScore(second) > sightingScore(first) ? second : first);
		}
		pendingBodylessLabels.keySet().removeIf(label -> CritterEntities.sightingFor(label) == null);
		List<CritterEntities.Sighting> sparklingSightings = new ArrayList<>(bestByIndividual.values());
		for (CritterEntities.Sighting sighting : sparklingSightings) visibleKeys.add(keyOf(sighting));
		for (CritterEntities.Sighting sighting : sparklingSightings) {
			boolean visiblyConfirmed = visuallyConfirmed(sighting);
			if (SafeMode.sparklingCritters()) {
				// A dormant hidden species has no player-visible name tag. Its internal
				// label must not reveal a Sparkling through terrain before the body itself
				// is in direct view.
				if (!visiblyConfirmed) continue;
			}
			// Keep exact label ownership as the body appears or temporarily disappears.
			UUID labelId = sighting.label().getUUID();
			UUID bodyId = sighting.mob() == null ? null : sighting.mob().getUUID();
			UUID key = keyOf(sighting);
			BlockPos pos = sighting.body().blockPosition();
			if (outstanding.containsKey(key)) {
				Outstanding previous = outstanding.get(key);
				outstanding.put(key, new Outstanding(sighting.critter(), pos,
					visiblyConfirmed || previous.visiblyConfirmed()));
				announcedLabels.add(labelId);
				individualByLabel.put(labelId, key);
				if (bodyId != null) announcedBodies.add(bodyId);
				postVisibleFoundAlerts(sighting, key);
				continue;
			}
			boolean knownId = announcedLabels.contains(labelId)
				|| bodyId != null && announcedBodies.contains(bodyId);
			announcedLabels.add(labelId);
			if (bodyId != null) announcedBodies.add(bodyId);
            // Body replacements are individual transitions; simultaneous visible bodies never merge.
            UUID replacement = labelReplacement(sighting.critter(), labelId, key, visibleKeys);
			if (replacement != null) {
				boolean alertsWereSent = visuallyAnnounced.remove(replacement);
				Outstanding previous = outstanding.remove(replacement);
				outstanding.put(key, new Outstanding(sighting.critter(), pos,
					visiblyConfirmed || previous != null && previous.visiblyConfirmed()));
				if (alertsWereSent) visuallyAnnounced.add(key);
				individualByLabel.replaceAll((label, owner) -> owner.equals(replacement) ? key : owner);
				individualByLabel.put(labelId, key);
				replacementExpectedUntil.remove(sighting.critter());
				DebugLog.line("SPARKLING", "replacement " + sighting.critter().name()
					+ " old=" + shortId(replacement) + " new=" + shortId(key)
					+ " pos=" + pos(pos));
				postVisibleFoundAlerts(sighting, key);
				continue;
			}
			if (knownId) continue;
			individualByLabel.put(labelId, key);
			outstanding.put(key, new Outstanding(sighting.critter(), pos, visiblyConfirmed));
			DebugLog.line("SPARKLING", "found " + sighting.critter().name()
				+ " label=" + shortId(labelId) + " body=" + shortId(bodyId)
				+ " key=" + shortId(key) + " pos=" + pos(pos)
				+ " source=" + ParticleDiagnostics.source(sighting));
			PartySparklingDiscoveries.discovered(sighting.critter());
			postVisibleFoundAlerts(sighting, key);
		}
	}

	static boolean bodylessReady(boolean knownCapsule, long ageMillis) {
		return !knownCapsule && ageMillis >= 500;
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

	/** Exact retained label evidence only; proximity cannot choose between peers. */
	private static UUID labelReplacement(Critter critter, UUID label, UUID key, Set<UUID> visibleKeys) {
		UUID owner = individualByLabel.get(label);
		Outstanding previous = outstanding.get(owner);
		return previous != null && previous.critter() == critter && !owner.equals(key)
			&& !visibleKeys.contains(owner) ? owner : null;
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

	/** Suppresses provisional bodyless markers during a locally observed capture transition. */
	public static void onCaptureInteraction(Critter critter) {
		if (critter == null || outstanding.values().stream()
			.noneMatch(entry -> entry.critter() == critter)) return;
		replacementExpectedUntil.put(critter,
			dev.serko.safariutils.state.MonotonicTime.nowMillis() + REPLACEMENT_GRACE_MILLIS);
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
		var position = sighting.body().position();
		EncounterAlerts.fireSparklingDetected(sighting.critter().name(), position);
		EncounterAlerts.post(config.sparkling.detected(),
			AlertText.spawn(config.sparkling.sparklingDetectedChatText, sighting.critter().name(), position));
	}

	/** Sends the lifetime-aware catch line through its independently selected chat. */
	public static void postCaught(String message) {
		EncounterAlerts.postDelayed(ConfigManager.get().sparkling.caught(), message, 250);
	}

	/** Keeps the special HUD frame briefly after the detected critter is caught. */
	public static void onCaught(Critter critter) { onCaught(critter, false); }

    public static void onCaught(Critter critter, boolean teammateCatch) {
        // Shared catches never resolve a local capsule attempt. Consume only an
        // unambiguous individual; do not guess ownership among same-species peers.
        if (teammateCatch) resolveSharedCatch(critter);
        // Global chat names a species, not an individual. CaptureEffects owns exact body removal.
        // Hideyho is the single-spawn interaction-only exception with no capsule resolution.
        if ("Hideyho".equals(critter.name()))
            outstanding.entrySet().removeIf(entry -> entry.getValue().critter() == critter);
        if (outstanding.values().stream().noneMatch(entry -> entry.critter() == critter))
            PartySparklingDiscoveries.caught(critter);
        DebugLog.line("SPARKLING", "caught announcement " + critter.name() + " remaining=" + outstanding.size());
		replacementExpectedUntil.remove(critter);
		caughtThemeUntil = dev.serko.safariutils.state.MonotonicTime.nowMillis() + CAUGHT_THEME_MILLIS;
		FullScreenAlert.show("SPARKLING!", critter.name(), null, FullScreenAlert.SPARKLING);
	}

    private static void resolveSharedCatch(Critter species) {
        UUID only = null;
        for (var entry : outstanding.entrySet()) {
            if (entry.getValue().critter() != species) continue;
            if (only != null) {
                DebugLog.line("SPARKLING", "shared catch identity ambiguous " + species.name());
                return;
            }
            only = entry.getKey();
        }
        if (only != null) onCaptureResolved(species, only, only, CritterState.Outcome.CAUGHT);
    }

	/** Whether every visible HUD should use the shared Sparkling presentation. */
    static void onBodyReplaced(Critter species, UUID before, UUID after) {
        if (before == null || after == null || before.equals(after)) return;
        Outstanding previous=outstanding.get(before);
        if(previous==null || previous.critter()!=species) return;
        outstanding.remove(before);
        outstanding.putIfAbsent(after,previous);
        announcedBodies.add(after);
        individualByLabel.replaceAll((label, owner) -> owner.equals(before) ? after : owner);
        if(visuallyAnnounced.remove(before)) visuallyAnnounced.add(after);
    }

    static void onCaptureResolved(Critter species, UUID before, UUID after, CritterState.Outcome outcome) {
        if(outcome!=CritterState.Outcome.CAUGHT) return;
        boolean removed=false;
        for(UUID id:new UUID[]{before,after}) {
            Outstanding entry=outstanding.get(id);
            if(entry!=null && entry.critter()==species) {
                outstanding.remove(id); visuallyAnnounced.remove(id); announcedBodies.add(id); removed=true;
            }
        }
        if(removed) {
            DebugLog.line("SPARKLING", "resolved individual " + species.name()+" body="+shortId(before)+" remaining="+outstanding.size());
            if(outstanding.values().stream().noneMatch(entry -> entry.critter()==species))
                PartySparklingDiscoveries.caught(species);
        }
    }

	public static boolean hudThemeActive() {
		return SafariLocation.inside() && (PartySparklingDiscoveries.sharedMask() != 0
			|| outstanding.values().stream().anyMatch(SparklingWatch::presentable)
			|| dev.serko.safariutils.state.MonotonicTime.nowMillis() < caughtThemeUntil);
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
		long shared = PartySparklingDiscoveries.sharedMask();
		dev.serko.safariutils.data.Critters.selectionOrder().stream()
			.filter(c -> c.biome() == biome && (shared & dev.serko.safariutils.data.Critters.selectionMask(c)) != 0)
			.forEach(c -> counts.putIfAbsent(c, 1));
		return counts;
	}

	/** Single-species objective checks must not allocate a biome map on every marker. */
	static int outstandingCount(Critter critter) {
		int count = 0;
		for (Outstanding entry : outstanding.values())
			if (entry.critter().equals(critter) && presentable(entry)) count++;
		if (count == 0 && (PartySparklingDiscoveries.sharedMask()
			& dev.serko.safariutils.data.Critters.selectionMask(critter)) != 0) return 1;
		return count;
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

    /** Headless lifecycle regression: same species, distinct bodies, and exact replacement ownership. */
    static void verifyIndividualTracking() {
        Critter species=dev.serko.safariutils.data.Critters.byName("Driftling");
        UUID first=new UUID(1,1),second=new UUID(1,2),replacement=new UUID(1,3);
        try {
            outstanding.put(first,new Outstanding(species,BlockPos.ZERO,true));
            outstanding.put(second,new Outstanding(species,new BlockPos(1,0,0),true));
            UUID label = new UUID(2,1);
            individualByLabel.put(label, first);
            if(labelReplacement(species,label,replacement,Set.of(first,second))!=null)
                throw new AssertionError("Simultaneous Sparkling bodies merged");
            if(!first.equals(labelReplacement(species,label,replacement,Set.of(second))))
                throw new AssertionError("Exact label lost replacement ownership");
            if(labelReplacement(species,new UUID(2,2),replacement,Set.of(second))!=null)
                throw new AssertionError("Unrelated label stole Sparkling identity");
            if(bodylessReady(false,0) || bodylessReady(false,499) || !bodylessReady(false,500)
                    || bodylessReady(true,10000))
                throw new AssertionError("Capsule metadata ordering can create a fresh Sparkling");
            if(!CritterEntities.capsuleLabelOffset(0,1.5,0)
                    || CritterEntities.capsuleLabelOffset(2,1.5,0))
                throw new AssertionError("Remote capsule label bounds");
            resolveSharedCatch(species);
            if(outstanding.size()!=2) throw new AssertionError("Ambiguous shared catch consumed a peer");
            onBodyReplaced(species,first,replacement);
            if(outstanding.size()!=2 || outstanding.containsKey(first) || !outstanding.containsKey(replacement)
                    || !replacement.equals(individualByLabel.get(label)))
                throw new AssertionError("Sparkling replacement lost an individual");
            onCaptureResolved(species,replacement,replacement,CritterState.Outcome.BREAKOUT);
            if(outstanding.size()!=2) throw new AssertionError("Breakout removed a Sparkling");
            onCaptureResolved(species,replacement,replacement,CritterState.Outcome.CAUGHT);
            if(outstanding.size()!=1 || !outstanding.containsKey(second) || !announcedBodies.contains(replacement))
                throw new AssertionError("Catch removed another Sparkling");
            resolveSharedCatch(species);
            if(!outstanding.isEmpty()) throw new AssertionError("Shared catch retained Sparkling HUD");
            resolveSharedCatch(species);
            if(!outstanding.isEmpty()) throw new AssertionError("Repeated shared catch changed state");
        } finally {
            outstanding.clear();announcedBodies.clear();visuallyAnnounced.clear();individualByLabel.clear();
        }
    }

	/** A new run has its own sparklings; the last one's are gone. */
	public static void reset() {
		PartySparklingDiscoveries.reset();
		announcedLabels.clear();
		individualByLabel.clear();
		deferredCapsuleLabels.clear();
		pendingBodylessLabels.clear();
		announcedBodies.clear();
		visuallyAnnounced.clear();
		outstanding.clear();
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
