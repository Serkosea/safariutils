package dev.serko.safariutils.api;

/** Shared wire-shape constants for the authenticated Party Sync transport. */
public final class PartySyncProtocol {
	public static final int VERSION = 3;
	public static final int AGGREGATE_VALUES = 39;
	public static final int LEGACY_AGGREGATE_VALUES = 38;
	public static final int HELD_COINS_INDEX = 38;
	public static final int MAX_HELD_COINS = 64;
	public static final int AGGREGATE_VERSION_INDEX = 33;
	public static final int USAGE_VALUES = 6;
	public static final int NEST_VALUES = 1;
	public static final int SPARKLING_VALUES = 4;
	public static final int MAX_PAYLOAD_LENGTH = 3_072;

	/** The v3 coin field is appended; every v2 position keeps its meaning. */
	public static boolean validAggregateShape(int length, int version) {
		return length == AGGREGATE_VALUES && version == VERSION
			|| length == LEGACY_AGGREGATE_VALUES && version == 2;
	}

	/** -1 is an internal unknown, never a v3 wire value. */
	public static int heldCoins(int[] values) {
		return values.length == AGGREGATE_VALUES ? values[HELD_COINS_INDEX] : -1;
	}

	public static boolean validHeldCoins(int count) {
		return count >= 0 && count <= MAX_HELD_COINS;
	}

	private PartySyncProtocol() { }
}
