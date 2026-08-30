package dev.serko.safariutils.parse;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses formatting-stripped Hypixel chat. Capture and loot-share messages resolve
 * species by roster lookup so wording variations remain compatible. Floor-drop shard
 * messages use {@link #floorDropShard(String)} because they are not catch events.
 */
public final class ChatParser {

	private static final Pattern SHARD_AMOUNT = Pattern.compile("(\\d[\\d,]*)x\\s+\\S");
	private static final Pattern FLOOR_DROP_SHARD =
		Pattern.compile("^FLOOR DROP! You found (?:(\\d[\\d,]*)x\\s+)?(.+?) Shard on the ground!$");
	private static final Pattern LOOT_SHARE_CATCHER =
		Pattern.compile("from\\s+(\\w{1,16})\\s+(?:catching|finding)\\b");
	private static final Pattern ATTEMPT =
		Pattern.compile("^You threw a (?:Masterful )?Critter Capsule at the (.+)!$");
	private static final Pattern FAILED =
		Pattern.compile("^The (.+?) (?:escaped your Critter Capsule|dodged your critter capsule)\\b");
	private static final Pattern ENTERED =
		Pattern.compile("^(?:\\[[^]]+]\\s*)?(\\w{1,16}) entered Critter Safari!$");
	private static final Pattern SPARKLING_CAUGHT = Pattern.compile(
		"^SPARKLING! (\\w{1,16}) caught a SPARKLING (.+)!$");
	private static final Pattern FORMATTING = Pattern.compile("§.");
	private static final Pattern DUPLICATE_SUFFIX = Pattern.compile(
		"\\s*(?:\\(\\s*[x×]?\\s*\\d+\\s*\\)|\\[\\s*[x×]?\\s*\\d+\\s*])$");

	/**
	 * Matches player-written chat while excluding NPC and mob lines. This stops quoted
	 * server messages from triggering trackers on another player's client.
	 */
	private static final Pattern PLAYER_SAID = Pattern.compile(
		"^(?:Party|Guild|Officer|Co-op|Team|Friend) >.*"
			+ "|^(?:From|To) .*"
			+ "|^(?:\\[(?!NPC]|MOB])[^]]+]\\s*)?\\w{1,16}: .*");

	private ChatParser() {
	}

	/**
	 * Whether a cleaned line was typed by a player.
	 *
	 * <p>Watchers that match on a phrase anywhere in the line must not run on these:
	 * anything a player can type, a player can quote.
	 */
	public static boolean playerSaid(String line) {
		return PLAYER_SAID.matcher(line).matches();
	}

	/**
	 * Strips §-colour codes and the trailing duplicate counter that chat-compacting
	 * mods (chatpatches, enhanced_chat) append — {@code " (3)"}, {@code " (×3)"},
	 * {@code " [x3]"}. Those suffixes are display artefacts, not part of the message.
	 */
	public static String clean(String raw) {
		String text = FORMATTING.matcher(raw).replaceAll("").trim();
		text = DUPLICATE_SUFFIX.matcher(text).replaceAll("");
		return text.trim();
	}

	/**
	 * Parses one already-{@linkplain #clean cleaned} chat line.
	 *
	 * @param selfName the local player's name, used to tell your own "entered Critter
	 *                 Safari!" line from a partymate's; may be {@code null}
	 * @return the event, or {@code null} if the line is not a Critter Safari event
	 */
	public static CritterEvent parse(String line, String selfName) {
		if (line.startsWith("CAPTURE!")) {
			Critter critter = Critters.findIn(line);
			if (critter == null) return null;
			return new CritterEvent(CritterEvent.Type.OWN_CATCH, critter, null,
				shardAmount(line), line.contains("SPARKLING"));
		}

		if (line.startsWith("LOOT SHARE!")) {
			Critter critter = Critters.findIn(line);
			if (critter == null) return null;
			Matcher catcher = LOOT_SHARE_CATCHER.matcher(line);
			if (!catcher.find()) return null;
			return new CritterEvent(CritterEvent.Type.SHARED_CATCH, critter, catcher.group(1),
				shardAmount(line), line.contains("SPARKLING"));
		}

		Matcher attempt = ATTEMPT.matcher(line);
		if (attempt.matches()) {
			Critter critter = Critters.byName(attempt.group(1));
			if (critter == null) return null;
			return new CritterEvent(CritterEvent.Type.ATTEMPT, critter, null, 0, false);
		}

		Matcher failed = FAILED.matcher(line);
		if (failed.find()) {
			Critter critter = Critters.byName(failed.group(1));
			if (critter == null) return null;
			return new CritterEvent(CritterEvent.Type.FAILED, critter, null, 0, false);
		}

		Matcher entered = ENTERED.matcher(line);
		if (entered.matches() && selfName != null && selfName.equals(entered.group(1))) {
			return new CritterEvent(CritterEvent.Type.ENTERED_SAFARI, null, selfName, 0, false);
		}

		return null;
	}

	/** The authoritative Sparkling occurrence line, separate from its reward message. */
	public static SparklingCatch sparklingCatch(String line) {
		Matcher matcher = SPARKLING_CAUGHT.matcher(line);
		if (!matcher.matches()) return null;
		Critter critter = Critters.byName(matcher.group(2));
		return critter == null ? null : new SparklingCatch(critter, matcher.group(1));
	}

	public static boolean bonusRainbowFeather(String line) {
		return line.equals("MACAW! You found a bonus Rainbow Feather!");
	}

	public record SparklingCatch(Critter critter, String catcher) {
	}

	/**
	 * Reads a species and shard count off a {@code FLOOR DROP!} pickup line, or
	 * {@code null} if this one is not a shard at all — a Gimmiegold's floor drop is a
	 * Shining Coin, not a shard, and does not match.
	 */
	public static FloorDropShard floorDropShard(String line) {
		Matcher matcher = FLOOR_DROP_SHARD.matcher(line);
		if (!matcher.find()) return null;
		Critter critter = Critters.byName(matcher.group(2));
		if (critter == null) return null;
		int amount = 1;
		if (matcher.group(1) != null) {
			try {
				amount = Integer.parseInt(matcher.group(1).replace(",", ""));
			} catch (NumberFormatException ignored) {
				amount = 1;
			}
		}
		return new FloorDropShard(critter, amount);
	}

	/** A species and how many of its shards were just found on the ground. */
	public record FloorDropShard(Critter critter, int amount) {
	}

	/**
	 * Reads the shard count out of a catch message. {@code "gained 2x Foxtrot Shard"}
	 * yields 2; {@code "gained a Foxtrot Shard"} has no numeral and yields 1.
	 */
	private static int shardAmount(String line) {
		Matcher matcher = SHARD_AMOUNT.matcher(line);
		if (!matcher.find()) return 1;
		try {
			return Integer.parseInt(matcher.group(1).replace(",", ""));
		} catch (NumberFormatException e) {
			return 1;
		}
	}
}
