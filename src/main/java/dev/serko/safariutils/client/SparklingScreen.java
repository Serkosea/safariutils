package dev.serko.safariutils.client;

import dev.serko.safariutils.api.SharedSparklingProviders;
import dev.serko.safariutils.api.SparklingPlayerLookup;
import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.session.RunHistory;
import dev.serko.safariutils.session.SparklingStats;
import dev.serko.safariutils.state.MonotonicTime;
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
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Collection and lookup views of the shared Safari dashboard. */
public final class SparklingScreen extends Screen {
	private static final List<SafariBiome> BIOMES = List.of(
		SafariBiome.CAVERN, SafariBiome.ICY, SafariBiome.HAUNTED, SafariBiome.FOREST);
	private static final int BACKGROUND = 0xE0121922;
	private static final int SURFACE = 0xE0141B25;
	private static final int HOVER = 0xEF1B2532;
	private static final int BORDER = 0x38FFFFFF;
	private static final int KEYLINE = 0x24FFFFFF;
	private static final int GOLD = StatColours.COINS;
	private static final int COLLECTION_GOLD = StatColours.value(StatColours.COINS);
	private static final int AQUA = 0xFF55FFFF;
	private static final int GREEN = 0xFF55FF55;
	private static final int RED = 0xFFFF7777;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int LABEL = StatColours.INFO;
	private static final int DIM = StatColours.SECONDARY;
	private static final int LINE = 13;
	private static final int SUMMARY_EDGE_MARGIN = 16;
	private static final int MAX_BIOME_ROWS = BIOMES.stream()
		.mapToInt(biome -> Critters.inBiome(biome).size()).max().orElse(10);
	private static final long LOOKUP_CACHE_MILLIS = 5 * 60_000L;
	private static final String LOOKUP_HINT = "Minecraft username";
	private static final Component LOOKUP_HINT_COMPONENT = Component.literal(LOOKUP_HINT);

