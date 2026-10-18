package dev.serko.safariutils.backend;

import dev.serko.safariutils.SafariUtils;
import dev.serko.safariutils.BuildVersion;
import dev.serko.safariutils.api.SharedSparklingProvider;
import dev.serko.safariutils.api.SparklingPlayerLookup;
import dev.serko.safariutils.api.PartyRefreshStatus;
import dev.serko.safariutils.api.PartySparklingSnapshot;
import dev.serko.safariutils.client.SafariLocation;
import dev.serko.safariutils.client.SafariPartyWatch;
import dev.serko.safariutils.client.SparklingMode;
import dev.serko.safariutils.client.SparklingScreen;
import dev.serko.safariutils.client.ClientMessages;
import dev.serko.safariutils.client.ChatQueue;
import dev.serko.safariutils.client.CanonicalPlayerNames;
import dev.serko.safariutils.client.ConfigManager;
import dev.serko.safariutils.client.DebugLog;
import dev.serko.safariutils.client.OperationalLog;
import dev.serko.safariutils.client.PlayerIdentityStore;
import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.session.SparklingStats;
import dev.serko.safariutils.session.SessionManager;
import dev.serko.safariutils.state.PartySnapshot;
import dev.serko.safariutils.state.PartyState;
import dev.serko.safariutils.state.SafariEpoch;
import dev.serko.safariutils.state.SafariRuntimeState;
import dev.serko.safariutils.concurrent.BoundedRequests;
import dev.serko.safariutils.concurrent.SingleFlight;
import dev.serko.safariutils.concurrent.GenerationDispatch;
import dev.serko.safariutils.backend.BackendPartyRoster.Member;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Shared profile lookup, per-player cache, roster observer, and mutual-set reducer. */
public final class BackendSparklingProvider implements SharedSparklingProvider {
	private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
	private static final int NAME_CACHE_LIMIT = 512;
	private static final int PARTY_CACHE_LIMIT = 64;

	private final BoundedRequests requests = new BoundedRequests("SafariUtils-API", 1, 64);
	private final PlayerCollections playerCache = new PlayerCollections(NAME_CACHE_LIMIT);
	private final ConcurrentHashMap<String, CompletableFuture<Integer>> pendingNameColours = new ConcurrentHashMap<>();
	private final Map<String, Long> nameColourRetryAfter = new ConcurrentHashMap<>();
	private final Map<String, String> specialNamesByUuid = new ConcurrentHashMap<>();
	private final Set<String> automaticRefreshFailures = ConcurrentHashMap.newKeySet();
	private final Set<String> announcedPartySignatures = ConcurrentHashMap.newKeySet();
	private final Map<String, Long> partyCacheTouchedAt = new ConcurrentHashMap<>();
	private final Map<String, List<Member>> resolvedParties = new ConcurrentHashMap<>();
	private volatile HypixelApiClient api;
	private volatile SparklingPlayerLookup latestLocalCollection;
	private volatile boolean stopped;

	private final EntryRoster entryRoster = new EntryRoster();
	private List<Member> activeRoster = List.of();
	private String activeSignature = "";
	private String attemptedSignature = "";
	private boolean observedVisit;
	private String observedLobby;
	private long visitEnteredAt;
	private boolean compositionChanging;
	private String lastRosterStatus = "";

	@Override
	public Optional<URI> updatePage() {
		return Optional.empty();
	}

	@Override
	public void openTicketTradingTest(Screen parent) {
		if (!BuildVersion.has(BuildVersion.Capability.DEVELOPER_TOOLS)) return;
		try {
			Class<?> screen = Class.forName(
				"dev.serko.safariutils.client.TicketTradingTestScreen");
			screen.getMethod("open", Screen.class).invoke(null, parent);
		} catch (ReflectiveOperationException error) {
			OperationalLog.error("TICKET_TRADE/TEST_SCREEN", error);
		}
	}

