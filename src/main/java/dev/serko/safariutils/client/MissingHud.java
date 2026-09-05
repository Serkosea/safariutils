package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.session.SafariSession;
import dev.serko.safariutils.session.SessionManager;
import dev.serko.safariutils.session.TrackingMode;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * Top-right panel listing what is still uncaught <em>in the biome you are standing
 * in</em> — the working list for whoever is assigned that biome.
 *
 * <p>Species already caught by a partymate count as done, since the run's goal is
 * party-wide coverage. A caught-by-you marker distinguishes the two.
 */
public final class MissingHud implements HudElement {
	private final TickCache<HudPanel> panelCache = new TickCache<>();

	private static final int DONE = 0xFF55FF55;
	private static final int DIM = 0xFF888888;
	private static final Critter GIMMIEGOLD = Critters.byName("Gimmiegold");

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		SafariConfig config = ConfigManager.get();
		if (!config.display.hudEnabled || !config.display.showMissing) return;
		if (!SafariLocation.inside() || SafariLocation.biome() == null) return;

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || ClientCompat.hudHidden()) return;

		SafariBiome biome = SafariLocation.biome();

		HudPanel panel = panelCache.get(() -> buildPanel(biome, SessionManager.currentOrLast()));
		HudBox box = HudBox.MISSING;
		float scale = box.scale() * ResponsiveUI.scale(graphics.guiWidth(), graphics.guiHeight());
		int x = box.pixelX(graphics.guiWidth(), panel, client.font, scale);
		int y = box.pixelY(graphics.guiHeight(), panel, scale);
		if (SparklingWatch.missingHudThemeActive()) {
			panel.renderRainbow(graphics, client.font, x, y, scale);
		} else if (SparklingMode.enabled()) {
			panel.renderRainbowBorder(graphics, client.font, x, y, scale);
		} else {
			panel.render(graphics, client.font, x, y, scale,
				HudBorderStyle.missing(biome, SessionManager.currentOrLast()));
		}
	}

	/** Builds the list for {@code biome}; {@code session} may be null before the first catch. */
	static HudPanel buildPanel(SafariBiome biome, SafariSession session) {
		if (SparklingMode.enabled()) return buildSparklingModePanel(biome, SessionManager.current());
		// Before the first catch there is no session yet, but standing in a biome with
		// nothing caught is exactly when the full list is most useful — so fall back
		// to the whole roster rather than hiding the panel.
		//
		// Gimmiegold is always kept in, regardless of session.missing()'s usual
		// caught-once-and-done judgement: its line tracks coins found against
		// Gimmiegolds caught, not a one-and-done catch, so it needs to stay in its
		// normal rarity-ordered spot in the list rather than falling out into "also
		// here" the moment the first one is caught.
		List<Critter> shown = session == null ? Critters.inBiome(biome)
			: Critters.inBiome(biome).stream()
				.filter(c -> !ConfigManager.get().display.missingUniqueOnly
					|| !session.caughtByParty(c))
				.filter(c -> !c.hasQuota() || !session.isComplete(c))
				.toList();

		HudPanel panel = new HudPanel();

		// Gimmiegold never counts toward "how many left" or "all caught", and its row
		// is built and shown separately below rather than inside the missing loop —
		// its ratio is an ongoing thing with no real finish line, not a species that
		// becomes done. Left in the title count, a Haunted biome could never reach
		// "all caught" at all; left inside the missing loop only, its row would vanish
		// the moment every real species in the biome actually was caught, even with
		// coins still on the books.
		long uniqueMissing = session == null ? Critters.totalIn(biome)
			: Critters.inBiome(biome).stream()
				.filter(c -> !session.caughtByParty(c) && !session.isUnavailable(c)).count();

		// Whether Gimmiegold's own row would still have anything worth showing —
		// mirrors the same check appendGimmiegold makes below, kept separate so
		// "fully cleared" can be decided before that row is actually built.
		boolean gimmiegoldStillShowing = shown.contains(GIMMIEGOLD)
			&& gimmiegoldWouldShow(session);

		String biomeName = biome.displayName();
		// The title describes the unique checklist, not quota cleanup such as 4/4 Gazers.
		boolean uniquesDone = session != null && uniqueMissing == 0;
		if (uniquesDone) {
			panel.titleSuffix(biomeName + " ", "(Uniques Done)",
				HudBorderStyle.missingTitle(biome, session), DONE);
		} else {
			panel.titleSuffix(biomeName + " ", "(%d Left)".formatted(uniqueMissing),
				HudBorderStyle.missingTitle(biome, session),
				HudBorderStyle.missingTitle(biome, session));
		}
		if (appendSparklingEntries(panel, biome)) panel.blank();
		if (!shown.isEmpty()) {
			// Gimmiegold sits wherever its own rarity
			// places it alphabetically among the rest — Rare, so between the Uncommon
			// tier and Hideonwall/Hideyho.
			for (Critter critter : shown) {
				if (GIMMIEGOLD.equals(critter)) {
					if (gimmiegoldStillShowing) appendGimmiegold(panel, session);
					continue;
				}
				// A quota species shows how many of its spawns are already taken,
				// since "caught one" is not the same as "done with it".
				int caught = session == null ? 0 : session.partyCatches(critter);
				int total = session == null ? 0 : session.required(critter);
				int near = nearby(session, critter);
				String note;
				if ("Hideyho".equals(critter.name()) && hideyhoHiding()) note = "Hiding";
				else if (near > 0) note = near + " Near";
				else if (total > 1 && !TrackingMode.uniqueOnly()) note = caught + "/" + total;
				else note = ConfigManager.get().display.showTotalCatches
					? caught + " Caught" : "";
				panel.statusPair(session != null && session.caughtByParty(critter),
					critter.name(), note, ProgressHud.rarityColour(critter), DIM);
			}
		}
		// Anything the run can no longer produce is stated outright rather than just
		// dropping off the list, so its absence does not look like a tracking bug.
		if (session != null) {
			List<Critter> gone = Critters.inBiome(biome).stream()
				.filter(session::isUnavailable)
				.toList();
			if (!gone.isEmpty()) {
				panel.blank();
				for (Critter critter : gone) {
					panel.pair(critter.name(), "None This Run", DIM, DIM);
				}
			}
		}

		boolean groupStarted = false;
		groupStarted |= appendFloorDropCount(panel, biome, !groupStarted);
		groupStarted |= appendMounds(panel, biome, !groupStarted);
		groupStarted |= appendWalls(panel, biome, WallTracker.SNOOPER,
			ConfigManager.get().display.showSnooperWalls,
			Colours.argb(ConfigManager.get().display.snooperWallColour, 0xFFFFC800), !groupStarted);
		groupStarted |= appendWalls(panel, biome, WallTracker.TROODON,
			ConfigManager.get().display.showTroodonWalls,
			Colours.argb(ConfigManager.get().display.troodonWallColour, 0xFF55AAFF), !groupStarted);
		groupStarted |= appendNests(panel, biome, !groupStarted);
		groupStarted |= appendBirdFeed(panel, biome, !groupStarted);

		return panel.trimTrailingBlanks();
	}

	/** Sparkling-search view: Sparkling-needed species, then shared species needing only a unique. */
	private static HudPanel buildSparklingModePanel(SafariBiome biome, SafariSession session) {
		HudPanel panel = new HudPanel();
		panel.sparklingModeTitle(biome.displayName(), HudBorderStyle.missingTitle(biome, session));
		if (appendSparklingEntries(panel, biome)) panel.blank();

		boolean nonSharedShown = false;
		for (Critter critter : Critters.inBiome(biome)) {
			if (SparklingMode.isShared(critter)) continue;
			boolean caught = session != null && session.caughtByParty(critter);
			if (ConfigManager.get().display.missingUniqueOnly && caught) continue;
			boolean complete = session != null && session.isComplete(critter);
			String note = sparklingModeNear(session, critter);
			panel.statusPair(complete, critter.name(), note, ProgressHud.rarityColour(critter), DIM);
			nonSharedShown = true;
		}

		boolean sharedShown = false;
		for (Critter critter : Critters.inBiome(biome)) {
			if (!SparklingMode.isShared(critter)) continue;
			if (SparklingMode.ignoreUniques()) continue;
			if (session != null && session.caughtByParty(critter)) continue;
			if (!sharedShown && nonSharedShown) panel.blank();
			panel.statusPair(false, critter.name(), sparklingModeNear(session, critter),
				ProgressHud.rarityColour(critter), DIM);
			sharedShown = true;
		}

		boolean speciesShown = nonSharedShown || sharedShown;
		boolean groupStarted = false;
		groupStarted |= appendFloorDropCount(panel, biome, speciesShown && !groupStarted);
		groupStarted |= appendMounds(panel, biome, speciesShown && !groupStarted);
		groupStarted |= appendWalls(panel, biome, WallTracker.SNOOPER,
			ConfigManager.get().display.showSnooperWalls,
			Colours.argb(ConfigManager.get().display.snooperWallColour, 0xFFFFC800), speciesShown && !groupStarted);
		groupStarted |= appendWalls(panel, biome, WallTracker.TROODON,
			ConfigManager.get().display.showTroodonWalls,
			Colours.argb(ConfigManager.get().display.troodonWallColour, 0xFF55AAFF), speciesShown && !groupStarted);
		groupStarted |= appendNests(panel, biome, speciesShown && !groupStarted);
		appendBirdFeed(panel, biome, speciesShown && !groupStarted);
		return panel.trimTrailingBlanks();
	}

	private static String sparklingModeNear(SafariSession session, Critter critter) {
		if ("Hideyho".equals(critter.name()) && HideyhoSolver.live()) return "1 Near";
		if ("Hideyho".equals(critter.name()) && hideyhoHiding()) return "Hiding";
		int near = nearby(session, critter);
		return near > 0 ? near + " Near" : "";
	}

	/** Safe Mode exposes only visibility-confirmed counts; other modes use the loaded tally. */
	private static int nearby(SafariSession session, Critter critter) {
		if (session == null || !ConfigManager.get().display.countSpawns) return 0;
		return SafeMode.nearbyCounts()
			? DetectedCritters.currentConcurrent(critter) : session.nearby(critter);
	}

	private static boolean hideyhoHiding() {
		return HideyhoSolver.phase() == HideyhoSolver.Phase.PENDING
			|| HideyhoSolver.phase() == HideyhoSolver.Phase.END;
	}

	/** Adds retained, uncaught Sparkling detections above the ordinary species rows. */
	private static boolean appendSparklingEntries(HudPanel panel, SafariBiome biome) {
		java.util.Map<Critter, Integer> counts = SparklingWatch.outstandingCounts(biome);
		counts.forEach((critter, count) -> panel.rainbowPair(
			"SPARKLING " + critter.name(), count + " Near"));
		return !counts.isEmpty();
	}

	private static boolean gimmiegoldWouldShow(SafariSession session) {
		if (session == null) return true;
		if (session.nearby(GIMMIEGOLD) > 0) return true;
		if (SafariObjectives.shiningCoinsHeld() > 0) return true;
		if (session.partyCatches(GIMMIEGOLD) > session.ownCatches(GIMMIEGOLD)) return false;
		int coins = ShiningCoinWatch.found();
		int caught = session.ownCatches(GIMMIEGOLD);
		return coins == 0 || caught < coins;
	}

	/**
	 * Gimmiegold's own row, shown whenever it is worth showing per
	 * {@link #gimmiegoldWouldShow} — independent of whatever the title branch
	 * decided, since its ratio keeps being worth showing after every other species in
	 * the biome is done, right up until it genuinely has nothing left to say.
	 */
	private static void appendGimmiegold(HudPanel panel, SafariSession session) {
		// Same near-beats-everything priority as any other critter: once coins are
		// thrown in and one actually spawns, that matters more than the ratio, exactly
		// as Gazer's quota gives way to "X near".
		int near = nearby(session, GIMMIEGOLD);
		String note;
		if (near > 0) {
			note = near + " Near";
		} else {
			// Coins found this run against Gimmiegolds you personally caught, not
			// party-wide: a partymate's coin finds are not something this client can
			// see. A personal catch can still outpace a personal coin count, though —
			// spawning one at all appears to draw on the whole party's coins, so
			// catching one someone else's coin produced is entirely real and not a
			// counting error. 0/? until the first coin is found — the denominator is
			// not zero, it is simply not known yet.
			int coins = ShiningCoinWatch.found();
			int caught = session == null ? 0 : session.ownCatches(GIMMIEGOLD);
			note = caught + "/" + (coins == 0 ? "?" : String.valueOf(coins));
		}
		panel.statusPair(session != null && session.caughtByParty(GIMMIEGOLD),
			GIMMIEGOLD.name(), note, ProgressHud.rarityColour(GIMMIEGOLD), DIM);
	}

	/**
	 * Counts the mounds still standing near you.
	 *
	 * <p>The count is of mounds detected around you, not of mounds in the Cavern — they
	 * are found by their hitbox within scanning range — so it is left out entirely when
	 * none are in range rather than shown as a zero that would read as "none left".
	 */
	private static boolean appendMounds(HudPanel panel, SafariBiome biome, boolean needsLeadingBlank) {
		if (biome != SafariBiome.CAVERN) return false;
		if (SparklingMode.hideMounds(SessionManager.current())) return false;
		if (!ConfigManager.get().display.showMoundCount) return false;

		int nearby = MoundSpotter.mounds().size();
		if (nearby == 0) return false;

		if (needsLeadingBlank) panel.blank();
		int colour = Colours.argb(ConfigManager.get().display.moundColour, 0xFFFF5E2C);
		panel.pair("Rockmite Mounds Left", String.valueOf(nearby), colour, DIM);
		return true;
	}

	/** Whether Snoozle/Troodon walls (whichever {@code tracker} is) still have anything standing. */
	private static boolean wallsWouldShow(SafariBiome biome, WallTracker tracker, boolean show) {
		if (biome != tracker.biome()) return false;
		List<WallTracker.Wall> walls = tracker.walls();
		if (walls.isEmpty()) return false;
		// Checked regardless of the show toggle, unlike the row itself below — a
		// biome is not actually cleared just because its wall display happens to be
		// switched off; the walls are still standing either way.
		if (tracker == WallTracker.SNOOPER ? SafeMode.snoozleWalls() : SafeMode.troodonWalls()) {
			return walls.stream().anyMatch(w -> w.state() == WallTracker.State.INTACT);
		}
		return walls.stream().anyMatch(w -> w.state() != WallTracker.State.BROKEN);
	}

	/** Whether Forest's bee nests still have anything unpunched. */
	private static boolean nestsWouldShow(SafariBiome biome) {
		if (biome != SafariBiome.FOREST) return false;
		return NestTracker.nests().stream().anyMatch(NestTracker.Nest::unpunched);
	}

	private static boolean appendNests(HudPanel panel, SafariBiome biome, boolean needsLeadingBlank) {
		if (biome != SafariBiome.FOREST) return false;
		if (SparklingMode.hideNests(SessionManager.current())) return false;
		if (!ConfigManager.get().display.showNests) return false;

		List<NestTracker.Nest> nests = NestTracker.nests();
		if (nests.isEmpty()) return false;

		// Safe Mode includes every unresolved candidate, matching its rendered waypoints.
		long unpunched = nests.stream()
			.filter(NestTracker.Nest::unpunched)
			.count();
		// Remove the row once no nest remains.
		if (unpunched == 0) return false;

		if (needsLeadingBlank) panel.blank();
		// Just the count: the waypoints know where they are, and a list of coordinates
		// was only ever a way of getting there without them.
		int colour = Colours.argb(ConfigManager.get().display.nestColour, 0xFFFFE840);
		panel.pair("Bee Nests Left", String.valueOf(unpunched), colour, DIM);
		return true;
	}

	/**
	 * Feed found this run against how many spawn events it has already produced —
	 * decrements per event, not per bird, since a Macaw event spends one feed the same
	 * as any other despite producing two of them.
	 */
	private static boolean appendBirdFeed(HudPanel panel, SafariBiome biome, boolean needsLeadingBlank) {
		if (biome != SafariBiome.FOREST) return false;
		if (SparklingMode.hideBirdFeed(SessionManager.current())) return false;
		if (!ConfigManager.get().display.showBirdFeedCount) return false;

		int remaining = BirdfeederWatch.remaining();
		if (remaining == 0) return false;

		if (needsLeadingBlank) panel.blank();
		// Same colour as floor drops, for now — its own colour setting can follow if
		// that turns out to want distinguishing from an actual floor drop at a glance.
		int colour = Colours.argb(ConfigManager.get().display.floorDropColour, 0xFF55FFAA);
		panel.pair("Bird Feed Left", String.valueOf(remaining), colour, DIM);
		return true;
	}

	/**
	 * A start-count-and-count-down for floor drops in this biome — everSeen() against
	 * remaining(), not a live scan, since a live scan only ever shows what happens to
	 * be loaded nearby right now and was never going to read as a real total.
	 * Nothing shown until at least one has actually been seen this run, so a biome
	 * just entered does not flash a misleading "0 left" before any are found at all.
	 */
	private static boolean appendFloorDropCount(HudPanel panel, SafariBiome biome, boolean needsLeadingBlank) {
		if (!ConfigManager.get().display.showFloorDropCount) return false;
		if (SparklingMode.hideFloorDrops(biome, SessionManager.current())) return false;
		// Hide a completed countdown. Safe Mode uses confirmed drops so background
		// scanning cannot reveal how many unseen drops exist.
		int remaining = SafeMode.floorDrops()
			? FloorDrops.positions(biome).size() : FloorDrops.remainingConfirmed(biome);
		if (remaining == 0) return false;

		if (needsLeadingBlank) panel.blank();
		int labelColour = Colours.argb(ConfigManager.get().display.floorDropColour, 0xFF55FFAA);
		panel.pair("Floor Drops Left", String.valueOf(remaining), labelColour, DIM);
		return true;
	}

	private static boolean appendWalls(HudPanel panel, SafariBiome biome, WallTracker tracker,
										boolean show, int colour, boolean needsLeadingBlank) {
		if (biome != tracker.biome() || !show) return false;
		if (tracker == WallTracker.SNOOPER
			&& SparklingMode.hideSnoozleWalls(SessionManager.current())) return false;
		if (tracker == WallTracker.TROODON
			&& SparklingMode.hideTroodonWalls(SessionManager.current())) return false;

		List<WallTracker.Wall> walls = tracker.walls();
		if (walls.isEmpty()) return false;

		long intact = walls.stream().filter(w -> w.state() == WallTracker.State.INTACT).count();
		long unknown = walls.stream().filter(w -> w.state() == WallTracker.State.UNKNOWN).count();
		boolean safeMode = tracker == WallTracker.SNOOPER
			? SafeMode.snoozleWalls() : SafeMode.troodonWalls();
		// Dropped entirely once there is nothing left to break, rather than a lingering
		// "all broken" line — the row's whole point was a countdown, and once it is
		// actually done there is nothing left worth taking up space over.
		if (intact == 0 && unknown == 0) return false;

		if (needsLeadingBlank) panel.blank();
		panel.pair(tracker.name() + " Walls Left", String.valueOf(
			safeMode ? intact + unknown : intact) + (!safeMode && unknown > 0
			? " (+" + unknown + "?)" : ""), colour, DIM);
		return true;
	}
}
