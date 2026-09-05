package dev.serko.safariutils.client;

import com.google.gson.annotations.Expose;

/**
 * Settings mapped to {@code config/safariutils/safariutils.json}. Public field names
 * are the stable persistence keys; UI annotations are owned by Safari Utils.
 */
public class SafariConfig {

	/** Where an announcement goes. */
	public enum Broadcast {
		/** Kept to yourself. */
		NONE,
		/** {@code /pc} — the party. */
		PARTY,
		/** {@code /ac} — everyone on the island. */
		ALL;

		/** The Hypixel command, without the slash, or null when nothing is sent. */
		public String command() {
			return switch (this) {
				case PARTY -> "pc";
				case ALL -> "ac";
				case NONE -> null;
			};
		}
	}

	/** Which side of the bazaar spread a shard is valued at. */
	public enum PriceSource {
		/** The best standing buy order — what selling to the bazaar pays right now. */
		INSTANT_SELL,
		/** The cheapest standing sell offer — what you would list at, and wait for. */
		SELL_OFFER
	}

	/** Runnable id for the "Edit Hud Positions" button in the Display category. */
	private static final int EDIT_POSITIONS = 1;
	private static final int TESTING_SESSION = 2;

	public void executeRunnable(int runnableId) {
		if (runnableId == EDIT_POSITIONS) {
			HudEditorScreen.open();
			return;
		}
		if (runnableId == TESTING_SESSION) TestingMode.settingChanged();
	}

	public boolean isValidRunnable(int runnableId) {
		return runnableId == EDIT_POSITIONS || runnableId == TESTING_SESSION;
	}

	@SettingCategory(name = "Display", desc = "HUDs, waypoints, colors, and interface appearance")
	@Expose
	public DisplayConfig display = new DisplayConfig();

	@SettingCategory(name = "Gameplay", desc = "QOL gameplay features for the Safari")
	@Expose
	public GameplayConfig gameplay = new GameplayConfig();

	@SettingCategory(name = "Alerts", desc = "On-screen banner alerts for Safari Events, Encounters, and Contests")
	@Expose
	public AlertConfig alerts = new AlertConfig();

	@SettingCategory(name = "Chat Alerts", desc = "Messages sent to your selected chat channels")
	@Expose
	public PartyConfig party = new PartyConfig();

	@SettingCategory(name = "Sparkling", desc = "Sparkling detection, hunting, and shared collections")
	@Expose
	public SparklingConfig sparkling = new SparklingConfig();

	@SettingCategory(name = "Profit", desc = "Bazaar profit from Shards, Essence, and Sparkling drops")
	@Expose
	public ProfitConfig profit = new ProfitConfig();

	@SettingCategory(name = "Advanced", desc = "Extra themes and tools")
	@Expose
	public AdvancedConfig advanced = new AdvancedConfig();

	public static class GameplayConfig {
		@SettingInfo(name = "Auto Accept Hideyho",
			desc = "Automatically accepts Hideyho's Hide N' Seek game")
		@SettingToggle
		@Expose
		public boolean autoAcceptHideyho = true;

		@SettingInfo(name = "Protect Safari Ticket",
			desc = "Blocks ticket use until every party member has joined the Safari")
		@SettingToggle
		@Expose
		public boolean protectSafariTicket = true;
	}

	public static class DisplayConfig {
		@SettingInfo(name = "Settings Theme", desc = "Changes the colors and style of this settings menu")
		@SettingChoice(values = {"Default", "Rainbow", "Amethyst", "Arctic", "Aurora",
			"Blueprint", "Candy", "Canyon", "Cherry Blossom", "Coffee", "Copper", "Cyberpunk",
			"Deep Sea", "Desert", "Ember", "Ender", "Forest", "Frostfire", "Golden Hour",
			"Jade", "Lavender", "Matrix", "Midnight", "Monochrome", "Nebula", "Ocean",
			"Paper", "Rose", "Royal", "Slate", "Solarized", "Sunset", "Terminal", "Vaporwave",
			"Custom"})
		@Expose
		public int settingsTheme = 4;

		// Custom palette roles are edited through the theme modal, not ordinary cards.
		@SettingInfo(name = "Background", desc = "") @Expose
		public String customThemeBackground = "0:31:85:85:85";
		@SettingInfo(name = "Navigation Surface", desc = "") @Expose
		public String customThemeSurface = "0:68:30:87:174";
		@SettingInfo(name = "Cards", desc = "") @Expose
		public String customThemeCard = "0:217:24:27:36";
		@SettingInfo(name = "Hovered Cards", desc = "") @Expose
		public String customThemeCardHover = "0:227:34:39:51";
		@SettingInfo(name = "Selected Tabs", desc = "") @Expose
		public String customThemeSelected = "0:255:41:74:105";
		@SettingInfo(name = "Selected Sub-Tabs", desc = "") @Expose
		public String customThemeSubSelected = "0:255:68:55:81";
		@SettingInfo(name = "Borders", desc = "") @Expose
		public String customThemeBorder = "0:255:88:46:255";
		@SettingInfo(name = "Primary Accent", desc = "") @Expose
		public String customThemePrimary = "0:255:0:53:133";
		@SettingInfo(name = "Secondary Accent", desc = "") @Expose
		public String customThemeSecondary = "0:255:85:255:255";
		@SettingInfo(name = "Success", desc = "") @Expose
		public String customThemeSuccess = "0:255:85:255:136";
		@SettingInfo(name = "Error", desc = "") @Expose
		public String customThemeError = "0:255:106:133:0";
		@SettingInfo(name = "Highlight", desc = "") @Expose
		public String customThemeHighlight = "0:255:255:200:87";
		@SettingInfo(name = "Primary Text", desc = "") @Expose
		public String customThemeText = "0:255:198:255:184";
		@SettingInfo(name = "Secondary Text", desc = "") @Expose
		public String customThemeMuted = "0:255:133:0:80";
		@SettingInfo(name = "Muted Text", desc = "") @Expose
		public String customThemeDim = "0:255:28:28:28";
		@SettingInfo(name = "Safari Title", desc = "") @Expose
		public String customThemeSafariTitle = "0:255:85:255:255";
		@SettingInfo(name = "Utils Title", desc = "") @Expose
		public String customThemeUtilsTitle = "0:255:255:200:87";

		@SettingInfo(name = "Edit HUD Positions", desc = "Move and resize HUD panels")
		@SettingAction(runnableId = 1, buttonText = "Edit")
		@Expose
		public boolean editPositions = false;

		/** Editor-only preference; intentionally omitted from the ordinary settings list. */
		@Expose
		public boolean hudSnapping = true;

		/** Accordion id for the Progress HUD's grouped sub-settings. */
		private static final int PROGRESS_HUD = 4;

		@SettingInfo(name = "Progress HUD", desc = "")
		@SettingSection(id = PROGRESS_HUD)
		public boolean progressHudAccordion = false;

		@SettingInfo(name = "Show Progress HUD", desc = "Shows timing, biome progress, catches, and profit for the current run")
		@SettingToggle
		@SettingGroup(id = PROGRESS_HUD)
		@Expose
		public boolean hudEnabled = true;

		@SettingInfo(name = "Show In Canyon", desc = "Shows the Progress HUD while in Torrhus Canyon")
		@SettingToggle
		@SettingGroup(id = PROGRESS_HUD)
		@Expose
		public boolean showInCanyon = false;

		@SettingInfo(name = "Show Last Run",
			desc = "Shows most recent run while Progress HUD is visible outside of Safari")
		@SettingToggle
		@SettingGroup(id = PROGRESS_HUD)
		@Expose
		public boolean showLastRun = false;

		/** Accordion id for the Progress HUD's own non-"Show" grouped sub-settings. */
		private static final int PROGRESS_HUD_OPTIONS = 17;

		@SettingInfo(name = "Progress HUD Options", desc = "")
		@SettingSection(id = PROGRESS_HUD_OPTIONS)
		@SettingGroup(id = PROGRESS_HUD)
		public boolean progressHudOptionsAccordion = false;

		@SettingInfo(name = "Directional Expansion",
			desc = "Direction the HUD expands horizontally when text gets longer")
		@SettingChoice(values = {"Left", "Equal", "Right"})
		@SettingGroup(id = PROGRESS_HUD_OPTIONS)
		@Expose
		public int progressExpansion = 2;

		@SettingInfo(
			name = "Personal Hotspot",
			desc = "Shows which biome your hotspot is")
		@SettingToggle
		@SettingGroup(id = PROGRESS_HUD_OPTIONS)
		@Expose
		public boolean showHotspot = true;

		@SettingInfo(
			name = "Profit",
			desc = "Show what this run has earned, under the biome bars on the progress HUD")
		@SettingToggle
		@SettingGroup(id = PROGRESS_HUD_OPTIONS)
		@Expose
		public boolean shardProfit = true;

		@SettingInfo(name = "Per-Player Lines", desc = "Shows each player's unique catches by biome")
		@SettingToggle
		@SettingGroup(id = PROGRESS_HUD_OPTIONS)
		@Expose
		public boolean showPerPlayer = false;

		@SettingInfo(
			name = "Unique Only",
			desc = "Counts a species as complete after its first unique catch")
		@SettingToggle
		@SettingGroup(id = PROGRESS_HUD_OPTIONS)
		@Expose
		public boolean uniqueOnly = true;

		/** Accordion id for the Missing HUD's grouped sub-settings. */
		private static final int MISSING_HUD = 5;

		@SettingInfo(name = "Missing HUD", desc = "")
		@SettingSection(id = MISSING_HUD)
		public boolean missingHudAccordion = false;

		@SettingInfo(name = "Show Missing HUD", desc = "Shows catches and objectives still needed in the current biome")
		@SettingToggle
		@SettingGroup(id = MISSING_HUD)
		@Expose
		public boolean showMissing = true;

		/** Accordion id for the Missing HUD's own non-"Show" grouped sub-settings. */
		private static final int MISSING_HUD_OPTIONS = 18;

		@SettingInfo(name = "Missing HUD Options", desc = "")
		@SettingSection(id = MISSING_HUD_OPTIONS)
		@SettingGroup(id = MISSING_HUD)
		public boolean missingHudOptionsAccordion = false;

		@SettingInfo(name = "Directional Expansion",
			desc = "Direction the HUD expands horizontally when text gets longer")
		@SettingChoice(values = {"Left", "Equal", "Right"})
		@SettingGroup(id = MISSING_HUD_OPTIONS)
		@Expose
		public int missingExpansion = 2;

		@SettingInfo(
			name = "Total Catches",
			desc = "Lists total catches per species in each biome")
		@SettingToggle
		@SettingGroup(id = MISSING_HUD_OPTIONS)
		@Expose
		public boolean showTotalCatches = true;

		@SettingInfo(
			name = "Nearby Critters",
			desc = "Lists counts of nearby detected critters")
		@SettingToggle
		@SettingGroup(id = MISSING_HUD_OPTIONS)
		@Expose
		public boolean countSpawns = true;

		@SettingInfo(
			name = "Unique Only",
			desc = "Removes a critter from the Missing HUD after its first unique catch")
		@SettingToggle
		@SettingGroup(id = MISSING_HUD_OPTIONS)
		@Expose
		public boolean missingUniqueOnly = false;

		private static final int MISSING_HUD_OBJECTIVES = 26;

		@SettingInfo(name = "Objectives", desc = "")
		@SettingSection(id = MISSING_HUD_OBJECTIVES)
		@SettingGroup(id = MISSING_HUD_OPTIONS)
		public boolean missingHudObjectivesAccordion = false;

		@SettingInfo(
			name = "Floor Drops",
			desc = "Lists the floor drops you haven't collected yet per biome")
		@SettingToggle
		@SettingGroup(id = MISSING_HUD_OBJECTIVES)
		@Expose
		public boolean showFloorDropCount = true;

		@SettingInfo(
			name = "Rockmite Mounds",
			desc = "Lists count of unbroken rockmite mounds near you in the Cavern biome")
		@SettingToggle
		@SettingGroup(id = MISSING_HUD_OBJECTIVES)
		@Expose
		public boolean showMoundCount = true;

		@SettingInfo(
			name = "Snoozle Walls",
			desc = "Lists unbroken Snoozle walls in the Cavern biome")
		@SettingToggle
		@SettingGroup(id = MISSING_HUD_OBJECTIVES)
		@Expose
		public boolean showSnooperWalls = true;

		@SettingInfo(
			name = "Troodon Walls",
			desc = "Lists unbroken Troodon walls in the Icy biome")
		@SettingToggle
		@SettingGroup(id = MISSING_HUD_OBJECTIVES)
		@Expose
		public boolean showTroodonWalls = true;

		@SettingInfo(
			name = "Bee Nests",
			desc = "Lists the bee nests you haven't punched yet in the Forest biome")
		@SettingToggle
		@SettingGroup(id = MISSING_HUD_OBJECTIVES)
		@Expose
		public boolean showNests = true;

		@SettingInfo(
			name = "Bird Feed",
			desc = "Lists the bird feed you've found that hasn't spawned a bird yet in the Forest biome")
		@SettingToggle
		@SettingGroup(id = MISSING_HUD_OBJECTIVES)
		@Expose
		public boolean showBirdFeedCount = true;

		private static final int CONTEST_HUD = 19;

		@SettingInfo(name = "Contest HUD", desc = "")
		@SettingSection(id = CONTEST_HUD)
		public boolean contestHudAccordion = false;

		@SettingInfo(name = "Show Contest HUD",
			desc = "Shows the Contest HUD which gives information and standing related to the ongoing Miria's Contest")
		@SettingToggle
		@SettingGroup(id = CONTEST_HUD)
		@Expose
		public boolean showContestHud = true;

		@SettingInfo(name = "Show Everywhere",
			desc = "Show Contest HUD on all Islands, not just Torrhus Canyon and Safari")
		@SettingToggle
		@SettingGroup(id = CONTEST_HUD)
		@Expose
		public boolean contestShowEverywhere = true;

		@SettingInfo(name = "Show Outside Skyblock",
			desc = "Show Contest HUD outside of Skyblock")
		@SettingToggle
		@SettingGroup(id = CONTEST_HUD)
		@Expose
		public boolean contestShowOutsideSkyblock = false;

		private static final int CONTEST_HUD_OPTIONS = 20;

		@SettingInfo(name = "Contest HUD Options", desc = "")
		@SettingSection(id = CONTEST_HUD_OPTIONS)
		@SettingGroup(id = CONTEST_HUD)
		public boolean contestHudOptionsAccordion = false;

		@SettingInfo(name = "Directional Expansion",
			desc = "Direction the HUD expands horizontally when text gets longer")
		@SettingChoice(values = {"Left", "Equal", "Right"})
		@SettingGroup(id = CONTEST_HUD_OPTIONS)
		@Expose
		public int contestExpansion = 0;

		@SettingInfo(name = "Time Remaining",
			desc = "Shows time remaining in ongoing Miria's Contest")
		@SettingToggle
		@SettingGroup(id = CONTEST_HUD_OPTIONS)
		@Expose
		public boolean contestTimeRemaining = true;

		@SettingInfo(name = "Current Standing",
			desc = "Shows your current bracket and score in ongoing Miria's Contest")
		@SettingToggle
		@SettingGroup(id = CONTEST_HUD_OPTIONS)
		@Expose
		public boolean contestCurrentStanding = false;

		@SettingInfo(name = "Ticket Earned",
			desc = "Shows whether or not a Safari Ticket has been earned in ongoing Miria's Contest")
		@SettingToggle
		@SettingGroup(id = CONTEST_HUD_OPTIONS)
		@Expose
		public boolean contestTicketEarned = true;

		@SettingInfo(name = "Hide On Complete",
			desc = "Hide the Contest HUD if a Safari Ticket has been earned for the ongoing Miria's Contest")
		@SettingToggle
		@SettingGroup(id = CONTEST_HUD_OPTIONS)
		@Expose
		public boolean contestHideOnComplete = false;

		/** Accordion id for the grouped highlight toggles. */
		private static final int HIGHLIGHTS = 3;

		@SettingInfo(name = "Waypoints", desc = "")
		@SettingSection(id = HIGHLIGHTS)
		public boolean highlightsAccordion = false;

		@SettingInfo(name = "Include Distance", desc = "Shows your distance from each waypoint")
		@SettingToggle
		@SettingGroup(id = HIGHLIGHTS)
		@Expose
		public boolean waypointDistance = false;

		@SettingInfo(name = "Hide Possible",
			desc = "Hides unchecked Safe Mode locations until the critter or objective is visually confirmed")
		@SettingToggle
		@SettingGroup(id = HIGHLIGHTS)
		@Expose
		public boolean hidePossibleWaypoints = false;

		/** Accordion id for marks on fixed world features rather than living critters. */
		private static final int MARKERS = 7;

		@SettingInfo(name = "Objectives", desc = "")
		@SettingSection(id = MARKERS)
		@SettingGroup(id = HIGHLIGHTS)
		public boolean markersAccordion = false;

		@SettingInfo(
			name = "Floor Drops",
			desc = "Marks floor drops of the biome you are in")
		@SettingToggle
		@SettingGroup(id = MARKERS)
		@Expose
		public boolean floorDrops = true;

		/** Accordion id for the Recatch-specific settings, nested inside Critters. */
		private static final int RECATCH = 11;

		@SettingInfo(name = "Recatch", desc = "")
		@SettingSection(id = RECATCH)
		@SettingGroup(id = ENTITIES)
		public boolean recatchAccordion = false;

		@SettingInfo(
			name = "Recatch",
			desc = "Marks where a critter was caught when hit with a capsule in case of escape")
		@SettingToggle
		@SettingGroup(id = RECATCH)
		@Expose
		public boolean recatchHelper = true;

		@SettingInfo(name = "Pity Title", desc = "Adds the pity count to the recatch mark's title")
		@SettingToggle
		@SettingGroup(id = RECATCH)
		@Expose
		public boolean recatchPityTitle = true;

		@SettingInfo(
			name = "Match Critter Hitbox",
			desc = "Matches the respective critter's hitbox color instead of a set color")
		@SettingToggle
		@SettingGroup(id = RECATCH)
		@Expose
		public boolean recatchRarityColour = true;

		@SettingInfo(
			name = "Rockmite Mounds",
			desc = "Marks detected Rockmite Mounds while in the Cavern biome")
		@SettingToggle
		@SettingGroup(id = MARKERS)
		@Expose
		public boolean highlightMounds = true;

		@SettingInfo(name = "Snoozle Walls", desc = "Marks unbroken Snoozle walls while in the Cavern biome")
		@SettingToggle
		@SettingGroup(id = MARKERS)
		@Expose
		public boolean highlightSnooperWalls = true;

		@SettingInfo(name = "Troodon Walls", desc = "Marks unbroken Troodon walls while in the Icy biome")
		@SettingToggle
		@SettingGroup(id = MARKERS)
		@Expose
		public boolean highlightTroodonWalls = true;

