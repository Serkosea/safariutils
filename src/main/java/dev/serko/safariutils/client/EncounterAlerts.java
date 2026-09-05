package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.session.TrackingMode;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Shows encounter and completion banners, sounds, and optional chat notices. Gemzie,
 * Wumpa and Doomspiral progress through ready, started and done stages; cooldowns
 * suppress repeated signals for the same stage.
 */
public final class EncounterAlerts implements HudElement {

	private static final long STAGE_COOLDOWN_MILLIS = 20_000;

	private static final int READY_COLOUR = 0xFFFFAA00;
	private static final int STARTED_COLOUR = 0xFFFF5555;
	private static final int DONE_COLOUR = 0xFF55FF55;

	/** Exactly this many Gemzies spawn each time the chamber opens. */
	private static final int GEMZIE_PER_CHAMBER = 3;

	private static final Map<String, Long> lastFired = new HashMap<>();

	/** Gemzies still to catch in the open chamber; 0 when no chamber is active. */
	private static int gemzieRemaining;

	/** True only while the test command is firing, so the biome gate cannot swallow it. */
	private static boolean testing;

	private static String message;
	private static int colour;
	private static long shownAtMillis;
	private static long displayMillis = 3000;
	private static float displayedScale = 4f;
	private static float displayedHorizontalPosition = 0.5f;
	private static float displayedVerticalPosition = 0.4f;
	private static boolean rainbowMessage;

	public enum Stage {READY, STARTED, DONE}
	public enum Preview {FULL_PARTY, HOTSPOT, FLOOR_DROPS, BIOME_UNIQUES, ALL_BUT_MACAW, ALL_DONE,
		GEMZIE_READY, GEMZIE_DONE,
		WUMPA_READY, WUMPA_STARTED, WUMPA_DONE, DOOM_READY, DOOM_STARTED, DOOM_DONE,
		HIDEYHO, MACAW, BIRDS, FEED_GONE, BIRDFEEDER_EMPTY,
		START, FIVE_MINUTES, ONE_MINUTE, ENDED, TICKET}

	/**
	 * Runs {@code test} with every gate that depends on where the player is standing
	 * lifted, so {@code /su debug testalert} shows all three encounters wherever it is
	 * run from rather than only the one whose biome you happen to be in.
	 */
	public static void whileTesting(Runnable test) {
		testing = true;
		try {
			test.run();
		} finally {
			testing = false;
		}
	}

	/**
	 * Reacts to a cleaned chat line.
	 *
	 * @return true if the line was an encounter announcement
	 */
	public static boolean onChatMessage(String line) {
		// --- Gemzie ---
		if (line.startsWith("A rumbling sound can be heard")) {
			// Exactly three Gemzies spawn per chamber, so the encounter is over once
			// three have been caught by anyone rather than on any message.
			Critter gemzie = Critters.byName("Gemzie");
			gemzieRemaining = gemzie == null ? GEMZIE_PER_CHAMBER : TrackingMode.required(gemzie);
			fire("Gemzie", Stage.READY, "chamber open, " + gemzieRemaining + " to catch", 1.4f);
			return true;
		}

		// --- Wumpa ---
		if (line.startsWith("You hear the sound of massive footsteps")) {
			fire("Wumpa", Stage.READY, "it wakes in ~30s", 1.4f);
			return true;
		}
		if (line.startsWith("The Wumpa has awoken")) {
			fire("Wumpa", Stage.STARTED, "fight is live", 1.8f);
			return true;
		}
		if (line.startsWith("The cave opens up again")) {
			fire("Wumpa", Stage.DONE, "the cave has reopened", 1.2f);
			return true;
		}

		// --- Doomspiral ---
		if (line.startsWith("You used the Soothing Incense to light the candle")) {
			fire("Doomspiral", Stage.READY, "ritual underway", 1.4f);
			return true;
		}
		if (line.startsWith("Your ritual summoned a Doomspiral")) {
			fire("Doomspiral", Stage.STARTED, "fight is live", 1.8f);
			return true;
		}
		if (line.startsWith("The Doomspiral retreats back underground")) {
			fire("Doomspiral", Stage.DONE, "it retreated", 1.2f);
			return true;
		}

		return false;
	}

	/**
	 * Called for every catch this run, by anyone.
	 *
	 * <p>Wumpa and Doomspiral each end when caught. Gemzie has no end message at all —
	 * exactly three spawn per chamber, so the count is what closes it.
	 */
	public static void onCatch(String critterName) {
		switch (critterName) {
			case "Wumpa", "Doomspiral" -> fire(critterName, Stage.DONE, "caught", 1.2f);
			case "Gemzie" -> {
				if (gemzieRemaining <= 0) return;
				if (--gemzieRemaining == 0) {
					fire("Gemzie", Stage.DONE, "all caught", 1.2f);
				}
			}
			default -> {
			}
		}
	}

	/** Every species in {@code biome} has now been caught by someone this run. */
	public static void onBiomeComplete(SafariBiome biome) {
		SafariConfig config = ConfigManager.get();
		if (config.alerts.biomeUniquesDoneSoundMode == 0
			&& config.party.biomeDone() == SafariConfig.Broadcast.NONE) return;
		if (onCooldown("biome:" + biome.name())) return;

		SafariConfig.AlertConfig alerts = config.alerts;
		int biomeColour = 0xFF000000 | biome.colour();
		if ((alerts.biomeUniquesDoneSoundMode != 0)) {
			banner(AlertText.format(alerts.biomeUniquesDoneText, "<BIOME>", biome.displayName()),
				alerts.biomeUniquesDoneUseBiomeColour ? biomeColour
					: Colours.argb(alerts.biomeUniquesDoneColour, DONE_COLOUR),
				alerts.biomeUniquesDoneSoundPitch, alerts.biomeUniquesDoneDuration,
				alerts.biomeUniquesDoneSoundMode, alerts.biomeUniquesDoneSoundChoice,
				alerts.biomeUniquesDoneSoundVolume, alerts.biomeUniquesDoneScale,
				alerts.biomeUniquesDoneVerticalPosition);
		}
		if (config.party.biomeDone() != SafariConfig.Broadcast.NONE) {
			String chatText = AlertText.format(config.party.biomeDoneChatText,
				"<BIOME>", biome.displayName());
			post(config.party.biomeDone(), chatText);
		}
	}

	/** Everything but the Macaw is caught — usually the real finish line for a run. */
	public static void onAllButMacaw() {
		SafariConfig config = ConfigManager.get();
		SafariConfig.AlertConfig alerts = config.alerts;
		if ((alerts.allButMacawDoneSoundMode != 0)) {
			banner(alerts.allButMacawDoneText,
				Colours.argb(alerts.allButMacawDoneColour, DONE_COLOUR),
				alerts.allButMacawDoneSoundPitch, alerts.allButMacawDoneDuration,
				alerts.allButMacawDoneSoundMode, alerts.allButMacawDoneSoundChoice,
				alerts.allButMacawDoneSoundVolume, alerts.allButMacawDoneScale,
				alerts.allButMacawDoneVerticalPosition);
		}
		post(config.party.allButMacaw(), config.party.allButMacawChatText);
	}

