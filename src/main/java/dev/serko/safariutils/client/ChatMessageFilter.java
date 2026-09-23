package dev.serko.safariutils.client;

import dev.serko.safariutils.parse.ChatParser;
import net.minecraft.network.chat.Component;

import java.util.Set;

/** Display-only filtering for recognized Safari server messages. */
public final class ChatMessageFilter {
	private static final int RUN_START = 1;
	private static final int CATCH_ATTEMPTS = 1 << 1;
	private static final int CRITTER_CATCHES = 1 << 2;
	private static final int LOOT_SHARES = 1 << 3;
	private static final int FLOOR_DROPS = 1 << 4;
	private static final int MANAGER = 1 << 5;
	private static final int GEMZIE = 1 << 6;
	private static final int CHUCKWALLA = 1 << 7;
	private static final int ROCKMITE = 1 << 8;
	private static final int SCRAPPY = 1 << 9;
	private static final int SHYWORM = 1 << 10;
	private static final int SNOOZLE = 1 << 11;
	private static final int WUMPA = 1 << 12;
	private static final int TROODON = 1 << 13;
	private static final int COLD = 1 << 14;
	private static final int DOOMSPIRAL = 1 << 15;
	private static final int BLOODBAT = 1 << 16;
	private static final int DUPLICO = 1 << 17;
	private static final int GAZER = 1 << 18;
	private static final int GIMMIEGOLD = 1 << 19;
	private static final int HIDEYHO = 1 << 20;
	private static final int BIRD_SPAWNS = 1 << 21;
	private static final int EMPTY_NESTS = 1 << 22;

	private static final Set<String> ROCKMITE_MESSAGES = Set.of(
		"Small cracks begin to form in the mound...",
		"The cracks seem to be getting larger, keep hitting it!",
		"Chunks of the mound begin falling away...",
		"The mound is about to fall to pieces! Keep going!",
		"The mound falls apart, but nothing is inside...",
		"The mound fell apart, revealing a Rockmite hidden inside!"
	);
	private static final Set<String> CHUCKWALLA_MESSAGES = Set.of(
		"The Chuckwalla dodged your critter capsule and started running away!",
		"The Chuckwalla is cornered! Catch it!"
	);
	private static final Set<String> SCRAPPY_MESSAGES = Set.of(
		"This particular Scrappy doesn't seem to care for this type of food...",
		"The Scrappy cautiously started eating the Wholesale Wheat.",
		"The Scrappy reaches out its paw for more Wholesale Wheat.",
		"The Scrappy ate the Wholesale Wheat and came out of its shell!",
		"The Scrappy, shocked at your betrayal, decided to attack!",
		"The Scrappy cautiously started eating the Flavor-packed Fish.",
		"The Scrappy reaches out its paw for more Flavor-packed Fish.",
		"The Scrappy ate the Flavor-packed Fish and came out of its shell!",
		"The Scrappy cautiously started eating the Lush Lily Pad.",
		"The Scrappy reaches out its paw for more Lush Lily Pad.",
		"The Scrappy ate the Lush Lily Pad and came out of its shell!",
		"Your Critter Capsule can't get through Scrappy's armor!"
	);
	private static final Set<String> SHYWORM_MESSAGES = Set.of(
		"The Shyworm surfaced nearby! Don't let it see you!",
		"The Shyworm hid back into the ground.",
		"The Shyworm fled because it was startled!"
	);
	private static final Set<String> SNOOZLE_MESSAGES = Set.of(
		"You ran headfirst into the wall. Some more cracks appeared in it...",
		"Keep running into the wall! More cracks are forming!",
		"You destroyed the wall! Maybe something was hiding behind it..."
	);
	private static final Set<String> COLD_MESSAGES = Set.of(
		"BRRR! It's getting really cold! But you've got to keep moving...",
		"BRRR! It's so cold that you can barely feel your fingers. Moving is getting difficult...",
		"BRRR! Your movement slows to a crawl as the cold threatens to take over. Time to get out of here...",
		"BRRR! You're freezing! All you can think about is getting out of here to a warm campfire..."
	);
	private static final Set<String> GAZER_MESSAGES = Set.of(
		"You wake up suddenly...",
		"You don't feel like sleeping in that bed again..."
	);

