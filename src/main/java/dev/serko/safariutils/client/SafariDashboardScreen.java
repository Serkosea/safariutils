package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.session.RunHistory;
import dev.serko.safariutils.session.SparklingStats;
import dev.serko.safariutils.session.RunRecord;
import dev.serko.safariutils.session.SafariSession;
import dev.serko.safariutils.session.SessionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Full-screen view of run, history, statistics, and Sparkling data.
 *
 * <p>One column per biome listing every species, colour-coded by who has it: green if
 * you caught it, aqua if only a partymate did, grey if nobody has. That makes the two
 * questions the run actually turns on — what is left, and who is covering it —
 * answerable at a glance instead of by reading chat.
 */
public final class SafariDashboardScreen extends Screen {

	/** Never narrower than this, however small the window is. */
	private static final int MIN_COLUMN_WIDTH = 70;
	/** Blank space between the longest species name and its count. */
	private static final int COLUMN_GAP = 8;
	/** Blank space between a column's count and the next column. */
	private static final int COLUMN_PAD = 8;
	private static final int LINE_HEIGHT = 11;
	private static final int PANEL_PADDING = 10;
	/** Space reserved for the navigation rail inside the panel. */
	private static final int NAV_CONTENT_OFFSET = 25;
	private static final int NAV_ITEM_WIDTH = 66;
	/** An odd height centers Minecraft's nine-pixel font without a half-pixel bias. */
	private static final int NAV_ITEM_HEIGHT = 21;
	private static final int NAV_TEXT_HEIGHT = 9;
	private static final int NAV_HIGHLIGHT_INSET = 3;
	private static final int ACTION_HEIGHT = 21;
	/** Fixed history viewport; additional saved runs remain available by scrolling. */
	private static final int HISTORY_ROWS = 15;

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d @ h:mm a");
	private static final String STAT_SEPARATOR = "  │  ";
	private static final SafariBiome[] BIOME_ORDER = {
		SafariBiome.CAVERN, SafariBiome.ICY, SafariBiome.HAUNTED, SafariBiome.FOREST
	};

	private static final int CAUGHT_BY_YOU = 0xFF55FF55;
	private static final int CAUGHT_BY_PARTY = 0xFF55FFFF;
	private static final int UNCAUGHT = StatColours.MISSING;
	private static final int HEADING = 0xFFFFAA00;
	/** Gold, as coins are everywhere else in SkyBlock. */
	private static final int COINS = StatColours.COINS;
	private static final int LABEL = StatColours.INFO;
	private static final int DIM = StatColours.SECONDARY;
	private static final int WHITE = 0xFFFFFFFF;

	private static final int PANEL_BACKGROUND = 0xE00B1017;
	private static final int PANEL_SURFACE = 0xE0141B25;
	private static final int PANEL_SURFACE_HOVER = 0xEF1B2532;
	private static final int PANEL_BORDER = 0x38FFFFFF;
	private static final int PANEL_KEYLINE = 0x24FFFFFF;
	private static final int BAR_TRACK = 0x5A25303D;

    private SafariSession detailSession;
    private int liveDetailTicks;

	/** Which view is showing. Static so it survives closing and reopening the screen. */
	private static Tab tab = Tab.HISTORY;
    private static final int BEST_ROW_HEIGHT = 13;
    private static final int BEST_MOB_GAP = 8;
    private static final int BEST_MOB_SECTION_HEIGHT = BEST_ROW_HEIGHT + BEST_MOB_GAP;
    private static final int BEST_CARD_HEIGHT = 22 + 4 * BEST_ROW_HEIGHT + 6;
    private RunRecord selectedRun;
    private boolean detailBests;
    private Map<String,Map<String,Integer>> detailPlayerCounts;
    private Tab runDetailBack = Tab.PERSONAL_BESTS;
    private final List<BestHit> bestHits = new ArrayList<>();
    private record BestHit(int x, int y, int width, int height, RunRecord run) { }


	/** Main navigation; run details are shared by History and Personal Bests. */
	private enum Tab {
		/** Filterable saved runs. */
		HISTORY("History"),
		/** Totals per species across every saved run. */
		STATS("Stats"),
        PERSONAL_BESTS("Personal Bests");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private enum HistorySort {
		NEWEST("Newest"),
		OLDEST("Oldest"),
		LONGEST("Longest"),
		SHORTEST("Shortest"),
		MOST_CATCHES("Most Catches"),
		MOST_SPARKLINGS("Most Sparklings");

		final String label;

		HistorySort(String label) {
			this.label = label;
		}
	}

	private enum ControlStyle {TAB, BUTTON, SPARKLING_TOGGLE}

	private record Control(String label, int x, int y, int width, int height,
			ControlStyle style, boolean selected, boolean sparkling, Runnable action) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private final List<Control> controls = new ArrayList<>();
	private boolean sparklingRunsOnly;
	private HistorySort historySort = HistorySort.NEWEST;
	private int historyScroll;
	private List<RunRecord> cachedHistory;
	private long cachedHistoryRevision = Long.MIN_VALUE;
	private List<HistoryDisplayRow> cachedHistoryDisplayRows;
	private List<RunRecord> cachedHistoryDisplaySource;
	private final Map<RunRecord, Integer> cachedRunNumbers = new java.util.IdentityHashMap<>();
	private long cachedRunNumberRevision = Long.MIN_VALUE;
	private final Map<RunRecord, HistoryRowView> cachedHistoryRows = new java.util.IdentityHashMap<>();
	private long cachedHistoryPriceRevision = Long.MIN_VALUE;
	private boolean cachedHistoryValueMode;
	private List<RunRecord> cachedHistoryWidthsSource;
	private long cachedHistoryWidthsPriceRevision = Long.MIN_VALUE;
	private boolean cachedHistoryWidthsValueMode;
	private int cachedHistoryWidthsCritterTotal = -1;
	private HistoryColumnWidths cachedHistoryWidths;
	private List<RunRecord> cachedHistorySummarySource;
	private long cachedHistorySummaryPriceRevision = Long.MIN_VALUE;
	private String cachedHistorySummary;
	private final Map<RunRecord, String> cachedSparklingSummaries = new java.util.IdentityHashMap<>();
	private long cachedValueTotalsHistoryRevision = Long.MIN_VALUE;
	private long cachedValueTotalsPriceRevision = Long.MIN_VALUE;
	private String cachedValueTotals = "";

	/** Shrinks from the preferred width when the window cannot fit four columns. */
	private int columnWidth;
	private int panelLeft;
	private int panelTop;
	private int panelWidth;
	private int panelHeight;
	private float responsiveScale = 1f;
	private int layoutWidth;
	private int layoutHeight;

	public SafariDashboardScreen() {
		super(Component.literal("Critter Safari"));
		tab = Tab.HISTORY;

		// History controls reset whenever the stats screen is reopened.
		this.sparklingRunsOnly = false;
		this.historySort = HistorySort.NEWEST;
		this.historyScroll = 0;
	}

	/** Whether the money columns and lines are wanted at all. */
	private static boolean showValue() {
		return ConfigManager.get().profit.enabled;
	}

