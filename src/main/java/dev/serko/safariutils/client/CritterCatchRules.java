package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;

/** Shared catch guarantees used by both pity titles and recatch tracking. */
final class CritterCatchRules {
	private static final int EAGLE_UNCOMMON = 2;

	private CritterCatchRules() {
	}

	static boolean guaranteedWithoutPity(Critter critter) {
		if (critter == null) return false;
		if (critter.rarity() == Critter.Rarity.COMMON) return true;
		return critter.rarity() == Critter.Rarity.UNCOMMON
			&& ConfigManager.get().display.eagleRarity >= EAGLE_UNCOMMON;
	}
}
