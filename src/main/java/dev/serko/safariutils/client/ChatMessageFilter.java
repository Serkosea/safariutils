package dev.serko.safariutils.client;

import dev.serko.safariutils.parse.ChatParser;
import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	private static final int DROP_ITEMS = 1 << 23;
	private static final int NPCS = 1 << 24;
	private static final long TRADE_CONTEXT_MILLIS = 30_000L;
	private static TradeContext tradeContext;
	private static long tradeContextAt;
	private static final Pattern BILLY_COST = Pattern.compile(
		"^\\[NPC] Hunter Billy: I'll trade it to you in exchange for an? (.+)\\.$");
	private static final Pattern DENNIS_COST = Pattern.compile(
		"^\\[NPC] Hunter Dennis: You can h-h-have it if you give m-m-me an? (.+)\\.\\.\\.$");
	private static final Pattern HARRY_COST = Pattern.compile(
		"^\\[NPC] Hunter Harry: I'll give you it in exchange for an? (.+)!$");
	private static final Pattern MELISSA_COST = Pattern.compile(
		"^\\[NPC] Huntress Melissa: How about I give you it in exchange for, say, an? (.+)\\?$");
	private static final Pattern[] TRADE_COST_PATTERNS = {
		BILLY_COST, DENNIS_COST, HARRY_COST, MELISSA_COST
	};

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
		"Your Critter Capsule can't get through Scrappy's armor!",
		"[NPC] Hunter Jasper: That little ball right here is actually a Scrappy.",
		"[NPC] Hunter Jasper: Critter Capsules don't seem to do anything to them.",
		"[NPC] Hunter Jasper: \"Hunter\" Tobias had mentioned to me that you can lure them out with Food.",
		"[NPC] Hunter Jasper: He said that they might like either Wholesale Wheat, Flavor-packed Fish, or Lush Lily Pads.",
		"[NPC] Hunter Luigi: See that little creature next to me? That's actually a Scrappy that curled itself into a ball.",
		"[NPC] Hunter Luigi: My Critter Capsules seem to just bounce right off it!",
		"[NPC] Hunter Luigi: That \"Hunter\" Tobias guy told me that they love Food.",
		"[NPC] Hunter Luigi: He said that they might be motivated to come out if given either Wholesale Wheat, Flavor-packed Fish, or Lush Lily Pads.",
		"[NPC] Hunter Reeves: That little ball nearby is actually a Scrappy all curled up.",
		"[NPC] Hunter Reeves: My Critter Capsules are useless against it in this state...",
		"[NPC] Hunter Reeves: However, that \"Hunter\" Tobias guy told me that you can sometimes coax them into coming out with Food.",
		"[NPC] Hunter Reeves: He said that each Scrappy is different, and they like either Wholesale Wheat, Flavor-packed Fish, or Lush Lily Pads."
	);
	private static final Set<String> NPC_MESSAGES = Set.of(
		"[NPC] Hunter Lennard: I saw a really strange  Critter the other day...",
		"[NPC] Hunter Lennard: It looked different from the usual stuff I see around here - it was SPARKLING...",
		"[NPC] Hunter Lennard: I stopped what I was doing and tried my best to catch it...but it got away.",
		"[NPC] Hunter Lennard: Haven't seen one since - they must be pretty rare...",
		"[NPC] Hunter Lennard: Perhaps \"Hunter\" Tobias knows something about them?",
		"[NPC] Huntress Alissa: There are lots of useful things lying about on the ground in the Critter Safari.",
		"[NPC] Huntress Alissa: Us hunters like to call them Floor Drops.",
		"[NPC] Huntress Alissa: Certain  Critters can only be found with the help of specific floor drops. How exciting!",
		"That's not a  Critter...",
		"[NPC] Birdwatcher Tim: The Birdfeeder in this here Forest Biome is simply amazing!",
		"[NPC] Birdwatcher Tim: If you place some food in it and wait a while, a Bird will show up!",
		"[NPC] Birdwatcher Tim: Different birds seem to prefer different foods, though.",
		"[NPC] Birdwatcher Tim: From what I can tell, Bluebirds prefer Yogi Berries...",
		"[NPC] Birdwatcher Tim: Parakeets prefer Wriggleworms...",
		"[NPC] Birdwatcher Tim: And Macaws, the rarest of the lot, prefer a good ol' Bag of Seeds!",
		"[NPC] Birdwatcher Tim: From what I've seen, you're more likely to attract a certain type of Bird if you use its favorite food...",
		"[NPC] Hunter Billy: Hey there!",
		"[NPC] Hunter Billy: Do you want it?",
		"[NPC] Hunter Billy: Alright, alright. Guess it's worthless...",
		"[NPC] Hunter Billy: Trying to scam me? I'll hunt YOU!",
		"[NPC] Hunter Billy: Nice doing business with ya!",
		"[NPC] Hunter Dennis: Brr!",
		"[NPC] Hunter Dennis: It's f-f-f-freezing out here - d-d-don't sit around for t-t-too long!",
		"[NPC] Hunter Dennis: Fair enough.",
		"[NPC] Hunter Dennis: Watch your s-s-step...",
		"[NPC] Hunter Dennis: Let's g-g-goo! T-t-t-thanks...",
		"[NPC] Hunter Dennis: Stop trying to s-s-swindle me!",
		"[NPC] Hunter Dennis: Awesome trade! T-t-thanks!",
		"[NPC] Hunter Harry: Phew!",
		"[NPC] Hunter Harry: Glad to find someone else out here - this place is scary!",
		"[NPC] Hunter Harry: Erm, okay.",
		"[NPC] Hunter Harry: Don't go too far, it's scary...",
		"[NPC] Hunter Harry: Don't mess with me!",
		"[NPC] Hunter Harry: Sweet, 'ppreciate it!",
		"[NPC] Hunter Harry: Thanks for the trade!",
		"[NPC] Huntress Melissa: Heyyy!",
		"[NPC] Huntress Melissa: Hmph, suit yourself.",
		"[NPC] Huntress Melissa: Yippee! Thanks for the trade!",
		"[NPC] Huntress Melissa: Come back when you find one!",
		"[NPC] Huntress Melissa: I'll let you know if I ever find another Shard to trade!"
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
		// These phrases are only Safari-specific while the player is in an active
		// Safari instance. Do not suppress an unrelated server message elsewhere.
		if (overlay || message == null || !SafariLocation.inside()) return false;
		int selected = ConfigManager.get().display.hiddenChatMessages;
		if (selected == 0) return false;
		for (String part : message.getString().split("\\r?\\n|\\\\n")) {
			String line = ChatParser.clean(part);
			if (line.isEmpty() || ChatParser.playerSaid(line)) continue;
			if ((selected & NPCS) != 0) {
				rememberTrade(message, line);
				if (npcMessage(line)) return true;
			}
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
			if ((selected & DROP_ITEMS) != 0 && dropItemsMessage(line)) return true;
		}
		return false;
	}

	/** Adds trade context without replacing the selector's clickable components. */
	public static Component modify(Component message, boolean overlay) {
		if (overlay || message == null || !SafariLocation.inside()
				|| (ConfigManager.get().display.hiddenChatMessages & NPCS) == 0) return message;
		String line = ChatParser.clean(message.getString());
		if (!line.startsWith("Select an option:") || !line.contains("Trade")
				|| !line.contains("No thanks") || tradeContext == null
				|| System.currentTimeMillis() - tradeContextAt > TRADE_CONTEXT_MILLIS) return message;
		TradeContext trade = tradeContext;
		if (!trade.complete()) return message;
		tradeContext = null;
		MutableComponent result = message.copy();
		result.append(Component.literal("\nTrade: ").withStyle(ChatFormatting.WHITE));
		result.append(coloured(trade.payment, trade.paymentColour));
		result.append(Component.literal(" -> ").withStyle(ChatFormatting.WHITE));
		result.append(coloured(trade.shard, trade.shardColour));
		return result;
	}

	private static Component coloured(String text, int colour) {
		return Component.literal(text).withStyle(style -> style.withColor(colour));
	}

	private static void rememberTrade(Component message, String line) {
		Critter critter = traderShard(line);
		if (critter != null) {
			String shard = critter.name() + " Shard";
			tradeContext = new TradeContext(null, 0xFFFFFF, shard,
				componentColour(message, shard, critter.rarity().colour()));
			tradeContextAt = System.currentTimeMillis();
			return;
		}

		String payment = traderCost(line);
		if (payment == null || tradeContext == null
				|| System.currentTimeMillis() - tradeContextAt > TRADE_CONTEXT_MILLIS) return;
		tradeContext = new TradeContext(payment,
			componentColour(message, payment, fallbackCostColour(payment)),
			tradeContext.shard, tradeContext.shardColour);
		tradeContextAt = System.currentTimeMillis();
	}

	private static boolean npcMessage(String line) {
		return NPC_MESSAGES.contains(line)
			|| traderShard(line) != null
			|| traderCost(line) != null
			|| line.startsWith("[NPC] Hunter Billy: Done deal! Enjoy the ")
				&& line.endsWith(" Shard, my dude!")
			|| line.startsWith("[NPC] Hunter Billy: Are you trying to scam me? You don't have a ")
				&& line.endsWith(".")
			|| line.startsWith("[NPC] Hunter Dennis: You don't even have a ")
				&& line.endsWith("!")
			|| line.startsWith("[NPC] Hunter Harry: Where's the ") && line.endsWith("?")
			|| line.startsWith("[NPC] Huntress Melissa: Err, you don't have a ")
				&& line.endsWith(".");
	}

	private static Critter traderShard(String line) {
		boolean offer = line.startsWith("[NPC] Hunter Billy: I found this really cool ")
			|| line.startsWith("[NPC] Hunter Dennis: I've got a ")
			|| line.startsWith("[NPC] Hunter Harry: Say, do you have a use for a ")
			|| line.startsWith("[NPC] Huntress Melissa: Do you want this ");
		if (!offer || !line.contains(" Shard")) return null;
		return Critters.findIn(line);
	}

	private static String traderCost(String line) {
		for (Pattern pattern : TRADE_COST_PATTERNS) {
			Matcher matcher = pattern.matcher(line);
			if (matcher.matches()) return matcher.group(1);
		}
		return null;
	}

	private static int componentColour(Component component, String text, int fallback) {
		Integer found = componentColour(component, text.toLowerCase(java.util.Locale.ROOT));
		return found == null ? fallback : found;
	}

	private static Integer componentColour(Component component, String lowerText) {
		if (!component.getString().toLowerCase(java.util.Locale.ROOT).contains(lowerText)) return null;
		for (Component sibling : component.getSiblings()) {
			Integer nested = componentColour(sibling, lowerText);
			if (nested != null) return nested;
		}
		return component.getStyle().getColor() == null ? null
			: component.getStyle().getColor().getValue();
	}

	private static int fallbackCostColour(String payment) {
		String lower = payment.toLowerCase(java.util.Locale.ROOT);
		if (lower.contains("lime")) return 0x55FF55;
		if (lower.contains("purple")) return 0xAA00AA;
		if (lower.contains("orange") || lower.contains("shining coin")) return 0xFFAA00;
		return 0xFFFFFF;
	}

	private record TradeContext(String payment, int paymentColour, String shard, int shardColour) {
		private boolean complete() {
			return payment != null && shard != null;
		}
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
		return line.equals("This candle is already lit...")
			|| line.startsWith("You used the Soothing Incense to light the candle")
			|| line.startsWith("Your ritual summoned a Doomspiral")
			|| line.startsWith("Something stirs in the Haunted Biome")
			|| line.startsWith("The darkness in the Haunted Biome fades away")
			|| line.startsWith("The Doomspiral retreats back underground");
	}

	private static boolean dropItemsMessage(String line) {
		return line.equals("You cannot drop items yet!")
			|| line.equals("You must double tap the drop button to drop this item!")
			|| line.equals("You can disable this in the Settings in your SkyBlock Menu!");
	}
}
