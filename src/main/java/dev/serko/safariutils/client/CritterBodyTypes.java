package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import java.util.Map;
import java.util.Set;

/** Shared species evidence and generic entity eligibility, independent of a client world. */
final class CritterBodyTypes {
	/** Props/projectiles are never generic critter bodies; special bodies use separate pools. */
	private static final Set<String> NON_MOB_BODY_TYPES = Set.of(
		"armor_stand", "interaction", "item_display", "block_display", "text_display", "player", "item",
		"painting", "item_frame", "glow_item_frame", "leash_knot", "experience_orb", "end_crystal",
		"falling_block", "lightning_bolt", "marker", "happy_ghast", "arrow", "spectral_arrow",
		"trident", "snowball", "egg", "ender_pearl", "firework_rocket", "tnt", "fishing_bobber");
	/** Body types repeatedly verified in research logs; unlisted species retain generic pairing. */
	private static final Map<String, Set<String>> EXPECTED_BODY_TYPES = Map.ofEntries(
		Map.entry("Foxtrot", Set.of("fox")),
		Map.entry("Bluebird", Set.of("parrot")),
		Map.entry("Honeybug", Set.of("bee")),
		Map.entry("Treefrog", Set.of("frog")),
		Map.entry("Woodchucker", Set.of("creaking")),
		Map.entry("Fluffling", Set.of("panda")),
		Map.entry("Hideonfloor", Set.of("shulker", "silverfish")),
		Map.entry("Parakeet", Set.of("parrot")),
		Map.entry("Macaw", Set.of("parrot")),
		Map.entry("Cavernfish", Set.of("tropical_fish")),
		Map.entry("Flitter", Set.of("bat")),
		Map.entry("Shyworm", Set.of("slime", "zombie")),
		Map.entry("Driftling", Set.of("silverfish")),
		Map.entry("Chuckwalla", Set.of("silverfish")),
		Map.entry("Rockmite", Set.of("silverfish")),
		Map.entry("Scrappy", Set.of("armadillo")),
		Map.entry("Mantis Shrimp", Set.of("tropical_fish")),
		Map.entry("Gimmiegold", Set.of("tropical_fish")),
		Map.entry("Nozzlenose", Set.of("dolphin")),
		Map.entry("Wumpa", Set.of("ravager")),
		Map.entry("Billygoat", Set.of("goat")),
		Map.entry("Gemzie", Set.of("vex")),
		Map.entry("Polaris", Set.of("polar_bear")),
		Map.entry("Shuddersquid", Set.of("glow_squid")),
		Map.entry("Snoozle", Set.of("sniffer")),
		Map.entry("Tepid", Set.of("tropical_fish")),
		Map.entry("Troodon", Set.of("silverfish")),
		Map.entry("Strongarm", Set.of("snow_golem")),
		Map.entry("Areita", Set.of("cave_spider")),
		Map.entry("Bloodbat", Set.of("bat")),
		Map.entry("Gazer", Set.of("armor_stand")),
		Map.entry("Litterbug", Set.of("endermite")),
		Map.entry("Solsnatcher", Set.of("phantom")),
		Map.entry("Hideonwall", Set.of("shulker", "silverfish")),
		Map.entry("Duplico", Set.of("interaction", "silverfish")),
		Map.entry("Doomspiral", Set.of("warden"))
	);

	static boolean known(Critter critter) { return EXPECTED_BODY_TYPES.containsKey(critter.name()); }
	static boolean verified(Critter critter, String type) {
		Set<String> expected = EXPECTED_BODY_TYPES.get(critter.name());
		return expected != null && expected.contains(type);
	}
	static boolean accepts(Critter critter, String type) { return !known(critter) || verified(critter, type); }
	static boolean generic(String type) { return !NON_MOB_BODY_TYPES.contains(type); }
	private CritterBodyTypes() { }
}
