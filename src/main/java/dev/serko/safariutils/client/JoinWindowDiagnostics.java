package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.DisplaySlot;
import dev.serko.safariutils.parse.ChatParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Measures the complete pre-ticket transition without making any event part of
 * normal run activation. The resulting timeline is retained across the island
 * transfer so {@code /su debug jointimer} can copy it after the Manager lockout.
 */
public final class JoinWindowDiagnostics {

	private static final String TICKET_LOCKOUT =
		"[NPC] Safari Manager: Dude, if you're not gonna pay, you're not gonna play.";
	private static final long EXPIRE_NANOS = 120_000_000_000L;

	private static final List<Mark> marks = new ArrayList<>();
	private static long originNanos;
	private static long originMillis;
	private static boolean active;
	private static boolean connectionSeen;
	private static boolean worldTickSeen;
	private static boolean sidebarSeen;
	private static boolean insideSeen;
	private static boolean areaSeen;
	private static boolean lobbySeen;
	private static boolean managerSeen;
	private static long connectionNanos = -1L;
	private static long worldTickNanos = -1L;
	private static long sidebarNanos = -1L;
	private static long insideNanos = -1L;
	private static long lobbyNanos = -1L;
	private static String latestReport = "No Safari join-window sample has been recorded yet\n";

	private JoinWindowDiagnostics() {
	}

	/** Starts early enough to include the entry banner that precedes Queuing. */
	public static void onSelfEntry(String line) {
		if (active) mark("self entry announcement", line);
		else begin("self entry announcement", line);
	}

	/** Records relevant server text, including wording variants around matchmaking. */
	public static void onChatMessage(String line) {
		String entrant = ChatParser.safariEntrant(line);
		if (entrant != null && !entrant.equals(Minecraft.getInstance().getUser().getName())
			&& PartyRosterWatch.isListedMember(entrant)) {
			if (active) mark("party entry announcement", line);
			else begin("party entry announcement", line);
		}
		if (ChatParser.safariQueueLine(line)) {
			if (!active) begin("queue/cutscene message", line);
			else mark("queue/cutscene message", line);
		}
		if (!active) return;
		if (line.equals("You were kicked while joining that server!")
			|| line.startsWith("You tried to rejoin too fast")) {
			mark("join failed", line);
			finish("join failed");
			return;
		}

		if (line.contains("Safari Manager")) mark("Manager dialogue", line);
		if (line.equals(TICKET_LOCKOUT)) finish("ticket lockout");
	}

	/** Fabric's play-connection event is the earliest destination-world boundary. */
	public static void onConnectionJoin() {
		if (!active) return;
		connectionSeen = true;
		connectionNanos = elapsedNanos();
		mark("play connection joined", null);
	}

