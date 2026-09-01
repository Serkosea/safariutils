package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.SafariBiome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves and caches the player's Safari location once per tick. Server area text is
 * authoritative; critter labels and the entry message only bridge transfer delays.
 * World changes clear the chat-derived fallback.
 */
public final class SafariLocation {

	/** Which of the three places the player is. */
	public enum Where {
		/** Not at the Safari at all. */
		ELSEWHERE,
		/** The Critter Safari Entrance, over in Torrhus Canyon — not the island itself. */
		ENTRANCE,
		/** Inside the Safari, where the critters are. */
		INSIDE
	}

	/** Where the current answer came from; shown by {@code /su debug}. */
	public enum Source {
		/** The server's own area line — the only self-clearing source. */
		AREA_LINE,
		/** A critter name tag in the world, used before the area line arrives. */
		CRITTER_LABELS,
		/** The "entered Critter Safari!" message, used before anything else lands. */
		CHAT,
		/** Nothing says the player is at the Safari. */
		NONE
	}

	/**
	 * Scoreboard area pattern: a colour code, the area symbol, then the name.
	 * The symbol is matched as "any character" on purpose — Hypixel renders it with a
	 * private-use glyph in places, which is exactly what stripping formatting removes.
	 */
	private static final Pattern AREA_LINE = Pattern.compile("\\s*§.(?<symbol>.) §.(?<area>.*)");
	private static final Pattern LOBBY_LINE = Pattern.compile("^\\d{2}/\\d{2}/\\d{2}\\s+([A-Za-z0-9]+)$");
	private static final Pattern ESSENCE_LINE = Pattern.compile("^Safari Essence:\\s*([\\d,]+)$");

	/** Symbols on scoreboard lines that shape like an area line but are not one. */
	private static final String NON_AREA_SYMBOLS = "♲☀";

	private static final String TAB_AREA_PREFIX = "Area: ";

	/**
	 * Names that mean the entrance rather than the island.
	 *
	 * <p>The entrance area also carries "Safari" in its name, which is why matching on
	 * that alone put the overlays up in Torrhus Canyon.
	 */
	private static final String[] ENTRANCE_WORDS = {"Entrance", "Entry"};

	private static String area;
	private static Where where = Where.ELSEWHERE;
	private static SafariBiome biome;
	private static Source source = Source.NONE;
	/** Server text is rebuilt once per tick and shared by every interested feature. */
	private static List<String> sidebarLines = List.of();
	private static List<String> tabListEntries = List.of();
	private static String tabListArea;
	private static String lobbyId;
	private static Integer safariEssence;

	/** Set by the entry message; only consulted while the server has stated no area. */
	private static boolean chatEntered;
	private static int labelFallbackTicks;
	private static boolean labelsNearby;

	private SafariLocation() {
	}

	// --- the answer ----------------------------------------------------------

	/** Whether the player is at the Critter Safari, its entrance included. */
	public static boolean inSafari() {
		return where != Where.ELSEWHERE;
	}

	/** Whether the player is inside the Safari proper, the entrance not counting. */
	public static boolean inside() {
		return where == Where.INSIDE;
	}

	/** The full answer, for anything that treats the entrance differently. */
	public static Where where() {
		return where;
	}

	/**
	 * The island itself, from the tablist's own {@code Area:} line — Torrhus Canyon,
	 * Safari, or whatever else Hypixel reports there. Deliberately not
	 * {@link #statedArea}: that prioritises the scoreboard's sub-area line first, so
	 * on Torrhus Canyon it reads as "Critter Safari Entrance" or wherever else
	 * specifically, never the island name itself. This is the one place that
	 * actually wants the island, regardless of where on it the player currently is.
	 */
	public static String tabListArea() {
		return tabListArea;
	}

	/** The biome being stood in, or {@code null} when it cannot be determined. */
	public static SafariBiome biome() {
		return biome;
	}

	/** The area the server says the player is in, or {@code null} if it has not said. */
	public static String area() {
		return area;
	}

	public static Source source() {
		return source;
	}

