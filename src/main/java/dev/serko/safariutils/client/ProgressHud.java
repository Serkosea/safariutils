package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.session.SafariSession;
import dev.serko.safariutils.session.SessionManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Map;

/**
 * Top-left overview panel: total run progress, a bar per biome, profit, and optional
 * player assignments.
 */
public final class ProgressHud implements HudElement {
	private final TickCache<HudPanel> panelCache = new TickCache<>();

	private static final int HEADER = 0xFFFFAA00;
	private static final int LABEL = 0xFFBBBBBB;
	private static final int DIM = 0xFF888888;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int DONE = 0xFF55FF55;
	/** Gold, as coins are everywhere else in SkyBlock. */
	private static final int COINS = 0xFFFFD700;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		SafariConfig config = ConfigManager.get();
		if (!config.display.hudEnabled) return;

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || ClientCompat.hudHidden()) return;
		boolean inCanyon = config.display.showInCanyon && "Torrhus Canyon".equals(SafariLocation.tabListArea());
		if (!SafariLocation.inside() && !inCanyon) return;

		HudPanel panel = panelCache.get(ProgressHud::buildPanel);
		if (panel == null) return;

		HudBox box = HudBox.PROGRESS;
		int x = box.pixelX(graphics.guiWidth(), panel, client.font);
		int y = box.pixelY(graphics.guiHeight(), panel, client.font);
		if (SparklingWatch.hudThemeActive()) panel.renderRainbow(graphics, client.font, x, y, box.scale());
		else panel.render(graphics, client.font, x, y, box.scale(), HudBorderStyle.progress());
	}

	/** Builds the panel for the current run, or {@code null} when there is nothing to show. */
	static HudPanel buildPanel() {
		SafariConfig config = ConfigManager.get();

		// Standing at the entrance before going in, no run has been tracked yet. Showing
		// an empty tracker is right there: it says the mod is watching and gives the
		// biome targets. Returning nothing just looked like it was broken.
		SafariSession session = SessionManager.currentOrLast();
		boolean waiting = SafariLocation.inside() && SessionManager.current() == null;

		int total = Critters.total();
		boolean live = SessionManager.current() != null;

		HudPanel panel = new HudPanel();
		if (waiting && SafariLocation.inside()) {
			int joined = SafariPartyWatch.joinedPlayers();
			panel.titleSuffix("Critter Safari ", "(%d/4)".formatted(joined),
				HudBorderStyle.progressTitle(), joined >= 4 ? 0xFF55FF55 : 0xFFFF5555);
		} else {
			panel.title(waiting ? "Critter Safari"
				: live ? "Critter Safari  " + formatDuration(session.elapsedMillis(System.currentTimeMillis()))
				: config.display.showLastRun ? "Critter Safari (Last Run)" : "Critter Safari",
				HudBorderStyle.progressTitle());
		}

		// Nothing but the title while there is no actual run to report on — every bar
		// and row below would just be showing zeroes against a session that only
		// exists to give the title something to say. This is what makes it sensible
		// to leave the panel visible outside the Safari at all rather than only
		// inside it: elsewhere, it is just a small marker, not an empty tracker.
		if (waiting) return panel;
		panel.minimumWidth(190);
		if (session == null) session = new SafariSession(Minecraft.getInstance().getUser().getName(),
			System.currentTimeMillis());

		// Outside the Safari, a past run's full details are opt-in — the title alone
		// already gives the "there was a run" signal, and Show Last Run is what
		// actually asks to see the rest of it there too.
		if (!live && !SafariLocation.inside() && !config.display.showLastRun) return panel;

		// Named a few seconds into the run, and different for everyone in the party, so
		// it is worth a line of its own rather than a mark on one of the bars.
		SafariBiome hotspot = HotspotWatch.biome();
		if (config.display.showHotspot && hotspot != null) {
			panel.compactPair("Hotspot", hotspot.displayName(), 0xFF000000 | hotspot.colour(),
				0xFF000000 | hotspot.colour());
		}
		if (config.profit.enabled && config.display.shardProfit
			&& (session.totalShards() > 0 || session.safariEssence() > 0
				|| session.rainbowFeathers() > 0) && BazaarPrices.known()) {
			panel.compactPair("Profit", BazaarPrices.coinsText(session), COINS, COINS);
		}
		panel.blank();
		if (session.dexComplete()) {
			panel.checkedBar("Total", session.partyUnique(), total, WHITE, WHITE);
		} else {
			panel.bar("Total", session.partyUnique(), total, WHITE, WHITE);
		}

		for (SafariBiome biome : SafariBiome.values()) {
			int max = Critters.totalIn(biome);
			boolean complete = session.partyUnique(biome) == max;
			if (complete) {
				panel.checkedBar(biome.displayName(), session.partyUnique(biome), max,
					0xFF000000 | biome.colour(), 0xFF000000 | biome.colour());
			} else {
				panel.bar(biome.displayName(), session.partyUnique(biome), max,
					0xFF000000 | biome.colour(), 0xFF000000 | biome.colour());
			}
		}

		if (config.display.showPerPlayer) {
			Map<String, Map<SafariBiome, Integer>> perPlayer = session.uniquePerPlayer();
			if (perPlayer.size() > 1) {
				// Always exactly one gap here, whatever came right before it — the
				// bars directly, or Profit's own line if that rendered too. Adding a
				// second blank after Profit specifically would have doubled the gap
				// on any run where both are showing, since blank() always adds a row
				// rather than collapsing consecutive calls into one.
				panel.blank();
				perPlayer.forEach((player, counts) ->
					panel.pair(player, describe(counts), 0xFF55FFFF, DIM));
			}
		}

		return panel;
	}

	/** A player's coverage as {@code "Icy 9, Haunted 2"}, busiest biome first. */
	private static String describe(Map<SafariBiome, Integer> counts) {
		return counts.entrySet().stream()
			.sorted(Map.Entry.<SafariBiome, Integer>comparingByValue().reversed())
			.map(e -> e.getKey().displayName() + " " + e.getValue())
			.reduce((a, b) -> a + ", " + b)
			.orElse("-");
	}

	static String formatDuration(long millis) {
		long seconds = millis / 1000;
		return "%d:%02d".formatted(seconds / 60, seconds % 60);
	}

	static int rarityColour(Critter critter) {
		return 0xFF000000 | critter.rarity().colour();
	}
}