	/**
	 * A Macaw has turned up.
	 *
	 * <p>Not a staged encounter like the bosses, so it says the one thing there is to
	 * say. {@code where} is the spot to send people to, or null when the Birdfeeder
	 * announced it from out of range.
	 */
	public static void onMacawSpawn() {
		SafariConfig config = ConfigManager.get();
		if (birdBannerAllowed() && config.alerts.macawSoundMode != 0) {
			Critter macaw = Critters.byName("Macaw");
			int rarityColour = 0xFF000000 | (macaw == null ? 0xFFAA00 : macaw.rarity().colour());
			banner(config.alerts.macawText,
				config.alerts.macawUseRarityColour ? rarityColour
					: Colours.argb(config.alerts.macawAlertColour, rarityColour),
				config.alerts.macawSoundPitch,
				config.alerts.macawDuration, config.alerts.macawSoundMode,
				config.alerts.macawSoundChoice, config.alerts.macawSoundVolume,
				config.alerts.macawScale, config.alerts.macawVerticalPosition);
		}

		if (birdChatAllowed()) post(config.party.macaw(), config.party.macawChatText);
	}

	/** All 37 caught by someone. */
	public static void onAllDone() {
		SafariConfig config = ConfigManager.get();
		SafariConfig.AlertConfig alerts = config.alerts;
		if ((alerts.allUniquesDoneSoundMode != 0)) {
			banner(alerts.allUniquesDoneText,
				Colours.argb(alerts.allUniquesDoneColour, DONE_COLOUR),
				alerts.allUniquesDoneSoundPitch, alerts.allUniquesDoneDuration,
				alerts.allUniquesDoneSoundMode, alerts.allUniquesDoneSoundChoice,
				alerts.allUniquesDoneSoundVolume, alerts.allUniquesDoneScale,
				alerts.allUniquesDoneVerticalPosition);
		}
		post(config.party.allDone(), config.party.allDoneChatText);
	}

	/**
	 * Fires when {@link HideyhoSolver} newly confirms a position — reveal or a fresh
	 * spot after a re-hide — coloured to match its waypoint rather than a fixed stage
	 * colour, since this is not a ready/started/done progression.
	 */
	static void fireHideyho() {
		SafariConfig.AlertConfig alerts = ConfigManager.get().alerts;
		if (alerts.hideyhoSoundMode == 0) return;
		banner(alerts.hideyhoText, Colours.argb(alerts.hideyhoAlertColour, 0xFFFF55FF),
			alerts.hideyhoSoundPitch, alerts.hideyhoDuration, alerts.hideyhoSoundMode,
			alerts.hideyhoSoundChoice, alerts.hideyhoSoundVolume,
			alerts.hideyhoScale, alerts.hideyhoVerticalPosition);
	}

	/**
	 * Fires when every known floor drop in a biome has been collected — see
	 * {@link FloorDrops} for the count and the once-per-biome-per-run gating, since
	 * that class already has to track the count anyway and is the natural place to
	 * decide when it hits zero.
	 */
	static void fireFloorDropsDone(SafariBiome biome) {
		// Normal mode may scan other biomes, but completion only matters locally.
		if (SafariLocation.biome() != biome) return;
		if (ConfigManager.get().alerts.floorDropsDoneSoundMode == 0) return;
		SafariConfig.AlertConfig alerts = ConfigManager.get().alerts;
		int biomeColour = 0xFF000000 | biome.colour();
		banner(AlertText.format(alerts.floorDropsDoneText, "<BIOME>", biome.displayName()),
			alerts.floorDropsDoneUseBiomeColour ? biomeColour
				: Colours.argb(alerts.floorDropsDoneAlertColour, 0xFF55FFAA),
			alerts.floorDropsDoneSoundPitch,
			alerts.floorDropsDoneDuration, alerts.floorDropsDoneSoundMode,
			alerts.floorDropsDoneSoundChoice, alerts.floorDropsDoneSoundVolume,
			alerts.floorDropsDoneScale, alerts.floorDropsDoneVerticalPosition);
	}

	/** Announces the run's personal hotspot using that biome's HUD colour. */
	static void fireHotspot(SafariBiome biome) {
		SafariConfig.AlertConfig alerts = ConfigManager.get().alerts;
		if (alerts.hotspotSoundMode == 0) return;
		int biomeColour = 0xFF000000 | biome.colour();
		banner(AlertText.format(alerts.hotspotText, "<BIOME>", biome.displayName()),
			alerts.hotspotUseBiomeColour ? biomeColour
				: Colours.argb(alerts.hotspotAlertColour, 0xFFFF55FF),
			alerts.hotspotSoundPitch, alerts.hotspotDuration, alerts.hotspotSoundMode,
			alerts.hotspotSoundChoice, alerts.hotspotSoundVolume,
			alerts.hotspotScale, alerts.hotspotVerticalPosition);
	}

	/** Announces that every member of the known party reached this Safari instance. */
	static void fireFullPartyJoined(int players) {
		SafariConfig config = ConfigManager.get();
		SafariConfig.AlertConfig alerts = config.alerts;
		if ((alerts.fullPartyJoinedSoundMode != 0)) {
			banner(AlertText.format(alerts.fullPartyJoinedText,
				"<PLAYERS>", String.valueOf(players), "<MAX>", String.valueOf(players)), Colours.argb(alerts.fullPartyJoinedColour, 0xFF55FF55),
				alerts.fullPartyJoinedSoundPitch, alerts.fullPartyJoinedDuration,
				alerts.fullPartyJoinedSoundMode, alerts.fullPartyJoinedSoundChoice,
				alerts.fullPartyJoinedSoundVolume, alerts.fullPartyJoinedScale,
				alerts.fullPartyJoinedVerticalPosition);
		}
		post(config.party.fullPartyJoined(), AlertText.format(config.party.fullPartyJoinedChatText,
			"<PLAYERS>", String.valueOf(players), "<MAX>", String.valueOf(players)));
	}