	private enum Tab { COLLECTION, LOOKUP }
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
	private record CollectionSummary(String prefix, String feathers, String suffix) {
		String fullText() {
			return prefix + feathers + suffix;
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
	private int biomeColumns = 4;
	private String status = "";
	private int statusColour = DIM;
	private long statusUntil;
	private EditBox editor;
	private NumberHit editingBounds;
	private Critter editingCritter;
	private boolean editingFeathers;
	private int editingOriginal;
	private EditBox lookupName;
	private int lookupHintPhase = -1;
	private boolean lookupLoading;
	private long lookupRevision;
	private static SparklingPlayerLookup lastLookup;
	private static String lastLookupName = "";
	private static final LookupHistory recentLookups = PlayerLookupCoordinator.history();
	private boolean recentLookupsOpen;

	public SparklingScreen() {
		super(Component.literal("Sparkling Collection"));
	}

    SparklingScreen(boolean lookup) {
        this(); tab=lookup?Tab.LOOKUP:Tab.COLLECTION; status=defaultStatus(tab);
        if(lookup&&!recentLookups.entries().isEmpty()) {
            lastLookup=recentLookups.entries().getFirst(); lastLookupName="";
        }
    }
    private List<DashboardNavigation.Item> navigation=List.of();

	public static void open() {
		DashboardNavigation.Tab.SPARKLINGS.open();
	}

	@Override
	protected void init() {
		boolean lookupFocused = lookupName != null && lookupName.isFocused();
		boolean editorFocused = editor != null && editor.isFocused();
		clearWidgets();
		hits.clear();
		numberHits.clear();
		updatePanelBounds();
		if (tab == Tab.LOOKUP && SharedSparklingProviders.available()) addLookupField();
		if (editor != null) addRenderableWidget(editor);
		if (editorFocused && editor != null) setFocused(editor);
		else if (lookupFocused && lookupName != null) setFocused(lookupName);
	}

	private void updatePanelBounds() {
        navigation=List.of();
        int twoColumnHeight=tab==Tab.LOOKUP?530:450;
        int fourColumnHeight=tab==Tab.LOOKUP?340:308;
		float twoColumnScale = Math.min(1f, Math.min(width / (float)Math.max(320,DashboardNavigation.width(font)+24), height / (float)twoColumnHeight));
		float fourColumnScale = Math.min(1f, Math.min(width / (float)Math.max(560,DashboardNavigation.width(font)+24), height / (float)fourColumnHeight));
		// Pick the reflow that produces the largest readable result for the complete
		// canvas. Short 16:9 windows often fit four columns better than two tall ones.
		biomeColumns = fourColumnScale >= twoColumnScale ? 4 : 2;
		int preferredHeight = biomeColumns == 2 ? twoColumnHeight : fourColumnHeight;
		int minimumWidth = Math.max(biomeColumns == 2 ? 320 : 560,DashboardNavigation.width(font)+24);
		// Reflow biome columns before scaling; scaling is reserved for canvases that
		// cannot fit even the compact two-column layout or its vertical content.
		scale = biomeColumns == 2 ? twoColumnScale : fourColumnScale;
		int logicalWidth = ResponsiveUI.logicalWidth(width, scale);
		int logicalHeight = ResponsiveUI.logicalHeight(height, scale);
		int summaryWidth = font.width(collectionSummary().fullText()) + SUMMARY_EDGE_MARGIN * 2;
		int preferredWidth = biomeColumns == 2 ? 520 : Math.max(660, summaryWidth);
		panelWidth = Math.min(preferredWidth, logicalWidth - 8);
		panelHeight = Math.min(preferredHeight, logicalHeight - 8);
		panelLeft = (logicalWidth - panelWidth) / 2;
		panelTop = (logicalHeight - panelHeight) / 2;
	}

	private void addLookupField() {
		int x = panelLeft + 18;
		int y = panelTop + 34;
		if (lookupName != null) {
			lookupName.setX(x);
			lookupName.setY(y);
			lookupName.setWidth(Math.min(190, panelWidth - 130));
			addRenderableWidget(lookupName);
			return;
		}
		lookupName = new EditBox(font, x, y, Math.min(190, panelWidth - 130), 18,
			Component.literal("Minecraft username"));
		lookupName.setMaxLength(16);
		lookupName.setValue(lastLookupName);
		SettingsUiKit.showStart(lookupName);
		lookupName.setResponder(value -> {
			if (!lastLookupName.equals(value)) lookupRevision++;
			lastLookupName = value;
		});
		lookupName.setHint(LOOKUP_HINT_COMPONENT);
		lookupName.setTextColor(WHITE);
		lookupName.setTextColorUneditable(DIM);
		UIDraw.rainbowEditBox(lookupName, font);
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
		SpecialTheme.stars(graphics, panelLeft + 2, panelTop + 2,
			panelWidth - 4, panelHeight - 4, 1.375f);
		drawRainbowBorder(graphics);
		UIDraw.outline(graphics, panelLeft + 2, panelTop + 2, panelWidth - 4, panelHeight - 4, KEYLINE);
		hits.clear();
		numberHits.clear();
		drawTitle(graphics, mx, my);
		drawTabs(graphics, mx, my);
		switch (tab) {
			case COLLECTION -> drawCollection(graphics, mx, my);
			case LOOKUP -> drawLookup(graphics, mx, my);
		}
		if (tab == Tab.LOOKUP) drawStatus(graphics);
		if (tab == Tab.LOOKUP) drawRecentLookups(graphics, mx, my);
		if (editor != null && editingBounds != null) {
			graphics.fill(editingBounds.x(), editingBounds.y(),
				editingBounds.x() + editingBounds.width(), editingBounds.y() + editingBounds.height(),
				SURFACE);
		}
		updateLookupHint();
		UIDraw.updateRainbowCaret(lookupName, WHITE);
		UIDraw.updateRainbowCaret(editor, COLLECTION_GOLD);
		super.extractRenderState(graphics, mx, my, partialTick);
		if (editor != null && editingBounds != null) {
			themedOutline(graphics, editingBounds.x(), editingBounds.y(),
				editingBounds.width(), editingBounds.height(), GOLD);
		}
		if (tab == Tab.LOOKUP && SpecialTheme.rainbow() && lookupName != null) {
			SpecialTheme.border(graphics, lookupName.getX(), lookupName.getY(),
				lookupName.getWidth(), lookupName.getHeight(), 1);
		}
        graphics.pose().popMatrix();
	}

    @Override public void tick() {
        super.tick();
        checkCachedLocalCollection();
        if(lastLookup!=null) for(var recent:recentLookups.entries()) {
            if(recent.username().equalsIgnoreCase(lastLookup.username())&&recent.fetchedAt()>lastLookup.fetchedAt()) {
                lastLookup=recent;break;
            }
        }
    }

	private void updateLookupHint() {
		if (lookupName == null) return;
		lookupHintPhase = UIDraw.updateRainbowHint(lookupName, font, LOOKUP_HINT,
			LOOKUP_HINT_COMPONENT, lookupHintPhase);
	}

    private void drawTitle(GuiGraphicsExtractor graphics,int mouseX,int mouseY) {
        button(graphics,panelLeft+(panelWidth-80)/2,panelTop+panelHeight-25,80,18,"Close",false,mouseX,mouseY,this::onClose);
    }
    private void drawTabs(GuiGraphicsExtractor graphics,int mouseX,int mouseY) {
        if(navigation.isEmpty()) navigation=DashboardNavigation.layout(font,panelLeft,panelWidth,panelTop+5);
        DashboardNavigation.draw(graphics,font,navigation,tab==Tab.LOOKUP?DashboardNavigation.Tab.LOOKUP:DashboardNavigation.Tab.SPARKLINGS,mouseX,mouseY);
        for(var item:navigation)hits.add(new Hit(item.x(),item.y(),item.width(),21,item.tab().label,()->{
            if(item.tab()==(tab==Tab.LOOKUP?DashboardNavigation.Tab.LOOKUP:DashboardNavigation.Tab.SPARKLINGS))return;
            commitEditor(); item.tab().open();
        }));
    }


	private void drawCollection(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int top = panelTop + 35;
		int totalSpecies = Critters.total();
		CollectionSummary summary = collectionSummary();
		String prefix = summary.prefix();
		String feathers = summary.feathers();
		String suffix = summary.suffix();
		boolean compactSummary = font.width(summary.fullText()) > panelWidth - SUMMARY_EDGE_MARGIN * 2;
		String compactFirst = "Unique  " + SparklingStats.unique() + "/" + totalSpecies
			+ "   ✦   Sparklings  " + displayedSparklingTotal()
			+ "   ✦   Duplicates  " + duplicateText();
		String compactPrefix = "Rainbow Feathers  ";
        String compactSuffix = "   \u2726   Emerald Dyes  " + RunHistory.totalEmeraldDyes();
		String shownPrefix = compactSummary ? compactPrefix : prefix;
		String shownSuffix = compactSummary ? compactSuffix : suffix;
		int summaryWidth = font.width(shownPrefix + feathers + shownSuffix);
		int summaryX = panelLeft + (panelWidth - summaryWidth) / 2;
		int featherX = summaryX + font.width(shownPrefix);
		int featherY = compactSummary ? top + 12 : top;
		int featherWidth = Math.max(font.width("00000") + 8, font.width(feathers) + 4);
		int featherHitX = featherX - (featherWidth - font.width(feathers)) / 2;
		if (contains(featherHitX, featherY - 2, featherWidth, 12, mouseX, mouseY)) {
			graphics.fill(featherHitX, featherY - 2, featherHitX + featherWidth, featherY + 10, HOVER);
		}
        // Preserve each statistic's semantic color even for a completed collection.
        if (compactSummary) {
            int firstX=panelLeft+(panelWidth-font.width(compactFirst))/2;
            SpecialTheme.text(graphics,font,StatColours.collection(compactFirst,"   \u2726   "),firstX,top,LABEL);
        }
        SpecialTheme.text(graphics,font,StatColours.collection(shownPrefix+feathers+shownSuffix,"   \u2726   "),summaryX,featherY,LABEL);
		numberHits.add(new NumberHit(featherHitX, featherY - 2, featherWidth, 12,
			featherY, null, true));

		int barLeft = panelLeft + 24;
		int barRight = panelLeft + panelWidth - 24;
		int barY = top + (compactSummary ? 28 : 16);
        UIDraw.collectionProgress(graphics,barLeft,barY,barRight-barLeft,4,
            SparklingStats.unique(),totalSpecies);
		drawSpeciesColumns(graphics, barY + 12, mouseX, mouseY, null, true);
        int statsY = barY + 12 + (17 + MAX_BIOME_ROWS * LINE + 8) * (4 / biomeColumns) + 4;
        int since = dev.serko.safariutils.session.SparklingRunIntervals.sinceAny();
        int sinceNew = dev.serko.safariutils.session.SparklingRunIntervals.sinceNew();
        int half = (panelWidth - 32) / 2;
        centered(graphics, "Runs Since Unique", panelLeft + 16, half, statsY, StatColours.UNIQUE);
        centered(graphics, "Runs Since Sparkling", panelLeft + 16 + half, half, statsY, StatColours.PERSONAL_BEST);
        centered(graphics, sinceNew < 0 ? "Unknown" : Integer.toString(sinceNew), panelLeft + 16, half, statsY + 13, StatColours.value(StatColours.UNIQUE));
        centered(graphics, since < 0 ? "Unknown" : Integer.toString(since), panelLeft + 16 + half, half, statsY + 13, StatColours.value(StatColours.PERSONAL_BEST));

	}

	private void drawSpeciesColumns(GuiGraphicsExtractor graphics, int y, int mouseX, int mouseY,
			Set<String> remoteSpecies, boolean editable) {
		int columnWidth = Math.min(150, (panelWidth - 24) / biomeColumns);
		int left = panelLeft + (panelWidth - columnWidth * biomeColumns) / 2;
		int rowHeight = 17 + MAX_BIOME_ROWS * LINE + 8;
		for (int column = 0; column < BIOMES.size(); column++) {
			SafariBiome biome = BIOMES.get(column);
			int x = left + column % biomeColumns * columnWidth;
			int sectionY = y + column / biomeColumns * rowHeight;
			List<Critter> species = Critters.inBiome(biome);
			boolean complete = species.stream().allMatch(critter -> remoteSpecies == null
				? SparklingStats.count(critter) > 0 : remoteSpecies.contains(SparklingPlayerLookup.speciesId(critter.name())));
			drawBiomeTitle(graphics, biome, x + 8, columnWidth - 16, sectionY, complete);
			for (int row = 0; row < species.size(); row++) {
				Critter critter = species.get(row);
				int rowY = sectionY + 17 + row * LINE;
				int count = SparklingStats.count(critter);
				boolean remoteOwned = remoteSpecies != null
					&& remoteSpecies.contains(SparklingPlayerLookup.speciesId(critter.name()));
				int nameColour = remoteSpecies == null
					? count == 0 ? StatColours.MISSING : 0xFF000000 | critter.rarity().colour()
					: remoteOwned ? 0xFF000000 | critter.rarity().colour() : StatColours.MISSING;
				text(graphics, critter.name(), x + 8, rowY, nameColour);
				String value;
				int colour;
				if (remoteSpecies == null) {
					value = count == 0 ? "—" : String.valueOf(count);
					colour = count > 0 ? StatColours.value(nameColour) : StatColours.MISSING;
				} else {
					boolean owned = remoteSpecies.contains(SparklingPlayerLookup.speciesId(critter.name()));
					value = owned ? "✓" : "—";
					colour = owned ? StatColours.value(nameColour) : StatColours.MISSING;
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

    private void checkCachedLocalCollection() {
        SharedSparklingProviders.cachedLocalCollection().ifPresent(this::syncIfLocal);
    }

	private void drawLookup(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!SharedSparklingProviders.available()) {
            centered(graphics,"Player lookups are unavailable",panelLeft,panelWidth,panelTop+50,RED);return;
        }
		int y = panelTop + 34;
		drawLookupButton(graphics, panelLeft + 214, y, mouseX, mouseY);
		if (lastLookup == null) {
			return;
		}
		int detailsBottom = drawLookupNameAndTickets(graphics, y + 29);
		int unique = lastLookup.species().size();
		String duplicates = lastLookup.duplicates() < 0 ? "—" : String.valueOf(lastLookup.duplicates());
		String total = lastLookup.duplicates() < 0 ? "—" : String.valueOf(unique + lastLookup.duplicates());
		String summary = "Unique Sparklings  " + unique + "/" + Critters.total()
			+ "   ✦   Sparklings  " + total + "   ✦   Duplicates  " + duplicates;
		int summaryY = detailsBottom + 4;
		String shownSummary=trimToWidth(summary,panelWidth-24);
        SpecialTheme.text(graphics,font,StatColours.collection(shownSummary,"   \u2726   "),
            panelLeft+(panelWidth-font.width(shownSummary))/2,summaryY,LABEL);
		drawSpeciesColumns(graphics, summaryY + 18, mouseX, mouseY, lastLookup.species(), false);
	}

	private int drawLookupNameAndTickets(GuiGraphicsExtractor graphics, int y) {
		int columnWidth = Math.min(150, (panelWidth - 24) / biomeColumns);
		int x = biomeColumns == 2 ? panelLeft + 12
			: panelLeft + (panelWidth - columnWidth * biomeColumns) / 2 + 8;
		String displayedName = CanonicalPlayerNames.display(lastLookup.username());
		int nameX = biomeColumns == 2
			? panelLeft + (panelWidth - font.width(displayedName)) / 2 : x;
		PlayerNameStyle.drawName(graphics, font, displayedName, nameX, y);
		String[] keys = LookupPresentation.TICKET_NAMES;
		long ticketTotal = 0L;
		for (String key : keys) ticketTotal += lastLookup.tickets().getOrDefault(key, 0L);
		if (biomeColumns == 2) {
			centered(graphics, "Total  " + ticketTotal, panelLeft, panelWidth, y + 12, COLLECTION_GOLD);
			for (int row = 0; row < 2; row++) {
				String leftTicket = LookupPresentation.ticket(lastLookup,row*2).getString();
				String rightTicket = LookupPresentation.ticket(lastLookup,row*2+1).getString();
				int totalWidth = font.width(leftTicket) + font.width("   │   ") + font.width(rightTicket);
				int cursor = panelLeft + (panelWidth - totalWidth) / 2;
				SpecialTheme.text(graphics,font,LookupPresentation.ticket(lastLookup,row*2),cursor,y+23+row*11,WHITE);
				cursor += font.width(leftTicket);
				text(graphics, "   │   ", cursor, y + 23 + row * 11, DIM);
				cursor += font.width("   │   ");
				SpecialTheme.text(graphics,font,LookupPresentation.ticket(lastLookup,row*2+1),cursor,y+23+row*11,WHITE);
			}
            return y + 46;
		}
		int cursor = x + font.width(displayedName) + font.width("  ");
		text(graphics, "(  ", cursor, y, WHITE);
		cursor += font.width("(  ");
		String total = "Total  " + ticketTotal;
		text(graphics, total, cursor, y, COLLECTION_GOLD);
		cursor += font.width(total);
		for (int i = 0; i < keys.length; i++) {
			text(graphics, "  │   ", cursor, y, WHITE);
			cursor += font.width("  │   ");
			String value = LookupPresentation.ticket(lastLookup,i).getString();
			SpecialTheme.text(graphics,font,LookupPresentation.ticket(lastLookup,i),cursor,y,WHITE);
			cursor += font.width(value);
		}
		text(graphics, "  )", cursor, y, WHITE);
        return y + 13;
	}

	private void drawLookupButton(GuiGraphicsExtractor graphics, int x, int y,
			int mouseX, int mouseY) {
		LookupState state = lookupState();
		boolean hovered = contains(x, y, 72, 18, mouseX, mouseY);
		graphics.fill(x, y, x + 72, y + 18, hovered ? HOVER : SURFACE);
		themedOutline(graphics, x, y, 72, 18, state.colour());
		centered(graphics, state.label(), x, 72, centeredTextY(y, 18), state.colour());
		if (state.enabled()) {
			hits.add(new Hit(x, y, 72, 18, state.label(), () -> lookupPlayer()));
		}
	}

	private LookupState lookupState() {
		String entered = lookupName == null ? lastLookupName : lookupName.getValue().trim();
		boolean blank = entered.isEmpty();
		boolean displayed = lastLookup != null;
		long now = System.currentTimeMillis();
		boolean freshCached = LookupHistory.fresh(lastLookup, now, LOOKUP_CACHE_MILLIS)
			|| recentLookups.recentlyChecked(lastLookup, MonotonicTime.nowMillis(), LOOKUP_CACHE_MILLIS);
		long cooldown = Math.max(0L, SharedSparklingProviders.lookupAvailableAt() - now);
		boolean available = SharedSparklingProviders.available();
		boolean refreshable = blank && displayed && !freshCached && cooldown == 0L && available;
		boolean enabled = !lookupLoading && (refreshable
			|| !blank && available);
		String label;
		if (lookupLoading) label = "Loading…";
		else if (blank && displayed) label = refreshable ? "Refresh" : "Loaded";
		else label = "Lookup";
		int colour = enabled ? refreshable ? COLLECTION_GOLD : AQUA : DIM;
		return new LookupState(enabled, label, colour);
	}

	/** Starts a lookup only when the same state shown by the button allows it. */
	private boolean lookupPlayer() {
		if (lookupName == null || !lookupState().enabled()) return false;
		String entered = lookupName.getValue().trim();
		String username = entered.isEmpty() && lastLookup != null
			? lastLookup.username() : entered;
		if (!username.matches("[A-Za-z0-9_]{1,16}")) {
			setStatus("Enter a valid Minecraft username", RED);
			return false;
		}
		lookupLoading = true;
		long revision = ++lookupRevision;
		setStatus("Looking up " + username + "…", DIM);
		PlayerLookupCoordinator.lookup(username)
			.whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
				lookupLoading = false;
				SparklingPlayerLookup accepted = error == null ? result : null;
				if (revision != lookupRevision || ClientCompat.screen() != this) {
					DebugLog.line("PLAYER_LOOKUP", "superseded presentation skipped success=" + (error == null));
					return;
				}
				if (error != null) setStatus(ClientMessages.playerLookupFailure(error), RED);
				else {
					lastLookup = accepted;
					lastLookupName = "";
					if (lookupName != null) {
						lookupName.setValue("");
						SettingsUiKit.blurToStart(lookupName);
					}
					setFocused(null);
					setStatus("Loaded " + result.username() + "'s Profile", GREEN);
					syncIfLocal(accepted);
				}
			}));
		return true;
	}

	private record LookupState(boolean enabled, String label, int colour) { }

    private void syncIfLocal(SparklingPlayerLookup result) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && result.username().equalsIgnoreCase(client.player.getGameProfile().name())
                && SparklingStats.syncApiCollection(result.species(), result.duplicates(), result.fetchedAt())) {
            updatePanelBounds();
        }
    }

