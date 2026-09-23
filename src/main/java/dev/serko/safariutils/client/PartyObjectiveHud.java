package dev.serko.safariutils.client;

import dev.serko.safariutils.api.PartyItemSyncProviders;
import dev.serko.safariutils.api.SharedSparklingProviders;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.session.SessionManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Biome-aware party objective HUD; legacy config keys preserve existing settings. */
public final class PartyObjectiveHud implements HudElement {
	private static final String[] BIRDS = {"Bluebird", "Parakeet", "Macaw"};
	private static final int[] BIRD_COLOURS = {0xFF1647D8, 0xFFA7E522, 0xFFF04A24};
	private static final int[] GEM_COLOURS = {0xFFA83DE9, 0xFF63DC2F, 0xFFF06B14};
	private static final int INCENSE_COLOUR = 0xFFD28AFF;
	static final int FOREST_LINE_COLOUR = 0xFFAAFFAA;
	static final int CAVERN_LINE_COLOUR = 0xFFFFCC77;
	static final int ICY_LINE_COLOUR = 0xFF99EEFF;
	static final int HAUNTED_LINE_COLOUR = 0xFFAA55FF;
	static final int INTERMEDIATE_COLOUR = 0xFFFFFF55;
	private static final Pattern DISPLAYED_PLAYER = Pattern.compile(
		"^\\[\\d+]\\s+([A-Za-z0-9_]{1,16})(?:\\s.*)?$");
	private static Map<String, Integer> playerColours = Map.of();
	private static boolean playerColoursDirty = true;
	private final TickCache<HudPanel> panelCache = new TickCache<>();

	public static int borderColour() { return HudBorderStyle.partyObjectives(); }

	/**
	 * The shared location cache is normally authoritative, but can briefly lose its
	 * biome while Hypixel rebuilds area text. During an active run the position map is
	 * still valid, so do not collapse an in-biome HUD to its title-only form.
	 */
	public static SafariBiome currentBiome() {
		SafariBiome biome = SafariLocation.biome();
		return biome != null || SessionManager.current() == null
			? biome : SafariLocation.biomeFromPosition();
	}

	/** Whether this biome is selected for both its title status and live HUD details. */
	public static boolean biomeEnabled(SafariBiome biome) {
		if (biome == null) return false;
		int bit = switch (biome) {
			case CAVERN -> 1;
			case ICY -> 2;
			case HAUNTED -> 4;
			case FOREST -> 8;
		};
		return (ConfigManager.get().display.partyObjectiveTitleBiomes & bit) != 0;
	}

	public static int titleColour() {
		int border = borderColour();
		SafariBiome biome = currentBiome();
		return border != 0 ? border : biome == null ? 0xFFFFAA00 : 0xFF000000 | biome.colour();
	}

	public static HudPanel panel() {
		HudPanel synchronizedPanel = PartyItemSyncProviders.objectivePanel();
		if (synchronizedPanel != null) return synchronizedPanel;
		Minecraft client = Minecraft.getInstance();
		SafariBiome biome = currentBiome();
		if (SessionManager.current() == null || client.player == null) return null;
		if (biome == null || !biomeEnabled(biome)) return basePanel();
		return switch (biome) {
			case FOREST -> forestPanel(client);
			case CAVERN -> cavernPanel(client);
			case HAUNTED -> hauntedPanel(client);
			case ICY -> icyPanel();
		};
	}

	public static boolean localObjectiveComplete() {
		SafariBiome biome = currentBiome();
		if (biome == null) return false;
		return switch (biome) {
			case FOREST -> BirdfeederWatch.allForestFeedUsed();
			case CAVERN -> SafariObjectives.gemzieCaught();
			case HAUNTED -> SafariObjectives.doomspiralComplete();
			case ICY -> SafariObjectives.wumpaComplete();
		};
	}

