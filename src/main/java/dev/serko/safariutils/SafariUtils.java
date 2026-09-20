package dev.serko.safariutils;

import dev.serko.safariutils.client.BazaarPrices;
import dev.serko.safariutils.client.AlertSounds;
import dev.serko.safariutils.client.ChatQueue;
import dev.serko.safariutils.client.SafariCommands;
import dev.serko.safariutils.client.ProgressHud;
import dev.serko.safariutils.client.ConfigManager;
import dev.serko.safariutils.client.ContestTracker;
import dev.serko.safariutils.client.DetectedCritters;
import dev.serko.safariutils.client.StartingItemsWatch;
import dev.serko.safariutils.client.CritterEntities;
import dev.serko.safariutils.client.CritterSpotter;
import dev.serko.safariutils.client.DarknessFilter;
import dev.serko.safariutils.client.FloorDrops;
import dev.serko.safariutils.client.CritterCountLog;
import dev.serko.safariutils.client.DebugLog;
import dev.serko.safariutils.client.DebugStateLog;
import dev.serko.safariutils.client.InteractionDebugLog;
import dev.serko.safariutils.client.HideyhoSolver;
import dev.serko.safariutils.client.HideyhoAutoAccept;
import dev.serko.safariutils.client.PartyErrorSuppressor;
import dev.serko.safariutils.client.PartyRosterWatch;
import dev.serko.safariutils.client.TicketProtection;
import dev.serko.safariutils.client.StillCritters;
import dev.serko.safariutils.client.HotspotWatch;
import dev.serko.safariutils.client.BirdfeederWatch;
import dev.serko.safariutils.client.ShiningCoinWatch;
import dev.serko.safariutils.client.MissingHud;
import dev.serko.safariutils.client.PartyObjectiveHud;
import dev.serko.safariutils.client.MoundSpotter;
import dev.serko.safariutils.client.NestTracker;
import dev.serko.safariutils.client.RecatchSpots;
import dev.serko.safariutils.client.SafariLocation;
import dev.serko.safariutils.client.SafariPartyWatch;
import dev.serko.safariutils.client.SafariPaths;
import dev.serko.safariutils.client.OperationalLog;
import dev.serko.safariutils.client.StaticWaypointCatalog;
import dev.serko.safariutils.client.StaticEntityCatalog;
import dev.serko.safariutils.client.FullScreenAlert;
import dev.serko.safariutils.client.SparklingWatch;
import dev.serko.safariutils.client.SparklingMode;
import dev.serko.safariutils.client.ParticleDiagnostics;
import dev.serko.safariutils.client.SafariObjectives;
import dev.serko.safariutils.client.WaypointRenderer;
import dev.serko.safariutils.client.EncounterAlerts;
import dev.serko.safariutils.api.SharedSparklingProviders;
import dev.serko.safariutils.api.PartyItemSyncProviders;
import dev.serko.safariutils.parse.ChatParser;
import dev.serko.safariutils.session.RunHistory;
import dev.serko.safariutils.session.SessionManager;
import dev.serko.safariutils.session.SparklingStats;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side tracker for Hypixel SkyBlock's Critter Safari.
 *
 * <p>Listens to Hypixel's own catch messages and tallies, for the current run,
 * how many of the 37 species you and your party have caught — overall and per
 * biome. It reads chat the client already receives and sends only the chat alerts
 * the player explicitly enables.
 */
public class SafariUtils implements ClientModInitializer {

