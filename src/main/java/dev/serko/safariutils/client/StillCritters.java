package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.parse.ChatParser;
import dev.serko.safariutils.parse.CritterEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps last-confirmed positions for Duplico, Hideonwall, Hideonfloor and Bloodbat.
 * Capture events clear a species because chat does not identify the individual.
 * Nearby replacements absorb entity-ID changes, and stale entries expire as a
 * fallback when event ordering reintroduces an old sighting.
 */
public final class StillCritters {

	private static final Set<String> TRACKED = Set.of("Duplico", "Hideonwall", "Hideonfloor", "Bloodbat");
	/** How long an entry can go unconfirmed before it is dropped as likely orphaned. */
	private static final long STALE_MILLIS = 20_000;
	/**
	 * How close a fresh sighting has to land to an existing entry of the same species
	 * to be treated as that same individual under a new id, not a genuinely different
	 * one standing nearby. Deliberately tight — this is only meant to catch an id
	 * change happening essentially in place, not to guess across any real distance.
	 */
	private static final double SUPERSEDE_DISTANCE = 2.0;

	private record Entry(Critter critter, BlockPos pos, boolean sparkling,
			long millis, boolean visiblyConfirmed, boolean persistentThroughWalls) {
	}

	private static final Map<UUID, Entry> remembered = new HashMap<>();
	private static final Set<UUID> cataloguedIds = new java.util.HashSet<>();
	private static final Map<UUID, BlockPos> hideonfloorOrigins = new HashMap<>();
	private static final Map<UUID, Integer> hideonfloorStableScans = new HashMap<>();
	private static final Set<UUID> movedHideonfloors = new java.util.HashSet<>();
	/** Consecutive stationary scans before a Duplico pairing may confirm a spawn. */
	private static final Map<UUID, BlockPos> duplicoPairOrigins = new HashMap<>();
	private static final Map<UUID, UUID> duplicoPairLabels = new HashMap<>();
	private static final Map<UUID, Integer> duplicoStableScans = new HashMap<>();
	private static final Set<Critter> catalogClosed = new java.util.HashSet<>();
	private static final Map<Critter, Set<BlockPos>> unchecked = new HashMap<>();
	private static final Map<Critter, UUID> resolving = new HashMap<>();
	/** Bodies resolved by a catch but still lingering in the client entity list. */
	private static final Set<UUID> suppressedBodies = new java.util.HashSet<>();
	private static long lastScan = Long.MIN_VALUE;
	private static String preparedLobby;

	/** One remembered individual, for the renderer — which one, and where. */
	public record Sighted(UUID id, BlockPos pos, boolean sparkling, boolean persistentThroughWalls) {
	}

	private StillCritters() {
	}

