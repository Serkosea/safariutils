package dev.serko.safariutils.api;

/** Shared wire-shape constants for the authenticated Party Sync transport. */
public final class PartySyncProtocol {
	public static final int VERSION = 2;
	public static final int AGGREGATE_VALUES = 38;
	public static final int AGGREGATE_VERSION_INDEX = 33;
	public static final int USAGE_VALUES = 6;
	public static final int NEST_VALUES = 1;
	public static final int MAX_PAYLOAD_LENGTH = 3_072;

	private PartySyncProtocol() { }
}