	/** Fires one of Miria's real-time contest alerts. */
	static void fireContestAlert(ContestTracker.Alert alert) {
		SafariConfig settings = ConfigManager.get();
		SafariConfig.AlertConfig config = settings.alerts;
		boolean bannerWarnings = ContestTracker.ticketEarned() && config.contestNoWarningsAfterTicket;
		boolean chatWarnings = ContestTracker.ticketEarned() && settings.party.contestNoWarningsAfterTicket;
		switch (alert) {
			case START -> {
				if ((config.contestStartSoundMode != 0)) banner(config.contestStartText,
					Colours.argb(config.contestStartColour, 0xFF55FF55), config.contestStartSoundPitch,
					config.contestStartDuration, config.contestStartSoundMode,
					config.contestStartSoundChoice, config.contestStartSoundVolume,
					config.contestStartScale, config.contestStartVerticalPosition);
				post(settings.party.contestStart(), settings.party.contestStartChatText);
			}
			case FIVE_MINUTES -> {
				if (!bannerWarnings && config.contestFiveMinuteSoundMode != 0) banner(config.contestFiveMinuteText,
					Colours.argb(config.contestFiveMinuteColour, 0xFFFFFF55), config.contestFiveMinuteSoundPitch,
					config.contestFiveMinuteDuration, config.contestFiveMinuteSoundMode,
					config.contestFiveMinuteSoundChoice, config.contestFiveMinuteSoundVolume,
					config.contestFiveMinuteScale, config.contestFiveMinuteVerticalPosition);
				if (!chatWarnings) post(settings.party.contestFiveMinute(), settings.party.contestFiveMinuteChatText);
			}
			case ONE_MINUTE -> {
				if (!bannerWarnings && config.contestOneMinuteSoundMode != 0) banner(config.contestOneMinuteText,
					Colours.argb(config.contestOneMinuteColour, 0xFFFFAA00), config.contestOneMinuteSoundPitch,
					config.contestOneMinuteDuration, config.contestOneMinuteSoundMode,
					config.contestOneMinuteSoundChoice, config.contestOneMinuteSoundVolume,
					config.contestOneMinuteScale, config.contestOneMinuteVerticalPosition);
			}
			case ENDED -> {
				if ((config.contestEndedSoundMode != 0)) banner(config.contestEndedText,
					Colours.argb(config.contestEndedColour, 0xFFFF5555), config.contestEndedSoundPitch,
					config.contestEndedDuration, config.contestEndedSoundMode,
					config.contestEndedSoundChoice, config.contestEndedSoundVolume,
					config.contestEndedScale, config.contestEndedVerticalPosition);
				post(settings.party.contestEnded(), settings.party.contestEndedChatText);
			}
			case TICKET_EARNED -> {
				if ((config.contestTicketEarnedSoundMode != 0)) banner(config.contestTicketEarnedText,
					Colours.argb(config.contestTicketEarnedColour, 0xFF55FF55), config.contestTicketEarnedSoundPitch,
					config.contestTicketEarnedDuration, config.contestTicketEarnedSoundMode,
					config.contestTicketEarnedSoundChoice, config.contestTicketEarnedSoundVolume,
					config.contestTicketEarnedScale, config.contestTicketEarnedVerticalPosition);
				post(settings.party.contestTicketEarned(), settings.party.contestTicketEarnedChatText);
			}
		}
	}

	/**
	 * Bluebird or Parakeet, attracted to the Birdfeeder — a banner, the same as Macaw
	 * gets from {@link #onMacawSpawn}. The full-screen call is kept for a sparkling
	 * specifically now, since that is the one thing worth the bigger interruption.
	 * Its own rarity colour, so a Bluebird cannot be mistaken for the thing you were
	 * actually waiting for.
	 */
	static void fireAllBirdSpawn(Critter bird) {
		SafariConfig config = ConfigManager.get();
		SafariConfig.AlertConfig alerts = config.alerts;
		if (birdBannerAllowed() && alerts.birdfeederSoundMode != 0) {
			int rarityColour = 0xFF000000 | bird.rarity().colour();
			String chosen = bird.name().equals("Parakeet")
				? alerts.parakeetAlertColour : alerts.bluebirdAlertColour;
			banner(AlertText.format(alerts.birdfeederText, "<CRITTER>", bird.name()), alerts.birdfeederUseRarityColour
					? rarityColour : Colours.argb(chosen, rarityColour),
				alerts.birdfeederSoundPitch,
				alerts.birdfeederDuration, alerts.birdfeederSoundMode,
				alerts.birdfeederSoundChoice, alerts.birdfeederSoundVolume,
				alerts.birdfeederScale, alerts.birdfeederVerticalPosition);
		}
		if (birdChatAllowed()) post(config.party.allBirds(), AlertText.format(config.party.allBirdsChatText,
			"<CRITTER>", bird.name()));
	}

	/** Reports the completed Forest's live feed inventory through its selected chat. */
	static void onTotalFeed(int seeds, int worms, int berries) {
		SafariConfig.PartyConfig party = ConfigManager.get().party;
		if (birdChatAllowed()) post(party.totalFeed(), AlertText.format(party.totalFeedChatText,
			"<ALL_FEED>", formatFeedList(seeds, worms, berries),
			"<TOTAL>", String.valueOf(seeds + worms + berries),
			"<SEEDS>", String.valueOf(seeds),
			"<WORMS>", String.valueOf(worms),
			"<BERRIES>", String.valueOf(berries)));
	}

	private static String formatFeedList(int seeds, int worms, int berries) {
		java.util.List<String> feed = new java.util.ArrayList<>(3);
		if (seeds > 0) feed.add(seeds + (seeds == 1 ? " Seed" : " Seeds"));
		if (worms > 0) feed.add(worms + (worms == 1 ? " Worm" : " Worms"));
		if (berries > 0) feed.add(berries + (berries == 1 ? " Berry" : " Berries"));
		return feed.isEmpty() ? "No Feed" : String.join(", ", feed);
	}

	/** Announces that every collected feed item has been placed in the Birdfeeder. */
	static void onFeedGone() {
		SafariConfig config = ConfigManager.get();
		SafariConfig.AlertConfig alerts = config.alerts;
		if (birdBannerAllowed() && alerts.feedGoneSoundMode != 0) {
			banner(alerts.feedGoneText, Colours.argb(alerts.feedGoneColour, 0xFFFF5555),
				alerts.feedGoneSoundPitch, alerts.feedGoneDuration, alerts.feedGoneSoundMode,
				alerts.feedGoneSoundChoice, alerts.feedGoneSoundVolume,
				alerts.feedGoneScale, alerts.feedGoneVerticalPosition);
		}
		if (birdChatAllowed()) post(config.party.feedGone(), config.party.feedGoneChatText);
	}

	/** Called only after the open feeder changes from containing feed to empty. */
	static void onBirdfeederEmpty() {
		SafariConfig.AlertConfig a = ConfigManager.get().alerts;
		if (!birdBannerAllowed()) return;
		banner(a.birdfeederEmptyText, Colours.argb(a.birdfeederEmptyColour, 0xFFFF5555),
			a.birdfeederEmptySoundPitch, a.birdfeederEmptyDuration, a.birdfeederEmptySoundMode,
			a.birdfeederEmptySoundChoice, a.birdfeederEmptySoundVolume,
			a.birdfeederEmptyScale, a.birdfeederEmptyVerticalPosition);
	}