	@Override
	protected void init() {
		controls.clear();
		int preferredColumnsWidth = measureColumnWidth() * BIOME_ORDER.length + PANEL_PADDING * 2;
		int preferredWidth = Math.max(420, Math.max(preferredColumnsWidth,
			measureTabTextWidth() + PANEL_PADDING * 2)) + 8;
		int valueRows = showValue() ? 1 : 0;
		int preferredPanelHeight = navigationOffset() + (selectedRun != null ? runDetailHeight() : switch (tab) {
			case HISTORY -> 46 + (HISTORY_ROWS + 2) * LINE_HEIGHT + 46;
			case STATS -> 46 + (4 + valueRows + 10) * LINE_HEIGHT;
            case PERSONAL_BESTS -> bestPanelHeight();
		});
		// Use native-size adaptive columns whenever they fit. Dense history tables or
		// unusually short canvases alone fall back to proportional scaling.
		responsiveScale = Math.min(1f, Math.min(width / (float) preferredWidth,
			height / (float) (preferredPanelHeight + 20)));
		layoutWidth = ResponsiveUI.logicalWidth(width, responsiveScale);
		layoutHeight = ResponsiveUI.logicalHeight(height, responsiveScale);
		int columns = BIOME_ORDER.length;
		int available = layoutWidth - PANEL_PADDING * 2 - 8;
		// Measured from the longest species name rather than guessed at: "Mantis Shrimp"
		// and "Shuddersquid" ran into their own counts at a fixed width.
		columnWidth = Math.max(MIN_COLUMN_WIDTH, Math.min(measureColumnWidth(), available / columns));
		int columnsWidth = columnWidth * columns + PANEL_PADDING * 2;
		int textWidth = measureTabTextWidth() + PANEL_PADDING * 2;
		panelWidth = Math.min(layoutWidth - 8, Math.max(columnsWidth, textWidth));
		// Price totals add one row to the header and Stats summary.
		int statsValueRows = valueRows;
		panelHeight = navigationOffset() + (selectedRun != null ? runDetailHeight() : switch (tab) {
			// Header block, the longest biome column (Haunted has 10), then the player table.
			// A summary block, then one line per run shown.
			case HISTORY -> 46 + (HISTORY_ROWS + 2) * LINE_HEIGHT + 46;
			// A summary block, then the species columns.
			case STATS -> 46 + (4 + statsValueRows + 10) * LINE_HEIGHT;
            case PERSONAL_BESTS -> bestPanelHeight();
		});
		panelLeft = Math.max(4, (layoutWidth - panelWidth) / 2);
		panelTop = Math.max(10, (layoutHeight - panelHeight) / 2);

		if (selectedRun == null) addTabButtons();

		int buttonY = panelTop + panelHeight - 30;
		if (selectedRun != null || tab == Tab.PERSONAL_BESTS) addBestControls(buttonY);
        else if (tab == Tab.HISTORY) addHistoryControls(buttonY);
		else {
			int buttonWidth = 80;
			int buttonX = panelLeft + (panelWidth - buttonWidth) / 2;

			controls.add(new Control("Close", buttonX, buttonY, buttonWidth, ACTION_HEIGHT,
				ControlStyle.BUTTON, false, false, this::onClose));
		}
	}

    private int navigationOffset() { return selectedRun == null ? NAV_CONTENT_OFFSET : 0; }

    private void clearRunDetail() { selectedRun=null; detailSession=null; liveDetailTicks=0; detailPlayerCounts=null; }
    private void openRunDetail(RunRecord record, SafariSession current) {
        runDetailBack=tab; selectedRun=record; detailSession=current; liveDetailTicks=0;
        detailBests=tab==Tab.PERSONAL_BESTS; detailPlayerCounts=record.displayPlayerBiomeUniques();
        rebuildWidgets();
    }
    @Override public void tick() {
        super.tick();
        if(selectedRun==null || detailSession==null || ++liveDetailTicks<20) return;
        liveDetailTicks=0;
        int previousHeight=runDetailHeight();
        selectedRun=RunRecord.of(detailSession);
        detailPlayerCounts=selectedRun.displayPlayerBiomeUniques();
        if(SessionManager.current()==detailSession) selectedRun.ended=detailSession.startedAtMillis()+detailSession.elapsedMillis(System.currentTimeMillis());
        if(previousHeight!=runDetailHeight()) rebuildWidgets();
    }
    private void detailText(GuiGraphicsExtractor graphics,Font font,String line,int x,int y,int colour) {
        text(graphics,font,Component.literal(font.plainSubstrByWidth(line,panelWidth-PANEL_PADDING*2)),x,y,colour);
    }
    private void detailSummary(GuiGraphicsExtractor graphics, Font font, String line, int x, int y) {
        StatColours.summaryLine(graphics,font,line,STAT_SEPARATOR,x,y);
    }

    private static String runCatchSummary(RunRecord run, boolean fullLabels) {
        int own=run.own.values().stream().mapToInt(Integer::intValue).sum();
        int attempts=run.attempts.values().stream().mapToInt(Integer::intValue).sum();
        return "Uniques "+run.partyUnique()+"/"+Critters.total()+STAT_SEPARATOR
            +(fullLabels?"Your Uniques ":"Yours ")+run.own.size()+STAT_SEPARATOR
            +"Catches "+run.partyTotal()+" ("+own+" Yours)"+STAT_SEPARATOR+"Catch Attempts "+attempts;
    }

    private static String runCritterCounts(RunRecord run, Critter critter) {
        int caught=run.caught(critter), attempts=run.attempts.getOrDefault(critter.name(),0);
        String count=caught>0?String.valueOf(caught):attempts>0?attempts+"t":"-";
        return run.hasShardData()?count+" ("+run.shards(critter)+"x)":count;
    }

    private static String runRewardSummary(RunRecord run, boolean fullLabels) {
        return run.totalShards+" Shards ("+run.ownShards+" Yours)"+STAT_SEPARATOR
            +run.safariEssence+" Essence"+STAT_SEPARATOR
            +(fullLabels?"Rainbow Feathers ":"Feathers ")+run.rainbowFeathers
            +STAT_SEPARATOR+"Emerald Dyes "+run.emeraldDyes();
    }

    private void drawRunDetails(GuiGraphicsExtractor graphics,Font font,int y) {
        RunRecord run=selectedRun;
        y+=ACTION_HEIGHT+10;
        int left=panelLeft+PANEL_PADDING, right=panelLeft+panelWidth-PANEL_PADDING;
        boolean current=detailSession!=null&&detailSession==SessionManager.current();
        String title=current?"Current Run":runNumber(run)>0?"Run "+runNumber(run):"Last Run";
        var heading=Component.literal(title).withColor(tabBorderColour())
            .append(Component.literal(STAT_SEPARATOR).withColor(WHITE))
            .append(Component.literal(formatWhen(run.started)).withColor(StatColours.DATE))
            .append(Component.literal(STAT_SEPARATOR).withColor(WHITE))
            .append(Component.literal(ProgressHud.formatDuration(run.durationMillis())).withColor(StatColours.TIME));
        if(run.interrupted) heading.append(Component.literal(STAT_SEPARATOR+"Interrupted").withColor(DIM));
        text(graphics,font,heading,left,y,WHITE);y+=13;
        if(detailBests) { drawRunCompletions(graphics,font,run,left,right,y); return; }
        drawRunPlayers(graphics,font,run,left,y);y+=21;
        String catches=runCatchSummary(run,true);
        if(font.width(catches)>right-left) catches=runCatchSummary(run,false);
        detailSummary(graphics,font,catches,left,y);y+=13;
        String rewards=runRewardSummary(run,true);
        if(font.width(rewards)>right-left) rewards=runRewardSummary(run,false);
        detailSummary(graphics,font,rewards,left,y);y+=13;
        if(showValue()) {
            long value=detailSession!=null?BazaarPrices.valueOf(detailSession):BazaarPrices.valueOf(run);
            String valueText=!BazaarPrices.known()?"Market prices not loaded":BazaarPrices.format(value)+" coins"
                +(BazaarPrices.rareDropPricingIncomplete(run.rareDrops)?" + unpriced drops":"");
            detailText(graphics,font,"Profit: "+valueText,left,y,COINS);y+=13;
        }
        y=drawRunSparklings(graphics,font,run,left,y);
        y=runDetailDivider(graphics,font,left,right,y-13);
        text(graphics,font,Component.literal("You Caught"),left,y,CAUGHT_BY_YOU);
        text(graphics,font,Component.literal("Teammate Only"),left+font.width("You Caught")+18,y,CAUGHT_BY_PARTY);
        text(graphics,font,Component.literal("Uncaught"),left+font.width("You CaughtTeammate Only")+36,y,UNCAUGHT);
        String countsLegend=run.hasShardData()?"Catches (Shards)":"Catches (shards not saved)";
        text(graphics,font,Component.literal(countsLegend),right-font.width(countsLegend),y,StatColours.UNIQUE);
        y+=18;
        int gridTop=y, gridBottom=y;
        for(int column=0;column<BIOME_ORDER.length;column++) {
            SafariBiome biome=BIOME_ORDER[column];int x=left+column*columnWidth,rowY=gridTop;
            int uniques=(int)Critters.inBiome(biome).stream().filter(c -> run.caught(c)>0).count();
            text(graphics,font,Component.literal(biome.displayName()+" ("+uniques+"/"+Critters.totalIn(biome)+")"),x,rowY,0xFF000000|biome.colour());rowY+=13;
            for(Critter critter:Critters.inBiome(biome)) {
                int caught=run.caught(critter), yours=run.own.getOrDefault(critter.name(),0);
                int colour=yours>0?CAUGHT_BY_YOU:caught>0?CAUGHT_BY_PARTY:UNCAUGHT;
                text(graphics,font,Component.literal(critter.name()),x,rowY,caught == 0 ? UNCAUGHT : 0xFF000000|critter.rarity().colour());
                String count=runCritterCounts(run,critter);
                text(graphics,font,Component.literal(count),x+columnWidth-COLUMN_PAD-font.width(count),rowY,colour);rowY+=LINE_HEIGHT;
            }
            gridBottom=Math.max(gridBottom,rowY);
        }
        y=runDetailDivider(graphics,font,left,right,gridBottom-LINE_HEIGHT);
        int nameWidth=Math.max(115,(right-left)/3), cell=(right-left-nameWidth)/4;
        text(graphics,font,Component.literal("Uniques Per Player"),left,y,StatColours.UNIQUE);
        for(int i=0;i<4;i++) centeredCell(graphics,font,BIOME_ORDER[i].displayName(),left+nameWidth+i*cell,cell,y,0xFF000000|BIOME_ORDER[i].colour());
        y+=13;
        if(detailPlayerCounts==null || detailPlayerCounts.isEmpty()) {
            detailText(graphics,font,detailPlayerCounts==null?"Other Players Not Saved":"No catches yet.",left,y,DIM);y+=LINE_HEIGHT;
        } else for(var player:detailPlayerCounts.entrySet()) {
            PlayerNameStyle.drawName(graphics,font,CanonicalPlayerNames.display(player.getKey()),left,y);
            for(int i=0;i<4;i++) centeredCell(graphics,font,String.valueOf(player.getValue().getOrDefault(BIOME_ORDER[i].name(),0)),left+nameWidth+i*cell,cell,y,StatColours.value(0xFF000000|BIOME_ORDER[i].colour()));
            y+=LINE_HEIGHT;
        }
        if(run.missingIndividualTeammateTotals())
            detailText(graphics,font,"Other Players Not Saved",left,y,DIM);
    }

