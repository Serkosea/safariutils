package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critters;

/** Visit-scoped, monotone discovery facts. Remote facts are never re-published as local facts. */
final class PartySparklingState {
	private long localFound, localCaught, remoteFound, found, caught;
	long localRevision;
	long active() { return found & ~caught; }
	long shared() { return remoteFound & ~caught; }
	long caughtMask() { return caught; }
	long reminder(boolean inside, boolean ticketed, long remainingMillis) {
		return inside && !ticketed && remainingMillis > 0 ? active() : 0;
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
	boolean accept(int[] values) {
		if (!valid(values)) return false;
		remoteFound |= values[0] | ((long) values[1] << 30);
		found |= remoteFound;
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
	void clear() { localFound = localCaught = remoteFound = found = caught = 0; localRevision++; }
}
