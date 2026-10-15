package dev.serko.safariutils.state;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Owns monotonically increasing lifecycle identities. Trackers attach state to these
 * identities so delayed observations cannot mutate a later lobby or run.
 */
public final class SafariRuntimeState {
	private static long nextConnection;
	private static long nextVisit;
	private static long nextRun;
	private static SafariEpoch current = new SafariEpoch(0L, 0L, 0L, null, SafariEpoch.Phase.OUTSIDE);
	private static long revision;
	private static SafariRunSnapshot snapshot = new SafariRunSnapshot(current, 0L,
		System.currentTimeMillis(), System.nanoTime(), "initial");
	private static Consumer<Transition> transitionListener = ignored -> { };

	public record Transition(String reason, SafariEpoch before, SafariRunSnapshot after) { }

	private SafariRuntimeState() {
	}

	public static synchronized SafariEpoch current() {
		return current;
	}

	public static synchronized SafariRunSnapshot snapshot() {
		return snapshot;
	}

	public static synchronized void setTransitionListener(Consumer<Transition> listener) {
		transitionListener = listener == null ? ignored -> { } : listener;
	}

	public static synchronized SafariEpoch connectionJoined() {
		if (current.run() > 0L && (current.phase() == SafariEpoch.Phase.ACTIVE
				|| current.phase() == SafariEpoch.Phase.TRANSFERRING)) {
			return transition("connection-joined", new SafariEpoch(++nextConnection,
				current.visit(), current.run(), current.lobbyId(), SafariEpoch.Phase.TRANSFERRING));
		}
		return transition("connection-joined", new SafariEpoch(++nextConnection, 0L,
			0L, null, SafariEpoch.Phase.ENTRANCE));
	}

	public static synchronized SafariEpoch visitStarted(String lobbyId) {
		return transition("visit-started", new SafariEpoch(current.connection(), ++nextVisit, 0L,
			lobbyId, SafariEpoch.Phase.VISIT));
	}

	public static synchronized SafariEpoch lobbyIdentified(String lobbyId) {
		if (Objects.equals(lobbyId, current.lobbyId())) {
			if (current.phase() == SafariEpoch.Phase.TRANSFERRING && current.run() > 0L) {
				return transition("transfer-resumed", new SafariEpoch(current.connection(),
					current.visit(), current.run(), lobbyId, SafariEpoch.Phase.ACTIVE));
			}
			return current;
		}
		SafariEpoch.Phase phase = current.phase();
		if (phase == SafariEpoch.Phase.ENTRANCE && current.visit() == 0L) {
			phase = SafariEpoch.Phase.ENTRANCE;
		}
		return transition("lobby-identified", new SafariEpoch(current.connection(), current.visit(),
			current.run(), lobbyId, phase));
	}

	public static synchronized SafariEpoch runStarted(String lobbyId) {
		return transition("run-started", new SafariEpoch(current.connection(), current.visit(),
			++nextRun, lobbyId, SafariEpoch.Phase.ACTIVE));
	}

	public static synchronized SafariEpoch runClosed() {
		if (current.phase() == SafariEpoch.Phase.CLOSED) return current;
		return transition("run-closed", new SafariEpoch(current.connection(), current.visit(),
			current.run(), current.lobbyId(), SafariEpoch.Phase.CLOSED));
	}

	public static synchronized SafariEpoch visitEnded() {
		if (current.phase() == SafariEpoch.Phase.OUTSIDE && current.visit() == 0L) return current;
		return transition("visit-ended", new SafariEpoch(current.connection(), 0L, 0L,
			null, SafariEpoch.Phase.OUTSIDE));
	}

	public static synchronized SafariEpoch disconnected() {
		if (current.run() > 0L && (current.phase() == SafariEpoch.Phase.ACTIVE
				|| current.phase() == SafariEpoch.Phase.TRANSFERRING)) {
			return transition("transfer-started", new SafariEpoch(++nextConnection,
				current.visit(), current.run(), current.lobbyId(), SafariEpoch.Phase.TRANSFERRING));
		}
		return transition("disconnected", new SafariEpoch(++nextConnection, 0L, 0L,
			null, SafariEpoch.Phase.OUTSIDE));
	}

	private static SafariEpoch transition(String reason, SafariEpoch next) {
		SafariEpoch before = current;
		current = next;
		snapshot = new SafariRunSnapshot(next, ++revision, System.currentTimeMillis(),
			System.nanoTime(), reason);
		transitionListener.accept(new Transition(reason, before, snapshot));
		return next;
	}
}