	@Override
	public void tick() {
		if (stopped) return;
		if (!SafariLocation.inside()) {
			entryRoster.reset();
			observedVisit = false;
			observedLobby = null;
			lastRosterStatus = "";
			return;
		}
		String lobby = SafariLocation.lobbyId();
		if (!observedVisit || lobby != null && observedLobby != null && !lobby.equals(observedLobby)) {
			DebugLog.line("PARTYAPI", "new Safari visit; waiting for complete party roster");
			observedVisit = true;
			observedLobby = lobby;
			visitEnteredAt = SafariLocation.insideSinceMillis();
			if (visitEnteredAt == 0L) visitEnteredAt = System.currentTimeMillis();
			entryRoster.reset();
			activeRoster = List.of();
			activeSignature = "";
			attemptedSignature = "";
			compositionChanging = true;
			lastRosterStatus = "";
		}
		else if (observedLobby == null && lobby != null) observedLobby = lobby;
		entryRoster.observe(PartyState.snapshot(), visitEnteredAt);
		PartySnapshot party = entryRoster.claim(SessionManager.current() != null);
		if (party == null) return;
		// Shared Sparklings belong to the full Minecraft party that entered Safari,
		// not the temporary subset whose player entities have loaded into the instance.
		List<Member> observed = BackendPartyRoster.apiParty(party);
		int expectedParty = party.expectedPlayers();
		String rosterStatus = observed.size() + "/" + expectedParty;
		if (!rosterStatus.equals(lastRosterStatus)) {
			DebugLog.line("PARTYAPI", "resolved party roster=" + rosterStatus);
			lastRosterStatus = rosterStatus;
		}
		if (observed.isEmpty()) return;
		if (observed.size() < expectedParty) return;

		String signature = signature(observed);
		// Once selected, composition is immutable for this Safari visit. A member
		// leaving or disconnecting does not turn the remaining subset into a new run.
		if (!attemptedSignature.isEmpty() && !signature.equals(attemptedSignature)) return;
		// Connection state and leadership can change without changing membership.
		// Returning to the same names therefore keeps the existing cached result.
		activeRoster = observed;
		activeSignature = signature;
		if (signature.equals(attemptedSignature)) return;
		attemptedSignature = signature;
		Set<String> cached = cachedPartyCollection(signature);
		if (cached != null) {
			DebugLog.line("PARTYAPI", "using party composition cache members=" + observed.size()
				+ " shared=" + cached.size());
			partyCacheTouchedAt.put(signature, System.currentTimeMillis());
			compositionChanging = false;
			activeRoster = resolvedParties.getOrDefault(signature, observed);
			applyPartyCollection(cached, signature);
			return;
		}
		DebugLog.line("PARTYAPI", "shared Sparkling load started members=" + observed.size());
		SafariEpoch requestedEpoch = SafariRuntimeState.current();
		refresh(observed, signature, "Party Shared Sparklings Loaded").thenApply(names -> {
			automaticRefreshFailures.remove(signature);
			return names;
		}).exceptionally(error -> {
			Minecraft.getInstance().execute(() -> {
				if (!ownsRefresh(requestedEpoch, signature)) return;
				DebugLog.line("PARTYAPI", "refresh failed members=" + observed.size()
					+ " usingCache=false cause=" + rootCause(error).getClass().getSimpleName());
				automaticRefreshFailures.add(signature);
				trimPartyCaches();
				OperationalLog.error("API/PARTY_REFRESH", rootCause(error));
			});
			return Set.of();
		});
	}

	@Override
	public void onPartyMembershipChanged() {
		if (observedVisit) {
			entryRoster.observe(PartyState.snapshot(), visitEnteredAt);
			// The current Safari remains bound to its entry roster. The refreshed party
			// list is intentionally ignored until a different Safari visit begins.
			return;
		}
		compositionChanging = true;
	}

