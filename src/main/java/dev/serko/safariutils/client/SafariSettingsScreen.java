package dev.serko.safariutils.client;

import dev.serko.safariutils.BuildVersion;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Safari Utils' fast, dependency-free settings workspace. */
public final class SafariSettingsScreen extends Screen {
	private static final long REOPEN_MEMORY_MILLIS = 10_000L;
	private static final Map<String, String> REMEMBERED_SELECTIONS = new HashMap<>();
	private static final Set<String> REMEMBERED_OPEN_GROUPS = new HashSet<>();
	private static final Map<Class<?>, Field[]> PUBLIC_FIELDS = new HashMap<>();
	private static String rememberedSettingCategory;
	private static int rememberedScroll;
	private static long rememberedAt;
	private static final int NAV_WIDTH = 158;
	private static final int HEADER_HEIGHT = 58;
	private static final int FOOTER_HEIGHT = 30;
	private static final int THEME_BUTTON_WIDTH = 126;
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

	private final Screen parent;
	private final List<SettingCategoryView> categories = new ArrayList<>();
	private final List<Hit> hits = new ArrayList<>();
	private final List<SoundPreviewHit> soundPreviewHits = new ArrayList<>();
	private final Set<VisibleSetting> visibleSettings = new java.util.LinkedHashSet<>();
	private final Map<String, String> selectedGroups = new HashMap<>();
	private final Set<String> openGroups = new HashSet<>();
	private SettingCategoryView selected;
	private EditBox search;
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
	private int scroll;
	private int contentHeight;
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
	private long signalCompletedAt;
	private int modalHitStart = -1;
	private long resetArmedUntil;

