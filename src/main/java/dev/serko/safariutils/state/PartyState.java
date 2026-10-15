package dev.serko.safariutils.state;

import java.util.List;

/** Publishes one immutable party snapshot for every party-aware subsystem. */
public final class PartyState {
	private static volatile PartySnapshot snapshot = new PartySnapshot(0L, 0L,
		PartySnapshot.Status.UNKNOWN, false, 1, List.of(), 0L, List.of());

	private PartyState() { }

	public static PartySnapshot snapshot() {
		return snapshot;
	}

	public static synchronized void publishRoster(long generation, long capturedAt,
			PartySnapshot.Status status, boolean localLeader, int expected,
			List<PartySnapshot.Member> members) {
		boolean sameGeneration = generation == snapshot.generation();
		snapshot = new PartySnapshot(generation, capturedAt, status, localLeader, expected,
			members, sameGeneration ? snapshot.attendanceCapturedAtMillis() : 0L,
			sameGeneration ? snapshot.presentInSafari() : List.of());
	}

	public static synchronized void transitioning(long generation) {
		boolean sameGeneration = generation == snapshot.generation();
		snapshot = new PartySnapshot(generation, snapshot.capturedAtMillis(),
			PartySnapshot.Status.TRANSITIONING, snapshot.localLeader(),
			snapshot.expectedPlayers(), snapshot.members(),
			sameGeneration ? snapshot.attendanceCapturedAtMillis() : 0L,
			sameGeneration ? snapshot.presentInSafari() : List.of());
	}

	public static synchronized void publishAttendance(long capturedAt,
			List<PartySnapshot.Member> present) {
		snapshot = new PartySnapshot(snapshot.generation(), snapshot.capturedAtMillis(),
			snapshot.status(), snapshot.localLeader(), snapshot.expectedPlayers(),
			snapshot.members(), capturedAt, present);
	}

	public static synchronized void clear(long generation) {
		snapshot = new PartySnapshot(generation, 0L, PartySnapshot.Status.UNKNOWN,
			false, 1, List.of(), 0L, List.of());
	}
}
