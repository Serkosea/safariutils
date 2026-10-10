package dev.serko.safariutils.state;

import dev.serko.safariutils.parse.CritterEvent;

/** One normalized, run-scoped observation consumed by Safari systems. */
public record SafariEvent(long sequence, long wallMillis, long monotonicNanos,
		SafariEpoch epoch, Type type, EvidenceLevel evidence, String source,
		String rawText, CritterEvent critterEvent) {
	public enum Type {
		SERVER_CHAT,
		CRITTER_ATTEMPT,
		CRITTER_BREAKOUT,
		CRITTER_CATCH,
		SAFARI_ENTRY
	}
}