	/** First fresh central confirmation wins, even if it precedes ticket activation. */
	static final class EntryRoster {
		private PartySnapshot confirmed;
		private boolean claimed;

		void observe(PartySnapshot party, long enteredAt) {
			if (confirmed == null && party.completeRoster() && party.capturedAtMillis() >= enteredAt) {
				confirmed = party;
			}
		}

		PartySnapshot claim(boolean runActive) {
			if (!runActive || claimed || confirmed == null || confirmed.expectedPlayers() > 4) return null;
			claimed = true;
			return confirmed;
		}

		void reset() {
			confirmed = null;
			claimed = false;
		}
	}

	@Override
	public PartyRefreshStatus partyRefreshStatus() {
		int expected = PartyState.snapshot().expectedPlayers();
		if (expected > 4) return new PartyRefreshStatus(
			"Too many party members — shared Sparkling loading supports at most 4 players", false);
		List<Member> roster = rosterForControls();
		if (roster.isEmpty()) return new PartyRefreshStatus(
			"A stable Safari party roster is not available yet", false);
		String signature = signature(roster);
		boolean fallback = automaticRefreshFailures.contains(signature);
		return new PartyRefreshStatus(null, fallback);
	}

	@Override
	public PartySparklingSnapshot partySnapshot() {
		List<Member> roster = rosterForControls();
		if (roster.isEmpty()) return new PartySparklingSnapshot(List.of(), false);
		String currentSignature = signature(roster);
		List<Member> resolved = resolvedParties.get(currentSignature);
		boolean loaded = resolved != null && playerCache.containsAll(resolved.stream().map(Member::uuid).toList());
		List<String> members = roster.stream().map(Member::name)
			.sorted(String.CASE_INSENSITIVE_ORDER).toList();
		return new PartySparklingSnapshot(members, loaded && !compositionChanging);
	}

	@Override
	public java.util.Optional<SparklingPlayerLookup> cachedLocalCollection() {
		return java.util.Optional.ofNullable(latestLocalCollection);
	}

	/** UI and fallback actions stay attached to the immutable current-visit roster. */
	private List<Member> rosterForControls() {
		if (observedVisit && !activeRoster.isEmpty()) return activeRoster;
		List<Member> current = manualRefreshRoster();
		return current.isEmpty() ? activeRoster : current;
	}

	private static List<Member> manualRefreshRoster() {
		PartySnapshot party = PartyState.snapshot();
		List<Member> full = BackendPartyRoster.apiParty(party);
		if (!full.isEmpty()) return full;
		int expected = party.expectedPlayers();
		if (expected > 4) return List.of();
		List<Member> roster = currentSafariRoster();
		if (!roster.isEmpty() && roster.size() >= expected) return roster;
		if (expected > 1) return List.of();
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return List.of();
		String name = client.player.getGameProfile().name();
		String uuid = client.player.getGameProfile().id().toString()
			.replace("-", "").toLowerCase(Locale.ROOT);
		return List.of(new Member(name, uuid));
	}

	@Override
	public CompletableFuture<SparklingPlayerLookup> lookupPlayer(String username) {
		if (!USERNAME.matcher(username).matches()) {
			return CompletableFuture.failedFuture(new IOException("Invalid Minecraft username"));
		}
		return requests.submit(() -> {
			long collectionCheckpoint = playerCache.checkpoint();
			try {
				HypixelApiClient.ProfileIdentity identity = client().identity(username);
				String uuid = identity.uuid();
				String canonicalName = identity.name();
				rememberIdentity(uuid, canonicalName);
				SafariPlayerData collection = client().collectionManual(uuid);
				cacheRankColour(uuid, canonicalName);
				Set<String> species = collection.species();
				playerCache.put(uuid, species, collectionCheckpoint);
				return collection.lookup(canonicalName);
			} catch (IOException error) {
				OperationalLog.error("API/PLAYER_LOOKUP", error);
				throw new CompletionException(error);
			} catch (InterruptedException error) {
				Thread.currentThread().interrupt();
				OperationalLog.error("API/PLAYER_LOOKUP", error);
				throw new CompletionException(new IOException("Player lookup was interrupted", error));
			}
		});
	}

