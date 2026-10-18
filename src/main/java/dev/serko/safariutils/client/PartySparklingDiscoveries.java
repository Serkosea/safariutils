package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.session.SessionManager;
import dev.serko.safariutils.state.MonotonicTime;
import java.util.stream.Collectors;

/** Shared discovery facts and one ticket reminder, independent of trading automation. */
public final class PartySparklingDiscoveries {
	private static final PartySparklingState state = new PartySparklingState();
	private static long displayed, nextSound;
	private PartySparklingDiscoveries() { }
	static void discovered(Critter critter) { state.discover(Critters.selectionMask(critter)); }
	static void caught(Critter critter) { state.caught(Critters.selectionMask(critter)); }
	static long activeMask() { return state.active(); }
	static long sharedMask() { return state.shared(); }
	static boolean wasCaught(Critter critter) { return (state.caughtMask() & Critters.selectionMask(critter)) != 0; }
	static long localRevision() { return state.localRevision; }
	static String payload() { return state.payload(); }
	static boolean accept(int[] values) {
		long before = state.active();
		if (!state.accept(values)) return false;
		SparklingWatch.onSharedCaught(state.caughtMask());
		long added = state.active() & ~before;
		if (added != 0 && SessionManager.current() != null)
			EncounterAlerts.fireSparklingDetected(names(added));
		if (before != state.active()) StructuredDiagnostics.decision("party-sparkling", "received",
			"active=" + Long.bitCount(state.active()) + " added=" + Long.bitCount(added));
		return true;
	}
	public static void tick() {
		boolean inside = HypixelConnection.active() && SafariLocation.inside();
		boolean ticketed = SessionManager.current() != null;
		long remaining = SessionManager.ticketWindowRemainingMillis();
		long eligible = state.reminder(inside, ticketed, remaining);
		long now = MonotonicTime.nowMillis();
		if (eligible != displayed) {
			if (eligible == 0) EncounterAlerts.clearSparklingTicketReminder();
			else EncounterAlerts.showSparklingTicketReminder(names(eligible), Long.MAX_VALUE, true, displayed == 0);
			StructuredDiagnostics.decision("party-sparkling", eligible == 0 ? "reminder-stopped" : "reminder-started",
				"species=" + Long.bitCount(eligible) + " reason="
					+ (!inside ? "outside" : ticketed ? "ticket-used" : remaining <= 0 ? "window-closed"
						: eligible == 0 ? "caught" : "discovery"));
			if (displayed == 0) nextSound = now + 8_000;
			displayed = eligible;
		}
		if (displayed != 0 && now >= nextSound) {
			EncounterAlerts.playSparklingTicketSound();
			nextSound = now + 8_000;
		}
	}
	static String names(long mask) {
		return Critters.selectionOrder().stream().filter(c -> (mask & Critters.selectionMask(c)) != 0)
			.map(Critter::name).collect(Collectors.joining(", "));
	}
	static void reset() {
		state.clear(); displayed = nextSound = 0;
		EncounterAlerts.clearSparklingTicketReminder();
	}
}