	public static void tick() {
		prepareLobby();
		if (!SafariLocation.inSafari()) return;
		long now = System.currentTimeMillis();
		remembered.values().removeIf(entry -> {
			boolean stale = !entry.visiblyConfirmed() && now - entry.millis() > STALE_MILLIS;
			if (stale) DebugLog.line("STILL", "EXPIRE " + entry.critter().name() + " (unconfirmed " + STALE_MILLIS + "ms)");
			return stale;
		});
		long scan = CritterEntities.scannedAt();
		if (scan == lastScan) return;
		lastScan = scan;

		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (!TRACKED.contains(sighting.critter().name())) continue;

			Entity entity = sighting.mob();
			if (entity != null && suppressedBodies.contains(entity.getUUID())) continue;
			learnInitialPosition(sighting, entity);
			if (entity == null) {
				// Duplico always has a persistent interaction body. A label without that
				// body is capture/capsule scaffolding and must never promote a candidate.
				if ("Duplico".equals(sighting.critter().name())) continue;
				// Some dormant critters are label-only. The hidden label establishes that
				// the candidate is real only after the player directly inspects its spot.
				if (TRACKED.contains(sighting.critter().name())) {
					BlockPos pos = sighting.label().blockPosition();
					boolean inspected = unchecked.getOrDefault(sighting.critter(), Set.of()).stream()
						.anyMatch(candidate -> sameSpawn(candidate, pos)
							&& ("Hideonwall".equals(sighting.critter().name())
								? VisibilityCheck.canInspectPaintingCandidate(candidate)
								: VisibilityCheck.canInspectCandidate(candidate)));
					if (inspected) {
						unchecked.getOrDefault(sighting.critter(), Set.of())
							.removeIf(candidate -> sameSpawn(candidate, pos));
						remembered.put(sighting.label().getUUID(), new Entry(sighting.critter(), pos,
							sighting.sparkling(), now, true, true));
					}
				}
				continue;
			}
			UUID id = entity.getUUID();
			BlockPos pos = entity.blockPosition();
			boolean duplico = "Duplico".equals(sighting.critter().name());
			boolean stableDuplicoPair = !duplico || stableDuplicoPair(sighting, entity);
			boolean directlyVisible = duplico
				? stableDuplicoPair && VisibilityCheck.canSeeDecoratedEntity(entity)
				: VisibilityCheck.canSee(entity);
			boolean visible = !SafeMode.hiddenCritter(sighting.critter(), sighting.sparkling())
				|| directlyVisible;
			if (visible) unchecked.computeIfAbsent(sighting.critter(),
				ignored -> new java.util.LinkedHashSet<>())
				.removeIf(candidate -> sameSpawn(candidate, pos));

			if (!remembered.containsKey(id)) {
				DebugLog.line("STILL", "REMEMBER " + sighting.critter().name() + " id=" + shortId(id)
					+ " pos=" + pos(pos));
				supersedeNearby(sighting.critter(), id, pos, now);
			}
			Entry previous = remembered.get(id);
			boolean stationary = entity.getDeltaMovement().lengthSqr() < 1.0e-4;
			boolean persistent = stationary && (directlyVisible
				|| previous != null && previous.persistentThroughWalls() && previous.pos().equals(pos));
			remembered.put(id, new Entry(sighting.critter(), pos, sighting.sparkling(), now,
				visible || previous != null && previous.visiblyConfirmed(), persistent));
		}
		pruneVisibleEmptyCandidates();
	}

	private static void prepareLobby() {
		if (!SafariLocation.inSafari()) {
			preparedLobby = null;
			return;
		}
		String lobby = SafariLocation.lobbyId() == null ? "pending" : SafariLocation.lobbyId();
		if (lobby.equals(preparedLobby)) return;
		preparedLobby = lobby;
		reset();
	}

	private static void pruneVisibleEmptyCandidates() {
		var client = net.minecraft.client.Minecraft.getInstance();
		if (client.level == null) return;
		for (Critter critter : trackedCritters()) {
			if (!SafeMode.hiddenCritterCandidates(critter)) continue;
			Set<BlockPos> candidates = unchecked.get(critter);
			if (candidates == null || candidates.isEmpty()) continue;
			List<BlockPos> live = CritterEntities.all().stream()
				.filter(sighting -> critter.equals(sighting.critter()))
				.map(sighting -> sighting.mob() != null ? sighting.mob().blockPosition()
					: sighting.label().blockPosition())
				.filter(java.util.Objects::nonNull)
				.toList();
			candidates.removeIf(pos -> client.level.isLoaded(pos)
				&& VisibilityCheck.canInspectCandidate(pos)
				&& live.stream().noneMatch(actual -> sameSpawn(pos, actual)));
		}

		// A previously confirmed Duplico can outlive its actual interaction entity in
		// the client tracker. Preserve it while out of range, but retire the remembered
		// marker once its loaded position is directly inspected and is genuinely empty.
		List<BlockPos> liveDuplicos = CritterEntities.all().stream()
			.filter(sighting -> "Duplico".equals(sighting.critter().name()))
			.map(CritterEntities.Sighting::mob)
			.filter(java.util.Objects::nonNull)
			.map(Entity::blockPosition)
			.toList();
		Iterator<Map.Entry<UUID, Entry>> rememberedIterator = remembered.entrySet().iterator();
		while (rememberedIterator.hasNext()) {
			Map.Entry<UUID, Entry> rememberedEntry = rememberedIterator.next();
			Entry entry = rememberedEntry.getValue();
			if (!"Duplico".equals(entry.critter().name()) || !entry.visiblyConfirmed()) continue;
			if (!client.level.isLoaded(entry.pos())
				|| !VisibilityCheck.canInspectCandidate(entry.pos())) continue;
			if (liveDuplicos.stream().anyMatch(pos -> sameSpawn(entry.pos(), pos))) continue;
			DebugLog.line("STILL", "REMOVE stale Duplico id=" + shortId(rememberedEntry.getKey())
				+ " pos=" + pos(entry.pos()));
			rememberedIterator.remove();
		}
	}

	/** Unchecked Safe Mode candidates; a confirmed real location is rendered separately. */
	public static Set<BlockPos> candidatesFor(Critter critter) {
		if (!SafeMode.hiddenCritterCandidates(critter)) return Set.of();
		Set<BlockPos> result = new java.util.LinkedHashSet<>(unchecked.getOrDefault(critter, Set.of()));
		// Suppress the candidate copy only after the real critter is visibly confirmed.
		for (Entry entry : remembered.values()) {
			if (!critter.equals(entry.critter()) || !entry.visiblyConfirmed()) continue;
			result.removeIf(candidate -> sameSpawn(candidate, entry.pos()));
		}
		return Set.copyOf(result);
	}

	/**
	 * Drops any other entry of the same species close enough to {@code pos} to be the
	 * same individual reappearing under {@code newId} — see the class doc for why a
	 * punch specifically needs this and chat-driven clearing alone does not catch it.
	 */
	private static void supersedeNearby(Critter critter, UUID newId, BlockPos pos, long now) {
		double distSq = SUPERSEDE_DISTANCE * SUPERSEDE_DISTANCE;
		Iterator<Map.Entry<UUID, Entry>> it = remembered.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Entry> entry = it.next();
			if (entry.getKey().equals(newId)) continue;
			if (!critter.equals(entry.getValue().critter())) continue;
			if (entry.getValue().pos().distSqr(pos) > distSq) continue;

			DebugLog.line("STILL", "SUPERSEDE " + critter.name() + " id " + shortId(entry.getKey())
				+ " -> " + shortId(newId) + " (new sighting within " + SUPERSEDE_DISTANCE + " blocks)");
			it.remove();
		}
	}

	/** Every remembered individual of {@code critter}. */
	public static List<Sighted> entriesFor(Critter critter) {
		List<Sighted> result = new ArrayList<>();
		for (Map.Entry<UUID, Entry> entry : remembered.entrySet()) {
			if (!critter.equals(entry.getValue().critter())) continue;
			result.add(new Sighted(entry.getKey(), entry.getValue().pos(), entry.getValue().sparkling(),
				entry.getValue().persistentThroughWalls()));
		}
		return result;
	}

	public static boolean persistentThroughWalls(UUID id) {
		Entry entry = remembered.get(id);
		return entry != null && entry.persistentThroughWalls();
	}

	public static boolean isVisiblyConfirmed(UUID id) {
		Entry entry = remembered.get(id);
		return entry != null && entry.visiblyConfirmed();
	}

	/** Whether a caught body is merely lingering in the client's entity list. */
	public static boolean isResolved(UUID id) {
		return suppressedBodies.contains(id);
	}

	/** Feeds one cleaned chat line. */
	public static void onChatMessage(String line) {
		CritterEvent event = ChatParser.parse(line, null);
		if (event == null || event.critter() == null) return;
		if (!TRACKED.contains(event.critter().name())) return;

		// Chat identifies the species but not the individual. The recatch tracker has
		// already selected the aimed-at entity by the time this handler runs.
		if (event.type() != CritterEvent.Type.ATTEMPT
			&& event.type() != CritterEvent.Type.FAILED
			&& !event.isCatch()) {
			return;
		}
		catalogClosed.add(event.critter());
		if (event.type() == CritterEvent.Type.ATTEMPT) {
			UUID id = RecatchSpots.pendingCatchEntity(event.critter());
			if (id == null) id = nearestRemembered(event.critter());
			if (id != null) {
				resolving.put(event.critter(), id);
				suppressedBodies.add(id);
				remembered.remove(id);
				DebugLog.line("STILL", "RESOLVE " + event.critter().name() + " id=" + shortId(id));
			}
		} else if (event.type() == CritterEvent.Type.FAILED) {
			UUID id = resolving.remove(event.critter());
			if (id != null) suppressedBodies.remove(id);
		} else {
			UUID id = resolving.remove(event.critter());
			if (id == null) id = nearestRemembered(event.critter());
			if (id != null) {
				suppressedBodies.add(id);
				remembered.remove(id);
			}
		}
	}

	/** A spot from the last run says nothing about this one. */
	public static void reset() {
		remembered.clear();
		cataloguedIds.clear();
		hideonfloorOrigins.clear();
		hideonfloorStableScans.clear();
		movedHideonfloors.clear();
		duplicoPairOrigins.clear();
		duplicoPairLabels.clear();
		duplicoStableScans.clear();
		resolving.clear();
		suppressedBodies.clear();
		catalogClosed.clear();
		unchecked.clear();
		for (Critter critter : trackedCritters()) {
			unchecked.put(critter, new java.util.LinkedHashSet<>(StaticEntityCatalog.positions(critter.name())));
		}
		lastScan = Long.MIN_VALUE;
	}

	private static UUID nearestRemembered(Critter critter) {
		var player = net.minecraft.client.Minecraft.getInstance().player;
		if (player == null) return null;
		return remembered.entrySet().stream()
			.filter(entry -> critter.equals(entry.getValue().critter()))
			.min(java.util.Comparator.comparingDouble(entry -> player.distanceToSqr(
				entry.getValue().pos().getX() + 0.5, entry.getValue().pos().getY() + 0.5,
				entry.getValue().pos().getZ() + 0.5)))
			.map(Map.Entry::getKey).orElse(null);
	}

	/** Rejects transient label/body pairings produced during nearby capture effects. */
	private static boolean stableDuplicoPair(CritterEntities.Sighting sighting, Entity entity) {
		UUID bodyId = entity.getUUID();
		BlockPos pos = entity.blockPosition();
		UUID labelId = sighting.label().getUUID();
		BlockPos previousPos = duplicoPairOrigins.put(bodyId, pos);
		UUID previousLabel = duplicoPairLabels.put(bodyId, labelId);
		if (!pos.equals(previousPos) || !labelId.equals(previousLabel)) {
			duplicoStableScans.put(bodyId, 1);
			return false;
		}
		return duplicoStableScans.merge(bodyId, 1, Integer::sum) >= 3;
	}

	private static void learnInitialPosition(CritterEntities.Sighting sighting, Entity entity) {
		// Persistent spawn catalogs are curated from confirmed solo runs only. Party
		// members may wake or move these critters before the local client encounters
		// them, which would make a moved position look like an initial spawn.
		if (dev.serko.safariutils.session.SessionManager.current() == null
			|| !SafariPartyWatch.confirmedSoloForLearning()) return;
		if (catalogClosed.contains(sighting.critter())) return;
		if (!"Hideonfloor".equals(sighting.critter().name())) {
			if (entity != null && cataloguedIds.add(entity.getUUID())
				&& entity.getDeltaMovement().lengthSqr() < 1.0e-4) {
				StaticEntityCatalog.learn(sighting.critter().name(), entity.blockPosition());
			}
			return;
		}

		UUID id = entity.getUUID();
		if (movedHideonfloors.contains(id) || cataloguedIds.contains(id)) return;
		BlockPos pos = entity.blockPosition();
		BlockPos origin = hideonfloorOrigins.putIfAbsent(id, pos);
		if (origin == null) {
			hideonfloorStableScans.put(id, 1);
			return;
		}
		if (!origin.equals(pos)) {
			movedHideonfloors.add(id);
			hideonfloorStableScans.remove(id);
			return;
		}
		int stable = hideonfloorStableScans.merge(id, 1, Integer::sum);
		if (stable >= 2) {
			StaticEntityCatalog.learn(sighting.critter().name(), origin);
			cataloguedIds.add(id);
		}
	}

	/** Hidden bodies and their labels can sit a few blocks apart vertically. */
	private static boolean sameSpawn(BlockPos first, BlockPos second) {
		int dx = first.getX() - second.getX();
		int dz = first.getZ() - second.getZ();
		return dx * dx + dz * dz <= 4 && Math.abs(first.getY() - second.getY()) <= 4;
	}

	private static List<Critter> trackedCritters() {
		return TRACKED.stream().map(dev.serko.safariutils.data.Critters::byName)
			.filter(java.util.Objects::nonNull).toList();
	}

	private static String shortId(UUID id) {
		return id.toString().substring(0, 8);
	}

	private static String pos(BlockPos pos) {
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}
}