	@Override
	public void rememberIdentity(String uuid, String name) {
		PlayerIdentityStore.remember(name, uuid);
		if (name == null || name.isBlank() || !BackendPartyRoster.allowed(uuid)) return;
		specialNamesByUuid.put(BackendPartyRoster.compact(uuid), name.toLowerCase(Locale.ROOT));
	}

	@Override
	public boolean specialName(String name) {
		if (name == null) return false;
		String normalized = name.toLowerCase(Locale.ROOT);
		String knownUuid = CanonicalPlayerNames.uuid(name);
		if (BackendPartyRoster.allowed(knownUuid)) {
			specialNamesByUuid.put(BackendPartyRoster.compact(knownUuid), normalized);
			return true;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player != null
			&& BackendPartyRoster.allowed(client.player.getGameProfile().id().toString())
			&& client.player.getGameProfile().name().equalsIgnoreCase(name)) return true;
		return specialNamesByUuid.containsValue(normalized);
	}

	@Override
	public Set<String> specialNames() {
		Set<String> names = new LinkedHashSet<>(specialNamesByUuid.values());
		Minecraft client = Minecraft.getInstance();
		if (client.player != null
			&& BackendPartyRoster.allowed(client.player.getGameProfile().id().toString())) {
			names.add(client.player.getGameProfile().name().toLowerCase(Locale.ROOT));
		}
		return Set.copyOf(names);
	}

	@Override
	public long lookupAvailableAt() {
		HypixelApiClient current = api;
		return current == null ? 0L : current.nextProfileRequestAt();
	}

	@Override
	public CompletableFuture<Integer> refreshNameColour(String name, String knownUuid) {
		if (name == null || !USERNAME.matcher(name).matches()) {
			return CompletableFuture.completedFuture(-1);
		}
		String key = name.toLowerCase(Locale.ROOT);
		PlayerIdentityStore.RankSnapshot cached =
			PlayerIdentityStore.rankSnapshot(name, knownUuid);
		if (cached.fresh(System.currentTimeMillis())) {
			return CompletableFuture.completedFuture(cached.colour());
		}
		if (System.currentTimeMillis() < nameColourRetryAfter.getOrDefault(key, 0L)) {
			return CompletableFuture.completedFuture(-1);
		}
		return SingleFlight.request(pendingNameColours, key, () -> requests.submit(() -> {
			try {
				HypixelApiClient.ProfileIdentity identity = knownUuid == null || knownUuid.isBlank()
					? client().identity(name) : new HypixelApiClient.ProfileIdentity(knownUuid, name);
				HypixelApiClient.RankSnapshot rank = client().rank(identity.uuid());
				PlayerIdentityStore.rememberApiRank(identity.name(), identity.uuid(), rank.colour(),
					rank.permanentColour(), rank.permanentLevel(), rank.temporary(), rank.fetchedAt());
				return PlayerIdentityStore.rankColour(identity.name(), identity.uuid());
			} catch (IOException error) {
				DebugLog.line("PARTYAPI", "rank colour unavailable for " + name + ": "
					+ failureSummary(error));
				return -1;
			} catch (InterruptedException error) {
				Thread.currentThread().interrupt();
				return -1;
			}
		}).whenComplete((colour, error) -> {
			if (error != null || colour == null || colour == -1) {
				nameColourRetryAfter.put(key, System.currentTimeMillis() + 5 * 60_000L);
			} else nameColourRetryAfter.remove(key);
			trimNameCaches();
		}));
	}