	private static HudPanel forestPanel(Minecraft client) {
		SafariConfig.DisplayConfig config = ConfigManager.get().display;
		HudPanel panel = basePanel();
		int drops = Math.min(9, BirdfeederWatch.floorFeedFound());
		int found = Math.max(0, BirdfeederWatch.feedFound());
		int used = Math.min(Math.max(0, BirdfeederWatch.feedUsed()), found);
		boolean done = BirdfeederWatch.allForestFeedUsed();
		if (done) {
			if (config.partyObjectiveShowProgress) panel.pair("All Feed Done", found + "/" + found,
				FOREST_LINE_COLOUR, 0xFF55FF55);
			if (config.partyObjectiveShowBirdCounts) panel.iconPair("Birds", FOREST_LINE_COLOUR, visibleBirds());
			return panel;
		}
		if (config.partyObjectiveShowPlayers) addUnsynchronizedForestPlayers(panel, client);
		panel.pair("Forest Drops", drops >= 9 ? "✔" : drops + "/9",
			FOREST_LINE_COLOUR, drops >= 9 ? 0xFF55FF55 : 0xFFFFFFFF);
		if (config.partyObjectiveShowProgress) panel.pair("Feed Done", used + "/" + found,
			FOREST_LINE_COLOUR, done ? 0xFF55FF55 : 0xFFFFFFFF);
		if (config.partyObjectiveShowPlacement) addFeederRow(panel,
			BirdfeederWatch.feederType(), BirdfeederWatch.feederCount());
		if (config.partyObjectiveShowBirdCounts) panel.iconPair("Birds", FOREST_LINE_COLOUR, visibleBirds());
		return panel;
	}

	private static HudPanel cavernPanel(Minecraft client) {
		SafariConfig.DisplayConfig config = ConfigManager.get().display;
		HudPanel panel = basePanel();
		if (SafariObjectives.gemzieCaught()) {
			if (config.partyObjectiveShowPlacement) addPlacedGemsRow(panel, 7, true, true);
			return panel;
		}
		if (config.partyObjectiveShowPlayers) addUnsynchronizedObjectivePlayers(panel, client, SafariBiome.CAVERN);
		int placed = SafariObjectives.placedGemMask();
		if (config.partyObjectiveShowProgress) {
			int ready = Integer.bitCount(placed
				| (SafariObjectives.limeGemsHeld() > 0 ? 1 : 0)
				| (SafariObjectives.orangeGemsHeld() > 0 ? 2 : 0)
				| (SafariObjectives.purpleGemsHeld() > 0 ? 4 : 0));
			panel.pair("Gems", ready >= 3 ? "✔" : ready + "/3",
				CAVERN_LINE_COLOUR, ready >= 3 ? 0xFF55FF55 : 0xFFFFFFFF);
		}
		if (config.partyObjectiveShowPlacement) addPlacedGemsRow(panel, placed,
			SafariObjectives.gemzieDoorDisplayReady(), false);
		return panel;
	}

	private static HudPanel icyPanel() {
		SafariConfig.DisplayConfig config = ConfigManager.get().display;
		HudPanel panel = basePanel();
		if (SafariObjectives.wumpaComplete()) {
			if (config.partyObjectiveShowPlacement) panel.pair("Wumpa",
				SafariObjectives.wumpaCaught() ? "Caught" : "Retreated",
				ICY_LINE_COLOUR, 0xFF55FF55);
			return panel;
		}
		int catches = icyUniqueCatches();
		if (config.partyObjectiveShowProgress) {
			panel.pair("Unique Catches", catches + "/8", ICY_LINE_COLOUR,
				catches >= 8 ? 0xFF55FF55 : 0xFFFFFFFF);
		}
		if (config.partyObjectiveShowPlacement) {
			String value = SafariObjectives.wumpaCaught() ? "Caught"
				: SafariObjectives.wumpaSpawned() ? "Started"
				: catches >= 8 ? "Waiting" : "Locked";
			panel.pair("Wumpa", value, ICY_LINE_COLOUR,
				SafariObjectives.wumpaSpawned() ? INTERMEDIATE_COLOUR : 0xFFFFFFFF);
		}
		return panel;
	}