		@SettingInfo(name = "Bee Nests", desc = "Marks unsearched Bee Nests in the Forest biome")
		@SettingToggle
		@SettingGroup(id = MARKERS)
		@Expose
		public boolean highlightNests = true;

		/** Accordion id for marks that follow a living critter's own position. */
		private static final int ENTITIES = 8;

		@SettingInfo(name = "Critters", desc = "")
		@SettingSection(id = ENTITIES)
		@SettingGroup(id = HIGHLIGHTS)
		public boolean entitiesAccordion = false;

		@SettingInfo(
			name = "Hideyho",
			desc = "Marks start and end locations of Hideyho while in the Haunted biome")
		@SettingToggle
		@SettingGroup(id = ENTITIES)
		@Expose
		public boolean hideyhoSolver = true;

		@SettingInfo(
			name = "Hideonwall",
			desc = "Marks Hideonwall while in the Haunted biome")
		@SettingToggle
		@SettingGroup(id = ENTITIES)
		@Expose
		public boolean highlightHideonwalls = true;

		@SettingInfo(
			name = "Duplico",
			desc = "Marks Duplico while in the Haunted biome")
		@SettingToggle
		@SettingGroup(id = ENTITIES)
		@Expose
		public boolean highlightDuplico = true;

		@SettingInfo(
			name = "Bloodbat",
			desc = "Marks Bloodbat while in the Haunted biome")
		@SettingToggle
		@SettingGroup(id = ENTITIES)
		@Expose
		public boolean highlightBloodbat = true;

		@SettingInfo(
			name = "Hideonfloor",
			desc = "Marks Hideonfloor while in the Forest biome")
		@SettingToggle
		@SettingGroup(id = ENTITIES)
		@Expose
		public boolean highlightHideonfloor = true;

		// --- colours -------------------------------------------------------------

		/** Accordion id for the colour pickers, kept together so they fold away. */
		private static final int COLOURS = 2;

		@SettingInfo(name = "Waypoint Colors", desc = "")
		@SettingSection(id = COLOURS)
		public boolean coloursAccordion = false;

		/** Accordion id for the colours of marks on fixed world features. */
		private static final int MARKERS_COLOUR = 9;
		/** Accordion id for Floor Drops' own two colours, nested under Objectives. */
		private static final int FLOOR_DROPS_COLOUR = 12;

		@SettingInfo(name = "Objectives", desc = "")
		@SettingSection(id = MARKERS_COLOUR)
		@SettingGroup(id = COLOURS)
		public boolean markersColourAccordion = false;

		@SettingInfo(name = "Floor Drops", desc = "")
		@SettingSection(id = FLOOR_DROPS_COLOUR)
		@SettingGroup(id = MARKERS_COLOUR)
		public boolean floorDropsColourAccordion = false;

		@SettingInfo(name = "Block Outline", desc = "")
		@SettingColor
		@SettingGroup(id = FLOOR_DROPS_COLOUR)
		@Expose
		public String floorDropColour = colour(0x55, 0xFF, 0xAA);

		@SettingInfo(name = "Block Face", desc = "")
		@SettingColor
		@SettingGroup(id = FLOOR_DROPS_COLOUR)
		@Expose
		// Alpha 128, not the usual 255 the colour() helper below always uses — the
		// face sits directly on the ground, and a face this size at full opacity
		// read as too strong; ~50% is closer to a highlight than a solid overlay.
		// If "100 to 50" meant a direct 0-255 value of 50 rather than a percentage,
		// this is the wrong number and should be corrected.
		public String floorDropFaceColour = "0:127:160:255:211";

		@SettingInfo(name = "Recatch Spot", desc = "")
		@SettingColor
		@SettingGroup(id = MARKERS_COLOUR)
		@Expose
		public String recatchColour = colour(0xFF, 0xFF, 0xFF);

		@SettingInfo(name = "Rockmite Mounds", desc = "")
		@SettingColor
		@SettingGroup(id = MARKERS_COLOUR)
		@Expose
		public String moundColour = colour(0x9A, 0xC0, 0xCD);

		@SettingInfo(name = "Snoozle Walls", desc = "")
		@SettingColor
		@SettingGroup(id = MARKERS_COLOUR)
		@Expose
		public String snooperWallColour = colour(0x00, 0x05, 0xFF);

		@SettingInfo(name = "Troodon Walls", desc = "")
		@SettingColor
		@SettingGroup(id = MARKERS_COLOUR)
		@Expose
		public String troodonWallColour = colour(0x55, 0xAA, 0xFF);

		@SettingInfo(name = "Bee Nests", desc = "")
		@SettingColor
		@SettingGroup(id = MARKERS_COLOUR)
		@Expose
		public String nestColour = colour(0xFF, 0xE8, 0x40);

		/** Accordion id for the colours of marks that follow a living critter. */
		private static final int ENTITIES_COLOUR = 10;

		@SettingInfo(name = "Critters", desc = "")
		@SettingSection(id = ENTITIES_COLOUR)
		@SettingGroup(id = COLOURS)
		public boolean entitiesColourAccordion = false;

		@SettingInfo(name = "Hideyho", desc = "")
		@SettingColor
		@SettingGroup(id = ENTITIES_COLOUR)
		@Expose
		public String hideyhoColour = colour(0x5F, 0x00, 0xFF);

		@SettingInfo(name = "Hideonwall", desc = "")
		@SettingColor
		@SettingGroup(id = ENTITIES_COLOUR)
		@Expose
		public String hideonwallColour = colour(0xFF, 0x00, 0xFF);

		@SettingInfo(name = "Duplico", desc = "")
		@SettingColor
		@SettingGroup(id = ENTITIES_COLOUR)
		@Expose
		public String duplicoColour = colour(0xFF, 0x00, 0x00);

		@SettingInfo(name = "Bloodbat", desc = "")
		@SettingColor
		@SettingGroup(id = ENTITIES_COLOUR)
		@Expose
		public String bloodbatColour = colour(0xBF, 0xFF, 0x00);

		@SettingInfo(name = "Hideonfloor", desc = "")
		@SettingColor
		@SettingGroup(id = ENTITIES_COLOUR)
		@Expose
		public String hideonfloorColour = colour(0xFF, 0x00, 0xFF);

		private static final int HUD_BORDER_COLOURS = 21;

		@SettingInfo(name = "HUD Border Colors", desc = "")
		@SettingSection(id = HUD_BORDER_COLOURS)
		public boolean hudBorderColoursAccordion = false;

		private static final int PROGRESS_HUD_BORDER = 22;
		private static final int MISSING_HUD_BORDER = 23;
		private static final int CONTEST_HUD_BORDER = 24;

		@SettingInfo(name = "Current Run Tab", desc = "")
		@SettingColor @SettingGroup(id = HUD_BORDER_COLOURS) @Expose
		public String currentRunTabBorderColour = colour(0x55, 0xFF, 0x55);

		@SettingInfo(name = "History Tab", desc = "")
		@SettingColor @SettingGroup(id = HUD_BORDER_COLOURS) @Expose
		public String historyTabBorderColour = colour(0xFF, 0xAA, 0x00);

		@SettingInfo(name = "Stats Tab", desc = "")
		@SettingColor @SettingGroup(id = HUD_BORDER_COLOURS) @Expose
		public String statsTabBorderColour = colour(0x55, 0xFF, 0xFF);

		@SettingInfo(name = "Progress HUD", desc = "")
		@SettingSection(id = PROGRESS_HUD_BORDER)
		@SettingGroup(id = HUD_BORDER_COLOURS)
		public boolean progressHudBorderAccordion = false;

		@SettingInfo(name = "Show Border", desc = "Shows a border around the Progress HUD")
		@SettingToggle @SettingGroup(id = PROGRESS_HUD_BORDER) @Expose
		public boolean progressHudBorder = true;

		@SettingInfo(name = "Color", desc = "")
		@SettingColor @SettingGroup(id = PROGRESS_HUD_BORDER) @Expose
		public String progressHudBorderColour = colour(0x55, 0xFF, 0x55);

		@SettingInfo(name = "Use Biome Color", desc = "Uses the current biome's color for the Progress HUD border")
		@SettingToggle @SettingGroup(id = PROGRESS_HUD_BORDER) @Expose
		public boolean progressBorderUseBiomeColour = true;

		@SettingInfo(name = "Missing HUD", desc = "")
		@SettingSection(id = MISSING_HUD_BORDER)
		@SettingGroup(id = HUD_BORDER_COLOURS)
		public boolean missingHudBorderAccordion = false;

		@SettingInfo(name = "Show Border", desc = "Shows a border around the Missing HUD")
		@SettingToggle @SettingGroup(id = MISSING_HUD_BORDER) @Expose
		public boolean missingHudBorder = true;

		@SettingInfo(name = "Color", desc = "")
		@SettingColor @SettingGroup(id = MISSING_HUD_BORDER) @Expose
		public String missingHudBorderColour = colour(0xFF, 0xAA, 0x00);

		@SettingInfo(name = "Completed Missing HUD", desc = "")
		@SettingColor @SettingGroup(id = MISSING_HUD_BORDER) @Expose
		public String completedMissingHudBorderColour = colour(0x55, 0xFF, 0x55);

		@SettingInfo(name = "Use Biome Color", desc = "Uses the current biome's color for the Missing HUD border")
		@SettingToggle @SettingGroup(id = MISSING_HUD_BORDER) @Expose
		public boolean missingBorderUseBiomeColour = true;

		@SettingInfo(name = "Contest HUD", desc = "Used when bracket coloring is off or no bracket is known")
		@SettingSection(id = CONTEST_HUD_BORDER)
		@SettingGroup(id = HUD_BORDER_COLOURS)
		public boolean contestHudBorderAccordion = false;

		@SettingInfo(name = "Show Border", desc = "Shows a border around the Contest HUD")
		@SettingToggle @SettingGroup(id = CONTEST_HUD_BORDER) @Expose
		public boolean contestHudBorder = true;

		@SettingInfo(name = "Color", desc = "Used when bracket coloring is off or no bracket is known")
		@SettingColor @SettingGroup(id = CONTEST_HUD_BORDER) @Expose
		public String contestHudBorderColour = colour(0xE0, 0xC5, 0x18);

		@SettingInfo(name = "Use Ticket Status", desc = "Uses Safari Ticket status for the Contest HUD border")
		@SettingToggle @SettingGroup(id = CONTEST_HUD_BORDER) @Expose
		public boolean contestBorderUseTicketStatus = true;

		@SettingInfo(name = "Use Bracket Color", desc = "Uses the current contest bracket's color for the Contest HUD border")
		@SettingToggle @SettingGroup(id = CONTEST_HUD_BORDER) @Expose
		public boolean contestBorderUseBracketColour = false;

		/**
		 * A legacy-compatible static colour value used by the custom picker.
		 *
		 * <p>{@code speed:alpha:r:g:b} — speed 0 meaning it does not cycle. Stored as that
		 * string rather than as an int so the picker can offer alpha and chroma at all.
		 */
		private static String colour(int red, int green, int blue) {
			return "0:255:%d:%d:%d".formatted(red, green, blue);
		}

		/** Accordion id for the hitbox settings. */
		private static final int HITBOXES = 6;
		private static final int HITBOX_COLOURS = 25;

		@SettingInfo(name = "Critter Hitboxes", desc = "")
		@SettingSection(id = HITBOXES)
		public boolean hitboxesAccordion = false;

		@SettingInfo(name = "Enable Hitboxes", desc = "Shows critter hitboxes within the selected distance")
		@SettingToggle
		@SettingGroup(id = HITBOXES)
		@Expose
		public boolean enableHitboxes = true;

		@SettingInfo(name = "Hitbox Distance", desc = "Maximum distance at which critter hitboxes are shown")
		@SettingRange(minValue = 0, maxValue = 200, minStep = 1)
		@SettingGroup(id = HITBOXES)
		@Expose
		public int hitboxDistance = 50;

		@SettingInfo(name = "Pity Title", desc = "Shows a pity count as each hitbox's title")
		@SettingToggle
		@SettingGroup(id = HITBOXES)
		@Expose
		public boolean hitboxPityTitle = true;

		@SettingInfo(name = "Color Settings", desc = "")
		@SettingSection(id = HITBOX_COLOURS)
		@SettingGroup(id = HITBOXES)
		public boolean hitboxColoursAccordion = false;

		@SettingInfo(name = "Hitbox Color", desc = "")
		@SettingColor
		@SettingGroup(id = HITBOX_COLOURS)
		@Expose
		public String hitboxColour = colour(0xFF, 0xFF, 0xFF);

		@SettingInfo(name = "Unique Status Colors",
			desc = "Colors critter hitboxes red before their run unique and green afterward")
		@SettingToggle
		@SettingGroup(id = HITBOX_COLOURS)
		@Expose
		public boolean uniqueHitboxColours = true;

		@SettingInfo(name = "Rarity Color",
			desc = "Colors hitboxes and their titles by the critter's own rarity instead of a fixed color")
		@SettingToggle
		@SettingGroup(id = HITBOX_COLOURS)
		@Expose
		public boolean hitboxRarityColour = true;

		@SettingInfo(name = "Critter Color Override",
			desc = "Applies unique and rarity coloring to Hideyhos, Hideonwalls, Duplicos, Bloodbats, and Hideonfloors")
		@SettingToggle
		@SettingGroup(id = HITBOX_COLOURS)
		@Expose
		public boolean hitboxEntityColorOverride = false;

		@SettingInfo(
			name = "Remove Cold Overlay",
			desc = "Removes the cold overlay that appears in the Icy Biome")
		@SettingToggle
		@Expose
		public boolean removeColdOverlay = true;

		@SettingInfo(
			name = "Remove Warden Darkness",
			desc = "Removes Warden darkness effect while catching Doomspiral")
		@SettingToggle
		@Expose
		public boolean removeDarkness = true;

		// Not shown in the menu — the drag-to-place editor's scroll-to-resize is the
		// only way to change these, and it reads/writes these fields directly.
		@Expose
		public float progressScale = 1.0f;

		@Expose
		public float missingScale = 1.0f;

		@Expose
		public float contestScale = 1.0f;

		// Positions are fractions of the screen, so a box stays put across resolution
		// and GUI-scale changes. Set by dragging in the editor, not by hand.
		@Expose
		public float progressX = 0.0035128805f;
		@Expose
		public float progressY = 0.00625f;
		@Expose
		public float missingX = 0.0035128805f;
		@Expose
		public float missingY = 0.29375f;
		public float contestX = 0.99531615f;
		@Expose
		public float contestY = 0.00625f;

		@Expose
		public long contestSavedCycle = Long.MIN_VALUE;
		@Expose
		public String contestSavedBracket = "";
		@Expose
		public int contestSavedScore = -1;
		@Expose
		public boolean contestSavedTicket = false;
		@Expose
		public boolean contestSavedTicketAlerted = false;
		@Expose
		public boolean contestSavedStartAlertPlayed = true;
	}

	public static class ProfitConfig {

		@SettingInfo(
			name = "Track Safari Profit",
			desc = "Prices the Bazaar value of Shards, Essence, and Rainbow Feathers given in a run")
		@SettingToggle
		@Expose
		public boolean enabled = true;

		@SettingInfo(
			name = "Pricing Option",
			desc = "How shards are priced")
		@SettingChoice(values = {"Instant Sell", "Sell Offer"})
		@Expose
		public int priceSource = 0;

		/** The chosen side, guarded against a config file holding something out of range. */
		public PriceSource priceSource() {
			return priceSource >= 0 && priceSource < PriceSource.values().length
				? PriceSource.values()[priceSource] : PriceSource.INSTANT_SELL;
		}

	}

	public static class AdvancedConfig {
		private static final int TESTING = 24;
		private static final int SAFE_MODE = 18;
		private static final int SAFE_MODE_OPTIONS = 19;
		private static final int SAFE_DETECTION_HUD = 20;
		private static final int SAFE_CRITTER_OVERLAYS = 21;
		private static final int SAFE_HIDDEN_CRITTERS = 22;
		private static final int SAFE_STATIC_OBJECTIVES = 23;

		@SettingInfo(name = "Special Themes",
			desc = "Applies a special theme to stats HUDs and on-screen HUDs")
		@SettingChoice(values = {"Off", "Rainbow"})
		@Expose
		public int specialTheme = 0;

		@SettingInfo(name = "Safe Mode", desc = "Limits tracking to information the player has visibly confirmed")
		@SettingSection(id = SAFE_MODE)
		public boolean safeModeAccordion = false;

		@SettingInfo(name = "Safe Mode",
			desc = "Uses the visibility-based behavior selected below")
		@SettingToggle
		@SettingGroup(id = SAFE_MODE)
		@Expose
		public boolean safeMode = true;

		@SettingInfo(name = "Safe Mode Options", desc = "Choose which features require visible confirmation")
		@SettingSection(id = SAFE_MODE_OPTIONS)
		@SettingGroup(id = SAFE_MODE)
		public boolean safeModeOptionsAccordion = false;

		@SettingInfo(name = "Detection And HUD", desc = "Controls when critters and completion information become known")
		@SettingSection(id = SAFE_DETECTION_HUD)
		@SettingGroup(id = SAFE_MODE_OPTIONS)
		public boolean safeDetectionHudAccordion = false;

		@SettingInfo(name = "Visible Critter Detection",
			desc = "§6Safe: Detects a critter after its body or visible name tag is seen\n§7Normal: Detects every loaded critter")
		@SettingToggle
		@SettingGroup(id = SAFE_DETECTION_HUD)
		@Expose
		public boolean safeVisibleCritterDetection = true;

		@SettingInfo(name = "Nearby Counts",
			desc = "§6Safe: Counts only critters whose body or visible name tag is seen\n§7Normal: Counts every loaded critter")
		@SettingToggle
		@SettingGroup(id = SAFE_DETECTION_HUD)
		@Expose
		public boolean safeHideNearbyCounts = true;

		@SettingInfo(name = "Species Availability",
			desc = "§6Safe: Keeps an unseen species listed until its absence is visibly confirmed\n§7Normal: Uses every detected objective state")
		@SettingToggle
		@SettingGroup(id = SAFE_DETECTION_HUD)
		@Expose
		public boolean safeConservativeAvailability = true;

		@SettingInfo(name = "Completion Checks",
			desc = "§6Safe: Completes objectives only from catches and visible evidence\n§7Normal: Uses every detected objective state")
		@SettingToggle
		@SettingGroup(id = SAFE_DETECTION_HUD)
		@Expose
		public boolean safeConservativeCompletion = true;

		@SettingInfo(name = "Critter Overlays", desc = "Controls when critter hitboxes and waypoints are shown")
		@SettingSection(id = SAFE_CRITTER_OVERLAYS)
		@SettingGroup(id = SAFE_MODE_OPTIONS)
		public boolean safeCritterOverlaysAccordion = false;

