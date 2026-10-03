package dev.serko.safariutils.client;

import dev.serko.safariutils.BuildVersion;
import dev.serko.safariutils.data.Critters;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Safari Utils' fast, dependency-free settings workspace. */
public final class SafariSettingsScreen extends Screen {
	private static final long REOPEN_MEMORY_MILLIS = 10_000L;
	private static final Map<Class<?>, Field[]> PUBLIC_FIELDS = new HashMap<>();
	private static final Map<String, String> CLEAN_TEXT = new HashMap<>();
	private static final Map<String, String> DISPLAY_NAMES = new HashMap<>();
	private static String rememberedSettingsArea;
	private static String rememberedSettingsTab;
	private static int rememberedScroll;
	private static Set<String> rememberedExpandedSections = Set.of();
	private static long rememberedAt;
	private static final int WIDE_NAV_WIDTH = 136;
	private static final int HEADER_HEIGHT = 48;
	private static final int BRAND_LINE_GAP = 4;
	private static final int FOOTER_HEIGHT = 30;
	private static final int THEME_BUTTON_WIDTH = 126;
	private static final int DATA_BUTTON_SIZE = 28;
	private static final int SETTINGS_GAP = 6;
	private static final int[] CONSTELLATION_ORDER = {0, 4, 8, 3, 7, 2, 6, 1, 5};
	private static final String[] CRITTER_CHOICES = Critters.selectionOrder().stream()
		.map(critter -> critter.name()).toArray(String[]::new);
	private static final String[] CRITTER_GROUPS = Critters.selectionBiomes().stream()
		.map(biome -> biome.displayName()).toArray(String[]::new);
	private static final int[] CRITTER_GROUP_STARTS = critterGroupStarts();
	private static final String MOD_VERSION = FabricLoader.getInstance().getModContainer("safariutils")
		.map(container -> releaseVersion(container.getMetadata().getVersion().getFriendlyString()))
		.orElse("unknown");
	private int CARD;
	private int CARD_HOVER;
	private int BACKGROUND;
	private int SURFACE;
	private int BORDER;
	private int BLUE;
	private int CYAN;
	private int GREEN;
	private int RED;
	private int GOLD;
	private int SELECTED;
	private int SUB_SELECTED;
	private int TEXT;
	private int MUTED;
	private int DIM;
	private static int[][] themePalettes;
	private static int customThemeHash;
	private static int[] customThemeCache;
	private int navWidth = WIDE_NAV_WIDTH;
	private int workspaceLeft = WIDE_NAV_WIDTH;
	private int themeButtonWidth = THEME_BUTTON_WIDTH;