	private static SparklingPlayerLookup rememberLookup(SparklingPlayerLookup result) {
		return rememberLookup(result, false);
	}

	static SparklingPlayerLookup rememberLookup(SparklingPlayerLookup result, boolean manual) {
        SparklingPlayerLookup accepted = PlayerLookupCoordinator.remember(result, manual);
		if (lastLookup != null && lastLookup.username().equalsIgnoreCase(accepted.username())
				&& accepted.fetchedAt() >= lastLookup.fetchedAt()) lastLookup = accepted;
		return accepted;
	}

	@Override
	public void removed() {
		lookupRevision++;
		super.removed();
	}

	/** Adds automatic party results to the same five-minute cache as manual lookups. */
	public static void cacheRecentLookups(List<SparklingPlayerLookup> results) {
		for (SparklingPlayerLookup result : results) rememberLookup(result);
	}

	private void drawRecentLookups(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (recentLookups.entries().isEmpty()) return;
		int width = 142;
		int height = 18;
		int x = panelLeft + panelWidth - width - 12;
		int y = panelTop + 34;
		String selected = lastLookup == null ? "Recent Lookups"
			: "Recent: " + lastLookup.username();
		boolean hovered = contains(x, y, width, height, mouseX, mouseY);
		graphics.fill(x, y, x + width, y + height, hovered ? HOVER : SURFACE);
		themedOutline(graphics, x, y, width, height, recentLookupsOpen ? AQUA : BORDER);
		String buttonText = trimToWidth(selected, width - 25) + (recentLookupsOpen ? "  ▴" : "  ▾");
		if (lastLookup != null && (SpecialTheme.rainbow()
				|| SharedSparklingProviders.specialName(lastLookup.username()))) {
			centeredRainbowName(graphics, "Recent: ", lastLookup.username(),
				recentLookupsOpen ? "  ▴" : "  ▾", x, width, centeredTextY(y, height),
				recentLookupsOpen ? AQUA : LABEL);
		} else if (lastLookup != null) {
			centeredColouredName(graphics, "Recent: ", lastLookup.username(),
				recentLookupsOpen ? "  ▴" : "  ▾", x, width, centeredTextY(y, height),
				recentLookupsOpen ? AQUA : LABEL, PlayerNameStyle.colour(lastLookup.username()));
		} else {
			centered(graphics, buttonText, x, width, centeredTextY(y, height),
				recentLookupsOpen ? AQUA : LABEL);
		}
		hits.add(new Hit(x, y, width, height, "Recent Lookups",
			() -> recentLookupsOpen = !recentLookupsOpen));
		if (!recentLookupsOpen) return;
		for (int index = 0; index < recentLookups.entries().size(); index++) {
			SparklingPlayerLookup saved = recentLookups.entries().get(index);
			int itemY = y + height * (index + 1);
			boolean itemHovered = contains(x, itemY, width, height, mouseX, mouseY);
			graphics.fill(x, itemY, x + width, itemY + height, itemHovered ? HOVER : 0xFF141B25);
			themedOutline(graphics, x, itemY, width, height, BORDER);
			if (SharedSparklingProviders.specialName(saved.username())) {
				String displayedName = CanonicalPlayerNames.display(saved.username());
				rainbowText(graphics, displayedName,
					x + (width - font.width(displayedName)) / 2,
					centeredTextY(itemY, height));
			} else {
				String displayedName = CanonicalPlayerNames.display(saved.username());
				PlayerNameStyle.drawName(graphics, font, displayedName,
					x + (width - font.width(displayedName)) / 2, centeredTextY(itemY, height));
			}
			hits.add(new Hit(x, itemY, width, height, "Recent Player", () -> selectRecent(saved)));
		}
	}