    private void drawRunCompletions(GuiGraphicsExtractor graphics, Font font, RunRecord run, int left, int right, int y) {
        y=runDetailDivider(graphics,font,left,right,y-13);
        text(graphics,font,Component.literal("Completion Times"),left,y,StatColours.PERSONAL_BEST);y+=13;
        boolean any=false;
        if(run.personalBestSplits!=null) for(var split:run.personalBestSplits) {
            if(!dev.serko.safariutils.session.PersonalBestTracker.valid(split))continue;
            any=true;
            boolean eligible=dev.serko.safariutils.session.PersonalBestTracker.eligible(split);
            var biome=dev.serko.safariutils.session.PersonalBestTracker.biome(split.target());
            int titleColour=biome==null?StatColours.PERSONAL_BEST:0xFF000000|biome.colour();
            String title=dev.serko.safariutils.session.PersonalBestTracker.title(split.target())+(dev.serko.safariutils.session.PersonalBestTracker.isMob(split.target())?"":" ("+split.group().label+")");
            text(graphics,font,Component.literal(title),left,y,titleColour);
            String time=dev.serko.safariutils.session.PersonalBestTracker.time(split.elapsedMillis());
            text(graphics,font,Component.literal(time),right-font.width(time),y,StatColours.TIME);
            y+=14;
            if(split.previousBestMillis()>=0) {
                String comparison=dev.serko.safariutils.session.PersonalBestTracker.comparison(split.elapsedMillis(),split.previousBestMillis(),eligible);
                graphics.text(font,comparison,left,y,0xFF000000|
                    dev.serko.safariutils.session.PersonalBestTracker.comparisonColour(split.elapsedMillis(),split.previousBestMillis(),eligible));
                y+=14;
            }
            y+=8;
        }
        if(!any) text(graphics,font,Component.literal("No recorded completion times."),left,y,DIM);
    }

    /** Eight clear pixels on both sides of the same two-pixel section rule. */
    private int runDetailDivider(GuiGraphicsExtractor graphics, Font font, int left, int right, int previousTextY) {
        int lineY=previousTextY+font.lineHeight+8;
        graphics.fill(left,lineY,right,lineY+2,PANEL_BORDER);
        return lineY+2+8;
    }

    private void drawRunPlayers(GuiGraphicsExtractor graphics, Font font, RunRecord run, int left, int y) {
        String label="Players: ";
        graphics.text(font,label,left,y,StatColours.INFO);
        int x=left+font.width(label);
        List<String> players=run.players.isEmpty()?List.of(run.self==null?"":run.self):run.players;
        for(int i=0;i<players.size();i++) {
            if(i>0) { graphics.text(font,", ",x,y,WHITE);x+=font.width(", "); }
            String name=CanonicalPlayerNames.display(players.get(i));
            PlayerNameStyle.drawName(graphics,font,name,x,y);x+=font.width(name);
        }
    }

    private int drawRunSparklings(GuiGraphicsExtractor graphics, Font font, RunRecord run, int left, int y) {
        String heading="Sparklings: ";
        rainbowText(graphics,font,heading,left,y);
        if(run.sparklings.isEmpty()) {
            graphics.text(font,"None",left+font.width(heading),y,WHITE);
            return y+13;
        }
        for (var sparkling:run.sparklings) {
            int x=left+font.width(heading);
            Critter critter=Critters.byName(sparkling.species());
            text(graphics,font,Component.literal(sparkling.species()),x,y,critter==null?WHITE:0xFF000000|critter.rarity().colour());
            x+=font.width(sparkling.species());
            graphics.text(font," (",x,y,WHITE);x+=font.width(" (");
            String catcher=CanonicalPlayerNames.display(sparkling.catcher());
            if(catcher.isEmpty()) catcher="Unknown";
            PlayerNameStyle.drawName(graphics,font,catcher,x,y);x+=font.width(catcher);
            graphics.text(font,")",x,y,WHITE);
            y+=13;
        }
        return y;
    }

    private int bestPanelHeight() {
        return PANEL_PADDING + 3 * BEST_CARD_HEIGHT + 2 * BEST_MOB_SECTION_HEIGHT + 3 * 8 + font.lineHeight + 12 + 30;
    }
    private int runDetailHeight() {
        int players = detailPlayerCounts == null ? 1 : Math.max(1,detailPlayerCounts.size());
        int splits = selectedRun.personalBestSplits == null ? 0 : (int)selectedRun.personalBestSplits.stream()
            .filter(dev.serko.safariutils.session.PersonalBestTracker::valid).count();
        int content = detailBests ? 13 + (font.lineHeight+18-13) + 13 + (splits==0?13:selectedRun.personalBestSplits.stream().filter(dev.serko.safariutils.session.PersonalBestTracker::valid).mapToInt(split -> split.previousBestMillis()>=0?36:22).sum())
            : 13 + 21 + 3*13 + (showValue()?13:0) + Math.max(0,selectedRun.sparklings.size()-1)*13
                + (font.lineHeight+18-13) + 18 + 13 + 10*LINE_HEIGHT
                + (font.lineHeight+18-LINE_HEIGHT) + 13 + players*LINE_HEIGHT
                + (selectedRun.missingIndividualTeammateTotals()?LINE_HEIGHT:0);
        return PANEL_PADDING + ACTION_HEIGHT + 10 + content + 12 + 30;
    }

    private void addBestControls(int bottom) {
        int x = panelLeft + PANEL_PADDING;
        int y = panelTop + navigationOffset() + PANEL_PADDING;
        if (selectedRun != null) {
            controls.add(new Control(detailBests?"Completions":"Information",x,y,120,ACTION_HEIGHT,
                ControlStyle.BUTTON,false,false,() -> { detailBests=!detailBests; rebuildWidgets(); }));
        }
        int buttonWidth=70, gap=8;
        int start=panelLeft+(panelWidth-(selectedRun==null?buttonWidth:buttonWidth*2+gap))/2;
        if(selectedRun!=null) {
            controls.add(new Control("Back",start,bottom,buttonWidth,ACTION_HEIGHT,ControlStyle.BUTTON,false,false,() -> {
                Tab destination=runDetailBack; clearRunDetail(); switchTo(destination);
            }));
            start+=buttonWidth+gap;
        }
        controls.add(new Control("Close",start,bottom,buttonWidth,ACTION_HEIGHT,ControlStyle.BUTTON,false,false,this::onClose));
    }