		@SettingInfo(name = "Critter Hitboxes",
			desc = "§6Safe: Shows a hitbox only while its critter or visible name tag is seen\n§7Normal: Shows loaded hitboxes through terrain")
		@SettingToggle
		@SettingGroup(id = SAFE_CRITTER_OVERLAYS)
		@Expose
		public boolean safeCritterHitboxes = true;

		@SettingInfo(name = "Sparkling Critters",
			desc = "§6Safe: Reveals a Sparkling after its body or visible name tag is seen\n§7Normal: Reveals loaded Sparkling information immediately")
		@SettingToggle
		@SettingGroup(id = SAFE_CRITTER_OVERLAYS)
		@Expose
		public boolean safeSparklingCritters = true;

		@SettingInfo(name = "Initially Hidden Critters", desc = "Controls possible locations for critters hidden at spawn")
		@SettingSection(id = SAFE_HIDDEN_CRITTERS)
		@SettingGroup(id = SAFE_CRITTER_OVERLAYS)
		public boolean safeHiddenCrittersAccordion = false;

		@SettingInfo(name = "Hideyho", desc = "§6Safe: Shows learned locations until each is checked\n§7Normal: Reveals its detected location immediately")
		@SettingToggle @SettingGroup(id = SAFE_HIDDEN_CRITTERS) @Expose
		public boolean safeHideyho = true;

		@SettingInfo(name = "Hideonwall", desc = "§6Safe: Shows learned locations until each is checked\n§7Normal: Reveals its detected location immediately")
		@SettingToggle @SettingGroup(id = SAFE_HIDDEN_CRITTERS) @Expose
		public boolean safeHideonwall = true;

		@SettingInfo(name = "Duplico", desc = "§6Safe: Shows learned locations until each is checked\n§7Normal: Reveals its detected location immediately")
		@SettingToggle @SettingGroup(id = SAFE_HIDDEN_CRITTERS) @Expose
		public boolean safeDuplico = true;

		@SettingInfo(name = "Bloodbat", desc = "§6Safe: Shows learned locations until each is checked\n§7Normal: Reveals its detected location immediately")
		@SettingToggle @SettingGroup(id = SAFE_HIDDEN_CRITTERS) @Expose
		public boolean safeBloodbat = true;

		@SettingInfo(name = "Hideonfloor", desc = "§6Safe: Shows learned locations until each is checked\n§7Normal: Reveals its detected location immediately")
		@SettingToggle @SettingGroup(id = SAFE_HIDDEN_CRITTERS) @Expose
		public boolean safeHideonfloor = true;

		@SettingInfo(name = "Static Objectives", desc = "Controls learned locations for fixed Safari objectives")
		@SettingSection(id = SAFE_STATIC_OBJECTIVES)
		@SettingGroup(id = SAFE_MODE_OPTIONS)
		public boolean safeStaticObjectivesAccordion = false;

		@SettingInfo(name = "Floor Drops", desc = "§6Safe: Shows learned locations until checked\n§7Normal: Shows detected active drops")
		@SettingToggle @SettingGroup(id = SAFE_STATIC_OBJECTIVES) @Expose
		public boolean safeFloorDrops = true;

		@SettingInfo(name = "Bee Nests", desc = "§6Safe: Shows learned locations until checked\n§7Normal: Shows detected active nests")
		@SettingToggle @SettingGroup(id = SAFE_STATIC_OBJECTIVES) @Expose
		public boolean safeBeeNests = true;

		@SettingInfo(name = "Rockmite Mounds", desc = "§6Safe: Shows learned locations until checked\n§7Normal: Shows detected active mounds")
		@SettingToggle @SettingGroup(id = SAFE_STATIC_OBJECTIVES) @Expose
		public boolean safeRockmiteMounds = true;

		@SettingInfo(name = "Snoozle Walls", desc = "§6Safe: Changes a wall only after it is visibly checked\n§7Normal: Reads every loaded wall state")
		@SettingToggle @SettingGroup(id = SAFE_STATIC_OBJECTIVES) @Expose
		public boolean safeSnoozleWalls = true;

		@SettingInfo(name = "Troodon Walls", desc = "§6Safe: Changes a wall only after it is visibly checked\n§7Normal: Reads every loaded wall state")
		@SettingToggle @SettingGroup(id = SAFE_STATIC_OBJECTIVES) @Expose
		public boolean safeTroodonWalls = true;

		@SettingInfo(name = "Testing", desc = "Temporary logging and visualization tools")
		@SettingSection(id = TESTING)
		public boolean testingAccordion = false;

		@SettingInfo(name = "Testing Session",
			desc = "Runs normally without saving run history, totals, settings, or other progress until Minecraft restarts")
		@SettingToggle(runnableId = TESTING_SESSION)
		@SettingGroup(id = TESTING)
		public transient boolean testingSession = false;

		@SettingInfo(name = "Save Learned Locations",
			desc = "Saves newly confirmed objective and initially stationary critter locations during solo runs")
		@SettingToggle
		@SettingGroup(id = TESTING)
		public transient boolean testingSaveLearnedLocations = false;

		/** Accordion id for the debug-logging toggles. */
		private static final int DEBUG_LOGGING = 1;

		@SettingInfo(name = "Debug Logging", desc = "Records selected game and Safari activity for troubleshooting")
		@SettingSection(id = DEBUG_LOGGING)
		@SettingGroup(id = TESTING)
		public boolean debugLoggingAccordion = false;

		@SettingInfo(
			name = "Output Log",
			desc = "Writes a running log of entity and event activity to disk, for diagnosing bugs.\n" +
				"§7A new timestamped file each time this is turned on, in config/safariutils/logs")
		@SettingToggle
		@SettingGroup(id = DEBUG_LOGGING)
		@Expose
		public boolean debugLog = false;

		@SettingInfo(name = "Output Log Preset",
			desc = "Applies a ready-made set of testing and logging options")
		@SettingChoice(values = {"All", "Custom", "Party And Server", "Run Lifecycle",
			"Sparkling Research", "Static Locations", "World Tracking"})
		@SettingGroup(id = DEBUG_LOGGING)
		public transient int outputLogPreset = 1;

		/** Accordion id for which categories the Output Log actually writes. */
		private static final int OUTPUT_LOG_OPTIONS = 13;
		private static final int OUTPUT_SESSION_DATA = 15;
		private static final int OUTPUT_CRITTER_DETECTION = 16;
		private static final int OUTPUT_WORLD_TRACKING = 17;

		@SettingInfo(name = "Output Log Options",
			desc = "Choose which information is written while Output Log is enabled")
		@SettingSection(id = OUTPUT_LOG_OPTIONS)
		@SettingGroup(id = DEBUG_LOGGING)
		public boolean outputLogOptionsAccordion = false;

		@SettingInfo(name = "Session And Server", desc = "Chat, location, roster, server, and run-state diagnostics")
		@SettingSection(id = OUTPUT_SESSION_DATA)
		@SettingGroup(id = OUTPUT_LOG_OPTIONS)
		public boolean outputSessionDataAccordion = false;

		@SettingInfo(name = "Raw Chat", desc = "Every game chat line, unfiltered, exactly as it arrived")
		@SettingToggle
		@SettingGroup(id = OUTPUT_SESSION_DATA)
		@Expose
		public boolean logRaw = false;

		@SettingInfo(name = "Location And Lobby", desc = "Area, sub-area, biome, lobby id, and Safari entry or exit changes")
		@SettingToggle
		@SettingGroup(id = OUTPUT_SESSION_DATA)
		@Expose
		public boolean logLocation = false;

		@SettingInfo(name = "Party Roster", desc = "Player names, UUIDs, and displayed tab names whenever the roster changes")
		@SettingToggle
		@SettingGroup(id = OUTPUT_SESSION_DATA)
		@Expose
		public boolean logPartyRoster = false;

		@SettingInfo(name = "Full Party Timing", desc = "Raw and stabilized Safari player counts with alert timing")
		@SettingToggle
		@SettingGroup(id = OUTPUT_SESSION_DATA)
		@Expose
		public boolean logPartyTiming = false;

		@SettingInfo(name = "GUI And NPC Interactions",
			desc = "Safari NPC uses, GUI openings, clicks, slots, and container changes")
		@SettingToggle
		@SettingGroup(id = OUTPUT_SESSION_DATA)
		@Expose
		public boolean logInterfaces = false;

		@SettingInfo(name = "Tab List Changes", desc = "A snapshot whenever the stripped tab list changes")
		@SettingToggle
		@SettingGroup(id = OUTPUT_SESSION_DATA)
		@Expose
		public boolean logTabList = false;

		@SettingInfo(name = "Scoreboard Changes", desc = "A snapshot whenever the sidebar scoreboard changes")
		@SettingToggle
		@SettingGroup(id = OUTPUT_SESSION_DATA)
		@Expose
		public boolean logScoreboard = false;

		@SettingInfo(name = "Inventory And Hotbar", desc = "Non-empty inventory and hotbar slots whenever their contents change")
		@SettingToggle
		@SettingGroup(id = OUTPUT_SESSION_DATA)
		@Expose
		public boolean logInventory = false;

		@SettingInfo(name = "Run And Event Tracking", desc = "Parsed catches, activation, and run lifecycle decisions")
		@SettingSection(id = OUTPUT_CRITTER_DETECTION)
		@SettingGroup(id = OUTPUT_LOG_OPTIONS)
		public boolean outputCritterDetectionAccordion = false;

		@SettingInfo(name = "Parsed Catch Events",
			desc = "Attempts, escapes, and catches, once matched to a species and a chat line")
		@SettingToggle
		@SettingGroup(id = OUTPUT_CRITTER_DETECTION)
		@Expose
		public boolean logChat = false;

		@SettingInfo(name = "Run Start And End", desc = "When a run opens or closes, and why")
		@SettingToggle
		@SettingGroup(id = OUTPUT_CRITTER_DETECTION)
		@Expose
		public boolean logRun = false;

		@SettingInfo(name = "Run Activation", desc = "Safari entry, Manager-message matching, pending events, and activation decisions")
		@SettingToggle
		@SettingGroup(id = OUTPUT_CRITTER_DETECTION)
		@Expose
		public boolean logActivation = false;

		@SettingInfo(name = "World And Entity Tracking", desc = "Entity detection, waypoints, encounters, and Safari world features")
		@SettingSection(id = OUTPUT_WORLD_TRACKING)
		@SettingGroup(id = OUTPUT_LOG_OPTIONS)
		public boolean outputWorldTrackingAccordion = false;

		@SettingInfo(name = "Entity Sightings",
			desc = "A critter's label appearing, disappearing, or changing id underneath it")
		@SettingToggle
		@SettingGroup(id = OUTPUT_WORLD_TRACKING)
		@Expose
		public boolean logSighting = false;

		@SettingInfo(name = "Nearby Counts", desc = "How many of a species are currently loaded, each time it changes")
		@SettingToggle
		@SettingGroup(id = OUTPUT_WORLD_TRACKING)
		@Expose
		public boolean logNearby = false;

		@SettingInfo(name = "Entity Pairing",
			desc = "How a label was matched to its mob, and details on any match that failed")
		@SettingToggle
		@SettingGroup(id = OUTPUT_WORLD_TRACKING)
		@Expose
		public boolean logPair = false;

		@SettingInfo(name = "Ball Position Data",
			desc = "Where a capsule's ball appears relative to the player and the critter, for pattern data")
		@SettingToggle
		@SettingGroup(id = OUTPUT_WORLD_TRACKING)
		@Expose
		public boolean logBall = false;

		@SettingInfo(name = "Recatch Pins And Pity",
			desc = "A pin being placed or cleared, and every pity count change behind it")
		@SettingToggle
		@SettingGroup(id = OUTPUT_WORLD_TRACKING)
		@Expose
		public boolean logRecatch = false;

		@SettingInfo(name = "Hitbox Suppression",
			desc = "A hitbox being hidden or shown again because of a recatch pin over the same individual")
		@SettingToggle
		@SettingGroup(id = OUTPUT_WORLD_TRACKING)
		@Expose
		public boolean logDraw = false;

		@SettingInfo(name = "Stale Waypoint Fixes",
			desc = "A tracked entity's waypoint being replaced after it woke up under a new id nearby")
		@SettingToggle
		@SettingGroup(id = OUTPUT_WORLD_TRACKING)
		@Expose
		public boolean logStill = false;

		@SettingInfo(name = "Wall Tracking", desc = "A Snoozle or Troodon wall's state changing")
		@SettingToggle
		@SettingGroup(id = OUTPUT_WORLD_TRACKING)
		@Expose
		public boolean logWall = false;

		@SettingInfo(name = "Floor Drops", desc = "A floor drop being confirmed, and its shard credited if it was one")
		@SettingToggle
		@SettingGroup(id = OUTPUT_WORLD_TRACKING)
		@Expose
		public boolean logFloor = false;

		@SettingInfo(name = "Hideyho Tracking", desc = "Hideyho's own detection and hitbox placement")
		@SettingToggle
		@SettingGroup(id = OUTPUT_WORLD_TRACKING)
		@Expose
		public boolean logHideyho = false;

		@SettingInfo(name = "Head Start Scan", desc = "What the inventory scan for starting items found")
		@SettingToggle
		@SettingGroup(id = OUTPUT_WORLD_TRACKING)
		@Expose
		public boolean logHeadstart = false;

		@SettingInfo(name = "Nest Tracking",
			desc = "The Forest sweep for bee nests starting, finishing, and what it actually found")
		@SettingToggle
		@SettingGroup(id = OUTPUT_WORLD_TRACKING)
		@Expose
		public boolean logNest = false;

		@SettingInfo(
			name = "Critter Count Peaks",
			desc = "Highest concurrent count reached by each species during a run")
		@SettingToggle
		@SettingGroup(id = OUTPUT_WORLD_TRACKING)
		@Expose
		public boolean logCritterCounts = false;

		@SettingInfo(name = "Static Waypoint Locations",
			desc = "New stationary objective and initially hidden critter candidate positions")
		@SettingToggle
		@SettingGroup(id = OUTPUT_WORLD_TRACKING)
		@Expose
		public boolean logStaticWaypoints = false;

		@SettingInfo(name = "Critter Particles",
			desc = "Aggregates server particle packets and associates them with nearby critters")
		@SettingToggle
		@SettingGroup(id = OUTPUT_WORLD_TRACKING)
		@Expose
		public boolean logParticles = false;

		@SettingInfo(name = "Sparkling Detection",
			desc = "Records Sparkling discovery, visibility, entity replacement, and catch decisions")
		@SettingToggle
		@SettingGroup(id = OUTPUT_WORLD_TRACKING)
		@Expose
		public boolean logSparkling = false;

		/** Accordion id for hitboxes on entity types outside the normal critter set. */
		private static final int DIAGNOSTIC_HITBOXES = 14;

		@SettingInfo(name = "Diagnostic Hitboxes",
			desc = "Shows entity wireframes for identifying critter bodies and nearby objects")
		@SettingSection(id = DIAGNOSTIC_HITBOXES)
		@SettingGroup(id = TESTING)
		public boolean diagnosticHitboxesAccordion = false;

		@SettingInfo(name = "Critters",
			desc = "Every critter's own hitbox too, all in this diagnostic colour instead of rarity — " +
				"there was previously no way to see them alongside the other diagnostic types below")
		@SettingToggle
		@SettingGroup(id = DIAGNOSTIC_HITBOXES)
		@Expose
		public boolean showAllCritterHitboxes = false;

		@SettingInfo(name = "Armor Stands",
			desc = "Every armor stand nearby, named or not — this is how Gazer's real body, an unnamed " +
				"one otherwise excluded from normal pairing entirely, was actually found")
		@SettingToggle
		@SettingGroup(id = DIAGNOSTIC_HITBOXES)
		@Expose
		public boolean showAllArmorStands = false;

		@SettingInfo(name = "Item Displays",
			desc = "Every item display nearby — this is what a capsule's ball is made of, and also " +
				"what Duplico's own body renders as")
		@SettingToggle
		@SettingGroup(id = DIAGNOSTIC_HITBOXES)
		@Expose
		public boolean showAllItemDisplays = false;

		@SettingInfo(name = "Interactions",
			desc = "Every interaction entity nearby — invisible hitboxes with no model of their own; " +
				"this is what a Rockmite mound actually is")
		@SettingToggle
		@SettingGroup(id = DIAGNOSTIC_HITBOXES)
		@Expose
		public boolean showAllInteractions = false;

		@SettingInfo(name = "Block Displays",
			desc = "Every block display nearby — a floating, scaled, or rotated block model with no " +
				"real block behind it")
		@SettingToggle
		@SettingGroup(id = DIAGNOSTIC_HITBOXES)
		@Expose
		public boolean showAllBlockDisplays = false;

		@SettingInfo(name = "Text Displays",
			desc = "Every text display nearby — floating text with no name tag or entity attached to it")
		@SettingToggle
		@SettingGroup(id = DIAGNOSTIC_HITBOXES)
		@Expose
		public boolean showAllTextDisplays = false;

	}

	public static class AlertConfig {