	private void cacheRankColour(String uuid, String name) {
		try {
			HypixelApiClient.RankSnapshot rank = client().rank(uuid);
			PlayerIdentityStore.rememberApiRank(name, uuid, rank.colour(),
				rank.permanentColour(), rank.permanentLevel(), rank.temporary(), rank.fetchedAt());
		} catch (IOException error) {
			DebugLog.line("PARTYAPI", "rank colour unavailable for " + name + ": "
				+ failureSummary(error));
			OperationalLog.error("API/RANK_LOOKUP", error);
		} catch (InterruptedException error) {
			Thread.currentThread().interrupt();
		}
	}

	/** Network diagnostics contain the useful failure reason but never request credentials. */
	private static String failureSummary(IOException error) {
		String message = error.getMessage();
		return error.getClass().getSimpleName()
			+ (message == null || message.isBlank() ? "" : " (" + message + ")");
	}


	@Override
	public boolean onSharedCatch(String species) {
		if (activeRoster.isEmpty() || SessionManager.current() == null) return false;
		String id = speciesId(species);
		Set<String> present = currentSafariRoster().stream().map(Member::uuid)
			.collect(java.util.stream.Collectors.toSet());
		return playerCache.recordPartyCatch(activeRoster.stream().map(Member::uuid).toList(), present, id);
	}

	private Set<String> cachedPartyCollection(String signature) {
		List<Member> roster = resolvedParties.get(signature);
		return roster == null ? null : playerCache.shared(roster.stream().map(Member::uuid).toList());
	}

	@Override
	public void shutdown() {
		stopped = true;
		requests.close();
	}

	private CompletableFuture<Set<String>> refresh(List<Member> roster,
			String expectedSignature, String successMessage) {
		String localUuid = localUuid();
		SafariEpoch expectedEpoch = SafariRuntimeState.snapshot().epoch();
		return requests.submit(() -> {
			long collectionCheckpoint = playerCache.checkpoint();
			try {
				List<Member> resolvedRoster = new ArrayList<>(roster.size());
				for (Member member : roster) {
					if (member.uuid().isEmpty()) {
						HypixelApiClient.ProfileIdentity identity = client().identity(member.name());
						CanonicalPlayerNames.rememberProfile(identity.name(), identity.uuid());
						resolvedRoster.add(new Member(identity.name(), identity.uuid()));
					} else {
						CanonicalPlayerNames.rememberProfile(member.name(), member.uuid());
						resolvedRoster.add(member);
					}
				}
				client().prefetch(resolvedRoster.stream().map(Member::uuid).toList());
				Map<String, Set<String>> perPlayer = new LinkedHashMap<>();
				Set<String> localSpecies = Set.of();
				SparklingPlayerLookup localCollection = null;
				List<SparklingPlayerLookup> lookups = new ArrayList<>();
				for (Member member : resolvedRoster) {
					String uuid = member.uuid();
					cacheRankColour(uuid, member.name());
					boolean local = uuid.equals(localUuid);
					SafariPlayerData collection = client().collectionAutomatic(uuid);
					Set<String> species = collection.species();
					perPlayer.put(uuid, species);
					SparklingPlayerLookup lookup = collection.lookup(member.name());
					lookups.add(lookup);
					if (local) localCollection = lookup;
					if (local) localSpecies = species;
				}
				return new RefreshResult(Map.copyOf(perPlayer), collectionCheckpoint, localSpecies, localCollection,
					List.copyOf(lookups), List.copyOf(resolvedRoster));
			} catch (IOException error) {
				throw new CompletionException(error);
			} catch (InterruptedException error) {
				Thread.currentThread().interrupt();
				throw new CompletionException(new IOException("Profile refresh was interrupted", error));
			}
		}).thenCompose(result -> apply(result, expectedSignature, expectedEpoch, successMessage));
	}