	private record SettingCategoryView(String key, Field field, Object value, SettingCategory info) { }
	private record VisibleSetting(Object owner, Field field) { }
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
	private record SearchItem(Field field, List<SettingInfo> context) { }
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
		return release.endsWith("-extra")
			? release.substring(0, release.length() - "-extra".length()) : release;
	}

	public SafariSettingsScreen(Screen parent) {
		super(Component.literal("Safari Utils Settings"));
		this.parent = parent;
		loadCategories();
		if (System.currentTimeMillis() - rememberedAt <= REOPEN_MEMORY_MILLIS) {
			selectedGroups.putAll(REMEMBERED_SELECTIONS);
			openGroups.addAll(REMEMBERED_OPEN_GROUPS);
			selected = categories.stream().filter(category -> category.key.equals(rememberedSettingCategory))
				.findFirst().orElse(selected);
			scroll = rememberedScroll;
		}
	}

	private void loadCategories() {
		categories.clear();
		SafariConfig config = ConfigManager.get();
		for (Field field : SafariConfig.class.getFields()) {
			SettingCategory category = field.getAnnotation(SettingCategory.class);
			if (category == null) continue;
			if (field.getName().equals("advanced") && !AdvancedUnlock.isUnlocked()) continue;
			try {
				categories.add(new SettingCategoryView(field.getName(), field, field.get(config), category));
			} catch (IllegalAccessException ignored) {
			}
		}
		if (selected == null || categories.stream().noneMatch(c -> c.key.equals(selected.key))) {
			selected = categories.isEmpty() ? null : categories.getFirst();
		}
	}

	@Override
	protected void init() {
		clearWidgets();
		int searchX = NAV_WIDTH + 24;
		int themeLeft = width - 20 - THEME_BUTTON_WIDTH;
		int searchWidth = Math.max(24, themeLeft - 8 - searchX);
		search = new EditBox(font, searchX, 18, searchWidth, 20,
			Component.literal("Search Settings"));
		String hint = font.width("Search settings, descriptions, and tags...") <= searchWidth - 10
			? "Search settings, descriptions, and tags..."
			: font.width("Search settings...") <= searchWidth - 10 ? "Search settings..." : "Search";
		search.setHint(Component.literal(hint));
		search.setMaxLength(80);
		search.setResponder(value -> {
			scroll = 0;
		});
		addRenderableWidget(search);
	}

	@Override
	public void onClose() {
		rememberUIState();
		ConfigManager.save();
		ClientCompat.setScreen(parent);
	}

	@Override
	public void removed() {
		rememberUIState();
		ConfigManager.save();
		super.removed();
	}

	private void rememberUIState() {
		REMEMBERED_SELECTIONS.clear();
		REMEMBERED_SELECTIONS.putAll(selectedGroups);
		REMEMBERED_OPEN_GROUPS.clear();
		REMEMBERED_OPEN_GROUPS.addAll(openGroups);
		rememberedSettingCategory = selected == null ? null : selected.key;
		rememberedScroll = scroll;
		rememberedAt = System.currentTimeMillis();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		applyTheme();
		boolean editingSameSlider = editingInlineText && editingNumber && editor != null
			&& inside(mouseX, mouseY, editingSliderLeft, editingSliderTop,
				editingSliderRight, editingSliderBottom);
		boolean modalOpen = unlockPanel || customThemePanel || specialSparklingConfirmation
			|| editor != null && !editingInlineText || choiceField != null || editingSameSlider;
		int backgroundMouseX = modalOpen ? Integer.MIN_VALUE : mouseX;
		int backgroundMouseY = modalOpen ? Integer.MIN_VALUE : mouseY;
		graphics.fill(0, 0, width, height, BACKGROUND);
		graphics.fill(0, 0, NAV_WIDTH, height, SURFACE);
		graphics.fill(NAV_WIDTH, 0, width, HEADER_HEIGHT, SURFACE);
		graphics.fill(NAV_WIDTH, height - FOOTER_HEIGHT, width, height, SURFACE);
		graphics.fill(NAV_WIDTH - 1, 0, NAV_WIDTH, height, BORDER);
		graphics.fill(NAV_WIDTH, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, BORDER);
		drawBrand(graphics);
		hits.clear();
		soundPreviewHits.clear();
		modalHitStart = -1;
		drawThemeControl(graphics, backgroundMouseX, backgroundMouseY);
		drawNavigation(graphics, backgroundMouseX, backgroundMouseY);
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
		if (editor != null && !editingInlineText) {
			modalHitStart = hits.size();
			drawEditorModal(graphics, mouseX, mouseY);
		}
		if (choiceField != null) {
			modalHitStart = hits.size();
			drawChoiceModal(graphics, mouseX, mouseY);
		}
		if (search != null) {
			search.setTextColor(TEXT);
			search.setTextColorUneditable(MUTED);
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void applyTheme() {
		int id = Math.clamp(ConfigManager.get().display.settingsTheme, 0, 34);
		int rainbow = java.awt.Color.HSBtoRGB((System.currentTimeMillis() % 8_000L) / 8_000f, 0.66f, 1f)
			| 0xFF000000;
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
	}

	/** Active settings palette, shared by the standalone HUD editor. */
	static int[] activeThemePalette() {
		int rainbow = java.awt.Color.HSBtoRGB((System.currentTimeMillis() % 8_000L) / 8_000f,
			0.66f, 1f) | 0xFF000000;
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
		graphics.fillGradient(0, 0, NAV_WIDTH, 54, SURFACE, CARD);
		float titleScale = 1.42f;
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		int safariColour = display.settingsTheme == 34
			? Colours.argb(display.customThemeSafariTitle, CYAN) : CYAN;
		int utilsColour = display.settingsTheme == 34
			? Colours.argb(display.customThemeUtilsTitle, GOLD) : GOLD;
		Component safariTitle = Component.literal("SAFARI ").withStyle(style -> style.withBold(true));
		Component utilsTitle = Component.literal("UTILS").withStyle(style -> style.withBold(true));
		int titleWidth = font.width(safariTitle) + font.width(utilsTitle);
		int titleLeft = Math.round(NAV_WIDTH / 2f / titleScale - titleWidth / 2f);
		graphics.pose().pushMatrix();
		graphics.pose().scale(titleScale, titleScale);
		graphics.text(font, safariTitle, titleLeft, Math.round(11f / titleScale), safariColour);
		graphics.text(font, utilsTitle, titleLeft + font.width(safariTitle), Math.round(11f / titleScale), utilsColour);
		graphics.pose().popMatrix();
		drawScaledCenteredText(graphics, "VERSION " + MOD_VERSION, NAV_WIDTH / 2, 32, 1.08f, MUTED);
	}

	/** Header-only theme picker kept out of Display's ordinary setting cards. */
	private void drawThemeControl(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = width - 20 - THEME_BUTTON_WIDTH;
		int y = 14;
		boolean hovered = inside(mouseX, mouseY, x, y, x + THEME_BUTTON_WIDTH, y + 28);
		graphics.fillGradient(x, y, x + THEME_BUTTON_WIDTH, y + 28,
			hovered ? SELECTED : CARD, hovered ? SUB_SELECTED : SURFACE);
		outline(graphics, x, y, THEME_BUTTON_WIDTH, 28, hovered ? CYAN : GOLD);
		drawDiamond(graphics, x + 13, y + 14, 5, ConfigManager.get().display.settingsTheme == 4 ? CYAN : GOLD);
		graphics.text(font, "THEME", x + 24, y + 5, DIM);
		graphics.text(font, currentThemeLabel(), x + 24, y + 16, hovered ? TEXT : MUTED);
		hits.add(new Hit(x, y, x + THEME_BUTTON_WIDTH, y + 28, this::openThemePicker));
	}

	private String currentThemeLabel() {
		int current = ConfigManager.get().display.settingsTheme;
		return THEMES.stream().filter(theme -> theme.id == current)
			.findFirst().map(ThemeChoice::label).orElse("Default");
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
		int y = 66 - navigationScroll;
		graphics.enableScissor(0, top, NAV_WIDTH, bottom);
		for (SettingCategoryView category : categories) {
			boolean active = category == selected;
			boolean hovered = mouseX >= 8 && mouseX < NAV_WIDTH - 8 && mouseY >= y && mouseY < y + 30;
			if (active || hovered) {
				graphics.fill(8, y, NAV_WIDTH - 8, y + 30, active ? SELECTED : CARD_HOVER);
				if (active) graphics.fill(8, y, 11, y + 30, CYAN);
			}
			drawScaledText(graphics, category.info.name(), 18,
				y + (30f - font.lineHeight) / 2f, 1.0f, active ? TEXT : MUTED);
			int rowY = y;
			if (rowY + 30 > top && rowY < bottom) {
				hits.add(new Hit(8, Math.max(rowY, top), NAV_WIDTH - 8,
					Math.min(rowY + 30, bottom), () -> select(category)));
			}
			y += 34;
		}

		if (!AdvancedUnlock.isUnlocked()) {
			int lockY = y + 8;
			boolean hovered = mouseX >= 8 && mouseX < NAV_WIDTH - 8 && mouseY >= lockY && mouseY < lockY + 30;
			if (hovered) graphics.fill(8, lockY, NAV_WIDTH - 8, lockY + 30, CARD_HOVER);
			graphics.text(font, "◇  Locked", 18, lockY + 10, hovered ? GOLD : DIM);
			if (lockY + 30 > top && lockY < bottom) {
				hits.add(new Hit(8, Math.max(lockY, top), NAV_WIDTH - 8,
					Math.min(lockY + 30, bottom), this::openUnlockPanel));
			}
			y = lockY + 30;
		}
		navigationContentHeight = Math.max(0, y + navigationScroll - 66);
		graphics.disableScissor();
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
		if (search != null) search.visible = true;
	}

	private void select(SettingCategoryView category) {
		if (selected != null && !selected.key.equals(category.key)) {
			selectedGroups.clear();
			openGroups.clear();
		}
		selected = category;
		scroll = 0;
		resetArmedUntil = 0;
		search.setValue("");
	}

	private void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (selected == null) return;
		visibleSettings.clear();
		int left = NAV_WIDTH + 20;
		int right = width - 20;
		int top = HEADER_HEIGHT + 14;
		int bottom = height - FOOTER_HEIGHT - 8;
		graphics.enableScissor(left, top, right, bottom);
		int y = top - scroll;
		String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
		if (query.isEmpty()) {
			y = drawNormalFields(graphics, selected.value, selected.value.getClass(), null,
				selected.key, left, right, y, 0, mouseX, mouseY);
		} else {
			y = drawSearchResults(graphics, selected.value, selected.value.getClass(), query,
				left, right, y, mouseX, mouseY);
		}
		contentHeight = Math.max(0, y + scroll - top);
		graphics.disableScissor();
	}

	private int drawNormalFields(GuiGraphicsExtractor graphics, Object owner, Class<?> type,
			Integer parentId, String path, int left, int right, int y, int depth,
			int mouseX, int mouseY) {
		List<Field> groups = new ArrayList<>();
		List<Field> fields = new ArrayList<>(java.util.Arrays.asList(publicFields(type)));
		if (path.startsWith("advanced.testingAccordion.")
			|| path.startsWith("advanced.safeModeAccordion")) {
			fields.sort(java.util.Comparator.comparing(field -> {
				SettingInfo info = field.getAnnotation(SettingInfo.class);
				return info == null ? "" : clean(info.name()).toLowerCase(Locale.ROOT);
			}));
		}
		for (Field field : fields) {
			SettingInfo option = field.getAnnotation(SettingInfo.class);
			if (option == null || isHeaderOnly(field) || !belongsTo(field, parentId)) continue;
			SettingSection accordion = field.getAnnotation(SettingSection.class);
			if (accordion != null) {
				groups.add(field);
			} else if (hasEditor(field)) {
				y = drawSetting(graphics, owner, field, option, left + depth * 5, right, y,
					mouseX, mouseY);
			}
		}
		if (groups.isEmpty()) return y;

		if (depth <= 1 && !(depth == 1 && groups.size() == 1)) {
			String selectionKey = path + ":" + (parentId == null ? "root" : parentId);
			String selectedField = selectedGroups.get(selectionKey);
			boolean validSelection = false;
			for (Field field : groups) validSelection |= field.getName().equals(selectedField);
			if (!validSelection) selectedField = null;
			y = drawGroupTabs(graphics, groups, selectionKey, selectedField,
				left + depth * 5, right, y, depth, mouseX, mouseY);
			for (Field field : groups) {
				if (!field.getName().equals(selectedField)) continue;
				SettingSection accordion = field.getAnnotation(SettingSection.class);
				y = drawSelectedSection(graphics, field.getAnnotation(SettingInfo.class),
					left + depth * 5, right, y);
				y = drawNormalFields(graphics, owner, type, accordion.id(), path + "." + field.getName(),
					left, right, y, depth + 1, mouseX, mouseY);
			}
			return y;
		}

		for (Field field : groups) {
			SettingInfo option = field.getAnnotation(SettingInfo.class);
			String groupPath = path + "." + field.getName();
			boolean open = openGroups.contains(groupPath);
			y = drawCollapsibleSection(graphics, option, groupPath, open,
				left + depth * 5, right, y, depth, mouseX, mouseY);
			if (open) {
				y = drawNormalFields(graphics, owner, type,
					field.getAnnotation(SettingSection.class).id(), groupPath,
					left, right, y, depth + 1, mouseX, mouseY);
			}
		}
		return y;
	}

	private int drawGroupTabs(GuiGraphicsExtractor graphics, List<Field> groups, String selectionKey,
			String selectedField, int left, int right, int y, int depth, int mouseX, int mouseY) {
		int x = left;
		for (Field field : groups) {
			String label = displayName(field.getAnnotation(SettingInfo.class).name());
			int tabWidth = Math.min(right - left, Math.max(76, font.width(label) + 24));
			if (x > left && x + tabWidth > right) {
				x = left;
				y += 28;
			}
			boolean active = field.getName().equals(selectedField);
			boolean hovered = inside(mouseX, mouseY, x, y, x + tabWidth, y + 23);
			int activeFill = depth == 0 ? SELECTED : SUB_SELECTED;
			int inactiveFill = depth == 0 ? CARD : SURFACE;
			int accent = depth == 0 ? CYAN : GOLD;
			graphics.fill(x, y, x + tabWidth, y + 23,
				active ? activeFill : hovered ? CARD_HOVER : inactiveFill);
			outline(graphics, x, y, tabWidth, 23, active ? accent : BORDER);
			graphics.centeredText(font, trim(label, tabWidth - 12), x + tabWidth / 2, y + 8,
				active ? TEXT : MUTED);
			String choice = field.getName();
			hits.add(new Hit(x, y, x + tabWidth, y + 23, () -> {
				String parentPath = selectionKey.substring(0, selectionKey.lastIndexOf(':'));
				collapseDescendants(parentPath);
				if (depth == 0 && choice.equals(selectedGroups.get(selectionKey))) {
					selectedGroups.remove(selectionKey);
				} else {
					selectedGroups.put(selectionKey, choice);
				}
			}));
			x += tabWidth + 6;
		}
		return y + 31;
	}

	private void collapseDescendants(String parentPath) {
		String prefix = parentPath + ".";
		selectedGroups.keySet().removeIf(key -> key.startsWith(prefix));
		openGroups.removeIf(path -> path.startsWith(prefix));
	}

	private int drawSelectedSection(GuiGraphicsExtractor graphics, SettingInfo option,
			int left, int right, int y) {
		if (option.desc().isBlank()) return y;
		List<String> lines = wrap(clean(option.desc()), right - left - 24);
		graphics.text(font, displayName(option.name()), left + 4, y + 2, TEXT);
		int lineY = y + 17;
		for (String line : lines) {
			graphics.text(font, line, left + 4, lineY, MUTED);
			lineY += 11;
		}
		return lineY + 5;
	}

	private int drawCollapsibleSection(GuiGraphicsExtractor graphics, SettingInfo option,
			String path, boolean open, int left, int right, int y, int depth,
			int mouseX, int mouseY) {
		List<String> description = wrap(clean(option.desc()), right - left - 48);
		int height = Math.max(28, 28 + description.size() * 11);
		boolean hovered = inside(mouseX, mouseY, left, y, right, y + height);
		graphics.fill(left, y, right, y + height, hovered ? CARD_HOVER : CARD);
		int accent = depth <= 1 ? GOLD : CYAN;
		graphics.fill(left, y, left + 3, y + height, accent);
		graphics.text(font, open ? "−" : "+", left + 11, y + 10, accent);
		graphics.text(font, displayName(option.name()), left + 27, y + 9, TEXT);
		int lineY = y + 24;
		for (String line : description) {
			graphics.text(font, line, left + 27, lineY, MUTED);
			lineY += 11;
		}
		hits.add(new Hit(left, y, right, y + height, () -> {
			if (openGroups.remove(path)) collapseDescendants(path);
			else openGroups.add(path);
		}));
		return y + height + 6;
	}

	private int drawSearchResults(GuiGraphicsExtractor graphics, Object owner, Class<?> type,
			String query, int left, int right, int y, int mouseX, int mouseY) {
		List<SearchItem> results = new ArrayList<>();
		collectSearchResults(type, null, List.of(), query, false, results);
		String previousContext = null;
		for (SearchItem result : results) {
			String context = result.context.stream().map(SettingInfo::name)
				.map(SafariSettingsScreen::displayName).reduce((a, b) -> a + "  ›  " + b).orElse("");
			if (!context.equals(previousContext) && !context.isBlank()) {
				y = drawSearchContext(graphics, context, left, right, y);
				previousContext = context;
			}
			SettingInfo option = result.field.getAnnotation(SettingInfo.class);
			y = drawSetting(graphics, owner, result.field, option, left + 5, right, y, mouseX, mouseY);
		}
		if (results.isEmpty()) {
			graphics.centeredText(font, "No matching settings", (left + right) / 2, y + 30, MUTED);
			y += 70;
		}
		return y;
	}

	private void collectSearchResults(Class<?> type, Integer parentId, List<SettingInfo> context,
			String query, boolean ancestorMatches, List<SearchItem> results) {
		for (Field field : publicFields(type)) {
			SettingInfo option = field.getAnnotation(SettingInfo.class);
			if (option == null || isHeaderOnly(field) || !belongsTo(field, parentId)) continue;
			boolean matches = smartMatches(option, query);
			SettingSection accordion = field.getAnnotation(SettingSection.class);
			if (accordion != null) {
				List<SettingInfo> nested = new ArrayList<>(context);
				nested.add(option);
				collectSearchResults(type, accordion.id(), nested, query,
					ancestorMatches || matches, results);
			} else if (hasEditor(field) && (ancestorMatches || matches)) {
				results.add(new SearchItem(field, List.copyOf(context)));
			}
		}
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

	/** Keeps developer tools private and hides Safe Mode controls in Safe Mode jars. */
	private static boolean visibleInThisBuild(Class<?> owner, Field field) {
		if (owner != SafariConfig.AdvancedConfig.class || BuildVersion.DEVELOPER) return true;
		if (field.getName().equals("specialTheme")) return true;
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
		graphics.text(font, trim(context, right - left - 22), left + 12, y + 10, TEXT);
		return y + 34;
	}

	private int drawSetting(GuiGraphicsExtractor graphics, Object owner, Field field,
			SettingInfo option, int left, int right, int y, int mouseX, int mouseY) {
		String[] description = option.desc().split("\\n", 2);
		boolean safeModeComparison = description.length > 1
			&& clean(description[0]).startsWith("Safe:")
			&& clean(description[1]).startsWith("Normal:");
		int controlWidth = Math.clamp((right - left) / 3, 116, 210);
		int descriptionWidth = Math.max(40, right - controlWidth - 24 - (left + 12));
		List<String> mainLines = description[0].isBlank() ? List.of() : wrap(clean(description[0]), descriptionWidth);
		List<String> tagLines = description.length > 1 ? wrap(clean(description[1]), descriptionWidth) : List.of();
		int lineCount = mainLines.size() + tagLines.size();
		int height = Math.max(42, lineCount == 0 ? 42 : 32 + lineCount * 11);
		int controlX = right - controlWidth - 10;
		int controlY = y + (height - 22) / 2;
		if (field.isAnnotationPresent(com.google.gson.annotations.Expose.class)) {
			visibleSettings.add(new VisibleSetting(owner, field));
		}
		boolean rowHovered = inside(mouseX, mouseY, left, y, right, y + height);
		boolean controlHovered = inside(mouseX, mouseY, controlX, controlY,
			controlX + controlWidth, controlY + 22);
		boolean hovered = field.isAnnotationPresent(SettingToggle.class)
			? rowHovered : field.isAnnotationPresent(SettingRange.class)
				? inside(mouseX, mouseY, controlX, y, right, y + height)
				: controlHovered;
		graphics.fill(left, y, right, y + height, hovered ? CARD_HOVER : CARD);
		outline(graphics, left, y, right - left, height, hovered ? CYAN : BORDER);
		graphics.text(font, displayName(option.name()), left + 12, y + 9, TEXT);
		int lineY = y + 24;
		for (String line : mainLines) {
			graphics.text(font, line, left + 12, lineY, safeModeComparison ? CYAN : MUTED);
			lineY += 11;
		}
		for (String line : tagLines) {
			graphics.text(font, line, left + 12, lineY, safeModeComparison ? MUTED : CYAN);
			lineY += 11;
		}
		drawControl(graphics, owner, field, left, right, y, height, mouseX, mouseY);
		return y + height + 6;
	}

	private void drawControl(GuiGraphicsExtractor graphics, Object owner, Field field,
			int left, int right, int y, int height, int mouseX, int mouseY) {
		int controlWidth = Math.clamp((right - left) / 3, 116, 210);
		int x = right - controlWidth - 10;
		int controlY = y + (height - 22) / 2;
		try {
			SettingChoice dropdown = field.getAnnotation(SettingChoice.class);
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
		graphics.text(font, trim(label, width - 30), x + 8, y + 7, CYAN);
		graphics.text(font, "▦", x + width - 15, y + 7, MUTED);
	}

	private void openChoicePicker(Object owner, Field field, SettingChoice dropdown) {
		choiceOwner = owner;
		choiceField = field;
		choiceDropdown = dropdown;
		setFocused(null);
		if (search != null) search.visible = false;
	}

	private void drawChoiceModal(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
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
		graphics.text(font, "Choose " + displayName(option.name()), x + 14, y + 14, TEXT);
		if (soundChoice) {
			graphics.text(font, "Right-click a sound to preview it", x + 14, y + 26, CYAN);
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
			graphics.centeredText(font, trim(labels.get(index), cellWidth - 12),
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

	private int choiceValue() {
		try {
			return choiceField.getInt(choiceOwner);
		} catch (IllegalAccessException ignored) {
			return 0;
		}
	}

	private void chooseDropdownValue(int value) {
		boolean customTheme = isThemeChoice(choiceField) && value == 34;
		try {
			choiceField.setInt(choiceOwner, value);
			applyChoiceSideEffect(choiceField, value);
			ConfigManager.save();
		} catch (IllegalAccessException ignored) {
		}
		closeChoicePicker();
		if (customTheme) openCustomThemePanel();
	}

	private void closeChoicePicker() {
		choiceField = null;
		choiceOwner = null;
		choiceDropdown = null;
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
		if (search != null) search.visible = true;
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
		graphics.text(font, "CUSTOM THEME", x + 14, y + 13, TEXT);
		graphics.text(font, "Select a role to edit it · Changes preview live",
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
				graphics.text(font, role.label(), cellX + 41, cellY + 8,
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
		graphics.text(font, label, x + width - font.width(label), y + 2, TEXT);
	}

	private void drawColour(GuiGraphicsExtractor graphics, int x, int y, int width, int colour) {
		graphics.fill(x, y, x + width, y + 22, CARD);
		outline(graphics, x, y, width, 22, BORDER);
		graphics.fill(x + 5, y + 4, x + 35, y + 18, colour);
		outline(graphics, x + 5, y + 4, 30, 14, BORDER);
		String hex = "#%06X".formatted(colour & 0xFFFFFF);
		graphics.text(font, hex, x + 44, y + 7, TEXT);
	}

	private void drawTextValue(GuiGraphicsExtractor graphics, int x, int y, int width, String value) {
		graphics.fill(x, y, x + width, y + 22, CARD);
		outline(graphics, x, y, width, 22, BORDER);
		graphics.text(font, trim(value, width - 16), x + 8, y + 7, TEXT);
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
		graphics.centeredText(font, label, x + width / 2, y + 7, TEXT);
	}

	private void drawSpecialSparklingConfirmation(GuiGraphicsExtractor graphics,
			int mouseX, int mouseY) {
		int w = Math.min(500, width - 40);
		int h = 132;
		int x = (width - w) / 2;
		int y = (height - h) / 2;
		graphics.fill(0, 0, width, height, 0xBB000000);
		graphics.fill(x, y, x + w, y + h, SURFACE);
		outline(graphics, x, y, w, h, RED);
		graphics.centeredText(font, "EPILEPSY WARNING", x + w / 2, y + 15, RED);
		graphics.centeredText(font, "This option may affect photosensitive players.",
			x + w / 2, y + 40, RED);
		graphics.centeredText(font, "Please confirm that you want to enable it.",
			x + w / 2, y + 54, RED);
		graphics.centeredText(font, "Enable Special Sparkling Catch?", x + w / 2, y + 72, GOLD);
		int cancelX = x + w / 2 - 112;
		int enableX = x + w / 2 + 8;
		drawButton(graphics, cancelX, y + 96, 104, "Cancel", mouseX, mouseY);
		drawButton(graphics, enableX, y + 96, 104, "Enable", mouseX, mouseY);
		hits.add(new Hit(cancelX, y + 96, cancelX + 104, y + 118,
			() -> specialSparklingConfirmation = false));
		hits.add(new Hit(enableX, y + 96, enableX + 104, y + 118, () -> {
			ConfigManager.get().sparkling.specialSparklingCatch = true;
			specialSparklingConfirmation = false;
			ConfigManager.save();
		}));
	}

	private void drawFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (selected == null) return;
		int footerTop = height - FOOTER_HEIGHT;
		int y = footerTop + (FOOTER_HEIGHT - 22) / 2;
		int resetX = width - 14 - 90;
		int closeX = resetX - 8 - 58;
		String description = trim(selected.info.desc(), Math.max(40, closeX - NAV_WIDTH - 32));
		drawScaledText(graphics, description, NAV_WIDTH + 20,
			footerTop + (FOOTER_HEIGHT - font.lineHeight) / 2f, 1.0f, MUTED);
		boolean armed = System.currentTimeMillis() < resetArmedUntil;
		drawButton(graphics, closeX, y, 58, "Done", mouseX, mouseY);
		drawButton(graphics, resetX, y, 90, armed ? "Confirm Reset" : "Reset Page", mouseX, mouseY);
		hits.add(new Hit(closeX, y, closeX + 58, y + 22, this::onClose));
		hits.add(new Hit(resetX, y, resetX + 90, y + 22, this::resetSelected));
	}

	private void drawScaledText(GuiGraphicsExtractor graphics, String text,
			float x, float y, float scale, int colour) {
		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		graphics.text(font, text, Math.round(x / scale), Math.round(y / scale), colour);
		graphics.pose().popMatrix();
	}

	private void drawScaledCenteredText(GuiGraphicsExtractor graphics, String text,
			float centerX, float y, float scale, int colour) {
		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		graphics.centeredText(font, text, Math.round(centerX / scale), Math.round(y / scale), colour);
		graphics.pose().popMatrix();
	}

	private void resetSelected() {
		if (selected == null) return;
		if (System.currentTimeMillis() >= resetArmedUntil) {
			resetArmedUntil = System.currentTimeMillis() + 3_000L;
			return;
		}
		Map<Class<?>, Object> defaults = new HashMap<>();
		for (VisibleSetting setting : visibleSettings) {
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
		graphics.text(font, editingColour ? "Choose " + option.name()
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
		graphics.text(font, previewLabel,
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

		graphics.text(font, "Hue", x + 14, y + 156, MUTED);
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
			graphics.text(font, "Drag the gradients or enter #RRGGBB", x + 14, y + 184, DIM);
			return;
		}

		graphics.text(font, "Opacity", x + 14, y + 184, MUTED);
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
		graphics.text(font, Math.round(editingAlpha / 255f * 100f) + "%",
			x + w - 42, y + 199, TEXT);
		graphics.text(font, "Drag the gradients or enter #RRGGBB / #AARRGGBB",
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
		if (search != null) search.visible = !customThemePanel;
	}

	private void drawUnlockPanel(GuiGraphicsExtractor graphics) {
		if (signalCompletedAt > 0 && System.currentTimeMillis() - signalCompletedAt >= 900L) {
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
		outline(graphics, x, y, w, h, GOLD);
		int[][] nodes = constellation(x, y, w, h);
		int[] order = constellationOrder();
		int completedLines = Math.max(0, unlockProgress - 1);
		for (int step = 0; step < completedLines; step++) {
			int from = order[step];
			int to = order[step + 1];
			drawSignalLine(graphics, nodes[from][0], nodes[from][1], nodes[to][0], nodes[to][1],
				signalColour(step));
		}
		if (signalCompletedAt > 0) {
			int last = order[order.length - 1];
			int first = order[0];
			drawSignalLine(graphics, nodes[last][0], nodes[last][1], nodes[first][0], nodes[first][1],
				signalColour(order.length));
		}
		for (int i = 0; i < nodes.length; i++) {
			boolean completed = false;
			for (int step = 0; step < unlockProgress; step++) completed |= order[step] == i;
			boolean next = unlockProgress < order.length && order[unlockProgress] == i;
			int colour = signalCompletedAt > 0 ? signalColour(i) : completed ? GREEN : next ? CYAN : DIM;
			int pulse = signalCompletedAt > 0
				? 6 + (int) (3 * Math.abs(Math.sin((System.currentTimeMillis() - signalCompletedAt) / 90.0)))
				: next ? 8 : 6;
			drawDiamond(graphics, nodes[i][0], nodes[i][1], pulse, colour);
			int index = i;
			if (signalCompletedAt == 0) {
				hits.add(new Hit(nodes[i][0] - 16, nodes[i][1] - 16, nodes[i][0] + 17,
					nodes[i][1] + 17, () -> clickConstellation(index)));
			}
		}
		if (signalCompletedAt > 0) {
			long age = System.currentTimeMillis() - signalCompletedAt;
			int radius = 8 + (int) (age / 18L);
			drawDiamondOutline(graphics, x + w / 2, y + h / 2, radius,
				signalColour((int) age / 80));
		}
	}

	private int[][] constellation(int x, int y, int w, int h) {
		int[][] nodes = new int[9][2];
		int centreX = x + w / 2;
		int centreY = y + h / 2;
		int radius = Math.max(18, Math.min(116, (Math.min(w, h) - 64) / 2));
		for (int i = 0; i < nodes.length; i++) {
			double angle = -Math.PI / 2 + i * Math.PI * 2 / nodes.length;
			nodes[i][0] = centreX + (int) Math.round(Math.cos(angle) * radius);
			nodes[i][1] = centreY + (int) Math.round(Math.sin(angle) * radius);
		}
		return nodes;
	}

	private void clickConstellation(int index) {
		int[] order = constellationOrder();
		if (index != order[unlockProgress]) {
			AlertSounds.play(Minecraft.getInstance(), 21, 0.8f, 0.65f);
			unlockProgress = index == order[0] ? 1 : 0;
			return;
		}
		AlertSounds.play(Minecraft.getInstance(), 4, 0.65f, 0.85f + unlockProgress * 0.16f);
		if (++unlockProgress == order.length) {
			signalCompletedAt = System.currentTimeMillis();
		}
	}

	private static int[] constellationOrder() {
		return new int[]{0, 4, 8, 3, 7, 2, 6, 1, 5};
	}

	private void completeAdvancedUnlock() {
		unlockPanel = false;
		signalCompletedAt = 0;
		AdvancedUnlock.unlock();
		loadCategories();
		selected = categories.stream().filter(category -> category.key.equals("advanced"))
			.findFirst().orElse(selected);
		selectedGroups.clear();
		openGroups.clear();
		scroll = 0;
		if (search != null) {
			search.setValue("");
			search.visible = true;
		}
	}

	private static int signalColour(int step) {
		return 0xFF000000 | (java.awt.Color.HSBtoRGB((step * 0.105f
			+ (System.currentTimeMillis() % 3_000L) / 3_000f) % 1f, 0.5f, 1f) & 0xFFFFFF);
	}

	private static void drawSignalLine(GuiGraphicsExtractor graphics,
			int x0, int y0, int x1, int y1, int colour) {
		int dx = Math.abs(x1 - x0);
		int sx = x0 < x1 ? 1 : -1;
		int dy = -Math.abs(y1 - y0);
		int sy = y0 < y1 ? 1 : -1;
		int error = dx + dy;
		while (true) {
			graphics.fill(x0 - 1, y0 - 1, x0 + 2, y0 + 2, colour);
			if (x0 == x1 && y0 == y1) break;
			int twice = error * 2;
			if (twice >= dy) { error += dy; x0 += sx; }
			if (twice <= dx) { error += dx; y0 += sy; }
		}
	}

	private static void drawDiamondOutline(GuiGraphicsExtractor graphics,
			int x, int y, int radius, int colour) {
		for (int row = -radius; row <= radius; row++) {
			int half = radius - Math.abs(row);
			graphics.fill(x - half, y + row, x - half + 1, y + row + 1, colour);
			graphics.fill(x + half, y + row, x + half + 1, y + row + 1, colour);
		}
	}

	private void drawDiamond(GuiGraphicsExtractor graphics, int x, int y, int radius, int colour) {
		for (int row = -radius; row <= radius; row++) {
			int half = radius - Math.abs(row);
			graphics.fill(x - half, y + row, x + half + 1, y + row + 1, colour);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
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
			if (editor.isMouseOver(event.x(), event.y())) {
				return super.mouseClicked(event, doubled);
			}
			return true;
		}
		if (event.button() == 0 && updateColourControl(event.x(), event.y())) return true;
		if (editor != null && editor.isMouseOver(event.x(), event.y())) {
			return super.mouseClicked(event, doubled);
		}
		if (event.button() == 0) {
			int minimum = modalHitStart >= 0 ? modalHitStart : 0;
			for (int i = hits.size() - 1; i >= minimum; i--) {
				Hit hit = hits.get(i);
				if (!hit.contains(event.x(), event.y())) continue;
				hit.action.run();
				return true;
			}
		}
		if (modalHitStart >= 0) return true;
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (updateColourControl(event.x(), event.y())) return true;
		if (draggingSlider != null) {
			updateSlider(event.x());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
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
		if (mouseX < NAV_WIDTH && mouseY >= HEADER_HEIGHT && mouseY < height - FOOTER_HEIGHT) {
			int viewport = height - HEADER_HEIGHT - FOOTER_HEIGHT - 8;
			int max = Math.max(0, navigationContentHeight - viewport);
			navigationScroll = Math.clamp(navigationScroll
				+ (scrollY > 0 ? -34 : scrollY < 0 ? 34 : 0), 0, max);
			return true;
		}
		if (mouseX < NAV_WIDTH || mouseY < HEADER_HEIGHT || mouseY >= height - FOOTER_HEIGHT) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		int viewport = height - HEADER_HEIGHT - FOOTER_HEIGHT - 22;
		int max = Math.max(0, contentHeight - viewport);
		scroll = Math.clamp(scroll + (scrollY > 0 ? -36 : scrollY < 0 ? 36 : 0), 0, max);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (specialSparklingConfirmation && event.key() == 256) {
			specialSparklingConfirmation = false;
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
		if (unlockPanel && event.key() == 256) {
			closeUnlockPanel();
			return true;
		}
		return super.keyPressed(event);
	}

	private void setBoolean(Object owner, Field field, boolean value) {
		try {
			if (field.getName().equals("specialSparklingCatch") && value) {
				specialSparklingConfirmation = true;
				return;
			}
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
			applyChoiceSideEffect(field, field.getInt(owner));
			ConfigManager.save();
		} catch (IllegalAccessException ignored) {
		}
	}

	private static void applyChoiceSideEffect(Field field, int value) {
		if (field != null && field.getName().equals("outputLogPreset")) {
			OutputLogPresets.apply(value);
		}
	}

	private String dropdownLabel(Field field, SettingChoice dropdown, int value) {
		if (isSoundChoice(field)) return AlertSounds.label(value);
		if (field.getName().equals("outputLogPreset")) {
			int matchedPreset = OutputLogPresets.syncSelection();
			return matchedPreset >= 0 && matchedPreset < dropdown.values().length
				? dropdown.values()[matchedPreset] : dropdown.values()[0];
		}
		if (isThemeChoice(field)) return THEMES.stream().filter(theme -> theme.id == value)
			.findFirst().map(ThemeChoice::label).orElse("Default");
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
		return field.isAnnotationPresent(SettingToggle.class)
			|| field.isAnnotationPresent(SettingChoice.class)
			|| field.isAnnotationPresent(SettingRange.class)
			|| field.isAnnotationPresent(SettingColor.class)
			|| field.isAnnotationPresent(SettingText.class)
			|| field.isAnnotationPresent(SettingAction.class);
	}

	private static String clean(String text) {
		return text.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
	}

	private static String displayName(String text) {
		return text.replace("Hud", "HUD").replace("Gui", "GUI")
			.replace(" Id", " ID").replace("Api", "API");
	}

	private List<String> wrap(String text, int width) {
		if (text == null || text.isBlank()) return List.of();
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
		return lines;
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

	private static void outline(GuiGraphicsExtractor graphics, int x, int y,
			int width, int height, int colour) {
		UIDraw.outline(graphics, x, y, width, height, colour);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
