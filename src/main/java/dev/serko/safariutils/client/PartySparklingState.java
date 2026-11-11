package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critters;

/** Visit-scoped, monotone discovery facts. Remote facts are never re-published as local facts. */
final class PartySparklingState {
	private long localFound, localCaught, remoteFound, found, caught;
	private long locallyAlertedSpecies, publishedFound;
    private long presentedSpecies, locallyPresentedSpecies;
    /** Presentation is local to this client, independent of who may broadcast the find. */
    long claimSharedPresentation(long mask) {
        long added = mask & ~presentedSpecies;
        presentedSpecies |= mask;
        return added;
    }
    /** Called once per real individual by SparklingWatch; a shared first sighting is
     * already presented, but a second genuine individual still deserves an alert. */
    boolean claimLocalPresentation(long bit) {
        boolean firstLocal = (locallyPresentedSpecies & bit) == 0;
        locallyPresentedSpecies |= bit;
        boolean fresh = (presentedSpecies & bit) == 0;
        presentedSpecies |= bit;
        return fresh || !firstLocal;
    }
    void published() { publishedFound |= localFound; }
	long localRevision;
    boolean locallyFound(long bit) { return (localFound & bit)!=0; }
	long active() { return found & ~caught; }
	long shared() { return remoteFound & ~caught; }
	long caughtMask() { return caught; }
	long reminder(boolean inside, boolean ticketed, long remainingMillis) {
		return inside && !ticketed && remainingMillis > 0 ? active() : 0;
	}
	/** Suppress the first local counterpart of a previously shared find, not a second individual. */
	boolean claimFoundChat(long bit) {
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
	void clear() { localFound = localCaught = remoteFound = found = caught = locallyAlertedSpecies = publishedFound = presentedSpecies = locallyPresentedSpecies = 0; localRevision++; }
}