	// --- updating ------------------------------------------------------------

	/** Recomputes the location. Called once per client tick, before anything reads it. */
	public static void tick() {
		sidebarLines = readSidebarLines();
		tabListEntries = readTabListEntries();
		tabListArea = findTabListArea();
		lobbyId = findLobbyId();
		safariEssence = findSafariEssence();
		String stated = statedArea();
		if (stated != null) {
			area = stated;
			where = classify(stated);
			source = Source.AREA_LINE;
			// The server has spoken, so the chat flag has nothing left to add. Clearing
			// it here is what stops a run "following" the player out to the Hub.
			chatEntered = where != Where.ELSEWHERE;
		} else if (cachedCritterLabelsNearby()) {
			// Critters only exist on the island, so their labels mean inside, not the
			// entrance.
			area = null;
			where = Where.INSIDE;
			source = Source.CRITTER_LABELS;
		} else if (chatEntered) {
			area = null;
			where = Where.INSIDE;
			source = Source.CHAT;
		} else {
			area = null;
			where = Where.ELSEWHERE;
			source = Source.NONE;
		}

		biome = where == Where.INSIDE ? resolveBiome() : null;
	}

	/**
	 * Notes the entry message. Applied immediately as well as recorded, so a catch
	 * arriving in the same tick as the banner is not treated as happening elsewhere.
	 */
	public static void onChatMessage(String line) {
		if (line.endsWith("entered Critter Safari!")) markEntered();
	}

	/** Marks the player as being at the Safari on evidence other than the area line. */
	public static void markEntered() {
		chatEntered = true;
		if (where != Where.INSIDE) where = Where.INSIDE;
		if (source == Source.NONE) source = Source.CHAT;
	}

	/**
	 * Forgets everything on a world change.
	 *
	 * <p>Hypixel never announces leaving the Safari, but every island change reconnects
	 * the play connection, so this is the one moment the chat flag can be known stale.
	 */
	public static void onWorldChange() {
		chatEntered = false;
		where = Where.ELSEWHERE;
		area = null;
		biome = null;
		source = Source.NONE;
		labelFallbackTicks = 0;
		labelsNearby = false;
		tabListArea = null;
		lobbyId = null;
		safariEssence = null;
	}

	// --- reading the server's area line --------------------------------------

	/**
	 * Combines the area label with the mapped island position. The position check keeps
	 * the Torrhus entrance separate while still accepting the Safari's unnamed hub.
	 */
	private static Where classify(String stated) {
		if (!stated.contains("Safari")) return Where.ELSEWHERE;
		for (String word : ENTRANCE_WORDS) {
			if (stated.contains(word)) return Where.ENTRANCE;
		}
		return onIsland() ? Where.INSIDE : Where.ENTRANCE;
	}

