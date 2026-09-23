package dev.serko.safariutils.client;

import dev.serko.safariutils.api.PartyItemSyncProvider;
import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.parse.ChatParser;
import dev.serko.safariutils.session.SessionManager;
import dev.serko.safariutils.client.PartySyncRoster.Member;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Opt-in party-chat-backed objective synchronization. */
public final class PartyItemSync implements PartyItemSyncProvider {
	private static final String MARKER = "[SU210]";
	private static final String ENABLE_PREFIX = "[SU2E:";
	private static final Pattern CONTROL = Pattern.compile("\\[SU2([ED]):([A-Za-z0-9_-]{12})]");
	private static final Pattern FORMATTING = Pattern.compile("§.");
	private static final String[] BIRDS = {"Bluebird", "Parakeet", "Macaw"};
	private static final long STATE_COALESCE_MILLIS = 500;
	private static final long RECOVERY_RETRY_MILLIS = 3_000;
	private static final long ROSTER_GRACE_MILLIS = 5_000;
	private final Map<String, State> states = new LinkedHashMap<>();
	private final Set<String> seen = new LinkedHashSet<>();
	private final Set<String> capableMembers = new LinkedHashSet<>();
	private final Set<String> announcedRosters = new LinkedHashSet<>();
	private final Set<String> pendingCapabilityLines = new LinkedHashSet<>();
	private List<Member> roster = List.of();
	private int[] localFound = new int[StartingItems.ORDERED.size()];
	private int localForestDrops;
	private int localSeeds;
	private int localWorms;
	private int localBerries;
	private int localLimeGems;
	private int localOrangeGems;
	private int localPurpleGems;
	private int localIncense;
	private int localPlacedGems;
	private int localCandlesLit;
	private boolean localGemzieOpen;
	private boolean localGemzieCaught;
	private boolean localDoomspiralSpawned;
	private boolean localDoomspiralCaught;
	private boolean localDoomspiralRetreated;
	private boolean localWumpaSpawned;
	private boolean localWumpaCaught;
	private boolean localWumpaRetreated;
	private long gemzieOpenedAt;
	private int used;
	private int ticks;
	private int feederType = -1;
	private int feederCount;
	private final int[] birdCounts = new int[BIRDS.length];
	private final Map<BlockPos, Long> nestRetries = new LinkedHashMap<>();
	private final Set<BlockPos> confirmedNests = new LinkedHashSet<>();
	private final Map<String, Long> missingSince = new HashMap<>();
	private long sequence;
	private long stateSendAt;
	private long stateRetryAt;
	private long partyMembershipChangedAt;
	private boolean active;
	private boolean transportAllowed;
	private boolean snapshotSent;
	private boolean feedDoneAnnounced;
	private boolean insideVisit;
	private String visitLobby;
	private long visitEnteredAt;
	private String lastDebugTransport = "";
	private boolean enabledLastTick;

	/** Solo owns the complete run state locally and never needs chat transport. */
	private static boolean enabledForCurrentParty() {
		return ConfigManager.get().advanced.enablePartySync
			|| PartyRosterWatch.known() && PartyRosterWatch.expectedPlayers() == 1;
	}

	@Override
	public void tick() {
		if (++ticks % 10 != 0) return;
		long now = System.currentTimeMillis();
		boolean enabled = enabledForCurrentParty();
		if (!enabled) {
			if (enabledLastTick) {
				boolean announceDisabled = transportAllowed && roster.size() > 1
					&& SafariLocation.inside();
				ChatQueue.discardContaining(MARKER);
				ChatQueue.discardContaining(ENABLE_PREFIX);
				String disabled = controlMessage('D', localName());
				if (announceDisabled && disabled != null) {
					ChatQueue.enqueueVerifiedPartyFirst("pc " + disabled);
				}
				capableMembers.clear();
				announcedRosters.clear();
				pendingCapabilityLines.clear();
				transportAllowed = false;
				active = false;
			}
			enabledLastTick = false;
			return;
		}
		enabledLastTick = true;
		if (!SafariLocation.inside()) {
			if (insideVisit) clearRun();
			insideVisit = false;
			visitLobby = null;
			visitEnteredAt = 0;
			active = false;
			transportAllowed = false;
			roster = List.of();
			return;
		}

		String lobby = SafariLocation.lobbyId();
		if (!insideVisit || lobby != null && visitLobby != null && !lobby.equals(visitLobby)) {
			clearRun();
			roster = List.of();
			active = false;
			transportAllowed = false;
			insideVisit = true;
			visitLobby = lobby;
			visitEnteredAt = SafariLocation.insideSinceMillis();
			if (visitEnteredAt == 0L) visitEnteredAt = now;
		} else if (visitLobby == null && lobby != null) {
			visitLobby = lobby;
		}
		// Objective inventory belongs to an activated run. Waiting until capsule-gated
		// activation also avoids copying the prior visit's cache before beginVisit resets it.
		if (SessionManager.current() != null) refreshLocalObjectives();

		List<Member> present = currentRoster();
		boolean validPresence = !present.isEmpty();
		// A run belongs to the complete party that entered this Safari. Current tab-list
		// presence is attendance only; ticket timing and disconnects never redefine it.
		if (roster.isEmpty() && PartyRosterWatch.rosterCapturedAt() >= visitEnteredAt) {
			List<Member> fullParty = fullPartyRoster();
			if (!fullParty.isEmpty()) {
				roster = fullParty;
				snapshotSent = false;
			}
		}
		active = !roster.isEmpty();
		if (active) capableMembers.add(localUuid());
		resolvePendingCapabilities();
		maybeAnnounceCapability();
		boolean previouslyAllowed = transportAllowed;
		boolean currentRosterFresh = PartyRosterWatch.rosterCapturedAt() >= partyMembershipChangedAt;
		List<Member> currentParty = fullPartyRoster();
		boolean currentPartyConfirmed = !currentParty.isEmpty()
			&& currentParty.stream().allMatch(member -> capableMembers.contains(member.uuid()));
		transportAllowed = active && currentRosterFresh && currentPartyConfirmed;
		if (DebugLog.isEnabled() && ConfigManager.get().advanced.logPartySync) {
			String transportState = "roster=" + roster.size() + " active=" + active
				+ " rosterFresh=" + currentRosterFresh + " confirmed=" + transportAllowed;
			if (!transportState.equals(lastDebugTransport)) {
				DebugLog.line("SYNC", "transport " + transportState);
				lastDebugTransport = transportState;
			}
		} else lastDebugTransport = "";
		if (previouslyAllowed && !transportAllowed) ChatQueue.discardContaining(MARKER);
		if (!previouslyAllowed && transportAllowed) {
			stateSendAt = 0;
			snapshotSent = true;
			sendState();
			stateRetryAt = now + RECOVERY_RETRY_MILLIS;
			sendNests(confirmedNests);
			for (BlockPos nest : confirmedNests) {
				nestRetries.put(nest, now + RECOVERY_RETRY_MILLIS);
			}
		}
		if (!active) {
			updateLocal();
			return;
		}
		for (Member member : roster) {
			states.computeIfAbsent(member.uuid(), ignored -> new State());
		}
		if (validPresence) reconcilePresence(present, now);
		if (!snapshotSent) {
			snapshotSent = true;
			stateSendAt = 0;
			sendState();
			stateRetryAt = now + RECOVERY_RETRY_MILLIS;
		}
		flushScheduled(now);
	}