	/** The eight ordinary Icy uniques that wake Wumpa; Wumpa itself is excluded. */
	public static int icyUniqueCatches() {
		var session = SessionManager.current();
		if (session == null) return 0;
		int catches = session.partyUnique(SafariBiome.ICY);
		var wumpa = Critters.byName("Wumpa");
		if (wumpa != null && session.partyCatches(wumpa) > 0) catches--;
		return Math.clamp(catches, 0, 8);
	}

	private static HudPanel hauntedPanel(Minecraft client) {
		SafariConfig.DisplayConfig config = ConfigManager.get().display;
		HudPanel panel = basePanel();
		if (SafariObjectives.doomspiralComplete()) {
			if (config.partyObjectiveShowPlacement) panel.pair("Doomspiral",
				SafariObjectives.doomspiralCaught() ? "Caught" : "Retreated",
				HAUNTED_LINE_COLOUR, 0xFF55FF55);
			return panel;
		}
		if (config.partyObjectiveShowPlayers) addUnsynchronizedObjectivePlayers(panel, client, SafariBiome.HAUNTED);
		int candles = Math.min(4, SafariObjectives.incenseUsed());
		if (config.partyObjectiveShowProgress) {
			int ready = Math.min(4, candles + SafariObjectives.incenseHeld());
			panel.pair("Incense", ready >= 4 ? "✔" : ready + "/4",
				HAUNTED_LINE_COLOUR, ready >= 4 ? 0xFF55FF55 : 0xFFFFFFFF);
		}
		if (config.partyObjectiveShowPlacement) {
			String value = SafariObjectives.doomspiralCaught() ? "Caught"
				: SafariObjectives.doomspiralRetreated() ? "Retreated"
				: SafariObjectives.doomspiralSpawned() ? "Started"
				: candles >= 4 ? "Complete" : candles + "/4";
			panel.pair("Doomspiral", value, HAUNTED_LINE_COLOUR,
				SafariObjectives.doomspiralSpawned() ? INTERMEDIATE_COLOUR : 0xFFFFFFFF);
		}
		return panel;
	}

	private static HudPanel basePanel() {
		return objectivePanel(SafariObjectives.gemzieCaught(),
			SafariObjectives.wumpaComplete(), SafariObjectives.doomspiralComplete(),
			BirdfeederWatch.allForestFeedUsed(), false);
	}

	public static HudPanel objectivePanel(boolean cavernComplete,
			boolean icyComplete, boolean hauntedComplete, boolean forestComplete,
			boolean forestKnown) {
		int shown = ConfigManager.get().display.partyObjectiveTitleBiomes;
		int known = forestKnown ? 15 : 7;
		return new HudPanel().objectiveTitle("Party Objectives", titleColour(),
			shown, known, cavernComplete, icyComplete, hauntedComplete, forestComplete)
			.minimumWidth(155);
	}

	private static void addUnsynchronizedForestPlayers(HudPanel panel, Minecraft client) {
		int seeds = SafariObjectives.bagOfSeedsHeld();
		int worms = SafariObjectives.wrigglewormsHeld();
		int berries = SafariObjectives.yogiBerriesHeld();
		boolean showed = false;
		if (seeds + worms + berries > 0) {
			addPlayerRow(panel, client.player.getGameProfile().name(),
				feedValues(String.valueOf(berries), String.valueOf(worms), String.valueOf(seeds)));
			showed = true;
		}
		String localName = client.player.getGameProfile().name();
		for (String name : SafariPartyWatch.presentPlayerNames()) {
			if (name.equalsIgnoreCase(localName)) continue;
			addPlayerRow(panel, name, feedValues("?", "?", "?"));
			showed = true;
		}
		if (showed) panel.blank();
	}

