package dev.serko.safariutils.client;

import dev.serko.safariutils.api.SharedSparklingProviders;
import dev.serko.safariutils.api.SparklingPlayerLookup;
import dev.serko.safariutils.api.PartyRefreshStatus;
import dev.serko.safariutils.api.PartySparklingSnapshot;
import dev.serko.safariutils.api.PartyItemSyncProviders;
import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.session.RunHistory;
import dev.serko.safariutils.session.SparklingStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Standalone editor, party collection, and private player lookup for Sparklings. */
public final class SparklingScreen extends Screen {
	private static final Pattern FORMATTED_LIST = Pattern.compile(
		"(?i)(shared|missing)\\s+sparklings?(?:\\s+critters?)?\\s*(?:\\(\\d+\\s*/\\s*\\d+\\))?\\s*:\\s*(.*)$");
	private static final List<SafariBiome> BIOMES = List.of(
		SafariBiome.CAVERN, SafariBiome.ICY, SafariBiome.HAUNTED, SafariBiome.FOREST);
	private static final int BACKGROUND = 0xE0121922;
	private static final int SURFACE = 0xE0141B25;
	private static final int HOVER = 0xEF1B2532;
	private static final int BORDER = 0x38FFFFFF;
	private static final int KEYLINE = 0x24FFFFFF;
	private static final int GOLD = 0xFFFFD700;
	private static final int AQUA = 0xFF55FFFF;
	private static final int GREEN = 0xFF55FF55;
	private static final int RED = 0xFFFF7777;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int LABEL = 0xFFBBBBBB;
	private static final int DIM = 0xFF888888;
	private static final int LINE = 13;
	private static final long LOOKUP_CACHE_MILLIS = 5 * 60_000L;