	private void selectRecent(SparklingPlayerLookup saved) {
		lookupRevision++;
		lastLookup = saved;
		lastLookupName = "";
		if (lookupName != null) {
			lookupName.setValue("");
			SettingsUiKit.blurToStart(lookupName);
		}
		setFocused(null);
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
		graphics.fill(panelLeft + 8, panelTop + panelHeight - 64,
			panelLeft + panelWidth - 8, panelTop + panelHeight - 37, SURFACE);
		List<String> lines = wrap(status, panelWidth - 28, 2);
		for (int i = 0; i < lines.size(); i++) {
			text(graphics, lines.get(i), panelLeft + 14,
				panelTop + panelHeight - 59 + i * 11, statusColour);
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
		editor.setTextColor(COLLECTION_GOLD);
		UIDraw.rainbowEditBox(editor, font);
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
						updatePanelBounds();

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

	private static CollectionSummary collectionSummary() {
		String prefix = "Unique Sparklings  " + SparklingStats.unique() + "/" + Critters.total()
			+ "   ✦   Sparklings  " + displayedSparklingTotal()
			+ "   ✦   Duplicates  " + duplicateText()
			+ "   ✦   Rainbow Feathers  ";
		String feathers = String.valueOf(SparklingStats.rainbowFeathers());
		String suffix = "   \u2726   Emerald Dyes  " + RunHistory.totalEmeraldDyes();
		return new CollectionSummary(prefix, feathers, suffix);
	}

	private static String duplicateText() {
		int setDuplicates = SparklingStats.duplicates();
		return SparklingStats.hasImportedDuplicates()
			&& SparklingStats.importedDuplicates() != setDuplicates
			? SparklingStats.importedDuplicates() + " (" + setDuplicates + ")"
			: String.valueOf(setDuplicates);
	}

	private static int displayedSparklingTotal() {
		return SparklingStats.hasImportedDuplicates()
			? SparklingStats.unique() + SparklingStats.importedDuplicates() : SparklingStats.total();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		double mouseX = event.x() / scale;
		double mouseY = event.y() / scale;
		if (lookupName != null && lookupName.isMouseOver(mouseX, mouseY)) {
			setFocused(lookupName);
			MouseButtonEvent scaledEvent = scale == 1f ? event
				: new MouseButtonEvent(mouseX, mouseY, event.buttonInfo());
			SettingsUiKit.focusAt(lookupName, scaledEvent, doubled);
			return true;
		}
		if (lookupName != null && lookupName.isFocused()
				&& !lookupName.isMouseOver(mouseX, mouseY)) {
			SettingsUiKit.blurToStart(lookupName);
			setFocused(null);
		}
		MouseButtonEvent scaledEvent = scale == 1f ? event
			: new MouseButtonEvent(mouseX, mouseY, event.buttonInfo());
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
		if (editor != null && editingBounds != null
				&& editingBounds.contains(mouseX, mouseY)) {
			if (editor.isMouseOver(mouseX, mouseY)) super.mouseClicked(scaledEvent, doubled);
			setFocused(editor);
			editor.setFocused(true);
			if (mouseX <= editor.getX()) editor.setCursorPosition(0);
			else if (mouseX >= editor.getX() + editor.getWidth()) {
				editor.setCursorPosition(editor.getValue().length());
			}
			return true;
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
		if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_TAB) return true;
		if (editor != null && (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER)) {
			commitEditor();
			return true;
		}
		if (tab == Tab.LOOKUP && lookupName != null && lookupName.isFocused()
				&& (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER)) {
			if (lookupPlayer()) {
				SettingsUiKit.blurToStart(lookupName);
				setFocused(null);
			}
			return true;
		}
		return super.keyPressed(event);
	}

	private void button(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
			String label, boolean selected, int mouseX, int mouseY, Runnable action) {
		boolean hovered = contains(x, y, width, height, mouseX, mouseY);
		graphics.fill(x, y, x + width, y + height, hovered ? HOVER : SURFACE);
		themedOutline(graphics, x, y, width, height, selected ? GOLD : BORDER);
		centered(graphics, label, x, width, centeredTextY(y, height),
			selected ? GOLD : hovered ? WHITE : LABEL);
		hits.add(new Hit(x, y, width, height, label, action));
	}

	private void drawRainbowBorder(GuiGraphicsExtractor graphics) {
		SpecialTheme.border(graphics, panelLeft, panelTop, panelWidth, panelHeight);
	}

	private void rainbowText(GuiGraphicsExtractor graphics, String value, int x, int y) {
		if (SpecialTheme.rainbow()) SpecialTheme.rainbowText(graphics, font, value, x, y);
		else UIDraw.rainbowText(graphics, font, value, x, y, 0.45f);
	}

	private void drawBiomeTitle(GuiGraphicsExtractor graphics, SafariBiome biome,
			int x, int width, int y, boolean complete) {
		String label = "✦ " + biome.displayName() + " ✦";
		int textX = x + (width - font.width(label)) / 2 - 4;
		if (complete) rainbowText(graphics, label, textX, y);
		else text(graphics, label, textX, y, 0xFF000000 | biome.colour());
	}

	private void drawRainbowLine(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		SpecialTheme.bar(graphics, x, y, width, height);
	}

	private void themedOutline(GuiGraphicsExtractor graphics, int x, int y,
			int width, int height, int fallback) {
		if (SpecialTheme.rainbow()) SpecialTheme.border(graphics, x, y, width, height, 1);
		else UIDraw.outline(graphics, x, y, width, height, fallback);
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
		String displayedName = CanonicalPlayerNames.display(name);
		int cursor = x + (width - font.width(prefix + displayedName + suffix)) / 2;
		text(graphics, prefix, cursor, y, colour);
		cursor += font.width(prefix);
		if (SpecialTheme.rainbow()) {
			SpecialTheme.rainbowText(graphics, font, displayedName, cursor, y);
		} else UIDraw.rainbowText(graphics, font, displayedName, cursor, y, 0.45f);
		cursor += font.width(displayedName);
		text(graphics, suffix, cursor, y, colour);
	}

	private void centeredColouredName(GuiGraphicsExtractor graphics, String prefix, String name,
			String suffix, int x, int width, int y, int colour, int nameColour) {
		String displayedName = CanonicalPlayerNames.display(name);
		int cursor = x + (width - font.width(prefix + displayedName + suffix)) / 2;
		text(graphics, prefix, cursor, y, colour);
		cursor += font.width(prefix);
		graphics.text(font, Component.literal(displayedName), cursor, y, nameColour);
		cursor += font.width(displayedName);
		text(graphics, suffix, cursor, y, colour);
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
        if (tab != Tab.LOOKUP) return;
		status = value.stripTrailing();
		while (status.endsWith(".")) status = status.substring(0, status.length() - 1);
		statusColour = colour;
		statusUntil = colour == DIM ? 0L : System.currentTimeMillis() + 5_000L;
	}

	private static String defaultStatus(Tab target) {
		return switch (target) {
			case COLLECTION -> "";
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

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