	private ChatMessageFilter() { }

	/** Called only after normal automation/sync filters have accepted the message. */
	public static boolean shouldHide(Component message, boolean overlay) {
		if (overlay || message == null) return false;
		int selected = ConfigManager.get().display.hiddenChatMessages;
		if (selected == 0) return false;
		for (String part : message.getString().split("\\r?\\n|\\\\n")) {
			String line = ChatParser.clean(part);
			if (line.isEmpty() || ChatParser.playerSaid(line)) continue;
			if ((selected & RUN_START) != 0 && runStartMessage(line)) return true;
			if ((selected & CATCH_ATTEMPTS) != 0 && ChatParser.catchAttemptMessage(line)) return true;
			if ((selected & CRITTER_CATCHES) != 0 && line.startsWith("CAPTURE!")) return true;
			if ((selected & LOOT_SHARES) != 0 && line.startsWith("LOOT SHARE!")) return true;
			if ((selected & FLOOR_DROPS) != 0 && line.startsWith("FLOOR DROP!")) return true;
			if ((selected & MANAGER) != 0 && line.startsWith("[NPC] Safari Manager:")) return true;
			if ((selected & GEMZIE) != 0 && gemzieMessage(line)) return true;
			if ((selected & CHUCKWALLA) != 0 && CHUCKWALLA_MESSAGES.contains(line)) return true;
			if ((selected & ROCKMITE) != 0 && ROCKMITE_MESSAGES.contains(line)) return true;
			if ((selected & SCRAPPY) != 0 && SCRAPPY_MESSAGES.contains(line)) return true;
			if ((selected & SHYWORM) != 0 && SHYWORM_MESSAGES.contains(line)) return true;
			if ((selected & SNOOZLE) != 0 && SNOOZLE_MESSAGES.contains(line)) return true;
			if ((selected & WUMPA) != 0 && wumpaMessage(line)) return true;
			if ((selected & TROODON) != 0 && line.equals(
				"You used your Icebreaker to shatter the ice and free the Troodon trapped within!")) return true;
			if ((selected & COLD) != 0 && COLD_MESSAGES.contains(line)) return true;
			if ((selected & DOOMSPIRAL) != 0 && doomspiralMessage(line)) return true;
			if ((selected & BLOODBAT) != 0 && line.equals("You startled the Bloodbat!")) return true;
			if ((selected & DUPLICO) != 0 && line.equals(
				"You found a Duplico that was disguised as a block!")) return true;
			if ((selected & GAZER) != 0 && GAZER_MESSAGES.contains(line)) return true;
			if ((selected & GIMMIEGOLD) != 0 && line.equals(
				"A Gimmiegold appeared out of nowhere and gobbled up your Shining Coin!")) return true;
			if ((selected & HIDEYHO) != 0 && line.startsWith("[MOB] Hideyho:")) return true;
			if ((selected & BIRD_SPAWNS) != 0 && BirdfeederWatch.isBirdSpawnMessage(line)) return true;
			if ((selected & EMPTY_NESTS) != 0 && line.equals("Looks like the hive is empty now...")) return true;
		}
		return false;
	}

	private static boolean runStartMessage(String line) {
		return line.startsWith("HEAD START!") || line.startsWith("HOTSPOT!");
	}

	private static boolean gemzieMessage(String line) {
		return line.startsWith("You placed the ") && line.endsWith(" Gem on its podium!")
			|| line.startsWith("A rumbling sound can be heard");
	}

	private static boolean wumpaMessage(String line) {
		return line.startsWith("You hear the sound of massive footsteps")
			|| line.startsWith("The Wumpa has awoken")
			|| line.startsWith("The cave opens up again")
			|| line.contains("fainted by a Wumpa") && line.endsWith("lost some of your items!");
	}

	private static boolean doomspiralMessage(String line) {
		return line.startsWith("You used the Soothing Incense to light the candle")
			|| line.startsWith("Your ritual summoned a Doomspiral")
			|| line.startsWith("Something stirs in the Haunted Biome")
			|| line.startsWith("The darkness in the Haunted Biome fades away")
			|| line.startsWith("The Doomspiral retreats back underground");
	}
}