    private void drawPersonalBests(GuiGraphicsExtractor graphics, Font font, int y, int mouseX, int mouseY) {
        int left = panelLeft+PANEL_PADDING, width = panelWidth-PANEL_PADDING*2;
        drawBestCard(graphics,font,dev.serko.safariutils.session.PersonalBestTracker.ALL,
            left,y,width,StatColours.PERSONAL_BEST,mouseX,mouseY);
        y += BEST_CARD_HEIGHT + 8;
        int column=(width-8)/2;
        for (int i=0;i<BIOME_ORDER.length;i++) {
            SafariBiome biome=BIOME_ORDER[i];
            drawBestCard(graphics,font,biome.name(),left+(i%2)*(column+8),
                y+(i/2)*(BEST_CARD_HEIGHT+BEST_MOB_SECTION_HEIGHT+8),column,0xFF000000|biome.colour(),mouseX,mouseY);
        }
        text(graphics,font,Component.literal("Times start at ticket use. Mob records require your own catch. Click a time for its run."),
            left,y+2*(BEST_CARD_HEIGHT+BEST_MOB_SECTION_HEIGHT+8),DIM);
    }

    private void drawBestCard(GuiGraphicsExtractor graphics, Font font, String target,
            int x,int y,int width,int colour,int mx,int my) {
        var groups=dev.serko.safariutils.session.PersonalBestTracker.Group.values();
        int clears=0;
        for(var group:groups) {
            var best=dev.serko.safariutils.session.PersonalBests.best(target,group);
            if(best!=null) clears+=best.clears();
        }
        boolean all=dev.serko.safariutils.session.PersonalBestTracker.ALL.equals(target);
        int cardHeight=BEST_CARD_HEIGHT+(all?0:BEST_MOB_SECTION_HEIGHT);
        graphics.fill(x,y,x+width,y+cardHeight,PANEL_SURFACE);
        themedOutline(graphics,x,y,width,cardHeight,PANEL_BORDER);
        text(graphics,font,Component.literal(dev.serko.safariutils.session.PersonalBestTracker.title(target)
            +" ("+clears+(clears==1?" Clear)":" Clears)")),x+8,y+7,colour);
        for(int index=0;index<groups.length;index++) {
            var group=groups[index];
            var best=dev.serko.safariutils.session.PersonalBests.best(target,group);
            drawBestRow(graphics,font,best,group.label,"No recorded clear",x,y+22+index*BEST_ROW_HEIGHT,width,colour,mx,my);
        }
        if(!all) {
            int sectionY=y+22+groups.length*BEST_ROW_HEIGHT;
            themedOutline(graphics,x+8,sectionY+1,width-16,1,PANEL_BORDER);
            String mob=dev.serko.safariutils.session.PersonalBestTracker.mobTarget(
                dev.serko.safariutils.session.PersonalBestTracker.biome(target));
            var best=dev.serko.safariutils.session.PersonalBests.best(mob,
                dev.serko.safariutils.session.PersonalBestTracker.Group.SOLO);
            drawBestRow(graphics,font,best,dev.serko.safariutils.session.PersonalBestTracker.title(mob),
                "No recorded catch",x,sectionY+BEST_MOB_GAP,width,colour,mx,my);
        }
    }

    private void drawBestRow(GuiGraphicsExtractor graphics, Font font,
            dev.serko.safariutils.session.PersonalBests.Best best, String label, String empty,
            int x,int textY,int width,int colour,int mx,int my) {
        int rowTop=textY-(BEST_ROW_HEIGHT-7)/2;
        int rowLeft=x+4, rowWidth=width-8;
        boolean hovered=best!=null && mx>=rowLeft && mx<rowLeft+rowWidth
            && my>=rowTop && my<rowTop+BEST_ROW_HEIGHT;
        if(hovered) graphics.fill(rowLeft,rowTop,rowLeft+rowWidth,rowTop+BEST_ROW_HEIGHT,0x24FFFFFF);
        text(graphics,font,Component.literal(label),x+8,textY,StatColours.value(colour));
        String value=best==null?empty:dev.serko.safariutils.session.PersonalBestTracker.time(best.split().elapsedMillis());
        text(graphics,font,Component.literal(value),x+width-8-font.width(value),textY,best==null?DIM:StatColours.TIME);
        if(best!=null) bestHits.add(new BestHit(rowLeft,rowTop,rowWidth,BEST_ROW_HEIGHT,best.run()));
    }

	private void addHistoryControls(int y) {
		int filterWidth = Math.max(108, font.width("Sparkling Runs") + 30);
		int widestSort = 0;
		for (HistorySort value : HistorySort.values()) {
			widestSort = Math.max(widestSort, font.width("Sort: " + value.label));
		}
		int sortWidth = Math.max(104, widestSort + 16);
		int closeWidth = 60;
		int spacing = 4;
		boolean hasCurrent = SessionManager.current() != null;
        int currentWidth = hasCurrent ? font.width("Current Run")+16 : 0;
        int totalWidth = filterWidth + sortWidth + closeWidth + spacing * 2 + (hasCurrent?currentWidth+spacing:0);
		int x = panelLeft + (panelWidth - totalWidth) / 2;
        if(hasCurrent) {
            controls.add(new Control("Current Run",x,y,currentWidth,ACTION_HEIGHT,ControlStyle.BUTTON,false,false,() -> {
                SafariSession current = SessionManager.current();
                if(current!=null) openRunDetail(RunRecord.of(current),current);
            }));
            x+=currentWidth+spacing;
        }
		controls.add(new Control("Sparkling Runs", x, y, filterWidth, ACTION_HEIGHT,
			ControlStyle.SPARKLING_TOGGLE, sparklingRunsOnly, true, () -> {
			sparklingRunsOnly = !sparklingRunsOnly;
			historyScroll = 0;
			invalidateHistoryView();
			rebuildWidgets();
		}));
		x += filterWidth + spacing;
		controls.add(new Control("Sort: " + historySort.label, x, y, sortWidth, ACTION_HEIGHT,
			ControlStyle.BUTTON, false, false, () -> {
			historySort = next(historySort, HistorySort.values());
			historyScroll = 0;
			invalidateHistoryView();
			rebuildWidgets();
		}));
		x += sortWidth + spacing;
		controls.add(new Control("Close", x, y, closeWidth, ACTION_HEIGHT,
			ControlStyle.BUTTON, false, false, this::onClose));
	}

	private static <T extends Enum<T>> T next(T current, T[] values) {
		return values[(current.ordinal() + 1) % values.length];
	}

	/** Wide enough for equal padding and at least three spaces between name and count. */
	private int measureColumnWidth() {
		int widestName = 0;
		int widestCount = font.width("—");
		for (Critter critter : Critters.all()) {
			int nameWidth = font.width(critter.name());
			if (SparklingStats.count(critter) > 0) nameWidth += 2 + font.width("✦");
			widestName = Math.max(widestName, nameWidth);
			widestCount = Math.max(widestCount,
				font.width(selectedRun==null?String.valueOf(RunHistory.statFor(critter).total()):runCritterCounts(selectedRun,critter)));
		}
		return COLUMN_PAD * 2 + widestName + font.width("   ") + widestCount;
	}

	/** Lets summary-heavy tabs grow equally left and right around their text. */
	private int measureTabTextWidth() {
        if (selectedRun != null) {
            // Shared detail width must not depend on which main tab opened it.
            int detailWidth=Math.max(font.width(runRewardSummary(selectedRun,true)),
                font.width(runCatchSummary(selectedRun,true)));
            for (var sparkling:selectedRun.sparklings)
                detailWidth=Math.max(detailWidth,font.width("Sparklings: "+sparkling.species()+" ("+CanonicalPlayerNames.display(sparkling.catcher())+")"));
            return Math.max(520,detailWidth);
        }
		int widest = 0;
		if (tab == Tab.HISTORY) {
			widest = Math.max(widest, font.width(historySummary()));
			widest = Math.max(widest, font.width("A run is saved when the next one starts."));
			widest = Math.max(widest, measureHistoryControlsWidth());
			widest = Math.max(widest, measureHistoryTableWidth());
		} else if (tab == Tab.STATS) {
			widest = Math.max(widest, font.width(statsSummary()));
			widest = Math.max(widest, font.width(valueTotalsText()));
		}
		return widest;
	}

	private int measureHistoryControlsWidth() {
		int filterWidth = Math.max(108, font.width("Sparkling Runs") + 30);
		int widestSort = 0;
		for (HistorySort value : HistorySort.values()) {
			widestSort = Math.max(widestSort, font.width("Sort: " + value.label));
		}
		return filterWidth + Math.max(104, widestSort + 16) + 60 + 8
            + (SessionManager.current()!=null?font.width("Current Run")+20:0);
	}

	private int measureHistoryTableWidth() {
		HistoryColumnWidths widths = historyColumnWidths(filteredHistory(), showValue(), Critters.total());
		return widths.total() + 48 + (font.width("✦") + 3) * 2;
	}

