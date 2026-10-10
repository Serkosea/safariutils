package dev.serko.safariutils.state;

/** Immutable identity of the connection, Safari visit, and activated run owning an event. */
public record SafariEpoch(long connection, long visit, long run, String lobbyId, Phase phase) {
	public enum Phase {
		OUTSIDE,
		VISIT,
		ACTIVE,
		CLOSED
	}

	public boolean sameRun(SafariEpoch other) {
		return other != null && run > 0L && run == other.run;
	}

	public boolean sameVisit(SafariEpoch other) {
		return other != null && visit > 0L && visit == other.visit;
	}
}