		/** Accordion id for the banner alert's own duration/scale/position settings. */
		private static final int ALERT_SETTINGS = 15;
		/** Accordion id for the individual per-alert on/off toggles. */
		private static final int ALERT_TOGGLES = 16;
		private static final int SAFARI_ALERTS = 17;
		private static final int ENCOUNTER_ALERTS = 18;
		private static final int ALERT_COLOURS = 19;
		private static final int ENCOUNTER_ALERT_COLOURS = 20;
		private static final int GEMZIE_ALERT_COLOURS = 21;
		private static final int WUMPA_ALERT_COLOURS = 22;
		private static final int DOOMSPIRAL_ALERT_COLOURS = 23;
		private static final int CONTEST_ALERTS = 24;
		private static final int CONTEST_ALERT_COLOURS = 25;
		private static final int TEST_ALERT_SETTINGS = 26;
		private static final int ALERT_DURATIONS = 27;
		private static final int DURATION_SAFARI_ALERTS = 28;
		private static final int DURATION_ENCOUNTER_ALERTS = 29;
		private static final int DURATION_GEMZIE = 30;
		private static final int DURATION_WUMPA = 31;
		private static final int DURATION_DOOMSPIRAL = 32;
		private static final int DURATION_CONTEST_ALERTS = 33;
		private static final int ALERT_SOUNDS = 34;
		private static final int SOUND_SAFARI_ALERTS = 35;
		private static final int SOUND_ENCOUNTER_ALERTS = 36;
		private static final int SOUND_GEMZIE = 37;
		private static final int SOUND_WUMPA = 38;
		private static final int SOUND_DOOMSPIRAL = 39;
		private static final int SOUND_CONTEST_ALERTS = 40;
		private static final int SOUND_TEST = 41;
		private static final int SOUND_HOTSPOT = 42;
		private static final int SOUND_FLOOR_DROPS = 43;
		private static final int SOUND_GEMZIE_READY = 44;
		private static final int SOUND_GEMZIE_DONE = 46;
		private static final int SOUND_WUMPA_READY = 47;
		private static final int SOUND_WUMPA_STARTED = 48;
		private static final int SOUND_WUMPA_DONE = 49;
		private static final int SOUND_DOOMSPIRAL_READY = 50;
		private static final int SOUND_DOOMSPIRAL_STARTED = 51;
		private static final int SOUND_DOOMSPIRAL_DONE = 52;
		private static final int SOUND_HIDEYHO = 53;
		private static final int SOUND_MACAW = 54;
		private static final int SOUND_BIRDS = 55;
		private static final int SOUND_CONTEST_START = 56;
		private static final int SOUND_CONTEST_FIVE = 57;
		private static final int SOUND_CONTEST_ONE = 58;
		private static final int SOUND_CONTEST_ENDED = 59;
		private static final int SOUND_TICKET = 60;
		private static final int APPEAR_HOTSPOT = 61, APPEAR_FLOOR_DROPS = 62;
		private static final int APPEAR_GEMZIE_READY = 63, APPEAR_GEMZIE_DONE = 65;
		private static final int APPEAR_WUMPA_READY = 66, APPEAR_WUMPA_STARTED = 67, APPEAR_WUMPA_DONE = 68;
		private static final int APPEAR_DOOM_READY = 69, APPEAR_DOOM_STARTED = 70, APPEAR_DOOM_DONE = 71;
		private static final int APPEAR_HIDEYHO = 72, APPEAR_MACAW = 73, APPEAR_BIRDS = 74;
		private static final int APPEAR_CONTEST_START = 75, APPEAR_CONTEST_FIVE = 76;
		private static final int APPEAR_CONTEST_ONE = 77, APPEAR_CONTEST_ENDED = 78, APPEAR_TICKET = 79;
		private static final int SOUND_SETTINGS_HOTSPOT = 80, SOUND_SETTINGS_FLOOR_DROPS = 81;
		private static final int SOUND_SETTINGS_GEMZIE_READY = 82, SOUND_SETTINGS_GEMZIE_DONE = 84;
		private static final int SOUND_SETTINGS_WUMPA_READY = 85, SOUND_SETTINGS_WUMPA_STARTED = 86, SOUND_SETTINGS_WUMPA_DONE = 87;
		private static final int SOUND_SETTINGS_DOOM_READY = 88, SOUND_SETTINGS_DOOM_STARTED = 89, SOUND_SETTINGS_DOOM_DONE = 90;
		private static final int SOUND_SETTINGS_HIDEYHO = 91, SOUND_SETTINGS_MACAW = 92, SOUND_SETTINGS_BIRDS = 93;
		private static final int SOUND_SETTINGS_CONTEST_START = 94, SOUND_SETTINGS_CONTEST_FIVE = 95;
		private static final int SOUND_SETTINGS_CONTEST_ONE = 96, SOUND_SETTINGS_CONTEST_ENDED = 97, SOUND_SETTINGS_TICKET = 98;
		private static final int BIOME_UNIQUES_DONE = 99;
		private static final int APPEAR_BIOME_UNIQUES_DONE = 100;
		private static final int SOUND_SETTINGS_BIOME_UNIQUES_DONE = 101;
		private static final int FULL_PARTY_JOINED = 102;
		private static final int APPEAR_FULL_PARTY_JOINED = 103;
		private static final int SOUND_SETTINGS_FULL_PARTY_JOINED = 104;
		private static final int ALL_BUT_MACAW_DONE = 105, APPEAR_ALL_BUT_MACAW_DONE = 106;
		private static final int SOUND_SETTINGS_ALL_BUT_MACAW_DONE = 107;
		private static final int ALL_UNIQUES_DONE = 108, APPEAR_ALL_UNIQUES_DONE = 109;
		private static final int SOUND_SETTINGS_ALL_UNIQUES_DONE = 110;
		private static final int UNIQUE_COMPLETIONS = 111;
		private static final int BANNER_APPEARANCE = 112;
		private static final int BANNER_BACKGROUND = 113;
		private static final int BANNER_BORDER = 114;
		private static final int BANNER_FONT = 115;
		private static final int BANNER_TIMER_BARS = 116;
		private static final int BIRD_ALERTS = 117;
		private static final int FEED_GONE = 118;
		private static final int APPEAR_FEED_GONE = 119;
		private static final int SOUND_SETTINGS_FEED_GONE = 120;
		private static final int BIRDFEEDER_EMPTY = 124;
		private static final int APPEAR_BIRDFEEDER_EMPTY = 125;
		private static final int SOUND_SETTINGS_BIRDFEEDER_EMPTY = 126;

		@SettingInfo(name = "Mute Other Sounds", desc = "Mutes Minecraft audio except Safari Utils alert sounds and previews")
		@SettingToggle @Expose
		public boolean muteOtherSounds = false;

		@SettingInfo(name = "Banner Appearance", desc = "Shared appearance settings for every banner alert")
		@SettingSection(id = BANNER_APPEARANCE)
		public boolean bannerAppearanceAccordion = false;

		@SettingInfo(name = "Test Alert", desc = "Preview the shared banner appearance")
		@SettingAction(buttonText = "Alert")
		@SettingGroup(id = BANNER_APPEARANCE)
		public Runnable testBannerAppearance = EncounterAlerts::fireTestAlert;

		@SettingInfo(name = "Background", desc = "Shared banner background appearance")
		@SettingSection(id = BANNER_BACKGROUND)
		@SettingGroup(id = BANNER_APPEARANCE)
		public boolean bannerBackgroundAccordion = false;

		@SettingInfo(name = "Show Background", desc = "Draws a panel behind banner text")
		@SettingToggle @SettingGroup(id = BANNER_BACKGROUND) @Expose
		public boolean bannerBackground = true;

		@SettingInfo(name = "Style", desc = "Controls the shading across the banner background")
		@SettingChoice(values = {"Solid", "Subtle Gradient", "Deep Gradient"})
		@SettingGroup(id = BANNER_BACKGROUND) @Expose
		public int bannerBackgroundStyle = 2;

		@SettingInfo(name = "Color", desc = "Base color and opacity of the banner background")
		@SettingColor
		@SettingGroup(id = BANNER_BACKGROUND) @Expose
		public String bannerBackgroundColour = "0:155:18:25:37";

		@SettingInfo(name = "Match Alert Color",
			desc = "Uses a dark tint of each alert's text and border color instead of the selected color")
		@SettingToggle @SettingGroup(id = BANNER_BACKGROUND) @Expose
		public boolean bannerBackgroundMatchAlertColour = true;

		@SettingInfo(name = "Border", desc = "Shared banner border appearance")
		@SettingSection(id = BANNER_BORDER)
		@SettingGroup(id = BANNER_APPEARANCE)
		public boolean bannerBorderAccordion = false;

		@SettingInfo(name = "Show Border", desc = "Draws an alert-colored border around banners")
		@SettingToggle @SettingGroup(id = BANNER_BORDER) @Expose
		public boolean bannerBorder = true;

		@SettingInfo(name = "Thickness", desc = "Thickness of the banner border, in pixels")
		@SettingRange(minValue = 1f, maxValue = 4f, minStep = 1f)
		@SettingGroup(id = BANNER_BORDER) @Expose
		public float bannerBorderThickness = 1f;

		@SettingInfo(name = "Font", desc = "Shared banner text appearance")
		@SettingSection(id = BANNER_FONT)
		@SettingGroup(id = BANNER_APPEARANCE)
		public boolean bannerFontAccordion = false;

		@SettingInfo(name = "Style", desc = "Text style used by every banner alert")
		@SettingChoice(values = {"Normal", "Bold", "Italic"})
		@SettingGroup(id = BANNER_FONT) @Expose
		public int bannerFont = 0;

		@SettingInfo(name = "Text Shadow", desc = "Draws a subtle shadow behind banner text")
		@SettingToggle @SettingGroup(id = BANNER_FONT) @Expose
		public boolean bannerTextShadow = false;

		@SettingInfo(name = "Timer Bars", desc = "Shared banner duration bar appearance")
		@SettingSection(id = BANNER_TIMER_BARS)
		@SettingGroup(id = BANNER_APPEARANCE)
		public boolean bannerTimerBarsAccordion = false;

		@SettingInfo(name = "Top", desc = "Direction the top bar moves as time runs out")
		@SettingChoice(values = {"Off", "Left", "Right"})
		@SettingGroup(id = BANNER_TIMER_BARS) @Expose
		public int bannerTopBar = 0;

		@SettingInfo(name = "Bottom", desc = "Direction the bottom bar moves as time runs out")
		@SettingChoice(values = {"Off", "Left", "Right"})
		@SettingGroup(id = BANNER_TIMER_BARS) @Expose
		public int bannerBottomBar = 1;

		public boolean alertSettingsAccordion = false;

		@Expose
		public float alertScale = 3.5f;

		@Expose
		public float alertHorizontalPosition = 0.49882904f;

		@Expose
		public float alertVerticalPosition = 0.33125f;

		public boolean testAlertSettingsAccordion = false;

		@Expose
		public float testAlertDuration = 3f;

		public boolean testAlertSoundAccordion = false;

		@Expose
		public boolean testAlertSound = true;

		@Expose
		public int testAlertSoundChoice = 0;

		@Expose
		public float testAlertSoundVolume = 1f;

		@Expose
		public float testAlertSoundPitch = 1.6f;

		@Expose
		public String testAlertColour = DisplayConfig.colour(0xF4, 0xB0, 0xFF);

		public boolean alertTogglesAccordion = false;

		@SettingInfo(name = "Safari Alerts", desc = "")
		@SettingSection(id = SAFARI_ALERTS)
		public boolean safariAlertsAccordion = false;

		@SettingInfo(name = "Full Party Joined", desc = "")
		@SettingSection(id = FULL_PARTY_JOINED)
		@SettingGroup(id = SAFARI_ALERTS)
		public boolean fullPartyJoinedAccordion = false;

		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert")
		@SettingGroup(id = FULL_PARTY_JOINED)
		public Runnable testFullPartyJoinedAlert = () ->
			EncounterAlerts.fireTestAlert(EncounterAlerts.Preview.FULL_PARTY);

		@SettingInfo(name = "Play Alert", desc = "Plays alert when all 4 members of your party have joined the Safari")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"}) @SettingGroup(id = FULL_PARTY_JOINED) @Expose
		public int fullPartyJoinedSoundMode = 3;

		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = APPEAR_FULL_PARTY_JOINED)
		@SettingGroup(id = FULL_PARTY_JOINED)
		public boolean fullPartyJoinedAppearanceAccordion = false;