	private static void fire(String boss, Stage stage, String detail, float pitch) {
		// Gemzie chambers repeat every few minutes and its ready/done pair can be
		// seconds apart, so the anti-repeat cooldown must not apply to it.
		if (!boss.equals("Gemzie") && onCooldown(boss + ":" + stage)) return;

		String text = encounterBannerText(boss, stage);
		if (alertsOn(boss, stage) && inItsBiome(boss)) {
			banner(text, encounterColour(boss, stage), encounterPitch(boss, stage),
				encounterDuration(boss, stage), encounterPlayback(boss, stage),
				encounterSoundChoice(boss, stage), encounterVolume(boss, stage),
				encounterScale(boss, stage), encounterVerticalPosition(boss, stage));
		}

		if (chatInItsBiome(boss)) post(broadcastFor(boss), encounterChatText(boss, stage));
	}

	private static int encounterColour(String boss, Stage stage) {
		SafariConfig.AlertConfig alerts = ConfigManager.get().alerts;
		return switch (boss) {
			case "Gemzie" -> stage == Stage.READY
				? Colours.argb(alerts.gemzieReadyColour, READY_COLOUR)
				: Colours.argb(alerts.gemzieDoneColour, DONE_COLOUR);
			case "Wumpa" -> stageColour(stage, alerts.wumpaReadyColour,
				alerts.wumpaStartedColour, alerts.wumpaDoneColour);
			case "Doomspiral" -> stageColour(stage, alerts.doomspiralReadyColour,
				alerts.doomspiralStartedColour, alerts.doomspiralDoneColour);
			default -> DONE_COLOUR;
		};
	}

	private static String encounterBannerText(String boss, Stage stage) {
		SafariConfig.AlertConfig a = ConfigManager.get().alerts;
		return switch (boss) {
			case "Gemzie" -> stage == Stage.READY ? a.gemzieReadyText : a.gemzieDoneText;
			case "Wumpa" -> switch (stage) {
				case READY -> a.wumpaReadyText;
				case STARTED -> a.wumpaStartedText;
				case DONE -> a.wumpaDoneText;
			};
			case "Doomspiral" -> switch (stage) {
				case READY -> a.doomspiralReadyText;
				case STARTED -> a.doomspiralStartedText;
				case DONE -> a.doomspiralDoneText;
			};
			default -> boss;
		};
	}

	private static String encounterChatText(String boss, Stage stage) {
		SafariConfig.PartyConfig p = ConfigManager.get().party;
		return switch (boss) {
			case "Gemzie" -> stage == Stage.READY ? p.gemzieReadyChatText : p.gemzieDoneChatText;
			case "Wumpa" -> switch (stage) {
				case READY -> p.wumpaReadyChatText;
				case STARTED -> p.wumpaStartedChatText;
				case DONE -> p.wumpaDoneChatText;
			};
			case "Doomspiral" -> switch (stage) {
				case READY -> p.doomspiralReadyChatText;
				case STARTED -> p.doomspiralStartedChatText;
				case DONE -> p.doomspiralDoneChatText;
			};
			default -> boss;
		};
	}

	private static int stageColour(Stage stage, String ready, String started, String done) {
		return switch (stage) {
			case READY -> Colours.argb(ready, READY_COLOUR);
			case STARTED -> Colours.argb(started, STARTED_COLOUR);
			case DONE -> Colours.argb(done, DONE_COLOUR);
		};
	}

	private static float encounterDuration(String boss, Stage stage) {
		SafariConfig.AlertConfig alerts = ConfigManager.get().alerts;
		return switch (boss) {
			case "Gemzie" -> gemzieStageValue(stage, alerts.gemzieReadyDuration, alerts.gemzieDoneDuration);
			case "Wumpa" -> stageValue(stage, alerts.wumpaReadyDuration,
				alerts.wumpaStartedDuration, alerts.wumpaDoneDuration);
			case "Doomspiral" -> stageValue(stage, alerts.doomspiralReadyDuration,
				alerts.doomspiralStartedDuration, alerts.doomspiralDoneDuration);
			default -> 3f;
		};
	}

	private static int encounterPlayback(String boss, Stage stage) {
		SafariConfig.AlertConfig alerts = ConfigManager.get().alerts;
		return switch (boss) {
			case "Gemzie" -> gemzieStageValue(stage, alerts.gemzieReadySoundMode, alerts.gemzieDoneSoundMode);
			case "Wumpa" -> stageValue(stage, alerts.wumpaReadySoundMode,
				alerts.wumpaStartedSoundMode, alerts.wumpaDoneSoundMode);
			case "Doomspiral" -> stageValue(stage, alerts.doomspiralReadySoundMode,
				alerts.doomspiralStartedSoundMode, alerts.doomspiralDoneSoundMode);
			default -> 3;
		};
	}

	private static int encounterSoundChoice(String boss, Stage stage) {
		SafariConfig.AlertConfig alerts = ConfigManager.get().alerts;
		return switch (boss) {
			case "Gemzie" -> gemzieStageValue(stage, alerts.gemzieReadySoundChoice, alerts.gemzieDoneSoundChoice);
			case "Wumpa" -> stageValue(stage, alerts.wumpaReadySoundChoice,
				alerts.wumpaStartedSoundChoice, alerts.wumpaDoneSoundChoice);
			case "Doomspiral" -> stageValue(stage, alerts.doomspiralReadySoundChoice,
				alerts.doomspiralStartedSoundChoice, alerts.doomspiralDoneSoundChoice);
			default -> 0;
		};
	}

	private static float encounterVolume(String boss, Stage stage) {
		SafariConfig.AlertConfig alerts = ConfigManager.get().alerts;
		return switch (boss) {
			case "Gemzie" -> gemzieStageValue(stage, alerts.gemzieReadySoundVolume, alerts.gemzieDoneSoundVolume);
			case "Wumpa" -> stageValue(stage, alerts.wumpaReadySoundVolume,
				alerts.wumpaStartedSoundVolume, alerts.wumpaDoneSoundVolume);
			case "Doomspiral" -> stageValue(stage, alerts.doomspiralReadySoundVolume,
				alerts.doomspiralStartedSoundVolume, alerts.doomspiralDoneSoundVolume);
			default -> 1f;
		};
	}

	private static float encounterPitch(String boss, Stage stage) {
		SafariConfig.AlertConfig alerts = ConfigManager.get().alerts;
		return switch (boss) {
			case "Gemzie" -> gemzieStageValue(stage, alerts.gemzieReadySoundPitch, alerts.gemzieDoneSoundPitch);
			case "Wumpa" -> stageValue(stage, alerts.wumpaReadySoundPitch,
				alerts.wumpaStartedSoundPitch, alerts.wumpaDoneSoundPitch);
			case "Doomspiral" -> stageValue(stage, alerts.doomspiralReadySoundPitch,
				alerts.doomspiralStartedSoundPitch, alerts.doomspiralDoneSoundPitch);
			default -> 1f;
		};
	}