	/** A single navigation rail inside the panel, with one active section. */
	private void addTabButtons() {
		Tab[] tabs = Tab.values();
		int totalWidth = java.util.Arrays.stream(tabs).mapToInt(t -> Math.max(NAV_ITEM_WIDTH,font.width(t.label)+20)).sum();
		int x = panelLeft + (panelWidth - totalWidth) / 2;
		int y = panelTop + 5;

		for (Tab value : tabs) {
			int itemWidth = Math.max(NAV_ITEM_WIDTH,font.width(value.label)+20);
            controls.add(new Control(value.label, x, y, itemWidth, NAV_ITEM_HEIGHT, ControlStyle.TAB,
				value == tab, false, () -> switchTo(value)));
			x += itemWidth;
		}
	}

	private void switchTo(Tab value) {
        clearRunDetail();
		tab = value;
		// The panel is a different height per tab, so everything is laid out again.
		rebuildWidgets();
	}

	private void drawControls(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (selectedRun == null) drawNavigationRail(graphics);
		for (Control control : controls) {
			boolean hovered = control.contains(mouseX, mouseY);
			int colour = SpecialTheme.rainbow() ? SpecialTheme.accent(control.x)
				: control.sparkling
				? UIDraw.rainbow(0, 12, 0.55f)
				: control.style == ControlStyle.TAB
					? tabColour(Tab.valueOf(control.label.toUpperCase(java.util.Locale.ROOT).replace(' ','_')))
					: tabBorderColour();

			if (control.style == ControlStyle.SPARKLING_TOGGLE) {
				graphics.fill(control.x, control.y, control.x + control.width,
					control.y + control.height, hovered ? PANEL_SURFACE_HOVER : PANEL_SURFACE);
				int boxX = control.x + 6;
				int boxY = control.y + (control.height - 9) / 2;
				themedOutline(graphics, control.x, control.y,
					control.width, control.height, PANEL_BORDER);
				themedOutline(graphics, boxX, boxY, 9, 9, colour);
				if (control.selected) {
					graphics.fill(boxX + 2, boxY + 2, boxX + 7, boxY + 7,
						0xA0000000 | (colour & 0xFFFFFF));
					rainbowText(graphics, font, control.label, control.x + 20,
						control.y + (control.height - NAV_TEXT_HEIGHT) / 2);
				} else {
					text(graphics, font, Component.literal(control.label),
						control.x + 20, control.y + (control.height - NAV_TEXT_HEIGHT) / 2,
						hovered ? WHITE : LABEL);
				}
				continue;
			}

			if (control.style == ControlStyle.TAB) {
				// The rail is one control. Only the current section receives a surface,
				// which keeps the labels from reading as four unrelated buttons.
				if (control.selected) {
					int highlightLeft = control.x + NAV_HIGHLIGHT_INSET;
					int highlightRight = control.x + control.width - NAV_HIGHLIGHT_INSET;
					int highlightBottom = control.y + control.height - NAV_HIGHLIGHT_INSET;
					graphics.fill(highlightLeft, control.y + NAV_HIGHLIGHT_INSET,
						highlightRight, highlightBottom,
						0xB025303D);
					if (SpecialTheme.rainbow()) {
						SpecialTheme.bar(graphics, highlightLeft, highlightBottom - 2,
							highlightRight - highlightLeft, 2);
					} else {
						graphics.fill(highlightLeft, highlightBottom - 2,
							highlightRight, highlightBottom, colour);
					}
				} else if (hovered) {
					graphics.fill(control.x + NAV_HIGHLIGHT_INSET,
						control.y + NAV_HIGHLIGHT_INSET,
						control.x + control.width - NAV_HIGHLIGHT_INSET,
						control.y + control.height - NAV_HIGHLIGHT_INSET,
						0x5825303D);
				}
			} else {
				graphics.fill(control.x, control.y, control.x + control.width,
					control.y + control.height, hovered ? PANEL_SURFACE_HOVER : PANEL_SURFACE);
				themedOutline(graphics, control.x, control.y,
					control.width, control.height, PANEL_BORDER);
				if (hovered) {
					if (SpecialTheme.rainbow()) {
						SpecialTheme.bar(graphics, control.x + 1, control.y + 1,
							2, control.height - 2);
					} else {
						graphics.fill(control.x + 1, control.y + 1, control.x + 3,
							control.y + control.height - 1, colour);
					}
				}
			}
			int textX = control.x + Math.round((control.width - font.width(control.label)) / 2.0f);
			int textY = control.y + (control.height - NAV_TEXT_HEIGHT) / 2;
			if (control.sparkling) rainbowText(graphics, font, control.label, textX, textY);
			else text(graphics, font, Component.literal(control.label), textX, textY,
				control.selected || hovered ? WHITE : LABEL);
		}
	}