	/** Captures destination state as each independently observable signal appears. */
	public static void tick(Minecraft client) {
		if (!active) return;
		if (System.nanoTime() - originNanos > EXPIRE_NANOS) {
			finish("sample expired");
			return;
		}

		// Entry chat and Queuing are emitted while the entrance world is still live.
		// Do not mistake that old world's player, area, lobby, or Manager for the
		// destination instance merely because they remain available for a few ticks.
		if (!connectionSeen) return;
		if (!worldTickSeen && client.level != null && client.player != null) {
			worldTickSeen = true;
			worldTickNanos = elapsedNanos();
			mark("first playable world tick", position(client.player));
		}
		if (!sidebarSeen && client.level != null
			&& client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR) != null) {
			sidebarSeen = true;
			sidebarNanos = elapsedNanos();
			mark("first sidebar objective", null);
		}
		if (!insideSeen && SafariLocation.inside()) {
			insideSeen = true;
			insideNanos = elapsedNanos();
			mark("first inside-Safari recognition", "source=" + SafariLocation.source());
		}
		if (!areaSeen && SafariLocation.area() != null) {
			areaSeen = true;
			mark("first stated area", SafariLocation.area());
		}
		if (!lobbySeen && SafariLocation.lobbyId() != null) {
			lobbySeen = true;
			lobbyNanos = elapsedNanos();
			mark("first lobby id", SafariLocation.lobbyId());
		}
		if (!managerSeen && client.level != null) {
			for (Entity entity : client.level.entitiesForRendering()) {
				String name = entity.hasCustomName()
					? entity.getCustomName().getString() : entity.getDisplayName().getString();
				if (!name.contains("Safari Manager")) continue;
				managerSeen = true;
				mark("first Safari Manager entity", position(entity));
				break;
			}
		}
	}

	/** Notes successful ticket acceptance without confusing it with the lockout test. */
	public static void onRunStarted(String trigger) {
		if (!active) return;
		mark("run activated", trigger);
		finish("ticket accepted");
	}

	/** Most recently completed timeline, or the live partial timeline while measuring. */
	public static String report() {
		if (!active) return latestReport;
		return format("sample still active");
	}

	private static void begin(String label, String detail) {
		originNanos = System.nanoTime();
		originMillis = System.currentTimeMillis();
		marks.clear();
		active = true;
		connectionSeen = false;
		worldTickSeen = false;
		sidebarSeen = false;
		insideSeen = false;
		areaSeen = false;
		lobbySeen = false;
		managerSeen = false;
		connectionNanos = -1L;
		worldTickNanos = -1L;
		sidebarNanos = -1L;
		insideNanos = -1L;
		lobbyNanos = -1L;
		mark(label, detail);
	}

	private static void mark(String label, String detail) {
		long elapsedNanos = elapsedNanos();
		marks.add(new Mark(elapsedNanos, label, detail));
		DebugLog.line("JOINTIME", formatMark(marks.getLast()));
	}

	private static void finish(String outcome) {
		if (!active) return;
		latestReport = format(outcome);
		active = false;
		DebugLog.line("JOINTIME", "complete outcome=" + outcome
			+ " duration=" + seconds(marks.isEmpty() ? 0L : marks.getLast().elapsedNanos()));
	}

	private static String format(String outcome) {
		StringBuilder text = new StringBuilder("Safari join-window timing\n");
		text.append("  outcome  ").append(outcome).append('\n');
		text.append("  started  ").append(originMillis).append(" epoch ms\n");
		for (Mark mark : marks) text.append("  ").append(formatMark(mark)).append('\n');
		if (!marks.isEmpty()) {
			long end = marks.getLast().elapsedNanos();
			text.append("  lockout/finish deltas\n");
			appendDelta(text, "play connection", connectionNanos, end);
			appendDelta(text, "playable world", worldTickNanos, end);
			appendDelta(text, "sidebar objective", sidebarNanos, end);
			appendDelta(text, "Safari recognition", insideNanos, end);
			appendDelta(text, "lobby id", lobbyNanos, end);
		}
		return text.toString();
	}

	private static long elapsedNanos() {
		return Math.max(0L, System.nanoTime() - originNanos);
	}

	private static void appendDelta(StringBuilder text, String label, long anchor, long end) {
		text.append("    ").append(label).append(" -> ")
			.append(anchor < 0L ? "not observed" : seconds(Math.max(0L, end - anchor)))
			.append('\n');
	}

	private static String formatMark(Mark mark) {
		return "+%8s  %s%s".formatted(seconds(mark.elapsedNanos()), mark.label(),
			mark.detail() == null || mark.detail().isBlank() ? "" : " · " + mark.detail());
	}

	private static String seconds(long nanos) {
		return String.format(Locale.ROOT, "%.3fs", nanos / 1_000_000_000.0);
	}

	private static String position(Entity entity) {
		return "%.1f %.1f %.1f".formatted(entity.getX(), entity.getY(), entity.getZ());
	}

	private record Mark(long elapsedNanos, String label, String detail) {
	}
}