	private CompletableFuture<Set<String>> apply(RefreshResult result, String expectedSignature,
			SafariEpoch expectedEpoch, String successMessage) {
		return GenerationDispatch.call(Minecraft.getInstance()::execute,
			() -> ownsRefresh(expectedEpoch, expectedSignature), () -> {
			partyCacheTouchedAt.put(expectedSignature, System.currentTimeMillis());
			playerCache.putAll(result.collections(), result.collectionCheckpoint());
			activeRoster = result.roster();
			resolvedParties.put(expectedSignature, result.roster());
			Set<String> names = cachedPartyCollection(expectedSignature);
			trimPartyCaches();
			if (result.localCollection() != null) latestLocalCollection = result.localCollection();
			SparklingScreen.cacheRecentLookups(result.lookups());
			DebugLog.line("PARTYAPI", "refresh succeeded shared=" + names.size()
				+ " recentLookups=" + result.lookups().size());
			compositionChanging = false;
			reportLocalDifferences(result.localSpecies());
			applyPartyCollection(names, expectedSignature);
			ClientMessages.send(successMessage, ClientMessages.Tone.SUCCESS);
			return Set.copyOf(names);
		}).thenApply(resultApplied -> resultApplied.orElseThrow(() -> {
			DebugLog.line("PARTYAPI", "refresh result discarded: party, visit or runtime changed");
			return new CompletionException(new IOException("Safari party changed during refresh"));
		}));
	}

	/** Evaluated only on the client thread, immediately before applying success or failure. */
	private boolean ownsRefresh(SafariEpoch expected, String signature) {
		SafariEpoch current = SafariRuntimeState.current();
		return !stopped && SafariLocation.inside() && expected.sameVisit(current)
			&& expected.connection() == current.connection() && signature.equals(activeSignature);
	}

	private void applyPartyCollection(Set<String> names, String signature) {
		Set<Critter> critters = new LinkedHashSet<>();
		for (Critter critter : Critters.all()) {
			if (names.contains(speciesId(critter.name()))) critters.add(critter);
		}
		SparklingMode.replaceShared(critters);
		String sharedChat = ConfigManager.get().sparkling.shared().command();
		if (sharedChat != null && announcedPartySignatures.add(signature)) {
			sendSharedChatAlert(sharedChat, critters);
		}
		trimPartyCaches();
	}

	private void trimNameCaches() {
		while (nameColourRetryAfter.size() > NAME_CACHE_LIMIT) {
			if (!removeOne(nameColourRetryAfter, pendingNameColours.keySet())) break;
		}
	}

	private void trimPartyCaches() {
		Set<String> signatures = new LinkedHashSet<>();
		signatures.addAll(automaticRefreshFailures);
		signatures.addAll(announcedPartySignatures);
		signatures.addAll(partyCacheTouchedAt.keySet());
		signatures.addAll(resolvedParties.keySet());
		if (signatures.size() <= PARTY_CACHE_LIMIT) return;
		List<String> oldest = signatures.stream()
			.filter(signature -> !signature.equals(activeSignature))
			.sorted(java.util.Comparator.comparingLong(
				signature -> partyCacheTouchedAt.getOrDefault(signature, 0L))).toList();
		for (String signature : oldest) {
			if (signatures.size() <= PARTY_CACHE_LIMIT) break;
			automaticRefreshFailures.remove(signature);
			announcedPartySignatures.remove(signature);
			partyCacheTouchedAt.remove(signature);
			resolvedParties.remove(signature);
			signatures.remove(signature);
		}
	}

	private static boolean removeOne(Map<String, ?> values, Set<String> protectedKeys) {
		for (String key : values.keySet()) {
			if (protectedKeys.contains(key)) continue;
			values.remove(key);
			return true;
		}
		return false;
	}