	/** Draws one quiet backdrop behind all four section labels. */
	private void drawNavigationRail(GuiGraphicsExtractor graphics) {
		Control first = null;
		Control last = null;
		for (Control control : controls) {
			if (control.style != ControlStyle.TAB) continue;
			if (first == null) first = control;
			last = control;
		}
		if (first == null) return;
		int left = first.x;
		int right = last.x + last.width;
		graphics.fillGradient(left, first.y, right, first.y + first.height,
			0x9A18212C, 0x9A111820);
		themedOutline(graphics, left, first.y, right - left, first.height, PANEL_BORDER);
		Control previous = null;
		for (Control control : controls) {
			if (control.style != ControlStyle.TAB) continue;
			if (previous != null) {
				int dividerX = control.x;
				graphics.fill(dividerX, control.y + 5, dividerX + 1,
					control.y + control.height - 5, PANEL_BORDER);
			}
			previous = control;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int logicalMouseX = Math.round(mouseX / responsiveScale);
		int logicalMouseY = Math.round(mouseY / responsiveScale);
		graphics.pose().pushMatrix();
		graphics.pose().scale(responsiveScale, responsiveScale);
		graphics.fillGradient(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight,
			0xE0121922, PANEL_BACKGROUND);
		if (SpecialTheme.rainbow()) {
			SpecialTheme.stars(graphics, panelLeft + 2, panelTop + 2,
				panelWidth - 4, panelHeight - 4, 1.375f);
			SpecialTheme.border(graphics, panelLeft, panelTop, panelWidth, panelHeight);
		} else drawPanelBorder(graphics, tabBorderColour());
		// Drawn after the main frame so a two-pixel themed border cannot cover it.
		UIDraw.outline(graphics, panelLeft + 2, panelTop + 2, panelWidth - 4,
			panelHeight - 4, PANEL_KEYLINE);

		bestHits.clear();
        Font font = this.font;
		int y = panelTop + PANEL_PADDING + navigationOffset();

        if (selectedRun != null) drawRunDetails(graphics,font,y);
        else switch (tab) {
            case HISTORY -> drawHistory(graphics,font,y,logicalMouseX,logicalMouseY);
            case STATS -> drawStats(graphics,font,y);
            case PERSONAL_BESTS -> drawPersonalBests(graphics,font,y,logicalMouseX,logicalMouseY);
        }

		// Screen children first, then the themed controls above the panel.
		super.extractRenderState(graphics, logicalMouseX, logicalMouseY, partialTick);
		drawControls(graphics, logicalMouseX, logicalMouseY);
		graphics.pose().popMatrix();
	}

	/**
	 * Filtered and sorted saved runs in a fixed scrollable viewport.
	 *
	 * <p>Finished runs are persisted by the session lifecycle. The current run is
     * available through the History toolbar without inserting a fake saved record.
	 */
    /** Seven-pixel icon matches normal text ink, independent of Unicode font bearings. */
    private static void historySparkle(GuiGraphicsExtractor graphics, int x, int y) {
        int colour=RainbowColours.shared(x/96f,0.65f);
        for(int row=0;row<7;row++) {
            int half=row==3?3:Math.abs(row-3)==1?1:0;
            graphics.fill(x+3-half,y+row,x+4+half,y+row+1,colour);
        }
    }

	private void drawHistory(GuiGraphicsExtractor graphics, Font font, int y, int mouseX, int mouseY) {
		int left = panelLeft + PANEL_PADDING;
		int right = panelLeft + panelWidth - PANEL_PADDING;
		List<RunRecord> allRuns = RunHistory.runs();
		List<RunRecord> runs = filteredHistory();
		List<HistoryDisplayRow> displayRows = historyDisplayRows(runs);
		historyScroll = Math.clamp(historyScroll, 0, Math.max(0, displayRows.size() - HISTORY_ROWS));

		text(graphics, font, Component.literal("Saved Runs"), left, y, tabBorderColour());
		String count = !sparklingRunsOnly
			? allRuns.size() + " Kept"
			: runs.size() + "/" + allRuns.size() + " Shown";
		text(graphics, font, Component.literal(count), right - font.width(count), y, DIM);
		y += LINE_HEIGHT + 3;

		if (allRuns.isEmpty()) {
			centered(graphics, font, "No runs saved yet.", y, LABEL);
			y += LINE_HEIGHT;
			centered(graphics, font, "A run is saved when the next one starts.", y, DIM);
			y += LINE_HEIGHT;
			return;
		}

		boolean value = showValue();
		centeredSummary(graphics,font,historySummary(runs),y);
		y += LINE_HEIGHT + 5;

		// Fixed columns: the proportional font makes padded text impossible to align.
		int sparkleGutter = font.width("✦") + 3;
		// Anchor the two outside columns inward, so a fourth digit in the run number or
		// a larger coin value grows toward the table instead of eating an edge margin.
		int tableLeft = left + sparkleGutter + 4;
		int tableRight = right - sparkleGutter - 4;
		HistoryColumnWidths widths = historyColumnWidths(runs, value, Critters.total());
		int runX = tableLeft;
		int catchesX = tableRight - widths.catches();
		int middleWidths = widths.date() + widths.length() + widths.uniques();
		int gap = Math.max(0, (catchesX - runX - widths.run() - middleWidths) / 4);
		int dateX = runX + widths.run() + gap;
		int lengthX = dateX + widths.date() + gap;
		int uniquesX = lengthX + widths.length() + gap;
		text(graphics, font, Component.literal("Run"), runX, y, LABEL);
		centeredCell(graphics, font, "Date", dateX, widths.date(), y, StatColours.DATE);
		centeredCell(graphics, font, "Length", lengthX, widths.length(), y, StatColours.TIME);
		centeredCell(graphics, font, "Uniques", uniquesX, widths.uniques(), y, StatColours.UNIQUE);
		text(graphics, font, Component.literal("Catches"),
			tableRight - font.width("Catches"), y, showValue()?COINS:StatColours.CATCHES);
		y += LINE_HEIGHT + 2;
		int rowsTop = y;

		int total = Critters.total();
		RunRecord hovered = null;
		int end = Math.min(displayRows.size(), historyScroll + HISTORY_ROWS);
		for (int i = historyScroll; i < end; i++) {
			HistoryDisplayRow displayRow = displayRows.get(i);
			if (displayRow.divider()) {
				drawYearDivider(graphics, font, displayRow.year(), left, right, y);
				y += LINE_HEIGHT;
				continue;
			}
			RunRecord run = displayRow.run();
			HistoryRowView row = historyRow(run, value, total);
            int rowTop=y-(LINE_HEIGHT-7)/2;
            bestHits.add(new BestHit(left,rowTop,right-left,LINE_HEIGHT,run));
			boolean sparkling = row.sparkling;
			if (mouseX >= left && mouseX < right && mouseY >= rowTop && mouseY < rowTop + LINE_HEIGHT) {
				hovered = run;
				graphics.fill(left, rowTop, right, rowTop + LINE_HEIGHT, 0x24FFFFFF);
			}
			if (sparkling) {
				// Fixed outer anchors keep both stars and the table-wall spacing stable
				// as run numbers and formatted profit values gain digits.
				rainbowText(graphics, font, "✦", left + 2, y);
				rainbowText(graphics, font, "✦", right - 2 - font.width("✦"), y);
			}
			historyText(graphics, font, row.runLabel, runX, y, StatColours.value(LABEL), sparkling);
			historyCell(graphics, font, row.date,
				dateX, widths.date(), y, StatColours.value(StatColours.DATE), sparkling);
			historyCell(graphics, font, row.length,
				lengthX, widths.length(), y, StatColours.value(StatColours.TIME), sparkling);
			historyCell(graphics, font, row.uniques,
				uniquesX, widths.uniques(), y,
				row.perfect ? 0xFF35D9C4 : StatColours.value(StatColours.UNIQUE), sparkling);
			historyText(graphics, font, row.catches,
				tableRight - font.width(row.catches), y,
				StatColours.value(row.priced ? COINS : StatColours.CATCHES), sparkling);
			y += LINE_HEIGHT;
		}

		int detailsY = rowsTop + HISTORY_ROWS * LINE_HEIGHT + 3;
		if (hovered != null && hovered.emeraldDyes() > 0 && sparklingCount(hovered) == 0) {
            centered(graphics, font, "Emerald Dye \u00d7" + hovered.emeraldDyes(), detailsY, 0xFF55FF88);
        } else if (hovered != null && sparklingCount(hovered) > 0) {
			rainbowCentered(graphics, font, sparklingSummary(hovered), detailsY);
		} else if (runs.isEmpty()) {
			centered(graphics, font, "No runs match this filter.", detailsY, DIM);
		} else {
			int first = 1 + countRuns(displayRows, 0, historyScroll);
			int last = first + countRuns(displayRows, historyScroll, end) - 1;
			centered(graphics, font, "Showing %d–%d of %d%sScroll for more"
				.formatted(first, Math.max(first, last), runs.size(), STAT_SEPARATOR), detailsY, DIM);
		}
	}

	/**
	 * What every species has been worth across the saved runs.
	 *
	 * <p>The totals are cached by {@link RunHistory}; opening this tab does not rescan
	 * every saved run each frame.
	 */
	private void drawStats(GuiGraphicsExtractor graphics, Font font, int y) {
		int left = panelLeft + PANEL_PADDING;
		int right = panelLeft + panelWidth - PANEL_PADDING;
		int runs = RunHistory.size();

		text(graphics, font, Component.literal("Total Run Stats"), left, y, tabBorderColour());
		String legend = "Total Catches";
		text(graphics, font, Component.literal(legend), right - font.width(legend), y, StatColours.CATCHES);
		y += LINE_HEIGHT + 3;

		if (runs == 0) {
			centered(graphics, font, "Nothing saved yet.", y, LABEL);
			return;
		}

		centeredSummary(graphics,font,statsSummary(),y);
		y += LINE_HEIGHT;

		if (showValue()) {
			y = drawValueTotals(graphics, font, y);
		}
		y += 5;

		SafariBiome[] biomes = BIOME_ORDER;
		int gridLeft = panelLeft + (panelWidth - columnWidth * biomes.length) / 2;
		for (int i = 0; i < biomes.length; i++) {
			SafariBiome biome = biomes[i];
			int x = gridLeft + i * columnWidth;
			int rowY = y;

			boolean biomeComplete = Critters.inBiome(biome).stream()
				.allMatch(critter -> SparklingStats.count(critter) > 0);
			String biomeTitle = biome.displayName();
			int biomeTitleX = x + (columnWidth - font.width(biomeTitle)) / 2;
			text(graphics, font, Component.literal(biomeTitle), biomeTitleX, rowY,
				0xFF000000 | biome.colour());
			if (biomeComplete) {
				int starWidth = font.width("✦");
				rainbowText(graphics, font, "✦", biomeTitleX - starWidth - 4, rowY);
				rainbowText(graphics, font, "✦", biomeTitleX + font.width(biomeTitle) + 4, rowY);
			}
			rowY += LINE_HEIGHT + 2;

			for (Critter critter : Critters.inBiome(biome)) {
				RunHistory.SpeciesStat stat = RunHistory.statFor(critter);
				String note = stat.total() == 0 ? "—" : String.valueOf(stat.total());
				int noteX = x + columnWidth - COLUMN_PAD - font.width(note);
				int nameX = x + COLUMN_PAD;
				text(graphics, font, Component.literal(critter.name()), nameX, rowY,
					0xFF000000 | critter.rarity().colour());
				if (SparklingStats.count(critter) > 0) {
					rainbowText(graphics, font, "✦", nameX + font.width(critter.name()) + 2, rowY);
				}
				text(graphics, font, Component.literal(note), noteX, rowY,
					stat.total() == 0 ? UNCAUGHT : StatColours.value(0xFF000000|critter.rarity().colour()));
				rowY += LINE_HEIGHT;
			}
		}
	}

	/**
	 * Money across the saved runs: everything they were worth, and what that is a run.
	 *
	 * <p>The average excludes old runs without a shard breakdown instead of treating
	 * their unknown value as zero.
	 */
	private int drawValueTotals(GuiGraphicsExtractor graphics, Font font, int y) {
		int priced = RunHistory.pricedRuns();

		if (!BazaarPrices.known()) {
			centered(graphics, font, BazaarPrices.lastError() == null
					? "Waiting for bazaar prices…"
					: "No bazaar prices: " + BazaarPrices.lastError(), y, DIM);
			return y + LINE_HEIGHT;
		}

		if (priced > 0) {
			centeredSummary(graphics,font,valueTotalsText(),y);
			y += LINE_HEIGHT;
		}

		return y;
	}

	private int tabBorderColour() {
        if(selectedRun!=null && detailBests) return tabColour(Tab.PERSONAL_BESTS);
        if(selectedRun!=null) return Colours.argb(ConfigManager.get().display.currentRunTabBorderColour,0xFF55FF55);
		return tabColour(tab);
	}

	private static int tabColour(Tab target) {
		SafariConfig.DisplayConfig display = ConfigManager.get().display;
		return switch (target) {
			case HISTORY -> Colours.argb(display.historyTabBorderColour, 0xFF55FF55);
			case STATS -> Colours.argb(display.statsTabBorderColour, 0xFF55FFFF);
            case PERSONAL_BESTS -> StatColours.PERSONAL_BEST;
		};
	}

	private void drawPanelBorder(GuiGraphicsExtractor graphics, int colour) {
		UIDraw.outline(graphics, panelLeft, panelTop, panelWidth, panelHeight, colour);
		UIDraw.outline(graphics, panelLeft + 1, panelTop + 1,
			panelWidth - 2, panelHeight - 2, colour);
	}

	private static void rainbowText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y) {
		if (SpecialTheme.rainbow()) {
			SpecialTheme.rainbowText(graphics, font, text, x, y);
			return;
		}
		UIDraw.rainbowText(graphics, font, text, x, y, 0.45f);
	}

