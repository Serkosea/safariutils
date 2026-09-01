package dev.serko.safariutils.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.serko.safariutils.BuildVersion;
import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.api.SharedSparklingProviders;
import dev.serko.safariutils.session.SessionManager;
import dev.serko.safariutils.session.SparklingStats;
import dev.serko.safariutils.session.RunHistory;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.Map;

/** Registers Safari Utils commands and their short aliases. */
public final class SafariCommands {

	/** Matches what the server actually loads out here — wider just adds empty scan time. */
	private static final double ENTITY_SCAN_RADIUS = 50.0;
	/** Close enough that whatever you are stood on is at the top of the list. */
	private static final double NEARBY_SCAN_RADIUS = 8.0;

	private SafariCommands() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		// Each alias gets its own tree because Brigadier builders cannot be reused.
		for (String name : List.of("safariutils", "su", "safari")) {
			var settingsRoot = ClientCommands.literal(name)
			.executes(ctx -> {
				openSettings();
				return 1;
			})
			.then(ClientCommands.literal("gui").executes(ctx -> {
				HudEditorScreen.open();
				return 1;
			}))
			.then(statsRoot());

			// Developer builds alone register the diagnostic command tree.
			if (BuildVersion.DEVELOPER) {
				var debugRoot = ClientCommands.literal("debug")
				.executes(ctx -> {
					requireUnlocked(ctx.getSource(), () -> debug(ctx.getSource()));
					return 1;
				})
				.then(ClientCommands.literal("entities").executes(ctx -> {
					requireUnlocked(ctx.getSource(), () -> entities(ctx.getSource()));
					return 1;
				}))
				.then(ClientCommands.literal("nearby").executes(ctx -> {
					requireUnlocked(ctx.getSource(), () -> nearby(ctx.getSource()));
					return 1;
				}))
				.then(ClientCommands.literal("run").executes(ctx -> {
					requireUnlocked(ctx.getSource(), () -> runState(ctx.getSource()));
					return 1;
				}))
				.then(ClientCommands.literal("critters").executes(ctx -> {
					requireUnlocked(ctx.getSource(), () -> critterPairings(ctx.getSource()));
					return 1;
				}))
				.then(ClientCommands.literal("catalogs").executes(ctx -> {
					requireUnlocked(ctx.getSource(), () -> catalogs(ctx.getSource()));
					return 1;
				}))
				.then(ClientCommands.literal("block").executes(ctx -> {
					requireUnlocked(ctx.getSource(), () -> block(ctx.getSource()));
					return 1;
				}))
				.then(ClientCommands.literal("tablist").executes(ctx -> {
					requireUnlocked(ctx.getSource(), () -> tablist(ctx.getSource()));
					return 1;
				}))
				.then(ClientCommands.literal("waypoints").executes(ctx -> {
					requireUnlocked(ctx.getSource(), () -> waypoints(ctx.getSource()));
					return 1;
				}))
				.then(ClientCommands.literal("testalert").executes(ctx -> {
					requireUnlocked(ctx.getSource(), () -> {
						// Walks every stage without waiting for a real encounter.
						EncounterAlerts.whileTesting(() -> {
							EncounterAlerts.onChatMessage("A rumbling sound can be heard, and the door at the back of the chamber opens...");
							EncounterAlerts.onChatMessage("You hear the sound of massive footsteps echoing through the Icy Biome... What could it be?");
							EncounterAlerts.onChatMessage("The Wumpa has awoken.");
							EncounterAlerts.onChatMessage("The cave opens up again...");
							EncounterAlerts.onChatMessage("Your ritual summoned a Doomspiral into this world. Stay still.");
							EncounterAlerts.onChatMessage("The Doomspiral retreats back underground...");
							for (int i = 0; i < 3; i++) EncounterAlerts.onCatch("Gemzie");
							EncounterAlerts.onBiomeComplete(SafariBiome.CAVERN);
							EncounterAlerts.onAllButMacaw();
							EncounterAlerts.onAllDone();
						});
						BirdfeederWatch.onChatMessage("A Bluebird was attracted to the Birdfeeder!");
						BirdfeederWatch.onChatMessage("Two Macaws were attracted to the Birdfeeder!");
						HotspotWatch.onChatMessage("HOTSPOT! Your Hunting Hotspot is the Icy Biome!");
						FullScreenAlert.show("SPARKLING!", "Rockmite", "Cavern -96 40 42",
							FullScreenAlert.SPARKLING);
					});
					return 1;
				}));

				settingsRoot.then(debugRoot);
			}

			dispatcher.register(settingsRoot);
		}
	}

	/** Builds the statistics branch shared by all three command names. */
	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource>
			statsRoot() {
		return ClientCommands.literal("stats")
			.executes(ctx -> {
				openScreen();
				return 1;
			})
			.then(ClientCommands.literal("reset").executes(ctx -> {
				SessionManager.reset();
				ctx.getSource().sendFeedback(prefixed("Session reset", ChatFormatting.YELLOW));
				return 1;
			}))
			.then(ClientCommands.literal("sparkling")
				.executes(ctx -> {
					int since = RunHistory.runsSinceLastSparkling();
					ctx.getSource().sendFeedback(prefixed(
						"Sparkling Totals: %d/%d Unique, %d Total, %d Duplicates, %d Rainbow Feathers, %s Since Last"
							.formatted(SparklingStats.unique(), Critters.total(), SparklingStats.total(),
								SparklingStats.duplicates(), SparklingStats.rainbowFeathers(),
								since < 0 ? "—" : String.valueOf(since)),
						ChatFormatting.GOLD));
					return 1;
				})
				.then(ClientCommands.literal("shared")
					.executes(ctx -> showSharedSparklings(ctx.getSource()))
					.then(ClientCommands.literal("refresh")
						.requires(source -> SharedSparklingProviders.available())
						.executes(ctx -> refreshSharedSparklings(ctx.getSource())))
					.then(ClientCommands.literal("reset").executes(ctx -> {
						SparklingMode.clearShared();
						ctx.getSource().sendFeedback(prefixed("Shared Sparkling list cleared", ChatFormatting.YELLOW));
						return 1;
					}))
					.then(ClientCommands.argument("comma-separated species", StringArgumentType.greedyString())
						.executes(ctx -> setSharedSparklings(ctx.getSource(),
							StringArgumentType.getString(ctx, "comma-separated species")))))
				.then(sparklingSetCommand())
				.then(ClientCommands.literal("feathers")
					.then(ClientCommands.argument("count", IntegerArgumentType.integer(0))
						.executes(ctx -> setSparklingFeathers(ctx.getSource(),
							IntegerArgumentType.getInteger(ctx, "count"))))));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource>
			sparklingSetCommand() {
		var set = ClientCommands.literal("set");
		for (Critter critter : Critters.all()) {
			var count = ClientCommands.argument("count", IntegerArgumentType.integer(0))
				.executes(ctx -> setSparkling(ctx.getSource(), critter,
					IntegerArgumentType.getInteger(ctx, "count")));
			String[] words = critter.name().split(" ");
			if (words.length == 1) {
				set.then(ClientCommands.literal(words[0]).then(count));
			} else {
				// Mantis Shrimp is currently the only spaced species. Separate literal
				// nodes keep Brigadier's token parsing intact and still lead to <count>.
				set.then(ClientCommands.literal(words[0])
					.then(ClientCommands.literal(words[1]).then(count)));
			}
		}
		return set;
	}

	private static int setSparkling(FabricClientCommandSource source, Critter critter, int count) {
		SparklingStats.set(critter, count);
		source.sendFeedback(prefixed(critter.name() + " Sparkling total set to " + count,
			ChatFormatting.GOLD));
		return 1;
	}

	private static int showSharedSparklings(FabricClientCommandSource source) {
		source.sendFeedback(prefixed("Shared Sparklings: " + SparklingMode.describeShared(),
			ChatFormatting.AQUA));
		return 1;
	}

	private static int refreshSharedSparklings(FabricClientCommandSource source) {
		var provider = SharedSparklingProviders.provider().orElse(null);
		if (provider == null) {
			source.sendError(prefixed("Private shared-Sparkling provider is not installed",
				ChatFormatting.RED));
			return 0;
		}
		source.sendFeedback(prefixed("Refreshing the current Safari party…", ChatFormatting.GRAY));
		provider.refreshCurrentParty().whenComplete((shared, error) ->
			Minecraft.getInstance().execute(() -> {
				if (error != null) {
					Throwable cause = error;
					while (cause.getCause() != null) cause = cause.getCause();
					source.sendError(prefixed("Shared Sparkling refresh failed: " + cause.getMessage(),
						ChatFormatting.RED));
					return;
				}
				source.sendFeedback(prefixed(
					"Shared Sparklings refreshed: " + SparklingMode.describeShared(),
					ChatFormatting.AQUA));
			}));
		return 1;
	}

	private static int setSharedSparklings(FabricClientCommandSource source, String input) {
		java.util.Set<Critter> parsed = new java.util.LinkedHashSet<>();
		java.util.List<String> unknown = new java.util.ArrayList<>();
		for (String part : input.split(",", -1)) {
			String name = part.trim();
			if (name.isEmpty()) {
				unknown.add("(empty entry)");
				continue;
			}
			Critter critter = Critters.all().stream()
				.filter(candidate -> candidate.name().equalsIgnoreCase(name))
				.findFirst().orElse(null);
			if (critter == null) unknown.add(name);
			else parsed.add(critter);
		}
		if (!unknown.isEmpty()) {
			source.sendError(prefixed("Unknown critter" + (unknown.size() == 1 ? "" : "s")
				+ ": " + String.join(", ", unknown), ChatFormatting.RED));
			return 0;
		}
		SparklingMode.replaceShared(parsed);
		return showSharedSparklings(source);
	}

	private static int setSparklingFeathers(FabricClientCommandSource source, int count) {
		SparklingStats.setRainbowFeathers(count);
		source.sendFeedback(prefixed("Rainbow Feather total set to " + count, ChatFormatting.GOLD));
		return 1;
	}

	/**
	 * Reports what the waypoint marker is doing.
	 *
	 * <p>Separates "nothing was registered" from "registered but nothing is drawn": the
	 * second means the client has the markers and the locator bar is not showing them,
	 * which is not something this mod can fix from here.
	 */
	private static void waypoints(FabricClientCommandSource source) {
		SafariConfig config = ConfigManager.get();
		source.sendFeedback(header("Waypoints"));
		source.sendFeedback(Component.literal("  on: %s%s%s%s%s%s%s%s%s%s%s".formatted(
			config.display.highlightSnooperWalls ? "snoozle " : "",
			config.display.highlightTroodonWalls ? "troodon " : "",
			config.display.highlightNests ? "nests " : "",
			config.display.hideyhoSolver ? "hideyho " : "",
			config.display.highlightHideonwalls ? "hideonwall " : "",
			config.display.highlightDuplico ? "duplico " : "",
			config.display.highlightBloodbat ? "bloodbat " : "",
			config.display.highlightHideonfloor ? "hideonfloor " : "",
			config.display.recatchHelper ? "recatch " : "",
			config.display.floorDrops ? "drops " : "",
			config.display.highlightMounds ? "mounds" : ""))
			.withStyle(ChatFormatting.GREEN));
		// Most markers are gated on the biome, so the biome is half the
		// answer to "why is nothing showing".
		source.sendFeedback(Component.literal("  in Safari   " + SafariLocation.inSafari()
			+ "  biome " + nameOf(SafariLocation.biome())).withStyle(ChatFormatting.GRAY));
		// Hideyho, Hideonwall, Duplico and Hideonfloor draw outside this list, straight
		// off their own live sightings — see WaypointRenderer — so this count is
		// everything else: floor drops, walls, mounds, recatch, hard-to-find.
		source.sendFeedback(Component.literal("  highlighted " + Markers.collect().size()
			+ "  (plus hideyho/hideonwall/duplico/hideonfloor, tracked separately)")
			.withStyle(ChatFormatting.WHITE));

		List<String> sizes = MoundSpotter.describeAll();
		source.sendFeedback(Component.literal("  interaction hitboxes nearby (mound-sized: "
			+ MoundSpotter.mounds().size() + ")").withStyle(ChatFormatting.YELLOW));
		sizes.stream().limit(12).forEach(line ->
			source.sendFeedback(Component.literal(line).withStyle(
				line.contains("<-") ? ChatFormatting.GREEN : ChatFormatting.WHITE)));

		long nests = NestTracker.nests().stream().filter(NestTracker.Nest::unpunched).count();
		source.sendFeedback(Component.literal(
			"  candidates  snooper %d · troodon %d · nests %d".formatted(
				WallTracker.SNOOPER.intactCount(), WallTracker.TROODON.intactCount(), nests))
			.withStyle(ChatFormatting.DARK_GRAY));
		// Reported whether or not the solver is on, since "is it even loaded while
		// hidden" is the thing worth checking.
		// The farthest label loaded says what Hypixel's entity tracking range is, which
		// is the only thing deciding how close you have to get. Nothing here filters by
		// distance, so whatever this reports is the server's limit, not ours.
		double farthest = 0;
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			farthest = Math.max(farthest,
				source.getPlayer().position().distanceTo(sighting.body().position()));
		}
		source.sendFeedback(Component.literal("  critters    %d loaded, farthest %.0fm"
			.formatted(CritterEntities.all().size(), farthest)).withStyle(ChatFormatting.DARK_GRAY));
		SafariBiome hereBiome = SafariLocation.biome();
		int floorDropCount = hereBiome == null ? 0 : FloorDrops.positions(hereBiome).size();
		source.sendFeedback(Component.literal("  floor drops " + floorDropCount)
			.withStyle(ChatFormatting.DARK_GRAY));
		List<RecatchSpots.ActivePin> pins = RecatchSpots.active();
		source.sendFeedback(Component.literal("  pinned      " + (pins.isEmpty()
			? "nothing" : pins.size() + ": " + pins.stream()
				.map(pin -> pin.critter().name()).collect(java.util.stream.Collectors.joining(", "))))
			.withStyle(ChatFormatting.DARK_GRAY));
		BlockPos hideyho = HideyhoSolver.position();
		source.sendFeedback(Component.literal("  hideyho     " + (hideyho == null ? "not loaded"
			: "%d %d %d".formatted(hideyho.getX(), hideyho.getY(), hideyho.getZ())))
			.withStyle(ChatFormatting.DARK_GRAY));
	}

	private static void openSettings() {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> ClientCompat.setScreen(ConfigManager.createScreen(null)));
	}

	/**
	 * Opens the run screen on the next tick. Setting it inline would be undone when
	 * the chat screen closes immediately after the command runs.
	 */
	private static void openScreen() {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> ClientCompat.setScreen(new SafariDashboardScreen()));
	}

	/**
	 * Dumps every area source so the biome detection can be pinned to real client
	 * data instead of assumptions. Run it while standing in a biome.
	 */
	private static void debug(FabricClientCommandSource source) {
		StringBuilder record = new StringBuilder();
		report(source, record, "Area sources");

		// Shown with the § codes intact, as & — the area line is recognised by its
		// formatting, so a stripped dump would hide exactly what decides the answer.
		List<String> sidebar = SafariLocation.sidebarLines();
		report(source, record, "  sidebar (" + sidebar.size() + " lines):", ChatFormatting.YELLOW);
		for (String line : sidebar) {
			report(source, record, "    | " + line.replace('§', '&'), ChatFormatting.GRAY);
		}

		// Most tab entries are player names; only metadata rows matter here.
		List<String> interesting = SafariLocation.tabListEntries().stream()
			.filter(e -> e.contains("Area") || e.contains("Biome") || e.contains("⏣")
				|| e.contains("Safari") || e.contains("Zone"))
			.toList();
		report(source, record, "  tab list matches (" + interesting.size() + "):", ChatFormatting.YELLOW);
		for (String entry : interesting) {
			report(source, record, "    | " + entry, ChatFormatting.GRAY);
		}

		Vec3 pos = source.getPlayer().position();
		report(source, record, "  position: %.1f %.1f %.1f".formatted(pos.x, pos.y, pos.z), ChatFormatting.YELLOW);
		report(source, record, "    nearest mapped node: %.1f blocks (of %d)"
			.formatted(SafariLocation.distanceToNearestNode(), SafariAreaMap.nodeCount()), ChatFormatting.GRAY);

		report(source, record, "  resolved:", ChatFormatting.YELLOW);
		report(source, record, "    island        "
			+ (SafariLocation.tabListArea() == null ? "not stated" : SafariLocation.tabListArea()),
			ChatFormatting.WHITE);
		report(source, record, "    area          "
			+ (SafariLocation.area() == null ? "not stated" : SafariLocation.area()), ChatFormatting.WHITE);
		report(source, record, "    decided by    " + SafariLocation.source(), ChatFormatting.GRAY);
		report(source, record, "    from position " + nameOf(SafariLocation.biomeFromPosition()), ChatFormatting.GRAY);
		report(source, record, "    biome         " + nameOf(SafariLocation.biome()), ChatFormatting.WHITE);
		report(source, record, "    inSafari      " + SafariLocation.inSafari(), ChatFormatting.WHITE);
		report(source, record, "    where         " + SafariLocation.where(), ChatFormatting.GRAY);
		report(source, record, "    lobby         "
			+ (SafariLocation.lobbyId() == null ? "unknown" : SafariLocation.lobbyId()), ChatFormatting.GRAY);
		report(source, record, "    essence       "
			+ (SafariLocation.safariEssence() == null ? "not present" : SafariLocation.safariEssence()),
			ChatFormatting.GRAY);
		report(source, record, "    safe mode     " + SafeMode.active()
			+ "  sparkling mode " + SparklingMode.enabled(), ChatFormatting.GRAY);
		// Which trigger opened the live run: if a stale run is showing, this says whether
		// a reset was missed or one fired and something else is wrong.
		report(source, record, "    run opened by " + SessionManager.startedBy(), ChatFormatting.WHITE);

		finishReport(source, record);
	}

	/** Dumps the complete cleaned tab list for parser diagnostics. */
	private static void tablist(FabricClientCommandSource source) {
		StringBuilder record = new StringBuilder();
		List<String> entries = SafariLocation.tabListEntries();
		report(source, record, "Tab list (" + entries.size() + " entries)");
		for (String entry : entries) {
			report(source, record, "  | " + entry, ChatFormatting.GRAY);
		}
		finishReport(source, record);
	}

	private static String nameOf(SafariBiome biome) {
		return biome == null ? "none" : biome.displayName();
	}

	/** Reports raw catch progress separately from objectives inferred to be exhausted. */
	private static void runState(FabricClientCommandSource source) {
		StringBuilder record = new StringBuilder();
		report(source, record, "Run state");
		report(source, record, "  location  area=%s biome=%s lobby=%s"
			.formatted(SafariLocation.tabListArea(), nameOf(SafariLocation.biome()),
				SafariLocation.lobbyId()), ChatFormatting.GRAY);
		report(source, record, "  active    " + (SessionManager.current() != null)
			+ "  opened by " + SessionManager.startedBy(), ChatFormatting.WHITE);
		report(source, record, "  joined    " + SafariPartyWatch.joinedPlayers() + "/4"
			+ "  sparkling mode " + SparklingMode.enabled(), ChatFormatting.GRAY);

		var session = SessionManager.current();
		if (session == null) {
			report(source, record, "  no active run", ChatFormatting.DARK_GRAY);
			finishReport(source, record);
			return;
		}
		report(source, record, "  catches   %d unique · %d total · %d attempts · %d failures"
			.formatted(session.partyUnique(), session.partyTotal(), session.totalAttempts(),
				session.totalFailures()), ChatFormatting.WHITE);
		report(source, record, "  rewards   %d essence · %d feathers · %d shards"
			.formatted(session.safariEssence(), session.rainbowFeathers(), session.totalShards()),
			ChatFormatting.GOLD);
		for (SafariBiome biome : SafariBiome.values()) {
			List<String> unavailable = Critters.inBiome(biome).stream()
				.filter(session::isUnavailable).map(Critter::name).toList();
			report(source, record, "  %-7s %d/%d caught · total %d · objective %s%s".formatted(
				biome.displayName(), session.partyUnique(biome), Critters.inBiome(biome).size(),
				session.partyTotal(biome), session.biomeComplete(biome),
				unavailable.isEmpty() ? "" : " · unavailable " + String.join(", ", unavailable)),
				ChatFormatting.GRAY);
		}
		finishReport(source, record);
	}

	/** Shows every label/body pairing used by hitboxes, Near counts, and static tracking. */
	private static void critterPairings(FabricClientCommandSource source) {
		StringBuilder record = new StringBuilder();
		List<CritterEntities.Sighting> sightings = CritterEntities.all();
		report(source, record, "Critter pairings (" + sightings.size() + ")");
		for (CritterEntities.Sighting sighting : sightings) {
			Entity label = sighting.label();
			Entity body = sighting.mob();
			String bodyText = body == null ? "unpaired" : "%s %s @ %d %d %d".formatted(
				entityType(body), shortId(body), body.blockPosition().getX(),
				body.blockPosition().getY(), body.blockPosition().getZ());
			boolean visible = body != null && VisibilityCheck.canSee(body);
			boolean persistent = body != null && StillCritters.persistentThroughWalls(body.getUUID());
			report(source, record, "  %s%s label=%s @ %d %d %d -> %s · visible=%s persistent=%s"
				.formatted(sighting.critter().name(), SparklingWatch.isSparkling(sighting)
					? " [SPARKLING:" + ParticleDiagnostics.source(sighting) + "]" : "",
					shortId(label), label.blockPosition().getX(), label.blockPosition().getY(),
					label.blockPosition().getZ(), bodyText, visible, persistent),
				body == null ? ChatFormatting.RED : ChatFormatting.WHITE);
		}
		finishReport(source, record);
	}

	/** Summarises bundled plus locally learned positions and their current rendered state. */
	private static void catalogs(FabricClientCommandSource source) {
		StringBuilder record = new StringBuilder();
		report(source, record, "Static catalogs");
		for (SafariBiome biome : SafariBiome.values()) {
			report(source, record, "  floor/%-7s catalog=%d visible=%d confirmed=%d".formatted(
				biome.displayName(), StaticWaypointCatalog.floorDrops(biome).size(),
				FloorDrops.positions(biome).size(), FloorDrops.remainingConfirmed(biome)),
				ChatFormatting.GRAY);
		}
		report(source, record, "  nests      catalog=%d visible=%d unpunched=%d".formatted(
			StaticWaypointCatalog.nests().size(), NestTracker.nests().size(),
			NestTracker.unpunchedCount()), ChatFormatting.GRAY);
		report(source, record, "  mounds     catalog=%d visible=%d ever-detected=%s".formatted(
			StaticWaypointCatalog.mounds().size(), MoundSpotter.mounds().size(),
			MoundSpotter.everDetectedAny()), ChatFormatting.GRAY);
		for (String name : List.of("Duplico", "Hideonwall", "Hideonfloor", "Bloodbat", "Hideyho")) {
			Critter critter = Critters.byName(name);
			int remembered = critter == null ? 0 : StillCritters.entriesFor(critter).size();
			int possible = critter == null ? 0 : StillCritters.candidatesFor(critter).size();
			report(source, record, "  %-11s catalog=%d possible=%d confirmed=%d".formatted(name,
				StaticEntityCatalog.positions(name).size(), possible, remembered), ChatFormatting.GRAY);
		}
		finishReport(source, record);
	}

	private static String entityType(Entity entity) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
	}

	private static String shortId(Entity entity) {
		return entity.getUUID().toString().substring(0, 8);
	}

	/**
	 * Dumps nearby entities so critter mobs can be identified from real data.
	 *
	 * <p>Groundwork for counting how many of a species actually spawned this run: most
	 * species spawn a randomised number, so no static table can say how many there are
	 * to catch. Seeing them as entities could.
	 */
	private static void entities(FabricClientCommandSource source) {
		Minecraft client = source.getClient();
		if (client.level == null) {
			source.sendError(prefixed("No world loaded", ChatFormatting.RED));
			return;
		}

		Vec3 origin = source.getPlayer().position();
		List<String> matched = new ArrayList<>();
		// Named things that are not a species are the interesting ones: shells and the
		// like have to announce themselves somehow, and this is where they would show.
		Map<String, Integer> otherNames = new TreeMap<>();
		Map<String, Integer> byType = new TreeMap<>();
		int total = 0;

		for (Entity entity : client.level.entitiesForRendering()) {
			double distance = Math.sqrt(entity.position().distanceToSqr(origin));
			if (distance > ENTITY_SCAN_RADIUS) continue;
			total++;

			String type = entity.getType().toString();
			type = type.substring(type.lastIndexOf('.') + 1);
			byType.merge(type, 1, Integer::sum);

			String custom = entity.hasCustomName() ? stripCodes(entity.getCustomName().getString()) : "";
			String display = stripCodes(entity.getDisplayName().getString());
			String label = !custom.isEmpty() ? custom : display;
			if (label.isEmpty()) continue;

			Critter critter = Critters.byName(label);
			if (critter != null) {
				matched.add("  %-13s %-9s %.0fm  uuid %s".formatted(
					critter.name(), type, distance, entity.getUUID().toString().substring(0, 8)));
				continue;
			}
			// Unnamed entities fall back to their type as a display name; that is noise.
			if (label.equalsIgnoreCase(type.replace('_', ' '))) continue;
			otherNames.merge(label + "  [" + type + "]", 1, Integer::sum);
		}

		StringBuilder record = new StringBuilder();
		report(source, record, "Entities within %.0f blocks: %d".formatted(ENTITY_SCAN_RADIUS, total));
		report(source, record, "  species name tags: " + matched.size(), ChatFormatting.YELLOW);
		matched.stream().limit(14).forEach(line -> report(source, record, line, ChatFormatting.WHITE));

		report(source, record, "  other named entities: " + otherNames.size(), ChatFormatting.YELLOW);
		otherNames.entrySet().stream().limit(20).forEach(e ->
			report(source, record, "  %dx %s".formatted(e.getValue(), e.getKey()), ChatFormatting.AQUA));

		report(source, record, "  types: " + byType, ChatFormatting.DARK_GRAY);
		finishReport(source, record);
	}

	/** Dumps nearby entities of every type to identify critter props and containers. */
	private static void nearby(FabricClientCommandSource source) {
		Minecraft client = source.getClient();
		if (client.level == null) {
			source.sendError(prefixed("No world loaded", ChatFormatting.RED));
			return;
		}

		Vec3 origin = source.getPlayer().position();
		record Near(double distance, String line) {
		}
		List<Near> found = new ArrayList<>();

		for (Entity entity : client.level.entitiesForRendering()) {
			double distance = Math.sqrt(entity.position().distanceToSqr(origin));
			if (distance > NEARBY_SCAN_RADIUS) continue;

			String type = entity.getType().toString();
			type = type.substring(type.lastIndexOf('.') + 1);
			String custom = entity.hasCustomName() ? stripCodes(entity.getCustomName().getString()) : "";
			String display = stripCodes(entity.getDisplayName().getString());
			// The display name falls back to the type for unnamed entities, which is
			// noise here; only show it when it says something the type does not.
			String label = !custom.isEmpty() ? custom
				: display.equalsIgnoreCase(type.replace('_', ' ')) ? "" : display;

			found.add(new Near(distance, "  %-16s %4.1fm  %d %d %d  %s".formatted(
				type, distance,
				entity.blockPosition().getX(), entity.blockPosition().getY(),
				entity.blockPosition().getZ(),
				label.isEmpty() ? "-" : label)));
		}

		found.sort((a, b) -> Double.compare(a.distance(), b.distance()));
		StringBuilder record = new StringBuilder();
		report(source, record, "Closest entities within %.0f blocks".formatted(NEARBY_SCAN_RADIUS));
		found.stream().limit(18).forEach(n -> report(source, record, n.line(), ChatFormatting.WHITE));
		if (found.isEmpty()) {
			report(source, record, "  nothing in range", ChatFormatting.DARK_GRAY);
		}
		finishReport(source, record);
	}

	/**
	 * Reports the block under the crosshair, and what surrounds it.
	 *
	 * <p>Reports an entity or a block, whichever it is. A mound draws an entity outline
	 * and no block outline, so it is an entity of some kind — naming its type is the one
	 * thing needed before mounds can be counted.
	 */
	private static void block(FabricClientCommandSource source) {
		Minecraft client = source.getClient();
		if (client.level == null) {
			source.sendError(prefixed("No world loaded", ChatFormatting.RED));
			return;
		}
		if (client.hitResult == null || client.hitResult.getType() == HitResult.Type.MISS) {
			source.sendError(prefixed("Look directly at something first", ChatFormatting.RED));
			return;
		}

		StringBuilder record = new StringBuilder();

		// A mound draws an entity outline rather than a block outline, so whatever the
		// crosshair is on has to be reported either way.
		if (client.hitResult.getType() == HitResult.Type.ENTITY) {
			Entity hit = ((EntityHitResult) client.hitResult).getEntity();
			report(source, record, "Entity under crosshair");
			report(source, record, "  type    "
				+ BuiltInRegistries.ENTITY_TYPE.getKey(hit.getType()), ChatFormatting.WHITE);
			report(source, record, "  name    "
				+ (hit.hasCustomName() ? stripCodes(hit.getCustomName().getString()) : "(none)"),
				ChatFormatting.AQUA);
			report(source, record, "  display " + stripCodes(hit.getDisplayName().getString()), ChatFormatting.GRAY);
			report(source, record, "  at      %d %d %d   uuid %s".formatted(
				hit.blockPosition().getX(), hit.blockPosition().getY(), hit.blockPosition().getZ(),
				hit.getUUID().toString().substring(0, 8)), ChatFormatting.GRAY);
			report(source, record, "  exact   %.3f %.3f %.3f   velocity %.3f %.3f %.3f".formatted(
				hit.getX(), hit.getY(), hit.getZ(), hit.getDeltaMovement().x,
				hit.getDeltaMovement().y, hit.getDeltaMovement().z), ChatFormatting.DARK_GRAY);
			report(source, record, "  glowing %s   invisible %s   box %.2f x %.2f".formatted(
				hit.isCurrentlyGlowing(), hit.isInvisible(),
				hit.getBoundingBox().getXsize(), hit.getBoundingBox().getYsize()), ChatFormatting.DARK_GRAY);

			CritterEntities.all().stream()
				.filter(sighting -> sighting.label().getUUID().equals(hit.getUUID())
					|| sighting.mob() != null && sighting.mob().getUUID().equals(hit.getUUID()))
				.findFirst().ifPresent(sighting -> report(source, record,
					"  pairing %s%s  label=%s  body=%s".formatted(sighting.critter().name(),
						SparklingWatch.isSparkling(sighting)
							? " [SPARKLING:" + ParticleDiagnostics.source(sighting) + "]" : "",
						sighting.mob() == null ? "unpaired" : shortId(sighting.mob())),
					ChatFormatting.YELLOW));

			// A mound may well be a head on an armor stand, and they come in different
			// sizes — so the small flag, the scale attribute and what is worn on the
			// head are the three things that would identify one.
			if (hit instanceof LivingEntity living) {
				double scale = living.getAttributes().hasAttribute(Attributes.SCALE)
					? living.getAttributeValue(Attributes.SCALE) : 1.0;
				String small = hit instanceof ArmorStand stand
					? "small %s  marker %s".formatted(stand.isSmall(), stand.isMarker()) : "-";
				report(source, record, "  scale %.2f   %s".formatted(scale, small), ChatFormatting.DARK_GRAY);

				for (EquipmentSlot slot : EquipmentSlot.values()) {
					ItemStack worn = living.getItemBySlot(slot);
					if (worn.isEmpty()) continue;
					report(source, record, "  %-10s %s  x%d".formatted(
						slot.getName(), stripCodes(worn.getHoverName().getString()), worn.getCount()),
						ChatFormatting.AQUA);
				}
			}
			finishReport(source, record);
			return;
		}

		BlockPos pos = ((BlockHitResult) client.hitResult).getBlockPos();
		BlockState state = client.level.getBlockState(pos);

		report(source, record, "Block at %d %d %d".formatted(pos.getX(), pos.getY(), pos.getZ()));
		report(source, record, "  id    " + BuiltInRegistries.BLOCK.getKey(state.getBlock()), ChatFormatting.WHITE);
		report(source, record, "  state " + state, ChatFormatting.GRAY);

		// A mound is a cluster, so the neighbouring make-up says whether one block type
		// is the whole thing or just its surface.
		Map<String, Integer> around = new TreeMap<>();
		for (BlockPos near : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
			BlockState neighbour = client.level.getBlockState(near);
			if (neighbour.isAir()) continue;
			around.merge(BuiltInRegistries.BLOCK.getKey(neighbour.getBlock()).getPath(), 1, Integer::sum);
		}
		report(source, record, "  within 2 blocks: " + around, ChatFormatting.DARK_GRAY);
		finishReport(source, record);
	}

	private static String stripCodes(String text) {
		return text.replaceAll("\u00a7.", "").replaceAll("[\\p{Cf}\\p{Co}]", "").trim();
	}

	/**
	 * Runs {@code action} if Advanced has been unlocked this session, otherwise says
	 * so and does nothing else — the one gate every diagnostic command but
	 * {@code unlock} itself goes through.
	 */
	private static void requireUnlocked(FabricClientCommandSource source, Runnable action) {
		if (!AdvancedUnlock.isUnlocked()) {
			source.sendError(prefixed("Advanced settings are locked this session", ChatFormatting.RED));
			return;
		}
		action.run();
	}

	private static Component header(String text) {
		return Component.literal("[SafariUtils] ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal(text).withStyle(ChatFormatting.YELLOW));
	}

	/**
	 * Sends one line of a debug report to chat, styled, and records its plain text
	 * for the clipboard copy the report ends with — one call standing in for what
	 * would otherwise be a {@code sendFeedback} and a separate append kept in sync
	 * by hand at every single line of a report.
	 */
	private static void report(FabricClientCommandSource source, StringBuilder record,
								String text, ChatFormatting style) {
		source.sendFeedback(Component.literal(text).withStyle(style));
		record.append(text).append('\n');
	}

	private static void report(FabricClientCommandSource source, StringBuilder record, String header) {
		source.sendFeedback(header(header));
		record.append(header).append('\n');
	}

	/** Copies the finished report and confirms it, ending a debug command's output. */
	private static void finishReport(FabricClientCommandSource source, StringBuilder record) {
		Minecraft.getInstance().keyboardHandler.setClipboard(record.toString());
		source.sendFeedback(prefixed("Copied to clipboard",
			ChatFormatting.GREEN));
	}

	private static Component prefixed(String text, ChatFormatting colour) {
		return Component.literal("[SafariUtils] ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal(text).withStyle(colour));
	}
}
