package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/** Quietly reads /party list so attendance can be compared with the real party size. */
public final class PartyRosterWatch {
	private static final Pattern COUNT = Pattern.compile("^Party Members \\((\\d+)\\)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern LEGACY_COLOURS = Pattern.compile("§.");
	private static int expectedPlayers;
	private static boolean known;
	private static boolean capturing;
	private static boolean sawCount;
	private static boolean localLeader;
	private static boolean sawLeader;
	private static boolean leaderSeenThisCapture;
	private static long captureUntil;
	private static long requestAt;
	private static boolean announceScheduledRefresh;
	private static boolean announceCurrentRefresh;
	private static boolean wasInsideSafari;
	private static boolean wasConnected;
	private static long suppressRosterTailUntil;

	private PartyRosterWatch() {}

	public static void tick() {
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
			announceScheduledRefresh = false;
			announceCurrentRefresh = false;
			captureUntil = 0;
			suppressRosterTailUntil = 0;
		}
		wasConnected = connected;
		boolean inside = SafariLocation.inside();
		if (inside && !wasInsideSafari) schedule(false);
		wasInsideSafari = inside;
		if (requestAt > 0 && now >= requestAt) request();
		if (capturing && now > captureUntil) finishCapture();
	}

	public static boolean allow(Component message, boolean overlay) {
		if (overlay || message == null) return true;
		String line = LEGACY_COLOURS.matcher(message.getString()).replaceAll("").trim();
		String lower = line.toLowerCase(Locale.ROOT);
		if (membershipChanged(lower)) schedule(joinedParty(lower));
		var count = COUNT.matcher(line);
		if (!capturing) {
			return System.currentTimeMillis() > suppressRosterTailUntil
				|| !(count.matches() || isRosterLine(lower) || isDivider(line)
				|| lower.equals("you are not in a party right now.")
				|| lower.equals("you are not currently in a party."));
		}
		if (count.matches()) {
			expectedPlayers = Math.clamp(Integer.parseInt(count.group(1)), 1, 4);
			known = true;
			sawCount = true;
			return false;
		}
		if (lower.startsWith("party leader:")) {
			String localName = Minecraft.getInstance().getUser().getName();
			localLeader = !localName.isBlank()
				&& lower.contains(localName.toLowerCase(Locale.ROOT));
			sawLeader = true;
			leaderSeenThisCapture = true;
			return false;
		}
		if (lower.equals("you are not in a party right now.") || lower.equals("you are not currently in a party.")) {
			expectedPlayers = 1;
			known = true;
			sawCount = true;
			localLeader = true;
			sawLeader = true;
			leaderSeenThisCapture = true;
			finishCapture();
			return false;
		}
		if (isRosterLine(lower)) return false;
		if (isDivider(line)) {
			if (sawCount) finishCapture();
			return false;
		}
		return true;
	}

	public static int expectedPlayers() {
		return known ? Math.max(1, expectedPlayers) : 1;
	}

	public static boolean known() {
		return known;
	}

	/** Unknown status fails open; a confirmed solo player does not send party chat. */
	public static boolean canSendPartyChat() {
		return !known || expectedPlayers > 1;
	}

	/** The Manager itself is guarded only for the party leader; members may open its ticket menu. */
	public static boolean localPlayerIsLeader() {
		return known && sawLeader && localLeader;
	}

	private static void schedule(boolean announce) {
		long when = System.currentTimeMillis() + 250L;
		if (requestAt == 0 || when < requestAt) requestAt = when;
		announceScheduledRefresh |= announce;
	}

	private static void request() {
		requestAt = 0;
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() == null) return;
		announceCurrentRefresh = announceScheduledRefresh;
		announceScheduledRefresh = false;
		capturing = true;
		sawCount = false;
		leaderSeenThisCapture = false;
		captureUntil = System.currentTimeMillis() + 3_000L;
		suppressRosterTailUntil = captureUntil + 300L;
		client.getConnection().sendCommand("party list");
		DebugLog.line("PARTYTIME", "automatic /party list requested");
	}

	private static void finishCapture() {
		boolean announce = capturing && sawCount && announceCurrentRefresh;
		// A missing or malformed response must fail open: ticket protection should
		// never strand the player because Hypixel did not answer /party list.
		if (capturing && !sawCount) {
			known = false;
			sawLeader = false;
		}
		if (capturing && sawCount && expectedPlayers > 1 && !leaderSeenThisCapture) {
			sawLeader = false;
		}
		capturing = false;
		sawCount = false;
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
			|| line.contains("party was disbanded")
			|| line.contains("party was transferred to") || line.contains(" has promoted ")
			|| line.contains("you have joined ") && line.endsWith("'s party!");
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
