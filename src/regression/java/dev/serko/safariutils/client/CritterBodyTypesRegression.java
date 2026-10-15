package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.SafariBiome;
import java.util.Map;
import java.util.Set;

/** Special species cannot acquire unrelated bodies before their dedicated fallback runs. */
public final class CritterBodyTypesRegression {
	public static int run() {
		int checks = 0;
		var expected = Map.of(
			"Gazer", Set.of("armor_stand"),
			"Duplico", Set.of("interaction", "silverfish"),
			"Bloodbat", Set.of("bat"),
			"Hideonwall", Set.of("shulker", "silverfish"),
			"Hideonfloor", Set.of("shulker", "silverfish"));
		for (var entry : expected.entrySet()) {
			Critter species = Critters.byName(entry.getKey());
			for (String type : new String[] {"armor_stand", "interaction", "silverfish", "bat",
				"shulker", "bee", "fox", "player", "item", "tropical_fish", "snow_golem"}) {
				boolean allowed = entry.getValue().contains(type);
				check(CritterEntities.acceptsBodyType(species, type) == allowed, "pairing accepted wrong type for " + species.name()); checks++;
				check(CritterEntities.verifiedBodyType(species, type) == allowed, "capture verification disagrees for " + species.name()); checks++;
			}
		}
		var unknown = new Critter("Unknown test species", SafariBiome.CAVERN, Critter.Rarity.RARE, 1);
		check(CritterEntities.acceptsBodyType(unknown, "bat"), "unknown species lost generic pairing"); checks++;
		check(!CritterEntities.verifiedBodyType(unknown, "bat"), "generic proximity became verified evidence"); checks++;
		for (String type : new String[] {"armor_stand", "interaction", "item_display", "block_display",
			"text_display", "player", "item", "painting", "item_frame", "glow_item_frame", "leash_knot",
			"experience_orb", "end_crystal", "falling_block", "lightning_bolt", "marker", "happy_ghast",
			"arrow", "spectral_arrow", "trident", "snowball", "egg", "ender_pearl", "firework_rocket",
			"tnt", "fishing_bobber"}) {
			check(!CritterEntities.isMobBodyType(type), "prop/projectile entered generic body pool: " + type); checks++;
		}
		for (String type : new String[] {"fox", "parrot", "bee", "frog", "creaking", "panda", "shulker",
			"silverfish", "tropical_fish", "bat", "slime", "zombie", "armadillo", "dolphin", "ravager",
			"goat", "vex", "polar_bear", "glow_squid", "sniffer", "snow_golem", "cave_spider", "endermite",
			"phantom", "warden", "future_mob"}) {
			check(CritterEntities.isMobBodyType(type), "generic body type excluded: " + type); checks++;
		}
		return checks;
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
	private CritterBodyTypesRegression() { }
}
