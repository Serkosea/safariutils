package dev.serko.safariutils.state;

/**
 * Owns monotonically increasing lifecycle identities. Trackers attach state to these
 * identities so delayed observations cannot mutate a later lobby or run.
 */
public final class SafariRuntimeState {
	private static long nextConnection;
	private static long nextVisit;
	private static long nextRun;
	private static SafariEpoch current = new SafariEpoch(0L, 0L, 0L, null, SafariEpoch.Phase.OUTSIDE);

	private SafariRuntimeState() {
	}

	public static SafariEpoch current() {
		return current;
	}

	public static SafariEpoch connectionJoined() {
		current = new SafariEpoch(++nextConnection, current.visit(), current.run(),
			current.lobbyId(), current.phase());
		return current;
	}

	public static SafariEpoch visitStarted(String lobbyId) {
		current = new SafariEpoch(current.connection(), ++nextVisit, 0L,
			lobbyId, SafariEpoch.Phase.VISIT);
		return current;
	}

	public static SafariEpoch lobbyIdentified(String lobbyId) {
		if (java.util.Objects.equals(lobbyId, current.lobbyId())) return current;
		current = new SafariEpoch(current.connection(), current.visit(), current.run(),
			lobbyId, current.phase());
		return current;
	}

	public static SafariEpoch runStarted(String lobbyId) {
		current = new SafariEpoch(current.connection(), current.visit(), ++nextRun,
			lobbyId, SafariEpoch.Phase.ACTIVE);
		return current;
	}

	public static SafariEpoch runClosed() {
		current = new SafariEpoch(current.connection(), current.visit(), current.run(),
			current.lobbyId(), SafariEpoch.Phase.CLOSED);
		return current;
	}

	public static SafariEpoch visitEnded() {
		current = new SafariEpoch(current.connection(), 0L, 0L, null, SafariEpoch.Phase.OUTSIDE);
		return current;
	}

	public static SafariEpoch disconnected() {
		current = new SafariEpoch(++nextConnection, 0L, 0L, null, SafariEpoch.Phase.OUTSIDE);
		return current;
	}
}