	private final Screen parent;
	private final List<SettingCategoryView> categories = new ArrayList<>();
	private final List<SettingsArea> settingsAreas = new ArrayList<>();
	private final Set<String> expandedSettingSections = new java.util.HashSet<>();
	private final Map<SettingsTab, Map<SettingsPage, Integer>> effectiveDepthCache =
		new java.util.IdentityHashMap<>();
	private final Map<SettingsPage, List<List<Field>>> settingCardCache =
		new java.util.IdentityHashMap<>();
	private final Map<Field, List<Field>> normalSettingCardCache =
		new java.util.IdentityHashMap<>();
	private final Map<Field, Integer> settingOrderCache = new java.util.IdentityHashMap<>();
	private final Map<Field, Map<Integer, SettingTextLayout>> settingTextLayoutCache =
		new java.util.IdentityHashMap<>();
	private final boolean[] visibleSettingBranches = new boolean[16];
	private final List<Hit> hits = new ArrayList<>();
	private final List<SoundPreviewHit> soundPreviewHits = new ArrayList<>();
	private final Map<WrapKey, List<String>> wrappedText = new LinkedHashMap<>(128, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<WrapKey, List<String>> eldest) {
			return size() > 512;
		}
	};
	private String cachedSearchQuery;
	private List<SearchGroup> cachedSearchGroups = List.of();
	private SettingsArea selectedArea;
	private SettingsTab selectedTab;
	private EditBox search;
	private int searchFrameX, searchFrameY, searchFrameWidth, searchFrameHeight;
	private String searchHintText = "Search";
	private Component searchHintNormal = Component.literal("Search");
	private int searchHintPhase = -1;
	private EditBox editor;
	private Field editingField;
	private Object editingOwner;
	private String editingOriginal;
	private boolean editingColour;
	private boolean editingNumber;
	private boolean editingInlineText;
	private float editingHue;
	private float editingSaturation;
	private float editingBrightness;
	private int editingAlpha = 255;
	private int colourFieldLeft, colourFieldTop, colourFieldWidth, colourFieldHeight;
	private int hueSliderLeft, hueSliderTop, hueSliderWidth;
	private int alphaSliderLeft, alphaSliderTop, alphaSliderWidth;
	private boolean updatingColourControls;
	private Number editingNumberOriginal;
	private int inlineEditorLeft, inlineEditorTop, inlineEditorRight, inlineEditorBottom;
	private int editingSliderLeft, editingSliderTop, editingSliderRight, editingSliderBottom;
	private Field choiceField;
	private Object choiceOwner;
	private SettingChoice choiceDropdown;
	private SettingMultiChoice multiChoiceDropdown;
	private int multiChoiceScroll;
	private int scroll;
	private int contentHeight;
	private ScreenRectangle contentScissor;
	private int contentViewportTop;
	private int contentViewportBottom;
	private int contentHeaderBottom = HEADER_HEIGHT;
	private int navigationScroll;
	private int navigationContentHeight;
	private Field draggingSlider;
	private Object draggingOwner;
	private SettingRange draggingRange;
	private int draggingLeft;
	private int draggingWidth;
	private int unlockProgress;
	private boolean unlockPanel;
	private boolean customThemePanel;
	private boolean specialSparklingConfirmation;
	private boolean customSparklingPanel;
	private int customSparklingPage;
	private EditBox customCalloutEditor;
	private EditBox customDurationEditor;
	private boolean customDurationDragging;
	private int customDurationSliderLeft;
	private int customDurationSliderWidth;
	private int customDurationSliderTop;
	private EditBox customVolumeEditor;
	private EditBox customPresetNameEditor;
	private final EditorBounds customCalloutBounds = new EditorBounds();
	private final EditorBounds customDurationBounds = new EditorBounds();
	private final EditorBounds customVolumeBounds = new EditorBounds();
	private final EditorBounds customPresetNameBounds = new EditorBounds();
	private boolean customPresetMenu;
	private boolean customPresetNaming;
	private boolean customPresetRenaming;
	private int customPresetPage;
	private int customPresetHitStart = -1;
	private int customSavedPresetIndex = -1;
	private String customPresetStatus = "";
	private boolean customVolumeDragging;
	private int customVolumeSliderLeft;
	private int customVolumeSliderWidth;
	private int customVolumeSliderTop;
	private int previewSong = Integer.MIN_VALUE;
	private int previewTheme = Integer.MIN_VALUE;
	private float previewDuration = Float.NaN;
	private int previewVolume = Integer.MIN_VALUE;
	private long previewSongStartedAt;
	private boolean customPreviewResuming;
	private long customResetArmedUntil;
	private long customDeleteArmedUntil;
	private int customDeleteArmedIndex = -1;
	private boolean partySyncConfirmation;
	private int pendingSparklingIntensity = -1;
	private int pendingSparklingSourcePreset = -1;
	private long signalCompletedAt;
	private int constellationLeft = Integer.MIN_VALUE;
	private int constellationTop;
	private int constellationSize;
	private final int[][] constellationNodes = new int[9][2];
	private long unlockGeometryFrame = Long.MIN_VALUE;
	private int unlockGeometryProgress = -1;
	private boolean unlockGeometryComplete;
	private final UnlockQuadBuilder unlockGeometry = new UnlockQuadBuilder(12_000);
	private int unlockGeometryLength;
	private int modalHitStart = -1;
	private long resetArmedUntil;

	private record SettingCategoryView(String key, Field field, Object value, SettingCategory info) { }
	private static final class SettingsArea {
		private final String key;
		private final String name;
		private final String description;
		private final List<SettingsPage> pages = new ArrayList<>();
		private final List<SettingsTab> tabs = new ArrayList<>();

		private SettingsArea(String key, String name, String description) {
			this.key = key;
			this.name = name;
			this.description = description;
		}
	}

	private record SettingsPage(String key, String name, String description, String breadcrumb,
		Object owner, List<Field> fields, int depth) {
		private boolean selectable() {
			return !fields.isEmpty();
		}
	}
	private record SettingsTab(String key, String name, String description,
		List<SettingsPage> pages) {
		private boolean selectable() {
			return pages.stream().anyMatch(SettingsPage::selectable);
		}
	}
	private record VisibleSetting(Object owner, Field field) { }
	private record SettingTextLayout(List<String> mainLines, List<String> tagLines,
		boolean safeModeComparison, int height) { }
	private record ThemeChoice(int id, String label) { }
	private record CustomThemeRole(String label, String field) { }
	private static final List<ThemeChoice> THEMES = List.of(
		new ThemeChoice(0, "Default"), new ThemeChoice(4, "Rainbow"),
		new ThemeChoice(5, "Amethyst"), new ThemeChoice(8, "Arctic"),
		new ThemeChoice(24, "Aurora"), new ThemeChoice(16, "Blueprint"),
		new ThemeChoice(25, "Candy"), new ThemeChoice(2, "Canyon"),
		new ThemeChoice(9, "Cherry Blossom"), new ThemeChoice(17, "Coffee"),
		new ThemeChoice(26, "Copper"), new ThemeChoice(18, "Cyberpunk"),
		new ThemeChoice(27, "Deep Sea"), new ThemeChoice(19, "Desert"),
		new ThemeChoice(10, "Ember"), new ThemeChoice(11, "Ender"),
		new ThemeChoice(3, "Forest"), new ThemeChoice(28, "Frostfire"),
		new ThemeChoice(12, "Golden Hour"), new ThemeChoice(20, "Jade"),
		new ThemeChoice(29, "Lavender"), new ThemeChoice(30, "Matrix"),
		new ThemeChoice(1, "Midnight"),
		new ThemeChoice(13, "Monochrome"), new ThemeChoice(14, "Nebula"),
		new ThemeChoice(6, "Ocean"), new ThemeChoice(21, "Paper"),
		new ThemeChoice(7, "Rose"), new ThemeChoice(31, "Royal"),
		new ThemeChoice(15, "Slate"), new ThemeChoice(22, "Solarized"),
		new ThemeChoice(32, "Sunset"), new ThemeChoice(23, "Terminal"),
		new ThemeChoice(33, "Vaporwave"), new ThemeChoice(34, "Custom"));
	private static final List<CustomThemeRole> CUSTOM_THEME_ROLES = List.of(
		new CustomThemeRole("Background", "customThemeBackground"),
		new CustomThemeRole("Navigation Surface", "customThemeSurface"),
		new CustomThemeRole("Cards", "customThemeCard"),
		new CustomThemeRole("Hovered Cards", "customThemeCardHover"),
		new CustomThemeRole("Selected Tabs", "customThemeSelected"),
		new CustomThemeRole("Selected Sub-Tabs", "customThemeSubSelected"),
		new CustomThemeRole("Borders", "customThemeBorder"),
		new CustomThemeRole("Primary Accent", "customThemePrimary"),
		new CustomThemeRole("Secondary Accent", "customThemeSecondary"),
		new CustomThemeRole("Success", "customThemeSuccess"),
		new CustomThemeRole("Error", "customThemeError"),
		new CustomThemeRole("Highlight", "customThemeHighlight"),
		new CustomThemeRole("Primary Text", "customThemeText"),
		new CustomThemeRole("Secondary Text", "customThemeMuted"),
		new CustomThemeRole("Muted Text", "customThemeDim"),
		new CustomThemeRole("Safari Title", "customThemeSafariTitle"),
		new CustomThemeRole("Utils Title", "customThemeUtilsTitle"));
	private static final List<String> THEME_LABELS = THEMES.stream().map(ThemeChoice::label).toList();
	private static final List<String> SOUND_LABELS = AlertSounds.alphabetical().stream()
		.map(AlertSounds.Choice::label).toList();
	private record SearchItem(Object owner, Field field, String context) { }
	private record SearchGroup(String context, Object owner, List<Field> fields) { }
	private static final class EditorBounds {
		private int left;
		private int top;
		private int right;
		private int bottom;
		private boolean active;

		private void set(int left, int top, int right, int bottom) {
			this.left = left;
			this.top = top;
			this.right = right;
			this.bottom = bottom;
			active = true;
		}

		private void clear() {
			active = false;
		}

		private boolean contains(double x, double y) {
			return active && inside(x, y, left, top, right, bottom);
		}
	}
	private record WrapKey(String text, int width) { }
	private record SoundPreviewHit(int left, int top, int right, int bottom, int soundId) {
		boolean contains(double x, double y) {
			return x >= left && x < right && y >= top && y < bottom;
		}
	}
	private record Hit(int left, int top, int right, int bottom, Runnable action) {
		boolean contains(double x, double y) {
			return x >= left && x < right && y >= top && y < bottom;
		}
	}

	private static String releaseVersion(String version) {
		int profileSuffix = version.indexOf("+mc");
		String release = profileSuffix < 0 ? version : version.substring(0, profileSuffix);
		return release.replaceFirst("-(?:extra|private|developer)$", "");
	}

	public SafariSettingsScreen(Screen parent) {
		super(Component.literal("Safari Utils Settings"));
		this.parent = parent;
		loadCategories();
		if (System.currentTimeMillis() - rememberedAt <= REOPEN_MEMORY_MILLIS) {
			selectedArea = settingsAreas.stream().filter(area -> area.key.equals(rememberedSettingsArea))
				.findFirst().orElse(selectedArea);
			selectRememberedTab();
			restoreRememberedSections();
			scroll = rememberedScroll;
		}
	}

	private void restoreRememberedSections() {
		if (selectedTab == null || rememberedExpandedSections.isEmpty()) return;
		for (SettingsPage page : selectedTab.pages) {
			if (rememberedExpandedSections.contains(page.key)) {
				expandedSettingSections.add(page.key);
			}
		}
	}

	private void loadCategories() {
		categories.clear();
		cachedSearchQuery = null;
		cachedSearchGroups = List.of();
		SafariConfig config = ConfigManager.get();
		for (Field field : SafariConfig.class.getFields()) {
			SettingCategory category = field.getAnnotation(SettingCategory.class);
			if (category == null) continue;
			try {
				categories.add(new SettingCategoryView(field.getName(), field, field.get(config), category));
			} catch (IllegalAccessException ignored) {
			}
		}
		buildSettingsNavigation();
	}

	private void buildSettingsNavigation() {
		settingsAreas.clear();
		effectiveDepthCache.clear();
		settingCardCache.clear();
		normalSettingCardCache.clear();
		settingOrderCache.clear();
		settingTextLayoutCache.clear();
		SettingsArea display = area("display", "Display", "HUDs, waypoints, colors, and Safari presentation");
		SettingsArea gameplay = area("gameplay", "Gameplay", "Safari behavior, party tools, and profit tracking");
		SettingsArea alerts = area("alerts", "Alerts", "On-screen and outgoing chat alerts");
		SettingsArea sparkling = area("sparkling", "Sparkling", "Sparkling hunting, detection, and celebrations");
		SettingsArea advanced = AdvancedUnlock.isUnlocked()
			? area("advanced", "Safe Mode", "Visibility-based detection and waypoint behavior") : null;
		SettingsArea developer = AdvancedUnlock.isUnlocked() && BuildVersion.DEVELOPER
			? area("developer", "Developer", "Diagnostics, research, and test-run controls") : null;

		SettingCategoryView displaySource = source("display");
		SettingCategoryView gameplaySource = source("gameplay");
		SettingCategoryView profitSource = source("profit");
		SettingCategoryView alertSource = source("alerts");
		SettingCategoryView chatSource = source("party");
		SettingCategoryView sparklingSource = source("sparkling");
		SettingCategoryView advancedSource = source("advanced");

		addRootPage(display, displaySource, "display.overview", "HUD Layout",
			"Move and arrange the visible HUD panels", "editPositions");
		if (advancedSource != null) addRootPage(display, advancedSource, "display.theme", "Special Theme",
			"Apply the optional theme across settings and HUDs", "specialTheme");
		addRootPage(display, displaySource, "display.safari.chat", "Chat Filtering",
			"Hide selected server dialogue while retaining mod detection", "hiddenChatMessages");
		addRootPage(display, displaySource, "display.safari.comfort", "Visual Comfort",
			"Reduce disruptive Safari screen effects", "removeColdOverlay", "removeDarkness");
		appendAllSections(display, displaySource, 0, "Display");
		organizeWaypointSettings(display, displaySource);

		addRootPage(gameplay, gameplaySource, "gameplay.auto-hideyho", "Hideyho",
			"Automatically accept Hideyho's Hide N' Seek game", "autoAcceptHideyho");
		addRootPage(gameplay, gameplaySource, "gameplay.tickets", "Tickets",
			"Protect tickets and configure trusted players for timed ticket trading",
			"protectSafariTicket", "configureTicketTrading");
		if (advancedSource != null) addRootPage(gameplay, advancedSource, "gameplay.party", "Party Sync",
			"Share objective progress with a fully participating modded party", "enablePartySync");
		addRootPage(gameplay, profitSource, "gameplay.profit", "Profit Tracking",
			"Control run profit tracking and Bazaar pricing");

		addRootPage(alerts, alertSource, "alerts.general", "General",
			"Shared alert behavior", "muteOtherSounds");
		appendAllSections(alerts, alertSource, 0, "On-Screen Alerts");
		appendAllSections(alerts, chatSource, 0, "Chat");

		addRootPage(sparkling, sparklingSource, "sparkling.overview", "Sparkling Mode",
			"Core Sparkling hunting behavior", "sparklingMode");
		appendAllSections(sparkling, sparklingSource, 0, "Sparkling");
		organizeSparklingAlertSettings(sparkling);

		if (advanced != null && advancedSource != null) {
			if (BuildVersion.SAFE) {
				addRootPage(advanced, advancedSource, "advanced.safe-mode.locked", "Safe Mode",
					"Safe Mode build information", "safeModeLockedNotice");
			} else {
				appendNamedSection(advanced, advancedSource, "safeModeAccordion", 0, "Advanced");
				organizeSafeModeSettings(advanced);
			}
		}
		if (developer != null && advancedSource != null) {
			appendNamedSection(developer, advancedSource, "testingAccordion", 0, "Developer");
			flattenDeveloperSettings(developer);
		}

		buildSettingsTabs();
		settingsAreas.removeIf(area -> area.tabs.stream().noneMatch(SettingsTab::selectable));
		indexSettingCards();
		String selectedAreaKey = selectedArea == null ? null : selectedArea.key;
		selectedArea = settingsAreas.stream().filter(area -> area.key.equals(selectedAreaKey))
			.findFirst().orElse(settingsAreas.isEmpty() ? null : settingsAreas.getFirst());
		ensureSelectedTab();
	}

	private void indexSettingCards() {
		int order = 0;
		try {
			settingOrderCache.put(SafariConfig.DisplayConfig.class.getField("settingsTheme"), order++);
		} catch (NoSuchFieldException ignored) {
		}
		Set<SettingsPage> indexedPages = java.util.Collections.newSetFromMap(
			new java.util.IdentityHashMap<>());
		for (SettingsArea area : settingsAreas) {
			for (SettingsTab tab : area.tabs) {
				for (SettingsPage page : tab.pages) {
					if (!indexedPages.add(page)) continue;
					if (page.owner == null) continue;
					for (List<Field> card : settingCardCache.computeIfAbsent(page, this::settingCards)) {
						for (Field field : card) {
							normalSettingCardCache.putIfAbsent(field, card);
							settingOrderCache.putIfAbsent(field, order++);
						}
					}
				}
			}
		}
	}

	private void buildSettingsTabs() {
		for (SettingsArea area : settingsAreas) area.tabs.clear();
		SettingsArea display = areaByKey("display");
		addTab(display, "huds", "HUDs", "Visible HUD panels, lines, and layout",
			page -> page.key.equals("display.overview") || !page.breadcrumb.contains("Border Colors")
				&& containsAny(page.breadcrumb,
					"Progress HUD", "Missing HUD", "Contest HUD", "Party Objective HUD"));
		addTab(display, "waypoints", "Waypoints", "World markers, critter overlays, and their colors",
			page -> page.key.startsWith("display.waypoint-settings")
				|| page.breadcrumb.contains("Critter Hitboxes"));
		addTab(display, "theme", "Theme & Border Colors", "Interface theme and border colors",
			page -> page.key.equals("display.theme") || page.breadcrumb.contains("Border Colors"));
		addTab(display, "safari", "Safari", "Safari chat visibility and visual comfort",
			page -> page.key.equals("display.safari.chat")
				|| page.key.equals("display.safari.comfort"));

		SettingsArea gameplay = areaByKey("gameplay");
		addTab(gameplay, "safari", "Safari", "Safari convenience, tickets, and optional party synchronization",
			page -> page.key.equals("gameplay.auto-hideyho") || page.key.equals("gameplay.tickets")
				|| page.key.equals("gameplay.party"));
		addTab(gameplay, "profit", "Profit", "Run profit tracking and Bazaar pricing",
			page -> page.key.equals("gameplay.profit"));

		SettingsArea alerts = areaByKey("alerts");
		addTab(alerts, "general", "General", "Shared alert behavior and appearance",
			page -> page.key.equals("alerts.general") || page.breadcrumb.contains("Banner Appearance"));
		addTab(alerts, "banners", "Banners", "On-screen alert appearance, playback, and content",
			page -> page.breadcrumb.startsWith("On-Screen Alerts"));
		addTab(alerts, "chat", "Chat", "Outgoing Safari, encounter, and contest messages",
			page -> page.breadcrumb.startsWith("Chat"));

		SettingsArea sparkling = areaByKey("sparkling");
		addTab(sparkling, "sparkling-mode", "Sparkling Mode", "Sparkling detection and hunting behavior",
			page -> page.key.equals("sparkling.overview")
				|| page.breadcrumb.contains("Sparkling Mode Options"));
		addTab(sparkling, "alerts", "Alerts", "Sparkling banners, catches, sounds, and chat alerts",
			page -> page.breadcrumb.contains("Sparkling Alerts"));

		SettingsArea advanced = areaByKey("advanced");
		addTab(advanced, "options", "Options", "Choose which features require visible confirmation",
			page -> true);

		SettingsArea developer = areaByKey("developer");
		addTab(developer, "diagnostics", "Diagnostics", "Automatic logging and diagnostic overlays",
			page -> !page.breadcrumb.contains("Data Collecting"));
		addTab(developer, "data", "Data Collection", "Test-run persistence and learned locations",
			page -> page.breadcrumb.contains("Data Collecting"));
		// Any future setting section remains reachable even before its navigation is
		// intentionally categorized.
		for (SettingsArea area : settingsAreas) {
			Set<SettingsPage> assigned = new java.util.LinkedHashSet<>();
			for (SettingsTab tab : area.tabs) assigned.addAll(tab.pages);
			List<SettingsPage> remaining = area.pages.stream()
				.filter(page -> !assigned.contains(page)).toList();
			if (!remaining.isEmpty()) area.tabs.add(new SettingsTab("more", "More",
				"Additional settings", remaining));
		}
	}

	private SettingsArea areaByKey(String key) {
		return settingsAreas.stream().filter(area -> area.key.equals(key)).findFirst().orElse(null);
	}

	private void addTab(SettingsArea area, String key, String name, String description,
			java.util.function.Predicate<SettingsPage> include) {
		if (area == null) return;
		Set<SettingsPage> assigned = new java.util.LinkedHashSet<>();
		for (SettingsTab tab : area.tabs) assigned.addAll(tab.pages);
		List<SettingsPage> pages = area.pages.stream()
			.filter(page -> !assigned.contains(page) && include.test(page)).toList();
		if (pages.stream().anyMatch(SettingsPage::selectable)) {
			area.tabs.add(new SettingsTab(key, name, description, pages));
		}
	}

	private static boolean containsAny(String value, String... needles) {
		for (String needle : needles) if (value.contains(needle)) return true;
		return false;
	}

	private SettingsArea area(String key, String name, String description) {
		SettingsArea area = new SettingsArea(key, name, description);
		settingsAreas.add(area);
		return area;
	}

	private SettingCategoryView source(String key) {
		return categories.stream().filter(category -> category.key.equals(key)).findFirst().orElse(null);
	}

	private void organizeSafeModeSettings(SettingsArea area) {
		Object owner = area.pages.stream().map(SettingsPage::owner)
			.filter(java.util.Objects::nonNull).findFirst().orElse(null);
		if (owner == null) return;
		area.pages.clear();
		area.pages.add(navigationPage(owner, "advanced.safe-mode.sparklings", "Sparklings",
			"Safe Mode  ›  Sparklings", 0, "safeSparklingCritters"));
		area.pages.add(navigationPage(owner, "advanced.safe-mode.detection", "Detection And HUD",
			"Safe Mode  ›  Detection And HUD", 0, "safeVisibleCritterDetection",
			"safeHideNearbyCounts", "safeConservativeAvailability", "safeConservativeCompletion"));
		area.pages.add(navigationPage(owner, "advanced.safe-mode.overlays", "Critter Overlays",
			"Safe Mode  ›  Critter Overlays", 0, "safeCritterHitboxes", "safeHideyho",
			"safeHideonwall", "safeDuplico", "safeBloodbat", "safeHideonfloor"));
		area.pages.add(navigationPage(owner, "advanced.safe-mode.objectives", "Static Objectives",
			"Safe Mode  ›  Static Objectives", 0, "safeFloorDrops", "safeBeeNests",
			"safeRockmiteMounds", "safeSnoozleWalls", "safeTroodonWalls"));
	}

	private static void flattenDeveloperSettings(SettingsArea area) {
		for (int index = 0; index < area.pages.size(); index++) {
			SettingsPage page = area.pages.get(index);
			if (!page.name.equals("Testing")) continue;
			area.pages.set(index, new SettingsPage("developer.logging", "Automatic Logging", "",
				"Developer  ›  Automatic Logging", page.owner, page.fields, 0));
			return;
		}
	}

	/** Keeps the three top-level Sparkling alert groups in their intentional UI order. */
	private static void organizeSparklingAlertSettings(SettingsArea area) {
		if (area == null) return;
		int insertion = -1;
		List<SettingsPage> alertPages = new ArrayList<>();
		for (int index = 0; index < area.pages.size(); index++) {
			SettingsPage page = area.pages.get(index);
			if (!page.breadcrumb.contains("Sparkling Alerts")) continue;
			if (insertion < 0) insertion = index;
			alertPages.add(page);
		}
		if (insertion < 0) return;
		area.pages.removeAll(alertPages);
		alertPages.sort(java.util.Comparator.comparingInt(SafariSettingsScreen::sparklingAlertOrder));
		area.pages.addAll(Math.min(insertion, area.pages.size()), alertPages);
	}

	private static int sparklingAlertOrder(SettingsPage page) {
		if (page.name.equals("Sparkling Alerts")) return 0;
		if (page.breadcrumb.contains("  ›  Banner")) return 10 + page.depth;
		if (page.breadcrumb.contains("  ›  Chat")) return 20 + page.depth;
		if (page.breadcrumb.contains("  ›  Special Catch")) return 30 + page.depth;
		return 40 + page.depth;
	}

	private void organizeWaypointSettings(SettingsArea area, SettingCategoryView source) {
		if (area == null || source == null) return;
		int insertion = -1;
		for (int index = 0; index < area.pages.size(); index++) {
			SettingsPage page = area.pages.get(index);
			if (page.breadcrumb.contains("Waypoints") || page.breadcrumb.contains("Waypoint Colors")) {
				if (insertion < 0) insertion = index;
			}
		}
		if (insertion < 0) return;
		area.pages.removeIf(page -> page.breadcrumb.contains("Waypoints")
			|| page.breadcrumb.contains("Waypoint Colors"));
		List<SettingsPage> organized = List.of(
			settingsPage(source.value, "display.waypoint-settings", "Waypoint Settings", 0,
				"waypointDistance", "hidePossibleWaypoints", "displayNametags", "eagleRarity"),
			settingsPage(source.value, "display.waypoint-settings.objectives", "Objectives", 1,
				"floorDrops", "floorDropColour", "floorDropFaceColour",
				"highlightMounds", "moundColour", "highlightSnooperWalls", "snooperWallColour",
				"highlightTroodonWalls", "troodonWallColour", "highlightNests", "nestColour"),
			settingsPage(source.value, "display.waypoint-settings.critters", "Critters", 1,
				"hideyhoSolver", "hideyhoColour", "highlightHideonwalls", "hideonwallColour",
				"highlightDuplico", "duplicoColour", "highlightBloodbat", "bloodbatColour",
				"highlightHideonfloor", "hideonfloorColour"),
			settingsPage(source.value, "display.waypoint-settings.critters.recatch", "Recatch", 2,
				"recatchHelper", "recatchColour", "recatchPityTitle", "recatchRarityColour")
		);
		area.pages.addAll(Math.min(insertion, area.pages.size()), organized);
	}

	private SettingsPage settingsPage(Object owner, String key, String name, int depth,
			String... fieldNames) {
		String breadcrumb = switch (key) {
			case "display.waypoint-settings" -> "Display  ›  Waypoint Settings";
			case "display.waypoint-settings.objectives" -> "Display  ›  Waypoint Settings  ›  Objectives";
			case "display.waypoint-settings.critters" -> "Display  ›  Waypoint Settings  ›  Critters";
			default -> "Display  ›  Waypoint Settings  ›  Critters  ›  " + name;
		};
		return navigationPage(owner, key, name, breadcrumb, depth, fieldNames);
	}

	private SettingsPage navigationPage(Object owner, String key, String name,
			String breadcrumb, int depth, String... fieldNames) {
		Map<String, Field> available = new HashMap<>();
		for (Field field : publicFields(owner.getClass())) available.put(field.getName(), field);
		List<Field> fields = new ArrayList<>();
		for (String fieldName : fieldNames) {
			Field field = available.get(fieldName);
			if (field != null && hasEditor(field)) fields.add(field);
		}
		return new SettingsPage(key, name, "", breadcrumb,
			owner, List.copyOf(fields), depth);
	}

	private void addRootPage(SettingsArea area, SettingCategoryView source, String key,
			String name, String description, String... fieldNames) {
		if (area == null || source == null) return;
		List<Field> fields = new ArrayList<>();
		if (fieldNames.length == 0) {
			for (Field field : publicFields(source.value.getClass())) {
				if (rootSetting(field)) fields.add(field);
			}
		} else {
			Map<String, Field> available = new HashMap<>();
			for (Field field : publicFields(source.value.getClass())) {
				if (rootSetting(field)) available.put(field.getName(), field);
			}
			for (String fieldName : fieldNames) {
				Field field = available.get(fieldName);
				if (field != null) fields.add(field);
			}
		}
		if (!fields.isEmpty()) area.pages.add(new SettingsPage(key, name, description,
			area.name + "  ›  " + name, source.value, List.copyOf(fields), 0));
	}

	private static boolean rootSetting(Field field) {
		return field.getAnnotation(SettingInfo.class) != null && !isHeaderOnly(field)
			&& field.getAnnotation(SettingSection.class) == null && hasEditor(field)
			&& belongsTo(field, null);
	}

	private void appendAllSections(SettingsArea area, SettingCategoryView source,
			int depth, String breadcrumb) {
		if (area == null || source == null) return;
		for (Field field : publicFields(source.value.getClass())) {
			if (field.getAnnotation(SettingSection.class) == null || !belongsTo(field, null)) continue;
			appendSection(area, source.value, source.value.getClass(), field, depth, breadcrumb);
		}
	}

	private void appendNamedSection(SettingsArea area, SettingCategoryView source,
			String fieldName, int depth, String breadcrumb) {
		if (area == null || source == null) return;
		for (Field field : publicFields(source.value.getClass())) {
			if (field.getName().equals(fieldName) && field.getAnnotation(SettingSection.class) != null) {
				appendSection(area, source.value, source.value.getClass(), field, depth, breadcrumb);
				return;
			}
		}
	}

	private void appendSection(SettingsArea area, Object owner, Class<?> type, Field sectionField,
			int depth, String breadcrumb) {
		SettingInfo info = sectionField.getAnnotation(SettingInfo.class);
		SettingSection section = sectionField.getAnnotation(SettingSection.class);
		if (info == null || section == null) return;
		List<Field> direct = directSettings(type, section.id());
		String name = displayName(info.name());
		String path = breadcrumb + "  ›  " + name;
		String key = type.getName() + "." + sectionField.getName();
		area.pages.add(new SettingsPage(key, name, clean(info.desc()), path,
			direct.isEmpty() ? null : owner, List.copyOf(direct), depth));
		for (Field child : publicFields(type)) {
			if (child.getAnnotation(SettingSection.class) == null || !belongsTo(child, section.id())) continue;
			appendSection(area, owner, type, child, depth + 1, path);
		}
	}

	private List<Field> directSettings(Class<?> type, Integer parentId) {
		List<Field> fields = new ArrayList<>();
		for (Field field : publicFields(type)) {
			if (field.getAnnotation(SettingInfo.class) == null || isHeaderOnly(field)
					|| field.getAnnotation(SettingSection.class) != null || !hasEditor(field)
					|| !belongsTo(field, parentId)) continue;
			fields.add(field);
		}
		return fields;
	}

	private void ensureSelectedTab() {
		if (selectedArea == null) {
			selectedTab = null;
			return;
		}
		String selectedTabKey = selectedTab == null ? null : selectedTab.key;
		selectedTab = selectedArea.tabs.stream()
			.filter(tab -> tab.selectable() && tab.key.equals(selectedTabKey))
			.findFirst().orElseGet(() -> selectedArea.tabs.stream()
				.filter(SettingsTab::selectable).findFirst().orElse(null));
	}

	private void selectRememberedTab() {
		if (selectedArea == null || rememberedSettingsTab == null) {
			ensureSelectedTab();
			return;
		}
		selectedTab = selectedArea.tabs.stream()
			.filter(tab -> tab.selectable() && tab.key.equals(rememberedSettingsTab))
			.findFirst().orElse(null);
		ensureSelectedTab();
	}

	@Override
	protected void init() {
		updateResponsiveLayout();
		String preservedSearch = search == null ? "" : search.getValue();
		boolean searchFocused = search != null && search.isFocused();
		boolean editorFocused = editor != null && editor.isFocused();
		boolean calloutFocused = customCalloutEditor != null && customCalloutEditor.isFocused();
		boolean durationFocused = customDurationEditor != null && customDurationEditor.isFocused();
		boolean volumeFocused = customVolumeEditor != null && customVolumeEditor.isFocused();
		boolean presetNameFocused = customPresetNameEditor != null
			&& customPresetNameEditor.isFocused();
		clearWidgets();
		int gutter = ResponsiveUI.gutter(width);
		int searchX = workspaceLeft + gutter;
		int dataLeft = width - gutter - DATA_BUTTON_SIZE;
		int themeLeft = dataLeft - SETTINGS_GAP - themeButtonWidth;
		int searchWidth = Math.max(24, themeLeft - 8 - searchX);
		searchFrameX = searchX;
		searchFrameY = (HEADER_HEIGHT - 20) / 2 - 1;
		searchFrameWidth = searchWidth;
		searchFrameHeight = 20;
		// The native unbordered control puts text at its own top-left. Inset the
		// widget itself while Safari Utils draws the themed outer shell.
		search = new EditBox(font, searchX + 4, searchFrameY + 6,
			Math.max(8, searchWidth - 8), 9,
			Component.literal("Search Settings"));
		String hint = font.width("Search settings, descriptions, and tags...") <= searchWidth - 18
			? "Search settings, descriptions, and tags..."
			: font.width("Search settings...") <= searchWidth - 18 ? "Search settings..." : "Search";
		searchHintText = hint;
		searchHintNormal = Component.literal(hint);
		searchHintPhase = -1;
		search.setHint(searchHintNormal);
		search.setMaxLength(80);
		search.setValue(preservedSearch);
		search.setBordered(false);
		search.setResponder(value -> {
			scroll = 0;
		});
		UIDraw.rainbowEditBox(search, font);
		addRenderableWidget(search);
		if (editor != null) addRenderableWidget(editor);
		if (customSparklingPanel) {
			if (customCalloutEditor != null) addRenderableWidget(customCalloutEditor);
			if (customDurationEditor != null) addRenderableWidget(customDurationEditor);
			if (customVolumeEditor != null) addRenderableWidget(customVolumeEditor);
			if (customPresetNaming && customPresetNameEditor != null) {
				addRenderableWidget(customPresetNameEditor);
			}
			search.visible = false;
			search.active = false;
		}
		if (presetNameFocused && customPresetNameEditor != null) setFocused(customPresetNameEditor);
		else if (calloutFocused && customCalloutEditor != null) setFocused(customCalloutEditor);
		else if (durationFocused && customDurationEditor != null) setFocused(customDurationEditor);
		else if (volumeFocused && customVolumeEditor != null) setFocused(customVolumeEditor);
		else if (editorFocused && editor != null) setFocused(editor);
		else if (searchFocused && search.visible) setFocused(search);
	}

	private void updateResponsiveLayout() {
		if (width >= 760) {
			navWidth = WIDE_NAV_WIDTH;
		} else if (width >= 520) {
			navWidth = 116;
		} else {
			navWidth = Math.max(88, Math.min(102, width / 5));
		}
		workspaceLeft = navWidth;
		int contentWidth = width - workspaceLeft;
		themeButtonWidth = contentWidth < 330 ? 78 : THEME_BUTTON_WIDTH;
	}

	@Override
	public void onClose() {
		rememberUIState();
		ConfigManager.save();
		ClientCompat.setScreen(parent);
	}

	Screen parentScreen() {
		return parent;
	}

	@Override
	public void removed() {
		if (customSparklingPanel) AlertSounds.stopSparklingPreview(Minecraft.getInstance());
		rememberUIState();
		ConfigManager.save();
		super.removed();
	}

	private void rememberUIState() {
		rememberedSettingsArea = selectedArea == null ? null : selectedArea.key;
		rememberedSettingsTab = selectedTab == null ? null : selectedTab.key;
		rememberedScroll = scroll;
		rememberedExpandedSections = Set.copyOf(expandedSettingSections);
		rememberedAt = System.currentTimeMillis();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		applyTheme();
		boolean editingSameSlider = editingInlineText && editingNumber && editor != null
			&& inside(mouseX, mouseY, editingSliderLeft, editingSliderTop,
				editingSliderRight, editingSliderBottom);
		boolean modalOpen = unlockPanel || customThemePanel || customSparklingPanel
			|| specialSparklingConfirmation
			|| partySyncConfirmation
			|| editor != null && !editingInlineText || choiceField != null || editingSameSlider;
		int backgroundMouseX = modalOpen ? Integer.MIN_VALUE : mouseX;
		int backgroundMouseY = modalOpen ? Integer.MIN_VALUE : mouseY;
		graphics.fill(0, 0, width, height, BACKGROUND);
		graphics.fill(0, 0, navWidth, height, SURFACE);
		graphics.fill(workspaceLeft, 0, width, HEADER_HEIGHT, SURFACE);
		graphics.fill(workspaceLeft, height - FOOTER_HEIGHT, width, height, SURFACE);
		graphics.fill(navWidth - 1, 0, navWidth, height, BORDER);
		graphics.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, BORDER);
		if (SpecialTheme.rainbow()) {
			// One cached batch covers the whole workspace; individual cards do not own
			// independent particle systems or allocate effects while scrolling.
			SpecialTheme.stars(graphics, 0, 0, width, height, 0.7f);
		}
		drawBrand(graphics);
		hits.clear();
		soundPreviewHits.clear();
		customCalloutBounds.clear();
		customDurationBounds.clear();
		customVolumeBounds.clear();
		customPresetNameBounds.clear();
		modalHitStart = -1;
		customPresetHitStart = -1;
		drawSearchFieldFrame(graphics);
		drawThemeControl(graphics, backgroundMouseX, backgroundMouseY);
		drawDataControl(graphics, backgroundMouseX, backgroundMouseY);
		drawNavigation(graphics, backgroundMouseX, backgroundMouseY);
		drawCategoryHeader(graphics, backgroundMouseX, backgroundMouseY);
		drawContent(graphics, backgroundMouseX, backgroundMouseY);
		drawFooter(graphics, backgroundMouseX, backgroundMouseY);
		if (unlockPanel) {
			modalHitStart = hits.size();
			drawUnlockPanel(graphics);
		}
		if (customThemePanel) {
			modalHitStart = hits.size();
			drawCustomThemePanel(graphics, mouseX, mouseY);
		}
		if (specialSparklingConfirmation) {
			modalHitStart = hits.size();
			drawSpecialSparklingConfirmation(graphics, mouseX, mouseY);
		}
		if (customSparklingPanel) {
			modalHitStart = hits.size();
			drawCustomSparklingPanel(graphics, mouseX, mouseY);
		}
		if (partySyncConfirmation) {
			modalHitStart = hits.size();
			drawPartySyncConfirmation(graphics, mouseX, mouseY);
		}
		if (editor != null && !editingInlineText) {
			modalHitStart = hits.size();
			drawEditorModal(graphics, mouseX, mouseY);
		}
		if (choiceField != null) {
			modalHitStart = hits.size();
			drawChoiceModal(graphics, mouseX, mouseY);
		}
		if (search != null) {
			updateSearchHint();
			search.setTextColor(TEXT);
			search.setTextColorUneditable(MUTED);
			UIDraw.updateRainbowCaret(search, TEXT);
		}
		UIDraw.updateRainbowCaret(editor, TEXT);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	/** Animates the placeholder only when the special theme is active. */
	private void updateSearchHint() {
		searchHintPhase = UIDraw.updateRainbowHint(search, font, searchHintText,
			searchHintNormal, searchHintPhase);
	}

	/** Replaces the native textbox shell so search follows every settings theme. */
	private void drawSearchFieldFrame(GuiGraphicsExtractor graphics) {
		if (search == null || !search.visible) return;
		int x = searchFrameX;
		int y = searchFrameY;
		int w = searchFrameWidth;
		int h = searchFrameHeight;
		graphics.fill(x, y, x + w, y + h, CARD);
		outline(graphics, x, y, w, h, BORDER);
	}

	private void applyTheme() {
		int id = Math.clamp(ConfigManager.get().display.settingsTheme, 0, 34);
		int rainbow = UIDraw.rainbowAt(0, 0.66f);
		if (themePalettes == null) themePalettes = new int[][] {
			{0xF00B0D13, 0xE611141C, 0xD9181B24, 0xE3222733, 0xFF30384A, 0xFF55AAFF, 0xFF55FFFF, 0xFF55FF88, 0xFFFF6677, 0xFFFFC857, 0xFFF2F5FA, 0xFF9DA7B8, 0xFF697386},
			{0xF0060810, 0xEB0C1020, 0xDC11172A, 0xEB18213A, 0xFF2A3557, 0xFF7A8CFF, 0xFFA6B3FF, 0xFF67E8A5, 0xFFFF6F91, 0xFFB8A7FF, 0xFFF5F6FF, 0xFFA2A9C2, 0xFF69708B},
			{0xF0120B08, 0xEB21120D, 0xDC2B1810, 0xEB392116, 0xFF66402A, 0xFFFF8C42, 0xFFFFB35C, 0xFF8FDB76, 0xFFFF635D, 0xFFFFD166, 0xFFFFF4E8, 0xFFC6A993, 0xFF846A59},
			{0xF0050D0A, 0xEB0A1912, 0xDC10241A, 0xEB173326, 0xFF28543E, 0xFF55B98A, 0xFF72E6AC, 0xFF7DFF9B, 0xFFFF7272, 0xFFE8C96A, 0xFFF0FFF7, 0xFF9CC2AD, 0xFF648272},
			{0xF00A0911, 0xEB151220, 0xDC1B1728, 0xEB29213B, 0xFF4C4165, rainbow, rainbow, 0xFF70FF9A, 0xFFFF708B, rainbow, 0xFFFFFFFF, 0xFFBBB4CC, 0xFF776F89},
			{0xF00D0813, 0xEB1A1026, 0xDC251735, 0xEB322047, 0xFF56376F, 0xFFB66DFF, 0xFFD69BFF, 0xFF7DFFB3, 0xFFFF719E, 0xFFE5B8FF, 0xFFFFF5FF, 0xFFC4A9D2, 0xFF806B8D},
			{0xF0040E15, 0xEB071B27, 0xDC0B2635, 0xEB103548, 0xFF20566D, 0xFF39A9DB, 0xFF61D8FF, 0xFF67F2C0, 0xFFFF738C, 0xFF8DE8FF, 0xFFF0FBFF, 0xFF9CBECB, 0xFF607E8B},
			{0xF016070B, 0xEB2B0C12, 0xDC40101A, 0xEB571623, 0xFF8A263B, 0xFFFF3E68, 0xFFFF6F8F, 0xFF6FE39A, 0xFFFF4359, 0xFFFFA14A, 0xFFFFF1F3, 0xFFD4A0A8, 0xFF8B5A64},
			{0xF0071017, 0xEB0D202C, 0xDC13303F, 0xEB1A4154, 0xFF39748B, 0xFF7DDBFF, 0xFFB0ECFF, 0xFF8DFFD5, 0xFFFF8298, 0xFFD6F5FF, 0xFFF4FCFF, 0xFFA9CAD6, 0xFF6B8994},
			{0xF0140B13, 0xEB271424, 0xDC361B32, 0xEB482443, 0xFF723C68, 0xFFFF82B2, 0xFFFFC0D9, 0xFF88E8B1, 0xFFFF718A, 0xFFDDB7FF, 0xFFFFF6FA, 0xFFD1AEBE, 0xFF896D7A},
			{0xF0150804, 0xEB2B1008, 0xDC3D170B, 0xEB522012, 0xFF813A1D, 0xFFFF6B2C, 0xFFFF9D52, 0xFF9DE06F, 0xFFFF554F, 0xFFFFC15A, 0xFFFFF3E8, 0xFFD0A088, 0xFF8C6551},
			{0xF00B0613, 0xEB170C29, 0xDC23123B, 0xEB301950, 0xFF593181, 0xFFA85CFF, 0xFFD190FF, 0xFF67ECA2, 0xFFFF638D, 0xFFE3AEFF, 0xFFFCF4FF, 0xFFBFA7CF, 0xFF79668A},
			{0xF0120E05, 0xEB271D08, 0xDC382A0C, 0xEB4A3811, 0xFF795E26, 0xFFFFC642, 0xFFFFDD76, 0xFF9BE58C, 0xFFFF6D64, 0xFFFFE09A, 0xFFFFF9E8, 0xFFD1C29A, 0xFF887C5C},
			{0xF00B0B0B, 0xEB171717, 0xDC242424, 0xEB323232, 0xFF555555, 0xFFBDBDBD, 0xFFE0E0E0, 0xFF9AD5AC, 0xFFFF7B7B, 0xFFF2F2F2, 0xFFFFFFFF, 0xFFB8B8B8, 0xFF777777},
			{0xF0080714, 0xEB121027, 0xDC1B1839, 0xEB27214E, 0xFF4A3D78, 0xFF776BFF, 0xFFC06CFF, 0xFF64E7B0, 0xFFFF668F, 0xFFFF9BDC, 0xFFF8F4FF, 0xFFB2A8CA, 0xFF716886},
			{0xF00A0D11, 0xEB141A21, 0xDC202934, 0xEB2C3845, 0xFF4B5D70, 0xFF7A9AB8, 0xFFA9C3D9, 0xFF85D6A7, 0xFFFF7C83, 0xFFC7D8E5, 0xFFF4F7FA, 0xFFAAB5BF, 0xFF6F7B86},
			{0xF0041020, 0xEB071A31, 0xDC0B2645, 0xEB10345B, 0xFF23659B, 0xFF41A7FF, 0xFF83CEFF, 0xFF5DE3B2, 0xFFFF6E7E, 0xFFFFD166, 0xFFF3FAFF, 0xFF9CBAD0, 0xFF58768D},
			{0xF0140D08, 0xEB25170F, 0xDC362117, 0xEB493022, 0xFF75513A, 0xFFC98A55, 0xFFE9B778, 0xFF9BCB75, 0xFFE96A58, 0xFFF1CF8A, 0xFFFFF4E5, 0xFFC9AE94, 0xFF88705D},
			{0xF00F0615, 0xEB1E0A2B, 0xDC30103F, 0xEB461657, 0xFF7B258A, 0xFFFF2DCB, 0xFF20F6FF, 0xFF5DFF78, 0xFFFF4B3E, 0xFFFFFF38, 0xFFFFFFFF, 0xFFC9A5D2, 0xFF835B8F},
			{0xF0171005, 0xEB2A200B, 0xDC3C3012, 0xEB51421C, 0xFF806B35, 0xFFE5A944, 0xFFF1D07A, 0xFF8BCB72, 0xFFD95B4C, 0xFFF6C85F, 0xFFFFF5D8, 0xFFCAB98A, 0xFF86774F},
			{0xF004100D, 0xEB071F19, 0xDC0B3026, 0xEB104335, 0xFF24735B, 0xFF24C98A, 0xFF72E8B9, 0xFF8FFF76, 0xFFFF6B63, 0xFFFFD05A, 0xFFF1FFF8, 0xFF9CC8B3, 0xFF5D8873},
			{0xF0EEE8D8, 0xEBE2DAC8, 0xD9D4CBB7, 0xEBC6BBA4, 0xFF9B8F76, 0xFF315A91, 0xFF287E8A, 0xFF39754A, 0xFFB43D3D, 0xFF996515, 0xFF25231F, 0xFF625E55, 0xFF8B8477},
			{0xF0002B36, 0xEB073642, 0xDC0D414D, 0xEB164D58, 0xFF526C72, 0xFF268BD2, 0xFF2AA198, 0xFF859900, 0xFFDC322F, 0xFFB58900, 0xFFFDF6E3, 0xFF93A1A1, 0xFF657B83},
			{0xF0010803, 0xEB031207, 0xDC061D0B, 0xEB092A10, 0xFF155C27, 0xFF20C95A, 0xFF62F58C, 0xFF8CFF63, 0xFFFF5D57, 0xFFE8F35A, 0xFFE8FFE9, 0xFF8FC49A, 0xFF50795A},
			{0xF0040B18, 0xEB09172A, 0xDC10243C, 0xEB183552, 0xFF315F7E, 0xFF47C7B1, 0xFF85F0D1, 0xFF9BFF88, 0xFFFF719C, 0xFFB58CFF, 0xFFF1FFFC, 0xFF9EC7C2, 0xFF5F827F},
			{0xF0180B18, 0xEB2A112A, 0xDC3A183B, 0xEB502451, 0xFF834A82, 0xFFFF71C8, 0xFF70E8FF, 0xFF8CFFBA, 0xFFFF6F91, 0xFFFFD36E, 0xFFFFF4FC, 0xFFD7A9CA, 0xFF916B8A},
			{0xF0140B06, 0xEB26150D, 0xDC382016, 0xEB4C2D20, 0xFF79503C, 0xFFC97845, 0xFF4FC1B2, 0xFF8BD47C, 0xFFE85D4C, 0xFFE6A85C, 0xFFFFF1E4, 0xFFC6A58D, 0xFF846A58},
			{0xF0010710, 0xEB031322, 0xDC071F34, 0xEB0B2D49, 0xFF174E70, 0xFF168AAD, 0xFF39E4E8, 0xFF6FE8A5, 0xFFFF627D, 0xFF69C8FF, 0xFFE9FFFF, 0xFF87B8C4, 0xFF4C7888},
			{0xF00D0B15, 0xEB171526, 0xDC22213A, 0xEB302F50, 0xFF535485, 0xFF63B8FF, 0xFFFF7A45, 0xFF7FE4A1, 0xFFFF5665, 0xFFFFB15C, 0xFFF5F7FF, 0xFFAAAEC7, 0xFF6B708C},
			{0xF0120D1A, 0xEB21182D, 0xDC302440, 0xEB433459, 0xFF705D8A, 0xFFA98ADB, 0xFFD3B8FF, 0xFF8BDBAA, 0xFFE87589, 0xFFF0C77D, 0xFFFFF8FF, 0xFFC8B7D2, 0xFF877890},
			{0xF0010602, 0xEB031005, 0xDC061A09, 0xEB09260D, 0xFF174D22, 0xFF18A83E, 0xFF4DFF76, 0xFF82FF72, 0xFFFF5E5E, 0xFFC8FF4D, 0xFFE8FFE9, 0xFF8AC397, 0xFF4D7857},
			{0xF00A0617, 0xEB140C2B, 0xDC211342, 0xEB301D5A, 0xFF563A89, 0xFF7958C8, 0xFFFFC857, 0xFF78D69B, 0xFFFF607A, 0xFFFFD77A, 0xFFFFF7E8, 0xFFBFB0D0, 0xFF796A8E},
			{0xF0150817, 0xEB28102A, 0xDC3B183B, 0xEB52234F, 0xFF84406F, 0xFFFF5A91, 0xFFFF9D70, 0xFF88DF9D, 0xFFFF5656, 0xFFFFC266, 0xFFFFF1E8, 0xFFD3A3B2, 0xFF8C6575},
			{0xF0090719, 0xEB130E2D, 0xDC211642, 0xEB32205B, 0xFF5E3C8A, 0xFFFF4FD8, 0xFF44E8FF, 0xFF69F3A5, 0xFFFF6F80, 0xFFFFD04A, 0xFFFFFFFF, 0xFFC0A7D4, 0xFF7B628F}
		};
		themePalettes[4][5] = rainbow;
		themePalettes[4][6] = rainbow;
		themePalettes[4][9] = rainbow;
		int[] value = id == 34 ? customThemePalette() : themePalettes[id];
		BACKGROUND = value[0]; SURFACE = value[1]; CARD = value[2]; CARD_HOVER = value[3];
		BORDER = value[4]; BLUE = value[5]; CYAN = value[6]; GREEN = value[7]; RED = value[8];
		GOLD = value[9]; TEXT = value[10]; MUTED = value[11]; DIM = value[12];
		SELECTED = id == 34 ? value[13] : blendOpaque(CARD, BLUE, 0.38f);
		SUB_SELECTED = id == 34 ? value[14] : blendOpaque(CARD, GOLD, 0.30f);
		if (SpecialTheme.rainbow()) {
			// Keep surfaces dark and readable while every semantic/accent role follows
			// the same cached rainbow clock used by text, borders, and HUDs.
			BLUE = SpecialTheme.accent(0);
			CYAN = SpecialTheme.accent(18);
			GREEN = SpecialTheme.accent(36);
			RED = SpecialTheme.accent(54);
			GOLD = SpecialTheme.accent(72);
			BORDER = blendOpaque(CARD, SpecialTheme.accent(9), 0.58f);
			CARD_HOVER = blendOpaque(CARD, SpecialTheme.accent(27), 0.20f);
			SELECTED = blendOpaque(CARD, SpecialTheme.accent(45), 0.38f);
			SUB_SELECTED = blendOpaque(CARD, SpecialTheme.accent(63), 0.30f);
		}
	}

	/** Active settings palette, shared by the standalone HUD editor. */
	static int[] activeThemePalette() {
		int rainbow = UIDraw.rainbowAt(0, 0.66f);
		if (themePalettes == null) {
			// Initialize through the normal path so the palette has one source of truth.
			new SafariSettingsScreen(null).applyTheme();
		}
		themePalettes[4][5] = rainbow;
		themePalettes[4][6] = rainbow;
		themePalettes[4][9] = rainbow;
		int id = Math.clamp(ConfigManager.get().display.settingsTheme, 0, 34);
		return id == 34 ? customThemePalette() : themePalettes[id];
	}

	private static int[] customThemePalette() {
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		int hash = 1;
		hash = 31 * hash + java.util.Objects.hashCode(display.customThemeBackground);
		hash = 31 * hash + java.util.Objects.hashCode(display.customThemeSurface);
		hash = 31 * hash + java.util.Objects.hashCode(display.customThemeCard);
		hash = 31 * hash + java.util.Objects.hashCode(display.customThemeCardHover);
		hash = 31 * hash + java.util.Objects.hashCode(display.customThemeSelected);
		hash = 31 * hash + java.util.Objects.hashCode(display.customThemeSubSelected);
		hash = 31 * hash + java.util.Objects.hashCode(display.customThemeBorder);
		hash = 31 * hash + java.util.Objects.hashCode(display.customThemePrimary);
		hash = 31 * hash + java.util.Objects.hashCode(display.customThemeSecondary);
		hash = 31 * hash + java.util.Objects.hashCode(display.customThemeSuccess);
		hash = 31 * hash + java.util.Objects.hashCode(display.customThemeError);
		hash = 31 * hash + java.util.Objects.hashCode(display.customThemeHighlight);
		hash = 31 * hash + java.util.Objects.hashCode(display.customThemeText);
		hash = 31 * hash + java.util.Objects.hashCode(display.customThemeMuted);
		hash = 31 * hash + java.util.Objects.hashCode(display.customThemeDim);
		if (customThemeCache != null && hash == customThemeHash) return customThemeCache;
		int[] fallback = themePalettes[0];
		customThemeHash = hash;
		customThemeCache = new int[] {
			Colours.argb(display.customThemeBackground, fallback[0]),
			Colours.argb(display.customThemeSurface, fallback[1]),
			Colours.argb(display.customThemeCard, fallback[2]),
			Colours.argb(display.customThemeCardHover, fallback[3]),
			Colours.argb(display.customThemeBorder, fallback[4]),
			Colours.argb(display.customThemePrimary, fallback[5]),
			Colours.argb(display.customThemeSecondary, fallback[6]),
			Colours.argb(display.customThemeSuccess, fallback[7]),
			Colours.argb(display.customThemeError, fallback[8]),
			Colours.argb(display.customThemeHighlight, fallback[9]),
			Colours.argb(display.customThemeText, fallback[10]),
			Colours.argb(display.customThemeMuted, fallback[11]),
			Colours.argb(display.customThemeDim, fallback[12]),
			Colours.argb(display.customThemeSelected, blendOpaque(fallback[2], fallback[5], 0.38f)),
			Colours.argb(display.customThemeSubSelected, blendOpaque(fallback[2], fallback[9], 0.30f))
		};
		return customThemeCache;
	}

	private void drawBrand(GuiGraphicsExtractor graphics) {
		graphics.fillGradient(0, 0, navWidth - 1, HEADER_HEIGHT - 1, SURFACE, CARD);
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		int safariColour = display.settingsTheme == 34
			? Colours.argb(display.customThemeSafariTitle, CYAN) : CYAN;
		int utilsColour = display.settingsTheme == 34
			? Colours.argb(display.customThemeUtilsTitle, GOLD) : GOLD;
		Component safariTitle = Component.literal("SAFARI ").withStyle(style -> style.withBold(true));
		Component utilsTitle = Component.literal("UTILS").withStyle(style -> style.withBold(true));
		int titleWidth = font.width(safariTitle) + font.width(utilsTitle);
		float titleScale = Math.min(navWidth < 106 ? 1.18f : 1.52f,
			(navWidth - 8f) / Math.max(1, titleWidth));
		Component version = Component.literal("VERSION " + MOD_VERSION + BuildVersion.titleSuffix())
			.withStyle(style -> style.withBold(true));
		float versionScale = Math.min(1.0f,
			(navWidth - 8f) / Math.max(1, font.width(version)));
		int titleHeight = Math.round(font.lineHeight * titleScale);
		int versionHeight = Math.round(font.lineHeight * versionScale);
		int blockTop = Math.max(0,
			(HEADER_HEIGHT - 1 - titleHeight - versionHeight - BRAND_LINE_GAP) / 2 + 1);
		int titleLeft = Math.round(navWidth / 2f / titleScale - titleWidth / 2f);
		graphics.pose().pushMatrix();
		graphics.pose().scale(titleScale, titleScale);
		drawText(graphics, safariTitle, titleLeft, Math.round(blockTop / titleScale), safariColour);
		drawText(graphics, utilsTitle, titleLeft + font.width(safariTitle), Math.round(blockTop / titleScale), utilsColour);
		graphics.pose().popMatrix();
		drawScaledCenteredText(graphics, version, navWidth / 2,
			blockTop + titleHeight + BRAND_LINE_GAP, versionScale, MUTED);
	}

	/** Header-only theme picker kept out of Display's ordinary setting cards. */
	private void drawThemeControl(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = width - ResponsiveUI.gutter(width) - DATA_BUTTON_SIZE
			- SETTINGS_GAP - themeButtonWidth;
		int y = (HEADER_HEIGHT - 28) / 2 - 1;
		boolean hovered = inside(mouseX, mouseY, x, y, x + themeButtonWidth, y + 28);
		graphics.fillGradient(x, y, x + themeButtonWidth, y + 28,
			hovered ? SELECTED : CARD, hovered ? SUB_SELECTED : SURFACE);
		outline(graphics, x, y, themeButtonWidth, 28, hovered ? CYAN : GOLD);
		drawDiamond(graphics, x + 13, y + 14, 5, ConfigManager.get().display.settingsTheme == 4 ? CYAN : GOLD);
		if (themeButtonWidth >= 100) {
			drawText(graphics, "THEME", x + 24, y + 5, DIM);
			drawText(graphics, currentThemeLabel(), x + 24, y + 16, hovered ? TEXT : MUTED);
		} else drawText(graphics, trim(currentThemeLabel(), themeButtonWidth - 30), x + 24, y + 10,
			hovered ? TEXT : MUTED);
		hits.add(new Hit(x, y, x + themeButtonWidth, y + 28, this::openThemePicker));
	}

	private void drawDataControl(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = width - ResponsiveUI.gutter(width) - DATA_BUTTON_SIZE;
		int y = (HEADER_HEIGHT - DATA_BUTTON_SIZE) / 2 - 1;
		boolean hovered = inside(mouseX, mouseY, x, y,
			x + DATA_BUTTON_SIZE, y + DATA_BUTTON_SIZE);
		graphics.fillGradient(x, y, x + DATA_BUTTON_SIZE, y + DATA_BUTTON_SIZE,
			hovered ? SELECTED : CARD, hovered ? SUB_SELECTED : SURFACE);
		outline(graphics, x, y, DATA_BUTTON_SIZE, DATA_BUTTON_SIZE, hovered ? CYAN : BORDER);
		Component icon = Component.literal("⚙").withStyle(style -> style.withBold(true));
		float iconScale = 1.45f;
		float iconWidth = font.width(icon) * iconScale;
		float iconHeight = (font.lineHeight - 1) * iconScale;
		drawScaledText(graphics, icon,
			x + (DATA_BUTTON_SIZE - iconWidth) / 2f + 1,
			y + (DATA_BUTTON_SIZE - iconHeight) / 2f - 1,
			iconScale, hovered ? TEXT : MUTED);
		hits.add(new Hit(x, y, x + DATA_BUTTON_SIZE, y + DATA_BUTTON_SIZE,
			DataToolsScreen::open));
	}

	private String currentThemeLabel() {
		int current = ConfigManager.get().display.settingsTheme;
		for (ThemeChoice theme : THEMES) {
			if (theme.id == current) return theme.label;
		}
		return "Default";
	}

	private void openThemePicker() {
		try {
			Field field = SafariConfig.DisplayConfig.class.getField("settingsTheme");
			openChoicePicker(ConfigManager.get().display, field,
				field.getAnnotation(SettingChoice.class));
		} catch (NoSuchFieldException ignored) {
		}
	}

	private void drawNavigation(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int top = HEADER_HEIGHT;
		int bottom = height - FOOTER_HEIGHT;
		int y = HEADER_HEIGHT + 8 - navigationScroll;
		graphics.enableScissor(0, top, navWidth, bottom);
		for (SettingsArea area : settingsAreas) {
			boolean active = area == selectedArea;
			boolean hovered = mouseX >= 0 && mouseX < navWidth && mouseY >= y && mouseY < y + 32;
			if (active || hovered) {
				graphics.fill(0, y, navWidth - 1, y + 32, active ? SELECTED : CARD_HOVER);
				if (active) graphics.fill(0, y, 3, y + 32, CYAN);
			}
			Component categoryTitle = Component.literal(trim(area.name, navWidth - 20))
				.withStyle(style -> style.withBold(true));
			drawText(graphics, categoryTitle, 12,
				y + (32 - font.lineHeight) / 2 + 1, active ? TEXT : MUTED);
			int rowY = y;
			if (rowY + 32 > top && rowY < bottom) {
				hits.add(new Hit(0, Math.max(rowY, top), navWidth,
					Math.min(rowY + 32, bottom), () -> selectArea(area)));
			}
			y += 36;
		}

		if (!AdvancedUnlock.isUnlocked()) {
			int lockY = y + 8;
			boolean hovered = mouseX >= 0 && mouseX < navWidth && mouseY >= lockY && mouseY < lockY + 32;
			if (hovered) graphics.fill(0, lockY, navWidth - 1, lockY + 32, CARD_HOVER);
			drawText(graphics, "◇  Locked", 12,
				lockY + (32 - font.lineHeight) / 2, hovered ? GOLD : DIM);
			if (lockY + 32 > top && lockY < bottom) {
				hits.add(new Hit(0, Math.max(lockY, top), navWidth,
					Math.min(lockY + 32, bottom), this::openUnlockPanel));
			}
			y = lockY + 32;
		}
		navigationContentHeight = Math.max(0, y + navigationScroll - (HEADER_HEIGHT + 8));
		graphics.disableScissor();
		drawUpdateStatus(graphics, mouseX, mouseY);
	}

	private void drawUpdateStatus(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		UpdateChecker.Status status = UpdateChecker.status();
		if (status == UpdateChecker.Status.NOT_STARTED
				|| status == UpdateChecker.Status.UNAVAILABLE) return;
		int rowY = height - FOOTER_HEIGHT;
		String symbol;
		String label;
		int colour;
		if (status == UpdateChecker.Status.AVAILABLE) {
			symbol = "↑";
			label = "  Update " + UpdateChecker.availableVersion();
			colour = GOLD;
		} else if (status == UpdateChecker.Status.CURRENT) {
			symbol = "✓";
			label = "  Up to date";
			colour = GREEN;
		} else {
			symbol = "◇";
			label = "  Checking updates";
			colour = DIM;
		}
		Component statusText = Component.literal(symbol)
			.withStyle(style -> style.withBold(true))
			.append(Component.literal(label));
		boolean hovered = inside(mouseX, mouseY, 0, rowY, navWidth, height);
		graphics.fill(0, rowY, navWidth - 1, height, hovered ? CARD_HOVER : CARD);
		graphics.fill(0, rowY, 3, height, colour);
		drawText(graphics, statusText, 12,
			rowY + (FOOTER_HEIGHT - font.lineHeight) / 2 + 2, colour);
		hits.add(new Hit(0, rowY, navWidth, height,
			UpdateDetailsScreen::open));
	}

	private void drawCategoryHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		contentHeaderBottom = HEADER_HEIGHT;
		if (selectedArea == null || selectedTab == null) return;
		int gutter = ResponsiveUI.gutter(width);
		int left = workspaceLeft + gutter;
		int right = width - gutter;
		int tabsY = HEADER_HEIGHT + 8;
		int tabX = left;
		int tabY = tabsY;
		for (SettingsTab tab : selectedArea.tabs) {
			if (!tab.selectable()) continue;
			int tabWidth = settingsTabWidth(tab, left, right);
			if (tabX > left && tabX + tabWidth > right) {
				tabX = left;
				tabY += 27;
			}
			tabX += tabWidth + 6;
		}
		contentHeaderBottom = Math.min(height - FOOTER_HEIGHT - 40, tabY + 31);
		graphics.fill(workspaceLeft, HEADER_HEIGHT, width, contentHeaderBottom, SURFACE);
		graphics.fill(workspaceLeft, contentHeaderBottom - 1, width, contentHeaderBottom, BORDER);
		tabX = left;
		tabY = tabsY;
		for (SettingsTab tab : selectedArea.tabs) {
			if (!tab.selectable()) continue;
			int tabWidth = settingsTabWidth(tab, left, right);
			if (tabX > left && tabX + tabWidth > right) {
				tabX = left;
				tabY += 27;
			}
			boolean active = tab == selectedTab;
			boolean hovered = inside(mouseX, mouseY, tabX, tabY, tabX + tabWidth, tabY + 22);
			graphics.fill(tabX, tabY, tabX + tabWidth, tabY + 22,
				active ? SUB_SELECTED : hovered ? CARD_HOVER : CARD);
			outline(graphics, tabX, tabY, tabWidth, 22, active ? GOLD : BORDER);
			drawCenteredText(graphics, trim(tab.name, tabWidth - 12),
				tabX + tabWidth / 2, tabY + 7,
				active ? TEXT : MUTED);
			int hitX = tabX;
			hits.add(new Hit(hitX, tabY, hitX + tabWidth, tabY + 22, () -> selectTab(tab)));
			tabX += tabWidth + 6;
		}
	}

	private int settingsTabWidth(SettingsTab tab, int left, int right) {
		return Math.min(right - left,
			Math.max(66, Math.min(180, font.width(tab.name) + 24)));
	}

	private void openUnlockPanel() {
		unlockPanel = true;
		setFocused(null);
		if (search != null) search.visible = false;
	}

	private void closeUnlockPanel() {
		unlockPanel = false;
		unlockProgress = 0;
		signalCompletedAt = 0;
		if (search != null) {
			search.visible = true;
			search.active = true;
		}
	}

	private void selectArea(SettingsArea area) {
		if (selectedArea == area) return;
		selectedArea = area;
		selectedTab = null;
		expandedSettingSections.clear();
		ensureSelectedTab();
		scroll = 0;
		resetArmedUntil = 0;
		search.setValue("");
	}

	private void selectTab(SettingsTab tab) {
		if (!tab.selectable()) return;
		if (selectedTab == tab) return;
		selectedTab = tab;
		expandedSettingSections.clear();
		scroll = 0;
		resetArmedUntil = 0;
		if (search != null) search.setValue("");
	}

	private void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (selectedArea == null || selectedTab == null) return;
		int gutter = ResponsiveUI.gutter(width);
		int left = workspaceLeft + gutter;
		int right = width - gutter;
		int top = contentHeaderBottom + 8;
		int bottom = height - FOOTER_HEIGHT - 8;
		contentViewportTop = top;
		contentViewportBottom = bottom;
		contentScissor = new ScreenRectangle(left, top, right - left, bottom - top);
		graphics.enableScissor(left, top, right, bottom);
		int y = top - scroll;
		String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
		if (query.isEmpty()) {
			y = drawTabPages(graphics, selectedTab.pages, left, right, y, mouseX, mouseY);
		} else {
			y = drawSearchResults(graphics, query, left, right, y, mouseX, mouseY);
		}
		contentHeight = Math.max(0, y + scroll - top);
		graphics.disableScissor();
		contentScissor = null;
	}

	private int drawTabPages(GuiGraphicsExtractor graphics, List<SettingsPage> pages,
			int left, int right, int y, int mouseX, int mouseY) {
		if (pages.isEmpty()) return y;
		Map<SettingsPage, Integer> effectiveDepths = effectiveDepthCache.computeIfAbsent(
			selectedTab, ignored -> calculateEffectiveDepths(pages));
		int rootCount = 0;
		SettingsPage root = null;
		for (SettingsPage page : pages) {
			if (effectiveDepths.getOrDefault(page, 0) != 0) continue;
			rootCount++;
			root = page;
		}
		SettingsPage hiddenRoot = rootCount == 1 && root != null && !root.selectable() ? root : null;
		java.util.Arrays.fill(visibleSettingBranches, true);
		for (int index = 0; index < pages.size(); index++) {
			SettingsPage page = pages.get(index);
			int depth = effectiveDepths.getOrDefault(page, 0);
			if (hiddenRoot != null && page != hiddenRoot
					&& page.breadcrumb.startsWith(hiddenRoot.breadcrumb + "  ›  ")) {
				depth = Math.max(0, depth - 1);
			}
			final int layoutDepth = depth;
			java.util.Arrays.fill(visibleSettingBranches,
				Math.min(layoutDepth, visibleSettingBranches.length),
				visibleSettingBranches.length, true);
			boolean parentVisible = true;
			for (int branchDepth = 0;
					branchDepth < Math.min(layoutDepth, visibleSettingBranches.length);
					branchDepth++) {
				if (!visibleSettingBranches[branchDepth]) {
					parentVisible = false;
					break;
				}
			}
			boolean hasChildren = index + 1 < pages.size() && pages.get(index + 1).depth > page.depth;
			if (!parentVisible) {
				if (depth < visibleSettingBranches.length) visibleSettingBranches[depth] = false;
				continue;
			}

			boolean structuralRoot = page == hiddenRoot;
			boolean direct = structuralRoot || isDirectSettingsPage(page, hasChildren);
			if (direct) {
				int settingLeft = left + Math.min(18, depth * 6);
				y = drawPageSettings(graphics, page, settingLeft, right, y, mouseX, mouseY);
				if (depth < visibleSettingBranches.length) visibleSettingBranches[depth] = true;
				continue;
			}

			boolean expanded = expandedSettingSections.contains(page.key);
			int sectionLeft = left + Math.min(18, depth * 6);
			y = drawCollapsibleSection(graphics, page, sectionLeft, right, y,
				mouseX, mouseY, expanded);
			if (expanded) {
				int settingLeft = Math.min(right - 40, sectionLeft + 6);
				y = drawPageSettings(graphics, page, settingLeft, right, y, mouseX, mouseY);
			}
			if (depth < visibleSettingBranches.length) visibleSettingBranches[depth] = expanded;
		}
		return y;
	}

	private boolean isDirectSettingsPage(SettingsPage page, boolean hasChildren) {
		if (page.key.equals("advanced.safe-mode.sparklings")) return true;
		if (page.key.startsWith("advanced.safe-mode.")) return false;
		if (page.key.startsWith("display.waypoint-settings")) return false;
		if (!page.key.startsWith("dev.")) return true;
		if (!hasChildren && page.fields.size() == 1) return true;
		String pageName = compactSectionName(page.name).replace(" Alerts", "");
		String tabName = selectedTab == null ? "" : selectedTab.name.replace(" Alerts", "");
		return pageName.equalsIgnoreCase(tabName);
	}

	private static Map<SettingsPage, Integer> calculateEffectiveDepths(List<SettingsPage> pages) {
		Map<SettingsPage, Integer> result = new java.util.IdentityHashMap<>();
		for (SettingsPage page : pages) {
			int depth = 0;
			for (SettingsPage candidate : pages) {
				if (candidate == page || candidate.depth >= page.depth) continue;
				if (page.breadcrumb.startsWith(candidate.breadcrumb + "  ›  ")) depth++;
			}
			result.put(page, depth);
		}
		return result;
	}

	private int drawCollapsibleSection(GuiGraphicsExtractor graphics, SettingsPage page,
			int left, int right, int y, int mouseX, int mouseY, boolean expanded) {
		int height = 22;
		boolean hovered = inside(mouseX, mouseY, left, y, right, y + height);
		if (y + height > contentViewportTop && y < contentViewportBottom) {
			int accent = page.depth % 2 == 0 ? GOLD : CYAN;
			graphics.fill(left, y, right, y + height, hovered ? CARD_HOVER : SURFACE);
			graphics.fill(left, y, left + 3, y + height, accent);
			drawText(graphics, trim(compactSectionName(page.name), right - left - 34),
				left + 10, y + 7, TEXT);
			drawCenteredText(graphics, expanded ? "−" : "+", right - 12, y + 7,
				expanded ? GOLD : MUTED);
			int hitTop = Math.max(y, contentViewportTop);
			int hitBottom = Math.min(y + height, contentViewportBottom);
			if (hitBottom > hitTop) {
				hits.add(new Hit(left, hitTop, right, hitBottom,
					() -> toggleSettingSection(page.key)));
			}
		}
		return y + height + SETTINGS_GAP;
	}

	private void toggleSettingSection(String key) {
		if (!expandedSettingSections.add(key)) {
			expandedSettingSections.remove(key);
			collapseChildSections(key);
		}
		resetArmedUntil = 0;
	}

	private void collapseChildSections(String parentKey) {
		if (selectedTab == null) return;
		SettingsPage parent = selectedTab.pages.stream()
			.filter(page -> page.key.equals(parentKey))
			.findFirst().orElse(null);
		if (parent == null) return;
		String childPrefix = parent.breadcrumb + "  ›  ";
		for (SettingsPage page : selectedTab.pages) {
			if (page.breadcrumb.startsWith(childPrefix)) {
				expandedSettingSections.remove(page.key);
			}
		}
	}

	private int drawPageSettings(GuiGraphicsExtractor graphics, SettingsPage page,
			int left, int right, int y, int mouseX, int mouseY) {
		if (page.fields.isEmpty()) return y;
		for (List<Field> card : settingCardCache.computeIfAbsent(page, this::settingCards)) {
			if (card.size() == 1) {
				Field field = card.getFirst();
				y = drawSetting(graphics, page.owner, field,
					field.getAnnotation(SettingInfo.class), left, right, y, mouseX, mouseY);
			} else {
				y = drawCombinedSettings(graphics, page.owner, card,
					left, right, y, mouseX, mouseY);
			}
		}
		return y;
	}

	private List<List<Field>> settingCards(SettingsPage page) {
		if (page.key.equals("display.waypoint-settings")) {
			return List.of(page.fields);
		}
		if (page.key.equals("display.waypoint-settings.objectives")
				|| page.key.equals("display.waypoint-settings.critters")) {
			return featureColourCards(page.fields);
		}
		return List.of(page.fields);
	}

	private List<List<Field>> featureColourCards(List<Field> fields) {
		List<List<Field>> cards = new ArrayList<>();
		for (int index = 0; index < fields.size(); index++) {
			Field field = fields.get(index);
			List<Field> card = new ArrayList<>();
			card.add(field);
			if (field.isAnnotationPresent(SettingToggle.class)) {
				while (index + 1 < fields.size()
						&& fields.get(index + 1).isAnnotationPresent(SettingColor.class)) {
					card.add(fields.get(++index));
				}
			}
			cards.add(List.copyOf(card));
		}
		return cards;
	}

	private static String compactSectionName(String name) {
		return switch (name) {
			case "Safari Alerts", "Safari Chat Alerts" -> "Safari";
			case "Encounter Alerts", "Encounter Chat Alerts" -> "Encounters";
			case "Contest Alerts", "Contest Chat Alerts" -> "Contest";
			case "Appearance Settings" -> "Appearance";
			case "Sound Settings" -> "Sound";
			case "Progress HUD Options", "Missing HUD Options", "Contest HUD Options",
				"Party Objective HUD Options", "Safe Mode Options" -> "Options";
			case "Data Collecting" -> "Data Collection";
			default -> name;
		};
	}

	private int drawSearchResults(GuiGraphicsExtractor graphics, String query,
			int left, int right, int y, int mouseX, int mouseY) {
		if (!query.equals(cachedSearchQuery)) {
			List<SearchItem> results = new ArrayList<>();
			for (SettingCategoryView category : categories) {
				collectSearchResults(category.value, category.value.getClass(), null, List.of(),
					query, false, category.info.name(), results);
			}
			results.sort((first, second) -> Integer.compare(
				settingOrderCache.getOrDefault(first.field, Integer.MAX_VALUE),
				settingOrderCache.getOrDefault(second.field, Integer.MAX_VALUE)));
			cachedSearchQuery = query;
			cachedSearchGroups = buildSearchGroups(results);
		}
		String previousContext = null;
		for (SearchGroup group : cachedSearchGroups) {
			String context = group.context;
			if (!context.equals(previousContext) && !context.isBlank()) {
				y = drawSearchContext(graphics, context, left, right, y);
				previousContext = context;
			}
			if (group.fields.size() > 1) {
				y = drawCombinedSettings(graphics, group.owner, group.fields,
					left + 5, right, y, mouseX, mouseY);
			} else {
				Field field = group.fields.getFirst();
				y = drawSetting(graphics, group.owner, field,
					field.getAnnotation(SettingInfo.class),
					left + 5, right, y, mouseX, mouseY);
			}
		}
		if (cachedSearchGroups.isEmpty()) {
			drawCenteredText(graphics, "No matching settings", (left + right) / 2, y + 30, MUTED);
			y += 70;
		}
		return y;
	}

	private List<SearchGroup> buildSearchGroups(List<SearchItem> results) {
		List<SearchGroup> groups = new ArrayList<>();
		for (int index = 0; index < results.size();) {
			SearchItem first = results.get(index);
			List<Field> normalCard = normalCard(first);
			List<Field> matches = new ArrayList<>();
			matches.add(first.field);
			int next = index + 1;
			while (next < results.size()) {
				SearchItem candidate = results.get(next);
				if (candidate.owner != first.owner || !candidate.context.equals(first.context)
						|| normalCard(candidate) != normalCard) break;
				matches.add(candidate.field);
				next++;
			}
			groups.add(new SearchGroup(first.context, first.owner, List.copyOf(matches)));
			index = next;
		}
		return List.copyOf(groups);
	}

	private List<Field> normalCard(SearchItem item) {
		List<Field> card = normalSettingCardCache.get(item.field);
		return card == null ? List.of(item.field) : card;
	}

	private void collectSearchResults(Object owner, Class<?> type, Integer parentId,
			List<SettingInfo> context, String query, boolean ancestorMatches,
			String category, List<SearchItem> results) {
		for (Field field : publicFields(type)) {
			SettingInfo option = field.getAnnotation(SettingInfo.class);
			if (option == null || isHeaderOnly(field) || !belongsTo(field, parentId)) continue;
			boolean matches = smartMatches(option, query);
			SettingSection accordion = field.getAnnotation(SettingSection.class);
			if (accordion != null) {
				List<SettingInfo> nested = new ArrayList<>(context);
				nested.add(option);
				collectSearchResults(owner, type, accordion.id(), nested, query,
					ancestorMatches || matches, category, results);
			} else if (hasEditor(field) && (ancestorMatches || matches)) {
				String mappedContext = navigationContext(owner, field);
				StringBuilder path = new StringBuilder(mappedContext == null
					? displayName(category) : mappedContext);
				if (mappedContext == null) {
					for (SettingInfo level : context) {
						path.append("  ›  ").append(displayName(level.name()));
					}
				}
				results.add(new SearchItem(owner, field, path.toString()));
			}
		}
	}

	private String navigationContext(Object owner, Field field) {
		for (SettingsArea area : settingsAreas) {
			for (SettingsPage page : area.pages) {
				if (page.owner != owner || !page.fields.contains(field)) continue;
				List<List<Field>> cards = settingCardCache.computeIfAbsent(page, this::settingCards);
				if (cards.size() <= 1) return page.breadcrumb;
				for (List<Field> card : cards) {
					if (!card.contains(field)) continue;
					SettingInfo cardInfo = card.getFirst().getAnnotation(SettingInfo.class);
					String cardName = cardInfo == null ? "" : displayName(cardInfo.name());
					if (cardName.isBlank() || page.breadcrumb.endsWith("  ›  " + cardName)) {
						return page.breadcrumb;
					}
					return page.breadcrumb + "  ›  " + cardName;
				}
				return page.breadcrumb;
			}
		}
		return null;
	}

	private static boolean smartMatches(SettingInfo option, String query) {
		String haystack = (option.name() + " " + option.desc()).toLowerCase(Locale.ROOT);
		for (String token : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
			if (token.isBlank() || haystack.contains(token)) continue;
			String alias = switch (token) {
				case "audio", "music", "melody" -> "sound";
				case "notification", "banner" -> "alert";
				case "message" -> "chat text";
				case "panel", "overlay" -> "hud";
				case "marker", "pin" -> "waypoint";
				case "colour" -> "color";
				case "position", "move" -> "location vertical";
				case "size" -> "scale";
				default -> "";
			};
			if (!alias.isBlank() && java.util.Arrays.stream(alias.split(" "))
				.anyMatch(haystack::contains)) continue;
			if (token.length() >= 4 && java.util.Arrays.stream(haystack.split("[^a-z0-9]+"))
				.anyMatch(word -> editDistanceAtMostOne(token, word))) continue;
			return false;
		}
		return true;
	}

	private static Field[] publicFields(Class<?> type) {
		return PUBLIC_FIELDS.computeIfAbsent(type, key -> java.util.Arrays.stream(key.getFields())
			.filter(field -> visibleInThisBuild(key, field))
			.toArray(Field[]::new));
	}

	/** Applies build-level availability; navigation separately gates Safe Mode and Developer areas. */
	private static boolean visibleInThisBuild(Class<?> owner, Field field) {
		if (field.getName().startsWith("private") && !BuildVersion.PRIVATE) return false;
		if (owner == SafariConfig.AdvancedConfig.class
				&& field.getName().equals("safeModeLockedNotice")) return BuildVersion.SAFE;
		if (owner == SafariConfig.AdvancedConfig.class && field.getName().equals("safeMode")) return false;
		if (owner != SafariConfig.AdvancedConfig.class || BuildVersion.DEVELOPER) return true;
		if (field.getName().equals("specialTheme")
			|| field.getName().equals("enablePartySync")) return true;
		return !BuildVersion.SAFE && (field.getName().startsWith("safe")
			|| field.getName().startsWith("SAFE_"));
	}

	private static boolean editDistanceAtMostOne(String first, String second) {
		if (Math.abs(first.length() - second.length()) > 1) return false;
		int i = 0;
		int j = 0;
		int edits = 0;
		while (i < first.length() && j < second.length()) {
			if (first.charAt(i) == second.charAt(j)) { i++; j++; continue; }
			if (++edits > 1) return false;
			if (first.length() > second.length()) i++;
			else if (second.length() > first.length()) j++;
			else { i++; j++; }
		}
		return edits + (i < first.length() || j < second.length() ? 1 : 0) <= 1;
	}

	private int drawSearchContext(GuiGraphicsExtractor graphics, String context,
			int left, int right, int y) {
		graphics.fill(left, y, right, y + 28, CARD);
		graphics.fill(left, y, left + 3, y + 28, GOLD);
		drawText(graphics, trim(context, right - left - 22), left + 12, y + 10, TEXT);
		return y + 34;
	}

	private int drawCombinedSettings(GuiGraphicsExtractor graphics, Object owner,
			List<Field> fields, int left, int right, int y, int mouseX, int mouseY) {
		int totalHeight = 0;
		for (Field field : fields) {
			totalHeight += settingTextLayout(field,
				field.getAnnotation(SettingInfo.class), left, right).height;
		}
		boolean visible = y + totalHeight > contentViewportTop && y < contentViewportBottom;
		if (visible) {
			graphics.fill(left, y, right, y + totalHeight, CARD);
			int dividerY = y;
			for (int index = 1; index < fields.size(); index++) {
				Field previous = fields.get(index - 1);
				dividerY += settingTextLayout(previous,
					previous.getAnnotation(SettingInfo.class), left, right).height;
				graphics.fill(left + 8, dividerY, right - 8, dividerY + 1, BORDER);
			}
		}

		int rowY = y;
		String previousName = null;
		int hoveredY = Integer.MIN_VALUE;
		int hoveredHeight = 0;
		for (int index = 0; index < fields.size(); index++) {
			Field field = fields.get(index);
			SettingInfo option = field.getAnnotation(SettingInfo.class);
			int rowHeight = settingTextLayout(field, option, left, right).height;
			if (rowY + rowHeight > contentViewportTop && rowY < contentViewportBottom) {
				boolean hovered = settingControlHovered(field, left, right, rowY, rowHeight,
					mouseX, mouseY);
				if (hovered) graphics.fill(left + 1, rowY, right - 1, rowY + rowHeight, CARD_HOVER);
				if (hovered) {
					hoveredY = rowY;
					hoveredHeight = rowHeight;
				}
				drawCombinedSettingRow(graphics, owner, field, option, previousName,
					left, right, rowY, rowHeight, mouseX, mouseY);
			}
			previousName = displayName(option.name());
			rowY += rowHeight;
		}
		// Shared rows meet on their exclusive bottom coordinate. Include that
		// boundary so the final card edge and every hovered divider are complete.
		if (visible) outline(graphics, left, y, right - left, totalHeight + 1, BORDER);
		if (hoveredY != Integer.MIN_VALUE) {
			outline(graphics, left, hoveredY, right - left, hoveredHeight + 1, CYAN);
		}
		return y + totalHeight + SETTINGS_GAP;
	}

	private boolean settingControlHovered(Field field, int left, int right, int y, int height,
			int mouseX, int mouseY) {
		boolean stacked = stackedSetting(left, right);
		int controlWidth = settingControlWidth(left, right, stacked);
		int controlX = stacked ? left + 12 : right - controlWidth - 10;
		int controlY = stacked ? y + height - 28 : y + (height - 22) / 2;
		if (field.isAnnotationPresent(SettingToggle.class)) {
			return inside(mouseX, mouseY, left, y, right, y + height);
		}
		if (field.isAnnotationPresent(SettingRange.class)) {
			return inside(mouseX, mouseY, controlX, y, right, y + height);
		}
		return inside(mouseX, mouseY, controlX, controlY,
			controlX + controlWidth, controlY + 22);
	}

	private SettingTextLayout settingTextLayout(Field field, SettingInfo option,
			int left, int right) {
		int availableWidth = right - left;
		Map<Integer, SettingTextLayout> widths = settingTextLayoutCache.computeIfAbsent(field,
			ignored -> new LinkedHashMap<>(4, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(
						Map.Entry<Integer, SettingTextLayout> eldest) {
					return size() > 8;
				}
			});
		SettingTextLayout cached = widths.get(availableWidth);
		if (cached != null) return cached;
		String description = option.desc();
		int split = description.indexOf('\n');
		String main = clean(split < 0 ? description : description.substring(0, split));
		String tag = split < 0 ? "" : clean(description.substring(split + 1));
		boolean readOnlyNotice = isReadOnlyNotice(field);
		boolean stacked = !readOnlyNotice && stackedSetting(left, right);
		int controlWidth = settingControlWidth(left, right, stacked);
		int descriptionWidth = readOnlyNotice ? Math.max(40, right - left - 24)
			: stacked ? Math.max(40, right - left - 24)
			: Math.max(40, right - controlWidth - 24 - (left + 12));
		List<String> mainLines = main.isBlank() ? List.of() : wrap(main, descriptionWidth);
		List<String> tagLines = tag.isBlank() ? List.of() : wrap(tag, descriptionWidth);
		int lines = mainLines.size() + tagLines.size();
		int textHeight = lines == 0 ? 30 : 24 + lines * 11;
		boolean safeModeComparison = main.startsWith("Safe:") && tag.startsWith("Normal:");
		SettingTextLayout layout = new SettingTextLayout(mainLines, tagLines,
			safeModeComparison, stacked ? Math.max(66, textHeight + 34)
				: Math.max(42, textHeight + 8));
		widths.put(availableWidth, layout);
		return layout;
	}

	private void drawCombinedSettingRow(GuiGraphicsExtractor graphics, Object owner, Field field,
			SettingInfo option, String previousName, int left, int right, int y, int height,
			int mouseX, int mouseY) {
		SettingTextLayout layout = settingTextLayout(field, option, left, right);
		String name = displayName(option.name());
		if (field.isAnnotationPresent(SettingColor.class) && name.equals(previousName)) name = "Color";
		drawText(graphics, name, left + 12, y + 9, TEXT);
		int lineY = y + 24;
		for (String line : layout.mainLines) {
			drawText(graphics, line, left + 12, lineY, layout.safeModeComparison ? CYAN : MUTED);
			lineY += 11;
		}
		for (String line : layout.tagLines) {
			drawText(graphics, line, left + 12, lineY, layout.safeModeComparison ? MUTED : CYAN);
			lineY += 11;
		}
		drawControl(graphics, owner, field, left, right, y, height, mouseX, mouseY);
	}

	private int drawSetting(GuiGraphicsExtractor graphics, Object owner, Field field,
			SettingInfo option, int left, int right, int y, int mouseX, int mouseY) {
		SettingTextLayout layout = settingTextLayout(field, option, left, right);
		boolean stacked = stackedSetting(left, right);
		int controlWidth = settingControlWidth(left, right, stacked);
		int height = layout.height;
		int controlX = stacked ? left + 12 : right - controlWidth - 10;
		int controlY = stacked ? y + height - 28 : y + (height - 22) / 2;
		if (y + height <= contentViewportTop || y >= contentViewportBottom) {
			return y + height + SETTINGS_GAP;
		}
		boolean rowHovered = inside(mouseX, mouseY, left, y, right, y + height);
		boolean controlHovered = inside(mouseX, mouseY, controlX, controlY,
			controlX + controlWidth, controlY + 22);
		boolean interactive = !isReadOnlyNotice(field);
		boolean hovered = interactive && (field.isAnnotationPresent(SettingToggle.class)
			? rowHovered : field.isAnnotationPresent(SettingRange.class)
				? inside(mouseX, mouseY, controlX, y, right, y + height)
				: controlHovered);
		graphics.fill(left, y, right, y + height, hovered ? CARD_HOVER : CARD);
		outline(graphics, left, y, right - left, height, hovered ? CYAN : BORDER);
		drawText(graphics, displayName(option.name()), left + 12, y + 9, TEXT);
		int lineY = y + 24;
		for (String line : layout.mainLines) {
			drawText(graphics, line, left + 12, lineY, layout.safeModeComparison ? CYAN : MUTED);
			lineY += 11;
		}
		for (String line : layout.tagLines) {
			drawText(graphics, line, left + 12, lineY, layout.safeModeComparison ? MUTED : CYAN);
			lineY += 11;
		}
		if (interactive) drawControl(graphics, owner, field, left, right, y, height, mouseX, mouseY);
		return y + height + SETTINGS_GAP;
	}

	private static boolean isReadOnlyNotice(Field field) {
		return field.getName().equals("safeModeLockedNotice");
	}

	private void drawControl(GuiGraphicsExtractor graphics, Object owner, Field field,
			int left, int right, int y, int height, int mouseX, int mouseY) {
		boolean stacked = stackedSetting(left, right);
		int controlWidth = settingControlWidth(left, right, stacked);
		int x = stacked ? left + 12 : right - controlWidth - 10;
		int controlY = stacked ? y + height - 28 : y + (height - 22) / 2;
		try {
			SettingChoice dropdown = field.getAnnotation(SettingChoice.class);
			SettingMultiChoice multiChoice = field.getAnnotation(SettingMultiChoice.class);
			SettingRange slider = field.getAnnotation(SettingRange.class);
			SettingAction button = field.getAnnotation(SettingAction.class);
			if (field.isAnnotationPresent(SettingToggle.class)) {
				boolean enabled = field.getBoolean(owner);
				drawToggle(graphics, x + controlWidth - 44, controlY + 2, enabled);
				hits.add(new Hit(left, y, right, y + height, () -> setBoolean(owner, field, !enabled)));
			} else if (dropdown != null) {
				int value = field.getInt(owner);
				String label = dropdownLabel(field, dropdown, value);
				drawChoice(graphics, x, controlY, controlWidth, label);
				hits.add(new Hit(x, controlY, x + controlWidth, controlY + 22,
					() -> openChoicePicker(owner, field, dropdown)));
			} else if (multiChoice != null) {
				int total = multiChoiceLabels(multiChoice).length;
				long selectedValue = multiChoiceValue(owner, field);
				int selected = multiChoiceSelectedCount(selectedValue, multiChoice.bits(), total);
				String label = selected == total ? "All selected"
					: selected == 0 ? "None selected"
					: selected + " of " + total + " selected";
				drawChoice(graphics, x, controlY, controlWidth, label);
				hits.add(new Hit(x, controlY, x + controlWidth, controlY + 22,
					() -> openMultiChoicePicker(owner, field, multiChoice)));
			} else if (slider != null) {
				float value = ((Number) field.get(owner)).floatValue();
				if (editingInlineText && editingNumber
					&& editingField == field && editingOwner == owner) {
					setEditingSliderBounds(x, y, right - x, height);
					String originalLabel = formatNumber(editingNumberOriginal.floatValue()) + "  ✎";
					int editorWidth = font.width(originalLabel) + 4;
					int editorX = x + controlWidth - editorWidth;
					int editorY = controlY - 2;
					drawInlineEditorFrame(graphics, editorX, editorY, editorWidth, 16);
					setInlineEditorBounds(editorX, editorY, editorWidth, 16);
					editor.setX(editorX + 4);
					editor.setY(controlY + 2);
					editor.setWidth(editorWidth - 4);
					editor.setTextColor(TEXT);
					editor.setTextColorUneditable(MUTED);
				} else {
					drawSlider(graphics, x, controlY, controlWidth, value, slider);
				}
				hits.add(new Hit(x, y, right, y + height,
					() -> beginSlider(owner, field, slider, mouseX, x, controlWidth)));
				String editLabel = formatNumber(value) + "  ✎";
				int editWidth = font.width(editLabel) + 4;
				int editLeft = x + controlWidth - editWidth;
				// Added after the track hit so reverse hit-testing gives the displayed
				// value and pencil priority over dragging when their areas overlap.
				hits.add(new Hit(editLeft, controlY - 2, x + controlWidth, controlY + 14, () -> {
					setEditingSliderBounds(x, y, right - x, height);
					openSliderEditor(owner, field, editLeft, controlY - 2, editWidth);
				}));
			} else if (field.isAnnotationPresent(SettingColor.class)) {
				int colour = Colours.argb((String) field.get(owner), 0xFFFFFFFF);
				drawColour(graphics, x, controlY, controlWidth, colour);
				hits.add(new Hit(x, controlY, x + controlWidth, controlY + 22,
					() -> openEditor(owner, field, true)));
			} else if (field.isAnnotationPresent(SettingText.class)) {
				if (editingInlineText && editingField == field && editingOwner == owner) {
					drawInlineEditorFrame(graphics, x, controlY, controlWidth);
					setInlineEditorBounds(x, controlY, controlWidth, 22);
					editor.setX(x + 8);
					editor.setY(controlY + 7);
					editor.setWidth(controlWidth - 16);
					editor.setTextColor(TEXT);
					editor.setTextColorUneditable(MUTED);
				} else {
					drawTextValue(graphics, x, controlY, controlWidth, (String) field.get(owner));
				}
				hits.add(new Hit(x, controlY, x + controlWidth, controlY + 22,
					() -> openInlineTextEditor(owner, field, x, controlY, controlWidth)));
			} else if (button != null) {
				drawButton(graphics, x, controlY, controlWidth, button.buttonText(), mouseX, mouseY);
				hits.add(new Hit(x, controlY, x + controlWidth, controlY + 22,
					() -> runButton(owner, field, button)));
			}
		} catch (IllegalAccessException ignored) {
		}
	}

	private static boolean stackedSetting(int left, int right) {
		return right - left < 430;
	}

	private static int settingControlWidth(int left, int right, boolean stacked) {
		return stacked ? Math.max(80, right - left - 24)
			: Math.clamp((right - left) / 3, 116, 210);
	}

	private void drawToggle(GuiGraphicsExtractor graphics, int x, int y, boolean enabled) {
		graphics.fill(x, y, x + 38, y + 18, enabled ? shade(BLUE, 0.48f) : SURFACE);
		outline(graphics, x, y, 38, 18, enabled ? BLUE : BORDER);
		int knob = enabled ? x + 23 : x + 3;
		graphics.fill(knob, y + 3, knob + 12, y + 15, enabled ? CYAN : MUTED);
	}

	private static int shade(int colour, float amount) {
		int red = Math.round((colour >> 16 & 0xFF) * amount);
		int green = Math.round((colour >> 8 & 0xFF) * amount);
		int blue = Math.round((colour & 0xFF) * amount);
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}

	private static int blendOpaque(int base, int accent, float amount) {
		float inverse = 1f - amount;
		int red = Math.round((base >> 16 & 0xFF) * inverse + (accent >> 16 & 0xFF) * amount);
		int green = Math.round((base >> 8 & 0xFF) * inverse + (accent >> 8 & 0xFF) * amount);
		int blue = Math.round((base & 0xFF) * inverse + (accent & 0xFF) * amount);
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}

	private void drawChoice(GuiGraphicsExtractor graphics, int x, int y, int width, String label) {
		graphics.fill(x, y, x + width, y + 22, CARD);
		outline(graphics, x, y, width, 22, BORDER);
		drawText(graphics, trim(label, width - 30), x + 8, y + 7, CYAN);
		drawText(graphics, "▦", x + width - 15, y + 7, MUTED);
	}

	private void openChoicePicker(Object owner, Field field, SettingChoice dropdown) {
		choiceOwner = owner;
		choiceField = field;
		choiceDropdown = dropdown;
		setFocused(null);
		if (search != null) search.visible = false;
	}

	private void openMultiChoicePicker(Object owner, Field field, SettingMultiChoice dropdown) {
		choiceOwner = owner;
		choiceField = field;
		choiceDropdown = null;
		multiChoiceDropdown = dropdown;
		multiChoiceScroll = 0;
		setFocused(null);
		if (search != null) search.visible = false;
	}

	private void drawChoiceModal(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (multiChoiceDropdown != null) {
			drawMultiChoiceModal(graphics, mouseX, mouseY);
			return;
		}
		List<String> labels = choiceLabels();
		boolean soundChoice = isSoundChoice(choiceField);
		int hintHeight = soundChoice ? 12 : 0;
		int maxRows = Math.max(1, (height - 98 - hintHeight) / 27);
		int columns = Math.clamp((labels.size() + maxRows - 1) / maxRows, 2, 6);
		int rows = (labels.size() + columns - 1) / columns;
		int w = Math.min(680, width - 30);
		int h = Math.min(height - 30, 68 + hintHeight + rows * 27);
		int x = (width - w) / 2;
		int y = (height - h) / 2;
		graphics.fill(0, 0, width, height, 0xAA000000);
		graphics.fill(x, y, x + w, y + h, SURFACE);
		outline(graphics, x, y, w, h, CYAN);
		SettingInfo option = choiceField.getAnnotation(SettingInfo.class);
		drawText(graphics, "Choose " + displayName(option.name()), x + 14, y + 14, TEXT);
		if (soundChoice) {
			drawText(graphics, "Right-click a sound to preview it", x + 14, y + 26, CYAN);
		}
		int cellWidth = (w - 28 - (columns - 1) * 6) / columns;
		int current = choiceValue();
		for (int index = 0; index < labels.size(); index++) {
			int column = index % columns;
			int row = index / columns;
			int cellX = x + 14 + column * (cellWidth + 6);
			int cellY = y + 36 + hintHeight + row * 27;
			if (cellY + 22 > y + h - 30) continue;
			int value = choiceStoredValue(index);
			boolean active = current == value;
			boolean hovered = inside(mouseX, mouseY, cellX, cellY, cellX + cellWidth, cellY + 22);
			graphics.fill(cellX, cellY, cellX + cellWidth, cellY + 22,
				active ? SELECTED : hovered ? CARD_HOVER : CARD);
			outline(graphics, cellX, cellY, cellWidth, 22, active ? CYAN : BORDER);
			drawCenteredText(graphics, trim(labels.get(index), cellWidth - 12),
				cellX + cellWidth / 2, cellY + 7, active ? TEXT : MUTED);
			hits.add(new Hit(cellX, cellY, cellX + cellWidth, cellY + 22,
				() -> chooseDropdownValue(value)));
			if (soundChoice) {
				soundPreviewHits.add(new SoundPreviewHit(cellX, cellY,
					cellX + cellWidth, cellY + 22, value));
			}
		}
		drawButton(graphics, x + w - 74, y + h - 28, 60, "Cancel", mouseX, mouseY);
		hits.add(new Hit(x + w - 74, y + h - 28, x + w - 14, y + h - 6,
			this::closeChoicePicker));
	}

	private void drawMultiChoiceModal(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		String[] labels = multiChoiceLabels(multiChoiceDropdown);
		String[] groups = multiChoiceDropdown.critters() ? CRITTER_GROUPS : multiChoiceDropdown.groups();
		int[] starts = multiChoiceDropdown.critters() ? CRITTER_GROUP_STARTS : multiChoiceDropdown.groupStarts();
		int columns = multiChoiceColumns();
		int rows = multiChoiceRows(labels.length, columns);
		boolean groupedColumns = multiChoiceDropdown.biomeColumns() && columns == groups.length;
		int groupedWidth = groups.length >= 5 ? 900 : 760;
		int w = Math.min(groupedColumns ? groupedWidth : 620, width - 30);
		int h = Math.min(height - 30, (groupedColumns ? 90 : 76) + rows * 31);
		int x = (width - w) / 2;
		int y = (height - h) / 2;
		int itemsTop = y + (groupedColumns ? 58 : 44);
		int itemsBottom = y + h - 32;
		multiChoiceScroll = Math.clamp(multiChoiceScroll, 0,
			Math.max(0, rows * 31 - (itemsBottom - itemsTop)));
		graphics.fill(0, 0, width, height, 0xAA000000);
		graphics.fill(x, y, x + w, y + h, SURFACE);
		outline(graphics, x, y, w, h, CYAN);
		SettingInfo option = choiceField.getAnnotation(SettingInfo.class);
		drawText(graphics, "Choose " + displayName(option.name()), x + 14, y + 14, TEXT);
		drawText(graphics, rows * 31 > itemsBottom - itemsTop
			? "Select options · scroll for more" : "Select every option that should be enabled",
			x + 14, y + 27, MUTED);
		int cellWidth = (w - 28 - (columns - 1) * 8) / columns;
		long selected = multiChoiceValue(choiceOwner, choiceField);
		int[] storedBits = multiChoiceDropdown.bits();
		if (groupedColumns) {
			for (int column = 0; column < groups.length; column++) {
				drawText(graphics, groups[column],
					x + 20 + column * (cellWidth + 8), y + 44, CYAN);
			}
		}
		graphics.enableScissor(x + 12, itemsTop, x + w - 12, itemsBottom);
		for (int index = 0; index < labels.length; index++) {
			int column;
			int row;
			if (groupedColumns) {
				column = groupIndex(index, starts);
				row = index - starts[column];
			} else if (multiChoiceDropdown.biomeColumns() && columns == 2) {
				int split = starts[(groups.length + 1) / 2];
				column = index >= split ? 1 : 0;
				row = index - (column == 1 ? split : 0);
			} else {
				column = index % columns;
				row = index / columns;
			}
			int cellX = x + 14 + column * (cellWidth + 8);
			int cellY = itemsTop + row * 31 - multiChoiceScroll;
			if (cellY < itemsTop || cellY + 26 > itemsBottom) continue;
			String group = groupFor(index, groups, starts);
			long bit = multiChoiceBit(storedBits, index);
			boolean active = (selected & bit) != 0;
			boolean hovered = inside(mouseX, mouseY, cellX, cellY, cellX + cellWidth, cellY + 26);
			graphics.fill(cellX, cellY, cellX + cellWidth, cellY + 26,
				active ? SELECTED : hovered ? CARD_HOVER : CARD);
			outline(graphics, cellX, cellY, cellWidth, 26, active ? CYAN : BORDER);
			Component mark = Component.literal(active ? "✓" : "○")
				.withStyle(style -> style.withBold(true));
			drawText(graphics, mark, cellX + 8, cellY + 9, active ? GREEN : DIM);
			drawText(graphics, trim(groupedColumns ? labels[index] : group + " · " + labels[index],
				cellWidth - 34),
				cellX + 24, cellY + 9, active ? TEXT : MUTED);
			hits.add(new Hit(cellX, cellY, cellX + cellWidth, cellY + 26,
				() -> toggleMultiChoice(bit)));
		}
		graphics.disableScissor();
		drawButton(graphics, x + w - 74, y + h - 28, 60, "Done", mouseX, mouseY);
		hits.add(new Hit(x + w - 74, y + h - 28, x + w - 14, y + h - 6,
			this::closeChoicePicker));
	}

	private static String groupFor(int index, String[] groups, int[] starts) {
		String group = "";
		for (int i = 0; i < groups.length && i < starts.length; i++) {
			if (starts[i] > index) break;
			group = groups[i];
		}
		return group;
	}

	private void toggleMultiChoice(long bit) {
		try {
			if (choiceField.getType() == long.class) {
				choiceField.setLong(choiceOwner, choiceField.getLong(choiceOwner) ^ bit);
			} else {
				choiceField.setInt(choiceOwner, choiceField.getInt(choiceOwner) ^ (int) bit);
			}
			ConfigManager.save();
		} catch (IllegalAccessException ignored) {
		}
	}

	private List<String> choiceLabels() {
		if (isSoundChoice(choiceField)) return SOUND_LABELS;
		if (isThemeChoice(choiceField)) return THEME_LABELS;
		return List.of(choiceDropdown.values());
	}

	private int choiceStoredValue(int visibleIndex) {
		if (isSoundChoice(choiceField)) return AlertSounds.alphabetical().get(visibleIndex).id();
		if (isThemeChoice(choiceField)) return THEMES.get(visibleIndex).id();
		return visibleIndex;
	}

	private static long multiChoiceValue(Object owner, Field field) {
		try {
			return field.getType() == long.class ? field.getLong(owner) : Integer.toUnsignedLong(field.getInt(owner));
		} catch (IllegalAccessException ignored) {
			return 0;
		}
	}

	private int choiceValue() {
		try {
			return choiceField.getInt(choiceOwner);
		} catch (IllegalAccessException ignored) {
			return 0;
		}
	}

	private static String[] multiChoiceLabels(SettingMultiChoice choice) {
		return choice.critters() ? CRITTER_CHOICES : choice.values();
	}

	private static int multiChoiceSelectedCount(long value, int[] bits, int total) {
		if (bits.length == 0) {
			long valid = total >= Long.SIZE ? -1L : (1L << total) - 1;
			return Long.bitCount(value & valid);
		}
		int selected = 0;
		for (int index = 0; index < total; index++) {
			if ((value & multiChoiceBit(bits, index)) != 0) selected++;
		}
		return selected;
	}

	private static long multiChoiceBit(int[] bits, int visibleIndex) {
		int bitIndex = visibleIndex < bits.length ? bits[visibleIndex] : visibleIndex;
		return bitIndex >= 0 && bitIndex < Long.SIZE ? 1L << bitIndex : 0L;
	}

	private static int[] critterGroupStarts() {
		int[] starts = new int[CRITTER_GROUPS.length];
		int offset = 0;
		for (int index = 0; index < starts.length; index++) {
			starts[index] = offset;
			offset += Critters.totalIn(Critters.selectionBiomes().get(index));
		}
		return starts;
	}

	private int multiChoiceColumns() {
		if (multiChoiceDropdown.biomeColumns()) {
			int groups = multiChoiceDropdown.critters()
				? CRITTER_GROUPS.length : multiChoiceDropdown.groups().length;
			if (width >= (groups >= 5 ? 800 : 600)) return groups;
			return width >= 420 ? 2 : 1;
		}
		return width >= 560 ? 2 : 1;
	}

	private int multiChoiceRows(int count, int columns) {
		if (!multiChoiceDropdown.biomeColumns() || columns == 1) return (count + columns - 1) / columns;
		int[] starts = multiChoiceDropdown.critters()
			? CRITTER_GROUP_STARTS : multiChoiceDropdown.groupStarts();
		if (columns == 2) {
			String[] groups = multiChoiceDropdown.critters()
				? CRITTER_GROUPS : multiChoiceDropdown.groups();
			int split = starts[(groups.length + 1) / 2];
			return Math.max(split, count - split);
		}
		int maximum = 0;
		for (int index = 0; index < starts.length; index++) {
			int end = index + 1 < starts.length ? starts[index + 1] : count;
			maximum = Math.max(maximum, end - starts[index]);
		}
		return maximum;
	}

	private static int groupIndex(int index, int[] starts) {
		for (int group = starts.length - 1; group >= 0; group--) {
			if (index >= starts[group]) return group;
		}
		return 0;
	}

	private void chooseDropdownValue(int value) {
		boolean customTheme = isThemeChoice(choiceField) && value == 34;
		if (choiceField != null && choiceField.getName().equals("specialSparklingIntensity")
			&& value > 0) {
			int current = ConfigManager.get().sparkling.specialSparklingIntensity;
			pendingSparklingSourcePreset = value == SparklingAlertStyle.CUSTOM_INDEX
				&& current >= 0 && current < SparklingAlertStyle.CUSTOM_INDEX ? current : -1;
			pendingSparklingIntensity = value;
			closeChoicePicker();
			specialSparklingConfirmation = true;
			if (search != null) search.visible = false;
			return;
		}
		try {
			choiceField.setInt(choiceOwner, value);
			ConfigManager.save();
		} catch (IllegalAccessException ignored) {
		}
		closeChoicePicker();
		if (customTheme) openCustomThemePanel();
	}

	private void cancelSparklingIntensity() {
		ConfigManager.get().sparkling.specialSparklingIntensity = 0;
		ConfigManager.save();
		specialSparklingConfirmation = false;
		pendingSparklingIntensity = -1;
		if (search != null) search.visible = true;
	}

	private void closeChoicePicker() {
		choiceField = null;
		choiceOwner = null;
		choiceDropdown = null;
		multiChoiceDropdown = null;
		if (search != null) search.visible = true;
	}

	private float soundPreviewSetting(String suffix, float fallback) {
		if (choiceField == null || choiceOwner == null) return fallback;
		String name = choiceField.getName();
		if (!name.endsWith("SoundChoice")) return fallback;
		try {
			Field setting = choiceOwner.getClass().getField(
				name.substring(0, name.length() - "Choice".length()) + suffix);
			return ((Number) setting.get(choiceOwner)).floatValue();
		} catch (NoSuchFieldException | IllegalAccessException | ClassCastException ignored) {
			return fallback;
		}
	}

	private void openCustomThemePanel() {
		customThemePanel = true;
		setFocused(null);
		if (search != null) search.visible = false;
	}

	private void closeCustomThemePanel() {
		customThemePanel = false;
		ConfigManager.save();
		if (search != null) {
			search.visible = true;
			search.active = true;
		}
	}

	private void closeCustomSparklingPanel() {
		customSparklingPanel = false;
		AlertSounds.stopSparklingPreview(Minecraft.getInstance());
		previewSong = Integer.MIN_VALUE;
		applyCustomDurationEditor();
		applyCustomVolumeEditor();
		if (customCalloutEditor != null) {
			removeWidget(customCalloutEditor);
			customCalloutEditor = null;
		}
		if (customDurationEditor != null) {
			removeWidget(customDurationEditor);
			customDurationEditor = null;
		}
		if (customVolumeEditor != null) {
			removeWidget(customVolumeEditor);
			customVolumeEditor = null;
		}
		if (customPresetNameEditor != null) {
			removeWidget(customPresetNameEditor);
			customPresetNameEditor = null;
		}
		customPresetMenu = false;
		customPresetNaming = false;
		ConfigManager.save();
		if (search != null) search.visible = true;
	}

	private void openCustomSparklingPanel() {
		customSparklingPanel = true;
		customSparklingPage = Math.max(0, customSparklingPage);
		customCalloutEditor = new EditBox(font, 0, 0, 120, 9,
			Component.literal("Capture callout text"));
		customCalloutEditor.setBordered(false);
		customCalloutEditor.setMaxLength(48);
		customCalloutEditor.setValue(ConfigManager.get().sparkling.customAlertCalloutText);
		customCalloutEditor.setResponder(value -> {
			ConfigManager.get().sparkling.customAlertCalloutText = value;
			SparklingAlertStyle.invalidateCustom();
		});
		UIDraw.rainbowEditBox(customCalloutEditor, font);
		addRenderableWidget(customCalloutEditor);
		customDurationEditor = new EditBox(font, 0, 0, 38, 9,
			Component.literal("Catch alert duration in seconds"));
		customDurationEditor.setBordered(false);
		customDurationEditor.setMaxLength(5);
		customDurationEditor.setValue(formatDuration(
			ConfigManager.get().sparkling.customAlertDuration));
		customDurationEditor.setCursorPosition(customDurationEditor.getValue().length());
		customDurationEditor.setResponder(this::previewCustomDuration);
		UIDraw.rainbowEditBox(customDurationEditor, font);
		addRenderableWidget(customDurationEditor);
		customVolumeEditor = new EditBox(font, 0, 0, 38, 9,
			Component.literal("Catch alert sound volume percent"));
		customVolumeEditor.setBordered(false);
		customVolumeEditor.setMaxLength(3);
		customVolumeEditor.setValue(Integer.toString(
			ConfigManager.get().sparkling.customAlertSoundVolume));
		customVolumeEditor.setCursorPosition(customVolumeEditor.getValue().length());
		customVolumeEditor.setResponder(this::previewCustomVolume);
		UIDraw.rainbowEditBox(customVolumeEditor, font);
		addRenderableWidget(customVolumeEditor);
		customPresetMenu = false;
		customPresetNaming = false;
		ConfigManager.get().sparkling.customAlertPerformanceBudget = 2;
		ConfigManager.get().sparkling.customAlertPreviewScale = 2;
		ConfigManager.get().sparkling.customAlertPreviewBackground = 0;
		ConfigManager.get().sparkling.customAlertPreviewLoop = true;
		FullScreenAlert.restartPreviewPaused();
		AlertSounds.stopSparklingPreview(Minecraft.getInstance());
		previewSong = Integer.MIN_VALUE;
		customPreviewResuming = false;
		setFocused(null);
		if (search != null) {
			search.setFocused(false);
			search.visible = false;
			search.active = false;
		}
	}

	/** Live alert editor; every control feeds the same bounded renderer used in-game. */
	private void drawCustomSparklingPanel(GuiGraphicsExtractor graphics,
			int mouseX, int mouseY) {
		updateCustomPreviewSong();
		if (search != null) {
			search.setFocused(false);
			search.visible = false;
			search.active = false;
		}
		int w = Math.min(720, width - 24);
		int h = Math.min(520, height - 24);
		int x = (width - w) / 2;
		int y = (height - h) / 2;
		graphics.fill(0, 0, width, height, 0xBB000000);
		graphics.fill(x, y, x + w, y + h, SURFACE);
		outline(graphics, x, y, w, h, CYAN);
		drawText(graphics, trim("CUSTOM SPARKLING CATCH ALERT", w - 28), x + 14, y + 13, TEXT);
		drawText(graphics,
			"Changes preview live · Higher intensities can increase flashing and motion",
			x + 14, y + 27, RED);
		if (w >= 620) {
			String load = customEffectLoadLabel();
			drawText(graphics, "Estimated load: " + load,
				x + w - 14 - font.width("Estimated load: " + load), y + 27,
				load.equals("Extreme") ? RED : load.equals("Heavy") ? GOLD : MUTED);
		}

		int previewX = x + 14;
		int previewY = y + 43;
		int previewW = w - 28;
		boolean compactHeight = h < 360;
		boolean tinyHeight = h < 280;
		int effectsPerPage = tinyHeight ? 4
			: compactHeight ? 8 : SparklingAlertStyle.EFFECTS_PER_PAGE;
		int pageCount = 0;
		for (int categoryIndex = 0;
				categoryIndex < SparklingAlertStyle.EFFECT_PAGE_NAMES.length; categoryIndex++) {
			pageCount += (customEffectCategoryCount(categoryIndex) + effectsPerPage - 1)
				/ effectsPerPage;
		}
		customSparklingPage = Math.clamp(customSparklingPage, 0, pageCount - 1);
		int category = 0;
		int subPage = customSparklingPage;
		while (category < SparklingAlertStyle.EFFECT_PAGE_NAMES.length - 1) {
			int pages = (customEffectCategoryCount(category) + effectsPerPage - 1)
				/ effectsPerPage;
			if (subPage < pages) break;
			subPage -= pages;
			category++;
		}
		int categoryStart = category * SparklingAlertStyle.EFFECTS_PER_PAGE;
		int firstEffect = categoryStart + subPage * effectsPerPage;
		int lastEffect = Math.min(SparklingAlertStyle.EFFECTS.size(),
			Math.min(categoryStart + SparklingAlertStyle.EFFECTS_PER_PAGE,
				firstEffect + effectsPerPage));
		int effectCount = lastEffect - firstEffect;
		boolean globalControls = customSparklingPage == 0;
		int columns = 3;
		int rows = (effectCount + columns - 1) / columns;
		int rowHeight = compactHeight ? 24 : h >= 43 + 12 + rows * 27 + 34 + 48 ? 27 : 24;
		int editorHeight = globalControls ? (compactHeight ? 23 : 28) : 0;
		int durationHeight = globalControls ? (compactHeight ? 69 : 88) : 0;
		int previewH = tinyHeight ? 0 : Math.clamp(h - 43 - 38 - editorHeight - durationHeight
			- rows * rowHeight - 34, compactHeight ? 28 : 36, 150);
		if (previewH > 0) {
			graphics.fill(previewX, previewY, previewX + previewW, previewY + previewH, 0xEE080B13);
			FullScreenAlert.preview(graphics, previewX, previewY, previewW, previewH);
			outline(graphics, previewX, previewY, previewW, previewH, BORDER);
			boolean previewPaused = FullScreenAlert.previewPaused();
			drawButton(graphics, previewX + 6, previewY + 6, 24, "", mouseX, mouseY);
			if (previewPaused) {
				// Pixel geometry avoids the font glyph's asymmetric side bearings.
				int playX = previewX + 14;
				int playY = previewY + 13;
				int[] widths = {1, 3, 5, 7, 9, 7, 5, 3, 1};
				for (int row = 0; row < widths.length; row++) {
					graphics.fill(playX + 1, playY + row + 1,
						playX + widths[row] + 1, playY + row + 2, 0xAA000000);
					graphics.fill(playX, playY + row,
						playX + widths[row], playY + row + 1, TEXT);
				}
			} else {
				// Draw the pause mark ourselves so it matches Play's weight and shadow.
				int pauseX = previewX + 14;
				int pauseY = previewY + 13;
				graphics.fill(pauseX + 1, pauseY + 1, pauseX + 4, pauseY + 10, 0xAA000000);
				graphics.fill(pauseX + 7, pauseY + 1, pauseX + 10, pauseY + 10, 0xAA000000);
				graphics.fill(pauseX, pauseY, pauseX + 3, pauseY + 9, TEXT);
				graphics.fill(pauseX + 6, pauseY, pauseX + 9, pauseY + 9, TEXT);
			}
			hits.add(new Hit(previewX + 6, previewY + 6, previewX + 30, previewY + 28,
				this::toggleCustomPreview));
			int timelineX = previewX + 5;
			int timelineY = previewY + previewH - 7;
			int timelineW = previewW - 10;
			graphics.fill(timelineX, timelineY, timelineX + timelineW, timelineY + 3, BORDER);
			int elapsedW = Math.round(timelineW * FullScreenAlert.previewProgress(
				SparklingAlertStyle.custom().displayMillis()));
			graphics.fill(timelineX, timelineY, timelineX + elapsedW, timelineY + 3, CYAN);
		}

		int pageY = previewY + previewH + (previewH > 0 ? 8 : 0);
		int pageHeight = compactHeight ? 18 : 20;
		if (compactHeight) {
			int navWidth = Math.min(72, (w - 40) / 3);
			int centreX = x + w / 2 - navWidth / 2;
			drawButton(graphics, centreX - navWidth - 5, pageY, navWidth, "Previous", mouseX, mouseY);
			graphics.fill(centreX, pageY, centreX + navWidth, pageY + pageHeight, SELECTED);
			outline(graphics, centreX, pageY, navWidth, pageHeight, CYAN);
			drawCenteredText(graphics, (customSparklingPage + 1) + "/" + pageCount,
				centreX + navWidth / 2, pageY + 5, TEXT);
			drawButton(graphics, centreX + navWidth + 5, pageY, navWidth, "Next", mouseX, mouseY);
			if (customSparklingPage > 0) hits.add(new Hit(centreX - navWidth - 5, pageY,
				centreX - 5, pageY + pageHeight, () -> customSparklingPage--));
			if (customSparklingPage + 1 < pageCount) hits.add(new Hit(centreX + navWidth + 5,
				pageY, centreX + navWidth * 2 + 5, pageY + pageHeight,
				() -> customSparklingPage++));
			drawText(graphics, customEffectPageName(customSparklingPage, effectsPerPage),
				x + 14, pageY + pageHeight + 2, MUTED);
		} else {
			int pageGap = 6;
			int pageWidth = (w - 28 - pageGap * (pageCount - 1)) / pageCount;
			for (int page = 0; page < pageCount; page++) {
				int pageX = x + 14 + page * (pageWidth + pageGap);
				boolean active = page == customSparklingPage;
				boolean hovered = inside(mouseX, mouseY, pageX, pageY,
					pageX + pageWidth, pageY + pageHeight);
				graphics.fill(pageX, pageY, pageX + pageWidth, pageY + pageHeight,
					active ? SELECTED : hovered ? CARD_HOVER : CARD);
				outline(graphics, pageX, pageY, pageWidth, pageHeight, active ? CYAN : BORDER);
				drawCenteredText(graphics, customEffectPageName(page, effectsPerPage),
					pageX + pageWidth / 2, pageY + 6, active ? TEXT : MUTED);
				int selectedPage = page;
				hits.add(new Hit(pageX, pageY, pageX + pageWidth, pageY + pageHeight,
					() -> customSparklingPage = selectedPage));
			}
		}

		int controlsTop = pageY + (compactHeight ? 32 : 26);
		customDurationSliderWidth = 0;
		customVolumeSliderWidth = 0;
		boolean globalEditorsVisible = globalControls && !customPresetMenu;
		if (customDurationEditor != null) {
			customDurationEditor.visible = globalEditorsVisible;
			customDurationEditor.active = globalEditorsVisible;
			if (!globalEditorsVisible) customDurationEditor.setFocused(false);
		}
		if (customVolumeEditor != null) {
			customVolumeEditor.visible = globalEditorsVisible;
			customVolumeEditor.active = globalEditorsVisible;
			if (!globalEditorsVisible) customVolumeEditor.setFocused(false);
		}
		if (globalControls) {
		int durationY = controlsTop;
		int durationFieldWidth = 46;
		customDurationSliderLeft = x + 82;
		customDurationSliderWidth = w - 82 - 14 - durationFieldWidth - 10;
		customDurationSliderTop = durationY;
		drawText(graphics, "Duration", x + 14, durationY + 5, TEXT);
		float duration = ConfigManager.get().sparkling.customAlertDuration;
		graphics.fill(customDurationSliderLeft, durationY + 9,
			customDurationSliderLeft + customDurationSliderWidth, durationY + 12, BORDER);
		int durationChoice = AlertSounds.nearestSparklingDurationChoice(duration);
		int durationProgress = Math.round(customDurationSliderWidth * durationChoice
			/ (float) (AlertSounds.sparklingDurationChoiceCount() - 1));
		graphics.fill(customDurationSliderLeft, durationY + 9,
			customDurationSliderLeft + durationProgress, durationY + 12, BLUE);
		int handleX = customDurationSliderLeft + durationProgress;
		graphics.fill(handleX - 2, durationY + 6, handleX + 2, durationY + 15, CYAN);
		int durationFieldX = x + w - 14 - durationFieldWidth;
		drawInlineEditorFrame(graphics, durationFieldX, durationY, durationFieldWidth, 20);
		if (customDurationEditor != null) {
			if (globalEditorsVisible) customDurationBounds.set(durationFieldX,
				durationY, durationFieldX + durationFieldWidth, durationY + 20);
			customDurationEditor.visible = globalEditorsVisible;
			customDurationEditor.setX(durationFieldX + 5);
			customDurationEditor.setY(durationY + 6);
			customDurationEditor.setWidth(durationFieldWidth - 18);
			customDurationEditor.setTextColor(TEXT);
			customDurationEditor.setTextColorUneditable(MUTED);
			UIDraw.updateRainbowCaret(customDurationEditor, TEXT);
			drawText(graphics, "s", durationFieldX + durationFieldWidth - 10,
				durationY + 6, MUTED);
		}

		int volumeY = durationY + (compactHeight ? 23 : 30);
		int volumeFieldWidth = 52;
		customVolumeSliderLeft = x + 82;
		customVolumeSliderWidth = w - 82 - 14 - volumeFieldWidth - 10;
		customVolumeSliderTop = volumeY;
		drawText(graphics, "Volume", x + 14, volumeY + 5, TEXT);
		int volume = Math.clamp(ConfigManager.get().sparkling.customAlertSoundVolume,
			0, SparklingAlertStyle.VOLUME_SLIDER_MAX);
		graphics.fill(customVolumeSliderLeft, volumeY + 9,
			customVolumeSliderLeft + customVolumeSliderWidth, volumeY + 12, BORDER);
		int volumeProgress = Math.round(customVolumeSliderWidth * volume
			/ (float) SparklingAlertStyle.VOLUME_SLIDER_MAX);
		graphics.fill(customVolumeSliderLeft, volumeY + 9,
			customVolumeSliderLeft + volumeProgress, volumeY + 12, BLUE);
		int volumeHandleX = customVolumeSliderLeft + volumeProgress;
		graphics.fill(volumeHandleX - 2, volumeY + 6,
			volumeHandleX + 2, volumeY + 15, CYAN);
		int volumeFieldX = x + w - 14 - volumeFieldWidth;
		drawInlineEditorFrame(graphics, volumeFieldX, volumeY, volumeFieldWidth, 20);
		if (customVolumeEditor != null) {
			if (globalEditorsVisible) customVolumeBounds.set(volumeFieldX,
				volumeY, volumeFieldX + volumeFieldWidth, volumeY + 20);
			customVolumeEditor.visible = globalEditorsVisible;
			customVolumeEditor.setX(volumeFieldX + 5);
			customVolumeEditor.setY(volumeY + 6);
			customVolumeEditor.setWidth(volumeFieldWidth - 20);
			customVolumeEditor.setTextColor(TEXT);
			customVolumeEditor.setTextColorUneditable(MUTED);
			UIDraw.updateRainbowCaret(customVolumeEditor, TEXT);
			drawText(graphics, "%", volumeFieldX + volumeFieldWidth - 12,
				volumeY + 6, MUTED);
		}
		int timingY = volumeY + (compactHeight ? 23 : 30);
		drawText(graphics, "Timing", x + 14, timingY + 5, TEXT);
		String[] timings = {"Designed", "Opening", "Reveal", "Sustain", "Finale"};
		int timingMode = Math.clamp(ConfigManager.get().sparkling.customAlertTimingMode, 0, 4);
		int timingWidth = (w - 96 - 4 * 4) / 5;
		for (int index = 0; index < timings.length; index++) {
			int buttonX = x + 82 + index * (timingWidth + 4);
			boolean selected = timingMode == index;
			graphics.fill(buttonX, timingY, buttonX + timingWidth, timingY + 20,
				selected ? SELECTED : CARD);
			outline(graphics, buttonX, timingY, timingWidth, 20, selected ? CYAN : BORDER);
			drawCenteredText(graphics, trim(timings[index], timingWidth - 6),
				buttonX + timingWidth / 2, timingY + 6, selected ? TEXT : MUTED);
			int choice = index;
			hits.add(new Hit(buttonX, timingY, buttonX + timingWidth, timingY + 20,
				() -> ConfigManager.get().sparkling.customAlertTimingMode = choice));
		}
		}
		controlsTop += durationHeight;
		if (customCalloutEditor != null) {
			customCalloutEditor.visible = globalEditorsVisible;
			customCalloutEditor.active = globalEditorsVisible;
			if (!globalEditorsVisible) customCalloutEditor.setFocused(false);
			if (globalControls) {
				int fieldX = x + 112;
				int fieldWidth = w - 126;
				drawText(graphics, "Callout Text", x + 14, controlsTop + 5, TEXT);
				drawInlineEditorFrame(graphics, fieldX, controlsTop, fieldWidth, 18);
				customCalloutBounds.set(fieldX, controlsTop,
					fieldX + fieldWidth, controlsTop + 18);
				customCalloutEditor.setX(fieldX + 4);
				customCalloutEditor.setY(controlsTop + 5);
				customCalloutEditor.setWidth(Math.max(8, fieldWidth - 8));
				customCalloutEditor.setTextColor(TEXT);
				customCalloutEditor.setTextColorUneditable(MUTED);
				UIDraw.updateRainbowCaret(customCalloutEditor, TEXT);
				controlsTop += editorHeight;
			}
		}
		int gap = 12;
		int cellWidth = (w - 28 - gap * (columns - 1)) / columns;
		boolean compact = cellWidth < 180;
		for (int index = firstEffect; index < lastEffect; index++) {
			SparklingAlertStyle.Effect effect = SparklingAlertStyle.EFFECTS.get(index);
			int pageIndex = index - firstEffect;
			int column = columns == 1 ? 0 : pageIndex / rows;
			int row = columns == 1 ? pageIndex : pageIndex % rows;
			int cellX = x + 14 + column * (cellWidth + gap);
			int cellY = controlsTop + row * rowHeight;
			int value = customAlertValue(effect);
			String level = effect.soundSong() ? AlertSounds.sparklingSongShortLabel(
				value == 0 ? AlertSounds.SPARKLING_SONG_OFF : value - 1)
				: effect.soundIntensity() ? AlertSounds.sparklingIntensityShortLabel(value)
				: effect.gradientSpeed() ? gradientSpeedLevel(value) : effectLevel(value);
			drawText(graphics, trim(effect.label(), compact
				? Math.max(24, cellWidth - 58)
				: Math.max(28, cellWidth - 64 - font.width(level))), cellX, cellY + 4, TEXT);
			if (!compact) drawText(graphics, level,
				cellX + cellWidth - 54 - font.width(level), cellY + 4,
				value == 0 ? DIM : CYAN);
			int plusX = cellX + cellWidth - 22;
			int minusX = plusX - 26;
			drawSmallEffectButton(graphics, minusX, cellY, "−", mouseX, mouseY,
				value > 0);
			drawSmallEffectButton(graphics, plusX, cellY, "+", mouseX, mouseY,
				value < effect.maximum());
			if (value > 0) hits.add(new Hit(minusX, cellY, minusX + 22, cellY + 22,
				() -> setCustomAlertValue(effect, value - 1)));
			if (value < effect.maximum()) hits.add(new Hit(plusX, cellY, plusX + 22, cellY + 22,
				() -> setCustomAlertValue(effect, value + 1)));
			int meterX = cellX;
			int meterY = cellY + rowHeight - 4;
			int meterW = Math.max(24, cellWidth - 58);
			for (int levelIndex = 1; levelIndex <= effect.maximum(); levelIndex++) {
				int left = meterX + meterW * (levelIndex - 1) / effect.maximum();
				int right = meterX + meterW * levelIndex / effect.maximum() - 2;
				graphics.fill(left, meterY, right, meterY + 4,
					levelIndex <= value ? BLUE : BORDER);
				int chosen = levelIndex;
				hits.add(new Hit(left, meterY - 2, right, meterY + 7,
					() -> setCustomAlertValue(effect, chosen)));
			}
		}

		int buttonY = y + h - 29;
		boolean resetArmed = System.currentTimeMillis() < customResetArmedUntil;
		drawButton(graphics, x + 14, buttonY, 88,
			resetArmed ? "Confirm Reset" : "New / Reset", mouseX, mouseY);
		drawButton(graphics, x + 108, buttonY, 112, "Saved Presets", mouseX, mouseY);
		hits.add(new Hit(x + 108, buttonY, x + 220, buttonY + 22,
			this::openCustomPresetMenu));
		if (!customPresetStatus.isEmpty()) drawCenteredText(graphics, customPresetStatus,
			x + w / 2, buttonY + 7, MUTED);
		drawButton(graphics, x + w - 74, buttonY, 60, "Done", mouseX, mouseY);
		hits.add(new Hit(x + 14, buttonY, x + 102, buttonY + 22,
			this::resetCustomSparklingAlert));
		hits.add(new Hit(x + w - 74, buttonY, x + w - 14, buttonY + 22,
			this::closeCustomSparklingPanel));
		if (customPresetMenu) {
			customPresetHitStart = hits.size();
			drawCustomPresetMenu(graphics, mouseX, mouseY, x, y, w, h);
		}
	}

	private void updateCustomPreviewSong() {
		if (!customSparklingPanel) return;
		if (FullScreenAlert.previewPaused()) {
			AlertSounds.stopSparklingPreview(Minecraft.getInstance());
			return;
		}
		SparklingAlertStyle style = SparklingAlertStyle.custom();
		int song = style.soundSong();
		int theme = style.soundTheme();
		float duration = style.durationSeconds();
		int volume = style.soundVolumePercent();
		long now = System.currentTimeMillis();
		if (song == AlertSounds.SPARKLING_SONG_OFF) {
			if (previewSong != song) AlertSounds.stopSparklingPreview(Minecraft.getInstance());
			previewSong = song;
			return;
		}
		long loopMillis = Math.max(1_000L, Math.round(duration * 1_000f));
		if (song != previewSong || theme != previewTheme || duration != previewDuration
				|| volume != previewVolume || now - previewSongStartedAt >= loopMillis) {
			if (!customPreviewResuming) FullScreenAlert.restartPreview();
			long resumeOffset = customPreviewResuming
				? Math.floorMod(FullScreenAlert.previewAgeMillis(), loopMillis) : 0;
			AlertSounds.playSparklingPreview(Minecraft.getInstance(), song, theme, duration,
				volume, resumeOffset);
			previewSong = song;
			previewTheme = theme;
			previewDuration = duration;
			previewVolume = volume;
			previewSongStartedAt = customPreviewResuming ? now - resumeOffset : now;
			customPreviewResuming = false;
		}
	}

	private void drawSmallEffectButton(GuiGraphicsExtractor graphics, int x, int y,
			String label, int mouseX, int mouseY, boolean enabled) {
		boolean hovered = enabled && inside(mouseX, mouseY, x, y, x + 22, y + 22);
		graphics.fill(x, y, x + 22, y + 22, hovered ? SELECTED : CARD);
		outline(graphics, x, y, 22, 22, enabled ? (hovered ? CYAN : BLUE) : BORDER);
		drawCenteredText(graphics, label, x + 11, y + 7, enabled ? TEXT : DIM);
	}

	private void drawCustomPresetMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			int parentX, int parentY, int parentWidth, int parentHeight) {
		SafariConfig.SparklingConfig config = ConfigManager.get().sparkling;
		if (config.customAlertSavedPresets == null) config.customAlertSavedPresets = new ArrayList<>();
		List<Integer> order = customPresetDisplayOrder(config);
		int width = Math.min(520, parentWidth - 28);
		int presetsPerPage = parentHeight < 390 ? 4 : 6;
		int pageCount = Math.max(1, (order.size() + presetsPerPage - 1) / presetsPerPage);
		customPresetPage = Math.clamp(customPresetPage, 0, pageCount - 1);
		int first = customPresetPage * presetsPerPage;
		int visible = Math.min(presetsPerPage, order.size() - first);
		boolean validSelection = customSavedPresetIndex >= 0
			&& customSavedPresetIndex < config.customAlertSavedPresets.size();
		int selectedVisibleRow = -1;
		for (int row = 0; row < visible; row++) {
			if (order.get(first + row) == customSavedPresetIndex) selectedVisibleRow = row;
		}
		int listHeight = Math.max(36, visible * 36);
		int actionHeight = selectedVisibleRow >= 0 && !customPresetNaming ? 52 : 0;
		int namingHeight = customPresetNaming ? 28 : 0;
		int height = Math.min(parentHeight - 18,
			68 + listHeight + actionHeight + namingHeight);
		int x = parentX + (parentWidth - width) / 2;
		int y = parentY + (parentHeight - height) / 2;
		graphics.fill(parentX, parentY, parentX + parentWidth, parentY + parentHeight, 0xCC000000);
		graphics.fill(x, y, x + width, y + height, SURFACE);
		outline(graphics, x, y, width, height, CYAN);
		drawText(graphics, "SAVED SPARKLING ALERTS", x + 12, y + 12, TEXT);
		drawButton(graphics, x + width - 66, y + 6, 54, "Close", mouseX, mouseY);
		hits.add(new Hit(x + width - 66, y + 6, x + width - 12, y + 28,
			this::closeCustomPresetMenu));

		int rowY = y + 34;
		if (config.customAlertSavedPresets.isEmpty()) {
			drawCenteredText(graphics, "No saved presets", x + width / 2, rowY + 8, MUTED);
			rowY += 36;
		} else {
			for (int row = 0; row < visible; row++) {
				int index = order.get(first + row);
				SafariConfig.SavedAlertPreset preset = config.customAlertSavedPresets.get(index);
				boolean selected = customSavedPresetIndex == index;
				boolean matchesCurrent = preset.recipe.equals(SparklingAlertStyle.exportCustom());
				boolean modified = selected && !matchesCurrent;
				graphics.fill(x + 10, rowY, x + width - 10, rowY + 32,
					selected ? SELECTED : CARD);
				outline(graphics, x + 10, rowY, width - 20, 32, selected ? CYAN : BORDER);
				String star = preset.favorite ? "★ " : "";
				drawText(graphics, trim(star + preset.name, width - 88), x + 17, rowY + 5,
					preset.favorite ? GOLD : TEXT);
				drawText(graphics,
					trim(SparklingAlertStyle.describeCustomPreset(preset.recipe), width - 42),
					x + 17, rowY + 18, MUTED);
				if (modified) drawText(graphics, "Modified", x + width - 70, rowY + 5, GOLD);
				else if (matchesCurrent) drawScaledCenteredText(graphics, "✔",
					x + width - 25, rowY + 11, 1.3f, CYAN);
				int chosenIndex = index;
				hits.add(new Hit(x + 10, rowY, x + width - 10, rowY + 32,
					() -> customSavedPresetIndex = customSavedPresetIndex == chosenIndex
						? -1 : chosenIndex));
				rowY += 36;
				if (selected && !customPresetNaming) {
					drawPresetActions(graphics, mouseX, mouseY, x, rowY, width,
						chosenIndex, preset);
					rowY += 52;
				}
			}
		}

		if (customPresetNaming) {
			drawText(graphics, "Preset Name", x + 12, rowY + 7, TEXT);
			int fieldX = x + 88;
			drawInlineEditorFrame(graphics, fieldX, rowY, width - 190, 20);
			customPresetNameBounds.set(fieldX, rowY,
				fieldX + width - 190, rowY + 20);
			if (customPresetNameEditor != null) {
				customPresetNameEditor.visible = true;
				customPresetNameEditor.active = true;
				customPresetNameEditor.setX(fieldX + 5);
				customPresetNameEditor.setY(rowY + 6);
				customPresetNameEditor.setWidth(width - 200);
				UIDraw.updateRainbowCaret(customPresetNameEditor, TEXT);
			}
			drawButton(graphics, x + width - 94, rowY, 38,
				customPresetRenaming ? "Rename" : "Save", mouseX, mouseY);
			drawButton(graphics, x + width - 52, rowY, 40, "Cancel", mouseX, mouseY);
			hits.add(new Hit(x + width - 94, rowY, x + width - 56, rowY + 22,
				this::finishPresetSave));
			hits.add(new Hit(x + width - 52, rowY, x + width - 12, rowY + 22,
				this::cancelPresetNaming));
		} else {
			drawButton(graphics, x + 10, rowY, 90, "Save Current", mouseX, mouseY);
			drawButton(graphics, x + 106, rowY, 70, "Import", mouseX, mouseY);
			if (pageCount > 1) {
				drawButton(graphics, x + width - 148, rowY, 42, "Prev", mouseX, mouseY);
				drawCenteredText(graphics, (customPresetPage + 1) + "/" + pageCount,
					x + width - 83, rowY + 7, MUTED);
				drawButton(graphics, x + width - 52, rowY, 40, "Next", mouseX, mouseY);
				if (customPresetPage > 0) hits.add(new Hit(x + width - 148, rowY,
					x + width - 106, rowY + 22, () -> customPresetPage--));
				if (customPresetPage + 1 < pageCount) hits.add(new Hit(x + width - 52, rowY,
					x + width - 12, rowY + 22, () -> customPresetPage++));
			}
			hits.add(new Hit(x + 10, rowY, x + 100, rowY + 22, this::beginPresetSave));
			hits.add(new Hit(x + 106, rowY, x + 176, rowY + 22,
				() -> customPresetAction(4)));
		}
	}

	private void drawPresetActions(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			int x, int y, int width, int index, SafariConfig.SavedAlertPreset preset) {
		String deleteLabel = customDeleteArmedIndex == index
			&& System.currentTimeMillis() < customDeleteArmedUntil ? "Confirm Delete" : "Delete";
		String[][] labels = {
			{"Load", "Update", "Rename", preset.favorite ? "Unfavorite" : "Favorite"},
			{"Copy", "Duplicate", deleteLabel}
		};
		int[][] actions = {{0, 1, 2, 3}, {4, 5, 6}};
		for (int row = 0; row < labels.length; row++) {
			int gap = 5;
			int available = width - 20 - gap * (labels[row].length - 1);
			int buttonWidth = available / labels[row].length;
			int buttonX = x + 10;
			for (int column = 0; column < labels[row].length; column++) {
				int actualWidth = column == labels[row].length - 1
					? x + width - 10 - buttonX : buttonWidth;
				drawButton(graphics, buttonX, y + row * 26, actualWidth,
					labels[row][column], mouseX, mouseY);
				int action = actions[row][column];
				hits.add(new Hit(buttonX, y + row * 26, buttonX + actualWidth,
					y + row * 26 + 22, () -> savedPresetRowAction(index, action)));
				buttonX += actualWidth + gap;
			}
		}
	}

	private static List<Integer> customPresetDisplayOrder(SafariConfig.SparklingConfig config) {
		List<Integer> order = new ArrayList<>();
		for (int index = 0; index < config.customAlertSavedPresets.size(); index++) order.add(index);
		order.sort((left, right) -> Boolean.compare(
			config.customAlertSavedPresets.get(right).favorite,
			config.customAlertSavedPresets.get(left).favorite));
		return order;
	}

	private void openCustomPresetMenu() {
		customDeleteArmedIndex = -1;
		customDeleteArmedUntil = 0;
		customSavedPresetIndex = -1;
		customPresetMenu = true;
	}

	private void beginPresetSave() {
		beginPresetNaming("Custom Alert", false);
	}

	private void beginPresetRename() {
		SafariConfig.SparklingConfig config = ConfigManager.get().sparkling;
		if (customSavedPresetIndex < 0
				|| customSavedPresetIndex >= config.customAlertSavedPresets.size()) return;
		beginPresetNaming(config.customAlertSavedPresets.get(customSavedPresetIndex).name, true);
	}

	private void beginPresetNaming(String initialValue, boolean renaming) {
		cancelPresetNaming();
		customPresetNaming = true;
		customPresetRenaming = renaming;
		customPresetNameEditor = new EditBox(font, 0, 0, 150, 9,
			Component.literal("Saved preset name"));
		customPresetNameEditor.setBordered(false);
		customPresetNameEditor.setMaxLength(40);
		customPresetNameEditor.setValue(initialValue);
		customPresetNameEditor.setCursorPosition(customPresetNameEditor.getValue().length());
		UIDraw.rainbowEditBox(customPresetNameEditor, font);
		addRenderableWidget(customPresetNameEditor);
		setFocused(customPresetNameEditor);
		customPresetNameEditor.setFocused(true);
	}

	private void finishPresetSave() {
		SafariConfig.SparklingConfig config = ConfigManager.get().sparkling;
		if (customPresetRenaming && customSavedPresetIndex >= 0
				&& customSavedPresetIndex < config.customAlertSavedPresets.size()) {
			SafariConfig.SavedAlertPreset preset =
				config.customAlertSavedPresets.get(customSavedPresetIndex);
			String requested = customPresetNameEditor == null ? preset.name
				: customPresetNameEditor.getValue();
			preset.name = uniquePresetName(config, requested, customSavedPresetIndex);
			customPresetStatus = "Renamed preset to " + preset.name;
			ConfigManager.save();
		} else {
			customPresetAction(0);
		}
		List<Integer> order = customPresetDisplayOrder(config);
		int displayIndex = order.indexOf(customSavedPresetIndex);
		customPresetPage = Math.max(0, displayIndex / 6);
		cancelPresetNaming();
	}

	private void cancelPresetNaming() {
		customPresetNaming = false;
		customPresetRenaming = false;
		if (customPresetNameEditor != null) {
			customPresetNameEditor.setFocused(false);
			removeWidget(customPresetNameEditor);
			customPresetNameEditor = null;
		}
		setFocused(null);
	}

	private void closeCustomPresetMenu() {
		cancelPresetNaming();
		customSavedPresetIndex = -1;
		customPresetMenu = false;
	}

	private void savedPresetRowAction(int index, int action) {
		SafariConfig.SparklingConfig config = ConfigManager.get().sparkling;
		if (config.customAlertSavedPresets == null || index < 0
				|| index >= config.customAlertSavedPresets.size()) return;
		customSavedPresetIndex = index;
		SafariConfig.SavedAlertPreset preset = config.customAlertSavedPresets.get(index);
		switch (action) {
			case 0 -> {
				if (SparklingAlertStyle.importCustom(preset.recipe)) {
					customPresetStatus = "Loaded " + preset.name;
					refreshCustomEditors();
				}
			}
			case 1 -> {
				preset.recipe = SparklingAlertStyle.exportCustom();
				customPresetStatus = "Updated " + preset.name;
				ConfigManager.save();
			}
			case 2 -> beginPresetRename();
			case 3 -> {
				preset.favorite = !preset.favorite;
				customPresetStatus = preset.favorite ? "Favorited " + preset.name
					: "Unfavorited " + preset.name;
				ConfigManager.save();
			}
			case 4 -> {
				Minecraft.getInstance().keyboardHandler.setClipboard(preset.recipe);
				customPresetStatus = "Copied " + preset.name;
			}
			case 5 -> {
				String copyName = uniquePresetName(config, preset.name + " Copy");
				SafariConfig.SavedAlertPreset copy =
					new SafariConfig.SavedAlertPreset(copyName, preset.recipe);
				copy.favorite = preset.favorite;
				config.customAlertSavedPresets.add(copy);
				ConfigManager.save();
				customSavedPresetIndex = config.customAlertSavedPresets.size() - 1;
				customPresetStatus = "Duplicated " + preset.name;
			}
			case 6 -> {
				long now = System.currentTimeMillis();
				if (customDeleteArmedIndex != index || now >= customDeleteArmedUntil) {
					customDeleteArmedIndex = index;
					customDeleteArmedUntil = now + 5_000L;
					customPresetStatus = "Select Delete again to confirm";
					return;
				}
				String removed = config.customAlertSavedPresets.remove(index).name;
				customSavedPresetIndex = -1;
				customDeleteArmedIndex = -1;
				customDeleteArmedUntil = 0;
				customPresetStatus = "Deleted " + removed;
				ConfigManager.save();
			}
			default -> { }
		}
	}

	private static String uniquePresetName(SafariConfig.SparklingConfig config,
			String requestedName) {
		return uniquePresetName(config, requestedName, -1);
	}

	private static String uniquePresetName(SafariConfig.SparklingConfig config,
			String requestedName, int ignoredIndex) {
		String base = requestedName == null || requestedName.isBlank()
			? "Custom Alert" : requestedName.trim();
		String candidate = base;
		for (int suffix = 2; ; suffix++) {
			boolean used = false;
			for (int index = 0; index < config.customAlertSavedPresets.size(); index++) {
				if (index == ignoredIndex) continue;
				SafariConfig.SavedAlertPreset preset = config.customAlertSavedPresets.get(index);
				if (preset.name.equalsIgnoreCase(candidate)) {
					used = true;
					break;
				}
			}
			if (!used) return candidate;
			candidate = base + " (" + suffix + ")";
		}
	}

	private static String customEffectPageName(int page, int effectsPerPage) {
		int group = 0;
		int within = page;
		int pages = 1;
		while (group < SparklingAlertStyle.EFFECT_PAGE_NAMES.length) {
			pages = (customEffectCategoryCount(group) + effectsPerPage - 1) / effectsPerPage;
			if (within < pages) break;
			within -= pages;
			group++;
		}
		group = Math.min(group, SparklingAlertStyle.EFFECT_PAGE_NAMES.length - 1);
		return SparklingAlertStyle.EFFECT_PAGE_NAMES[group]
			+ (pages == 1 ? "" : " " + (within + 1));
	}

	private static int customEffectCategoryCount(int category) {
		int start = category * SparklingAlertStyle.EFFECTS_PER_PAGE;
		return Math.clamp(SparklingAlertStyle.EFFECTS.size() - start, 0,
			SparklingAlertStyle.EFFECTS_PER_PAGE);
	}

	private int customAlertValue(SparklingAlertStyle.Effect effect) {
		int value = effect.value(ConfigManager.get().sparkling);
		return effect.soundSong()
			? value == AlertSounds.SPARKLING_SONG_OFF ? 0 : value + 1
			: value;
	}

	private void setCustomAlertValue(SparklingAlertStyle.Effect effect, int value) {
		int stored = effect.soundSong()
			? value == 0 ? AlertSounds.SPARKLING_SONG_OFF : value - 1
			: value;
		effect.set(ConfigManager.get().sparkling, stored);
		SparklingAlertStyle.invalidateCustom();
	}

	private void previewCustomDuration(String value) {
		if (value == null || value.isBlank()) return;
		try {
			float seconds = Float.parseFloat(value);
			if (seconds < 1 || seconds > 999) return;
			ConfigManager.get().sparkling.customAlertDuration = seconds;
			SparklingAlertStyle.invalidateCustom();
		} catch (NumberFormatException ignored) {
		}
	}

	private static String formatDuration(float seconds) {
		return seconds == Math.round(seconds)
			? Integer.toString(Math.round(seconds))
			: String.format(java.util.Locale.ROOT, "%.1f", seconds);
	}

	private void applyCustomDurationEditor() {
		if (customDurationEditor == null) return;
		float current = Math.clamp(ConfigManager.get().sparkling.customAlertDuration, 1f, 999f);
		try {
			current = Math.clamp(Float.parseFloat(customDurationEditor.getValue()), 1f, 999f);
		} catch (NumberFormatException ignored) {
		}
		ConfigManager.get().sparkling.customAlertDuration = current;
		customDurationEditor.setValue(formatDuration(current));
		SparklingAlertStyle.invalidateCustom();
	}

	private void updateCustomDuration(double mouseX) {
		float progress = (float) ((mouseX - customDurationSliderLeft)
			/ Math.max(1, customDurationSliderWidth));
		int choice = Math.round(Math.clamp(progress, 0f, 1f)
			* (AlertSounds.sparklingDurationChoiceCount() - 1));
		float seconds = AlertSounds.sparklingDurationChoice(choice);
		ConfigManager.get().sparkling.customAlertDuration = seconds;
		if (customDurationEditor != null
				&& !formatDuration(seconds).equals(customDurationEditor.getValue())) {
			customDurationEditor.setValue(formatDuration(seconds));
		}
		SparklingAlertStyle.invalidateCustom();
	}

	private void previewCustomVolume(String value) {
		if (value == null || value.isBlank()) return;
		try {
			int percent = Integer.parseInt(value);
			if (percent < 0 || percent > SparklingAlertStyle.VOLUME_MANUAL_MAX) return;
			ConfigManager.get().sparkling.customAlertSoundVolume = percent;
			SparklingAlertStyle.invalidateCustom();
		} catch (NumberFormatException ignored) {
		}
	}

	private void applyCustomVolumeEditor() {
		if (customVolumeEditor == null) return;
		int current = Math.clamp(ConfigManager.get().sparkling.customAlertSoundVolume,
			0, SparklingAlertStyle.VOLUME_MANUAL_MAX);
		try {
			current = Math.clamp(Integer.parseInt(customVolumeEditor.getValue()), 0,
				SparklingAlertStyle.VOLUME_MANUAL_MAX);
		} catch (NumberFormatException ignored) {
		}
		ConfigManager.get().sparkling.customAlertSoundVolume = current;
		customVolumeEditor.setValue(Integer.toString(current));
		SparklingAlertStyle.invalidateCustom();
	}

	private void updateCustomVolume(double mouseX) {
		float progress = (float) ((mouseX - customVolumeSliderLeft)
			/ Math.max(1, customVolumeSliderWidth));
		int percent = Math.round(Math.clamp(progress, 0f, 1f)
			* SparklingAlertStyle.VOLUME_SLIDER_MAX);
		ConfigManager.get().sparkling.customAlertSoundVolume = percent;
		if (customVolumeEditor != null
				&& !Integer.toString(percent).equals(customVolumeEditor.getValue())) {
			customVolumeEditor.setValue(Integer.toString(percent));
		}
		SparklingAlertStyle.invalidateCustom();
	}

	private void resetCustomSparklingAlert() {
		long now = System.currentTimeMillis();
		if (now >= customResetArmedUntil) {
			customResetArmedUntil = now + 5_000L;
			customPresetStatus = "Select Reset again to confirm";
			return;
		}
		customResetArmedUntil = 0;
		SparklingAlertStyle.resetCustom();
		customSavedPresetIndex = -1;
		customPresetStatus = "New custom alert";
		if (customCalloutEditor != null) {
			customCalloutEditor.setValue(ConfigManager.get().sparkling.customAlertCalloutText);
		}
		if (customDurationEditor != null) {
			customDurationEditor.setValue(formatDuration(
				ConfigManager.get().sparkling.customAlertDuration));
		}
		if (customVolumeEditor != null) {
			customVolumeEditor.setValue(Integer.toString(
				ConfigManager.get().sparkling.customAlertSoundVolume));
		}
	}

	private void customPresetAction(int action) {
		SafariConfig.SparklingConfig config = ConfigManager.get().sparkling;
		if (config.customAlertSavedPresets == null) config.customAlertSavedPresets = new ArrayList<>();
		switch (action) {
			case 0 -> {
				String name = customPresetNameEditor == null ? "Custom Alert"
					: customPresetNameEditor.getValue();
				SparklingAlertStyle.saveCustomPreset(name);
				customSavedPresetIndex = config.customAlertSavedPresets.size() - 1;
				customPresetStatus = "Saved "
					+ config.customAlertSavedPresets.get(customSavedPresetIndex).name;
			}
			case 1 -> {
				if (config.customAlertSavedPresets.isEmpty()) {
					customPresetStatus = "No saved presets";
					return;
				}
				customSavedPresetIndex = Math.floorMod(customSavedPresetIndex + 1,
					config.customAlertSavedPresets.size());
				SafariConfig.SavedAlertPreset preset =
					config.customAlertSavedPresets.get(customSavedPresetIndex);
				if (SparklingAlertStyle.importCustom(preset.recipe)) {
					customPresetNameEditor.setValue(preset.name);
					customPresetStatus = "Loaded " + preset.name;
					refreshCustomEditors();
				}
			}
			case 2 -> {
				if (customSavedPresetIndex >= 0
						&& customSavedPresetIndex < config.customAlertSavedPresets.size()) {
					String removed = config.customAlertSavedPresets
						.remove(customSavedPresetIndex).name;
					customSavedPresetIndex = -1;
					customPresetStatus = "Deleted " + removed;
					ConfigManager.save();
				}
			}
			case 3 -> {
				Minecraft.getInstance().keyboardHandler.setClipboard(
					SparklingAlertStyle.exportCustom());
				customPresetStatus = "Copied preset";
			}
			case 4 -> {
				boolean imported = SparklingAlertStyle.importCustom(
					Minecraft.getInstance().keyboardHandler.getClipboard().trim());
				customPresetStatus = imported ? "Imported preset" : "Invalid preset";
				if (imported) {
					customSavedPresetIndex = -1;
					refreshCustomEditors();
				}
			}
			case 5 -> {
				String name = customPresetNameEditor == null ? "Custom Alert Copy"
					: customPresetNameEditor.getValue() + " Copy";
				SparklingAlertStyle.saveCustomPreset(name);
				customSavedPresetIndex = config.customAlertSavedPresets.size() - 1;
				customPresetNameEditor.setValue(
					config.customAlertSavedPresets.get(customSavedPresetIndex).name);
				customPresetStatus = "Duplicated preset";
			}
			default -> { }
		}
	}

	private void toggleCustomPreview() {
		FullScreenAlert.togglePreview();
		if (FullScreenAlert.previewPaused()) {
			AlertSounds.stopSparklingPreview(Minecraft.getInstance());
		} else {
			previewSong = Integer.MIN_VALUE;
			customPreviewResuming = true;
			updateCustomPreviewSong();
		}
	}

	private static String customEffectLoadLabel() {
		SafariConfig.SparklingConfig config = ConfigManager.get().sparkling;
		int load = 0;
		for (SparklingAlertStyle.Effect effect : SparklingAlertStyle.EFFECTS) {
			if (!effect.soundSong() && !effect.soundIntensity()) load += effect.value(config);
		}
		return load < 34 ? "Light" : load < 85 ? "Moderate" : load < 165 ? "Heavy" : "Extreme";
	}

	private void refreshCustomEditors() {
		SafariConfig.SparklingConfig config = ConfigManager.get().sparkling;
		if (customCalloutEditor != null) customCalloutEditor.setValue(config.customAlertCalloutText);
		if (customDurationEditor != null) customDurationEditor.setValue(
			formatDuration(config.customAlertDuration));
		if (customVolumeEditor != null) customVolumeEditor.setValue(
			Integer.toString(config.customAlertSoundVolume));
	}

	private static String effectLevel(int value) {
		return switch (value) {
			case 0 -> "Off";
			case 1 -> "Subtle";
			case 2 -> "Gentle";
			case 3 -> "Moderate";
			case 4 -> "Strong";
			case 5 -> "Intense";
			default -> "Maximum";
		};
	}

	private static String gradientSpeedLevel(int value) {
		return switch (value) {
			case 0 -> "Still";
			case 1 -> "Slow";
			case 2 -> "Normal";
			case 3 -> "Fast";
			default -> "Rapid";
		};
	}

	/** Role-based palette editor; every change is reflected by the screen behind it. */
	private void drawCustomThemePanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int columns = 3;
		int rows = (CUSTOM_THEME_ROLES.size() + columns - 1) / columns;
		int w = Math.min(620, width - 30);
		int h = Math.min(height - 30, 76 + rows * 30);
		int x = (width - w) / 2;
		int y = (height - h) / 2;
		graphics.fill(0, 0, width, height, 0x88000000);
		graphics.fill(x, y, x + w, y + h, SURFACE);
		outline(graphics, x, y, w, h, CYAN);
		drawText(graphics, "CUSTOM THEME", x + 14, y + 13, TEXT);
		drawText(graphics, "Select a role to edit it · Changes preview live",
			x + 14, y + 27, MUTED);

		int gap = 8;
		int cellWidth = (w - 28 - (columns - 1) * gap) / columns;
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		for (int index = 0; index < CUSTOM_THEME_ROLES.size(); index++) {
			CustomThemeRole role = CUSTOM_THEME_ROLES.get(index);
			int column = index % columns;
			int row = index / columns;
			int cellX = x + 14 + column * (cellWidth + gap);
			int cellY = y + 45 + row * 30;
			boolean hovered = inside(mouseX, mouseY, cellX, cellY,
				cellX + cellWidth, cellY + 24);
			graphics.fill(cellX, cellY, cellX + cellWidth, cellY + 24,
				hovered ? CARD_HOVER : CARD);
			outline(graphics, cellX, cellY, cellWidth, 24, hovered ? CYAN : BORDER);
			try {
				Field field = SafariConfig.DisplayConfig.class.getField(role.field());
				int colour = Colours.argb((String) field.get(display), 0xFFFFFFFF);
				graphics.fill(cellX + 5, cellY + 4, cellX + 33, cellY + 20, colour);
				outline(graphics, cellX + 5, cellY + 4, 28, 16, BORDER);
				drawText(graphics, role.label(), cellX + 41, cellY + 8,
					hovered ? TEXT : MUTED);
				hits.add(new Hit(cellX, cellY, cellX + cellWidth, cellY + 24,
					() -> openEditor(display, field, true)));
			} catch (ReflectiveOperationException ignored) {
			}
		}

		int buttonY = y + h - 29;
		drawButton(graphics, x + w - 74, buttonY, 60, "Done", mouseX, mouseY);
		hits.add(new Hit(x + w - 74, buttonY, x + w - 14, buttonY + 22,
			this::closeCustomThemePanel));
	}

	private void drawSlider(GuiGraphicsExtractor graphics, int x, int y, int width,
			float value, SettingRange slider) {
		float progress = (value - slider.minValue()) / (slider.maxValue() - slider.minValue());
		graphics.fill(x, y + 15, x + width, y + 18, BORDER);
		graphics.fill(x, y + 15, x + Math.round(width * Math.clamp(progress, 0f, 1f)), y + 18, BLUE);
		String label = formatNumber(value) + "  ✎";
		drawText(graphics, label, x + width - font.width(label), y + 2, TEXT);
	}

	private void drawColour(GuiGraphicsExtractor graphics, int x, int y, int width, int colour) {
		graphics.fill(x, y, x + width, y + 22, CARD);
		outline(graphics, x, y, width, 22, BORDER);
		graphics.fill(x + 5, y + 4, x + 35, y + 18, colour);
		outline(graphics, x + 5, y + 4, 30, 14, BORDER);
		String hex = "#%06X".formatted(colour & 0xFFFFFF);
		drawText(graphics, hex, x + 44, y + 7, TEXT);
	}

	private void drawTextValue(GuiGraphicsExtractor graphics, int x, int y, int width, String value) {
		graphics.fill(x, y, x + width, y + 22, CARD);
		outline(graphics, x, y, width, 22, BORDER);
		drawText(graphics, trim(value, width - 16), x + 8, y + 7, TEXT);
	}

	/** Keeps inline editing visually identical to the control it replaces. */
	private void drawInlineEditorFrame(GuiGraphicsExtractor graphics, int x, int y, int width) {
		drawInlineEditorFrame(graphics, x, y, width, 22);
	}

	private void drawInlineEditorFrame(GuiGraphicsExtractor graphics,
			int x, int y, int width, int height) {
		graphics.fill(x, y, x + width, y + height, CARD);
		outline(graphics, x, y, width, height, CYAN);
	}

	private void setInlineEditorBounds(int x, int y, int width, int height) {
		inlineEditorLeft = x;
		inlineEditorTop = y;
		inlineEditorRight = x + width;
		inlineEditorBottom = y + height;
	}

	private void setEditingSliderBounds(int x, int y, int width, int height) {
		editingSliderLeft = x;
		editingSliderTop = y;
		editingSliderRight = x + width;
		editingSliderBottom = y + height;
	}

	private void drawButton(GuiGraphicsExtractor graphics, int x, int y, int width, String label,
			int mouseX, int mouseY) {
		boolean hovered = inside(mouseX, mouseY, x, y, x + width, y + 22);
		graphics.fill(x, y, x + width, y + 22, hovered ? SELECTED : shade(BLUE, 0.34f));
		outline(graphics, x, y, width, 22, hovered ? CYAN : BLUE);
		drawCenteredText(graphics, label, x + width / 2, y + 7, TEXT);
	}

	private void drawSpecialSparklingConfirmation(GuiGraphicsExtractor graphics,
			int mouseX, int mouseY) {
		int w = Math.min(500, width - 40);
		int h = 150;
		int x = (width - w) / 2;
		int y = (height - h) / 2;
		graphics.fill(0, 0, width, height, 0xBB000000);
		graphics.fill(x, y, x + w, y + h, SURFACE);
		outline(graphics, x, y, w, h, RED);
		// The safety notice deliberately stays red even when the novelty theme owns
		// every decorative colour around it.
		graphics.centeredText(font, "EPILEPSY WARNING", x + w / 2, y + 15, RED);
		graphics.centeredText(font, "This option may affect photosensitive players.",
			x + w / 2, y + 40, RED);
		graphics.centeredText(font,
			pendingSparklingIntensity == SparklingAlertStyle.CUSTOM_INDEX
				? "The editor includes effect intensities that can increase flashing and motion."
				: "Higher effect intensities can increase flashing and motion.",
			x + w / 2, y + 54, RED);
		graphics.centeredText(font, "Please confirm that you want to continue.",
			x + w / 2, y + 68, RED);
		String intensity = SparklingAlertStyle.NAMES[
			Math.clamp(pendingSparklingIntensity, 0, SparklingAlertStyle.CUSTOM_INDEX)];
		drawCenteredText(graphics,
			pendingSparklingIntensity == SparklingAlertStyle.CUSTOM_INDEX
				? "Open Custom Alert Editor?" : "Use " + intensity + " intensity?",
			x + w / 2, y + 88, GOLD);
		boolean customChoice = pendingSparklingIntensity == SparklingAlertStyle.CUSTOM_INDEX;
		boolean canCustomizePreset = customChoice && pendingSparklingSourcePreset >= 0;
		int buttonY = y + 114;
		int cancelX = canCustomizePreset ? x + w / 2 - 192 : x + w / 2 - 108;
		int enableX = cancelX + 112;
		drawButton(graphics, cancelX, buttonY, 104, "Cancel", mouseX, mouseY);
		drawButton(graphics, enableX, buttonY, 104,
			customChoice ? "Open Editor" : "Use Preset", mouseX, mouseY);
		hits.add(new Hit(cancelX, buttonY, cancelX + 104, buttonY + 22,
			this::cancelSparklingIntensity));
		hits.add(new Hit(enableX, buttonY, enableX + 104, buttonY + 22, () -> {
			ConfigManager.get().sparkling.specialSparklingIntensity =
				Math.clamp(pendingSparklingIntensity, 1, SparklingAlertStyle.CUSTOM_INDEX);
			boolean custom = pendingSparklingIntensity == SparklingAlertStyle.CUSTOM_INDEX;
			specialSparklingConfirmation = false;
			pendingSparklingIntensity = -1;
			if (custom) openCustomSparklingPanel();
			else if (search != null) search.visible = true;
			ConfigManager.save();
		}));
		if (canCustomizePreset) {
			int customizeX = enableX + 112;
			drawButton(graphics, customizeX, buttonY, 160,
				"Customize Current Preset", mouseX, mouseY);
			hits.add(new Hit(customizeX, buttonY, customizeX + 160, buttonY + 22, () -> {
				int preset = Math.clamp(pendingSparklingSourcePreset, 0,
					SparklingAlertStyle.CUSTOM_INDEX - 1);
				SparklingAlertStyle.copyToCustom(SparklingAlertStyle.preset(preset));
				ConfigManager.get().sparkling.specialSparklingIntensity =
					SparklingAlertStyle.CUSTOM_INDEX;
				specialSparklingConfirmation = false;
				pendingSparklingIntensity = -1;
				openCustomSparklingPanel();
				ConfigManager.save();
			}));
		}
	}

	private void drawPartySyncConfirmation(GuiGraphicsExtractor graphics,
			int mouseX, int mouseY) {
		int w = Math.min(540, width - 40);
		int h = 158;
		int x = (width - w) / 2;
		int y = (height - h) / 2;
		graphics.fill(0, 0, width, height, 0xBB000000);
		graphics.fill(x, y, x + w, y + h, SURFACE);
		outline(graphics, x, y, w, h, GOLD);
		graphics.centeredText(font, "ENABLE PARTY SYNC?", x + w / 2, y + 15, GOLD);
		graphics.centeredText(font, "This sends one compact verification token in party chat.",
			x + w / 2, y + 38, TEXT);
		graphics.centeredText(font,
			"Objective updates are then shared through parsed party messages.",
			x + w / 2, y + 52, TEXT);
		graphics.centeredText(font,
			"It only activates when every party member has Safari Utils",
			x + w / 2, y + 70, MUTED);
		graphics.centeredText(font,
			"and has Enable Party Sync turned on.", x + w / 2, y + 84, MUTED);
		int cancelX = x + w / 2 - 112;
		int enableX = x + w / 2 + 8;
		drawButton(graphics, cancelX, y + 116, 104, "Cancel", mouseX, mouseY);
		drawButton(graphics, enableX, y + 116, 104, "Enable", mouseX, mouseY);
		hits.add(new Hit(cancelX, y + 116, cancelX + 104, y + 138,
			this::cancelPartySync));
		hits.add(new Hit(enableX, y + 116, enableX + 104, y + 138, () -> {
			ConfigManager.get().advanced.enablePartySync = true;
			partySyncConfirmation = false;
			if (search != null) search.visible = true;
			ConfigManager.save();
		}));
	}

	private void cancelPartySync() {
		ConfigManager.get().advanced.enablePartySync = false;
		partySyncConfirmation = false;
		if (search != null) search.visible = true;
	}

	private void drawFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (selectedArea == null || selectedTab == null) return;
		int footerTop = height - FOOTER_HEIGHT;
		int y = footerTop + (FOOTER_HEIGHT - 22) / 2;
		int available = width - workspaceLeft - 16;
		int resetWidth = available < 180 ? 70 : 90;
		int closeWidth = available < 180 ? 50 : 58;
		int gap = available < 180 ? 4 : 8;
		int resetX = width - 8 - resetWidth;
		int closeX = resetX - gap - closeWidth;
		String description = trim(selectedTab.description,
			Math.max(40, closeX - workspaceLeft - 32));
		if (closeX - workspaceLeft > 150) drawScaledText(graphics, description, workspaceLeft + 12,
			footerTop + (FOOTER_HEIGHT - font.lineHeight) / 2f + 1, 1.0f,
			MUTED);
		boolean armed = System.currentTimeMillis() < resetArmedUntil;
		drawButton(graphics, closeX, y, closeWidth, "Done", mouseX, mouseY);
		drawButton(graphics, resetX, y, resetWidth,
			armed ? available < 180 ? "Confirm" : "Confirm Reset"
				: available < 180 ? "Reset" : "Reset Tab",
			mouseX, mouseY);
		hits.add(new Hit(closeX, y, closeX + closeWidth, y + 22, this::onClose));
		hits.add(new Hit(resetX, y, resetX + resetWidth, y + 22, this::resetSelected));
	}

	private void drawScaledText(GuiGraphicsExtractor graphics, String text,
			float x, float y, float scale, int colour) {
		drawScaledText(graphics, Component.literal(text), x, y, scale, colour);
	}

	private void drawScaledText(GuiGraphicsExtractor graphics, Component text,
			float x, float y, float scale, int colour) {
		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		drawText(graphics, text, Math.round(x / scale), Math.round(y / scale), colour);
		graphics.pose().popMatrix();
	}

	private void drawText(GuiGraphicsExtractor graphics, String text,
			int x, int y, int colour) {
		drawText(graphics, Component.literal(text), x, y, colour);
	}

	private void drawText(GuiGraphicsExtractor graphics, Component text,
			int x, int y, int colour) {
		SpecialTheme.text(graphics, font, text, x, y, colour);
	}

	private void drawCenteredText(GuiGraphicsExtractor graphics, String text,
			int centerX, int y, int colour) {
		if (SpecialTheme.rainbow()) {
			SpecialTheme.rainbowText(graphics, font, text, centerX - font.width(text) / 2, y);
		} else graphics.centeredText(font, text, centerX, y, colour);
	}

	private void drawScaledCenteredText(GuiGraphicsExtractor graphics, String text,
			float centerX, float y, float scale, int colour) {
		drawScaledCenteredText(graphics, Component.literal(text), centerX, y, scale, colour);
	}

	private void drawScaledCenteredText(GuiGraphicsExtractor graphics, Component text,
			float centerX, float y, float scale, int colour) {
		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		int x = Math.round(centerX / scale - font.width(text) / 2f);
		drawText(graphics, text, x, Math.round(y / scale), colour);
		graphics.pose().popMatrix();
	}

	private void resetSelected() {
		if (selectedTab == null) return;
		if (System.currentTimeMillis() >= resetArmedUntil) {
			resetArmedUntil = System.currentTimeMillis() + 3_000L;
			return;
		}
		Map<Class<?>, Object> defaults = new HashMap<>();
		Set<VisibleSetting> tabSettings = new java.util.LinkedHashSet<>();
		for (SettingsPage page : selectedTab.pages) {
			for (Field field : page.fields) tabSettings.add(new VisibleSetting(page.owner, field));
		}
		for (VisibleSetting setting : tabSettings) {
			try {
				Object defaultOwner = defaults.computeIfAbsent(setting.owner.getClass(), type -> {
					try {
						return type.getDeclaredConstructor().newInstance();
					} catch (ReflectiveOperationException ignored) {
						return null;
					}
				});
				if (defaultOwner != null) setting.field.set(setting.owner, setting.field.get(defaultOwner));
			} catch (IllegalAccessException ignored) {
			}
		}
		ConfigManager.save();
		resetArmedUntil = 0;
	}

	private void drawEditorModal(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int w = Math.min(520, width - 40);
		int h = colourModalHeight();
		int x = (width - w) / 2;
		int y = (height - h) / 2;
		graphics.fill(0, 0, width, height, 0x99000000);
		graphics.fill(x, y, x + w, y + h, SURFACE);
		outline(graphics, x, y, w, h, CYAN);
		SettingInfo option = editingField.getAnnotation(SettingInfo.class);
		drawText(graphics, editingColour ? "Choose " + option.name()
			: editingNumber ? "Enter " + option.name() : "Edit " + option.name(),
			x + 14, y + 13, TEXT);
		drawInlineEditorFrame(graphics, x + 14, y + 28, w - 28, 18);
		editor.setX(x + 20);
		editor.setY(y + 33);
		editor.setWidth(w - 40);
		editor.setTextColor(TEXT);
		editor.setTextColorUneditable(MUTED);
		if (editingColour) {
			drawColourControls(graphics, x, y, w);
		}
		int buttonY = y + h - 30;
		drawButton(graphics, x + w - 142, buttonY, 60, "Cancel", mouseX, mouseY);
		drawButton(graphics, x + w - 74, buttonY, 60, "Apply", mouseX, mouseY);
		hits.add(new Hit(x + w - 142, buttonY, x + w - 82, buttonY + 22, this::cancelEditor));
		hits.add(new Hit(x + w - 74, buttonY, x + w - 14, buttonY + 22, this::applyEditor));
	}

	private void openEditor(Object owner, Field field, boolean colour) {
		try {
			editingOwner = owner;
			editingField = field;
			editingOriginal = (String) field.get(owner);
			editingColour = colour;
			editingNumber = false;
			editingInlineText = false;
			editingNumberOriginal = null;
			int w = Math.min(520, width - 40);
			int h = colourModalHeight();
			int x = (width - w) / 2;
			int y = (height - h) / 2;
			editor = new EditBox(font, x + 20, y + 33, w - 40, 10, Component.literal("Value"));
			editor.setBordered(false);
			editor.setTextColor(TEXT);
			editor.setTextColorUneditable(MUTED);
			editor.setMaxLength(240);
			UIDraw.rainbowEditBox(editor, font);
			int currentColour = Colours.argb(editingOriginal, 0xFFFFFFFF);
			setEditingColourState(currentColour);
			if (colour && !editingAllowsAlpha()) editingAlpha = 255;
			editor.setValue(colour
				? (editingAlpha == 255 ? "#%06X".formatted(currentColour & 0xFFFFFF)
					: "#%08X".formatted(currentColour))
				: editingOriginal);
			editor.setResponder(this::previewEditor);
			addRenderableWidget(editor);
			setFocused(editor);
			editor.setFocused(true);
			setInitialFocus(editor);
			editor.setCursorPosition(editor.getValue().length());
			editor.setHighlightPos(editor.getValue().length());
			if (search != null) search.visible = false;
		} catch (IllegalAccessException ignored) {
		}
	}

	/** Edits text directly in the setting row instead of opening a modal. */
	private void openInlineTextEditor(Object owner, Field field, int x, int y, int width) {
		if (editingInlineText && editingField == field && editingOwner == owner) return;
		if (editor != null) applyEditor();
		try {
			editingOwner = owner;
			editingField = field;
			editingOriginal = (String) field.get(owner);
			editingColour = false;
			editingNumber = false;
			editingInlineText = true;
			editingNumberOriginal = null;
			editor = new EditBox(font, x + 8, y + 7, width - 16, 10, Component.literal("Value"));
			editor.setBordered(false);
			editor.setTextColor(TEXT);
			editor.setTextColorUneditable(MUTED);
			editor.setMaxLength(240);
			UIDraw.rainbowEditBox(editor, font);
			editor.setValue(editingOriginal);
			editor.setResponder(this::previewEditor);
			addRenderableWidget(editor);
			setFocused(editor);
			editor.setFocused(true);
			setInitialFocus(editor);
			editor.setCursorPosition(editor.getValue().length());
		} catch (IllegalAccessException ignored) {
		}
	}

	private void openSliderEditor(Object owner, Field field, int x, int y, int width) {
		try {
			if (editor != null) applyEditor();
			editingOwner = owner;
			editingField = field;
			editingColour = false;
			editingNumber = true;
			editingInlineText = true;
			editingNumberOriginal = (Number) field.get(owner);
			editingOriginal = null;
			editor = new EditBox(font, x + 4, y + 4, width - 4, 10, Component.literal("Value"));
			editor.setBordered(false);
			editor.setTextColor(TEXT);
			editor.setTextColorUneditable(MUTED);
			editor.setMaxLength(32);
			UIDraw.rainbowEditBox(editor, font);
			editor.setValue(formatNumber(editingNumberOriginal.floatValue()));
			editor.setResponder(this::previewEditor);
			addRenderableWidget(editor);
			setFocused(editor);
			editor.setFocused(true);
			setInitialFocus(editor);
			editor.setCursorPosition(editor.getValue().length());
			editor.setHighlightPos(editor.getValue().length());
			setInlineEditorBounds(x, y, width, 16);
		} catch (IllegalAccessException ignored) {
		}
	}

	private void drawColourControls(GuiGraphicsExtractor graphics, int x, int y, int w) {
		int selected = colourFromState();
		drawChecker(graphics, x + 14, y + 54, 72, 40);
		graphics.fill(x + 14, y + 54, x + 86, y + 94, selected);
		outline(graphics, x + 14, y + 54, 72, 40, BORDER);
		String previewLabel = "PREVIEW";
		drawText(graphics, previewLabel,
			x + 14 + (72 - font.width(previewLabel)) / 2, y + 99, MUTED);

		colourFieldLeft = x + 100;
		colourFieldTop = y + 52;
		colourFieldWidth = w - 114;
		colourFieldHeight = 96;
		// One vertical gradient per saturation slice replaces the old grid of over a
		// thousand rectangles while preserving a smooth two-dimensional field.
		int columns = 32;
		for (int column = 0; column < columns; column++) {
			float saturation = column / (float) (columns - 1);
			int x1 = colourFieldLeft + colourFieldWidth * column / columns;
			int x2 = colourFieldLeft + colourFieldWidth * (column + 1) / columns;
			graphics.fillGradient(x1, colourFieldTop, x2, colourFieldTop + colourFieldHeight,
				hsb(255, editingHue, saturation, 1f), 0xFF000000);
		}
		outline(graphics, colourFieldLeft, colourFieldTop,
			colourFieldWidth, colourFieldHeight, BORDER);
		int selectorX = colourFieldLeft + Math.round(editingSaturation * colourFieldWidth);
		int selectorY = colourFieldTop + Math.round((1f - editingBrightness) * colourFieldHeight);
		graphics.fill(selectorX - 5, selectorY - 5, selectorX + 6, selectorY + 6, BORDER);
		graphics.fill(selectorX - 4, selectorY - 4, selectorX + 5, selectorY + 5, TEXT);
		graphics.fill(selectorX - 2, selectorY - 2, selectorX + 3, selectorY + 3,
			hsb(255, editingHue, editingSaturation, editingBrightness));

		drawText(graphics, "Hue", x + 14, y + 156, MUTED);
		hueSliderLeft = x + 54;
		hueSliderTop = y + 154;
		hueSliderWidth = w - 68;
		// Two-pixel samples look continuous while keeping the modal inexpensive.
		int hueSegments = Math.max(1, hueSliderWidth / 2);
		for (int i = 0; i < hueSegments; i++) {
			int x1 = hueSliderLeft + hueSliderWidth * i / hueSegments;
			int x2 = hueSliderLeft + hueSliderWidth * (i + 1) / hueSegments;
			graphics.fill(x1, hueSliderTop, x2, hueSliderTop + 12,
				hsb(255, i / (float) hueSegments, 1f, 1f));
		}
		outline(graphics, hueSliderLeft, hueSliderTop, hueSliderWidth, 12, BORDER);
		drawSliderMarker(graphics, hueSliderLeft + Math.round(editingHue * hueSliderWidth),
			hueSliderTop, 12);
		if (!editingAllowsAlpha()) {
			alphaSliderWidth = 0;
			drawText(graphics, "Drag the gradients or enter #RRGGBB", x + 14, y + 184, DIM);
			return;
		}

		drawText(graphics, "Opacity", x + 14, y + 184, MUTED);
		alphaSliderLeft = x + 62;
		alphaSliderTop = y + 182;
		alphaSliderWidth = w - 76;
		drawChecker(graphics, alphaSliderLeft, alphaSliderTop, alphaSliderWidth, 12);
		int rgb = selected & 0xFFFFFF;
		for (int i = 0; i < 36; i++) {
			int x1 = alphaSliderLeft + alphaSliderWidth * i / 36;
			int x2 = alphaSliderLeft + alphaSliderWidth * (i + 1) / 36;
			int alpha = 1 + Math.round(254f * i / 35f);
			graphics.fill(x1, alphaSliderTop, x2, alphaSliderTop + 12, alpha << 24 | rgb);
		}
		outline(graphics, alphaSliderLeft, alphaSliderTop, alphaSliderWidth, 12, BORDER);
		int alphaX = alphaSliderLeft
			+ Math.round((editingAlpha - 1) / 254f * alphaSliderWidth);
		drawSliderMarker(graphics, alphaX, alphaSliderTop, 12);
		drawText(graphics, Math.round(editingAlpha / 255f * 100f) + "%",
			x + w - 42, y + 199, TEXT);
		drawText(graphics, "Drag the gradients or enter #RRGGBB / #AARRGGBB",
			x + 14, y + 214, DIM);
	}

	private int colourModalHeight() {
		return editingColour ? editingAllowsAlpha() ? 270 : 238 : 92;
	}

	private boolean editingAllowsAlpha() {
		return editingField != null
			&& ((editingField.getDeclaringClass() == SafariConfig.DisplayConfig.class
				&& editingField.getName().startsWith("customTheme"))
				|| editingField.getName().equals("bannerBackgroundColour"));
	}

	private static void drawChecker(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		int size = 8;
		for (int row = 0; row * size < h; row++) {
			for (int column = 0; column * size < w; column++) {
				int colour = (row + column & 1) == 0 ? 0xFFB8B8B8 : 0xFF666666;
				graphics.fill(x + column * size, y + row * size,
					Math.min(x + w, x + (column + 1) * size),
					Math.min(y + h, y + (row + 1) * size), colour);
			}
		}
	}

	private void drawSliderMarker(GuiGraphicsExtractor graphics,
			int x, int y, int height) {
		graphics.fill(x - 2, y - 2, x + 3, y + height + 2, BORDER);
		graphics.fill(x - 1, y - 1, x + 2, y + height + 1, TEXT);
	}

	private static int hsb(int alpha, float hue, float saturation, float brightness) {
		return alpha << 24 | java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
	}

	private int colourFromState() {
		return hsb(editingAlpha, editingHue, editingSaturation, editingBrightness);
	}

	private void setEditingColourState(int colour) {
		float[] hsb = java.awt.Color.RGBtoHSB(colour >> 16 & 0xFF,
			colour >> 8 & 0xFF, colour & 0xFF, null);
		editingHue = hsb[0];
		editingSaturation = hsb[1];
		editingBrightness = hsb[2];
		editingAlpha = Math.max(1, colour >>> 24);
	}

	private boolean updateColourControl(double mouseX, double mouseY) {
		if (!editingColour || editor == null) return false;
		if (inside(mouseX, mouseY, colourFieldLeft, colourFieldTop,
			colourFieldLeft + colourFieldWidth, colourFieldTop + colourFieldHeight)) {
			editingSaturation = Math.clamp((float) ((mouseX - colourFieldLeft) / colourFieldWidth), 0f, 1f);
			editingBrightness = 1f - Math.clamp(
				(float) ((mouseY - colourFieldTop) / colourFieldHeight), 0f, 1f);
			commitColourControls();
			return true;
		}
		if (inside(mouseX, mouseY, hueSliderLeft, hueSliderTop,
			hueSliderLeft + hueSliderWidth, hueSliderTop + 12)) {
			editingHue = Math.clamp((float) ((mouseX - hueSliderLeft) / hueSliderWidth),
				0f, Math.nextDown(1f));
			commitColourControls();
			return true;
		}
		if (editingAllowsAlpha() && alphaSliderWidth > 0
			&& inside(mouseX, mouseY, alphaSliderLeft, alphaSliderTop,
			alphaSliderLeft + alphaSliderWidth, alphaSliderTop + 12)) {
			float progress = Math.clamp((float) ((mouseX - alphaSliderLeft) / alphaSliderWidth), 0f, 1f);
			editingAlpha = 1 + Math.round(progress * 254f);
			commitColourControls();
			return true;
		}
		return false;
	}

	private void commitColourControls() {
		int colour = colourFromState();
		updatingColourControls = true;
		editor.setValue(editingAlpha == 255 ? "#%06X".formatted(colour & 0xFFFFFF)
			: "#%08X".formatted(colour));
		updatingColourControls = false;
	}

	private void previewEditor(String value) {
		if (editingField == null) return;
		try {
			if (editingNumber) {
				float parsed = Float.parseFloat(value);
				if (!Float.isFinite(parsed)) return;
				if (editingField.getType() == int.class) editingField.setInt(editingOwner, Math.round(parsed));
				else editingField.setFloat(editingOwner, parsed);
			} else if (editingColour) {
				String pattern = editingAllowsAlpha()
					? "#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?" : "#[0-9a-fA-F]{6}";
				if (!value.matches(pattern)) return;
				long parsed = Long.parseLong(value.substring(1), 16);
				int argb = value.length() == 7 ? 0xFF000000 | (int) parsed : (int) parsed;
				if (!updatingColourControls) setEditingColourState(argb);
				editingField.set(editingOwner, Colours.stored(argb));
			} else editingField.set(editingOwner, value);
		} catch (IllegalAccessException | NumberFormatException ignored) {
		}
	}

	private void applyEditor() {
		ConfigManager.save();
		closeEditor();
	}

	private void cancelEditor() {
		try {
			if (editingField != null) {
				if (editingNumber) editingField.set(editingOwner, editingNumberOriginal);
				else editingField.set(editingOwner, editingOriginal);
			}
		} catch (IllegalAccessException ignored) {
		}
		closeEditor();
	}

	private void closeEditor() {
		if (editor != null) removeWidget(editor);
		editor = null;
		editingField = null;
		editingOwner = null;
		editingNumber = false;
		editingInlineText = false;
		editingNumberOriginal = null;
		inlineEditorLeft = inlineEditorTop = inlineEditorRight = inlineEditorBottom = 0;
		editingSliderLeft = editingSliderTop = editingSliderRight = editingSliderBottom = 0;
		if (search != null) search.visible = !customThemePanel && !customSparklingPanel;
	}

	private void drawUnlockPanel(GuiGraphicsExtractor graphics) {
		long now = System.currentTimeMillis();
		if (signalCompletedAt > 0 && now - signalCompletedAt >= 900L) {
			completeAdvancedUnlock();
			return;
		}
		int size = Math.min(340, Math.min(width - 30, height - 30));
		int w = size;
		int h = size;
		int x = (width - w) / 2;
		int y = (height - h) / 2;
		graphics.fill(0, 0, width, height, 0xAA000000);
		graphics.fillGradient(x, y, x + w, y + h, BACKGROUND, SURFACE);
		SpecialTheme.stars(graphics, x + 2, y + 2, w - 4, h - 4, 2.15f, true);
		SpecialTheme.border(graphics, x, y, w, h, 2);
		int[][] nodes = constellation(x, y, size);
		boolean completedEffect = signalCompletedAt > 0;
		long frame = RainbowColours.frameId();
		if (unlockGeometryFrame != frame || unlockGeometryProgress != unlockProgress
			|| unlockGeometryComplete != completedEffect) {
			unlockGeometryLength = buildUnlockGeometry(unlockGeometry,
				nodes, x + w / 2, y + h / 2,
				unlockProgress, completedEffect, now);
			unlockGeometryFrame = frame;
			unlockGeometryProgress = unlockProgress;
			unlockGeometryComplete = completedEffect;
		}
		GuiQuadBatchRenderState.submit(graphics, 0, 0, width, height,
			unlockGeometry.values(), unlockGeometryLength);
		for (int i = 0; i < nodes.length; i++) {
			int index = i;
			if (!completedEffect) {
				hits.add(new Hit(nodes[i][0] - 16, nodes[i][1] - 16, nodes[i][0] + 17,
					nodes[i][1] + 17, () -> clickConstellation(index)));
			}
		}
	}

	private int[][] constellation(int x, int y, int size) {
		if (constellationLeft != x || constellationTop != y || constellationSize != size) {
			constellationLeft = x;
			constellationTop = y;
			constellationSize = size;
			int centreX = x + size / 2;
			int centreY = y + size / 2;
			int radius = Math.max(18, Math.min(116, (size - 64) / 2));
			for (int i = 0; i < constellationNodes.length; i++) {
				double angle = -Math.PI / 2 + i * Math.PI * 2 / constellationNodes.length;
				constellationNodes[i][0] = centreX + (int) Math.round(Math.cos(angle) * radius);
				constellationNodes[i][1] = centreY + (int) Math.round(Math.sin(angle) * radius);
			}
		}
		return constellationNodes;
	}

	private void clickConstellation(int index) {
		if (index != CONSTELLATION_ORDER[unlockProgress]) {
			AlertSounds.play(Minecraft.getInstance(), 21, 0.8f, 0.65f);
			unlockProgress = index == CONSTELLATION_ORDER[0] ? 1 : 0;
			return;
		}
		AlertSounds.play(Minecraft.getInstance(), 4, 0.65f, 0.85f + unlockProgress * 0.16f);
		if (++unlockProgress == CONSTELLATION_ORDER.length) {
			signalCompletedAt = System.currentTimeMillis();
		}
	}

	private void completeAdvancedUnlock() {
		unlockPanel = false;
		signalCompletedAt = 0;
		AdvancedUnlock.unlock();
		loadCategories();
		selectedArea = settingsAreas.stream().filter(area -> area.key.equals("advanced"))
			.findFirst().orElse(selectedArea);
		selectedTab = null;
		ensureSelectedTab();
		scroll = 0;
		if (search != null) {
			search.setValue("");
			search.visible = true;
		}
	}

	private static int signalColour(int step, long now) {
		return RainbowColours.phased((now % 3_000L) / 3_000f,
			step * 0.105f, 0.5f, 1f);
	}

	private int buildUnlockGeometry(UnlockQuadBuilder quads, int[][] nodes, int centreX, int centreY,
			int progress, boolean complete, long now) {
		quads.reset();
		int completedLines = Math.max(0, progress - 1);
		for (int step = 0; step < completedLines; step++) {
			int from = CONSTELLATION_ORDER[step];
			int to = CONSTELLATION_ORDER[step + 1];
			addSignalLine(quads, nodes[from][0], nodes[from][1], nodes[to][0], nodes[to][1],
				signalColour(step, now));
		}
		if (complete) {
			int last = CONSTELLATION_ORDER[CONSTELLATION_ORDER.length - 1];
			addSignalLine(quads, nodes[last][0], nodes[last][1],
				nodes[CONSTELLATION_ORDER[0]][0], nodes[CONSTELLATION_ORDER[0]][1],
				signalColour(CONSTELLATION_ORDER.length, now));
		}
		for (int i = 0; i < nodes.length; i++) {
			boolean visited = false;
			for (int step = 0; step < progress; step++) visited |= CONSTELLATION_ORDER[step] == i;
			boolean next = progress < CONSTELLATION_ORDER.length
				&& CONSTELLATION_ORDER[progress] == i;
			int colour = complete ? signalColour(i, now) : visited ? GREEN : next ? CYAN : DIM;
			int pulse = complete ? 6 + (int) (3 * Math.abs(Math.sin((now - signalCompletedAt) / 90.0)))
				: next ? 8 : 6;
			addDiamond(quads, nodes[i][0], nodes[i][1], pulse, colour);
		}

		// Orbiting sparks make the unlock feel distinct without creating independent
		// widgets; all geometry is rebuilt together on the shared 40 FPS clock.
		int orbit = Math.max(26, constellationSize / 3);
		float phase = (now % 4_000L) / 4_000f;
		for (int i = 0; i < 24; i++) {
			double angle = (phase + i / 24f) * Math.PI * 2;
			int radius = orbit + (i % 3) * 9;
			int sx = centreX + (int) Math.round(Math.cos(angle) * radius);
			int sy = centreY + (int) Math.round(Math.sin(angle) * radius);
			int colour = signalColour(i, now);
			quads.add(sx - 2, sy, sx + 3, sy + 1, colour);
			quads.add(sx, sy - 2, sx + 1, sy + 3, colour);
		}
		if (complete) {
			long age = now - signalCompletedAt;
			for (int ring = 0; ring < 3; ring++) {
				int radius = 8 + (int) (age / 18L) + ring * 13;
				addDiamondOutline(quads, centreX, centreY, radius,
					signalColour((int) age / 80 + ring * 3, now));
			}
		}
		return quads.size();
	}

	private static void addSignalLine(UnlockQuadBuilder quads,
			int x0, int y0, int x1, int y1, int colour) {
		int dx = Math.abs(x1 - x0);
		int sx = x0 < x1 ? 1 : -1;
		int dy = -Math.abs(y1 - y0);
		int sy = y0 < y1 ? 1 : -1;
		int error = dx + dy;
		while (true) {
			quads.add(x0 - 1, y0 - 1, x0 + 2, y0 + 2, colour);
			if (x0 == x1 && y0 == y1) break;
			int twice = error * 2;
			if (twice >= dy) { error += dy; x0 += sx; }
			if (twice <= dx) { error += dx; y0 += sy; }
		}
	}

	private static void addDiamondOutline(UnlockQuadBuilder quads,
			int x, int y, int radius, int colour) {
		for (int row = -radius; row <= radius; row++) {
			int half = radius - Math.abs(row);
			quads.add(x - half, y + row, x - half + 1, y + row + 1, colour);
			quads.add(x + half, y + row, x + half + 1, y + row + 1, colour);
		}
	}

	private static void addDiamond(UnlockQuadBuilder quads, int x, int y, int radius, int colour) {
		for (int row = -radius; row <= radius; row++) {
			int half = radius - Math.abs(row);
			quads.add(x - half, y + row, x + half + 1, y + row + 1, colour);
		}
	}

	private static void drawDiamond(GuiGraphicsExtractor graphics,
			int x, int y, int radius, int colour) {
		for (int row = -radius; row <= radius; row++) {
			int half = radius - Math.abs(row);
			graphics.fill(x - half, y + row, x + half + 1, y + row + 1, colour);
		}
	}

	private static final class UnlockQuadBuilder {
		private int[] values;
		private int size;

		private UnlockQuadBuilder(int initialInts) {
			values = new int[initialInts];
		}

		private void add(int x0, int y0, int x1, int y1, int colour) {
			if (x1 <= x0 || y1 <= y0) return;
			if (size + 5 > values.length) values = java.util.Arrays.copyOf(values, values.length * 2);
			values[size++] = x0;
			values[size++] = y0;
			values[size++] = x1;
			values[size++] = y1;
			values[size++] = colour;
		}

		private void reset() {
			size = 0;
		}

		private int[] values() {
			return values;
		}

		private int size() {
			return size;
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (event.button() == 0) blurInlineFieldsOutside(event.x(), event.y());
		if (event.button() == 0 && search != null && search.visible
				&& inside(event.x(), event.y(), searchFrameX, searchFrameY,
					searchFrameX + searchFrameWidth, searchFrameY + searchFrameHeight)) {
			return focusEditorFrame(event, doubled, search, searchFrameX, searchFrameY,
				searchFrameX + searchFrameWidth, searchFrameY + searchFrameHeight);
		}
		if (choiceField != null && isSoundChoice(choiceField) && event.button() == 1) {
			for (SoundPreviewHit hit : soundPreviewHits) {
				if (!hit.contains(event.x(), event.y())) continue;
				AlertSounds.preview(Minecraft.getInstance(), hit.soundId,
					soundPreviewSetting("Volume", 1f), soundPreviewSetting("Pitch", 1f));
				return true;
			}
		}
		if (editingInlineText && editor != null
			&& !inside(event.x(), event.y(), inlineEditorLeft, inlineEditorTop,
				inlineEditorRight, inlineEditorBottom)) {
			boolean sameSlider = editingNumber
				&& inside(event.x(), event.y(), editingSliderLeft, editingSliderTop,
					editingSliderRight, editingSliderBottom);
			applyEditor();
			if (sameSlider) return true;
		}
		if (editingInlineText && editor != null
				&& inside(event.x(), event.y(), inlineEditorLeft, inlineEditorTop,
					inlineEditorRight, inlineEditorBottom)) {
			return focusEditorFrame(event, doubled, editor, inlineEditorLeft,
				inlineEditorTop, inlineEditorRight, inlineEditorBottom);
		}
		if (event.button() == 0 && updateColourControl(event.x(), event.y())) return true;
		if (editor != null && editor.isMouseOver(event.x(), event.y())) {
			return super.mouseClicked(event, doubled);
		}
		if (customSparklingPanel && !customPresetMenu && customCalloutEditor != null
				&& customCalloutEditor.visible
				&& customCalloutBounds.contains(event.x(), event.y())) {
			return focusEditorFrame(event, doubled, customCalloutEditor, customCalloutBounds);
		}
		if (customSparklingPanel && !customPresetMenu && customDurationEditor != null
				&& customDurationEditor.visible && customDurationEditor.active
				&& customDurationBounds.contains(event.x(), event.y())) {
			return focusEditorFrame(event, doubled, customDurationEditor, customDurationBounds);
		}
		if (customSparklingPanel && !customPresetMenu && customVolumeEditor != null
				&& customVolumeEditor.visible && customVolumeEditor.active
				&& customVolumeBounds.contains(event.x(), event.y())) {
			return focusEditorFrame(event, doubled, customVolumeEditor, customVolumeBounds);
		}
		if (customSparklingPanel && customPresetNameEditor != null
				&& customPresetNameEditor.visible && customPresetNameEditor.active
				&& customPresetNameBounds.contains(event.x(), event.y())) {
			return focusEditorFrame(event, doubled, customPresetNameEditor,
				customPresetNameBounds);
		}
		if (customSparklingPanel && !customPresetMenu && event.button() == 0
				&& inside(event.x(), event.y(), customDurationSliderLeft,
					customDurationSliderTop + 3,
					customDurationSliderLeft + customDurationSliderWidth,
					customDurationSliderTop + 18)) {
			customDurationDragging = true;
			customVolumeDragging = false;
			updateCustomDuration(event.x());
			return true;
		}
		if (customSparklingPanel && !customPresetMenu && event.button() == 0
				&& inside(event.x(), event.y(), customVolumeSliderLeft,
					customVolumeSliderTop + 3,
					customVolumeSliderLeft + customVolumeSliderWidth,
					customVolumeSliderTop + 18)) {
			customVolumeDragging = true;
			customDurationDragging = false;
			updateCustomVolume(event.x());
			return true;
		}
		if (event.button() == 0) {
			int minimum = customPresetHitStart >= 0 ? customPresetHitStart
				: modalHitStart >= 0 ? modalHitStart : 0;
			for (int i = hits.size() - 1; i >= minimum; i--) {
				Hit hit = hits.get(i);
				if (!hit.contains(event.x(), event.y())) continue;
				hit.action.run();
				return true;
			}
		}
		if (customPresetHitStart >= 0 || modalHitStart >= 0) return true;
		return super.mouseClicked(event, doubled);
	}

	private boolean focusEditorFrame(MouseButtonEvent event, boolean doubled,
			EditBox field, EditorBounds bounds) {
		if (event.button() != 0 || field == null
				|| !bounds.contains(event.x(), event.y())) return false;
		return focusEditorFrame(event, doubled, field);
	}

	private boolean focusEditorFrame(MouseButtonEvent event, boolean doubled,
			EditBox field, int left, int top, int right, int bottom) {
		if (event.button() != 0 || field == null
				|| !inside(event.x(), event.y(), left, top, right, bottom)) return false;
		return focusEditorFrame(event, doubled, field);
	}

	private boolean focusEditorFrame(MouseButtonEvent event, boolean doubled, EditBox field) {
		boolean overNativeField = field.isMouseOver(event.x(), event.y());
		if (overNativeField) super.mouseClicked(event, doubled);
		setFocused(field);
		field.setFocused(true);
		if (!overNativeField) {
			if (event.x() <= field.getX()) field.setCursorPosition(0);
			else if (event.x() >= field.getX() + field.getWidth()) {
				field.setCursorPosition(field.getValue().length());
			}
		}
		return true;
	}

	private void blurInlineFieldsOutside(double mouseX, double mouseY) {
		if (search != null && search.isFocused()
				&& !inside(mouseX, mouseY, searchFrameX, searchFrameY,
					searchFrameX + searchFrameWidth, searchFrameY + searchFrameHeight)) {
			search.setFocused(false);
			setFocused(null);
		}
		if (customCalloutEditor != null && customCalloutEditor.isFocused()
				&& !customCalloutBounds.contains(mouseX, mouseY)) {
			customCalloutEditor.setFocused(false);
			setFocused(null);
		}
		if (customDurationEditor != null && customDurationEditor.isFocused()
				&& !customDurationBounds.contains(mouseX, mouseY)) {
			applyCustomDurationEditor();
			customDurationEditor.setFocused(false);
			setFocused(null);
		}
		if (customVolumeEditor != null && customVolumeEditor.isFocused()
				&& !customVolumeBounds.contains(mouseX, mouseY)) {
			applyCustomVolumeEditor();
			customVolumeEditor.setFocused(false);
			setFocused(null);
		}
		if (customPresetNameEditor != null && customPresetNameEditor.isFocused()
				&& !customPresetNameBounds.contains(mouseX, mouseY)) {
			customPresetNameEditor.setFocused(false);
			setFocused(null);
		}
	}

	private boolean blurFocusedInlineField() {
		if (customPresetNameEditor != null && customPresetNameEditor.isFocused()) {
			customPresetNameEditor.setFocused(false);
			setFocused(null);
			return true;
		}
		if (customCalloutEditor != null && customCalloutEditor.isFocused()) {
			customCalloutEditor.setFocused(false);
			setFocused(null);
			return true;
		}
		if (customDurationEditor != null && customDurationEditor.isFocused()) {
			applyCustomDurationEditor();
			customDurationEditor.setFocused(false);
			setFocused(null);
			return true;
		}
		if (customVolumeEditor != null && customVolumeEditor.isFocused()) {
			applyCustomVolumeEditor();
			customVolumeEditor.setFocused(false);
			setFocused(null);
			return true;
		}
		if (search != null && search.isFocused()) {
			search.setFocused(false);
			setFocused(null);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (updateColourControl(event.x(), event.y())) return true;
		if (customDurationDragging) {
			updateCustomDuration(event.x());
			return true;
		}
		if (customVolumeDragging) {
			updateCustomVolume(event.x());
			return true;
		}
		if (draggingSlider != null) {
			updateSlider(event.x());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (customDurationDragging) {
			customDurationDragging = false;
			ConfigManager.save();
			return true;
		}
		if (customVolumeDragging) {
			customVolumeDragging = false;
			ConfigManager.save();
			return true;
		}
		if (draggingSlider != null) {
			draggingSlider = null;
			draggingOwner = null;
			draggingRange = null;
			ConfigManager.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (multiChoiceDropdown != null) {
			int columns = multiChoiceColumns();
			int rows = multiChoiceRows(multiChoiceLabels(multiChoiceDropdown).length, columns);
			int margin = multiChoiceDropdown.biomeColumns() && columns == 4 ? 90 : 76;
			int h = Math.min(height - 30, margin + rows * 31);
			multiChoiceScroll = Math.clamp(multiChoiceScroll
				+ (scrollY > 0 ? -31 : scrollY < 0 ? 31 : 0), 0,
				Math.max(0, rows * 31 - (h - margin)));
			return true;
		}
		// Commit an inline value before its card moves. This prevents a native text
		// widget from remaining at an obsolete screen position while the page scrolls.
		if (editor != null) applyEditor();
		if (mouseX < navWidth && mouseY >= HEADER_HEIGHT && mouseY < height - FOOTER_HEIGHT) {
			int viewport = height - HEADER_HEIGHT - FOOTER_HEIGHT - 8;
			int max = Math.max(0, navigationContentHeight - viewport);
			navigationScroll = Math.clamp(navigationScroll
				+ (scrollY > 0 ? -34 : scrollY < 0 ? 34 : 0), 0, max);
			return true;
		}
		if (mouseX < workspaceLeft || mouseY < contentViewportTop
				|| mouseY >= contentViewportBottom) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		// Match the exact scissor rectangle. The previous estimate counted the
		// eight-pixel inset below the tab header as visible content, leaving the
		// final card eight pixels short of its fully scrolled position.
		int viewport = Math.max(1, contentViewportBottom - contentViewportTop);
		int max = Math.max(0, contentHeight - viewport);
		scroll = Math.clamp(scroll + (scrollY > 0 ? -36 : scrollY < 0 ? 36 : 0), 0, max);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (customSparklingPanel && customPresetNaming && event.key() == 257) {
			finishPresetSave();
			return true;
		}
		if (event.key() == 257 && blurFocusedInlineField()) return true;
		// Alt+Tab can deliver the Tab key before Windows removes focus. Do not let
		// Minecraft cycle focus into hidden widgets behind this modal.
		if (customSparklingPanel && event.key() == 258) return true;
		if (customSparklingPanel && customPresetMenu && event.key() == 256) {
			closeCustomPresetMenu();
			return true;
		}
		if (specialSparklingConfirmation && event.key() == 256) {
			cancelSparklingIntensity();
			return true;
		}
		if (partySyncConfirmation && event.key() == 256) {
			cancelPartySync();
			return true;
		}
		if (choiceField != null && event.key() == 256) {
			closeChoicePicker();
			return true;
		}
		if (editor != null && event.key() == 257) {
			applyEditor();
			return true;
		}
		if (editor != null && event.key() == 256) {
			cancelEditor();
			return true;
		}
		if (customThemePanel && event.key() == 256) {
			closeCustomThemePanel();
			return true;
		}
		if (customSparklingPanel && event.key() == 256) {
			closeCustomSparklingPanel();
			return true;
		}
		if (unlockPanel && event.key() == 256) {
			closeUnlockPanel();
			return true;
		}
		return super.keyPressed(event);
	}

	private void setBoolean(Object owner, Field field, boolean value) {
		if (field.getName().equals("enablePartySync") && value) {
			partySyncConfirmation = true;
			setFocused(null);
			if (search != null) search.visible = false;
			return;
		}
		try {
			field.setBoolean(owner, value);
			SettingToggle toggle = field.getAnnotation(SettingToggle.class);
			if (toggle.runnableId() >= 0) ConfigManager.get().executeRunnable(toggle.runnableId());
			ConfigManager.save();
		} catch (IllegalAccessException ignored) {
		}
	}

	private void cycleDropdown(Object owner, Field field, SettingChoice dropdown, int direction) {
		try {
			int current = field.getInt(owner);
			if (isSoundChoice(field)) {
				List<AlertSounds.Choice> choices = AlertSounds.alphabetical();
				int position = 0;
				for (int i = 0; i < choices.size(); i++) if (choices.get(i).id() == current) position = i;
				field.setInt(owner, choices.get(Math.floorMod(position + direction, choices.size())).id());
			} else if (isThemeChoice(field)) {
				int position = 0;
				for (int i = 0; i < THEMES.size(); i++) if (THEMES.get(i).id() == current) position = i;
				field.setInt(owner, THEMES.get(Math.floorMod(position + direction, THEMES.size())).id());
			} else {
				field.setInt(owner, Math.floorMod(current + direction, dropdown.values().length));
			}
			ConfigManager.save();
		} catch (IllegalAccessException ignored) {
		}
	}

	private String dropdownLabel(Field field, SettingChoice dropdown, int value) {
		if (isSoundChoice(field)) return AlertSounds.label(value);
		if (isThemeChoice(field)) {
			for (ThemeChoice theme : THEMES) {
				if (theme.id == value) return theme.label;
			}
			return "Default";
		}
		return value >= 0 && value < dropdown.values().length ? dropdown.values()[value] : dropdown.values()[0];
	}

	private static boolean isSoundChoice(Field field) {
		return field.getName().endsWith("SoundChoice") || field.getName().equals("testAlertSoundChoice");
	}

	private static boolean isThemeChoice(Field field) {
		return field != null && field.getName().equals("settingsTheme");
	}

	private static boolean isHeaderOnly(Field field) {
		return isThemeChoice(field);
	}

	private void beginSlider(Object owner, Field field, SettingRange slider,
			int mouseX, int x, int width) {
		draggingOwner = owner;
		draggingSlider = field;
		draggingRange = slider;
		draggingLeft = x;
		draggingWidth = width;
		setSlider(owner, field, slider, (mouseX - x) / (float) width);
	}

	private void updateSlider(double mouseX) {
		setSlider(draggingOwner, draggingSlider, draggingRange,
			(float) ((mouseX - draggingLeft) / draggingWidth));
	}

	private void setSlider(Object owner, Field field, SettingRange slider, float progress) {
		float raw = slider.minValue() + Math.clamp(progress, 0f, 1f)
			* (slider.maxValue() - slider.minValue());
		float value = Math.round(raw / slider.minStep()) * slider.minStep();
		try {
			if (field.getType() == int.class) field.setInt(owner, Math.round(value));
			else field.setFloat(owner, value);
		} catch (IllegalAccessException ignored) {
		}
	}

	private void runButton(Object owner, Field field, SettingAction button) {
		try {
			if (Runnable.class.isAssignableFrom(field.getType())) ((Runnable) field.get(owner)).run();
			else if (button.runnableId() >= 0) ConfigManager.get().executeRunnable(button.runnableId());
		} catch (IllegalAccessException ignored) {
		}
	}

	private static boolean belongsTo(Field field, Integer parentId) {
		SettingGroup parent = field.getAnnotation(SettingGroup.class);
		return parentId == null ? parent == null : parent != null && parent.id() == parentId;
	}

	private static boolean hasEditor(Field field) {
		Class<?> owner = field.getDeclaringClass();
		boolean oldAlertPlacement = owner == SafariConfig.AlertConfig.class
			&& (field.getName().endsWith("Scale") || field.getName().endsWith("VerticalPosition"));
		boolean oldSparklingPlacement = owner == SafariConfig.SparklingConfig.class
			&& (field.getName().equals("sparklingBannerScale")
				|| field.getName().equals("sparklingBannerVerticalPosition"));
		if (oldAlertPlacement || oldSparklingPlacement) return false;
		return isReadOnlyNotice(field)
			|| field.isAnnotationPresent(SettingToggle.class)
			|| field.isAnnotationPresent(SettingChoice.class)
			|| field.isAnnotationPresent(SettingMultiChoice.class)
			|| field.isAnnotationPresent(SettingRange.class)
			|| field.isAnnotationPresent(SettingColor.class)
			|| field.isAnnotationPresent(SettingText.class)
			|| field.isAnnotationPresent(SettingAction.class);
	}

	private static String clean(String text) {
		return CLEAN_TEXT.computeIfAbsent(text,
			value -> value.replaceAll("§[0-9A-FK-ORa-fk-or]", ""));
	}

	private static String displayName(String text) {
		return DISPLAY_NAMES.computeIfAbsent(text, value -> value.replace("Hud", "HUD")
			.replace("Gui", "GUI").replace(" Id", " ID").replace("Api", "API"));
	}

	private List<String> wrap(String text, int width) {
		if (text == null || text.isBlank()) return List.of();
		WrapKey key = new WrapKey(text, width);
		List<String> cached = wrappedText.get(key);
		if (cached != null) return cached;
		List<String> lines = new ArrayList<>();
		for (String paragraph : text.split("\\n", -1)) {
			if (paragraph.isBlank()) {
				lines.add("");
				continue;
			}
			StringBuilder line = new StringBuilder();
			for (String word : paragraph.trim().split("\\s+")) {
				String candidate = line.isEmpty() ? word : line + " " + word;
				if (!line.isEmpty() && font.width(candidate) > width) {
					lines.add(line.toString());
					line.setLength(0);
				}
				if (!line.isEmpty()) line.append(' ');
				line.append(word);
			}
			if (!line.isEmpty()) lines.add(line.toString());
		}
		List<String> result = List.copyOf(lines);
		wrappedText.put(key, result);
		return result;
	}

	private String trim(String text, int width) {
		if (text == null) return "";
		if (font.width(text) <= width) return text;
		String suffix = "…";
		int end = text.length();
		while (end > 0 && font.width(text.substring(0, end) + suffix) > width) end--;
		return text.substring(0, end) + suffix;
	}

	private static String formatNumber(float value) {
		return Math.abs(value - Math.round(value)) < 0.001f
			? Integer.toString(Math.round(value)) : String.format(Locale.ROOT, "%.2f", value)
				.replaceAll("0+$", "").replaceAll("\\.$", "");
	}

	private static boolean inside(double x, double y, int left, int top, int right, int bottom) {
		return x >= left && x < right && y >= top && y < bottom;
	}

	private void outline(GuiGraphicsExtractor graphics, int x, int y,
			int width, int height, int colour) {
		if (SpecialTheme.rainbow()) {
			SpecialTheme.border(graphics, x, y, width, height, 1, contentScissor);
		}
		else UIDraw.outline(graphics, x, y, width, height, colour);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