	/** Reports unique-species disagreements without altering the user's saved totals. */
	private static boolean reportLocalDifferences(Set<String> apiSpecies) {
		Set<String> savedSpecies = new LinkedHashSet<>();
		for (Critter critter : Critters.all()) {
			if (SparklingStats.count(critter) > 0) savedSpecies.add(speciesId(critter.name()));
		}

		Set<String> missingLocally = new LinkedHashSet<>(apiSpecies);
		missingLocally.removeAll(savedSpecies);
		Set<String> onlySavedLocally = new LinkedHashSet<>(savedSpecies);
		onlySavedLocally.removeAll(apiSpecies);
		if (missingLocally.isEmpty() && onlySavedLocally.isEmpty()) {
			return true;
		}

		SafariUtils.LOGGER.info("Sparkling collection/API difference: missing locally [{}]; only saved locally [{}]",
			displayNames(missingLocally), displayNames(onlySavedLocally));
		return false;
	}

	private static String displayNames(Set<String> ids) {
		if (ids.isEmpty()) return "None";
		return ids.stream().map(id -> {
			Critter critter = Critters.all().stream()
				.filter(candidate -> speciesId(candidate.name()).equals(id)).findFirst().orElse(null);
			return critter == null ? id : critter.name();
		}).sorted(String.CASE_INSENSITIVE_ORDER).collect(java.util.stream.Collectors.joining(", "));
	}

	private HypixelApiClient client() throws IOException {
		HypixelApiClient current = api;
		if (current != null) return current;
		synchronized (this) {
			if (api == null) api = new HypixelApiClient();
			return api;
		}
	}


	private static List<Member> currentSafariRoster() {
		return BackendPartyRoster.presentInSafari();
	}

	private static String signature(List<Member> roster) {
		return roster.stream().map(member -> member.name().toLowerCase(Locale.ROOT))
			.sorted().reduce((a, b) -> a + "," + b).orElse("");
	}

	private static String localUuid() {
		return BackendPartyRoster.localUuid();
	}

	private static String speciesId(String name) {
		return name.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
	}

	private static Throwable rootCause(Throwable error) {
		Throwable root = error;
		while (root.getCause() != null) root = root.getCause();
		return root;
	}

	private static void sendSharedChatAlert(String command, Set<Critter> shared) {
		List<String> sharedNames = shared.stream().map(Critter::name)
			.sorted(String.CASE_INSENSITIVE_ORDER).toList();
		List<String> missingNames = Critters.all().stream()
			.filter(critter -> !shared.contains(critter)).map(Critter::name)
			.sorted(String.CASE_INSENSITIVE_ORDER).toList();
		var config = ConfigManager.get().sparkling;
		boolean showMissing = config.sparklingSharedListFormat == 1;
		List<String> names = showMissing ? missingNames : sharedNames;
		String message = (showMissing ? "Missing" : "Shared") + " Sparklings ("
			+ names.size() + "/" + Critters.total() + "): "
			+ (names.isEmpty() ? "None" : String.join(", ", names));
		for (String part : splitChatMessage(message, 225)) {
			if ("pc".equals(command) && SafariPartyWatch.joinedPlayers() > 1) {
				ChatQueue.enqueueVerifiedParty(command + " " + part);
			} else ChatQueue.enqueue(command + " " + part, true);
		}
	}

	private static List<String> splitChatMessage(String message, int limit) {
		List<String> parts = new ArrayList<>();
		String remaining = message;
		while (remaining.length() > limit) {
			int split = remaining.lastIndexOf(", ", limit);
			if (split < 1) split = remaining.lastIndexOf(' ', limit);
			if (split < 1) split = limit;
			parts.add(remaining.substring(0, split).trim());
			remaining = remaining.substring(split).replaceFirst("^[, ]+", "");
		}
		if (!remaining.isBlank()) parts.add(remaining);
		return parts;
	}

	private record RefreshResult(Map<String, Set<String>> collections, long collectionCheckpoint, Set<String> localSpecies,
			SparklingPlayerLookup localCollection, List<SparklingPlayerLookup> lookups,
			List<Member> roster) {
	}
}