	private static float encounterScale(String boss, Stage stage) {
		SafariConfig.AlertConfig a = ConfigManager.get().alerts;
		return switch (boss) {
			case "Gemzie" -> gemzieStageValue(stage, a.gemzieReadyScale, a.gemzieDoneScale);
			case "Wumpa" -> stageValue(stage, a.wumpaReadyScale, a.wumpaStartedScale, a.wumpaDoneScale);
			case "Doomspiral" -> stageValue(stage, a.doomspiralReadyScale, a.doomspiralStartedScale, a.doomspiralDoneScale);
			default -> 4f;
		};
	}

	private static float encounterVerticalPosition(String boss, Stage stage) {
		SafariConfig.AlertConfig a = ConfigManager.get().alerts;
		return switch (boss) {
			case "Gemzie" -> gemzieStageValue(stage, a.gemzieReadyVerticalPosition, a.gemzieDoneVerticalPosition);
			case "Wumpa" -> stageValue(stage, a.wumpaReadyVerticalPosition, a.wumpaStartedVerticalPosition, a.wumpaDoneVerticalPosition);
			case "Doomspiral" -> stageValue(stage, a.doomspiralReadyVerticalPosition, a.doomspiralStartedVerticalPosition, a.doomspiralDoneVerticalPosition);
			default -> 0.4f;
		};
	}

	private static float stageValue(Stage stage, float ready, float started, float done) {
		return switch (stage) {
			case READY -> ready;
			case STARTED -> started;
			case DONE -> done;
		};
	}

	private static boolean stageValue(Stage stage, boolean ready, boolean started, boolean done) {
		return switch (stage) {
			case READY -> ready;
			case STARTED -> started;
			case DONE -> done;
		};
	}

	private static int stageValue(Stage stage, int ready, int started, int done) {
		return switch (stage) {
			case READY -> ready;
			case STARTED -> started;
			case DONE -> done;
		};
	}

	// Gemzie has no distinct Started event; these keep its settings limited to the
	// two server-observable states while the other encounters retain three stages.
	private static float gemzieStageValue(Stage stage, float ready, float done) {
		return stage == Stage.READY ? ready : done;
	}

	private static boolean gemzieStageValue(Stage stage, boolean ready, boolean done) {
		return stage == Stage.READY ? ready : done;
	}

	private static int gemzieStageValue(Stage stage, int ready, int done) {
		return stage == Stage.READY ? ready : done;
	}

	/**
	 * Applies the optional biome gate for encounter alerts. Unknown locations fail open
	 * so a brief location delay does not silently lose an alert.
	 */
	private static boolean inItsBiome(String boss) {
		if (testing || !ConfigManager.get().alerts.encountersInBiomeOnly) return true;
		return isInEncounterBiome(boss);
	}

	private static boolean chatInItsBiome(String boss) {
		if (testing || !ConfigManager.get().party.encountersInBiomeOnly) return true;
		return isInEncounterBiome(boss);
	}

	private static boolean birdBannerAllowed() {
		return testing || !ConfigManager.get().alerts.encountersInBiomeOnly
			|| SafariLocation.biome() == SafariBiome.FOREST;
	}

	private static boolean birdChatAllowed() {
		return testing || !ConfigManager.get().party.encountersInBiomeOnly
			|| SafariLocation.biome() == SafariBiome.FOREST;
	}

	private static boolean isInEncounterBiome(String boss) {
		Critter critter = Critters.byName(boss);
		SafariBiome here = SafariLocation.biome();
		if (critter == null || here == null) return true;
		return critter.biome() == here;
	}

	/** Whether this encounter's banners are wanted. Each is settable on its own. */
	private static boolean alertsOn(String boss, Stage stage) {
		SafariConfig.AlertConfig alerts = ConfigManager.get().alerts;
		return switch (boss) {
			case "Gemzie" -> gemzieStageValue(stage,
				(alerts.gemzieReadySoundMode != 0), (alerts.gemzieDoneSoundMode != 0));
			case "Wumpa" -> stageValue(stage,
				(alerts.wumpaReadySoundMode != 0), (alerts.wumpaStartedSoundMode != 0), (alerts.wumpaDoneSoundMode != 0));
			case "Doomspiral" -> stageValue(stage,
				(alerts.doomspiralReadySoundMode != 0), (alerts.doomspiralStartedSoundMode != 0), (alerts.doomspiralDoneSoundMode != 0));
			default -> false;
		};
	}

	/** Who hears about this encounter, which is a separate choice per encounter. */
	private static SafariConfig.Broadcast broadcastFor(String boss) {
		SafariConfig.PartyConfig party = ConfigManager.get().party;
		return switch (boss) {
			case "Gemzie" -> party.gemzie();
			case "Wumpa" -> party.wumpa();
			case "Doomspiral" -> party.doomspiral();
			default -> SafariConfig.Broadcast.NONE;
		};
	}

	/** Sends one line to whoever the setting names, or nowhere. */
	static void post(SafariConfig.Broadcast to, String message) {
		// A blank custom field intentionally disables that one chat line without
		// disabling the rest of a multi-stage encounter.
		if (message == null || message.isBlank()) return;
		String command = to.command();
		if (command == null) return;
		if (command.equals("pc") && !PartyRosterWatch.canSendPartyChat()) return;
		ChatQueue.enqueue(command + " " + message, true);
	}

	static void postDelayed(SafariConfig.Broadcast to, String message, long delayMillis) {
		if (message == null || message.isBlank()) return;
		String command = to.command();
		if (command == null) return;
		if (command.equals("pc") && !PartyRosterWatch.canSendPartyChat()) return;
		ChatQueue.enqueueDelayed(command + " " + message, true, delayMillis);
	}

	/** True if this stage already fired recently, so it should be suppressed. */
	private static boolean onCooldown(String key) {
		long now = System.currentTimeMillis();
		Long previous = lastFired.get(key);
		if (previous != null && now - previous < STAGE_COOLDOWN_MILLIS) return true;
		lastFired.put(key, now);
		return false;
	}

	private static void banner(String text, int bannerColour, float pitch) {
		SafariConfig.AlertConfig alerts = ConfigManager.get().alerts;
		banner(text, bannerColour, pitch, 3f, 3, 0, 1f,
			alerts.alertScale, alerts.alertVerticalPosition);
	}

	private static void banner(String text, int bannerColour, float pitch,
							   float durationSeconds, int playbackMode, int soundChoice,
							   float volume, float scale, float verticalPosition) {
		// Sound-only alerts leave the currently displayed banner untouched.
		if ((playbackMode & 2) != 0) sound(pitch, volume, soundChoice);
		if ((playbackMode & 1) == 0) return;
		message = text;
		rainbowMessage = false;
		colour = bannerColour;
		shownAtMillis = System.currentTimeMillis();
		displayMillis = (long) (durationSeconds * 1000);
		SafariConfig.AlertConfig placement = ConfigManager.get().alerts;
		displayedScale = placement.alertScale;
		displayedHorizontalPosition = placement.alertHorizontalPosition;
		displayedVerticalPosition = placement.alertVerticalPosition;

	}