	private void historyCell(GuiGraphicsExtractor graphics, Font font, String value,
			int x, int width, int y, int colour, boolean sparkling) {
		int textX = x + (width - font.width(value)) / 2;
		historyText(graphics, font, value, textX, y, colour, sparkling);
	}

	private void historyText(GuiGraphicsExtractor graphics, Font font, String value,
			int x, int y, int colour, boolean sparkling) {
		if (sparkling && !SpecialTheme.rainbow()) {
			UIDraw.rainbowText(graphics, font, value, x, y, 0.45f);
		} else {
			text(graphics, font, Component.literal(value), x, y, colour);
		}
	}

	private HistoryRowView historyRow(RunRecord run, boolean value, int totalCritters) {
		long priceRevision = BazaarPrices.revision();
		if (cachedHistoryPriceRevision != priceRevision || cachedHistoryValueMode != value) {
			cachedHistoryRows.clear();
			cachedHistoryPriceRevision = priceRevision;
			cachedHistoryValueMode = value;
		}
		return cachedHistoryRows.computeIfAbsent(run, current -> {
			boolean priced = value && current.hasShardData() && BazaarPrices.known();
			String catches = String.valueOf(current.partyTotal());
			if (value) catches += " (" + (priced
				? BazaarPrices.format(BazaarPrices.valueOf(current)) + (BazaarPrices.rareDropPricingIncomplete(current.rareDrops) ? " + ?" : "") : "—") + ")";
			return new HistoryRowView("#" + runNumber(current), formatWhen(current.started),
				ProgressHud.formatDuration(current.durationMillis()), current.partyUnique() + "/" + totalCritters,
				catches,
				sparklingCount(current) > 0, current.partyUnique() == totalCritters, priced);
		});
	}

	private record HistoryRowView(String runLabel, String date, String length, String uniques,
			String catches, boolean sparkling, boolean perfect,
			boolean priced) { }

	private record HistoryColumnWidths(int run, int date, int length, int uniques, int catches) {
		int total() {
			return run + date + length + uniques + catches;
		}
	}

	private HistoryColumnWidths historyColumnWidths(List<RunRecord> runs, boolean value,
			int totalCritters) {
		long priceRevision = BazaarPrices.revision();
		if (cachedHistoryWidthsSource == runs
				&& cachedHistoryWidthsPriceRevision == priceRevision
				&& cachedHistoryWidthsValueMode == value
				&& cachedHistoryWidthsCritterTotal == totalCritters
				&& cachedHistoryWidths != null) return cachedHistoryWidths;
		int run = font.width("Run");
		int date = font.width("Date");
		int length = font.width("Length");
		int uniques = font.width("Uniques");
		int catches = font.width("Catches");
		for (RunRecord savedRun : runs) {
			HistoryRowView row = historyRow(savedRun, value, totalCritters);
			run = Math.max(run, font.width(row.runLabel()));
			date = Math.max(date, font.width(row.date()));
			length = Math.max(length, font.width(row.length()));
			uniques = Math.max(uniques, font.width(row.uniques()));
			catches = Math.max(catches, font.width(row.catches()));
		}
		cachedHistoryWidthsSource = runs;
		cachedHistoryWidthsPriceRevision = priceRevision;
		cachedHistoryWidthsValueMode = value;
		cachedHistoryWidthsCritterTotal = totalCritters;
		cachedHistoryWidths = new HistoryColumnWidths(run, date, length, uniques, catches);
		return cachedHistoryWidths;
	}

	private record HistoryDisplayRow(RunRecord run, int year, boolean divider) { }

	private List<HistoryDisplayRow> historyDisplayRows(List<RunRecord> runs) {
		if (cachedHistoryDisplaySource == runs && cachedHistoryDisplayRows != null) {
			return cachedHistoryDisplayRows;
		}
		List<HistoryDisplayRow> rows = new ArrayList<>();
		Integer previousYear = null;
		for (RunRecord run : runs) {
			int year = yearOf(run.started);
			if (previousYear != null && year != previousYear) {
				rows.add(new HistoryDisplayRow(null, previousYear, true));
			}
			rows.add(new HistoryDisplayRow(run, year, false));
			previousYear = year;
		}
		if (previousYear != null) rows.add(new HistoryDisplayRow(null, previousYear, true));
		cachedHistoryDisplaySource = runs;
		cachedHistoryDisplayRows = List.copyOf(rows);
		return cachedHistoryDisplayRows;
	}

	private static int countRuns(List<HistoryDisplayRow> rows, int from, int to) {
		int count = 0;
		for (int i = from; i < to; i++) if (!rows.get(i).divider()) count++;
		return count;
	}

	private void drawYearDivider(GuiGraphicsExtractor graphics, Font font, int year,
			int left, int right, int y) {
		String label = String.valueOf(year);
		int centre = (left + right) / 2;
		int halfLabel = font.width(label) / 2;
		int lineY = y + 4;
		graphics.fill(left + 8, lineY, centre - halfLabel - 8, lineY + 1, PANEL_BORDER);
		graphics.fill(centre + halfLabel + 8, lineY, right - 8, lineY + 1, PANEL_BORDER);
		centered(graphics, font, label, y, tabBorderColour());
	}

	private void themedOutline(GuiGraphicsExtractor graphics, int x, int y,
			int width, int height, int fallback) {
		if (SpecialTheme.rainbow()) SpecialTheme.border(graphics, x, y, width, height, 1);
		else UIDraw.outline(graphics, x, y, width, height, fallback);
	}

	private void rainbowCentered(GuiGraphicsExtractor graphics, Font font, String value, int y) {
		rainbowText(graphics, font, value, panelLeft + (panelWidth - font.width(value)) / 2, y);
	}

	private static int sparklingCount(RunRecord run) {
		return run.sparklings == null ? 0 : run.sparklings.size();
	}

	/** Chronological saved-run number, independent of the current filter and sort. */
	private int runNumber(RunRecord target) {
		long historyRevision = RunHistory.revision();
		if (cachedRunNumberRevision != historyRevision) {
			List<RunRecord> chronological = new ArrayList<>(RunHistory.runs());
			chronological.sort(Comparator.comparingLong(run -> run.started));
			cachedRunNumbers.clear();
			for (int i = 0; i < chronological.size(); i++) {
				cachedRunNumbers.put(chronological.get(i), i + 1);
			}
			cachedRunNumberRevision = historyRevision;
		}
		return cachedRunNumbers.getOrDefault(target, 0);
	}

