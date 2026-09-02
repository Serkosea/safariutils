package dev.serko.safariutils;

import dev.serko.safariutils.client.BazaarPrices;
import dev.serko.safariutils.client.AlertSounds;
import dev.serko.safariutils.client.ChatQueue;
import dev.serko.safariutils.client.SafariCommands;
import dev.serko.safariutils.client.ProgressHud;
import dev.serko.safariutils.client.ConfigManager;
import dev.serko.safariutils.client.ContestTracker;
import dev.serko.safariutils.client.DetectedCritters;
import dev.serko.safariutils.client.HeadStartWatch;
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
import dev.serko.safariutils.client.MoundSpotter;
import dev.serko.safariutils.client.NestTracker;
import dev.serko.safariutils.client.RecatchSpots;
import dev.serko.safariutils.client.SafariLocation;
import dev.serko.safariutils.client.SafariPartyWatch;
import dev.serko.safariutils.client.SafariPaths;
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
 * biome. Nothing is sent anywhere; it only reads chat the client already receives.
 */
public class SafariUtils implements ClientModInitializer {

	public static final String MOD_ID = "safariutils";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		SafariPaths.migrateLegacyFiles();
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			InteractionDebugLog.onScreenInit(client, screen, width, height);
			TicketProtection.onScreenInit(screen);
		});
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			// Log before optional automation hides a clickable server prompt.
			InteractionDebugLog.onGameMessage(message, overlay);
			return PartyRosterWatch.allow(message, overlay)
				&& PartyErrorSuppressor.allow(message, overlay)
				&& HideyhoAutoAccept.allow(message, overlay);
		});
		// Hypixel sends catch messages as system chat, which is what GAME covers.
		// This fires upstream of chat-compacting mods, so the duplicate counters
		// they append never reach the parser.
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
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
				HeadStartWatch.onChatMessage(line);
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			AlertSounds.tick();
			if (BuildVersion.DEVELOPER) DebugLog.tick();
			// Next, and only here: everything below asks it where the player is.
			SafariLocation.tick();
			BirdfeederWatch.tickMenu();
			PartyRosterWatch.tick();
			SafariPartyWatch.tick();
			SparklingMode.tick();
			SharedSparklingProviders.tick();
			if (BuildVersion.DEVELOPER) DebugStateLog.tick();
			if (BuildVersion.DEVELOPER) InteractionDebugLog.tick();
			ContestTracker.tick();
			// One sweep of the world's critters, for everything below that wants them.
			CritterEntities.tick();
			ParticleDiagnostics.tick();
			if (BuildVersion.DEVELOPER) CritterCountLog.tick();
			HideyhoSolver.tick();
			StillCritters.tick();
			DetectedCritters.tick();
			HeadStartWatch.tick();
			SafariObjectives.tick();
			SessionManager.tick();
			CritterSpotter.tick();
			NestTracker.tick();
			SparklingWatch.tick();
			FloorDrops.tick();
			MoundSpotter.tick();
			StaticWaypointCatalog.tick();
			StaticEntityCatalog.tick();
			RecatchSpots.tick();
			DarknessFilter.tick();
			// Off-thread, at most every two minutes, and only where a price is shown.
			BazaarPrices.tick();
			ChatQueue.tick();
			ConfigManager.tick();
		});

		// Nothing else writes the settings file on the way out, and the game can be quit
		// straight from the settings screen.
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ConfigManager.save();
			BazaarPrices.shutdown();
			SharedSparklingProviders.shutdown();
			StaticWaypointCatalog.shutdown();
			StaticEntityCatalog.shutdown();
		});

		// Hypixel never says you have left the Safari, but moving island reconnects, so
		// this is the one moment the chat-driven flag is known to be stale.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			SafariLocation.onWorldChange();
			SessionManager.onWorldChange();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			SafariLocation.onWorldChange();
			SessionManager.onWorldChange();
		});

		// Punching a bee nest changes nothing about the block, so the punch itself is
		// the only signal that it has been done.
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			NestTracker.onAttack(pos);
			// A drop being picked up would clear itself a few seconds later anyway;
			// dropping it on the interaction just makes the mark go when you expect.
			FloorDrops.onInteract(pos);
			return InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			MoundSpotter.onAttack(entity);
			InteractionDebugLog.onEntityInteraction("attack", entity, hand.toString());
			return TicketProtection.blockManagerInteraction(entity)
				? InteractionResult.FAIL : InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			FloorDrops.onInteract(hit.getBlockPos());
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			SafariPartyWatch.onEntityUse(entity);
			BirdfeederWatch.onEntityUse(entity);
			InteractionDebugLog.onEntityInteraction("use", entity, hand.toString());
			return TicketProtection.blockManagerInteraction(entity)
				? InteractionResult.FAIL : InteractionResult.PASS;
		});

		ClientCommandRegistrationCallback.EVENT.register(
			(dispatcher, registryAccess) -> SafariCommands.register(dispatcher));

		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(MOD_ID, "safari_progress"),
			new ProgressHud());
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(MOD_ID, "safari_missing"),
			new MissingHud());
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(MOD_ID, "contest_tracker"),
			new ContestTracker());
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(MOD_ID, "encounter_alerts"),
			new EncounterAlerts());
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(MOD_ID, "full_screen_alert"),
			new FullScreenAlert());

		WaypointRenderer.register();

		// Past runs live next to the settings, as plain JSON, so they survive updates
		// and can be read or thrown away by hand.
		RunHistory.load(SafariPaths.runHistory());
		SparklingStats.load(SafariPaths.sparklingStats());

		LOGGER.info("Critter Safari tracker ready");
	}
}