	@Override
	public void onRunStarted() {
		scheduleState();
	}

	@Override
	public void onPartyMembershipChanged() {
		partyMembershipChangedAt = System.currentTimeMillis();
		ChatQueue.discardContaining(ENABLE_PREFIX);
		pendingCapabilityLines.clear();
		DebugLog.line("SYNC", "party membership changed; current run roster retained");
	}
	@Override
	public void onStartingItems(int[] counts) {
		localFound = Arrays.copyOf(counts, StartingItems.ORDERED.size());
		updateLocal();
		scheduleState();
	}

	@Override
	public void onInventoryFeed(int seeds, int worms, int berries) {
		seeds = Math.max(0, seeds);
		worms = Math.max(0, worms);
		berries = Math.max(0, berries);
		if (seeds == localSeeds && worms == localWorms && berries == localBerries) return;
		localSeeds = seeds;
		localWorms = worms;
		localBerries = berries;
		updateLocal();
		scheduleState();
	}

	@Override
	public void onBirdfeederState(int type, int count) {
		type = count > 0 ? type : -1;
		count = Math.max(0, count);
		if (feederType == type && feederCount == count) return;
		feederType = type;
		feederCount = count;
		scheduleState();
	}

	@Override
	public void onNestConfirmed(BlockPos pos) {
		if (!enabledForCurrentParty()
			|| SessionManager.current() == null) return;
		BlockPos nest = pos.immutable();
		confirmedNests.add(nest);
		if (!active) return;
		sendNests(List.of(nest));
		nestRetries.put(nest, System.currentTimeMillis() + RECOVERY_RETRY_MILLIS);
	}

	@Override
	public void onServerMessage(String line) {
		if (line.startsWith("FLOOR DROP!")) {
			int item = itemIn(line);
			if (item >= 0) {
				localFound[item]++;
				if (item >= 6) localForestDrops++;
				updateLocal();
				scheduleState();
			}
			return;
		}
		int placedGem = SafariObjectives.placedGemMaskFromMessage(line);
		if (placedGem != 0) {
			localPlacedGems |= placedGem;
			updateLocal();
			scheduleState();
			return;
		}
		if (line.startsWith("A rumbling sound can be heard")) {
			localGemzieOpen = true;
			if (gemzieOpenedAt == 0L) gemzieOpenedAt = System.currentTimeMillis();
			localPlacedGems = 7;
			updateLocal();
			scheduleState();
		}
		if (line.startsWith("You used the Soothing Incense to light the candle")) {
			localCandlesLit = Math.min(4, localCandlesLit + 1);
			updateLocal();
			scheduleState();
		}
		if (SafariObjectives.doomspiralObjectiveCompleteMessage(line)) {
			localDoomspiralSpawned = true;
			updateLocal();
			scheduleState();
		}
		if (line.startsWith("The Doomspiral retreats back underground")) {
			localDoomspiralSpawned = true;
			localDoomspiralRetreated = true;
			updateLocal();
			scheduleState();
		}
		if (line.startsWith("The Wumpa has awoken")) {
			localWumpaSpawned = true;
			updateLocal();
			scheduleState();
		}
		if (line.contains("fainted by a Wumpa") && line.endsWith("lost some of your items!")) {
			localWumpaSpawned = true;
			localWumpaRetreated = true;
			updateLocal();
			scheduleState();
		}
		int bird = birdIn(line);
		if (bird >= 0) {
			int[] next = birdCounts.clone();
			next[bird] += birdAmount(line);
			int nextCount = Math.max(0, feederCount - 1);
			int nextType = nextCount > 0 ? feederType : -1;
			if (active) {
				send("U", (used + 1) + "," + next[0] + "," + next[1] + "," + next[2]
					+ "," + nextType + "," + nextCount);
			} else {
				used++;
				feederType = nextType;
				feederCount = nextCount;
				System.arraycopy(next, 0, birdCounts, 0, next.length);
			}
			scheduleState();
			return;
		}
		var event = ChatParser.parse(line, localName());
		if (event != null && event.isCatch()) {
			if ("Gemzie".equals(event.critter().name())) {
				localGemzieOpen = true;
				localGemzieCaught = true;
				localPlacedGems = 7;
				updateLocal();
				scheduleState();
			} else if ("Doomspiral".equals(event.critter().name())) {
				localDoomspiralSpawned = true;
				localDoomspiralCaught = true;
				updateLocal();
				scheduleState();
			} else if ("Wumpa".equals(event.critter().name())) {
				localWumpaSpawned = true;
				localWumpaCaught = true;
				updateLocal();
				scheduleState();
			}
			for (int index = 0; index < BIRDS.length; index++) {
				if (!BIRDS[index].equals(event.critter().name()) || birdCounts[index] <= 0) continue;
				birdCounts[index]--;
				break;
			}
		}
	}

	@Override
	public boolean allowMessage(Component message, boolean overlay) {
		if (overlay || message == null) return true;
		String line = FORMATTING.matcher(message.getString()).replaceAll("").trim();
		var control = CONTROL.matcher(line);
		if (control.find()) {
			handleControlMessage(line, control.start(), control.group(1).charAt(0), control.group(2), true);
			return true;
		}
		int markerAt = line.indexOf(MARKER);
		if (markerAt < 0) return true;
		String sender = senderIn(line.substring(0, markerAt));
		if (sender != null && transportAllowed) {
			apply(sender, line.substring(markerAt + MARKER.length()).trim(), false);
		}
		return false;
	}

