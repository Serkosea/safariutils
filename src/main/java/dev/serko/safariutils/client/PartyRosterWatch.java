package dev.serko.safariutils.client;

import dev.serko.safariutils.api.PartyItemSyncProviders;
import dev.serko.safariutils.api.SharedSparklingProviders;
import dev.serko.safariutils.state.PartySnapshot;
import dev.serko.safariutils.state.PartyState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Quietly reads /party list so attendance can be compared with the real party size. */
public final class PartyRosterWatch {
	private enum State { UNKNOWN, SOLO, PARTY, TRANSITIONING }

	private static final long REFRESH_DELAY_MILLIS = 250L;
	private static final long CAPTURE_TIMEOUT_MILLIS = 3_000L;
	private static final long RESPONSE_TAIL_MILLIS = 300L;
	private static final Pattern COUNT = Pattern.compile("^Party Members \\((\\d+)\\)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern LEGACY_COLOURS = Pattern.compile("§.");
	private static final Pattern RANK = Pattern.compile("\\[[^]]+\\]");
	private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
	private static int expectedPlayers;
	private static boolean known;
	private static boolean capturing;
	private static boolean sawCount;
	private static boolean localLeader;
	private static int pendingExpectedPlayers;
	private static boolean pendingLocalLeader;
	private static boolean sawLeaderThisCapture;
	private static long captureUntil;
	private static long requestAt;
	private static long refreshDeferredUntil;
	private static boolean wasInsideSafari;
	private static boolean wasConnected;
	private static long suppressRosterTailUntil;
	private static final List<String> pendingRosterLines = new ArrayList<>();
	private static List<String> rosterLines = List.of();
	private static long rosterCapturedAt;
	private static State state = State.UNKNOWN;
	private static long generation;
	/** True only when a definitive membership message already advanced the generation. */
	private static boolean membershipTransitionPending;

	private PartyRosterWatch() {}

	public static void tick() {
		if (!eligible()) {
			resetConnectionState();
			return;
		}
		long now = System.currentTimeMillis();
		boolean connected = Minecraft.getInstance().getConnection() != null;
		if (connected && !wasConnected) {
			known = false;
			schedule();
		} else if (!connected && wasConnected) {
			known = false;
			capturing = false;
			requestAt = 0;
			refreshDeferredUntil = 0;
			captureUntil = 0;
			suppressRosterTailUntil = 0;
			pendingRosterLines.clear();
			rosterLines = List.of();
			rosterCapturedAt = 0L;
		}
		wasConnected = connected;
		boolean inside = SafariLocation.inside();
		if (inside && !wasInsideSafari) schedule();
		wasInsideSafari = inside;
		if (requestAt > 0 && now >= requestAt) request();
		if (capturing && now > captureUntil) finishCapture();
	}

	public static boolean allow(Component message, boolean overlay) {
		if (!eligible()) return true;
		if (overlay || message == null) return true;
		String line = LEGACY_COLOURS.matcher(message.getString()).replaceAll("").trim();
		String lower = line.toLowerCase(Locale.ROOT);
		if (membershipChanged(lower)) {
			generation++;
			if (authoritativeSolo(lower)) {
				membershipTransitionPending = false;
				expectedPlayers = 1;
				known = true;
				localLeader = true;
				state = State.SOLO;
				rosterLines = List.of();
				rosterCapturedAt = System.currentTimeMillis();
				publishSnapshot();
				capturing = false;
				requestAt = 0L;
				pendingRosterLines.clear();
				DebugLog.line("PARTYTIME", "authoritative solo transition generation=" + generation);
				onConfirmedMembershipChange();
			} else {
				membershipTransitionPending = true;
				state = State.TRANSITIONING;
				PartyState.transitioning(generation);
				schedule();
			}
		}
		var count = COUNT.matcher(line);
		if (!capturing) {
			return System.currentTimeMillis() > suppressRosterTailUntil
				|| !(count.matches() || isRosterLine(lower) || isDivider(line)
				|| lower.equals("you are not in a party right now.")
				|| lower.equals("you are not currently in a party."));
		}
		if (count.matches()) {
			pendingExpectedPlayers = Math.max(1, Integer.parseInt(count.group(1)));
			sawCount = true;
			return false;
		}
		if (lower.startsWith("party leader:")) {
			pendingRosterLines.add(line);
			String localName = Minecraft.getInstance().getUser().getName();
			pendingLocalLeader = !localName.isBlank()
				&& lower.contains(localName.toLowerCase(Locale.ROOT));
			sawLeaderThisCapture = true;
			return false;
		}
		if (lower.equals("you are not in a party right now.") || lower.equals("you are not currently in a party.")) {
			pendingExpectedPlayers = 1;
			sawCount = true;
			pendingLocalLeader = true;
			sawLeaderThisCapture = true;
			finishCapture();
			return false;
		}
		if (isRosterLine(lower)) {
			pendingRosterLines.add(line);
			return false;
		}
		if (isDivider(line)) {
			if (sawCount) finishCapture();
			return false;
		}
		return true;
	}

	private static int expectedPlayers() {
		return known() ? Math.max(1, expectedPlayers) : 1;
	}

	private static boolean known() {
		return known && state != State.TRANSITIONING;
	}

	private static void schedule() {
		long when = Math.max(System.currentTimeMillis() + REFRESH_DELAY_MILLIS,
			refreshDeferredUntil);
		if (requestAt == 0 || when < requestAt) requestAt = when;
	}

	/** Keeps automatic roster commands out of a time-sensitive command sequence. */
	static void deferRefreshUntil(long when) {
		refreshDeferredUntil = Math.max(refreshDeferredUntil, when);
		if (requestAt > 0L && requestAt < refreshDeferredUntil) {
			requestAt = refreshDeferredUntil;
		}
	}

	/** Preserves the last snapshot across a Hypixel server transfer while requesting a fresh one. */
	public static void onConnectionJoin() {
		capturing = false;
		requestAt = 0L;
		captureUntil = 0L;
		suppressRosterTailUntil = 0L;
		pendingRosterLines.clear();
		membershipTransitionPending = false;
		state = known ? State.TRANSITIONING : State.UNKNOWN;
		PartyState.transitioning(generation);
		wasConnected = true;
		wasInsideSafari = false;
		schedule();
	}

	private static void request() {
		requestAt = 0;
		Minecraft client = Minecraft.getInstance();
		if (!eligible() || client.getConnection() == null) return;
		var command = CommandCoordinator.request("party list",
			CommandCoordinator.Category.ROSTER, CommandCoordinator.Cooldown.NONE,
			CommandCoordinator.Scope.CONNECTION, false, false);
		if (!CommandCoordinator.trySend(command)) {
			requestAt = System.currentTimeMillis() + REFRESH_DELAY_MILLIS;
			return;
		}
		capturing = true;
		sawCount = false;
		pendingExpectedPlayers = 1;
		pendingLocalLeader = false;
		sawLeaderThisCapture = false;
		pendingRosterLines.clear();
		captureUntil = System.currentTimeMillis() + CAPTURE_TIMEOUT_MILLIS;
		suppressRosterTailUntil = captureUntil + RESPONSE_TAIL_MILLIS;
		DebugLog.line("PARTYTIME", "automatic /party list requested");
	}

	private static boolean eligible() {
		return HypixelConnection.active()
			&& (SafariLocation.inSkyblock() || SafariLocation.inSafari());
	}

	/** Clears all automatic command and capture state at a server boundary. */
	public static void resetConnectionState() {
		boolean hadState = known || state != State.UNKNOWN || capturing || requestAt > 0L
			|| !rosterLines.isEmpty();
		known = false;
		capturing = false;
		requestAt = 0;
		refreshDeferredUntil = 0;
		captureUntil = 0;
		suppressRosterTailUntil = 0;
		pendingRosterLines.clear();
		rosterLines = List.of();
		rosterCapturedAt = 0L;
		membershipTransitionPending = false;
		state = State.UNKNOWN;
		if (hadState) {
			generation++;
			PartyState.clear(generation);
		} else {
			PartySnapshot snapshot = PartyState.snapshot();
			if (snapshot.status() != PartySnapshot.Status.UNKNOWN
					|| !snapshot.members().isEmpty() || !snapshot.presentInSafari().isEmpty()) {
				PartyState.clear(generation);
			}
		}
		wasInsideSafari = false;
		wasConnected = false;
	}

	private static void finishCapture() {
		boolean complete = capturing && sawCount
			&& (pendingExpectedPlayers == 1 || sawLeaderThisCapture);
		// A missing or malformed response must fail open: ticket protection should
		// never strand the player because Hypixel did not answer /party list.
		if (complete) {
			boolean transitionPending = membershipTransitionPending;
			boolean changed = !known || expectedPlayers != pendingExpectedPlayers
				|| localLeader != pendingLocalLeader
				|| !rosterNames(rosterLines).equals(rosterNames(pendingRosterLines));
			expectedPlayers = pendingExpectedPlayers;
			localLeader = pendingLocalLeader;
			known = true;
			rosterLines = List.copyOf(pendingRosterLines);
			rosterCapturedAt = System.currentTimeMillis();
			state = expectedPlayers > 1 ? State.PARTY : State.SOLO;
			// A definitive membership message already advanced the generation when it
			// invalidated the prior roster. A later /party list response confirms that
			// same change rather than creating a second logical party generation.
			if (changed && !transitionPending) generation++;
			publishSnapshot();
			if (changed) onConfirmedMembershipChange();
		} else {
			// A timed-out refresh must not leave a previously confirmed snapshot in
			// TRANSITIONING forever. Keep it available until another complete reply.
			state = known ? (expectedPlayers > 1 ? State.PARTY : State.SOLO) : State.UNKNOWN;
			publishSnapshot();
			StructuredDiagnostics.decision("party", "refresh-incomplete",
				"generation=" + generation + ",restored=" + state);
		}
		capturing = false;
		sawCount = false;
		sawLeaderThisCapture = false;
		membershipTransitionPending = false;
		captureUntil = 0;
	}

	private static void onConfirmedMembershipChange() {
		// Party-derived API and network state refresh only from this authoritative
		// membership source. Consumers retain their useful cached result until the
		// confirmed replacement is ready.
		SharedSparklingProviders.onPartyMembershipChanged();
		PartyItemSyncProviders.onPartyMembershipChanged();
		StructuredDiagnostics.decision("party", "membership-confirmed",
			"generation=" + generation + ",expected=" + expectedPlayers());
	}

	private static void publishSnapshot() {
		PartySnapshot.Status status = switch (state) {
			case UNKNOWN -> PartySnapshot.Status.UNKNOWN;
			case SOLO -> PartySnapshot.Status.SOLO;
			case PARTY -> PartySnapshot.Status.PARTY;
			case TRANSITIONING -> PartySnapshot.Status.TRANSITIONING;
		};
		PartyState.publishRoster(generation, rosterCapturedAt, status, localLeader,
			expectedPlayers(), parsedMembers());
		StructuredDiagnostics.decision("party", "snapshot", "generation=" + generation
			+ ",status=" + status + ",expected=" + expectedPlayers()
			+ ",leader=" + localLeader);
	}

	private static List<PartySnapshot.Member> parsedMembers() {
		Minecraft client = Minecraft.getInstance();
		java.util.LinkedHashMap<String, PartySnapshot.Member> members = new java.util.LinkedHashMap<>();
		if (client.player != null) {
			String name = client.player.getGameProfile().name();
			String uuid = client.player.getGameProfile().id().toString();
			members.put(name.toLowerCase(Locale.ROOT), new PartySnapshot.Member(name, uuid));
		}
		java.util.Map<String, String> onlineUuids = new java.util.HashMap<>();
		if (client.getConnection() != null) {
			client.getConnection().getOnlinePlayers().forEach(info -> onlineUuids.put(
				info.getProfile().name().toLowerCase(Locale.ROOT), info.getProfile().id().toString()));
		}
		for (String line : rosterLines) {
			int colon = line.indexOf(':');
			if (colon < 0) continue;
			String names = RANK.matcher(line.substring(colon + 1)).replaceAll(" ");
			for (String token : names.split("\\s+")) {
				String cleaned = token.replace("●", "").replace("○", "").trim();
				if (!USERNAME.matcher(cleaned).matches() || "None".equalsIgnoreCase(cleaned)) continue;
				String key = cleaned.toLowerCase(Locale.ROOT);
				members.putIfAbsent(key, new PartySnapshot.Member(cleaned, onlineUuids.get(key)));
			}
		}
		return List.copyOf(members.values());
	}

	/** Stable composition identity; formatting, online dots, and row ordering are not changes. */
	private static java.util.Set<String> rosterNames(List<String> lines) {
		java.util.Set<String> names = new java.util.TreeSet<>();
		for (String line : lines) {
			int colon = line.indexOf(':');
			if (colon < 0) continue;
			String values = RANK.matcher(line.substring(colon + 1)).replaceAll(" ");
			for (String token : values.split("\\s+")) {
				String cleaned = token.replace("●", "").replace("○", "").trim();
				if (USERNAME.matcher(cleaned).matches() && !"None".equalsIgnoreCase(cleaned)) {
					names.add(cleaned.toLowerCase(Locale.ROOT));
				}
			}
		}
		return names;
	}

	private static boolean membershipChanged(String line) {
		return line.endsWith(" joined the party.") || line.endsWith(" has left the party.")
			|| line.contains(" was removed from your party") || line.contains("removed from the party")
			|| line.contains(" was kicked from the party") || line.contains("you left the party")
			|| line.contains("party was disbanded") || line.contains("has disbanded the party")
			|| line.contains("party was transferred to") || line.contains(" has promoted ")
			|| line.contains("you have joined ") && line.endsWith("'s party!");
	}

	private static boolean authoritativeSolo(String line) {
		return line.contains("you left the party") || line.contains("party was disbanded")
			|| line.contains("has disbanded the party");
	}

	private static boolean isRosterLine(String line) {
		return line.startsWith("party leader:") || line.startsWith("party moderators:")
			|| line.startsWith("party members:");
	}

	private static boolean isDivider(String line) {
		if (line.length() < 8) return false;
		for (int index = 0; index < line.length(); index++) {
			char character = line.charAt(index);
			if (character != '-' && character != '═' && character != '━' && character != '▬') return false;
		}
		return true;
	}
}