		@SettingInfo(name = "Text", desc = "Custom banner text\n§bTags: <PLAYERS>, <MAX>")
		@SettingText @SettingGroup(id = APPEAR_FULL_PARTY_JOINED) @Expose
		public String fullPartyJoinedText = "Full Party Joined!";

		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = APPEAR_FULL_PARTY_JOINED) @Expose
		public float fullPartyJoinedScale = 4f;

		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = APPEAR_FULL_PARTY_JOINED) @Expose
		public float fullPartyJoinedVerticalPosition = 0.4f;

		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = APPEAR_FULL_PARTY_JOINED) @Expose
		public float fullPartyJoinedDuration = 2f;

		@SettingInfo(name = "Color", desc = "")
		@SettingColor @SettingGroup(id = APPEAR_FULL_PARTY_JOINED) @Expose
		public String fullPartyJoinedColour = DisplayConfig.colour(0x55, 0xFF, 0x55);

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = SOUND_SETTINGS_FULL_PARTY_JOINED)
		@SettingGroup(id = FULL_PARTY_JOINED)
		public boolean fullPartyJoinedSoundSettingsAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = SOUND_SETTINGS_FULL_PARTY_JOINED) @Expose
		public int fullPartyJoinedSoundChoice = 9;

		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_FULL_PARTY_JOINED) @Expose
		public float fullPartyJoinedSoundVolume = 20f;

		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_FULL_PARTY_JOINED) @Expose
		public float fullPartyJoinedSoundPitch = 1f;

		@SettingInfo(name = "Personal Hotspot", desc = "")
		@SettingSection(id = SOUND_HOTSPOT)
		@SettingGroup(id = SAFARI_ALERTS)
		public boolean hotspotAlertAccordion = false;

		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert")
		@SettingGroup(id = SOUND_HOTSPOT)
		public Runnable testHotspotAlert = () -> EncounterAlerts.fireTestAlert(EncounterAlerts.Preview.HOTSPOT);

		@SettingInfo(name = "Play Alert", desc = "Plays alert when your Hotspot is chosen for a run")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"})
		@SettingGroup(id = SOUND_HOTSPOT)
		@Expose
		public int hotspotSoundMode = 0;

		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = APPEAR_HOTSPOT) @SettingGroup(id = SOUND_HOTSPOT)
		public boolean hotspotAppearanceAccordion = false;

		@SettingInfo(name = "Text", desc = "Custom banner text\n§bTags: <BIOME>")
		@SettingText @SettingGroup(id = APPEAR_HOTSPOT) @Expose
		public String hotspotText = "<BIOME> Hotspot";
		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = APPEAR_HOTSPOT) @Expose
		public float hotspotScale = 4f;
		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = APPEAR_HOTSPOT) @Expose
		public float hotspotVerticalPosition = 0.4f;
		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = APPEAR_HOTSPOT)
		@Expose
		public float hotspotDuration = 3f;
		@SettingInfo(name = "Color", desc = "")
		@SettingColor @SettingGroup(id = APPEAR_HOTSPOT) @Expose
		public String hotspotAlertColour = DisplayConfig.colour(0xFF, 0x55, 0xFF);
		@SettingInfo(name = "Use Biome Color", desc = "Uses the current biome's color instead of the selected color")
		@SettingToggle @SettingGroup(id = APPEAR_HOTSPOT) @Expose
		public boolean hotspotUseBiomeColour = true;

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = SOUND_SETTINGS_HOTSPOT) @SettingGroup(id = SOUND_HOTSPOT)
		public boolean hotspotSoundSettingsAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = SOUND_SETTINGS_HOTSPOT)
		@Expose
		public int hotspotSoundChoice = 9;

		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_HOTSPOT)
		@Expose
		public float hotspotSoundVolume = 20f;

		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_HOTSPOT)
		@Expose
		public float hotspotSoundPitch = 1f;

		public boolean floorDropsDoneSoundAccordion = false;

		@SettingInfo(name = "Floor Drops Done", desc = "")
		@SettingSection(id = SOUND_FLOOR_DROPS)
		@SettingGroup(id = SAFARI_ALERTS)
		public boolean floorDropsDoneAlertAccordion = false;

		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert")
		@SettingGroup(id = SOUND_FLOOR_DROPS)
		public Runnable testFloorDropsDoneAlert = () -> EncounterAlerts.fireTestAlert(EncounterAlerts.Preview.FLOOR_DROPS);

		@SettingInfo(name = "Play Alert", desc = "Plays alert once every floor drop in a biome has been collected")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"})
		@SettingGroup(id = SOUND_FLOOR_DROPS)
		@Expose
		public int floorDropsDoneSoundMode = 3;

		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = APPEAR_FLOOR_DROPS) @SettingGroup(id = SOUND_FLOOR_DROPS)
		public boolean floorDropsDoneAppearanceAccordion = false;

		@SettingInfo(name = "Text", desc = "Custom banner text\n§bTags: <BIOME>")
		@SettingText @SettingGroup(id = APPEAR_FLOOR_DROPS) @Expose
		public String floorDropsDoneText = "<BIOME> Floor Drops Done";
		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = APPEAR_FLOOR_DROPS) @Expose
		public float floorDropsDoneScale = 4f;
		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = APPEAR_FLOOR_DROPS) @Expose
		public float floorDropsDoneVerticalPosition = 0.4f;
		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = APPEAR_FLOOR_DROPS)
		@Expose
		public float floorDropsDoneDuration = 2f;
		@SettingInfo(name = "Color", desc = "")
		@SettingColor @SettingGroup(id = APPEAR_FLOOR_DROPS) @Expose
		public String floorDropsDoneAlertColour = DisplayConfig.colour(0x55, 0xFF, 0xAA);
		@SettingInfo(name = "Use Biome Color", desc = "Uses the completed biome's color instead of the selected color")
		@SettingToggle @SettingGroup(id = APPEAR_FLOOR_DROPS) @Expose
		public boolean floorDropsDoneUseBiomeColour = true;

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = SOUND_SETTINGS_FLOOR_DROPS) @SettingGroup(id = SOUND_FLOOR_DROPS)
		public boolean floorDropsDoneSoundSettingsAccordion = false;

		public boolean durationEncounterAlertsAccordion = false;

		public boolean durationGemzieAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = SOUND_SETTINGS_FLOOR_DROPS)
		@Expose
		public int floorDropsDoneSoundChoice = 44;

		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_FLOOR_DROPS)
		@Expose
		public float floorDropsDoneSoundVolume = 4f;

		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_FLOOR_DROPS)
		@Expose
		public float floorDropsDoneSoundPitch = 1f;

		@SettingInfo(name = "Unique Completions", desc = "")
		@SettingSection(id = UNIQUE_COMPLETIONS) @SettingGroup(id = SAFARI_ALERTS)
		public boolean uniqueCompletionsAccordion = false;

		@SettingInfo(name = "Biome Uniques Done", desc = "")
		@SettingSection(id = BIOME_UNIQUES_DONE)
		@SettingGroup(id = UNIQUE_COMPLETIONS)
		public boolean biomeUniquesDoneAccordion = false;

		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert")
		@SettingGroup(id = BIOME_UNIQUES_DONE)
		public Runnable testBiomeUniquesDoneAlert = () -> EncounterAlerts.fireTestAlert(EncounterAlerts.Preview.BIOME_UNIQUES);

		@SettingInfo(name = "Play Alert", desc = "Plays alert when every unique species in a biome has been caught")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"})
		@SettingGroup(id = BIOME_UNIQUES_DONE)
		@Expose
		public int biomeUniquesDoneSoundMode = 0;

		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = APPEAR_BIOME_UNIQUES_DONE)
		@SettingGroup(id = BIOME_UNIQUES_DONE)
		public boolean biomeUniquesDoneAppearanceAccordion = false;

		@SettingInfo(name = "Text", desc = "Custom banner text\n§bTags: <BIOME>")
		@SettingText @SettingGroup(id = APPEAR_BIOME_UNIQUES_DONE) @Expose
		public String biomeUniquesDoneText = "<BIOME> Uniques Done";

		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = APPEAR_BIOME_UNIQUES_DONE) @Expose
		public float biomeUniquesDoneScale = 4f;

		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = APPEAR_BIOME_UNIQUES_DONE) @Expose
		public float biomeUniquesDoneVerticalPosition = 0.4f;

		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = APPEAR_BIOME_UNIQUES_DONE) @Expose
		public float biomeUniquesDoneDuration = 3f;

		@SettingInfo(name = "Color", desc = "")
		@SettingColor
		@SettingGroup(id = APPEAR_BIOME_UNIQUES_DONE) @Expose
		public String biomeUniquesDoneColour = DisplayConfig.colour(0x55, 0xFF, 0x55);

		@SettingInfo(name = "Use Biome Color", desc = "Uses the completed biome's color instead of the selected color")
		@SettingToggle
		@SettingGroup(id = APPEAR_BIOME_UNIQUES_DONE) @Expose
		public boolean biomeUniquesDoneUseBiomeColour = true;

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = SOUND_SETTINGS_BIOME_UNIQUES_DONE)
		@SettingGroup(id = BIOME_UNIQUES_DONE)
		public boolean biomeUniquesDoneSoundSettingsAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = SOUND_SETTINGS_BIOME_UNIQUES_DONE) @Expose
		public int biomeUniquesDoneSoundChoice = 0;

		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_BIOME_UNIQUES_DONE) @Expose
		public float biomeUniquesDoneSoundVolume = 1f;

		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_BIOME_UNIQUES_DONE) @Expose
		public float biomeUniquesDoneSoundPitch = 1.4f;

		@SettingInfo(name = "All Uniques Except Macaw", desc = "")
		@SettingSection(id = ALL_BUT_MACAW_DONE) @SettingGroup(id = UNIQUE_COMPLETIONS)
		public boolean allButMacawDoneAccordion = false;

		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert") @SettingGroup(id = ALL_BUT_MACAW_DONE)
		public Runnable testAllButMacawDoneAlert = () ->
			EncounterAlerts.fireTestAlert(EncounterAlerts.Preview.ALL_BUT_MACAW);

		@SettingInfo(name = "Play Alert", desc = "Plays alert when every unique species except Macaw has been caught")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"}) @SettingGroup(id = ALL_BUT_MACAW_DONE) @Expose
		public int allButMacawDoneSoundMode = 0;

		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = APPEAR_ALL_BUT_MACAW_DONE) @SettingGroup(id = ALL_BUT_MACAW_DONE)
		public boolean allButMacawDoneAppearanceAccordion = false;

		@SettingInfo(name = "Text", desc = "")
		@SettingText @SettingGroup(id = APPEAR_ALL_BUT_MACAW_DONE) @Expose
		public String allButMacawDoneText = "All Except Macaw Done";
		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = APPEAR_ALL_BUT_MACAW_DONE) @Expose
		public float allButMacawDoneScale = 4f;
		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = APPEAR_ALL_BUT_MACAW_DONE) @Expose
		public float allButMacawDoneVerticalPosition = 0.4f;
		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = APPEAR_ALL_BUT_MACAW_DONE) @Expose
		public float allButMacawDoneDuration = 3f;
		@SettingInfo(name = "Color", desc = "")
		@SettingColor @SettingGroup(id = APPEAR_ALL_BUT_MACAW_DONE) @Expose
		public String allButMacawDoneColour = DisplayConfig.colour(0x55, 0xFF, 0x55);

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = SOUND_SETTINGS_ALL_BUT_MACAW_DONE) @SettingGroup(id = ALL_BUT_MACAW_DONE)
		public boolean allButMacawDoneSoundSettingsAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = SOUND_SETTINGS_ALL_BUT_MACAW_DONE) @Expose
		public int allButMacawDoneSoundChoice = 0;
		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_ALL_BUT_MACAW_DONE) @Expose
		public float allButMacawDoneSoundVolume = 1f;
		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_ALL_BUT_MACAW_DONE) @Expose
		public float allButMacawDoneSoundPitch = 1.6f;

		@SettingInfo(name = "All Uniques Done", desc = "")
		@SettingSection(id = ALL_UNIQUES_DONE) @SettingGroup(id = UNIQUE_COMPLETIONS)
		public boolean allUniquesDoneAccordion = false;

		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert") @SettingGroup(id = ALL_UNIQUES_DONE)
		public Runnable testAllUniquesDoneAlert = () ->
			EncounterAlerts.fireTestAlert(EncounterAlerts.Preview.ALL_DONE);

		@SettingInfo(name = "Play Alert", desc = "Plays alert when all 37 unique species have been caught")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"}) @SettingGroup(id = ALL_UNIQUES_DONE) @Expose
		public int allUniquesDoneSoundMode = 0;

		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = APPEAR_ALL_UNIQUES_DONE) @SettingGroup(id = ALL_UNIQUES_DONE)
		public boolean allUniquesDoneAppearanceAccordion = false;
		@SettingInfo(name = "Text", desc = "")
		@SettingText @SettingGroup(id = APPEAR_ALL_UNIQUES_DONE) @Expose
		public String allUniquesDoneText = "All Uniques Done";
		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = APPEAR_ALL_UNIQUES_DONE) @Expose
		public float allUniquesDoneScale = 4f;
		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = APPEAR_ALL_UNIQUES_DONE) @Expose
		public float allUniquesDoneVerticalPosition = 0.4f;
		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = APPEAR_ALL_UNIQUES_DONE) @Expose
		public float allUniquesDoneDuration = 3f;
		@SettingInfo(name = "Color", desc = "")
		@SettingColor @SettingGroup(id = APPEAR_ALL_UNIQUES_DONE) @Expose
		public String allUniquesDoneColour = DisplayConfig.colour(0x55, 0xFF, 0x55);

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = SOUND_SETTINGS_ALL_UNIQUES_DONE) @SettingGroup(id = ALL_UNIQUES_DONE)
		public boolean allUniquesDoneSoundSettingsAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = SOUND_SETTINGS_ALL_UNIQUES_DONE) @Expose
		public int allUniquesDoneSoundChoice = 0;
		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_ALL_UNIQUES_DONE) @Expose
		public float allUniquesDoneSoundVolume = 1f;
		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_ALL_UNIQUES_DONE) @Expose
		public float allUniquesDoneSoundPitch = 2f;

		public boolean soundEncounterAlertsAccordion = false;

		public boolean soundGemzieAccordion = false;

		public boolean gemzieReadySoundAccordion = false;

		@SettingInfo(name = "Encounter Alerts", desc = "")
		@SettingSection(id = ENCOUNTER_ALERTS)
		public boolean encounterAlertsAccordion = false;

		@SettingInfo(name = "Gemzie", desc = "")
		@SettingSection(id = SOUND_GEMZIE)
		@SettingGroup(id = ENCOUNTER_ALERTS)
		public boolean gemzieAlertAccordion = false;

		@SettingInfo(name = "Ready", desc = "")
		@SettingSection(id = SOUND_GEMZIE_READY) @SettingGroup(id = SOUND_GEMZIE)
		public boolean gemzieReadyAlertAccordion = false;
		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert") @SettingGroup(id = SOUND_GEMZIE_READY)
		public Runnable testGemzieReadyAlert = () -> EncounterAlerts.fireTestAlert(EncounterAlerts.Preview.GEMZIE_READY);
		@SettingInfo(name = "Play Alert", desc = "Plays alert for this encounter stage")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"}) @SettingGroup(id = SOUND_GEMZIE_READY) @Expose
		public int gemzieReadySoundMode = 0;
		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = APPEAR_GEMZIE_READY) @SettingGroup(id = SOUND_GEMZIE_READY)
		public boolean gemzieReadyAppearanceAccordion = false;

		@SettingInfo(name = "Text", desc = "")
		@SettingText @SettingGroup(id = APPEAR_GEMZIE_READY) @Expose
		public String gemzieReadyText = "Gemzie Ready";
		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = APPEAR_GEMZIE_READY) @Expose
		public float gemzieReadyScale = 4f;
		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = APPEAR_GEMZIE_READY) @Expose
		public float gemzieReadyVerticalPosition = 0.4f;
		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = APPEAR_GEMZIE_READY)
		@Expose
		public float gemzieReadyDuration = 3f;

		@SettingInfo(name = "Ready", desc = "")
		@SettingColor
		@SettingGroup(id = APPEAR_GEMZIE_READY)
		@Expose
		public String gemzieReadyColour = DisplayConfig.colour(0xFF, 0xAA, 0x00);

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = SOUND_SETTINGS_GEMZIE_READY) @SettingGroup(id = SOUND_GEMZIE_READY)
		public boolean gemzieReadySoundSettingsAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = SOUND_SETTINGS_GEMZIE_READY)
		@Expose
		public int gemzieReadySoundChoice = 0;

		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_GEMZIE_READY)
		@Expose
		public float gemzieReadySoundVolume = 1f;

		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_GEMZIE_READY)
		@Expose
		public float gemzieReadySoundPitch = 1.4f;

		public boolean gemzieDoneSoundAccordion = false;

		@SettingInfo(name = "Done", desc = "")
		@SettingSection(id = SOUND_GEMZIE_DONE) @SettingGroup(id = SOUND_GEMZIE)
		public boolean gemzieDoneAlertAccordion = false;
		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert") @SettingGroup(id = SOUND_GEMZIE_DONE)
		public Runnable testGemzieDoneAlert = () -> EncounterAlerts.fireTestAlert(EncounterAlerts.Preview.GEMZIE_DONE);
		@SettingInfo(name = "Play Alert", desc = "Plays alert for this encounter stage")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"}) @SettingGroup(id = SOUND_GEMZIE_DONE) @Expose
		public int gemzieDoneSoundMode = 3;
		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = APPEAR_GEMZIE_DONE) @SettingGroup(id = SOUND_GEMZIE_DONE)
		public boolean gemzieDoneAppearanceAccordion = false;

		@SettingInfo(name = "Text", desc = "")
		@SettingText @SettingGroup(id = APPEAR_GEMZIE_DONE) @Expose
		public String gemzieDoneText = "Gemzie Done!";
		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = APPEAR_GEMZIE_DONE) @Expose
		public float gemzieDoneScale = 4f;
		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = APPEAR_GEMZIE_DONE) @Expose
		public float gemzieDoneVerticalPosition = 0.4f;
		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = APPEAR_GEMZIE_DONE)
		@Expose
		public float gemzieDoneDuration = 2f;

		@SettingInfo(name = "Done", desc = "")
		@SettingColor
		@SettingGroup(id = APPEAR_GEMZIE_DONE)
		@Expose
		public String gemzieDoneColour = DisplayConfig.colour(0x55, 0xFF, 0x55);

		public boolean wumpaAlertColoursAccordion = false;

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = SOUND_SETTINGS_GEMZIE_DONE) @SettingGroup(id = SOUND_GEMZIE_DONE)
		public boolean gemzieDoneSoundSettingsAccordion = false;

		public boolean durationWumpaAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = SOUND_SETTINGS_GEMZIE_DONE)
		@Expose
		public int gemzieDoneSoundChoice = 27;

		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_GEMZIE_DONE)
		@Expose
		public float gemzieDoneSoundVolume = 10f;

		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_GEMZIE_DONE)
		@Expose
		public float gemzieDoneSoundPitch = 1f;

		public boolean soundWumpaAccordion = false;

		public boolean wumpaReadySoundAccordion = false;

		@SettingInfo(name = "Wumpa", desc = "")
		@SettingSection(id = SOUND_WUMPA)
		@SettingGroup(id = ENCOUNTER_ALERTS)
		public boolean wumpaAlertAccordion = false;

		@SettingInfo(name = "Ready", desc = "")
		@SettingSection(id = SOUND_WUMPA_READY) @SettingGroup(id = SOUND_WUMPA)
		public boolean wumpaReadyAlertAccordion = false;
		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert") @SettingGroup(id = SOUND_WUMPA_READY)
		public Runnable testWumpaReadyAlert = () -> EncounterAlerts.fireTestAlert(EncounterAlerts.Preview.WUMPA_READY);
		@SettingInfo(name = "Play Alert", desc = "Plays alert for this encounter stage")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"}) @SettingGroup(id = SOUND_WUMPA_READY) @Expose
		public int wumpaReadySoundMode = 0;
		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = APPEAR_WUMPA_READY) @SettingGroup(id = SOUND_WUMPA_READY)
		public boolean wumpaReadyAppearanceAccordion = false;

		@SettingInfo(name = "Text", desc = "")
		@SettingText @SettingGroup(id = APPEAR_WUMPA_READY) @Expose
		public String wumpaReadyText = "Wumpa Ready";
		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = APPEAR_WUMPA_READY) @Expose
		public float wumpaReadyScale = 4f;
		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = APPEAR_WUMPA_READY) @Expose
		public float wumpaReadyVerticalPosition = 0.4f;
		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = APPEAR_WUMPA_READY)
		@Expose
		public float wumpaReadyDuration = 3f;

		@SettingInfo(name = "Ready", desc = "")
		@SettingColor
		@SettingGroup(id = APPEAR_WUMPA_READY)
		@Expose
		public String wumpaReadyColour = DisplayConfig.colour(0xFF, 0xAA, 0x00);

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = SOUND_SETTINGS_WUMPA_READY) @SettingGroup(id = SOUND_WUMPA_READY)
		public boolean wumpaReadySoundSettingsAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = SOUND_SETTINGS_WUMPA_READY)
		@Expose
		public int wumpaReadySoundChoice = 0;

		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_WUMPA_READY)
		@Expose
		public float wumpaReadySoundVolume = 1f;

		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_WUMPA_READY)
		@Expose
		public float wumpaReadySoundPitch = 1.4f;

		public boolean wumpaStartedSoundAccordion = false;

		@SettingInfo(name = "Started", desc = "")
		@SettingSection(id = SOUND_WUMPA_STARTED) @SettingGroup(id = SOUND_WUMPA)
		public boolean wumpaStartedAlertAccordion = false;
		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert") @SettingGroup(id = SOUND_WUMPA_STARTED)
		public Runnable testWumpaStartedAlert = () -> EncounterAlerts.fireTestAlert(EncounterAlerts.Preview.WUMPA_STARTED);
		@SettingInfo(name = "Play Alert", desc = "Plays alert for this encounter stage")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"}) @SettingGroup(id = SOUND_WUMPA_STARTED) @Expose
		public int wumpaStartedSoundMode = 0;
		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = APPEAR_WUMPA_STARTED) @SettingGroup(id = SOUND_WUMPA_STARTED)
		public boolean wumpaStartedAppearanceAccordion = false;

		@SettingInfo(name = "Text", desc = "")
		@SettingText @SettingGroup(id = APPEAR_WUMPA_STARTED) @Expose
		public String wumpaStartedText = "Wumpa Started";
		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = APPEAR_WUMPA_STARTED) @Expose
		public float wumpaStartedScale = 4f;
		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = APPEAR_WUMPA_STARTED) @Expose
		public float wumpaStartedVerticalPosition = 0.4f;
		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = APPEAR_WUMPA_STARTED)
		@Expose
		public float wumpaStartedDuration = 3f;

		@SettingInfo(name = "Started", desc = "")
		@SettingColor
		@SettingGroup(id = APPEAR_WUMPA_STARTED)
		@Expose
		public String wumpaStartedColour = DisplayConfig.colour(0xFF, 0x55, 0x55);

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = SOUND_SETTINGS_WUMPA_STARTED) @SettingGroup(id = SOUND_WUMPA_STARTED)
		public boolean wumpaStartedSoundSettingsAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = SOUND_SETTINGS_WUMPA_STARTED)
		@Expose
		public int wumpaStartedSoundChoice = 0;

		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_WUMPA_STARTED)
		@Expose
		public float wumpaStartedSoundVolume = 1f;

		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_WUMPA_STARTED)
		@Expose
		public float wumpaStartedSoundPitch = 1.8f;

		public boolean wumpaDoneSoundAccordion = false;

		@SettingInfo(name = "Done", desc = "")
		@SettingSection(id = SOUND_WUMPA_DONE) @SettingGroup(id = SOUND_WUMPA)
		public boolean wumpaDoneAlertAccordion = false;
		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert") @SettingGroup(id = SOUND_WUMPA_DONE)
		public Runnable testWumpaDoneAlert = () -> EncounterAlerts.fireTestAlert(EncounterAlerts.Preview.WUMPA_DONE);
		@SettingInfo(name = "Play Alert", desc = "Plays alert for this encounter stage")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"}) @SettingGroup(id = SOUND_WUMPA_DONE) @Expose
		public int wumpaDoneSoundMode = 3;
		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = APPEAR_WUMPA_DONE) @SettingGroup(id = SOUND_WUMPA_DONE)
		public boolean wumpaDoneAppearanceAccordion = false;

		@SettingInfo(name = "Text", desc = "")
		@SettingText @SettingGroup(id = APPEAR_WUMPA_DONE) @Expose
		public String wumpaDoneText = "Wumpa Done!";
		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = APPEAR_WUMPA_DONE) @Expose
		public float wumpaDoneScale = 4f;
		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = APPEAR_WUMPA_DONE) @Expose
		public float wumpaDoneVerticalPosition = 0.4f;
		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = APPEAR_WUMPA_DONE)
		@Expose
		public float wumpaDoneDuration = 2f;

		@SettingInfo(name = "Done", desc = "")
		@SettingColor
		@SettingGroup(id = APPEAR_WUMPA_DONE)
		@Expose
		public String wumpaDoneColour = DisplayConfig.colour(0x55, 0xFF, 0x55);

		public boolean doomspiralAlertColoursAccordion = false;

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = SOUND_SETTINGS_WUMPA_DONE) @SettingGroup(id = SOUND_WUMPA_DONE)
		public boolean wumpaDoneSoundSettingsAccordion = false;

		public boolean durationDoomspiralAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = SOUND_SETTINGS_WUMPA_DONE)
		@Expose
		public int wumpaDoneSoundChoice = 27;

		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_WUMPA_DONE)
		@Expose
		public float wumpaDoneSoundVolume = 10f;

		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_WUMPA_DONE)
		@Expose
		public float wumpaDoneSoundPitch = 1f;

		public boolean soundDoomspiralAccordion = false;

		public boolean doomspiralReadySoundAccordion = false;

		@SettingInfo(name = "Doomspiral", desc = "")
		@SettingSection(id = SOUND_DOOMSPIRAL)
		@SettingGroup(id = ENCOUNTER_ALERTS)
		public boolean doomspiralAlertAccordion = false;

		@SettingInfo(name = "Ready", desc = "")
		@SettingSection(id = SOUND_DOOMSPIRAL_READY) @SettingGroup(id = SOUND_DOOMSPIRAL)
		public boolean doomspiralReadyAlertAccordion = false;
		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert") @SettingGroup(id = SOUND_DOOMSPIRAL_READY)
		public Runnable testDoomspiralReadyAlert = () -> EncounterAlerts.fireTestAlert(EncounterAlerts.Preview.DOOM_READY);
		@SettingInfo(name = "Play Alert", desc = "Plays alert for this encounter stage")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"}) @SettingGroup(id = SOUND_DOOMSPIRAL_READY) @Expose
		public int doomspiralReadySoundMode = 0;
		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = APPEAR_DOOM_READY) @SettingGroup(id = SOUND_DOOMSPIRAL_READY)
		public boolean doomspiralReadyAppearanceAccordion = false;

		@SettingInfo(name = "Text", desc = "")
		@SettingText @SettingGroup(id = APPEAR_DOOM_READY) @Expose
		public String doomspiralReadyText = "Doomspiral Ready";
		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = APPEAR_DOOM_READY) @Expose
		public float doomspiralReadyScale = 4f;
		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = APPEAR_DOOM_READY) @Expose
		public float doomspiralReadyVerticalPosition = 0.4f;
		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = APPEAR_DOOM_READY)
		@Expose
		public float doomspiralReadyDuration = 3f;

		@SettingInfo(name = "Ready", desc = "")
		@SettingColor
		@SettingGroup(id = APPEAR_DOOM_READY)
		@Expose
		public String doomspiralReadyColour = DisplayConfig.colour(0xFF, 0xAA, 0x00);

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = SOUND_SETTINGS_DOOM_READY) @SettingGroup(id = SOUND_DOOMSPIRAL_READY)
		public boolean doomspiralReadySoundSettingsAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = SOUND_SETTINGS_DOOM_READY)
		@Expose
		public int doomspiralReadySoundChoice = 0;

		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_DOOM_READY)
		@Expose
		public float doomspiralReadySoundVolume = 1f;

		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_DOOM_READY)
		@Expose
		public float doomspiralReadySoundPitch = 1.4f;

		public boolean doomspiralStartedSoundAccordion = false;

		@SettingInfo(name = "Started", desc = "")
		@SettingSection(id = SOUND_DOOMSPIRAL_STARTED) @SettingGroup(id = SOUND_DOOMSPIRAL)
		public boolean doomspiralStartedAlertAccordion = false;
		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert") @SettingGroup(id = SOUND_DOOMSPIRAL_STARTED)
		public Runnable testDoomspiralStartedAlert = () -> EncounterAlerts.fireTestAlert(EncounterAlerts.Preview.DOOM_STARTED);
		@SettingInfo(name = "Play Alert", desc = "Plays alert for this encounter stage")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"}) @SettingGroup(id = SOUND_DOOMSPIRAL_STARTED) @Expose
		public int doomspiralStartedSoundMode = 0;
		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = APPEAR_DOOM_STARTED) @SettingGroup(id = SOUND_DOOMSPIRAL_STARTED)
		public boolean doomspiralStartedAppearanceAccordion = false;

		@SettingInfo(name = "Text", desc = "")
		@SettingText @SettingGroup(id = APPEAR_DOOM_STARTED) @Expose
		public String doomspiralStartedText = "Doomspiral Started";
		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = APPEAR_DOOM_STARTED) @Expose
		public float doomspiralStartedScale = 4f;
		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = APPEAR_DOOM_STARTED) @Expose
		public float doomspiralStartedVerticalPosition = 0.4f;
		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = APPEAR_DOOM_STARTED)
		@Expose
		public float doomspiralStartedDuration = 3f;

		@SettingInfo(name = "Started", desc = "")
		@SettingColor
		@SettingGroup(id = APPEAR_DOOM_STARTED)
		@Expose
		public String doomspiralStartedColour = DisplayConfig.colour(0xFF, 0x55, 0x55);

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = SOUND_SETTINGS_DOOM_STARTED) @SettingGroup(id = SOUND_DOOMSPIRAL_STARTED)
		public boolean doomspiralStartedSoundSettingsAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = SOUND_SETTINGS_DOOM_STARTED)
		@Expose
		public int doomspiralStartedSoundChoice = 0;

		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_DOOM_STARTED)
		@Expose
		public float doomspiralStartedSoundVolume = 1f;

		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_DOOM_STARTED)
		@Expose
		public float doomspiralStartedSoundPitch = 1.8f;

		public boolean doomspiralDoneSoundAccordion = false;

		@SettingInfo(name = "Done", desc = "")
		@SettingSection(id = SOUND_DOOMSPIRAL_DONE) @SettingGroup(id = SOUND_DOOMSPIRAL)
		public boolean doomspiralDoneAlertAccordion = false;
		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert") @SettingGroup(id = SOUND_DOOMSPIRAL_DONE)
		public Runnable testDoomspiralDoneAlert = () -> EncounterAlerts.fireTestAlert(EncounterAlerts.Preview.DOOM_DONE);
		@SettingInfo(name = "Play Alert", desc = "Plays alert for this encounter stage")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"}) @SettingGroup(id = SOUND_DOOMSPIRAL_DONE) @Expose
		public int doomspiralDoneSoundMode = 3;
		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = APPEAR_DOOM_DONE) @SettingGroup(id = SOUND_DOOMSPIRAL_DONE)
		public boolean doomspiralDoneAppearanceAccordion = false;

		@SettingInfo(name = "Text", desc = "")
		@SettingText @SettingGroup(id = APPEAR_DOOM_DONE) @Expose
		public String doomspiralDoneText = "Doomspiral Done!";
		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = APPEAR_DOOM_DONE) @Expose
		public float doomspiralDoneScale = 4f;
		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = APPEAR_DOOM_DONE) @Expose
		public float doomspiralDoneVerticalPosition = 0.4f;
		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = APPEAR_DOOM_DONE)
		@Expose
		public float doomspiralDoneDuration = 2f;

		@SettingInfo(name = "Done", desc = "")
		@SettingColor
		@SettingGroup(id = APPEAR_DOOM_DONE)
		@Expose
		public String doomspiralDoneColour = DisplayConfig.colour(0x55, 0xFF, 0x55);

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = SOUND_SETTINGS_DOOM_DONE) @SettingGroup(id = SOUND_DOOMSPIRAL_DONE)
		public boolean doomspiralDoneSoundSettingsAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = SOUND_SETTINGS_DOOM_DONE)
		@Expose
		public int doomspiralDoneSoundChoice = 27;

		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_DOOM_DONE)
		@Expose
		public float doomspiralDoneSoundVolume = 10f;

		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_DOOM_DONE)
		@Expose
		public float doomspiralDoneSoundPitch = 1f;

		public boolean hideyhoSoundAccordion = false;

		@SettingInfo(
			name = "Only In That Biome",
			desc = "Only plays encounter and bird alerts in their respective biome")
		@SettingToggle
		@SettingGroup(id = ENCOUNTER_ALERTS)
		@Expose
		public boolean encountersInBiomeOnly = true;

		@SettingInfo(name = "Hideyho", desc = "")
		@SettingSection(id = SOUND_HIDEYHO)
		@SettingGroup(id = ENCOUNTER_ALERTS)
		public boolean hideyhoAlertAccordion = false;
		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert") @SettingGroup(id = SOUND_HIDEYHO)
		public Runnable testHideyhoAlert = () -> EncounterAlerts.fireTestAlert(EncounterAlerts.Preview.HIDEYHO);
		@SettingInfo(name = "Play Alert", desc = "Plays alert when start/end Hideyho location detected")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"})
		@SettingGroup(id = SOUND_HIDEYHO)
		@Expose
		public int hideyhoSoundMode = 3;

		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = APPEAR_HIDEYHO) @SettingGroup(id = SOUND_HIDEYHO)
		public boolean hideyhoAppearanceAccordion = false;

		@SettingInfo(name = "Text", desc = "")
		@SettingText @SettingGroup(id = APPEAR_HIDEYHO) @Expose
		public String hideyhoText = "Hideyho Found";
		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = APPEAR_HIDEYHO) @Expose
		public float hideyhoScale = 4f;
		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = APPEAR_HIDEYHO) @Expose
		public float hideyhoVerticalPosition = 0.4f;
		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = APPEAR_HIDEYHO)
		@Expose
		public float hideyhoDuration = 2.5f;

		@SettingInfo(name = "Hideyho", desc = "")
		@SettingColor
		@SettingGroup(id = APPEAR_HIDEYHO)
		@Expose
		public String hideyhoAlertColour = DisplayConfig.colour(0x5F, 0x00, 0xFF);

		public boolean contestAlertColoursAccordion = false;

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = SOUND_SETTINGS_HIDEYHO) @SettingGroup(id = SOUND_HIDEYHO)
		public boolean hideyhoSoundSettingsAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = SOUND_SETTINGS_HIDEYHO)
		@Expose
		public int hideyhoSoundChoice = 17;

		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_HIDEYHO)
		@Expose
		public float hideyhoSoundVolume = 15f;

		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_HIDEYHO)
		@Expose
		public float hideyhoSoundPitch = 0.9f;

		@SettingInfo(name = "Birds", desc = "")
		@SettingSection(id = BIRD_ALERTS) @SettingGroup(id = ENCOUNTER_ALERTS)
		public boolean birdAlertsAccordion = false;

		public boolean macawSoundAccordion = false;

		@SettingInfo(name = "Macaw", desc = "")
		@SettingSection(id = SOUND_MACAW) @SettingGroup(id = BIRD_ALERTS)
		public boolean macawAlertAccordion = false;
		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert") @SettingGroup(id = SOUND_MACAW)
		public Runnable testMacawAlert = () -> EncounterAlerts.fireTestAlert(EncounterAlerts.Preview.MACAW);
		@SettingInfo(name = "Play Alert", desc = "Plays alert when a Macaw appears")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"})
		@SettingGroup(id = SOUND_MACAW)
		@Expose
		public int macawSoundMode = 3;

		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = APPEAR_MACAW) @SettingGroup(id = SOUND_MACAW)
		public boolean macawAppearanceAccordion = false;

		@SettingInfo(name = "Text", desc = "")
		@SettingText @SettingGroup(id = APPEAR_MACAW) @Expose
		public String macawText = "Macaw Spawned";
		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = APPEAR_MACAW) @Expose
		public float macawScale = 4f;
		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = APPEAR_MACAW) @Expose
		public float macawVerticalPosition = 0.4f;
		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = APPEAR_MACAW)
		@Expose
		public float macawDuration = 2f;
		@SettingInfo(name = "Color", desc = "")
		@SettingColor @SettingGroup(id = APPEAR_MACAW) @Expose
		public String macawAlertColour = DisplayConfig.colour(0xFF, 0xAA, 0x00);
		@SettingInfo(name = "Use Rarity Color", desc = "Uses the critter's rarity color instead of the selected color")
		@SettingToggle @SettingGroup(id = APPEAR_MACAW) @Expose
		public boolean macawUseRarityColour = true;

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = SOUND_SETTINGS_MACAW) @SettingGroup(id = SOUND_MACAW)
		public boolean macawSoundSettingsAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = SOUND_SETTINGS_MACAW)
		@Expose
		public int macawSoundChoice = 29;

		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_MACAW)
		@Expose
		public float macawSoundVolume = 10f;

		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_MACAW)
		@Expose
		public float macawSoundPitch = 0.5f;

		public boolean birdfeederSoundAccordion = false;

		@SettingInfo(name = "All Birds", desc = "")
		@SettingSection(id = SOUND_BIRDS) @SettingGroup(id = BIRD_ALERTS)
		public boolean birdfeederAlertAccordion = false;
		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert") @SettingGroup(id = SOUND_BIRDS)
		public Runnable testBirdAlert = () -> EncounterAlerts.fireTestAlert(EncounterAlerts.Preview.BIRDS);
		@SettingInfo(name = "Play Alert", desc = "Plays alert when a Bluebird or Parakeet appears")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"})
		@SettingGroup(id = SOUND_BIRDS)
		@Expose
		public int birdfeederSoundMode = 0;

		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = APPEAR_BIRDS) @SettingGroup(id = SOUND_BIRDS)
		public boolean birdfeederAppearanceAccordion = false;

		@SettingInfo(name = "Text", desc = "Custom banner text\n§bTags: <CRITTER>")
		@SettingText @SettingGroup(id = APPEAR_BIRDS) @Expose
		public String birdfeederText = "<CRITTER> Spawned";
		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = APPEAR_BIRDS) @Expose
		public float birdfeederScale = 4f;
		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = APPEAR_BIRDS) @Expose
		public float birdfeederVerticalPosition = 0.4f;
		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = APPEAR_BIRDS)
		@Expose
		public float birdfeederDuration = 3f;
		@SettingInfo(name = "Bluebird Color", desc = "")
		@SettingColor @SettingGroup(id = APPEAR_BIRDS) @Expose
		public String bluebirdAlertColour = DisplayConfig.colour(0x55, 0xFF, 0x55);
		@SettingInfo(name = "Parakeet Color", desc = "")
		@SettingColor @SettingGroup(id = APPEAR_BIRDS) @Expose
		public String parakeetAlertColour = DisplayConfig.colour(0x55, 0x55, 0xFF);
		@SettingInfo(name = "Use Rarity Color", desc = "Uses each bird's rarity color instead of the selected colors")
		@SettingToggle @SettingGroup(id = APPEAR_BIRDS) @Expose
		public boolean birdfeederUseRarityColour = true;

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = SOUND_SETTINGS_BIRDS) @SettingGroup(id = SOUND_BIRDS)
		public boolean birdfeederSoundSettingsAccordion = false;

		public boolean durationContestAlertsAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = SOUND_SETTINGS_BIRDS)
		@Expose
		public int birdfeederSoundChoice = 0;

		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_BIRDS)
		@Expose
		public float birdfeederSoundVolume = 1f;

		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_BIRDS)
		@Expose
		public float birdfeederSoundPitch = 1.4f;

		@SettingInfo(name = "Birdfeeder Empty", desc = "")
		@SettingSection(id = BIRDFEEDER_EMPTY) @SettingGroup(id = BIRD_ALERTS)
		public boolean birdfeederEmptyAlertAccordion = false;
		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert") @SettingGroup(id = BIRDFEEDER_EMPTY)
		public Runnable testBirdfeederEmptyAlert = () -> EncounterAlerts.fireTestAlert(EncounterAlerts.Preview.BIRDFEEDER_EMPTY);
		@SettingInfo(name = "Play Alert", desc = "Plays alert when feed runs out while the Birdfeeder menu is open")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"}) @SettingGroup(id = BIRDFEEDER_EMPTY) @Expose
		public int birdfeederEmptySoundMode = 3;

		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = APPEAR_BIRDFEEDER_EMPTY) @SettingGroup(id = BIRDFEEDER_EMPTY)
		public boolean birdfeederEmptyAppearanceAccordion = false;
		@SettingInfo(name = "Text", desc = "")
		@SettingText @SettingGroup(id = APPEAR_BIRDFEEDER_EMPTY) @Expose
		public String birdfeederEmptyText = "Birdfeeder Empty";
		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = APPEAR_BIRDFEEDER_EMPTY) @Expose
		public float birdfeederEmptyScale = 4f;
		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = APPEAR_BIRDFEEDER_EMPTY) @Expose
		public float birdfeederEmptyVerticalPosition = 0.4f;
		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = APPEAR_BIRDFEEDER_EMPTY) @Expose
		public float birdfeederEmptyDuration = 2f;
		@SettingInfo(name = "Color", desc = "")
		@SettingColor @SettingGroup(id = APPEAR_BIRDFEEDER_EMPTY) @Expose
		public String birdfeederEmptyColour = DisplayConfig.colour(0xFF, 0x55, 0x55);

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = SOUND_SETTINGS_BIRDFEEDER_EMPTY) @SettingGroup(id = BIRDFEEDER_EMPTY)
		public boolean birdfeederEmptySoundSettingsAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = SOUND_SETTINGS_BIRDFEEDER_EMPTY) @Expose
		public int birdfeederEmptySoundChoice = 9;
		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_BIRDFEEDER_EMPTY) @Expose
		public float birdfeederEmptySoundVolume = 20f;
		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_BIRDFEEDER_EMPTY) @Expose
		public float birdfeederEmptySoundPitch = 0.7f;

		@SettingInfo(name = "All Feed Used", desc = "")
		@SettingSection(id = FEED_GONE) @SettingGroup(id = BIRD_ALERTS)
		public boolean feedGoneAlertAccordion = false;
		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert") @SettingGroup(id = FEED_GONE)
		public Runnable testFeedGoneAlert = () -> EncounterAlerts.fireTestAlert(EncounterAlerts.Preview.FEED_GONE);
		@SettingInfo(name = "Play Alert", desc = "Plays alert when all collected Bird Feed is placed in the Birdfeeder")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"}) @SettingGroup(id = FEED_GONE) @Expose
		public int feedGoneSoundMode = 3;

		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = APPEAR_FEED_GONE) @SettingGroup(id = FEED_GONE)
		public boolean feedGoneAppearanceAccordion = false;
		@SettingInfo(name = "Text", desc = "")
		@SettingText @SettingGroup(id = APPEAR_FEED_GONE) @Expose
		public String feedGoneText = "All Feed Used";
		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = APPEAR_FEED_GONE) @Expose
		public float feedGoneScale = 4f;
		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = APPEAR_FEED_GONE) @Expose
		public float feedGoneVerticalPosition = 0.4f;
		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = APPEAR_FEED_GONE) @Expose
		public float feedGoneDuration = 2f;
		@SettingInfo(name = "Color", desc = "")
		@SettingColor @SettingGroup(id = APPEAR_FEED_GONE) @Expose
		public String feedGoneColour = DisplayConfig.colour(0x61, 0xFF, 0x55);

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = SOUND_SETTINGS_FEED_GONE) @SettingGroup(id = FEED_GONE)
		public boolean feedGoneSoundSettingsAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = SOUND_SETTINGS_FEED_GONE) @Expose
		public int feedGoneSoundChoice = 9;
		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_FEED_GONE) @Expose
		public float feedGoneSoundVolume = 20f;
		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_FEED_GONE) @Expose
		public float feedGoneSoundPitch = 0.5f;

		public boolean soundContestAlertsAccordion = false;

		public boolean contestStartSoundAccordion = false;

		@SettingInfo(name = "Contest Alerts", desc = "")
		@SettingSection(id = CONTEST_ALERTS)
		public boolean contestAlertsAccordion = false;

		@SettingInfo(name = "Contest Start", desc = "")
		@SettingSection(id = SOUND_CONTEST_START)
		@SettingGroup(id = CONTEST_ALERTS)
		public boolean contestStartAlertAccordion = false;
		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert") @SettingGroup(id = SOUND_CONTEST_START)
		public Runnable testContestStartAlert = () -> EncounterAlerts.fireTestAlert(EncounterAlerts.Preview.START);
		@SettingInfo(name = "Play Alert", desc = "Plays alert when a new Miria's Contest starts")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"})
		@SettingGroup(id = SOUND_CONTEST_START)
		@Expose
		public int contestStartSoundMode = 3;

		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = APPEAR_CONTEST_START) @SettingGroup(id = SOUND_CONTEST_START)
		public boolean contestStartAppearanceAccordion = false;

		@SettingInfo(name = "Text", desc = "")
		@SettingText @SettingGroup(id = APPEAR_CONTEST_START) @Expose
		public String contestStartText = "Contest Started";
		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = APPEAR_CONTEST_START) @Expose
		public float contestStartScale = 4f;
		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = APPEAR_CONTEST_START) @Expose
		public float contestStartVerticalPosition = 0.4f;
		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = APPEAR_CONTEST_START)
		@Expose
		public float contestStartDuration = 2.5f;

		@SettingInfo(name = "Contest Start", desc = "")
		@SettingColor
		@SettingGroup(id = APPEAR_CONTEST_START)
		@Expose
		public String contestStartColour = DisplayConfig.colour(0x55, 0xFF, 0x55);

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = SOUND_SETTINGS_CONTEST_START) @SettingGroup(id = SOUND_CONTEST_START)
		public boolean contestStartSoundSettingsAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = SOUND_SETTINGS_CONTEST_START)
		@Expose
		public int contestStartSoundChoice = 30;

		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_CONTEST_START)
		@Expose
		public float contestStartSoundVolume = 20f;

		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_CONTEST_START)
		@Expose
		public float contestStartSoundPitch = 1f;

		public boolean contestFiveMinuteSoundAccordion = false;

		@SettingInfo(name = "5 Minute Warning", desc = "")
		@SettingSection(id = SOUND_CONTEST_FIVE)
		@SettingGroup(id = CONTEST_ALERTS)
		public boolean contestFiveMinuteAlertAccordion = false;
		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert") @SettingGroup(id = SOUND_CONTEST_FIVE)
		public Runnable testContestFiveMinuteAlert = () -> EncounterAlerts.fireTestAlert(EncounterAlerts.Preview.FIVE_MINUTES);
		@SettingInfo(name = "Play Alert", desc = "Plays alert when a Contest has 5 minutes left")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"})
		@SettingGroup(id = SOUND_CONTEST_FIVE)
		@Expose
		public int contestFiveMinuteSoundMode = 3;

		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = APPEAR_CONTEST_FIVE) @SettingGroup(id = SOUND_CONTEST_FIVE)
		public boolean contestFiveMinuteAppearanceAccordion = false;

		@SettingInfo(name = "Text", desc = "")
		@SettingText @SettingGroup(id = APPEAR_CONTEST_FIVE) @Expose
		public String contestFiveMinuteText = "5 Minutes Left";
		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = APPEAR_CONTEST_FIVE) @Expose
		public float contestFiveMinuteScale = 4f;
		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = APPEAR_CONTEST_FIVE) @Expose
		public float contestFiveMinuteVerticalPosition = 0.4f;
		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = APPEAR_CONTEST_FIVE)
		@Expose
		public float contestFiveMinuteDuration = 2.5f;

		@SettingInfo(name = "5 Minute Warning", desc = "")
		@SettingColor
		@SettingGroup(id = APPEAR_CONTEST_FIVE)
		@Expose
		public String contestFiveMinuteColour = DisplayConfig.colour(0xFF, 0xFF, 0x55);

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = SOUND_SETTINGS_CONTEST_FIVE) @SettingGroup(id = SOUND_CONTEST_FIVE)
		public boolean contestFiveMinuteSoundSettingsAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = SOUND_SETTINGS_CONTEST_FIVE)
		@Expose
		public int contestFiveMinuteSoundChoice = 30;

		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_CONTEST_FIVE)
		@Expose
		public float contestFiveMinuteSoundVolume = 20f;

		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_CONTEST_FIVE)
		@Expose
		public float contestFiveMinuteSoundPitch = 1.4f;

		public boolean contestOneMinuteSoundAccordion = false;

		@SettingInfo(name = "1 Minute Warning", desc = "")
		@SettingSection(id = SOUND_CONTEST_ONE)
		@SettingGroup(id = CONTEST_ALERTS)
		public boolean contestOneMinuteAlertAccordion = false;
		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert") @SettingGroup(id = SOUND_CONTEST_ONE)
		public Runnable testContestOneMinuteAlert = () -> EncounterAlerts.fireTestAlert(EncounterAlerts.Preview.ONE_MINUTE);
		@SettingInfo(name = "Play Alert", desc = "Plays alert when a Contest has 1 minute left")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"})
		@SettingGroup(id = SOUND_CONTEST_ONE)
		@Expose
		public int contestOneMinuteSoundMode = 3;

		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = APPEAR_CONTEST_ONE) @SettingGroup(id = SOUND_CONTEST_ONE)
		public boolean contestOneMinuteAppearanceAccordion = false;

		@SettingInfo(name = "Text", desc = "")
		@SettingText @SettingGroup(id = APPEAR_CONTEST_ONE) @Expose
		public String contestOneMinuteText = "1 Minute Left";
		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = APPEAR_CONTEST_ONE) @Expose
		public float contestOneMinuteScale = 4f;
		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = APPEAR_CONTEST_ONE) @Expose
		public float contestOneMinuteVerticalPosition = 0.4f;
		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = APPEAR_CONTEST_ONE)
		@Expose
		public float contestOneMinuteDuration = 2.5f;

		@SettingInfo(name = "1 Minute Warning", desc = "")
		@SettingColor
		@SettingGroup(id = APPEAR_CONTEST_ONE)
		@Expose
		public String contestOneMinuteColour = DisplayConfig.colour(0xFF, 0xAA, 0x00);

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = SOUND_SETTINGS_CONTEST_ONE) @SettingGroup(id = SOUND_CONTEST_ONE)
		public boolean contestOneMinuteSoundSettingsAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = SOUND_SETTINGS_CONTEST_ONE)
		@Expose
		public int contestOneMinuteSoundChoice = 30;

		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_CONTEST_ONE)
		@Expose
		public float contestOneMinuteSoundVolume = 20f;

		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_CONTEST_ONE)
		@Expose
		public float contestOneMinuteSoundPitch = 2f;

		public boolean contestEndedSoundAccordion = false;

		@SettingInfo(name = "Contest Ended", desc = "")
		@SettingSection(id = SOUND_CONTEST_ENDED)
		@SettingGroup(id = CONTEST_ALERTS)
		public boolean contestEndedAlertAccordion = false;
		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert") @SettingGroup(id = SOUND_CONTEST_ENDED)
		public Runnable testContestEndedAlert = () -> EncounterAlerts.fireTestAlert(EncounterAlerts.Preview.ENDED);
		@SettingInfo(name = "Play Alert", desc = "Plays alert when a Miria's Contest ends")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"})
		@SettingGroup(id = SOUND_CONTEST_ENDED)
		@Expose
		public int contestEndedSoundMode = 0;

		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = APPEAR_CONTEST_ENDED) @SettingGroup(id = SOUND_CONTEST_ENDED)
		public boolean contestEndedAppearanceAccordion = false;

		@SettingInfo(name = "Text", desc = "")
		@SettingText @SettingGroup(id = APPEAR_CONTEST_ENDED) @Expose
		public String contestEndedText = "Contest Ended";
		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = APPEAR_CONTEST_ENDED) @Expose
		public float contestEndedScale = 4f;
		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = APPEAR_CONTEST_ENDED) @Expose
		public float contestEndedVerticalPosition = 0.4f;
		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = APPEAR_CONTEST_ENDED)
		@Expose
		public float contestEndedDuration = 2.5f;

		@SettingInfo(name = "Contest Ended", desc = "")
		@SettingColor
		@SettingGroup(id = APPEAR_CONTEST_ENDED)
		@Expose
		public String contestEndedColour = DisplayConfig.colour(0xFF, 0x55, 0x55);

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = SOUND_SETTINGS_CONTEST_ENDED) @SettingGroup(id = SOUND_CONTEST_ENDED)
		public boolean contestEndedSoundSettingsAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = SOUND_SETTINGS_CONTEST_ENDED)
		@Expose
		public int contestEndedSoundChoice = 30;

		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_CONTEST_ENDED)
		@Expose
		public float contestEndedSoundVolume = 20f;

		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_CONTEST_ENDED)
		@Expose
		public float contestEndedSoundPitch = 0.8f;

		public boolean contestTicketEarnedSoundAccordion = false;

		@SettingInfo(name = "Ticket Earned", desc = "")
		@SettingSection(id = SOUND_TICKET)
		@SettingGroup(id = CONTEST_ALERTS)
		public boolean contestTicketEarnedAlertAccordion = false;
		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert") @SettingGroup(id = SOUND_TICKET)
		public Runnable testContestTicketEarnedAlert = () -> EncounterAlerts.fireTestAlert(EncounterAlerts.Preview.TICKET);
		@SettingInfo(name = "Play Alert", desc = "Plays alert when a Safari Ticket has been earned in the ongoing Miria's Contest")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"})
		@SettingGroup(id = SOUND_TICKET)
		@Expose
		public int contestTicketEarnedSoundMode = 3;

		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = APPEAR_TICKET) @SettingGroup(id = SOUND_TICKET)
		public boolean contestTicketEarnedAppearanceAccordion = false;

		@SettingInfo(name = "Text", desc = "")
		@SettingText @SettingGroup(id = APPEAR_TICKET) @Expose
		public String contestTicketEarnedText = "Ticket Earned";
		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = APPEAR_TICKET) @Expose
		public float contestTicketEarnedScale = 4f;
		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = APPEAR_TICKET) @Expose
		public float contestTicketEarnedVerticalPosition = 0.4f;
		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = APPEAR_TICKET)
		@Expose
		public float contestTicketEarnedDuration = 2.5f;

		@SettingInfo(name = "Ticket Earned", desc = "")
		@SettingColor
		@SettingGroup(id = APPEAR_TICKET)
		@Expose
		public String contestTicketEarnedColour = DisplayConfig.colour(0x55, 0xFF, 0x55);

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = SOUND_SETTINGS_TICKET) @SettingGroup(id = SOUND_TICKET)
		public boolean contestTicketEarnedSoundSettingsAccordion = false;

		public boolean alertSoundsAccordion = false;

		public boolean soundSafariAlertsAccordion = false;

		public boolean hotspotSoundAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = SOUND_SETTINGS_TICKET)
		@Expose
		public int contestTicketEarnedSoundChoice = 49;

		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_TICKET)
		@Expose
		public float contestTicketEarnedSoundVolume = 10f;

		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = SOUND_SETTINGS_TICKET)
		@Expose
		public float contestTicketEarnedSoundPitch = 1f;

		public boolean alertColoursAccordion = false;

		public boolean encounterAlertColoursAccordion = false;

		public boolean gemzieAlertColoursAccordion = false;

		@SettingInfo(name = "No Warnings",
			desc = "Skips 1 and 5 minute alerts after earning a ticket")
		@SettingToggle
		@SettingGroup(id = CONTEST_ALERTS)
		@Expose
		public boolean contestNoWarningsAfterTicket = true;

		public boolean alertDurationsAccordion = false;

		public boolean durationSafariAlertsAccordion = false;

	}

	public static class PartyConfig {

		// Lines are spaced 1.2s apart whatever the selected channel; Hypixel drops faster bursts.
		private static final int SAFARI_CHAT_ALERTS = 2;
		private static final int ENCOUNTER_CHAT_ALERTS = 3;
		private static final int CONTEST_CHAT_ALERTS = 4;
		private static final int CHAT_FULL_PARTY = 5, CHAT_HOTSPOT = 6, CHAT_BIOME_DONE = 7;
		private static final int CHAT_GEMZIE = 8, CHAT_WUMPA = 9, CHAT_DOOMSPIRAL = 10, CHAT_MACAW = 11;
		private static final int CHAT_CONTEST_START = 12, CHAT_CONTEST_FIVE = 13;
		private static final int CHAT_CONTEST_ENDED = 14, CHAT_TICKET = 15;
		private static final int CHAT_ALL_BUT_MACAW = 16, CHAT_ALL_DONE = 17;
		private static final int CHAT_UNIQUE_COMPLETIONS = 18;
		private static final int CHAT_BIRDS = 19;
		private static final int CHAT_ALL_BIRDS = 20;
		private static final int CHAT_TOTAL_FEED = 21;
		private static final int CHAT_FEED_GONE = 22;

		@SettingInfo(name = "Safari Chat Alerts", desc = "")
		@SettingSection(id = SAFARI_CHAT_ALERTS)
		public boolean safariChatAlertsAccordion = false;

		@SettingInfo(name = "Full Party Joined", desc = "")
		@SettingSection(id = CHAT_FULL_PARTY) @SettingGroup(id = SAFARI_CHAT_ALERTS)
		public boolean fullPartyJoinedChatAccordion = false;
		@SettingInfo(name = "Send To", desc = "Sends when all 4 members of your party have joined the Safari")
		@SettingChoice(values = {"Off", "Party Chat", "All Chat"})
		@SettingGroup(id = CHAT_FULL_PARTY)
		@Expose
		public int fullPartyJoinedBroadcast = 1;
		@SettingInfo(name = "Text", desc = "Custom chat text\n§bTags: <PLAYERS>, <MAX>") @SettingText @SettingGroup(id = CHAT_FULL_PARTY) @Expose
		public String fullPartyJoinedChatText = "Full Party Joined!";

		@SettingInfo(name = "Your Hotspot", desc = "")
		@SettingSection(id = CHAT_HOTSPOT) @SettingGroup(id = SAFARI_CHAT_ALERTS)
		public boolean hotspotChatAccordion = false;
		@SettingInfo(name = "Send To", desc = "Sends your Hotspot when a run starts")
		@SettingChoice(values = {"Off", "Party Chat", "All Chat"})
		@SettingGroup(id = CHAT_HOTSPOT)
		@Expose
		public int hotspotBroadcast = 0;
		@SettingInfo(name = "Text", desc = "Custom chat text\n§bTags: <BIOME>") @SettingText @SettingGroup(id = CHAT_HOTSPOT) @Expose
		public String hotspotChatText = "<BIOME> Hotspot!";

		@SettingInfo(name = "Unique Completions", desc = "")
		@SettingSection(id = CHAT_UNIQUE_COMPLETIONS) @SettingGroup(id = SAFARI_CHAT_ALERTS)
		public boolean uniqueCompletionsChatAccordion = false;

		@SettingInfo(name = "Biome Uniques Done", desc = "")
		@SettingSection(id = CHAT_BIOME_DONE) @SettingGroup(id = CHAT_UNIQUE_COMPLETIONS)
		public boolean biomeDoneChatAccordion = false;
		@SettingInfo(name = "Send To", desc = "Sends when every unique species in a biome has been caught")
		@SettingChoice(values = {"Off", "Party Chat", "All Chat"})
		@SettingGroup(id = CHAT_BIOME_DONE)
		@Expose
		public int biomeDoneBroadcast = 0;
		@SettingInfo(name = "Text", desc = "Custom chat text\n§bTags: <BIOME>") @SettingText @SettingGroup(id = CHAT_BIOME_DONE) @Expose
		public String biomeDoneChatText = "<BIOME> Uniques Done!";

		@SettingInfo(name = "All Uniques Except Macaw", desc = "")
		@SettingSection(id = CHAT_ALL_BUT_MACAW) @SettingGroup(id = CHAT_UNIQUE_COMPLETIONS)
		public boolean allButMacawChatAccordion = false;
		@SettingInfo(name = "Send To", desc = "Sends when every unique species except Macaw has been caught")
		@SettingChoice(values = {"Off", "Party Chat", "All Chat"})
		@SettingGroup(id = CHAT_ALL_BUT_MACAW) @Expose
		public int allButMacawBroadcast = 0;
		@SettingInfo(name = "Text", desc = "") @SettingText @SettingGroup(id = CHAT_ALL_BUT_MACAW) @Expose
		public String allButMacawChatText = "All Except Macaw Done!";

		@SettingInfo(name = "All Uniques Done", desc = "")
		@SettingSection(id = CHAT_ALL_DONE) @SettingGroup(id = CHAT_UNIQUE_COMPLETIONS)
		public boolean allDoneChatAccordion = false;
		@SettingInfo(name = "Send To", desc = "Sends when all 37 unique species have been caught")
		@SettingChoice(values = {"Off", "Party Chat", "All Chat"})
		@SettingGroup(id = CHAT_ALL_DONE) @Expose
		public int allDoneBroadcast = 0;
		@SettingInfo(name = "Text", desc = "") @SettingText @SettingGroup(id = CHAT_ALL_DONE) @Expose
		public String allDoneChatText = "All Uniques Done!";

		@SettingInfo(name = "Encounter Chat Alerts", desc = "")
		@SettingSection(id = ENCOUNTER_CHAT_ALERTS)
		public boolean encounterChatAlertsAccordion = false;

		@SettingInfo(name = "Gemzie", desc = "") @SettingSection(id = CHAT_GEMZIE)
		@SettingGroup(id = ENCOUNTER_CHAT_ALERTS) public boolean gemzieChatAccordion = false;
		@SettingInfo(name = "Send To", desc = "Sends Gemzie encounter stages")
		@SettingChoice(values = {"Off", "Party Chat", "All Chat"})
		@SettingGroup(id = CHAT_GEMZIE)
		@Expose
		public int gemzieBroadcast = 0;
		@SettingInfo(name = "Ready Text", desc = "") @SettingText @SettingGroup(id = CHAT_GEMZIE) @Expose public String gemzieReadyChatText = "";
		@SettingInfo(name = "Done Text", desc = "") @SettingText @SettingGroup(id = CHAT_GEMZIE) @Expose public String gemzieDoneChatText = "Gemzie Done!";

		@SettingInfo(name = "Wumpa", desc = "") @SettingSection(id = CHAT_WUMPA)
		@SettingGroup(id = ENCOUNTER_CHAT_ALERTS) public boolean wumpaChatAccordion = false;
		@SettingInfo(name = "Send To", desc = "Sends Wumpa encounter stages")
		@SettingChoice(values = {"Off", "Party Chat", "All Chat"})
		@SettingGroup(id = CHAT_WUMPA)
		@Expose
		public int wumpaBroadcast = 0;
		@SettingInfo(name = "Ready Text", desc = "") @SettingText @SettingGroup(id = CHAT_WUMPA) @Expose public String wumpaReadyChatText = "";
		@SettingInfo(name = "Started Text", desc = "") @SettingText @SettingGroup(id = CHAT_WUMPA) @Expose public String wumpaStartedChatText = "";
		@SettingInfo(name = "Done Text", desc = "") @SettingText @SettingGroup(id = CHAT_WUMPA) @Expose public String wumpaDoneChatText = "Wumpa Done!";

		@SettingInfo(name = "Doomspiral", desc = "") @SettingSection(id = CHAT_DOOMSPIRAL)
		@SettingGroup(id = ENCOUNTER_CHAT_ALERTS) public boolean doomspiralChatAccordion = false;
		@SettingInfo(name = "Send To", desc = "Sends Doomspiral encounter stages")
		@SettingChoice(values = {"Off", "Party Chat", "All Chat"})
		@SettingGroup(id = CHAT_DOOMSPIRAL)
		@Expose
		public int doomspiralBroadcast = 0;
		@SettingInfo(name = "Ready Text", desc = "") @SettingText @SettingGroup(id = CHAT_DOOMSPIRAL) @Expose public String doomspiralReadyChatText = "";
		@SettingInfo(name = "Started Text", desc = "") @SettingText @SettingGroup(id = CHAT_DOOMSPIRAL) @Expose public String doomspiralStartedChatText = "";
		@SettingInfo(name = "Done Text", desc = "") @SettingText @SettingGroup(id = CHAT_DOOMSPIRAL) @Expose public String doomspiralDoneChatText = "Doomspiral Done!";

		@SettingInfo(
			name = "Only In That Biome",
			desc = "Only sends encounter and bird chat alerts in their respective biome")
		@SettingToggle
		@SettingGroup(id = ENCOUNTER_CHAT_ALERTS)
		@Expose
		public boolean encountersInBiomeOnly = true;

		@SettingInfo(name = "Birds", desc = "") @SettingSection(id = CHAT_BIRDS)
		@SettingGroup(id = ENCOUNTER_CHAT_ALERTS) public boolean birdsChatAccordion = false;

		@SettingInfo(name = "Macaw", desc = "") @SettingSection(id = CHAT_MACAW)
		@SettingGroup(id = CHAT_BIRDS) public boolean macawChatAccordion = false;
		@SettingInfo(name = "Send To", desc = "Sends when a Macaw spawns")
		@SettingChoice(values = {"Off", "Party Chat", "All Chat"})
		@SettingGroup(id = CHAT_MACAW)
		@Expose
		public int macawBroadcast = 1;
		@SettingInfo(name = "Text", desc = "") @SettingText @SettingGroup(id = CHAT_MACAW) @Expose public String macawChatText = "Macaw Spawned!";

		@SettingInfo(name = "All Birds", desc = "") @SettingSection(id = CHAT_ALL_BIRDS)
		@SettingGroup(id = CHAT_BIRDS) public boolean allBirdsChatAccordion = false;
		@SettingInfo(name = "Send To", desc = "Sends when a Bluebird or Parakeet spawns")
		@SettingChoice(values = {"Off", "Party Chat", "All Chat"})
		@SettingGroup(id = CHAT_ALL_BIRDS) @Expose
		public int allBirdsBroadcast = 0;
		@SettingInfo(name = "Text", desc = "Custom chat text\n§bTags: <CRITTER>")
		@SettingText @SettingGroup(id = CHAT_ALL_BIRDS) @Expose
		public String allBirdsChatText = "<CRITTER> Spawned!";

		@SettingInfo(name = "Total Feed", desc = "") @SettingSection(id = CHAT_TOTAL_FEED)
		@SettingGroup(id = CHAT_BIRDS) public boolean totalFeedChatAccordion = false;
		@SettingInfo(name = "Send To", desc = "Sends your Bird Feed inventory when Forest floor drops are done")
		@SettingChoice(values = {"Off", "Party Chat", "All Chat"})
		@SettingGroup(id = CHAT_TOTAL_FEED) @Expose
		public int totalFeedBroadcast = 1;
		@SettingInfo(name = "Text", desc = "Custom chat text\n§bTags: <ALL_FEED>, <TOTAL>, <SEEDS>, <WORMS>, <BERRIES>")
		@SettingText @SettingGroup(id = CHAT_TOTAL_FEED) @Expose
		public String totalFeedChatText = "<ALL_FEED>";

		@SettingInfo(name = "All Feed Used", desc = "") @SettingSection(id = CHAT_FEED_GONE)
		@SettingGroup(id = CHAT_BIRDS) public boolean feedGoneChatAccordion = false;
		@SettingInfo(name = "Send To", desc = "Sends when all collected Bird Feed is placed in the Birdfeeder")
		@SettingChoice(values = {"Off", "Party Chat", "All Chat"})
		@SettingGroup(id = CHAT_FEED_GONE) @Expose
		public int feedGoneBroadcast = 1;
		@SettingInfo(name = "Text", desc = "")
		@SettingText @SettingGroup(id = CHAT_FEED_GONE) @Expose
		public String feedGoneChatText = "All Feed Used!";

		@SettingInfo(name = "Contest Chat Alerts", desc = "")
		@SettingSection(id = CONTEST_CHAT_ALERTS)
		public boolean contestChatAlertsAccordion = false;

		@SettingInfo(name = "No Warnings",
			desc = "Skips Contest warning chat alerts after earning a ticket")
		@SettingToggle @SettingGroup(id = CONTEST_CHAT_ALERTS) @Expose
		public boolean contestNoWarningsAfterTicket = true;

		@SettingInfo(name = "Contest Started", desc = "") @SettingSection(id = CHAT_CONTEST_START)
		@SettingGroup(id = CONTEST_CHAT_ALERTS) public boolean contestStartChatAccordion = false;
		@SettingInfo(name = "Send To", desc = "Sends when Miria's Contest starts")
		@SettingChoice(values = {"Off", "Party Chat", "All Chat"})
		@SettingGroup(id = CHAT_CONTEST_START)
		@Expose
		public int contestStartBroadcast = 1;
		@SettingInfo(name = "Text", desc = "") @SettingText @SettingGroup(id = CHAT_CONTEST_START) @Expose public String contestStartChatText = "New Contest Started!";

		@SettingInfo(name = "5 Minute Warning", desc = "") @SettingSection(id = CHAT_CONTEST_FIVE)
		@SettingGroup(id = CONTEST_CHAT_ALERTS) public boolean contestFiveMinuteChatAccordion = false;
		@SettingInfo(name = "Send To", desc = "Sends when Miria's Contest has 5 minutes left")
		@SettingChoice(values = {"Off", "Party Chat", "All Chat"})
		@SettingGroup(id = CHAT_CONTEST_FIVE)
		@Expose
		public int contestFiveMinuteBroadcast = 1;
		@SettingInfo(name = "Text", desc = "") @SettingText @SettingGroup(id = CHAT_CONTEST_FIVE) @Expose public String contestFiveMinuteChatText = "Contest Has 5 Minutes Left!";

		@SettingInfo(name = "Contest Ended", desc = "") @SettingSection(id = CHAT_CONTEST_ENDED)
		@SettingGroup(id = CONTEST_CHAT_ALERTS) public boolean contestEndedChatAccordion = false;
		@SettingInfo(name = "Send To", desc = "Sends when Miria's Contest ends")
		@SettingChoice(values = {"Off", "Party Chat", "All Chat"})
		@SettingGroup(id = CHAT_CONTEST_ENDED)
		@Expose
		public int contestEndedBroadcast = 0;
		@SettingInfo(name = "Text", desc = "") @SettingText @SettingGroup(id = CHAT_CONTEST_ENDED) @Expose public String contestEndedChatText = "Contest Ended! New One In 30 Seconds";

		@SettingInfo(name = "Ticket Earned", desc = "") @SettingSection(id = CHAT_TICKET)
		@SettingGroup(id = CONTEST_CHAT_ALERTS) public boolean ticketChatAccordion = false;
		@SettingInfo(name = "Send To", desc = "Sends when a Safari Ticket is earned in Miria's Contest")
		@SettingChoice(values = {"Off", "Party Chat", "All Chat"})
		@SettingGroup(id = CHAT_TICKET)
		@Expose
		public int contestTicketEarnedBroadcast = 1;
		@SettingInfo(name = "Text", desc = "") @SettingText @SettingGroup(id = CHAT_TICKET) @Expose public String contestTicketEarnedChatText = "Ticket Earned!";

		public Broadcast gemzie() {
			return broadcast(gemzieBroadcast);
		}

		public Broadcast wumpa() {
			return broadcast(wumpaBroadcast);
		}

		public Broadcast doomspiral() {
			return broadcast(doomspiralBroadcast);
		}

		public Broadcast biomeDone() {
			return broadcast(biomeDoneBroadcast);
		}

		public Broadcast allButMacaw() {
			return broadcast(allButMacawBroadcast);
		}

		public Broadcast allDone() {
			return broadcast(allDoneBroadcast);
		}

		public Broadcast macaw() {
			return broadcast(macawBroadcast);
		}

		public Broadcast allBirds() {
			return broadcast(allBirdsBroadcast);
		}

		public Broadcast totalFeed() {
			return broadcast(totalFeedBroadcast);
		}

		public Broadcast feedGone() {
			return broadcast(feedGoneBroadcast);
		}

		public Broadcast hotspot() {
			return broadcast(hotspotBroadcast);
		}

		public Broadcast fullPartyJoined() {
			return broadcast(fullPartyJoinedBroadcast);
		}

		public Broadcast contestStart() {
			return broadcast(contestStartBroadcast);
		}

		public Broadcast contestFiveMinute() {
			return broadcast(contestFiveMinuteBroadcast);
		}

		public Broadcast contestEnded() {
			return broadcast(contestEndedBroadcast);
		}

		public Broadcast contestTicketEarned() {
			return broadcast(contestTicketEarnedBroadcast);
		}

		/** Guards against a config file holding an index that is no longer a choice. */
		private static Broadcast broadcast(int index) {
			return index >= 0 && index < Broadcast.values().length
				? Broadcast.values()[index] : Broadcast.NONE;
		}
	}

	public static class SparklingConfig {
		private static final int SPARKLING_ALERTS = 1;
		private static final int BANNER_ALERT = 2;
		private static final int BANNER_APPEARANCE = 3;
		private static final int BANNER_SOUND = 4;
		private static final int CHAT_ALERTS = 5;
		private static final int CHAT_DETECTED = 6, CHAT_CAUGHT = 7, CHAT_SHARED = 8;
		private static final int SPARKLING_MODE_OPTIONS = 9;
		private static final int CATCH_ALERT = 10;

		@SettingInfo(name = "Sparkling Mode",
			desc = "Enables sparkling mode to remove extra information for party's mutual Sparkling critters\n" +
				"§b/sparkling shared §7sets your party's shared Sparkling list")
		@SettingToggle
		@Expose
		public boolean sparklingMode = true;

		@SettingInfo(name = "Sparkling Mode Options", desc = "")
		@SettingSection(id = SPARKLING_MODE_OPTIONS)
		public boolean sparklingModeOptionsAccordion = false;

		@SettingInfo(name = "Ignore Uniques",
			desc = "Ignore unique catches in Sparkling Mode to prioritize faster Sparkling checks, completely ignoring already shared Sparkling critters")
		@SettingToggle @SettingGroup(id = SPARKLING_MODE_OPTIONS)
		@Expose
		public boolean sparklingIgnoreUniques = true;

		@SettingInfo(name = "Only Show Sparkling",
			desc = "Hides ordinary critter hitboxes and critter waypoints while Sparkling Mode is enabled")
		@SettingToggle @SettingGroup(id = SPARKLING_MODE_OPTIONS)
		@Expose
		public boolean sparklingOnlyShowSparkling = false;

		// Kept only so older settings files can migrate this value to Display.
		@Expose
		public boolean sparklingUniqueHitboxColours = false;

		@SettingInfo(name = "Sparkling Alerts", desc = "")
		@SettingSection(id = SPARKLING_ALERTS)
		public boolean sparklingAlertsAccordion = false;

		@SettingInfo(name = "Banner Alert", desc = "")
		@SettingSection(id = BANNER_ALERT)
		@SettingGroup(id = SPARKLING_ALERTS)
		public boolean sparklingBannerAccordion = false;

		@SettingInfo(name = "Test Alert", desc = "Preview this alert with its current settings")
		@SettingAction(buttonText = "Alert")
		@SettingGroup(id = BANNER_ALERT)
		public Runnable testSparklingBanner = EncounterAlerts::fireTestSparklingDetected;

		@SettingInfo(name = "Play Alert", desc = "Plays alert when a Sparkling critter is detected")
		@SettingChoice(values = {"Off", "Banner", "Sound", "Banner + Sound"}) @SettingGroup(id = BANNER_ALERT) @Expose
		public int sparklingBannerSoundMode = 3;

		@SettingInfo(name = "Appearance Settings", desc = "")
		@SettingSection(id = BANNER_APPEARANCE)
		@SettingGroup(id = BANNER_ALERT)
		public boolean sparklingBannerAppearanceAccordion = false;

		@SettingInfo(name = "Text", desc = "Custom banner text\n§bTags: <CRITTER>")
		@SettingText @SettingGroup(id = BANNER_APPEARANCE) @Expose
		public String sparklingBannerText = "Sparkling <CRITTER> Found!";

		@SettingInfo(name = "Scale", desc = "How large this banner alert's text is")
		@SettingRange(minValue = 0.1f, maxValue = 10f, minStep = 0.1f)
		@SettingGroup(id = BANNER_APPEARANCE) @Expose
		public float sparklingBannerScale = 5f;

		@SettingInfo(name = "Vertical Position", desc = "How far down the screen this banner alert sits\n§7Always centered horizontally")
		@SettingRange(minValue = 0f, maxValue = 1f, minStep = 0.01f)
		@SettingGroup(id = BANNER_APPEARANCE) @Expose
		public float sparklingBannerVerticalPosition = 0.4f;

		@SettingInfo(name = "Duration", desc = "How long this banner stays on screen, in seconds")
		@SettingRange(minValue = 0.5f, maxValue = 30f, minStep = 0.5f)
		@SettingGroup(id = BANNER_APPEARANCE) @Expose
		public float sparklingBannerDuration = 3f;

		@SettingInfo(name = "Sound Settings", desc = "")
		@SettingSection(id = BANNER_SOUND)
		@SettingGroup(id = BANNER_ALERT)
		public boolean sparklingBannerSoundAccordion = false;

		@SettingInfo(name = "Sound Choice", desc = "")
		@SettingChoice(values = {"Challenge Complete", "Player Level Up", "Experience Orb", "Amethyst Chime", "Note Block Pling", "Note Block Bell", "Beacon Activate", "Button Click", "Totem Used", "Note Block Chime", "Note Block Xylophone", "Note Block Iron Xylophone", "Note Block Cow Bell", "Note Block Flute", "Note Block Harp", "Note Block Banjo", "Note Block Didgeridoo", "Enchanting Table", "Ender Chest Open", "Firework Twinkle"})
		@SettingGroup(id = BANNER_SOUND) @Expose
		public int sparklingBannerSoundChoice = 26;

		@SettingInfo(name = "Volume", desc = "")
		@SettingRange(minValue = 0f, maxValue = 20f, minStep = 0.1f)
		@SettingGroup(id = BANNER_SOUND) @Expose
		public float sparklingBannerSoundVolume = 20f;

		@SettingInfo(name = "Pitch", desc = "")
		@SettingRange(minValue = 0.5f, maxValue = 2f, minStep = 0.1f)
		@SettingGroup(id = BANNER_SOUND) @Expose
		public float sparklingBannerSoundPitch = 1f;

		@SettingInfo(name = "Catch Alert", desc = "")
		@SettingSection(id = CATCH_ALERT)
		@SettingGroup(id = SPARKLING_ALERTS)
		public boolean sparklingCatchAlertAccordion = false;

		@SettingInfo(name = "Special Sparkling Catch",
			desc = "Uses a special celebration when a Sparkling critter is caught\n§cEPILEPSY WARNING: This option may affect photosensitive players")
		@SettingToggle
		@SettingGroup(id = CATCH_ALERT)
		@Expose
		public boolean specialSparklingCatch = false;

		@SettingInfo(name = "Chat Alerts", desc = "")
		@SettingSection(id = CHAT_ALERTS)
		@SettingGroup(id = SPARKLING_ALERTS)
		public boolean sparklingChatAlertsAccordion = false;

		@SettingInfo(name = "Sparkling Detected", desc = "")
		@SettingSection(id = CHAT_DETECTED) @SettingGroup(id = CHAT_ALERTS)
		public boolean sparklingDetectedChatAccordion = false;
		@SettingInfo(name = "Send To", desc = "Sends when a Sparkling critter is found")
		@SettingChoice(values = {"Off", "Party Chat", "All Chat"})
		@SettingGroup(id = CHAT_DETECTED)
		@Expose
		public int sparklingBroadcast = 1;
		@SettingInfo(name = "Text", desc = "Custom chat text\n§bTags: <CRITTER>") @SettingText @SettingGroup(id = CHAT_DETECTED) @Expose
		public String sparklingDetectedChatText = "Sparkling <CRITTER> Found!";

		@SettingInfo(name = "Sparkling Caught", desc = "")
		@SettingSection(id = CHAT_CAUGHT) @SettingGroup(id = CHAT_ALERTS)
		public boolean sparklingCaughtChatAccordion = false;
		@SettingInfo(name = "Send To", desc = "Sends a Sparkling catch and its duplicate number")
		@SettingChoice(values = {"Off", "Party Chat", "All Chat"})
		@SettingGroup(id = CHAT_CAUGHT)
		@Expose
		public int sparklingCaughtBroadcast = 1;
		@SettingInfo(name = "First Text", desc = "Custom chat text\n§bTags: <CRITTER>") @SettingText @SettingGroup(id = CHAT_CAUGHT) @Expose
		public String sparklingFirstCaughtChatText = "New! Sparkling <CRITTER> Caught!";
		@SettingInfo(name = "Duplicate Text", desc = "Custom chat text\n§bTags: <CRITTER>, <COUNT>") @SettingText @SettingGroup(id = CHAT_CAUGHT) @Expose
		public String sparklingDuplicateCaughtChatText = "Sparkling <CRITTER> Caught! #<COUNT>";

		@SettingInfo(name = "Sparkling Shared", desc = "")
		@SettingSection(id = CHAT_SHARED) @SettingGroup(id = CHAT_ALERTS)
		public boolean sparklingSharedChatAccordion = false;
		@SettingInfo(name = "Send To", desc = "Sends the shared Sparkling list when it changes")
		@SettingChoice(values = {"Off", "Party Chat", "All Chat"})
		@SettingGroup(id = CHAT_SHARED)
		@Expose
		public int sparklingSharedBroadcast = 1;
		@SettingInfo(name = "List Format",
			desc = "Chooses whether updates list shared or missing Sparkling critters")
		@SettingChoice(values = {"Shared", "Missing"})
		@SettingGroup(id = CHAT_SHARED)
		@Expose
		public int sparklingSharedListFormat = 0;

		public Broadcast detected() {
			return broadcast(sparklingBroadcast);
		}

		public Broadcast caught() {
			return broadcast(sparklingCaughtBroadcast);
		}

		public Broadcast shared() {
			return broadcast(sparklingSharedBroadcast);
		}

		/** Guards against a config file holding an index that is no longer a choice. */
		private static Broadcast broadcast(int index) {
			return index >= 0 && index < Broadcast.values().length
				? Broadcast.values()[index] : Broadcast.NONE;
		}
	}
}