	private static void addUnsynchronizedObjectivePlayers(HudPanel panel, Minecraft client, SafariBiome biome) {
		String localName = client.player.getGameProfile().name();
		boolean showed = false;
		int localItems = biome == SafariBiome.CAVERN
			? SafariObjectives.purpleGemsHeld() + SafariObjectives.limeGemsHeld()
				+ SafariObjectives.orangeGemsHeld()
			: SafariObjectives.incenseHeld();
		if (localItems > 0) {
			addPlayerRow(panel, localName, biome == SafariBiome.CAVERN
				? gemValues(String.valueOf(SafariObjectives.purpleGemsHeld()),
					String.valueOf(SafariObjectives.limeGemsHeld()),
					String.valueOf(SafariObjectives.orangeGemsHeld()))
				: incenseValue(String.valueOf(SafariObjectives.incenseHeld())));
			showed = true;
		}
		for (String name : SafariPartyWatch.presentPlayerNames()) {
			if (name.equalsIgnoreCase(localName)) continue;
			addPlayerRow(panel, name, biome == SafariBiome.CAVERN
				? gemValues("?", "?", "?") : incenseValue("?"));
			showed = true;
		}
		if (showed) panel.blank();
	}

	public static HudPanel.IconValue[] feedValues(String berries, String worms, String seeds) {
		return new HudPanel.IconValue[] {
			new HudPanel.IconValue(HudPanel.HudIcon.BERRY, berries, 0xFF2D69EA),
			new HudPanel.IconValue(HudPanel.HudIcon.WORM, worms, 0xFFFF64AD),
			new HudPanel.IconValue(HudPanel.HudIcon.SEED_BAG, seeds, 0xFFFFD36A)
		};
	}

	public static HudPanel.IconValue[] gemValues(String purple, String lime, String orange) {
		return new HudPanel.IconValue[] {
			new HudPanel.IconValue(HudPanel.HudIcon.GEM, purple, GEM_COLOURS[0]),
			new HudPanel.IconValue(HudPanel.HudIcon.GEM, lime, GEM_COLOURS[1]),
			new HudPanel.IconValue(HudPanel.HudIcon.GEM, orange, GEM_COLOURS[2])
		};
	}

	public static HudPanel.IconValue[] incenseValue(String incense) {
		return new HudPanel.IconValue[] {
			new HudPanel.IconValue(HudPanel.HudIcon.INCENSE, incense, INCENSE_COLOUR)
		};
	}

	public static void addPlacedGemsRow(HudPanel panel, int placedMask, boolean open, boolean caught) {
		if (caught) {
			panel.pair("Gemzie", "Caught", CAVERN_LINE_COLOUR, 0xFF55FF55);
			return;
		}
		if (open) {
			panel.pair("Gemzie", "Door Open", CAVERN_LINE_COLOUR, INTERMEDIATE_COLOUR);
			return;
		}
		panel.iconPair("Gemzie", CAVERN_LINE_COLOUR,
			gemValues((placedMask & 4) != 0 ? "✔" : "—",
				(placedMask & 1) != 0 ? "✔" : "—", (placedMask & 2) != 0 ? "✔" : "—"));
	}

	/** Uses the server's tab-list text color; rank names need not be present. */
	public static int playerNameColour(String name) {
		if (name == null) return 0xFFAAAAAA;
		if (playerColoursDirty) snapshotPlayerColours();
		return playerColours.getOrDefault(name.toLowerCase(Locale.ROOT), 0xFFAAAAAA);
	}

	/** Invalidated only by world or player-list packets, including late arrivals. */
	public static void invalidatePlayerColours() {
		playerColoursDirty = true;
	}