	@Override
	public boolean active() {
		return active && (roster.size() == 1 || transportAllowed);
	}

	@Override
	public boolean suppressStartingItems() {
		return active();
	}

	@Override
	public int feedRemaining() {
		return active() ? Math.max(0, totalFound() - used) : -1;
	}

	@Override
	public boolean forestDropsComplete() {
		return active() && forestDrops() >= 9;
	}

	@Override
	public boolean feedDone() {
		return active() && forestDrops() >= 9 && totalFound() > 0 && used >= totalFound();
	}

	@Override
	public boolean objectiveComplete() {
		SafariBiome biome = PartyObjectiveHud.currentBiome();
		if (biome == null) return false;
		return switch (biome) {
			case FOREST -> feedDone();
			case CAVERN -> gemzieCaught();
			case HAUNTED -> doomspiralComplete();
			case ICY -> wumpaComplete();
		};
	}

	@Override
	public boolean objectiveAllowsFloorDropHide(SafariBiome biome) {
		if (!active() || biome == null) return false;
		State local = states.get(localUuid());
		return switch (biome) {
			case FOREST -> forestDropsComplete();
			case CAVERN -> gemzieOpen() || localCanOpenGemzie(local, placedGemMask());
			case HAUNTED -> doomspiralSpawned()
				|| local != null && local.incense >= Math.max(0, 4 - candlesLit());
			case ICY -> false;
		};
	}

	@Override
	public HudPanel objectivePanel() {
		if (SessionManager.current() == null || !active()) return null;
		SafariBiome biome = PartyObjectiveHud.currentBiome();
		if (biome == null || !PartyObjectiveHud.biomeEnabled(biome)) return objectivePanelBase();
		return switch (biome) {
			case FOREST -> forestPanel();
			case CAVERN -> cavernPanel();
			case HAUNTED -> hauntedPanel();
			case ICY -> icyPanel();
		};
	}

	private HudPanel icyPanel() {
		SafariConfig.DisplayConfig config = ConfigManager.get().display;
		HudPanel panel = objectivePanelBase();
		if (wumpaComplete()) {
			if (config.partyObjectiveShowPlacement) panel.pair("Wumpa",
				wumpaCaught() ? "Caught" : "Retreated",
				PartyObjectiveHud.ICY_LINE_COLOUR, 0xFF55FF55);
			return panel;
		}
		int catches = PartyObjectiveHud.icyUniqueCatches();
		if (config.partyObjectiveShowProgress) {
			panel.pair("Unique Catches", catches + "/8", PartyObjectiveHud.ICY_LINE_COLOUR,
				catches >= 8 ? 0xFF55FF55 : 0xFFFFFFFF);
		}
		if (config.partyObjectiveShowPlacement) {
			String value = wumpaCaught() ? "Caught" : wumpaSpawned() ? "Started"
				: catches >= 8 ? "Waiting" : "Locked";
			panel.pair("Wumpa", value, PartyObjectiveHud.ICY_LINE_COLOUR,
				wumpaSpawned() ? PartyObjectiveHud.INTERMEDIATE_COLOUR : 0xFFFFFFFF);
		}
		return panel;
	}

	private HudPanel forestPanel() {
		SafariConfig.DisplayConfig config = ConfigManager.get().display;
		HudPanel panel = objectivePanelBase();
		int drops = active ? forestDrops() : localForestDrops;
		int found = active ? totalFound() : localFeedFound();
		boolean done = found > 0 && used >= found && drops >= 9;
		if (done) {
			if (config.partyObjectiveShowProgress) panel.pair("All Feed Done", found + "/" + found,
				PartyObjectiveHud.FOREST_LINE_COLOUR, 0xFF55FF55);
			HudPanel.IconValue[] birds = birds(active);
			if (config.partyObjectiveShowBirdCounts && birds != null) {
				panel.iconPair("Birds", PartyObjectiveHud.FOREST_LINE_COLOUR, birds);
			}
			return panel;
		}
		boolean showedPlayer = false;
		if (config.partyObjectiveShowPlayers) {
			for (Member member : displayRoster()) {
				State state = states.get(member.uuid());
				boolean local = member.uuid().equals(localUuid());
				boolean show = active
					? state != null && state.heldFeed() > 0
					: !local || state != null && state.heldFeed() > 0;
				if (!show) continue;
				HudPanel.IconValue[] value = active || local
					? feed(state == null ? new State() : state)
					: unknownFeed();
				PartyObjectiveHud.addPlayerRow(panel, member.name(), value);
				showedPlayer = true;
			}
		}
		if (showedPlayer) panel.blank();

		panel.pair("Forest Drops", drops >= 9 ? "✔" : Math.min(9, drops) + "/9",
			PartyObjectiveHud.FOREST_LINE_COLOUR, drops >= 9 ? 0xFF55FF55 : 0xFFFFFFFF);
		if (config.partyObjectiveShowProgress) {
			panel.pair("Feed Done", Math.min(used, found) + "/" + found,
				PartyObjectiveHud.FOREST_LINE_COLOUR, done ? 0xFF55FF55 : 0xFFFFFFFF);
		}
		if (config.partyObjectiveShowPlacement) {
			addFeederRow(panel, active ? feederType : BirdfeederWatch.feederType(),
				active ? feederCount : BirdfeederWatch.feederCount());
		}
		HudPanel.IconValue[] birds = birds(active);
		if (config.partyObjectiveShowBirdCounts && birds != null) {
			panel.iconPair("Birds", PartyObjectiveHud.FOREST_LINE_COLOUR, birds);
		}
		return panel;
	}