	private enum Tab { COLLECTION, PARTY, LOOKUP }
	private record Hit(int x, int y, int width, int height, String label, Runnable action) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}
	private record NumberHit(int x, int y, int width, int height, int textY, Critter critter,
			boolean feathers) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private final List<Hit> hits = new ArrayList<>();
	private final List<NumberHit> numberHits = new ArrayList<>();
	private Tab tab = Tab.COLLECTION;
	private float scale = 1f;
	private int panelLeft;
	private int panelTop;
	private int panelWidth;
	private int panelHeight;
	private String status = "Click any critter count number or rainbow feathers number to change it";
	private int statusColour = DIM;
	private long statusUntil;
	private EditBox editor;
	private NumberHit editingBounds;
	private Critter editingCritter;
	private boolean editingFeathers;
	private int editingOriginal;
	private EditBox lookupName;
	private boolean lookupLoading;
	private static SparklingPlayerLookup lastLookup;
	private static String lastLookupName = "";
	private static final List<SparklingPlayerLookup> recentLookups = new ArrayList<>();
	private static long dismissedImportFetchedAt;
	private boolean recentLookupsOpen;
	private SparklingPlayerLookup pendingImport;
	private PartySparklingSnapshot cachedPartySnapshot;
	private PartyRefreshStatus cachedPartyRefresh;
	private long partyCacheUntil;

	public SparklingScreen() {
		super(Component.literal("Sparkling Collection"));
	}

	public static void open() {
		Minecraft.getInstance().execute(() -> ClientCompat.setScreen(new SparklingScreen()));
	}

	@Override
	protected void init() {
		clearWidgets();
		hits.clear();
		numberHits.clear();
		scale = ResponsiveUI.scale(width, height);
		int logicalWidth = ResponsiveUI.logicalWidth(width, scale);
		int logicalHeight = ResponsiveUI.logicalHeight(height, scale);
		panelWidth = Math.min(660, logicalWidth - 8);
		panelHeight = Math.min(330, logicalHeight - 8);
		panelLeft = (logicalWidth - panelWidth) / 2;
		panelTop = (logicalHeight - panelHeight) / 2;
		if (tab == Tab.LOOKUP && SharedSparklingProviders.available()) addLookupField();
		checkCachedLocalCollection();
	}

	private void addLookupField() {
		int x = panelLeft + 18;
		int y = panelTop + 54;
		lookupName = new EditBox(font, x, y, Math.min(190, panelWidth - 130), 18,
			Component.literal("Minecraft username"));
		lookupName.setMaxLength(16);
		lookupName.setValue(lastLookupName);
		lookupName.setResponder(value -> lastLookupName = value);
		lookupName.setHint(Component.literal("Minecraft username"));
		lookupName.setTextColor(WHITE);
		lookupName.setTextColorUneditable(DIM);
		addRenderableWidget(lookupName);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick) {
		int mx = Math.round(mouseX / scale);
		int my = Math.round(mouseY / scale);
		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		graphics.fillGradient(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight,
			0xE0181220, BACKGROUND);
		drawRainbowBorder(graphics);
		UIDraw.outline(graphics, panelLeft + 2, panelTop + 2, panelWidth - 4, panelHeight - 4, KEYLINE);
		hits.clear();
		numberHits.clear();
		drawTitle(graphics, mx, my);
		drawTabs(graphics, mx, my);
		switch (tab) {
			case COLLECTION -> drawCollection(graphics, mx, my);
			case PARTY -> drawParty(graphics, mx, my);
			case LOOKUP -> drawLookup(graphics, mx, my);
		}
		drawStatus(graphics);
		if (tab == Tab.LOOKUP) drawRecentLookups(graphics, mx, my);
		if (editor != null && editingBounds != null) {
			graphics.fill(editingBounds.x(), editingBounds.y(),
				editingBounds.x() + editingBounds.width(), editingBounds.y() + editingBounds.height(),
				SURFACE);
		}
		super.extractRenderState(graphics, mx, my, partialTick);
		if (editor != null && editingBounds != null) {
			UIDraw.outline(graphics, editingBounds.x(), editingBounds.y(),
				editingBounds.width(), editingBounds.height(), GOLD);
		}
		checkCachedLocalCollection();
		if (pendingImport != null) drawImportConfirmation(graphics, mx, my);
		graphics.pose().popMatrix();
	}

	private void drawTitle(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		rainbowText(graphics, "Sparkling Collection", panelLeft + 12, panelTop + 10);
		button(graphics, panelLeft + panelWidth - 55, panelTop + 7, 43, 18, "Done", false,
			mouseX, mouseY, this::onClose);
	}

	private void drawTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int width = 92;
		int total = SharedSparklingProviders.available() ? width * 3 : width * 2;
		int x = panelLeft + (panelWidth - total) / 2;
		tabButton(graphics, x, panelTop + 27, width, "My Collection", Tab.COLLECTION, mouseX, mouseY);
		tabButton(graphics, x + width, panelTop + 27, width, "Party", Tab.PARTY, mouseX, mouseY);
		if (SharedSparklingProviders.available()) {
			tabButton(graphics, x + width * 2, panelTop + 27, width, "Player Lookup", Tab.LOOKUP,
				mouseX, mouseY);
		}
	}

	private void tabButton(GuiGraphicsExtractor graphics, int x, int y, int width, String label,
			Tab target, int mouseX, int mouseY) {
		boolean selected = tab == target;
		boolean hovered = contains(x, y, width, 18, mouseX, mouseY);
		graphics.fill(x, y, x + width, y + 18, selected ? 0xB025303D : hovered ? HOVER : SURFACE);
		UIDraw.outline(graphics, x, y, width, 18, BORDER);
		if (selected) drawRainbowLine(graphics, x + 3, y + 15, width - 6, 1);
		centered(graphics, label, x, width, centeredTextY(y, 18), selected || hovered ? WHITE : LABEL);
		hits.add(new Hit(x, y, width, 18, label, () -> switchTab(target)));
	}

	private void switchTab(Tab target) {
		commitEditor();
		tab = target;
		status = defaultStatus(target);
		statusColour = DIM;
		statusUntil = 0L;
		rebuildWidgets();
	}

	private void drawCollection(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int top = panelTop + 55;
		int totalSpecies = Critters.total();
		int since = RunHistory.runsSinceLastSparkling();
		int setDuplicates = SparklingStats.duplicates();
		String duplicateText = SparklingStats.hasImportedDuplicates()
			&& SparklingStats.importedDuplicates() != setDuplicates
			? SparklingStats.importedDuplicates() + " (" + setDuplicates + ")"
			: String.valueOf(setDuplicates);
		int displayedTotal = SparklingStats.hasImportedDuplicates()
			? SparklingStats.unique() + SparklingStats.importedDuplicates() : SparklingStats.total();
		String prefix = "Unique Sparklings  " + SparklingStats.unique() + "/" + totalSpecies
			+ "   ✦   Sparklings  " + displayedTotal
			+ "   ✦   Duplicates  " + duplicateText
			+ "   ✦   Rainbow Feathers  ";
		String feathers = String.valueOf(SparklingStats.rainbowFeathers());
		String suffix = "   ✦   Runs Since Last  " + (since < 0 ? "—" : since);
		int summaryWidth = font.width(prefix + feathers + suffix);
		int summaryX = panelLeft + (panelWidth - summaryWidth) / 2;
		text(graphics, prefix, summaryX, top, 0xFFFFE08A);
		int featherX = summaryX + font.width(prefix);
		int featherWidth = Math.max(font.width("00000") + 8, font.width(feathers) + 4);
		int featherHitX = featherX - (featherWidth - font.width(feathers)) / 2;
		if (contains(featherHitX, top - 2, featherWidth, 12, mouseX, mouseY)) {
			graphics.fill(featherHitX, top - 2, featherHitX + featherWidth, top + 10, HOVER);
		}
		text(graphics, feathers, featherX, top, 0xFFFFE08A);
		text(graphics, suffix, featherX + font.width(feathers), top, 0xFFFFE08A);
		numberHits.add(new NumberHit(featherHitX, top - 2, featherWidth, 12, top, null, true));

		int barLeft = panelLeft + 24;
		int barRight = panelLeft + panelWidth - 24;
		int barY = top + 16;
		graphics.fill(barLeft, barY, barRight, barY + 4, 0x553A2A10);
		int filled = totalSpecies == 0 ? 0
			: (barRight - barLeft) * SparklingStats.unique() / totalSpecies;
		graphics.fill(barLeft, barY, barLeft + filled, barY + 4, 0xFFFFC83D);
		drawSpeciesColumns(graphics, barY + 12, mouseX, mouseY, null, true);
	}

	private void drawSpeciesColumns(GuiGraphicsExtractor graphics, int y, int mouseX, int mouseY,
			Set<String> remoteSpecies, boolean editable) {
		int columnWidth = Math.min(150, (panelWidth - 24) / 4);
		int left = panelLeft + (panelWidth - columnWidth * 4) / 2;
		for (int column = 0; column < BIOMES.size(); column++) {
			SafariBiome biome = BIOMES.get(column);
			int x = left + column * columnWidth;
			List<Critter> species = Critters.inBiome(biome);
			boolean complete = species.stream().allMatch(critter -> remoteSpecies == null
				? SparklingStats.count(critter) > 0 : remoteSpecies.contains(speciesId(critter.name())));
			drawBiomeTitle(graphics, biome, x + 8, columnWidth - 16, y, complete);
			for (int row = 0; row < species.size(); row++) {
				Critter critter = species.get(row);
				int rowY = y + 17 + row * LINE;
				int count = SparklingStats.count(critter);
				boolean remoteOwned = remoteSpecies != null
					&& remoteSpecies.contains(speciesId(critter.name()));
				int nameColour = remoteSpecies == null
					? count == 0 ? 0xFF5F594E : 0xFF000000 | critter.rarity().colour()
					: remoteOwned ? 0xFF000000 | critter.rarity().colour() : 0xFF5F594E;
				text(graphics, critter.name(), x + 8, rowY, nameColour);
				String value;
				int colour;
				if (remoteSpecies == null) {
					value = count == 0 ? "—" : String.valueOf(count);
					colour = count > 1 ? GOLD : count == 1 ? 0xFFFFF2B2 : 0xFF5F594E;
				} else {
					boolean owned = remoteSpecies.contains(speciesId(critter.name()));
					value = owned ? "✓" : "—";
					colour = owned ? GREEN : 0xFF5F594E;
				}
				int valueWidth = editable
					? Math.max(font.width("0000") + 8, font.width(value) + 10)
					: Math.max(18, font.width(value) + 10);
				int valueX = x + columnWidth - valueWidth - 8;
				if (editable && contains(valueX, rowY - 2, valueWidth, 12, mouseX, mouseY)) {
					graphics.fill(valueX, rowY - 2, valueX + valueWidth, rowY + 10, HOVER);
				}
				if (remoteSpecies == null) centered(graphics, value, valueX, valueWidth, rowY, colour);
				else boldCentered(graphics, value, valueX, valueWidth, rowY, colour);
				if (editable) numberHits.add(new NumberHit(valueX, rowY - 2, valueWidth, 12,
					rowY, critter, false));
			}
		}
	}

	private void drawParty(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = panelLeft + 12;
		int y = panelTop + 54;
		boolean privateProvider = SharedSparklingProviders.available();
		PartySparklingSnapshot snapshot = privateProvider ? partySnapshot() : null;
		PartyRefreshStatus refresh = privateProvider ? partyRefreshStatus() : null;
		boolean automatic = snapshot != null && snapshot.apiManaged();
		boolean fallback = refresh != null && refresh.manualFallback();
		boolean editable = !privateProvider || fallback;
		if (editable) {
			button(graphics, x, y, 108, 18, "Import Clipboard", false, mouseX, mouseY,
				this::importPartyClipboard);
			button(graphics, x + 112, y, 52, 18, "Clear", false, mouseX, mouseY, () -> {
				SparklingMode.clearShared();
				setStatus("Party collection cleared", GOLD);
			});
		} else {
			String description = refresh != null && refresh.error() != null ? refresh.error()
				: "Automatically loads shared Sparklings when entering the Safari";
			if (!automatic) centered(graphics, description, panelLeft, panelWidth,
				centeredTextY(y, 18), refresh != null && refresh.error() != null ? RED : DIM);
		}
		Set<Critter> shared = SparklingMode.shared();
		int summaryY = automatic ? y : y + 28;
		if (snapshot != null && !snapshot.members().isEmpty()) {
			drawPartyMembers(graphics, snapshot.members(), summaryY);
			summaryY += 12;
		}
		centered(graphics, "Shared Sparklings   ✦   " + shared.size() + "/" + Critters.total(),
			panelLeft, panelWidth, summaryY, AQUA);
		drawPartyColumns(graphics, summaryY + 15, mouseX, mouseY, shared, !editable);
	}

	private void drawPartyColumns(GuiGraphicsExtractor graphics, int y, int mouseX, int mouseY,
			Set<Critter> shared, boolean apiManaged) {
		int left = panelLeft + 12;
		int columnWidth = (panelWidth - 24) / 4;
		for (int column = 0; column < BIOMES.size(); column++) {
			SafariBiome biome = BIOMES.get(column);
			int x = left + column * columnWidth;
			List<Critter> species = Critters.inBiome(biome);
			drawBiomeTitle(graphics, biome, x + 8, columnWidth - 16, y,
				species.stream().allMatch(shared::contains));
			for (int row = 0; row < species.size(); row++) {
				Critter critter = species.get(row);
				int rowY = y + 17 + row * LINE;
				boolean shownSelected = shared.contains(critter);
				int rowWidth = columnWidth - 12;
				boolean hovered = !apiManaged
					&& contains(x + 4, rowY - 2, rowWidth, 12, mouseX, mouseY);
				if (hovered) graphics.fill(x + 4, rowY - 2, x + 4 + rowWidth, rowY + 10, HOVER);
				int colour = shownSelected ? 0xFF000000 | critter.rarity().colour() : 0xFF5F594E;
				text(graphics, critter.name(), x + 8, rowY, colour);
				String status = shownSelected ? "✓" : "—";
				int statusWidth = Math.max(18, font.width(status) + 10);
				int statusX = x + columnWidth - statusWidth - 8;
				boldCentered(graphics, status, statusX, statusWidth, rowY,
					shownSelected ? GREEN : 0xFF5F594E);
				if (!apiManaged) hits.add(new Hit(x + 4, rowY - 2, rowWidth, 12,
					critter.name(), () -> toggleParty(critter)));
			}
		}
	}

	private void toggleParty(Critter critter) {
		if (SharedSparklingProviders.available()
				&& partySnapshot().apiManaged()) {
			setStatus("API-cached party collections cannot be changed manually", GOLD);
			return;
		}
		Set<Critter> updated = new LinkedHashSet<>(SparklingMode.shared());
		boolean shouldShare = !updated.contains(critter);
		if (shouldShare) updated.add(critter); else updated.remove(critter);
		SparklingMode.replaceShared(updated);
		setStatus("Party collection updated", AQUA);
	}

	private void importPartyClipboard() {
		String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard()
			.replaceAll("§.", "").trim();
		if (clipboard.isEmpty()) {
			setStatus("Clipboard is empty", RED);
			return;
		}
		boolean missing = false;
		var matcher = FORMATTED_LIST.matcher(clipboard);
		String list = clipboard;
		if (matcher.find()) {
			missing = matcher.group(1).equalsIgnoreCase("missing");
			list = matcher.group(2).trim();
		}
		Set<Critter> parsed = new LinkedHashSet<>();
		List<String> unknown = new ArrayList<>();
		if (!list.equalsIgnoreCase("none") && !list.isBlank()) {
			for (String part : list.split(",", -1)) {
				String name = part.trim();
				Critter critter = Critters.all().stream()
					.filter(candidate -> candidate.name().equalsIgnoreCase(name)).findFirst().orElse(null);
				if (critter == null) unknown.add(name.isEmpty() ? "(empty entry)" : name);
				else parsed.add(critter);
			}
		}
		if (!unknown.isEmpty()) {
			setStatus("Clipboard format not recognized — copy a ‘Shared Sparklings:’ or "
				+ "‘Missing Sparklings:’ message, or a comma-separated shared species list", RED);
			return;
		}
		if (missing) {
			Set<Critter> shared = new LinkedHashSet<>(Critters.all());
			shared.removeAll(parsed);
			SparklingMode.replaceShared(shared);
		} else SparklingMode.replaceShared(parsed);
		setStatus("Imported " + (missing ? "missing" : "shared") + " Sparkling list", GREEN);
	}

	private PartySparklingSnapshot partySnapshot() {
		refreshPartyUiCache();
		return cachedPartySnapshot;
	}

	private PartyRefreshStatus partyRefreshStatus() {
		refreshPartyUiCache();
		return cachedPartyRefresh;
	}

	private void refreshPartyUiCache() {
		long now = System.currentTimeMillis();
		if (cachedPartySnapshot != null && cachedPartyRefresh != null && now < partyCacheUntil) return;
		var provider = SharedSparklingProviders.provider().orElseThrow();
		cachedPartySnapshot = provider.partySnapshot();
		cachedPartyRefresh = provider.partyRefreshStatus();
		partyCacheUntil = now + 250L;
	}

	private void checkCachedLocalCollection() {
		if (!SharedSparklingProviders.available() || pendingImport != null) return;
		SharedSparklingProviders.provider().orElseThrow().cachedLocalCollection()
			.filter(result -> result.fetchedAt() != dismissedImportFetchedAt)
			.filter(result -> !collectionMatches(result))
			.ifPresent(result -> pendingImport = result);
	}

	private static boolean collectionMatches(SparklingPlayerLookup result) {
		Set<String> saved = new LinkedHashSet<>();
		Set<String> api = new LinkedHashSet<>();
		for (Critter critter : Critters.all()) {
			String id = speciesId(critter.name());
			if (SparklingStats.count(critter) > 0) saved.add(id);
			if (result.species().contains(id)) api.add(id);
		}
		int knownDuplicates = SparklingStats.hasImportedDuplicates()
			? SparklingStats.importedDuplicates() : SparklingStats.duplicates();
		if (!saved.equals(api)) return false;
		if (SparklingStats.hasImportedDuplicates()) {
			return SparklingStats.importedSetUnchanged()
				&& (result.duplicates() < 0 || knownDuplicates == result.duplicates());
		}
		return result.duplicates() < 0 || knownDuplicates == result.duplicates();
	}

	private void drawImportConfirmation(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int width = Math.min(430, panelWidth - 40);
		int height = 112;
		int x = panelLeft + (panelWidth - width) / 2;
		int y = panelTop + (panelHeight - height) / 2;
		graphics.fill(panelLeft + 2, panelTop + 2, panelLeft + panelWidth - 2,
			panelTop + panelHeight - 2, 0xA0000000);
		graphics.fill(x, y, x + width, y + height, 0xFF141B25);
		UIDraw.outline(graphics, x, y, width, height, GOLD);
		centered(graphics, "Import Your API Collection?", x, width, y + 12, 0xFFFFE08A);
		centered(graphics, "Your saved collection does not match Hypixel", x, width, y + 31, WHITE);
		centered(graphics, "Import sets every API-owned unique to at least 1 and saves",
			x, width, y + 45, LABEL);
		centered(graphics, "the API duplicate total without assigning dupes to random species",
			x, width, y + 57, LABEL);
		button(graphics, x + width / 2 - 98, y + 82, 88, 20, "Not Now", false,
			mouseX, mouseY, this::dismissPendingImport);
		button(graphics, x + width / 2 + 10, y + 82, 88, 20, "Import", true,
			mouseX, mouseY, () -> {
				SparklingStats.importApiCollection(pendingImport.species(), pendingImport.duplicates());
				setStatus("Imported your Hypixel Sparkling collection", GREEN);
				dismissPendingImport();
			});
	}

	private void dismissPendingImport() {
		if (pendingImport != null) dismissedImportFetchedAt = pendingImport.fetchedAt();
		pendingImport = null;
	}

	private void drawLookup(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!SharedSparklingProviders.available()) return;
		int y = panelTop + 54;
		drawLookupButton(graphics, panelLeft + 214, y, mouseX, mouseY);
		if (lastLookup == null) {
			return;
		}
		drawLookupNameAndTickets(graphics, y + 29);
		int unique = lastLookup.species().size();
		String duplicates = lastLookup.duplicates() < 0 ? "—" : String.valueOf(lastLookup.duplicates());
		String total = lastLookup.duplicates() < 0 ? "—" : String.valueOf(unique + lastLookup.duplicates());
		String summary = "Unique Sparklings  " + unique + "/" + Critters.total()
			+ "   ✦   Sparklings  " + total + "   ✦   Duplicates  " + duplicates;
		centered(graphics, summary, panelLeft, panelWidth, y + 46, WHITE);
		drawSpeciesColumns(graphics, y + 64, mouseX, mouseY, lastLookup.species(), false);
	}

	private void drawLookupNameAndTickets(GuiGraphicsExtractor graphics, int y) {
		int columnWidth = Math.min(150, (panelWidth - 24) / 4);
		int x = panelLeft + (panelWidth - columnWidth * 4) / 2 + 8;
		Component name = Component.literal(lastLookup.username()).withStyle(style -> style.withColor(AQUA));
		if (PartyItemSyncProviders.whitelistedName(lastLookup.username())) {
			UIDraw.rainbowText(graphics, font, lastLookup.username(), x, y, 0.45f);
		} else {
			SpecialTheme.text(graphics, font, name, x, y, AQUA);
		}
		String[] keys = {"Basic", "Economy", "Premium", "First Class"};
		String[] labels = {"Basic", "Economy", "Premium", "First-Class"};
		int[] colours = {0xFF55FF55, 0xFF5599FF, 0xFFAA55FF, 0xFFFFAA00};
		int cursor = x + font.width(name) + font.width("  ");
		text(graphics, "(  ", cursor, y, WHITE);
		cursor += font.width("(  ");
		for (int i = 0; i < keys.length; i++) {
			if (i > 0) {
				text(graphics, "  │   ", cursor, y, WHITE);
				cursor += font.width("  │   ");
			}
			String value = labels[i] + "  " + lastLookup.tickets().getOrDefault(keys[i], 0L);
			text(graphics, value, cursor, y, colours[i]);
			cursor += font.width(value);
		}
		text(graphics, "  )", cursor, y, WHITE);
	}

	private void drawLookupButton(GuiGraphicsExtractor graphics, int x, int y,
			int mouseX, int mouseY) {
		String entered = lookupName == null ? lastLookupName : lookupName.getValue().trim();
		boolean same = lastLookup != null && entered.equalsIgnoreCase(lastLookup.username());
		boolean stale = same && System.currentTimeMillis() - lastLookup.fetchedAt() >= LOOKUP_CACHE_MILLIS;
		long cooldown = SharedSparklingProviders.provider()
			.map(provider -> Math.max(0L, provider.lookupAvailableAt() - System.currentTimeMillis()))
			.orElse(0L);
		boolean freshCached = same && !stale;
		boolean disabled = lookupLoading || freshCached || cooldown > 0;
		String label = lookupLoading ? "Loading…" : freshCached ? "Cached"
			: cooldown > 0 ? "Lookup " + ((cooldown + 999) / 1000) + "s"
			: same ? "Refresh" : "Lookup";
		int colour = disabled ? DIM : same ? GOLD : AQUA;
		boolean hovered = contains(x, y, 72, 18, mouseX, mouseY);
		graphics.fill(x, y, x + 72, y + 18, hovered ? HOVER : SURFACE);
		UIDraw.outline(graphics, x, y, 72, 18, colour);
		centered(graphics, label, x, 72, centeredTextY(y, 18), colour);
		if (!disabled) hits.add(new Hit(x, y, 72, 18, label, this::lookupPlayer));
	}

	private void lookupPlayer() {
		if (lookupLoading || lookupName == null) return;
		String username = lookupName.getValue().trim();
		if (!username.matches("[A-Za-z0-9_]{1,16}")) {
			setStatus("Enter a valid Minecraft username", RED);
			return;
		}
		lookupLoading = true;
		setStatus("Looking up " + username + "…", DIM);
		SharedSparklingProviders.provider().orElseThrow().lookupPlayer(username)
			.whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
				lookupLoading = false;
				if (error != null) setStatus(ClientMessages.apiFailure("look up Sparklings", error), RED);
				else {
					lastLookup = result;
					rememberLookup(result);
					lastLookupName = result.username();
					if (lookupName != null) lookupName.setValue(result.username());
					setStatus("Loaded " + result.username() + "'s Profile", GREEN);
					offerImportIfLocal(result);
				}
			}));
	}

	private void offerImportIfLocal(SparklingPlayerLookup result) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null
			|| !result.username().equalsIgnoreCase(client.player.getGameProfile().name())
			|| collectionMatches(result)) return;
		pendingImport = result;
	}

	private static void rememberLookup(SparklingPlayerLookup result) {
		recentLookups.removeIf(saved -> saved.username().equalsIgnoreCase(result.username()));
		recentLookups.addFirst(result);
		while (recentLookups.size() > 10) recentLookups.removeLast();
	}

	/** Adds automatic party results to the same five-minute cache as manual lookups. */
	public static void cacheRecentLookups(List<SparklingPlayerLookup> results) {
		for (SparklingPlayerLookup result : results) rememberLookup(result);
	}

	private void drawRecentLookups(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (recentLookups.isEmpty()) return;
		int width = 142;
		int height = 18;
		int x = panelLeft + panelWidth - width - 12;
		int y = panelTop + 54;
		String selected = lastLookup == null ? "Recent Lookups"
			: "Recent: " + lastLookup.username();
		boolean hovered = contains(x, y, width, height, mouseX, mouseY);
		graphics.fill(x, y, x + width, y + height, hovered ? HOVER : SURFACE);
		UIDraw.outline(graphics, x, y, width, height, recentLookupsOpen ? AQUA : BORDER);
		String buttonText = trimToWidth(selected, width - 25) + (recentLookupsOpen ? "  ▴" : "  ▾");
		if (lastLookup != null && PartyItemSyncProviders.whitelistedName(lastLookup.username())) {
			centeredRainbowName(graphics, "Recent: ", lastLookup.username(),
				recentLookupsOpen ? "  ▴" : "  ▾", x, width, centeredTextY(y, height),
				recentLookupsOpen ? AQUA : LABEL);
		} else {
			centered(graphics, buttonText, x, width, centeredTextY(y, height),
				recentLookupsOpen ? AQUA : LABEL);
		}
		hits.add(new Hit(x, y, width, height, "Recent Lookups",
			() -> recentLookupsOpen = !recentLookupsOpen));
		if (!recentLookupsOpen) return;
		for (int index = 0; index < recentLookups.size(); index++) {
			SparklingPlayerLookup saved = recentLookups.get(index);
			int itemY = y + height * (index + 1);
			boolean itemHovered = contains(x, itemY, width, height, mouseX, mouseY);
			graphics.fill(x, itemY, x + width, itemY + height, itemHovered ? HOVER : 0xFF141B25);
			UIDraw.outline(graphics, x, itemY, width, height, BORDER);
			if (PartyItemSyncProviders.whitelistedName(saved.username())) {
				UIDraw.rainbowText(graphics, font, saved.username(),
					x + (width - font.width(saved.username())) / 2,
					centeredTextY(itemY, height), 0.45f);
			} else {
				centered(graphics, saved.username(), x, width, centeredTextY(itemY, height),
					itemHovered ? WHITE : LABEL);
			}
			hits.add(new Hit(x, itemY, width, height, "Recent Player", () -> selectRecent(saved)));
		}
	}

	private void selectRecent(SparklingPlayerLookup saved) {
		lastLookup = saved;
		lastLookupName = saved.username();
		if (lookupName != null) lookupName.setValue(saved.username());
		recentLookupsOpen = false;
		status = defaultStatus(Tab.LOOKUP);
		statusColour = DIM;
		statusUntil = 0L;
	}

	private void drawStatus(GuiGraphicsExtractor graphics) {
		if (statusUntil > 0L && System.currentTimeMillis() >= statusUntil) {
			status = defaultStatus(tab);
			statusColour = DIM;
			statusUntil = 0L;
		}
		graphics.fill(panelLeft + 8, panelTop + panelHeight - 34,
			panelLeft + panelWidth - 8, panelTop + panelHeight - 7, SURFACE);
		List<String> lines = wrap(status, panelWidth - 28, 2);
		for (int i = 0; i < lines.size(); i++) {
			text(graphics, lines.get(i), panelLeft + 14,
				panelTop + panelHeight - 29 + i * 11, statusColour);
		}
	}

	private void openNumberEditor(NumberHit hit) {
		commitEditor();
		editingBounds = hit;
		editingCritter = hit.critter();
		editingFeathers = hit.feathers();
		int current = editingFeathers ? SparklingStats.rainbowFeathers()
			: SparklingStats.count(editingCritter);
		editingOriginal = current;
		editor = new EditBox(font, hit.x(), hit.textY(), hit.width(), 10,
			Component.literal("Count"));
		editor.setBordered(false);
		editor.setCentered(true);
		editor.setMaxLength(7);
		editor.setValue(String.valueOf(current));
		editor.setTextColor(0xFFFFE08A);
		addRenderableWidget(editor);
		setFocused(editor);
		editor.setFocused(true);
		setInitialFocus(editor);
		editor.setCursorPosition(editor.getValue().length());
		editor.setHighlightPos(0);
	}

	private void commitEditor() {
		if (editor == null) return;
		String value = editor.getValue().trim();
		int maximum = editingFeathers ? 99_999 : 9_999;
		if (value.isEmpty() || !value.chars().allMatch(Character::isDigit)) {
			setStatus("Invalid number", RED);
		} else {
			try {
				long parsed = Long.parseLong(value);
				if (parsed > maximum) setStatus("Maximum is " + maximum, RED);
				else {
					int count = (int) parsed;
					if (count != editingOriginal) {
						if (editingFeathers) SparklingStats.setRainbowFeathers(count);
						else SparklingStats.set(editingCritter, count);
						setStatus((editingFeathers ? "Rainbow Feathers" : editingCritter.name())
							+ " set to " + count, GREEN);
					}
				}
			} catch (NumberFormatException error) {
				setStatus("Maximum is " + maximum, RED);
			}
		}
		removeWidget(editor);
		editor = null;
		editingBounds = null;
		editingCritter = null;
		editingFeathers = false;
		editingOriginal = 0;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		double mouseX = event.x() / scale;
		double mouseY = event.y() / scale;
		MouseButtonEvent scaledEvent = scale == 1f ? event
			: new MouseButtonEvent(mouseX, mouseY, event.buttonInfo());
		if (pendingImport != null) {
			for (int i = hits.size() - 1; i >= 0; i--) {
				Hit hit = hits.get(i);
				if ((hit.label().equals("Import") || hit.label().equals("Not Now"))
						&& hit.contains(mouseX, mouseY)) {
					hit.action().run();
					return true;
				}
			}
			return true;
		}
		if (recentLookupsOpen) {
			for (int i = hits.size() - 1; i >= 0; i--) {
				Hit hit = hits.get(i);
				if ((hit.label().equals("Recent Player") || hit.label().equals("Recent Lookups"))
						&& hit.contains(mouseX, mouseY)) {
					hit.action().run();
					return true;
				}
			}
			recentLookupsOpen = false;
			return true;
		}
		if (editor != null && editor.isMouseOver(mouseX, mouseY)) {
			return super.mouseClicked(scaledEvent, doubled);
		}
		commitEditor();
		for (NumberHit hit : numberHits) {
			if (hit.contains(mouseX, mouseY)) {
				openNumberEditor(hit);
				return true;
			}
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(mouseX, mouseY)) {
				hit.action().run();
				return true;
			}
		}
		return super.mouseClicked(scaledEvent, doubled);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (editor != null && (event.key() == 257 || event.key() == 335)) {
			commitEditor();
			return true;
		}
		if (tab == Tab.LOOKUP && lookupName != null && lookupName.isFocused()
				&& (event.key() == 257 || event.key() == 335)) {
			lookupPlayer();
			return true;
		}
		return super.keyPressed(event);
	}

	private void button(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
			String label, boolean selected, int mouseX, int mouseY, Runnable action) {
		boolean hovered = contains(x, y, width, height, mouseX, mouseY);
		graphics.fill(x, y, x + width, y + height, hovered ? HOVER : SURFACE);
		UIDraw.outline(graphics, x, y, width, height, selected ? GOLD : BORDER);
		centered(graphics, label, x, width, centeredTextY(y, height),
			selected ? GOLD : hovered ? WHITE : LABEL);
		hits.add(new Hit(x, y, width, height, label, action));
	}

	private void drawRainbowBorder(GuiGraphicsExtractor graphics) {
		int horizontalSegments = 28;
		int verticalSegments = Math.max(1,
			Math.round(horizontalSegments * panelHeight / (float) panelWidth));
		int totalSegments = 2 * (horizontalSegments + verticalSegments);
		int segmentWidth = Math.max(1, panelWidth / horizontalSegments);
		int segmentHeight = Math.max(1, panelHeight / verticalSegments);
		float phase = (System.currentTimeMillis() % 8_000L) / 8_000f;
		for (int i = 0; i < horizontalSegments; i++) {
			int x1 = panelLeft + i * segmentWidth;
			int x2 = i == horizontalSegments - 1 ? panelLeft + panelWidth
				: Math.min(panelLeft + panelWidth, x1 + segmentWidth);
			int colour = UIDraw.rainbow(phase, i, totalSegments, 0.55f);
			graphics.fill(x1, panelTop, x2, panelTop + 2, colour);
			graphics.fill(panelLeft + panelWidth - (x2 - panelLeft), panelTop + panelHeight - 2,
				panelLeft + panelWidth - (x1 - panelLeft), panelTop + panelHeight,
				UIDraw.rainbow(phase, horizontalSegments + verticalSegments + i,
					totalSegments, 0.55f));
		}
		for (int i = 0; i < verticalSegments; i++) {
			int y1 = panelTop + i * segmentHeight;
			int y2 = i == verticalSegments - 1 ? panelTop + panelHeight
				: Math.min(panelTop + panelHeight, y1 + segmentHeight);
			graphics.fill(panelLeft + panelWidth - 2, y1, panelLeft + panelWidth, y2,
				UIDraw.rainbow(phase, horizontalSegments + i, totalSegments, 0.55f));
			graphics.fill(panelLeft, panelTop + panelHeight - (y2 - panelTop), panelLeft + 2,
				panelTop + panelHeight - (y1 - panelTop),
				UIDraw.rainbow(phase, 2 * horizontalSegments + verticalSegments + i,
					totalSegments, 0.55f));
		}
	}

	private void rainbowText(GuiGraphicsExtractor graphics, String value, int x, int y) {
		UIDraw.rainbowText(graphics, font, value, x, y, 0.45f);
	}

	private void drawBiomeTitle(GuiGraphicsExtractor graphics, SafariBiome biome,
			int x, int width, int y, boolean complete) {
		String label = "✦ " + biome.displayName() + " ✦";
		int textX = x + (width - font.width(label)) / 2 - 4;
		if (complete) rainbowText(graphics, label, textX, y);
		else text(graphics, label, textX, y, 0xFF000000 | biome.colour());
	}

	private void drawRainbowLine(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		float phase = (System.currentTimeMillis() % 8_000L) / 8_000f;
		for (int i = 0; i < width; i++) {
			int colour = UIDraw.rainbow(phase, i, width, 0.32f);
			graphics.fill(x + i, y, x + i + 1, y + 1, colour);
		}
	}

	private void text(GuiGraphicsExtractor graphics, String value, int x, int y, int colour) {
		SpecialTheme.text(graphics, font, Component.literal(value), x, y, colour);
	}

	private void centered(GuiGraphicsExtractor graphics, String value, int x, int width,
			int y, int colour) {
		text(graphics, value, x + (width - font.width(value)) / 2, y, colour);
	}

	private void centeredRainbowName(GuiGraphicsExtractor graphics, String prefix, String name,
			String suffix, int x, int width, int y, int colour) {
		int cursor = x + (width - font.width(prefix + name + suffix)) / 2;
		text(graphics, prefix, cursor, y, colour);
		cursor += font.width(prefix);
		UIDraw.rainbowText(graphics, font, name, cursor, y, 0.45f);
		cursor += font.width(name);
		text(graphics, suffix, cursor, y, colour);
	}

	private void drawPartyMembers(GuiGraphicsExtractor graphics, List<String> members, int y) {
		String prefix = "Party   ✦   ";
		String joined = String.join(", ", members);
		int cursor = panelLeft + (panelWidth - font.width(prefix + joined)) / 2;
		text(graphics, prefix, cursor, y, WHITE);
		cursor += font.width(prefix);
		for (int i = 0; i < members.size(); i++) {
			if (i > 0) {
				text(graphics, ", ", cursor, y, WHITE);
				cursor += font.width(", ");
			}
			String name = members.get(i);
			if (PartyItemSyncProviders.whitelistedName(name)) {
				UIDraw.rainbowText(graphics, font, name, cursor, y, 0.45f);
			} else {
				text(graphics, name, cursor, y, WHITE);
			}
			cursor += font.width(name);
		}
	}

	private void boldCentered(GuiGraphicsExtractor graphics, String value, int x, int width,
			int y, int colour) {
		Component component = Component.literal(value).withStyle(ChatFormatting.BOLD);
		SpecialTheme.text(graphics, font, component, x + (width - font.width(component)) / 2, y, colour);
	}

	private List<String> wrap(String value, int width, int maxLines) {
		List<String> lines = new ArrayList<>();
		String remaining = value.trim();
		while (!remaining.isEmpty() && lines.size() < maxLines) {
			if (font.width(remaining) <= width) {
				lines.add(remaining);
				remaining = "";
				break;
			}
			String fitted = font.plainSubstrByWidth(remaining, width);
			int split = fitted.lastIndexOf(' ');
			if (split <= 0) split = fitted.length();
			lines.add(remaining.substring(0, split).trim());
			remaining = remaining.substring(split).trim();
		}
		if (!remaining.isEmpty() && !lines.isEmpty()) {
			int last = lines.size() - 1;
			String line = lines.get(last);
			lines.set(last, font.plainSubstrByWidth(line, Math.max(0, width - font.width("…"))) + "…");
		}
		return lines;
	}

	private String trimToWidth(String value, int width) {
		if (font.width(value) <= width) return value;
		return font.plainSubstrByWidth(value, Math.max(0, width - font.width("…"))) + "…";
	}

	private int centeredTextY(int y, int height) {
		return y + Math.max(0, (height - font.lineHeight) / 2) + 1;
	}

	private void setStatus(String value, int colour) {
		status = value.stripTrailing();
		while (status.endsWith(".")) status = status.substring(0, status.length() - 1);
		statusColour = colour;
		statusUntil = colour == DIM ? 0L : System.currentTimeMillis() + 5_000L;
	}

	private static String defaultStatus(Tab target) {
		return switch (target) {
			case COLLECTION -> "Click any critter count number or rainbow feathers number to change it";
			case PARTY -> "This shared list controls the missing HUD while Sparkling Mode is enabled";
			case LOOKUP -> "Loads unique sparklings, duplicates, and ticket amounts";
		};
	}

	private static boolean contains(int x, int y, int width, int height,
			double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	private static String biomeLabel(SafariBiome biome) {
		String value = biome.name().toLowerCase(Locale.ROOT);
		return Character.toUpperCase(value.charAt(0)) + value.substring(1);
	}

	private static String speciesId(String name) {
		return name.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
