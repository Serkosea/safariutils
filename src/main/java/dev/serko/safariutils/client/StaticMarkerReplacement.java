package dev.serko.safariutils.client;

/** A spatial guess may replace one absent marker, never a cluster or an active owner. */
final class StaticMarkerReplacement<T> {
	private T candidate;
	private int matches;
	private boolean blocked;

	void consider(T value, boolean protectedOwner) {
		java.util.Objects.requireNonNull(value);
		matches++;
		if (matches == 1) candidate = value;
		blocked |= protectedOwner;
	}

	T replacement() { return matches == 1 && !blocked ? candidate : null; }
}