	private HudPanel cavernPanel() {
		SafariConfig.DisplayConfig config = ConfigManager.get().display;
		HudPanel panel = objectivePanelBase();
		if (gemzieCaught()) {
			if (config.partyObjectiveShowPlacement) PartyObjectiveHud.addPlacedGemsRow(panel, 7, true, true);
			return panel;
		}
		boolean showedPlayer = false;
		if (config.partyObjectiveShowPlayers) {
			for (Member member : displayRoster()) {
				State state = states.get(member.uuid());
				boolean local = member.uuid().equals(localUuid());
				boolean known = (active || local) && state != null && state.reported;
				if (known && state.limeGems + state.orangeGems + state.purpleGems <= 0) continue;
				HudPanel.IconValue[] values = (active || local) && state != null && state.reported
					? PartyObjectiveHud.gemValues(String.valueOf(state.purpleGems),
						String.valueOf(state.limeGems), String.valueOf(state.orangeGems))
					: PartyObjectiveHud.gemValues("?", "?", "?");
				PartyObjectiveHud.addPlayerRow(panel, member.name(), values);
				showedPlayer = true;
			}
			if (showedPlayer) panel.blank();
		}
		int placed = placedGemMask();
		if (config.partyObjectiveShowProgress) {
			int ready = Integer.bitCount(placed | heldGemMask());
			panel.pair("Gems", ready >= 3 ? "✔" : ready + "/3",
				PartyObjectiveHud.CAVERN_LINE_COLOUR, ready >= 3 ? 0xFF55FF55 : 0xFFFFFFFF);
		}
		if (config.partyObjectiveShowPlacement) PartyObjectiveHud.addPlacedGemsRow(panel, placed,
			gemzieDoorDisplayReady(), false);
		return panel;
	}

	private HudPanel hauntedPanel() {
		SafariConfig.DisplayConfig config = ConfigManager.get().display;
		HudPanel panel = objectivePanelBase();
		if (doomspiralComplete()) {
			if (config.partyObjectiveShowPlacement) panel.pair("Doomspiral",
				doomspiralCaught() ? "Caught" : "Retreated",
				PartyObjectiveHud.HAUNTED_LINE_COLOUR, 0xFF55FF55);
			return panel;
		}
		boolean showedPlayer = false;
		if (config.partyObjectiveShowPlayers) {
			for (Member member : displayRoster()) {
				State state = states.get(member.uuid());
				boolean local = member.uuid().equals(localUuid());
				boolean known = (active || local) && state != null && state.reported;
				if (known && state.incense <= 0) continue;
				HudPanel.IconValue[] values = (active || local) && state != null && state.reported
					? PartyObjectiveHud.incenseValue(String.valueOf(state.incense))
					: PartyObjectiveHud.incenseValue("?");
				PartyObjectiveHud.addPlayerRow(panel, member.name(), values);
				showedPlayer = true;
			}
			if (showedPlayer) panel.blank();
		}
		int candles = candlesLit();
		if (config.partyObjectiveShowProgress) {
			int ready = Math.min(4, candles + states.values().stream().mapToInt(state -> state.incense).sum());
			panel.pair("Incense", ready >= 4 ? "✔" : ready + "/4",
				PartyObjectiveHud.HAUNTED_LINE_COLOUR, ready >= 4 ? 0xFF55FF55 : 0xFFFFFFFF);
		}
		if (config.partyObjectiveShowPlacement) {
			String value = doomspiralCaught() ? "Caught"
				: doomspiralRetreated() ? "Retreated"
				: doomspiralSpawned() ? "Started" : candles >= 4 ? "Complete" : candles + "/4";
			panel.pair("Doomspiral", value, PartyObjectiveHud.HAUNTED_LINE_COLOUR,
				doomspiralSpawned() ? PartyObjectiveHud.INTERMEDIATE_COLOUR : 0xFFFFFFFF);
		}
		return panel;
	}

	private HudPanel objectivePanelBase() {
		return PartyObjectiveHud.objectivePanel(gemzieCaught(), wumpaComplete(),
			doomspiralComplete(), feedDone(), active && (roster.size() == 1 || transportAllowed));
	}

	/** One compact message keeps discovery and held inventory from drifting apart. */
	private void sendState() {
		String counts = Arrays.stream(localFound)
			.mapToObj(String::valueOf)
			.reduce((left, right) -> left + "," + right)
			.orElse("");
		send("A", counts + "," + localForestDrops + "," + localSeeds + "," + localWorms
			+ "," + localBerries + "," + used + "," + birdCounts[0] + "," + birdCounts[1]
			+ "," + birdCounts[2] + "," + feederType + "," + feederCount
			+ "," + localLimeGems + "," + localOrangeGems + "," + localPurpleGems
			+ "," + localIncense + "," + localPlacedGems + "," + localCandlesLit
			+ "," + (localGemzieOpen ? 1 : 0) + "," + (localDoomspiralSpawned ? 1 : 0)
			+ "," + (localDoomspiralCaught ? 1 : 0) + "," + (localWumpaSpawned ? 1 : 0)
			+ "," + (localWumpaCaught ? 1 : 0) + "," + (localDoomspiralRetreated ? 1 : 0)
			+ "," + (localGemzieCaught ? 1 : 0) + "," + (localWumpaRetreated ? 1 : 0));
	}

	private void scheduleState() {
		stateSendAt = System.currentTimeMillis() + STATE_COALESCE_MILLIS;
		stateRetryAt = 0;
	}

	/** Announces capability once for each roster containing an unconfirmed member. */
	private void maybeAnnounceCapability() {
		if (SessionManager.current() == null || roster.size() <= 1
			|| !SafariPartyWatch.readyForLocationLearning()) return;
		List<Member> present = currentRoster();
		if (present.size() != roster.size()
			|| !present.stream().map(Member::uuid).collect(java.util.stream.Collectors.toSet())
				.equals(roster.stream().map(Member::uuid)
					.collect(java.util.stream.Collectors.toSet()))) return;
		if (roster.stream().allMatch(member -> capableMembers.contains(member.uuid()))) return;
		String signature = roster.stream().map(Member::uuid).sorted().reduce((a, b) -> a + "," + b)
			.orElse("");
		if (!announcedRosters.add(signature)) return;
		capableMembers.add(localUuid());
		String message = controlMessage('E', localName());
		if (message == null) {
			announcedRosters.remove(signature);
			return;
		}
		ChatQueue.enqueueVerifiedParty("pc " + message);
		DebugLog.line("SYNC", "capability announced roster=" + roster.size());
	}

	/** Resolves readiness lines that arrived before this client froze its run roster. */
	private void resolvePendingCapabilities() {
		if (roster.isEmpty() || pendingCapabilityLines.isEmpty()) return;
		pendingCapabilityLines.removeIf(line -> {
			var control = CONTROL.matcher(line);
			if (!control.find()) return true;
			return handleControlMessage(line, control.start(),
				control.group(1).charAt(0), control.group(2), false);
		});
	}