	private static void rainbowBanner(String text, float pitch, float durationSeconds,
								 int playbackMode, int soundChoice, float volume,
								 float scale, float verticalPosition) {
		// Sound-only alerts leave the currently displayed banner untouched.
		if ((playbackMode & 2) != 0) sound(pitch, volume, soundChoice);
		if ((playbackMode & 1) == 0) return;
		message = text;
		rainbowMessage = true;
		shownAtMillis = System.currentTimeMillis();
		displayMillis = (long) (durationSeconds * 1000);
		SafariConfig.AlertConfig placement = ConfigManager.get().alerts;
		displayedScale = placement.alertScale;
		displayedHorizontalPosition = placement.alertHorizontalPosition;
		displayedVerticalPosition = placement.alertVerticalPosition;

	}

	static void fireSparklingDetected(String critterName) {
		SafariConfig.SparklingConfig config = ConfigManager.get().sparkling;
		if (config.sparklingBannerSoundMode == 0) return;
		rainbowBanner(AlertText.format(config.sparklingBannerText, "<CRITTER>", critterName),
			config.sparklingBannerSoundPitch, config.sparklingBannerDuration,
			config.sparklingBannerSoundMode, config.sparklingBannerSoundChoice,
			config.sparklingBannerSoundVolume, config.sparklingBannerScale,
			config.sparklingBannerVerticalPosition);
	}

	public static void fireTestSparklingDetected() {
		ClientCompat.setScreen(null);
		SafariConfig.SparklingConfig config = ConfigManager.get().sparkling;
		rainbowBanner(AlertText.format(config.sparklingBannerText, "<CRITTER>", "Rockmite"),
			config.sparklingBannerSoundPitch, config.sparklingBannerDuration,
			config.sparklingBannerSoundMode, config.sparklingBannerSoundChoice,
			config.sparklingBannerSoundVolume, config.sparklingBannerScale,
			config.sparklingBannerVerticalPosition);
	}

	/** Previews the selected playback mode without requiring the event to happen. */
	public static void fireTestAlert() {
		ClientCompat.setScreen(null);
		// The shared appearance preview is independent of any event's playback picker.
		banner("Test Alert", 0xFFFFC857, 1f);
	}

	public static void fireTestAlert(Preview preview) {
		ClientCompat.setScreen(null);
		SafariConfig.AlertConfig a = ConfigManager.get().alerts;
		switch (preview) {
			case FULL_PARTY -> banner(AlertText.format(a.fullPartyJoinedText,
				"<PLAYERS>", "4", "<MAX>", "4"),
				Colours.argb(a.fullPartyJoinedColour, 0xFF55FF55),
				a.fullPartyJoinedSoundPitch, a.fullPartyJoinedDuration,
				a.fullPartyJoinedSoundMode, a.fullPartyJoinedSoundChoice,
				a.fullPartyJoinedSoundVolume, a.fullPartyJoinedScale,
				a.fullPartyJoinedVerticalPosition);
			case GEMZIE_READY -> testEncounter("Gemzie", Stage.READY);
			case GEMZIE_DONE -> testEncounter("Gemzie", Stage.DONE);
			case WUMPA_READY -> testEncounter("Wumpa", Stage.READY);
			case WUMPA_STARTED -> testEncounter("Wumpa", Stage.STARTED);
			case WUMPA_DONE -> testEncounter("Wumpa", Stage.DONE);
			case DOOM_READY -> testEncounter("Doomspiral", Stage.READY);
			case DOOM_STARTED -> testEncounter("Doomspiral", Stage.STARTED);
			case DOOM_DONE -> testEncounter("Doomspiral", Stage.DONE);
			case HOTSPOT -> banner(AlertText.format(a.hotspotText, "<BIOME>", "Icy"), a.hotspotUseBiomeColour
					? 0xFF55FFFF : Colours.argb(a.hotspotAlertColour, 0xFFFF55FF), a.hotspotSoundPitch,
				a.hotspotDuration, a.hotspotSoundMode, a.hotspotSoundChoice, a.hotspotSoundVolume,
				a.hotspotScale, a.hotspotVerticalPosition);
			case FLOOR_DROPS -> banner(AlertText.format(a.floorDropsDoneText, "<BIOME>", "Icy"), a.floorDropsDoneUseBiomeColour
					? 0xFF55FFFF : Colours.argb(a.floorDropsDoneAlertColour, 0xFF55FFAA), a.floorDropsDoneSoundPitch,
				a.floorDropsDoneDuration, a.floorDropsDoneSoundMode, a.floorDropsDoneSoundChoice,
				a.floorDropsDoneSoundVolume, a.floorDropsDoneScale, a.floorDropsDoneVerticalPosition);
			case BIOME_UNIQUES -> banner(AlertText.format(a.biomeUniquesDoneText, "<BIOME>", "Icy"), a.biomeUniquesDoneUseBiomeColour
					? 0xFF55FFFF : Colours.argb(a.biomeUniquesDoneColour, DONE_COLOUR),
				a.biomeUniquesDoneSoundPitch, a.biomeUniquesDoneDuration,
				a.biomeUniquesDoneSoundMode, a.biomeUniquesDoneSoundChoice,
				a.biomeUniquesDoneSoundVolume, a.biomeUniquesDoneScale,
				a.biomeUniquesDoneVerticalPosition);
			case ALL_BUT_MACAW -> banner(a.allButMacawDoneText,
				Colours.argb(a.allButMacawDoneColour, DONE_COLOUR),
				a.allButMacawDoneSoundPitch, a.allButMacawDoneDuration,
				a.allButMacawDoneSoundMode, a.allButMacawDoneSoundChoice,
				a.allButMacawDoneSoundVolume, a.allButMacawDoneScale,
				a.allButMacawDoneVerticalPosition);
			case ALL_DONE -> banner(a.allUniquesDoneText,
				Colours.argb(a.allUniquesDoneColour, DONE_COLOUR),
				a.allUniquesDoneSoundPitch, a.allUniquesDoneDuration,
				a.allUniquesDoneSoundMode, a.allUniquesDoneSoundChoice,
				a.allUniquesDoneSoundVolume, a.allUniquesDoneScale,
				a.allUniquesDoneVerticalPosition);
			case HIDEYHO -> banner(a.hideyhoText, Colours.argb(a.hideyhoAlertColour, 0xFFFF55FF),
				a.hideyhoSoundPitch, a.hideyhoDuration, a.hideyhoSoundMode, a.hideyhoSoundChoice,
				a.hideyhoSoundVolume, a.hideyhoScale, a.hideyhoVerticalPosition);
			case MACAW -> banner(a.macawText, a.macawUseRarityColour
					? 0xFFFFAA00 : Colours.argb(a.macawAlertColour, 0xFFFFAA00), a.macawSoundPitch,
				a.macawDuration, a.macawSoundMode, a.macawSoundChoice, a.macawSoundVolume,
				a.macawScale, a.macawVerticalPosition);
			case BIRDS -> banner(AlertText.format(a.birdfeederText, "<CRITTER>", "Bluebird"), a.birdfeederUseRarityColour
					? 0xFF55FF55 : Colours.argb(a.bluebirdAlertColour, 0xFF55FF55), a.birdfeederSoundPitch,
				a.birdfeederDuration, a.birdfeederSoundMode, a.birdfeederSoundChoice,
				a.birdfeederSoundVolume, a.birdfeederScale, a.birdfeederVerticalPosition);
			case FEED_GONE -> banner(a.feedGoneText, Colours.argb(a.feedGoneColour, 0xFFFF5555),
				a.feedGoneSoundPitch, a.feedGoneDuration, a.feedGoneSoundMode,
				a.feedGoneSoundChoice, a.feedGoneSoundVolume,
				a.feedGoneScale, a.feedGoneVerticalPosition);
			case BIRDFEEDER_EMPTY -> banner(a.birdfeederEmptyText, Colours.argb(a.birdfeederEmptyColour, 0xFFFF5555),
				a.birdfeederEmptySoundPitch, a.birdfeederEmptyDuration, a.birdfeederEmptySoundMode,
				a.birdfeederEmptySoundChoice, a.birdfeederEmptySoundVolume,
				a.birdfeederEmptyScale, a.birdfeederEmptyVerticalPosition);
			case START -> testContest(a.contestStartText, a.contestStartColour, 0xFF55FF55,
				a.contestStartDuration, a.contestStartSoundMode, a.contestStartSoundChoice,
				a.contestStartSoundVolume, a.contestStartSoundPitch, a.contestStartScale, a.contestStartVerticalPosition);
			case FIVE_MINUTES -> testContest(a.contestFiveMinuteText, a.contestFiveMinuteColour, 0xFFFFFF55,
				a.contestFiveMinuteDuration, a.contestFiveMinuteSoundMode, a.contestFiveMinuteSoundChoice,
				a.contestFiveMinuteSoundVolume, a.contestFiveMinuteSoundPitch, a.contestFiveMinuteScale, a.contestFiveMinuteVerticalPosition);
			case ONE_MINUTE -> testContest(a.contestOneMinuteText, a.contestOneMinuteColour, 0xFFFFAA00,
				a.contestOneMinuteDuration, a.contestOneMinuteSoundMode, a.contestOneMinuteSoundChoice,
				a.contestOneMinuteSoundVolume, a.contestOneMinuteSoundPitch, a.contestOneMinuteScale, a.contestOneMinuteVerticalPosition);
			case ENDED -> testContest(a.contestEndedText, a.contestEndedColour, 0xFFFF5555,
				a.contestEndedDuration, a.contestEndedSoundMode, a.contestEndedSoundChoice,
				a.contestEndedSoundVolume, a.contestEndedSoundPitch, a.contestEndedScale, a.contestEndedVerticalPosition);
			case TICKET -> testContest(a.contestTicketEarnedText, a.contestTicketEarnedColour, 0xFF55FF55,
				a.contestTicketEarnedDuration, a.contestTicketEarnedSoundMode, a.contestTicketEarnedSoundChoice,
				a.contestTicketEarnedSoundVolume, a.contestTicketEarnedSoundPitch, a.contestTicketEarnedScale, a.contestTicketEarnedVerticalPosition);
		}
	}