	public static final String MOD_ID = "safariutils";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		SafariPaths.migrateLegacyFiles();
		OperationalLog.start();
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> OperationalLog.run("SCREEN/INIT", () -> {
			InteractionDebugLog.onScreenInit(client, screen, width, height);
			TicketProtection.onScreenInit(screen);
		}));
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			return OperationalLog.get("CHAT/FILTER", () -> {
				// Log before optional automation hides a clickable server prompt.
				InteractionDebugLog.onGameMessage(message, overlay);
				return PartyRosterWatch.allow(message, overlay)
					&& PartyErrorSuppressor.allow(message, overlay)
					&& HideyhoAutoAccept.allow(message, overlay)
					&& PartyItemSyncProviders.allowMessage(message, overlay);
			}, true);
		});
		// Hypixel sends catch messages as system chat, which is what GAME covers.
		// This fires upstream of chat-compacting mods, so the duplicate counters
		// they append never reach the parser.
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> OperationalLog.run("CHAT/HANDLE", () -> {
			if (overlay) return;
			// Hypixel sends banners such as the "entered Critter Safari!" notice as a
			// single multi-line component, so each line has to be handled separately
			// or the interesting one never matches on its own.
			for (String part : message.getString().split("\\r?\\n|\\\\n")) {
				String line = ChatParser.clean(part);
				if (line.isEmpty()) continue;

				// Player-written lines may quote server text, so trackers ignore them.
				if (ChatParser.playerSaid(line)) {
					continue;
				}

				// Log server messages before parsing so an unknown format remains
				// diagnosable. Player chat was filtered out above.
				DebugLog.line("RAW", "\"" + line + "\"");
				PartyItemSyncProviders.onServerMessage(line);

				SafariLocation.onChatMessage(line);
				SparklingMode.onChatMessage(line);
				SessionManager.onChatMessage(line);
				EncounterAlerts.onChatMessage(line);
				RecatchSpots.onChatMessage(line);
				BirdfeederWatch.onChatMessage(line);
				ShiningCoinWatch.onChatMessage(line);
				SafariObjectives.onChatMessage(line);
				HotspotWatch.onChatMessage(line);
				HideyhoSolver.onChatMessage(line);
				StillCritters.onChatMessage(line);
				FloorDrops.onChatMessage(line);
				MoundSpotter.onChatMessage(line);
			}
		}));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			tickSafely("alerts", AlertSounds::tick);
			if (BuildVersion.DEVELOPER) tickSafely("debug-log", DebugLog::tick);
			// Next, and only here: everything below asks it where the player is.
			tickSafely("location", SafariLocation::tick);
			tickSafely("birdfeeder-menu", BirdfeederWatch::tickMenu);
			tickSafely("party-roster", PartyRosterWatch::tick);
			tickSafely("safari-party", SafariPartyWatch::tick);
			tickSafely("sparkling-mode", SparklingMode::tick);
			tickSafely("shared-sparklings", SharedSparklingProviders::tick);
			tickSafely("starting-items", StartingItemsWatch::tick);
			tickSafely("objectives", SafariObjectives::tick);
			// Sync snapshots consume the inventory caches refreshed immediately above.
			tickSafely("party-objectives", PartyItemSyncProviders::tick);
			if (BuildVersion.DEVELOPER) tickSafely("debug-state", DebugStateLog::tick);
			if (BuildVersion.DEVELOPER) tickSafely("interaction-debug", InteractionDebugLog::tick);
			tickSafely("contest", ContestTracker::tick);
			// One sweep of the world's critters, for everything below that wants them.
			tickSafely("critter-entities", CritterEntities::tick);
			tickSafely("particle-diagnostics", ParticleDiagnostics::tick);
			if (BuildVersion.DEVELOPER) tickSafely("critter-count-log", CritterCountLog::tick);
			tickSafely("hideyho", HideyhoSolver::tick);
			tickSafely("still-critters", StillCritters::tick);
			tickSafely("detected-critters", DetectedCritters::tick);
			tickSafely("session", SessionManager::tick);
			tickSafely("critter-spotter", CritterSpotter::tick);
			tickSafely("nests", NestTracker::tick);
			tickSafely("sparkling-watch", SparklingWatch::tick);
			tickSafely("floor-drops", FloorDrops::tick);
			tickSafely("mounds", MoundSpotter::tick);
			tickSafely("static-waypoints", StaticWaypointCatalog::tick);
			tickSafely("static-entities", StaticEntityCatalog::tick);
			tickSafely("recatch", RecatchSpots::tick);
			tickSafely("darkness", DarknessFilter::tick);
			// Off-thread, at most every five minutes, and only where a price is shown.
			tickSafely("bazaar", BazaarPrices::tick);
			tickSafely("chat-queue", ChatQueue::tick);
			tickSafely("config", ConfigManager::tick);
		});

		// Nothing else writes the settings file on the way out, and the game can be quit
		// straight from the settings screen.
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ConfigManager.save();
			BazaarPrices.shutdown();
			SharedSparklingProviders.shutdown();
			StaticWaypointCatalog.shutdown();
			StaticEntityCatalog.shutdown();
			OperationalLog.shutdown();
		});

		// Hypixel never says you have left the Safari, but moving island reconnects, so
		// this is the one moment the chat-driven flag is known to be stale.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> OperationalLog.run("CONNECTION/JOIN", () -> {
			OperationalLog.info("LIFECYCLE", "Joined a server world");
			SafariLocation.onWorldChange();
			SessionManager.onWorldChange();
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> OperationalLog.run("CONNECTION/DISCONNECT", () -> {
			OperationalLog.info("LIFECYCLE", "Disconnected from server world");
			SafariLocation.onWorldChange();
			SessionManager.onWorldChange();
		}));

		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			return OperationalLog.get("INTERACTION/ATTACK_BLOCK", () -> {
				NestTracker.onInteract(pos);
				// A drop being picked up would clear itself a few seconds later anyway;
				// dropping it on the interaction just makes the mark go when you expect.
				FloorDrops.onInteract(pos);
				return InteractionResult.PASS;
			}, InteractionResult.PASS);
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			return OperationalLog.get("INTERACTION/ATTACK_ENTITY", () -> {
				MoundSpotter.onAttack(entity);
				InteractionDebugLog.onEntityInteraction("attack", entity, hand.toString());
				return TicketProtection.blockManagerInteraction(entity)
					? InteractionResult.FAIL : InteractionResult.PASS;
			}, InteractionResult.PASS);
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			return OperationalLog.get("INTERACTION/USE_BLOCK", () -> {
				NestTracker.onInteract(hit.getBlockPos());
				FloorDrops.onInteract(hit.getBlockPos());
				return InteractionResult.PASS;
			}, InteractionResult.PASS);
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			return OperationalLog.get("INTERACTION/USE_ENTITY", () -> {
				SafariPartyWatch.onEntityUse(entity);
				BirdfeederWatch.onEntityUse(entity);
				InteractionDebugLog.onEntityInteraction("use", entity, hand.toString());
				return TicketProtection.blockManagerInteraction(entity)
					? InteractionResult.FAIL : InteractionResult.PASS;
			}, InteractionResult.PASS);
		});

		ClientCommandRegistrationCallback.EVENT.register(
			(dispatcher, registryAccess) -> SafariCommands.register(dispatcher));

		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(MOD_ID, "safari_progress"),
			OperationalLog.hud("progress", new ProgressHud()));
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(MOD_ID, "safari_missing"),
			OperationalLog.hud("missing", new MissingHud()));
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(MOD_ID, "contest_tracker"),
			OperationalLog.hud("contest", new ContestTracker()));
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(MOD_ID, "party_objectives"),
			OperationalLog.hud("party-objectives", new PartyObjectiveHud()));
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(MOD_ID, "encounter_alerts"),
			OperationalLog.hud("encounter-alerts", new EncounterAlerts()));
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(MOD_ID, "full_screen_alert"),
			OperationalLog.hud("full-screen-alert", new FullScreenAlert()));

		WaypointRenderer.register();

		// Past runs live next to the settings, as plain JSON, so they survive updates
		// and can be read or thrown away by hand.
		RunHistory.load(SafariPaths.runHistory());
		SparklingStats.load(SafariPaths.sparklingStats());

		LOGGER.info("Critter Safari tracker ready");
	}

	private static void tickSafely(String tracker, Runnable action) {
		OperationalLog.run(tracker, action);
	}
}
