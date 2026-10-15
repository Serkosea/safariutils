package dev.serko.safariutils.state;

/** Strength and Safe Mode visibility of information used by Safari state. */
public enum EvidenceLevel {
	/** Directly rendered or otherwise visible to the local player. */
	VISIBLE,
	/** Established by the local player's own interaction. */
	INTERACTION,
	/** Observed in the local player's inventory or cursor stack. */
	INVENTORY,
	/** Explicitly confirmed by server chat, scoreboard, or tab-list state. */
	SERVER,
	/** Explicitly shared by a confirmed party member through the selected sync transport. */
	PARTY_SYNC,
	/** Remote profile information; never evidence for a hidden live-world entity. */
	API,
	/** A shipped or locally reviewed possible location, not proof of an active spawn. */
	STATIC_CANDIDATE,
	/** Loaded client state that was not directly visible to the player. */
	LOADED_INFERRED,
	/** Derived from other evidence and safe only when every dependency is permitted. */
	DERIVED,
	/** Developer-only observation that may never affect player-visible state. */
	DIAGNOSTIC,
	/** Legacy alias for loaded/inferred evidence retained for serialized diagnostics. */
	INFERRED
}