	private static void snapshotPlayerColours() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.player.connection == null) {
			playerColours = Map.of();
			playerColoursDirty = false;
			return;
		}
		Map<String, Integer> colours = new HashMap<>();
		for (PlayerInfo info : client.player.connection.getOnlinePlayers()) {
			Component shown = info.getTabListDisplayName();
			String name = displayedPlayerName(shown);
			if (name == null) continue;
			String key = name.toLowerCase(Locale.ROOT);
			Integer colour = componentNameColour(shown, key);
			int resolved = colour == null ? 0xFFAAAAAA : 0xFF000000 | colour;
			colours.put(key, resolved);
		}
		playerColours = Map.copyOf(colours);
		playerColoursDirty = false;
	}

	/** The UUID-backed owner identity remains rainbow; all other names retain rank color. */
	public static void addPlayerRow(HudPanel panel, String name, HudPanel.IconValue... values) {
		if (SharedSparklingProviders.specialName(name)) panel.rainbowIconPair(name, values);
		else panel.iconPair(name, playerNameColour(name), values);
	}

	private static String displayedPlayerName(Component shown) {
		if (shown == null) return null;
		var match = DISPLAYED_PLAYER.matcher(shown.getString().trim());
		return match.matches() ? match.group(1) : null;
	}

	private static Integer componentNameColour(Component component, String lowerName) {
		if (!component.getString().toLowerCase(Locale.ROOT).contains(lowerName)) return null;
		for (Component sibling : component.getSiblings()) {
			Integer nested = componentNameColour(sibling, lowerName);
			if (nested != null) return nested;
		}
		return component.getStyle().getColor() == null ? null
			: component.getStyle().getColor().getValue();
	}

	public static void addFeederRow(HudPanel panel, int type, int count) {
		if (type < 0 || count <= 0) {
			panel.pair("Birdfeeder", "Empty", FOREST_LINE_COLOUR, 0xFF888888);
			return;
		}
		HudPanel.HudIcon icon = switch (type) {
			case 0 -> HudPanel.HudIcon.SEED_BAG;
			case 1 -> HudPanel.HudIcon.WORM;
			case 2 -> HudPanel.HudIcon.BERRY;
			default -> HudPanel.HudIcon.SEED_BAG;
		};
		int colour = switch (type) {
			case 0 -> 0xFFFFD36A;
			case 1 -> 0xFFFF64AD;
			case 2 -> 0xFF2D69EA;
			default -> 0xFFFFFFFF;
		};
		panel.iconPair("Birdfeeder", FOREST_LINE_COLOUR,
			new HudPanel.IconValue(icon, String.valueOf(count), colour));
	}

	private static HudPanel.IconValue[] visibleBirds() {
		List<HudPanel.IconValue> values = new ArrayList<>();
		for (int index = 0; index < BIRDS.length; index++) {
			var critter = Critters.byName(BIRDS[index]);
			int count = critter == null ? 0 : DetectedCritters.currentConcurrent(critter);
			if (count > 0) values.add(new HudPanel.IconValue(HudPanel.HudIcon.BIRD,
				String.valueOf(count), BIRD_COLOURS[index]));
		}
		if (values.isEmpty()) for (int colour : BIRD_COLOURS) {
			values.add(new HudPanel.IconValue(HudPanel.HudIcon.BIRD, "?", colour));
		}
		return values.toArray(HudPanel.IconValue[]::new);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!ConfigManager.get().display.showPartyObjectiveHud) return;
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || ClientCompat.hudHidden()) return;
		HudPanel panel = panelCache.get(PartyObjectiveHud::panel);
		if (panel == null || panel.isEmpty()) return;
		HudBox box = HudBox.PARTY_OBJECTIVE;
		float scale = box.scale() * ResponsiveUI.scale(graphics.guiWidth(), graphics.guiHeight());
		int x = box.pixelX(graphics.guiWidth(), panel, client.font, scale);
		int y = box.pixelY(graphics.guiHeight(), panel, scale);
		if (SparklingWatch.hudThemeActive()) panel.renderRainbow(graphics, client.font, x, y, scale);
		else panel.render(graphics, client.font, x, y, scale, borderColour());
	}
}
