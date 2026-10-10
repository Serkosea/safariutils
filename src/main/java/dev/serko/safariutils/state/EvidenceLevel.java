package dev.serko.safariutils.state;

/** Strength and Safe Mode visibility of information used by Safari state. */
public enum EvidenceLevel {
	/** Directly rendered or otherwise visible to the local player. */
	VISIBLE,
	/** Established by the local player's own interaction. */
	INTERACTION,
	/** Explicitly confirmed by server chat, scoreboard, or tab-list state. */
	SERVER,
	/** Derived from information that conservative Safe Mode may not expose. */
	INFERRED
}