	private String sparklingSummary(RunRecord run) {
		String cached = cachedSparklingSummaries.get(run);
		if (cached != null) return cached;
		Map<String, Integer> species = new java.util.LinkedHashMap<>();
		if (run.sparklings != null) {
			for (RunRecord.SparklingRecord sparkling : run.sparklings) {
				if (sparkling != null && sparkling.species() != null) {
					species.merge(sparkling.species(), 1, Integer::sum);
				}
			}
		}
		String names = species.entrySet().stream()
			.map(entry -> entry.getKey() + (entry.getValue() > 1 ? " ×" + entry.getValue() : ""))
			.collect(java.util.stream.Collectors.joining(", "));
		String summary = "✦ " + sparklingCount(run) + " Sparkling" + (sparklingCount(run) == 1 ? "" : "s")
			+ (names.isEmpty() ? "" : ": " + names) + " ✦";
		cachedSparklingSummaries.put(run, summary);
		return summary;
	}

	private List<RunRecord> filteredHistory() {
		long historyRevision = RunHistory.revision();
		if (cachedHistory != null && cachedHistoryRevision == historyRevision) return cachedHistory;
		if (cachedHistoryRevision != historyRevision) {
			cachedHistoryRows.clear();
			cachedSparklingSummaries.clear();
			cachedRunNumbers.clear();
			cachedHistoryDisplayRows = null;
			cachedHistoryWidths = null;
			cachedHistorySummary = null;
		}
		List<RunRecord> runs = new ArrayList<>();
		for (RunRecord run : RunHistory.runs()) {
			if (!sparklingRunsOnly || sparklingCount(run) > 0) runs.add(run);
		}
		Comparator<RunRecord> order = switch (historySort) {
			case NEWEST -> Comparator.comparingLong((RunRecord run) -> run.started).reversed();
			case OLDEST -> Comparator.comparingLong(run -> run.started);
			case LONGEST -> Comparator.comparingLong(RunRecord::durationMillis).reversed()
				.thenComparing(Comparator.comparingLong((RunRecord run) -> run.started).reversed());
			case SHORTEST -> Comparator.comparingLong(RunRecord::durationMillis)
				.thenComparing(Comparator.comparingLong((RunRecord run) -> run.started).reversed());
			case MOST_CATCHES -> Comparator.comparingInt(RunRecord::partyTotal).reversed()
				.thenComparing(Comparator.comparingLong((RunRecord run) -> run.started).reversed());
			case MOST_SPARKLINGS -> Comparator.comparingInt(SafariDashboardScreen::sparklingCount).reversed()
				.thenComparing(Comparator.comparingLong((RunRecord run) -> run.started).reversed());
		};
		runs.sort(order);
		cachedHistoryRevision = historyRevision;
		cachedHistory = List.copyOf(runs);
		return cachedHistory;
	}

	private void invalidateHistoryView() {
		cachedHistory = null;
		cachedHistoryDisplaySource = null;
		cachedHistoryDisplayRows = null;
		cachedHistoryWidthsSource = null;
		cachedHistoryWidths = null;
		cachedHistorySummarySource = null;
		cachedHistorySummary = null;
	}

	private String historySummary() {
		return historySummary(filteredHistory());
	}

	private String historySummary(List<RunRecord> runs) {
		long priceRevision = BazaarPrices.revision();
		if (cachedHistorySummarySource == runs
				&& cachedHistorySummaryPriceRevision == priceRevision
				&& cachedHistorySummary != null) return cachedHistorySummary;
		long played = runs.stream().mapToLong(RunRecord::durationMillis).sum();
		int catches = runs.stream().mapToInt(RunRecord::partyTotal).sum();
		String summary = "%d Run%s%s%s Played%s%d Caught".formatted(
			runs.size(), runs.size() == 1 ? "" : "s", STAT_SEPARATOR,
			formatHours(played), STAT_SEPARATOR, catches);
		if (showValue() && BazaarPrices.known()) {
			long value = sparklingRunsOnly
				? BazaarPrices.totalValue(runs) : BazaarPrices.totalHistoryValue();
			summary += STAT_SEPARATOR + "%s Coins".formatted(BazaarPrices.format(value));
		}
		int unknown = sparklingRunsOnly ? unknownSparklings() : 0;
		if (unknown > 0) {
			summary += STAT_SEPARATOR + "%d Unknown".formatted(unknown);
		}
		cachedHistorySummarySource = runs;
		cachedHistorySummaryPriceRevision = priceRevision;
		cachedHistorySummary = summary;
		return summary;
	}

	/** Lifetime Sparkling catches that predate or otherwise fall outside saved runs. */
	private static int unknownSparklings() {
		int recorded = 0;
		for (RunRecord run : RunHistory.runs()) recorded += sparklingCount(run);
		return Math.max(0, SparklingStats.total() - recorded);
	}

	private String statsSummary() {
		int runs = RunHistory.size();
		if (runs == 0) return "Nothing saved yet.";
		return "%d Runs%s%s Played%s%d Catches%s%.1f Catches Per Run".formatted(
			runs, STAT_SEPARATOR, formatHours(RunHistory.totalTimeMillis()), STAT_SEPARATOR,
			RunHistory.totalCatches(), STAT_SEPARATOR,
			(double) RunHistory.totalCatches() / runs);
	}

	private String valueTotalsText() {
		int priced = RunHistory.pricedRuns();
		if (!BazaarPrices.known() || priced == 0) return "";
		long historyRevision = RunHistory.revision();
		long priceRevision = BazaarPrices.revision();
		if (cachedValueTotalsHistoryRevision == historyRevision
				&& cachedValueTotalsPriceRevision == priceRevision) return cachedValueTotals;
		long total = BazaarPrices.totalHistoryValue();
		cachedValueTotals = "%s Coins%s%s Per Run%s%d Shards%s%d Essence"
			.formatted(BazaarPrices.format(total), STAT_SEPARATOR,
				BazaarPrices.format(total / priced), STAT_SEPARATOR, RunHistory.totalShards(),
				STAT_SEPARATOR, RunHistory.totalSafariEssence());
		cachedValueTotalsHistoryRevision = historyRevision;
		cachedValueTotalsPriceRevision = priceRevision;
		return cachedValueTotals;
	}

    private void centeredSummary(GuiGraphicsExtractor graphics, Font font, String value, int y) {
        StatColours.summaryLine(graphics,font,value,STAT_SEPARATOR,panelLeft+(panelWidth-font.width(value))/2,y);
    }

	private void centered(GuiGraphicsExtractor graphics, Font font, String text, int y, int colour) {
		text(graphics, font, Component.literal(text),
			panelLeft + (panelWidth - font.width(text)) / 2, y, colour);
	}

	private static void centeredCell(GuiGraphicsExtractor graphics, Font font, String text,
								 int x, int width, int y, int colour) {
		text(graphics, font, Component.literal(text), x + (width - font.width(text)) / 2, y, colour);
	}

	private static void text(GuiGraphicsExtractor graphics, Font font, Component component,
						 int x, int y, int colour) {
		SpecialTheme.text(graphics, font, component, x, y, colour);
	}

	/** Date and time of day, which is how you recognise a run you remember. */
	private static String formatWhen(long millis) {
		return WHEN.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
	}

	private static int yearOf(long millis) {
		return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).getYear();
	}

	/** Hours and minutes, for spans far longer than one run. */
	private static String formatHours(long millis) {
		long minutes = millis / 60_000;
		return minutes < 60 ? minutes + "M" : "%dH %02dM".formatted(minutes / 60, minutes % 60);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		double mouseX = event.x() / responsiveScale;
		double mouseY = event.y() / responsiveScale;
		for (Control control : controls) {
			if (!control.contains(mouseX, mouseY)) continue;
			if (control.style == ControlStyle.TAB && control.selected && selectedRun == null) return true;
			control.action.run();
			return true;
		}
		for (BestHit hit : bestHits) {
            if (mouseX >= hit.x && mouseX < hit.x+hit.width && mouseY >= hit.y && mouseY < hit.y+hit.height) {
                openRunDetail(hit.run,null); return true;
            }
        }
        return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		mouseX /= responsiveScale;
		mouseY /= responsiveScale;
		if (selectedRun != null || tab != Tab.HISTORY || mouseX < panelLeft || mouseX >= panelLeft + panelWidth
			|| mouseY < panelTop || mouseY >= panelTop + panelHeight) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		int maxScroll = Math.max(0,
			historyDisplayRows(filteredHistory()).size() - HISTORY_ROWS);
		int direction = scrollY > 0 ? -1 : scrollY < 0 ? 1 : 0;
		if (direction == 0) return false;
		historyScroll = Math.clamp(historyScroll + direction * HISTORY_ROWS, 0, maxScroll);
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
