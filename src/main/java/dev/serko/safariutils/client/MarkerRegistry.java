package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.state.SafariRunSnapshot;
import dev.serko.safariutils.state.SafariRuntimeState;
import dev.serko.safariutils.state.EvidencePolicy;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Client-thread snapshot of marker ownership and render inputs, reused until its context
 * changes or a tracker invalidates it. Collections are frozen; entity references remain live.
 */
public final class MarkerRegistry {
	public record Snapshot(Object level, long gameTick, long configRevision, long scanRevision,
			long capturedAtMillis, SafariRunSnapshot run, List<Markers.Marker> markers,
			List<CritterEntities.Sighting> sightings,
			Map<Critter, List<CritterEntities.Sighting>> sightingsByCritter,
			List<CritterState.LogicalView> logicalCritters,
			List<CritterState.AttemptView> attempts,
			RecatchSpots.PresentationSnapshot presentation,
			StillCritters.PresentationSnapshot tracked) {
		public List<CritterEntities.Sighting> sightings(Critter critter) {
			return sightingsByCritter.getOrDefault(critter, List.of());
		}
	}

	private static Snapshot cached;
	private static long invalidation;
	private static DiagnosticOwnership lastDiagnosticOwnership;

	/** Full identities for comparison; shortened IDs are only a log presentation detail. */
	record DiagnosticOwnership(long connection, long visit, long run, Set<Long> attempts,
			Set<UUID> pins, Set<UUID> shrinks, Set<UUID> nativeBodies, Set<Critter> transitions) {
		DiagnosticOwnership {
			attempts = Set.copyOf(attempts);
			pins = Set.copyOf(pins);
			shrinks = Set.copyOf(shrinks);
			nativeBodies = Set.copyOf(nativeBodies);
			transitions = Set.copyOf(transitions);
		}
	}

	private MarkerRegistry() { }

	public static Snapshot snapshot() {
		Minecraft client = Minecraft.getInstance();
		Object level = client.level;
		long tick = client.level == null ? Long.MIN_VALUE : client.level.getGameTime();
		long configRevision = ConfigManager.revision();
		long scanRevision = CritterEntities.scanRevision();
		SafariRunSnapshot run = SafariRuntimeState.snapshot();
		Snapshot current = cached;
		if (current != null && current.level() == level && current.gameTick() == tick
			&& current.configRevision() == configRevision
			&& current.scanRevision() == scanRevision
			&& current.run().revision() == run.revision()) return current;
		long generation = invalidation;
		List<CritterEntities.Sighting> sightings = List.copyOf(CritterEntities.all());
		long now = System.currentTimeMillis();
		RecatchSpots.PresentationSnapshot presentation = RecatchSpots.snapshot();
		StillCritters.PresentationSnapshot tracked = StillCritters.snapshot();
		List<CritterState.LogicalView> logical = CritterState.logicalCritters().stream()
			.filter(view -> !SafeMode.active()
				|| EvidencePolicy.canRevealLiveWorld(view.evidence(), false))
			.toList();
		Snapshot fresh = new Snapshot(level, tick, configRevision, scanRevision, now, run,
			deduplicate(Markers.collect(presentation.pins())), sightings, CritterEntities.byCritter(),
			List.copyOf(logical),
			List.copyOf(CritterState.activeAttempts()), presentation, tracked);
		// A tracker invalidation during collection must not be overwritten by this build.
		if (generation == invalidation) cached = fresh;
		logSnapshotChange(fresh);
		return fresh;
	}

	public static void invalidate() {
		invalidation++;
		cached = null;
	}

	/**
	 * Change-only ownership summary for diagnosing a visual mismatch without recording
	 * one line per frame. Entity IDs are ephemeral client-world IDs, never player data.
	 */
	private static void logSnapshotChange(Snapshot snapshot) {
		if (!DebugLog.isEnabled()) {
			lastDiagnosticOwnership = null;
			return;
		}
		RecatchSpots.PresentationSnapshot presentation = snapshot.presentation();
		var epoch = snapshot.run().epoch();
		var ownership = new DiagnosticOwnership(epoch.connection(), epoch.visit(), epoch.run(),
			snapshot.attempts().stream().map(CritterState.AttemptView::id)
				.collect(java.util.stream.Collectors.toSet()),
			presentation.pinnedBodies(), presentation.animatedBodies(),
			presentation.nativeShrinkingBodies(), presentation.transitioning());
		if (ownership.equals(lastDiagnosticOwnership)) return;
		lastDiagnosticOwnership = ownership;
		String state = "run=" + snapshot.run().epoch().run()
			+ " scan=" + snapshot.scanRevision()
			+ " sightings=" + snapshot.sightings().size()
			+ " logical=" + snapshot.logicalCritters().size()
			+ " attempts=" + snapshot.attempts().size()
			+ " markers=" + snapshot.markers().size()
			+ " pins=" + shortIds(presentation.pinnedBodies())
			+ " shrinks=" + shortIds(presentation.animatedBodies())
			+ " native=" + shortIds(presentation.nativeShrinkingBodies())
			+ " transitions=" + presentation.transitioning().stream()
				.map(Critter::name).sorted().toList()
			+ " tracked=" + snapshot.tracked().remembered().values().stream()
				.mapToInt(List::size).sum();
		DebugLog.line("RENDERSTATE", state);
	}

	private static List<String> shortIds(Set<UUID> ids) {
		return ids.stream().map(UUID::toString).map(value -> value.substring(0, 8))
			.sorted().toList();
	}

	private static List<Markers.Marker> deduplicate(List<Markers.Marker> markers) {
		record Key(double minX, double minY, double minZ, double maxX, double maxY,
				double maxZ, String label, int colour, Markers.Style style,
				boolean seeThrough, boolean researchCandidate, boolean critterLabel) { }
		LinkedHashMap<Key, Markers.Marker> unique = new LinkedHashMap<>();
		for (Markers.Marker marker : markers) {
			var box = marker.box();
			unique.put(new Key(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
				marker.label(), marker.colour(), marker.style(), marker.seeThrough(),
				marker.researchCandidate(), marker.critterLabel()), marker);
		}
		return List.copyOf(unique.values());
	}

}
