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
	static void discovered(Critter critter) {
        long before=state.localRevision; state.discover(Critters.selectionMask(critter));
        if(before!=state.localRevision && PartySyncDebugHud.enabled()) PartySyncDebugHud.event("Local: found Sparkling "+critter.name(),0xFF65DDEC);
    }
	static void caught(Critter critter) {
        long before=state.localRevision; state.caught(Critters.selectionMask(critter));
        if(before!=state.localRevision && PartySyncDebugHud.enabled()) PartySyncDebugHud.event("Local: caught Sparkling "+critter.name(),0xFF77E59E);
    }
	static boolean claimLocalAlert(Critter critter) {
		return state.claimLocalAlert(Critters.selectionMask(critter));
	}
	static long activeMask() { return state.active(); }
	static long sharedMask() { return state.shared(); }
	static boolean wasCaught(Critter critter) { return (state.caughtMask() & Critters.selectionMask(critter)) != 0; }
	static long localRevision() { return state.localRevision; }
	static void published() { state.published(); }
	static String payload() { return state.payload(); }
	static boolean accept(int[] values) { return accept(values, false); }
	static boolean acceptRoom(int[] values) { return accept(values, true); }
	private static boolean accept(int[] values, boolean room) {
		long before = state.active();
        long previouslyCaught = state.caughtMask();
		if (!(room ? state.acceptRoom(values) : state.accept(values))) return false;
        SparklingWatch.remoteCaught(state.caughtMask() & ~previouslyCaught);
		// Species-only remote catch masks cannot erase individually observed local bodies.
		long added = state.active() & ~before;
		// Shared discoveries update markers/reminders, not the finder's ordinary alert.
		if (before != state.active()) StructuredDiagnostics.decision("party-sparkling", "received",
			"active=" + Long.bitCount(state.active()) + " added=" + Long.bitCount(added));
		return true;
	}
	public static void tick() {
		boolean inside = HypixelConnection.active() && SafariLocation.inside()
			&& SparklingWatch.discoveryAllowed(dev.serko.safariutils.state.SafariRuntimeState.current().phase());
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
