package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Quietly reads /party list so attendance can be compared with the real party size. */
public final class PartyRosterWatch {
	public enum State { UNKNOWN, SOLO, PARTY, TRANSITIONING }

	private static final long REFRESH_DELAY_MILLIS = 250L;
	private static final long CAPTURE_TIMEOUT_MILLIS = 3_000L;
	private static final long RESPONSE_TAIL_MILLIS = 300L;
	private static final Pattern COUNT = Pattern.compile("^Party Members \\((\\d+)\\)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern LEGACY_COLOURS = Pattern.compile("§.");
	private static int expectedPlayers;
	private static boolean known;
	private static boolean capturing;
	private static boolean sawCount;
	private static boolean localLeader;
	private static boolean sawLeader;
	private static int pendingExpectedPlayers;
	private static boolean pendingLocalLeader;
	private static boolean sawLeaderThisCapture;
	private static long captureUntil;
	private static long requestAt;
	private static long refreshDeferredUntil;
	private static boolean announceScheduledRefresh;
	private static boolean announceCurrentRefresh;
	private static boolean wasInsideSafari;
	private static boolean wasConnected;
	private static long suppressRosterTailUntil;
	private static final List<String> pendingRosterLines = new ArrayList<>();
	private static List<String> rosterLines = List.of();
	private static long rosterCapturedAt;
	private static State state = State.UNKNOWN;
	private static long generation;

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
			sawLeader = false;
			schedule(false);
		} else if (!connected && wasConnected) {
			known = false;
			sawLeader = false;
			capturing = false;
			requestAt = 0;
			refreshDeferredUntil = 0;
			announceScheduledRefresh = false;
			announceCurrentRefresh = false;
			captureUntil = 0;
			suppressRosterTailUntil = 0;
			pendingRosterLines.clear();
			rosterLines = List.of();
			rosterCapturedAt = 0L;
		}
		wasConnected = connected;
		boolean inside = SafariLocation.inside();
		if (inside && !wasInsideSafari) schedule(false);
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
				expectedPlayers = 1;
				known = true;
				localLeader = true;
				sawLeader = true;
				state = State.SOLO;
				rosterLines = List.of();
				rosterCapturedAt = System.currentTimeMillis();
				capturing = false;
				requestAt = 0L;
				pendingRosterLines.clear();
				DebugLog.line("PARTYTIME", "authoritative solo transition generation=" + generation);
			} else {
				state = State.TRANSITIONING;
				schedule(joinedParty(lower));
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

	public static int expectedPlayers() {
		return known() ? Math.max(1, expectedPlayers) : 1;
	}

	public static boolean known() {
		return known && state != State.TRANSITIONING;
	}

	public static State state() {
		return state;
	}

	/** Changes whenever an authoritative party composition event is observed. */
	public static long generation() {
		return generation;
	}

	/** Whether the last authoritative party-list response contains another player. */
	public static boolean inParty() {
		return known() && state == State.PARTY && expectedPlayers > 1;
	}

	public static boolean confirmedSolo() {
		return known() && state == State.SOLO;
	}

	/** Last complete roster response. Failed refreshes never erase this stable snapshot. */
	public static List<String> rosterLines() {
		return rosterLines;
	}

	/** True only for a name in the most recently confirmed party-list response. */
	public static boolean isListedMember(String name) {
		if (!known() || name == null || name.isBlank()) return false;
		Pattern exactName = Pattern.compile("(?i)(?<![A-Za-z0-9_])"
			+ Pattern.quote(name) + "(?![A-Za-z0-9_])");
		return rosterLines.stream().anyMatch(line -> exactName.matcher(line).find());
	}

	public static long rosterCapturedAt() {
		return rosterCapturedAt;
	}

	/** Ordinary party chat is sent only from a currently confirmed multi-player party. */
	public static boolean canSendPartyChat() {
		return inParty();
	}

	/** The Manager itself is guarded only for the party leader; members may open its ticket menu. */
	public static boolean localPlayerIsLeader() {
		return known() && sawLeader && localLeader;
	}

	private static void schedule(boolean announce) {
		long when = Math.max(System.currentTimeMillis() + REFRESH_DELAY_MILLIS,
			refreshDeferredUntil);
		if (requestAt == 0 || when < requestAt) requestAt = when;
		announceScheduledRefresh |= announce;
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
		state = known ? State.TRANSITIONING : State.UNKNOWN;
		wasConnected = true;
		wasInsideSafari = false;
		schedule(false);
	}

	private static void request() {
		requestAt = 0;
		Minecraft client = Minecraft.getInstance();
		if (!eligible() || client.getConnection() == null) return;
		announceCurrentRefresh = announceScheduledRefresh;
		announceScheduledRefresh = false;
		capturing = true;
		sawCount = false;
		pendingExpectedPlayers = 1;
		pendingLocalLeader = false;
		sawLeaderThisCapture = false;
		pendingRosterLines.clear();
		captureUntil = System.currentTimeMillis() + CAPTURE_TIMEOUT_MILLIS;
		suppressRosterTailUntil = captureUntil + RESPONSE_TAIL_MILLIS;
		client.getConnection().sendCommand("party list");
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
		sawLeader = false;
		capturing = false;
		requestAt = 0;
		refreshDeferredUntil = 0;
		announceScheduledRefresh = false;
		announceCurrentRefresh = false;
		captureUntil = 0;
		suppressRosterTailUntil = 0;
		pendingRosterLines.clear();
		rosterLines = List.of();
		rosterCapturedAt = 0L;
		state = State.UNKNOWN;
		if (hadState) generation++;
		wasInsideSafari = false;
		wasConnected = false;
	}

	private static void finishCapture() {
		boolean complete = capturing && sawCount
			&& (pendingExpectedPlayers == 1 || sawLeaderThisCapture);
		boolean announce = complete && announceCurrentRefresh;
		// A missing or malformed response must fail open: ticket protection should
		// never strand the player because Hypixel did not answer /party list.
		if (complete) {
			boolean changed = !known || expectedPlayers != pendingExpectedPlayers
				|| localLeader != pendingLocalLeader
				|| !rosterLines.equals(pendingRosterLines);
			expectedPlayers = pendingExpectedPlayers;
			localLeader = pendingLocalLeader;
			sawLeader = true;
			known = true;
			rosterLines = List.copyOf(pendingRosterLines);
			rosterCapturedAt = System.currentTimeMillis();
			state = expectedPlayers > 1 ? State.PARTY : State.SOLO;
			if (changed) generation++;
		} else {
			// A timed-out refresh must not leave a previously confirmed snapshot in
			// TRANSITIONING forever. Keep it available until another complete reply.
			state = known ? (expectedPlayers > 1 ? State.PARTY : State.SOLO) : State.UNKNOWN;
		}
		capturing = false;
		sawCount = false;
		sawLeaderThisCapture = false;
		announceCurrentRefresh = false;
		captureUntil = 0;
		if (announce) {
			ClientMessages.send("Party list refreshed (" + expectedPlayers + " player"
				+ (expectedPlayers == 1 ? "" : "s") + ")", ClientMessages.Tone.SUCCESS);
		}
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

	private static boolean joinedParty(String line) {
		return line.contains("you have joined ") && line.endsWith("'s party!");
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
