package dev.serko.safariutils.client;

import dev.serko.safariutils.api.PartyItemSyncProviders;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.session.SessionManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Biome-aware party objective HUD; legacy config keys preserve existing settings. */
public final class PartyObjectiveHud implements HudElement {
	private static final String[] BIRDS = {"Bluebird", "Parakeet", "Macaw"};
	private static final int[] BIRD_COLOURS = {0xFF1647D8, 0xFFA7E522, 0xFFF04A24};
	private static final int[] GEM_COLOURS = {0xFFA83DE9, 0xFF63DC2F, 0xFFF06B14};
	private static final int INCENSE_COLOUR = 0xFFD28AFF;
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
			case CAVERN -> SafariObjectives.gemzieDoorSettled();
			case HAUNTED -> SafariObjectives.doomspiralComplete();
			case ICY -> SafariObjectives.wumpaCaught();
		};
	}

	private static HudPanel forestPanel(Minecraft client) {
		SafariConfig.DisplayConfig config = ConfigManager.get().display;
		HudPanel panel = basePanel();
		if (config.partyObjectiveShowPlayers) addUnsynchronizedForestPlayers(panel, client);
		int drops = Math.min(9, BirdfeederWatch.floorFeedFound());
		int found = Math.max(0, BirdfeederWatch.feedFound());
		int used = Math.min(Math.max(0, BirdfeederWatch.feedUsed()), found);
		boolean done = BirdfeederWatch.allForestFeedUsed();
		panel.pair("Forest Drops", drops >= 9 ? "✔" : drops + "/9",
			0xFF55FFAA, drops >= 9 ? 0xFF55FF55 : 0xFFFFFFFF);
		if (config.partyObjectiveShowProgress) panel.pair("Feed Done", used + "/" + found,
			0xFF55FF55, done ? 0xFF55FF55 : 0xFFFFFFFF);
		if (config.partyObjectiveShowPlacement) addFeederRow(panel,
			BirdfeederWatch.feederType(), BirdfeederWatch.feederCount());
		if (config.partyObjectiveShowBirdCounts) panel.iconPair("Birds", 0xFF55FFFF, visibleBirds());
		return panel;
	}

	private static HudPanel cavernPanel(Minecraft client) {
		SafariConfig.DisplayConfig config = ConfigManager.get().display;
		HudPanel panel = basePanel();
		if (config.partyObjectiveShowPlayers) addUnsynchronizedObjectivePlayers(panel, client, SafariBiome.CAVERN);
		int placed = SafariObjectives.placedGemMask();
		if (config.partyObjectiveShowProgress) {
			int ready = Integer.bitCount(placed
				| (SafariObjectives.limeGemsHeld() > 0 ? 1 : 0)
				| (SafariObjectives.orangeGemsHeld() > 0 ? 2 : 0)
				| (SafariObjectives.purpleGemsHeld() > 0 ? 4 : 0));
			panel.pair("Gems", ready >= 3 ? "✔" : ready + "/3",
				0xFFFFAA00, ready >= 3 ? 0xFF55FF55 : 0xFFFFFFFF);
		}
		if (config.partyObjectiveShowPlacement) addPlacedGemsRow(panel, placed,
			SafariObjectives.gemzieDoorOpened());
		return panel;
	}

	private static HudPanel icyPanel() {
		SafariConfig.DisplayConfig config = ConfigManager.get().display;
		HudPanel panel = basePanel();
		int catches = icyUniqueCatches();
		if (config.partyObjectiveShowProgress) {
			panel.pair("Unique Catches", catches + "/8", 0xFF55CCFF,
				catches >= 8 ? 0xFF55FF55 : 0xFFFFFFFF);
		}
		if (config.partyObjectiveShowPlacement) {
			String value = SafariObjectives.wumpaCaught() ? "Caught"
				: SafariObjectives.wumpaSpawned() ? "Spawned"
				: catches >= 8 ? "Waiting" : "Locked";
			panel.pair("Wumpa", value, 0xFF55CCFF,
				SafariObjectives.wumpaCaught() ? 0xFF55FF55 : 0xFFFFFFFF);
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
		if (config.partyObjectiveShowPlayers) addUnsynchronizedObjectivePlayers(panel, client, SafariBiome.HAUNTED);
		int candles = Math.min(4, SafariObjectives.incenseUsed());
		if (config.partyObjectiveShowProgress) {
			int ready = Math.min(4, candles + SafariObjectives.incenseHeld());
			panel.pair("Incense", ready >= 4 ? "✔" : ready + "/4",
				0xFFAA55FF, ready >= 4 ? 0xFF55FF55 : 0xFFFFFFFF);
		}
		if (config.partyObjectiveShowPlacement) {
			String value = SafariObjectives.doomspiralCaught() ? "Caught"
				: SafariObjectives.doomspiralRetreated() ? "Retreated"
				: SafariObjectives.doomspiralSpawned() ? "Spawned"
				: candles >= 4 ? "Complete" : candles + "/4";
			panel.pair("Candles Lit", value, 0xFFAA55FF,
				SafariObjectives.doomspiralComplete() ? 0xFF55FF55 : 0xFFFFFFFF);
		}
		return panel;
	}

	private static HudPanel basePanel() {
		return objectivePanel(SafariObjectives.gemzieDoorSettled(),
			SafariObjectives.wumpaCaught(), SafariObjectives.doomspiralComplete(),
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
			panel.iconPair(client.player.getGameProfile().name(), 0xFFFFFFFF,
				feedValues(String.valueOf(berries), String.valueOf(worms), String.valueOf(seeds)));
			showed = true;
		}
		String localName = client.player.getGameProfile().name();
		for (String name : SafariPartyWatch.presentPlayerNames()) {
			if (name.equalsIgnoreCase(localName)) continue;
			panel.iconPair(name, 0xFFFFFFFF, feedValues("?", "?", "?"));
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
			panel.iconPair(localName, 0xFFFFFFFF, biome == SafariBiome.CAVERN
				? gemValues(String.valueOf(SafariObjectives.purpleGemsHeld()),
					String.valueOf(SafariObjectives.limeGemsHeld()),
					String.valueOf(SafariObjectives.orangeGemsHeld()))
				: incenseValue(String.valueOf(SafariObjectives.incenseHeld())));
			showed = true;
		}
		for (String name : SafariPartyWatch.presentPlayerNames()) {
			if (name.equalsIgnoreCase(localName)) continue;
			panel.iconPair(name, 0xFFFFFFFF, biome == SafariBiome.CAVERN
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

	public static void addPlacedGemsRow(HudPanel panel, int placedMask, boolean open) {
		if (open) {
			panel.pair("Placed Gems", "Open", 0xFFFFAA00, 0xFF55FF55);
			return;
		}
		panel.iconPair("Placed Gems", 0xFFFFAA00,
			gemValues((placedMask & 4) != 0 ? "✔" : "—",
				(placedMask & 1) != 0 ? "✔" : "—", (placedMask & 2) != 0 ? "✔" : "—"));
	}

	public static void addFeederRow(HudPanel panel, int type, int count) {
		if (type < 0 || count <= 0) {
			panel.pair("Birdfeeder", "Empty", 0xFF55FF55, 0xFF888888);
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
		panel.iconPair("Birdfeeder", 0xFF55FF55,
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