	/** Whether the player's current position is anywhere on the mapped island. */
	private static boolean onIsland() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return false;
		Vec3 pos = client.player.position();
		return SafariAreaMap.onIsland(pos.x, pos.y, pos.z);
	}

	/** The area SkyBlock states, from the scoreboard or the tab list, or {@code null}. */
	private static String statedArea() {
		for (String raw : sidebarLines) {
			Matcher matcher = AREA_LINE.matcher(raw);
			if (!matcher.matches()) continue;
			String symbol = matcher.group("symbol");
			if (!symbol.isEmpty() && NON_AREA_SYMBOLS.indexOf(symbol.charAt(0)) >= 0) continue;
			String name = strip(matcher.group("area"));
			if (!name.isEmpty()) return name;
		}
		return tabListArea;
	}

	/** Sidebar lines with their formatting intact, top to bottom. */
	public static List<String> sidebarLines() {
		return sidebarLines;
	}

	/** Stable identifier printed beside the SkyBlock date for the current server. */
	public static String lobbyId() {
		return lobbyId;
	}

	private static String findLobbyId() {
		for (String raw : sidebarLines) {
			Matcher matcher = LOBBY_LINE.matcher(strip(raw));
			if (matcher.matches()) return matcher.group(1);
		}
		return null;
	}

	/** Current Safari Essence balance, or {@code null} while the line is unavailable. */
	public static Integer safariEssence() {
		return safariEssence;
	}

	private static Integer findSafariEssence() {
		for (String raw : sidebarLines) {
			Matcher matcher = ESSENCE_LINE.matcher(strip(raw));
			if (!matcher.matches()) continue;
			try {
				return Integer.parseInt(matcher.group(1).replace(",", ""));
			} catch (NumberFormatException ignored) {
				return null;
			}
		}
		return null;
	}

	private static String findTabListArea() {
		for (String entry : tabListEntries) {
			if (!entry.startsWith(TAB_AREA_PREFIX)) continue;
			String name = entry.substring(TAB_AREA_PREFIX.length()).trim();
			if (!name.isEmpty()) return name;
		}
		return null;
	}

	private static List<String> readSidebarLines() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return List.of();

		Scoreboard scoreboard = client.level.getScoreboard();
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (objective == null) return List.of();

		List<String> lines = new ArrayList<>();
		for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
			if (entry.isHidden()) continue;
			PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
			lines.add(team == null
				? entry.ownerName().getString()
				: PlayerTeam.formatNameForTeam(team, entry.ownerName()).getString());
		}
		return lines;
	}

	/** Tab-list entries, stripped. SkyBlock puts metadata rows among the player names. */
	public static List<String> tabListEntries() {
		return tabListEntries;
	}

	private static List<String> readTabListEntries() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.player.connection == null) return List.of();

		List<String> entries = new ArrayList<>();
		for (PlayerInfo info : client.player.connection.getOnlinePlayers()) {
			if (info.getTabListDisplayName() == null) continue;
			String text = strip(info.getTabListDisplayName().getString());
			if (!text.isEmpty()) entries.add(text);
		}
		return entries;
	}

	// --- which biome ---------------------------------------------------------

	/**
	 * The biome within the Safari.
	 *
	 * <p>The area line names the Safari but not the biome inside it, so this is nearly
	 * always answered from position against the mapped areas.
	 */
	private static SafariBiome resolveBiome() {
		if (area != null) {
			SafariBiome named = SafariBiome.fromAreaName(area);
			if (named != null) return named;
		}
		return biomeFromPosition();
	}

	/** Biome from the player's position, or {@code null} outside the mapped areas. */
	public static SafariBiome biomeFromPosition() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return null;
		Vec3 pos = client.player.position();
		return SafariAreaMap.biomeAt(pos.x, pos.y, pos.z);
	}

	/** Distance to the nearest mapped node — used by {@code /su debug}. */
	public static double distanceToNearestNode() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return Double.NaN;
		Vec3 pos = client.player.position();
		return SafariAreaMap.distanceToNearestNode(pos.x, pos.y, pos.z);
	}

	/**
	 * Whether any critter name tag is loaded.
	 *
	 * <p>Hypixel labels every critter with an entity whose custom name is exactly the
	 * species name, and those exist nowhere else. Any entity type counts: most are
	 * armor stands but a Hideyho arrives as a player.
	 */
	public static boolean critterLabelsNearby() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return false;
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!entity.hasCustomName()) continue;
			if (Critters.byName(strip(entity.getCustomName().getString())) != null) return true;
		}
		return false;
	}

	/** The transfer fallback tolerates a short cache; entity detection already scans at this cadence. */
	private static boolean cachedCritterLabelsNearby() {
		if (labelFallbackTicks++ % 5 == 0) labelsNearby = critterLabelsNearby();
		return labelsNearby;
	}

	/** Strips §-codes and the invisible padding Hypixel puts in sidebar lines. */
	public static String strip(String text) {
		StringBuilder clean = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char character = text.charAt(i);
			if (character == '§' && i + 1 < text.length()) {
				i++;
				continue;
			}
			int type = Character.getType(character);
			if (type != Character.FORMAT && type != Character.PRIVATE_USE) clean.append(character);
		}
		return clean.toString().trim();
	}
}
