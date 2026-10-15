package dev.serko.safariutils.state;

/** Immutable lifecycle view shared by tracking, diagnostics, and rendering. */
public record SafariRunSnapshot(SafariEpoch epoch, long revision,
		long transitionedAtMillis, long transitionedAtNanos, String reason) {
	public boolean inVisit() {
		return epoch.visit() > 0L && epoch.phase() != SafariEpoch.Phase.OUTSIDE;
	}

	public boolean active() {
		return epoch.phase() == SafariEpoch.Phase.ACTIVE;
	}
}