	/** Applies a sender/lobby-bound control token; false means roster resolution is pending. */
	private boolean handleControlMessage(String line, int tokenAt, char action, String token,
		boolean retainIfPending) {
		String sender = senderIn(line.substring(0, tokenAt));
		Member member = member(sender);
		if (member == null) {
			if (retainIfPending && action == 'E' && ConfigManager.get().advanced.enablePartySync
				&& pendingCapabilityLines.size() < 8) pendingCapabilityLines.add(line);
			return false;
		}
		String expected = controlToken(action, member.name());
		if (expected == null || !MessageDigest.isEqual(
			expected.getBytes(StandardCharsets.US_ASCII), token.getBytes(StandardCharsets.US_ASCII))) {
			DebugLog.line("SYNC", "ignored invalid control token from=" + member.name());
			return true;
		}
		if (action == 'E') {
			if (ConfigManager.get().advanced.enablePartySync) capableMembers.add(member.uuid());
		} else {
			capableMembers.remove(member.uuid());
			transportAllowed = false;
			ChatQueue.discardContaining(MARKER);
			DebugLog.line("SYNC", "disabled by=" + member.name());
		}
		return true;
	}

	private static String controlMessage(char action, String username) {
		String token = controlToken(action, username);
		return token == null ? null : "[SU2" + action + ":" + token + "]";
	}

	private static String controlToken(char action, String username) {
		String lobby = SafariLocation.lobbyId();
		if (username == null || username.isBlank() || lobby == null || lobby.isBlank()) return null;
		String source = "SafariUtils/2/" + action + "/"
			+ username.toLowerCase(Locale.ROOT) + "/" + lobby.toLowerCase(Locale.ROOT);
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(source.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 12);
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}

	private void sendNests(java.util.Collection<BlockPos> positions) {
		if (positions.isEmpty()) return;
		String payload = positions.stream()
			.map(pos -> pos.getX() + "," + pos.getY() + "," + pos.getZ())
			.reduce((left, right) -> left + "," + right).orElse("");
		send("N", payload);
	}

