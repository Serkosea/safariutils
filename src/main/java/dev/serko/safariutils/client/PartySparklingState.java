package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critters;

/** Visit-scoped, monotone discovery facts. Remote facts are never re-published as local facts. */
final class PartySparklingState {
	private long localFound, localCaught, remoteFound, found, caught;
	private long locallyAlertedSpecies, publishedFound;
    void published() { publishedFound |= localFound; }
	long localRevision;
	long active() { return found & ~caught; }
	long shared() { return remoteFound & ~caught; }
	long caughtMask() { return caught; }
	long reminder(boolean inside, boolean ticketed, long remainingMillis) {
		return inside && !ticketed && remainingMillis > 0 ? active() : 0;
	}
	/** Suppress the first local counterpart of a previously shared find, not a second individual. */
	boolean claimLocalAlert(long bit) {
		boolean sharedFirst = (remoteFound & bit) != 0 && (locallyAlertedSpecies & bit) == 0;
		locallyAlertedSpecies |= bit;
		return !sharedFirst;
	}
	void discover(long bit) {
		long next = localFound | bit;
		if (next != localFound) { localFound = next; localRevision++; }
		found |= bit;
	}
	void caught(long bit) {
		long next = localCaught | bit;
		if (next != localCaught) { localCaught = next; localRevision++; }
		caught |= bit;
	}
	String payload() {
		return (localFound & 0x3fffffffL) + "," + (localFound >>> 30) + ","
			+ (localCaught & 0x3fffffffL) + "," + (localCaught >>> 30);
	}
	boolean accept(int[] values) { return accept(values, false); }
	boolean acceptRoom(int[] values) { return accept(values, true); }
	private boolean accept(int[] values, boolean room) {
		if (!valid(values)) return false;
		long incomingFound = values[0] | ((long) values[1] << 30);
		remoteFound |= room ? incomingFound & ~publishedFound : incomingFound;
		found |= incomingFound;
		caught |= values[2] | ((long) values[3] << 30);
		return true;
	}
	static boolean valid(int[] values) {
		if (values.length != 4) return false;
		long mask = Critters.allSelectionMask();
		for (int i = 0; i < 4; i++) {
			long allowed = (i % 2 == 0) ? mask & 0x3fffffffL : mask >>> 30;
			if (values[i] < 0 || (values[i] & ~allowed) != 0) return false;
		}
		return true;
	}
	void clear() { localFound = localCaught = remoteFound = found = caught = locallyAlertedSpecies = publishedFound = 0; localRevision++; }
}
