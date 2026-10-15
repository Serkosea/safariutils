package dev.serko.safariutils.state;

/** Central Safe Mode projection rules for live-world information. */
public final class EvidencePolicy {
	private EvidencePolicy() { }

	/**
	 * Whether evidence may establish an actual live-world fact in Safe Mode.
	 * Static candidates are intentionally excluded: they may only be shown as possible.
	 */
	public static boolean canRevealLiveWorld(EvidenceLevel evidence, boolean derivedFromSafeInputs) {
		if (evidence == null) return false;
		return switch (evidence) {
			case VISIBLE, INTERACTION, INVENTORY, SERVER, PARTY_SYNC -> true;
			case DERIVED -> derivedFromSafeInputs;
			case API, STATIC_CANDIDATE, LOADED_INFERRED, DIAGNOSTIC, INFERRED -> false;
		};
	}

	public static boolean canShowAsPossible(EvidenceLevel evidence) {
		return evidence == EvidenceLevel.STATIC_CANDIDATE
			|| canRevealLiveWorld(evidence, false);
	}
}