	private static void testEncounter(String boss, Stage stage) {
		banner(encounterBannerText(boss, stage), encounterColour(boss, stage),
			encounterPitch(boss, stage), encounterDuration(boss, stage), encounterPlayback(boss, stage),
			encounterSoundChoice(boss, stage), encounterVolume(boss, stage),
			encounterScale(boss, stage), encounterVerticalPosition(boss, stage));
	}

	private static void testContest(String text, String colourSetting, int fallback, float duration,
								int playbackMode, int soundChoice, float volume, float pitch,
								float scale, float verticalPosition) {
		banner(text, Colours.argb(colourSetting, fallback), pitch, duration, playbackMode,
			soundChoice, volume, scale, verticalPosition);
	}

	/** Silent unless asked for: a run fires plenty of these. */
	private static void sound(float pitch, float volume, int choice) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) AlertSounds.play(client, choice, volume, pitch);
	}

	/** Clears per-stage cooldowns; called when a new run starts. */
	public static void reset() {
		lastFired.clear();
		gemzieRemaining = 0;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (message == null) return;

		long age = System.currentTimeMillis() - shownAtMillis;
		if (age > displayMillis) {
			message = null;
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || ClientCompat.hudHidden()) return;

		// Fade over the last second so it does not simply vanish.
		int alpha = 0xFF;
		long fadeStart = displayMillis - 1000;
		if (age > fadeStart) {
			alpha = (int) (0xFF * (displayMillis - age) / 1000.0);
		}
		// A short ease-in gives the panel a modern appearance without retaining
		// animation objects or doing work while no banner is visible.
		alpha = Math.min(alpha, (int) (0xFF * Math.min(1.0, age / 180.0)));

		Font font = client.font;
		SafariConfig.AlertConfig appearance = ConfigManager.get().alerts;
		Component styledMessage = styledBannerText(message, appearance.bannerFont);
		int frameWidth = font.width(styledMessage) + 16;
		int edgeMargin = 5;
		int availableWidth = Math.max(1, graphics.guiWidth() - edgeMargin * 2);
		float scale = Math.min(displayedScale
			* ResponsiveUI.scale(graphics.guiWidth(), graphics.guiHeight()),
			availableWidth / (float) frameWidth);
		// Expand around the configured anchor, shift away from either edge only as
		// needed, then shrink as a last resort when the full banner cannot fit.
		float scaledWidth = frameWidth * scale;
		float desiredCentre = graphics.guiWidth() * displayedHorizontalPosition;
		float minimumCentre = edgeMargin + scaledWidth / 2f;
		float maximumCentre = graphics.guiWidth() - edgeMargin - scaledWidth / 2f;
		float physicalCentre = Math.clamp(desiredCentre, minimumCentre, maximumCentre);
		int centreX = Math.round(physicalCentre / scale);
		int y = Math.round(graphics.guiHeight() * displayedVerticalPosition / scale);

		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		drawBannerFrame(graphics, font, styledMessage, centreX, y, alpha, rainbowMessage,
			appearance,
			(float) Math.clamp(1.0 - age / (double) displayMillis, 0.0, 1.0));
		if (rainbowMessage) {
			rainbowCenteredText(graphics, font, message, centreX, y + 1, alpha,
				appearance.bannerFont, appearance.bannerTextShadow);
		} else {
			graphics.text(font, styledMessage, centreX - font.width(styledMessage) / 2, y + 1,
				(alpha << 24) | (colour & 0xFFFFFF), appearance.bannerTextShadow);
		}
		graphics.pose().popMatrix();
	}

	private static void drawBannerFrame(GuiGraphicsExtractor graphics, Font font, Component text,
			int centreX, int textY, int alpha, boolean rainbow,
			SafariConfig.AlertConfig appearance, float remaining) {
		int width = font.width(text) + 16;
		int left = centreX - width / 2;
		int right = left + width;
		int top = textY - 4;
		int bottom = textY + 14;
		int leftAccent = colour & 0xFFFFFF;
		int rightAccent = leftAccent;
		if (rainbow) {
			float phase = (System.currentTimeMillis() % 4_000L) / 4_000f;
			leftAccent = java.awt.Color.HSBtoRGB(phase, 0.55f, 1f) & 0xFFFFFF;
			rightAccent = java.awt.Color.HSBtoRGB((phase + 0.5f) % 1f, 0.55f, 1f) & 0xFFFFFF;
		}
		int background = Colours.argb(appearance.bannerBackgroundColour, 0x9B121925);
		int panelAlpha = (background >>> 24) * alpha / 255;
		if (appearance.bannerBackground && panelAlpha > 0) {
			int topRgb = appearance.bannerBackgroundMatchAlertColour
				? blendRgb(0x080B11, leftAccent, 0.24f)
				: background & 0xFFFFFF;
			int bottomRgb = switch (appearance.bannerBackgroundStyle) {
				case 0 -> topRgb;
				case 1 -> appearance.bannerBackgroundMatchAlertColour && rainbow
					? blendRgb(0x080B11, rightAccent, 0.19f) : shade(topRgb, 0.80f);
				default -> appearance.bannerBackgroundMatchAlertColour && rainbow
					? blendRgb(0x05070B, rightAccent, 0.13f) : shade(topRgb, 0.45f);
			};
			graphics.fillGradient(left, top, right, bottom,
				(panelAlpha << 24) | topRgb, (panelAlpha << 24) | bottomRgb);
		}

		int leftColor = (alpha << 24) | leftAccent;
		int rightColor = (alpha << 24) | rightAccent;
		int borderColor = rainbow ? leftColor : (alpha << 24) | leftAccent;
		int progressRgb = mixWithWhite(rainbow ? rightAccent : leftAccent, 0.32f);
		int progressColor = (alpha << 24) | progressRgb;
		int thickness = Math.max(1, Math.round(appearance.bannerBorderThickness));
		// Horizontal edges own the corners; vertical edges stop before them, so the
		// same alpha is written exactly once everywhere around the frame.
		if (appearance.bannerBorder) {
			graphics.fill(left, top, right, top + thickness, borderColor);
			graphics.fill(left, bottom - thickness, right, bottom, borderColor);
			graphics.fill(left, top + thickness, left + thickness, bottom - thickness, borderColor);
			graphics.fill(right - thickness, top + thickness, right, bottom - thickness, borderColor);
		}
		drawSmoothProgress(graphics, left, right, top, bottom, progressColor, remaining,
			appearance.bannerTopBar, appearance.bannerBottomBar, appearance.bannerBorder ? thickness : 0);
	}

	/** Draws inset duration bars at quarter-pixel horizontal resolution. */
	private static void drawSmoothProgress(GuiGraphicsExtractor graphics, int left, int right,
			int top, int bottom, int color, float remaining, int topDirection,
			int bottomDirection, int borderInset) {
		if (topDirection == 0 && bottomDirection == 0) return;
		int horizontalPrecision = 4;
		int inset = Math.max(1, borderInset);
		int scaledLeft = (left + inset) * horizontalPrecision;
		int scaledRight = (right - inset) * horizontalPrecision;
		int progressWidth = Math.round((right - left - inset * 2) * horizontalPrecision * remaining);
		graphics.pose().pushMatrix();
		graphics.pose().scale(1f / horizontalPrecision, 1f);
		if (topDirection != 0) {
			int start = topDirection == 1 ? scaledLeft : scaledRight - progressWidth;
			graphics.fill(start, top + inset, start + progressWidth, top + inset + 1, color);
		}
		if (bottomDirection != 0) {
			int start = bottomDirection == 1 ? scaledLeft : scaledRight - progressWidth;
			graphics.fill(start, bottom - inset - 1, start + progressWidth, bottom - inset, color);
		}
		graphics.pose().popMatrix();
	}

	private static Component styledBannerText(String text, int fontStyle) {
		Component component = Component.literal(text);
		return switch (fontStyle) {
			case 1 -> component.copy().withStyle(ChatFormatting.BOLD);
			case 2 -> component.copy().withStyle(ChatFormatting.ITALIC);
			default -> component;
		};
	}

	private static int mixWithWhite(int rgb, float amount) {
		int red = (rgb >> 16) & 0xFF;
		int green = (rgb >> 8) & 0xFF;
		int blue = rgb & 0xFF;
		red += Math.round((255 - red) * amount);
		green += Math.round((255 - green) * amount);
		blue += Math.round((255 - blue) * amount);
		return red << 16 | green << 8 | blue;
	}

	private static int shade(int rgb, float brightness) {
		int red = Math.round((rgb >> 16 & 0xFF) * brightness);
		int green = Math.round((rgb >> 8 & 0xFF) * brightness);
		int blue = Math.round((rgb & 0xFF) * brightness);
		return red << 16 | green << 8 | blue;
	}

	private static int blendRgb(int base, int tint, float tintAmount) {
		float baseAmount = 1f - tintAmount;
		int red = Math.round((base >> 16 & 0xFF) * baseAmount + (tint >> 16 & 0xFF) * tintAmount);
		int green = Math.round((base >> 8 & 0xFF) * baseAmount + (tint >> 8 & 0xFF) * tintAmount);
		int blue = Math.round((base & 0xFF) * baseAmount + (tint & 0xFF) * tintAmount);
		return red << 16 | green << 8 | blue;
	}

	static HudPanel editorPanel() {
		HudPanel panel = new HudPanel();
		panel.title("Banner Alert", 0xFFFFC857);
		panel.line("Preview notification", 0xFFFFFFFF);
		return panel;
	}

	private static void rainbowCenteredText(GuiGraphicsExtractor graphics, Font font,
			String text, int centreX, int y, int alpha, int fontStyle, boolean shadow) {
		int x = centreX - font.width(styledBannerText(text, fontStyle)) / 2;
		float phase = (System.currentTimeMillis() % 4_000L) / 4_000f;
		for (int i = 0; i < text.length(); i++) {
			String character = String.valueOf(text.charAt(i));
			int rgb = java.awt.Color.HSBtoRGB(
				(phase + i / (float) Math.max(1, text.length())) % 1f, 0.45f, 1f);
			Component styledCharacter = styledBannerText(character, fontStyle);
			graphics.text(font, styledCharacter, x, y,
				(alpha << 24) | (rgb & 0xFFFFFF), shadow);
			x += font.width(styledCharacter);
		}
	}
}