	private void flushScheduled(long now) {
		if (stateSendAt > 0 && now >= stateSendAt) {
			stateSendAt = 0;
			sendState();
			stateRetryAt = now + RECOVERY_RETRY_MILLIS;
		} else if (stateRetryAt > 0 && now >= stateRetryAt) {
			stateRetryAt = 0;
			sendState();
		}
		List<BlockPos> dueNests = new ArrayList<>();
		var iterator = nestRetries.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			if (now < entry.getValue()) continue;
			dueNests.add(entry.getKey());
			iterator.remove();
		}
		sendNests(dueNests);
	}

	/** Preserve discoveries, but remove only feed still held when a member remains absent. */
	private void reconcilePresence(List<Member> present, long now) {
		boolean changed = false;
		Set<String> presentIds = present.stream().map(Member::uuid).collect(java.util.stream.Collectors.toSet());
		for (Member member : roster) {
			State state = states.get(member.uuid());
			if (presentIds.contains(member.uuid())) {
				missingSince.remove(member.uuid());
				continue;
			}
			long since = missingSince.computeIfAbsent(member.uuid(), ignored -> now);
			if (now - since < ROSTER_GRACE_MILLIS || state == null) continue;
			int held = state.heldFeed();
			if (held <= 0) continue;
			state.unavailableFeed = Math.max(state.unavailableFeed, held);
			state.seeds = 0;
			state.worms = 0;
			state.berries = 0;
			changed = true;
		}
		if (changed) checkFeedDone(false);
	}

	private void send(String type, String payload) {
		if (!active) return;
		String body = localUuid() + "-" + (++sequence) + ":" + type + ":" + payload;
		apply(localName(), body, true);
		if (transportAllowed && roster.size() > 1) {
			if ("A".equals(type)) ChatQueue.discardContainingAll(
				MARKER, localUuid() + "-", ":A:");
			ChatQueue.enqueueVerifiedParty("pc " + MARKER + " " + body);
		}
		DebugLog.line("SYNC", "sent type=" + type + " recipients="
			+ (transportAllowed ? Math.max(0, roster.size() - 1) : 0)
			+ " pendingChat=" + ChatQueue.pendingCount());
	}

	private void apply(String sender, String body, boolean localReporter) {
		String[] parts = body.split(":", 3);
		if (parts.length != 3 || !seen.add(parts[0])) return;
		while (seen.size() > 256) seen.remove(seen.iterator().next());
		Member member = roster.stream()
			.filter(value -> value.name().equalsIgnoreCase(sender))
			.findFirst()
			.orElse(null);
		if (member == null) return;
		State state = states.computeIfAbsent(member.uuid(), ignored -> new State());
		try {
			int[] values = Arrays.stream(parts[2].split(","))
				.mapToInt(Integer::parseInt)
				.toArray();
				switch (parts[1]) {
				case "A" -> applyAggregate(state, values);
				case "S" -> applyLegacyStartingItems(state, values);
				case "I" -> applyLegacyInventory(state, values);
				case "U" -> applyUsageMessage(values, localReporter);
				case "N" -> applyNest(values);
				default -> { }
			}
			if (!localReporter) DebugLog.line("SYNC", "received type=" + parts[1]
				+ " from=" + member.name());
		} catch (NumberFormatException ignored) {
			// Malformed hidden messages are discarded rather than affecting run state.
			DebugLog.line("SYNC", "discarded malformed message from=" + member.name());
		}
	}

	private void applyAggregate(State state, int[] values) {
		int itemCount = StartingItems.ORDERED.size();
		if (values.length != itemCount + 24
			&& values.length != itemCount + 23
			&& values.length != itemCount + 22
			&& values.length != itemCount + 21
			&& values.length != itemCount + 18
			&& values.length != itemCount + 10
			&& values.length != itemCount + 8
			&& values.length != itemCount + 4) return;
		state.found = Arrays.copyOf(values, itemCount);
		state.reported = true;
		state.forestDrops = Math.max(0, values[itemCount]);
		state.seeds = Math.max(0, values[itemCount + 1]);
		state.worms = Math.max(0, values[itemCount + 2]);
		state.berries = Math.max(0, values[itemCount + 3]);
		state.unavailableFeed = 0;
		if (values.length >= itemCount + 8) {
			int nextFeederType = feederType;
			int nextFeederCount = feederCount;
			if (values.length >= itemCount + 10) {
				int reportedCount = Math.max(0, values[itemCount + 9]);
				if (reportedCount > 0) {
					nextFeederType = values[itemCount + 8];
					nextFeederCount = reportedCount;
				}
			}
			applyUsage(values[itemCount + 4], values[itemCount + 5], values[itemCount + 6],
				values[itemCount + 7], nextFeederType, nextFeederCount, false);
		}
		if (values.length == itemCount + 18 || values.length == itemCount + 21
			|| values.length == itemCount + 22 || values.length == itemCount + 23
			|| values.length == itemCount + 24) {
			state.limeGems = Math.max(0, values[itemCount + 10]);
			state.orangeGems = Math.max(0, values[itemCount + 11]);
			state.purpleGems = Math.max(0, values[itemCount + 12]);
			state.incense = Math.max(0, values[itemCount + 13]);
			state.placedGems |= values[itemCount + 14] & 7;
			state.candlesLit = Math.max(state.candlesLit, Math.max(0, values[itemCount + 15]));
			boolean openedNow = values[itemCount + 16] != 0;
			if (openedNow && !gemzieOpen() && gemzieOpenedAt == 0L) {
				gemzieOpenedAt = System.currentTimeMillis();
			}
			state.gemzieOpen |= openedNow;
			state.doomspiralSpawned |= values[itemCount + 17] != 0;
			if (values.length >= itemCount + 21) {
				state.doomspiralCaught |= values[itemCount + 18] != 0;
				state.wumpaSpawned |= values[itemCount + 19] != 0;
				state.wumpaCaught |= values[itemCount + 20] != 0;
			}
			if (values.length >= itemCount + 22) {
				state.doomspiralRetreated |= values[itemCount + 21] != 0;
			}
			if (values.length >= itemCount + 23) state.gemzieCaught |= values[itemCount + 22] != 0;
			if (values.length == itemCount + 24) state.wumpaRetreated |= values[itemCount + 23] != 0;
		}
		checkFeedDone(false);
	}

	private void applyLegacyStartingItems(State state, int[] values) {
		if (values.length != StartingItems.ORDERED.size() + 1) return;
		state.found = Arrays.copyOf(values, StartingItems.ORDERED.size());
		state.reported = true;
		state.forestDrops = Math.max(0, values[values.length - 1]);
		checkFeedDone(false);
	}

	private static void applyLegacyInventory(State state, int[] values) {
		if (values.length != 3) return;
		state.seeds = Math.max(0, values[0]);
		state.worms = Math.max(0, values[1]);
		state.berries = Math.max(0, values[2]);
		state.reported = true;
		state.unavailableFeed = 0;
	}

	private void applyUsageMessage(int[] values, boolean localReporter) {
		if (values.length == 6) {
			applyUsage(values[0], values[1], values[2], values[3], values[4], values[5],
				localReporter);
		} else if (values.length == 4) {
			applyUsage(values[0], values[1], values[2], values[3], feederType, feederCount,
				localReporter);
		}
	}

	private static void applyNest(int[] values) {
		if (values.length == 0 || values.length % 3 != 0) return;
		for (int index = 0; index < values.length; index += 3) {
			NestTracker.onPartyConfirmed(new BlockPos(
				values[index], values[index + 1], values[index + 2]));
		}
	}

	private void applyUsage(int nextUsed, int bluebirds, int parakeets, int macaws,
		int nextFeederType, int nextFeederCount, boolean localReporter) {
		int previousUsed = used;
		int feederBefore = feederCount;
		used = Math.max(used, nextUsed);
		if (nextUsed > previousUsed) {
			birdCounts[0] = Math.max(0, bluebirds);
			birdCounts[1] = Math.max(0, parakeets);
			birdCounts[2] = Math.max(0, macaws);
		} else if (nextUsed == previousUsed) {
			// With no new feed consumed, counts can only fall through catches. Taking the
			// minimum prevents a delayed recovery snapshot from resurrecting caught birds.
			birdCounts[0] = Math.min(birdCounts[0], Math.max(0, bluebirds));
			birdCounts[1] = Math.min(birdCounts[1], Math.max(0, parakeets));
			birdCounts[2] = Math.min(birdCounts[2], Math.max(0, macaws));
		}
		if (nextUsed >= previousUsed) {
			feederCount = Math.max(0, nextFeederCount);
			feederType = feederCount > 0 ? nextFeederType : -1;
		}
		if (used > previousUsed && feederCount == 0
			&& !checkFeedDone(localReporter) && feederBefore > 0) {
			EncounterAlerts.onBirdfeederEmpty();
		}
	}

	private boolean checkFeedDone(boolean sendChat) {
		boolean done = forestDrops() >= 9 && totalFound() > 0 && used >= totalFound();
		if (done && !feedDoneAnnounced) {
			feedDoneAnnounced = true;
			EncounterAlerts.onPrivateFeedDone(sendChat);
		}
		return done;
	}

	private void updateLocal() {
		String id = localUuid();
		if (id.isEmpty()) return;
		State state = states.computeIfAbsent(id, ignored -> new State());
		state.reported = true;
		state.found = localFound.clone();
		state.forestDrops = localForestDrops;
		state.seeds = localSeeds;
		state.worms = localWorms;
		state.berries = localBerries;
		state.limeGems = localLimeGems;
		state.orangeGems = localOrangeGems;
		state.purpleGems = localPurpleGems;
		state.incense = localIncense;
		state.placedGems = localPlacedGems;
		state.candlesLit = localCandlesLit;
		state.gemzieOpen = localGemzieOpen;
		state.gemzieCaught = localGemzieCaught;
		state.doomspiralSpawned = localDoomspiralSpawned;
		state.doomspiralCaught = localDoomspiralCaught;
		state.doomspiralRetreated = localDoomspiralRetreated;
		state.wumpaSpawned = localWumpaSpawned;
		state.wumpaCaught = localWumpaCaught;
		state.wumpaRetreated = localWumpaRetreated;
		state.unavailableFeed = 0;
	}

	/** Polls the already-cached inventory objective counts; no extra inventory scan occurs here. */
	private void refreshLocalObjectives() {
		int lime = SafariObjectives.limeGemsHeld();
		int orange = SafariObjectives.orangeGemsHeld();
		int purple = SafariObjectives.purpleGemsHeld();
		int incense = SafariObjectives.incenseHeld();
		int placed = SafariObjectives.placedGemMask();
		boolean open = localGemzieOpen || SafariObjectives.gemzieDoorOpened();
		boolean gemzieDone = localGemzieCaught || SafariObjectives.gemzieCaught();
		boolean spawned = localDoomspiralSpawned || SafariObjectives.doomspiralSpawned();
		boolean doomCaught = localDoomspiralCaught || SafariObjectives.doomspiralCaught();
		boolean doomRetreated = localDoomspiralRetreated || SafariObjectives.doomspiralRetreated();
		boolean wumpaAwake = localWumpaSpawned || SafariObjectives.wumpaSpawned();
		boolean wumpaDone = localWumpaCaught || SafariObjectives.wumpaCaught();
		boolean wumpaRetreated = localWumpaRetreated || SafariObjectives.wumpaRetreated();
		int candles = Math.max(localCandlesLit, SafariObjectives.incenseUsed());
		if (lime == localLimeGems && orange == localOrangeGems && purple == localPurpleGems
			&& incense == localIncense && placed == localPlacedGems && candles == localCandlesLit
			&& open == localGemzieOpen && gemzieDone == localGemzieCaught
			&& spawned == localDoomspiralSpawned
			&& doomCaught == localDoomspiralCaught && doomRetreated == localDoomspiralRetreated
			&& wumpaAwake == localWumpaSpawned
			&& wumpaDone == localWumpaCaught
			&& wumpaRetreated == localWumpaRetreated) return;
		localLimeGems = lime;
		localOrangeGems = orange;
		localPurpleGems = purple;
		localIncense = incense;
		localPlacedGems = placed;
		localCandlesLit = candles;
		localGemzieOpen = open;
		localGemzieCaught = gemzieDone;
		localDoomspiralSpawned = spawned;
		localDoomspiralCaught = doomCaught;
		localDoomspiralRetreated = doomRetreated;
		localWumpaSpawned = wumpaAwake;
		localWumpaCaught = wumpaDone;
		localWumpaRetreated = wumpaRetreated;
		updateLocal();
		if (SafariLocation.inside()) scheduleState();
	}

	private int placedGemMask() {
		return states.values().stream().mapToInt(state -> state.placedGems).reduce(0, (a, b) -> a | b);
	}

	private int heldGemMask() {
		int mask = 0;
		for (State state : states.values()) {
			if (state.limeGems > 0) mask |= 1;
			if (state.orangeGems > 0) mask |= 2;
			if (state.purpleGems > 0) mask |= 4;
		}
		return mask;
	}

	private int candlesLit() {
		return Math.min(4, states.values().stream().mapToInt(state -> state.candlesLit).sum());
	}

	private boolean gemzieOpen() {
		return states.values().stream().anyMatch(state -> state.gemzieOpen);
	}

	private boolean gemzieDoorDisplayReady() {
		return gemzieOpen() && gemzieOpenedAt > 0L
			&& System.currentTimeMillis() - gemzieOpenedAt >= 2_500L;
	}

	private boolean gemzieCaught() {
		return states.values().stream().anyMatch(state -> state.gemzieCaught)
			|| SafariObjectives.gemzieCaught();
	}

	private boolean doomspiralSpawned() {
		return states.values().stream().anyMatch(state -> state.doomspiralSpawned);
	}

	private boolean doomspiralCaught() {
		return states.values().stream().anyMatch(state -> state.doomspiralCaught)
			|| SafariObjectives.doomspiralCaught();
	}

	private boolean doomspiralRetreated() {
		return states.values().stream().anyMatch(state -> state.doomspiralRetreated)
			|| SafariObjectives.doomspiralRetreated();
	}

	private boolean doomspiralComplete() {
		return doomspiralCaught() || doomspiralRetreated();
	}

	private boolean wumpaSpawned() {
		return states.values().stream().anyMatch(state -> state.wumpaSpawned)
			|| SafariObjectives.wumpaSpawned();
	}

	private boolean wumpaCaught() {
		return states.values().stream().anyMatch(state -> state.wumpaCaught)
			|| SafariObjectives.wumpaCaught();
	}

	private boolean wumpaRetreated() {
		return states.values().stream().anyMatch(state -> state.wumpaRetreated)
			|| SafariObjectives.wumpaRetreated();
	}

	private boolean wumpaComplete() {
		return wumpaCaught() || wumpaRetreated();
	}

	private static boolean localCanOpenGemzie(State state, int placedMask) {
		if (state == null) return false;
		return ((placedMask & 1) != 0 || state.limeGems > 0)
			&& ((placedMask & 2) != 0 || state.orangeGems > 0)
			&& ((placedMask & 4) != 0 || state.purpleGems > 0);
	}

	private int totalFound() {
		int reported = states.values().stream().mapToInt(State::availableFeed).sum();
		// Held inventory, the feeder stack, and consumed spawns together prove a
		// complete lower bound even if the frozen run-start snapshot missed one item.
		return Math.max(reported, used + totalHeld() + feederCount);
	}

	private int totalHeld() {
		return states.values().stream().mapToInt(State::heldFeed).sum();
	}

	private int forestDrops() {
		return states.values().stream().mapToInt(state -> state.forestDrops).sum();
	}

	private static void addFeederRow(HudPanel panel, int type, int count) {
		if (type < 0 || count <= 0) {
			panel.pair("Birdfeeder", "Empty", PartyObjectiveHud.FOREST_LINE_COLOUR, 0xFF888888);
			return;
		}
		HudPanel.HudIcon icon = switch (type) {
			case 0 -> HudPanel.HudIcon.SEED_BAG;
			case 1 -> HudPanel.HudIcon.WORM;
			case 2 -> HudPanel.HudIcon.BERRY;
			default -> HudPanel.HudIcon.SEED_BAG;
		};
		int colour = switch (type) {
			case 0 -> 0xFFFFD36A;
			case 1 -> 0xFFFF64AD;
			case 2 -> 0xFF2D69EA;
			default -> 0xFFFFFFFF;
		};
		panel.iconPair("Birdfeeder", PartyObjectiveHud.FOREST_LINE_COLOUR,
			new HudPanel.IconValue(icon, String.valueOf(count), colour));
	}
	private HudPanel.IconValue[] birds(boolean synced) {
		int[] colours = {0xFF1647D8, 0xFFA7E522, 0xFFF04A24};
		int[] counts = birdCounts.clone();
		if (!synced) {
			for (int index = 0; index < BIRDS.length; index++) {
				var critter = dev.serko.safariutils.data.Critters.byName(BIRDS[index]);
				counts[index] = critter == null ? 0 : DetectedCritters.currentConcurrent(critter);
			}
		}
		List<HudPanel.IconValue> values = new ArrayList<>();
		for (int index = 0; index < BIRDS.length; index++) {
			if (counts[index] <= 0) continue;
			values.add(new HudPanel.IconValue(HudPanel.HudIcon.BIRD,
				String.valueOf(counts[index]), colours[index]));
		}
		if (values.isEmpty() && !synced) {
			for (int colour : colours) {
				values.add(new HudPanel.IconValue(HudPanel.HudIcon.BIRD, "?", colour));
			}
		}
		return values.isEmpty() ? null : values.toArray(HudPanel.IconValue[]::new);
	}

	private static HudPanel.IconValue[] feed(State state) {
		return new HudPanel.IconValue[] {
			new HudPanel.IconValue(HudPanel.HudIcon.BERRY, String.valueOf(state.berries), 0xFF2D69EA),
			new HudPanel.IconValue(HudPanel.HudIcon.WORM, String.valueOf(state.worms), 0xFFFF64AD),
			new HudPanel.IconValue(HudPanel.HudIcon.SEED_BAG, String.valueOf(state.seeds), 0xFFFFD36A)
		};
	}

	private static HudPanel.IconValue[] unknownFeed() {
		return new HudPanel.IconValue[] {
			new HudPanel.IconValue(HudPanel.HudIcon.BERRY, "?", 0xFF2D69EA),
			new HudPanel.IconValue(HudPanel.HudIcon.WORM, "?", 0xFFFF64AD),
			new HudPanel.IconValue(HudPanel.HudIcon.SEED_BAG, "?", 0xFFFFD36A)
		};
	}

	private int localFeedFound() {
		return Arrays.stream(localFound, 6, 9).sum();
	}

	private List<Member> displayRoster() {
		List<Member> displayed;
		if (active && !roster.isEmpty()) displayed = roster;
		else {
			List<Member> visible = currentRoster();
			if (!visible.isEmpty()) displayed = visible;
			else if (!roster.isEmpty()) displayed = roster;
			else {
				String id = localUuid();
				displayed = id.isEmpty() ? List.of() : List.of(new Member(localName(), id));
			}
		}
		return displayed.stream().sorted(java.util.Comparator.comparing(Member::name,
			String.CASE_INSENSITIVE_ORDER)).toList();
	}

	private void clearRun() {
		if (insideVisit) DebugLog.line("SYNC", "visit cleared roster=" + roster.size()
			+ " knownNests=" + confirmedNests.size());
		ChatQueue.discardContaining(MARKER);
		ChatQueue.discardContaining(ENABLE_PREFIX);
		pendingCapabilityLines.clear();
		states.clear();
		seen.clear();
		nestRetries.clear();
		confirmedNests.clear();
		missingSince.clear();
		localFound = new int[StartingItems.ORDERED.size()];
		localForestDrops = 0;
		localSeeds = 0;
		localWorms = 0;
		localBerries = 0;
		localLimeGems = 0;
		localOrangeGems = 0;
		localPurpleGems = 0;
		localIncense = 0;
		localPlacedGems = 0;
		localCandlesLit = 0;
		localGemzieOpen = false;
		localGemzieCaught = false;
		localDoomspiralSpawned = false;
		localDoomspiralCaught = false;
		localDoomspiralRetreated = false;
		localWumpaSpawned = false;
		localWumpaCaught = false;
		localWumpaRetreated = false;
		gemzieOpenedAt = 0L;
		used = 0;
		feederCount = 0;
		feederType = -1;
		Arrays.fill(birdCounts, 0);
		sequence = 0;
		stateSendAt = 0;
		stateRetryAt = 0;
		snapshotSent = false;
		feedDoneAnnounced = false;
		lastDebugTransport = "";
	}

	private static int itemIn(String line) {
		for (int index = 0; index < StartingItems.ORDERED.size(); index++) {
			if (line.contains(StartingItems.ORDERED.get(index).inventoryName())) return index;
		}
		return -1;
	}

	private static int birdIn(String line) {
		if (!line.contains("attracted to the Birdfeeder!")) return -1;
		for (int index = 0; index < BIRDS.length; index++) {
			if (line.contains(BIRDS[index])) return index;
		}
		return -1;
	}

	private static int birdAmount(String line) {
		return line.startsWith("Two ") ? 2 : 1;
	}

	private String senderIn(String prefix) {
		String lower = prefix.toLowerCase(Locale.ROOT);
		for (Member member : roster) {
			if (lower.contains(member.name().toLowerCase(Locale.ROOT))) {
				return member.name();
			}
		}
		return null;
	}

	private Member member(String name) {
		if (name == null) return null;
		return roster.stream().filter(member -> member.name().equalsIgnoreCase(name))
			.findFirst().orElse(null);
	}

	private static List<Member> currentRoster(){
		return PartySyncRoster.presentInSafari();
	}
	/** Resolves the immutable run roster from the complete, freshly captured party list. */
	private static List<Member> fullPartyRoster(){
		return PartySyncRoster.fullParty();
	}

	private static String localUuid() {
		return PartySyncRoster.localUuid();
	}

	private static String localName() {
		return PartySyncRoster.localName();
	}

	private static final class State {
		private boolean reported;
		private int[] found = new int[StartingItems.ORDERED.size()];
		private int forestDrops;
		private int seeds;
		private int worms;
		private int berries;
		private int unavailableFeed;
		private int limeGems;
		private int orangeGems;
		private int purpleGems;
		private int incense;
		private int placedGems;
		private int candlesLit;
		private boolean gemzieOpen;
		private boolean gemzieCaught;
		private boolean doomspiralSpawned;
		private boolean doomspiralCaught;
		private boolean doomspiralRetreated;
		private boolean wumpaSpawned;
		private boolean wumpaCaught;
		private boolean wumpaRetreated;

		private State() {
		}

		private int totalFeed() {
			return Arrays.stream(found, 6, 9).sum();
		}

		private int heldFeed() {
			return seeds + worms + berries;
		}

		private int availableFeed() {
			return Math.max(0, totalFeed() - unavailableFeed);
		}
	}
}
