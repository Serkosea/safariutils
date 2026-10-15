package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.parse.CritterEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Real tracker -> live effect adapter -> immutable presentation, without a Minecraft world. */
public final class CapturePresentationRegression {
	public static int run() throws Exception {
		var config = ConfigManager.class.getDeclaredField("config");
		config.setAccessible(true);
		Object previous = config.get(null);
		var catalog = StaticEntityCatalog.class.getDeclaredField("local");
		catalog.setAccessible(true);
		Object previousCatalog = catalog.get(null);
		var constructor = catalog.getType().getDeclaredConstructor();
		constructor.setAccessible(true);
		catalog.set(null, constructor.newInstance());
		var settings = new SafariConfig();
		settings.advanced.disableAutomaticLogging = true;
		settings.display.eagleRarity = 0;
		config.set(null, settings);
		int checks = 0;
		try {
			for (var species : Critters.all()) {
				if (species.name().equals("Hideyho")) continue;
				for (boolean master : new boolean[] {false, true}) {
					CritterState.onVisitStarted(); RecatchSpots.clear();
					UUID target = new UUID(30, 1), neighbor = new UUID(30, 2);
					var box = new AABB(0, 0, 0, 1.2, 1.5, 1.2);
					// Feed actual production label/body assignment into the capture pipeline.
					String type = bodyType(species.name());
					var samples = List.of(
						new CritterScan.Sample(new UUID(31, 1), "armor_stand", species.name(), .6, 1.5, .6),
						new CritterScan.Sample(target, type, null, .6, 0, .6),
						new CritterScan.Sample(new UUID(31, 2), "armor_stand", species.name(), 2.6, 1.5, .6),
						new CritterScan.Sample(neighbor, type, null, 2.6, 0, .6));
					var scanned = CritterScan.scan(samples, java.util.Map.of(), null);
					check(scanned.size() == 2 && scanned.get(0).body().id().equals(target)
						&& scanned.get(1).body().id().equals(neighbor), species.name() + ": scanner changed target ownership"); checks++;
					var bodies = scanned.stream().map(match -> new CritterState.BodyObservation(match.critter(),
						match.label().id(), match.body().id(), match.body().id().equals(target) ? box : box.move(2, 0, 0), match.sparkling())).toList();
					CritterState.observeBodies(bodies, 1000);
					CritterState.observeInteraction(target, box.getCenter(), 1010);
					CritterState.applyEvent(new CritterEvent(CritterEvent.Type.ATTEMPT, species, null, 1, false), master, 1020,
						new CritterState.Aim(new Vec3(.6, .75, -3), new Vec3(0, 0, 1)));
					boolean guaranteed = master || CritterCatchRules.guaranteedWithoutPity(species);
					var provisional = RecatchSpots.snapshot(true, Set.of());
					check(!provisional.isAnimated(neighbor), "provisional selection animated untouched neighbor"); checks++;
					if (guaranteed) {
						check(!provisional.isAnimated(target), "grouped guarantee animated before physical confirmation"); checks++;
					}
					// Tight groups need physical confirmation, not merely an aimed interaction.
					if (master) {
						CritterState.advanceCaptureEvidence(List.of(new CritterState.CapsuleObservation(
							new UUID(33, 1), box.getCenter())), List.of(), 1021);
						var contact = RecatchSpots.snapshot(true, Set.of());
						check(contact.isAnimated(target) && !contact.suppresses(neighbor),
							species.name() + ": first exclusive capsule contact did not start owned shrink"); checks++;
					}
					CritterState.observeBodies(List.of(bodies.get(1)), 1025);
					CritterState.advanceCaptureEvidence(List.of(), List.of(new CritterState.CaptureLabelObservation(
						species, new UUID(32, 1), box.getCenter())), 1026);
					var snapshot = RecatchSpots.snapshot(true, Set.of());
					check(!snapshot.suppresses(neighbor), species.name() + ": untouched neighbor suppressed"); checks++;
					check(snapshot.isAnimated(target) == guaranteed && snapshot.isPinned(target) != guaranteed,
						species.name() + ": wrong presentation mode"); checks++;
					var expected = CritterMarkerGeometry.recatch(species, box);
					var actual = guaranteed ? snapshot.shrinkStates().getFirst().box() : snapshot.pins().getFirst().box();
					check(actual.equals(expected), species.name() + ": geometry changed across effect boundary"); checks++;
					checks += frames(snapshot, target, neighbor);
					CritterState.observeBodies(List.of(bodies.get(1)), 1030);
					var refreshed = RecatchSpots.snapshot(true, Set.of());
					if (guaranteed) {
						check(snapshot.shrinkStates().getFirst().animation().equals(refreshed.shrinkStates().getFirst().animation()),
							species.name() + ": observation restarted animation"); checks++;
					}
					CritterState.confirmCatchTotal(species, 100, 1040);
					var completed = RecatchSpots.snapshot(true, Set.of());
					check(completed.pins().isEmpty() && completed.shrinkStates().isEmpty(), species.name() + ": completion left visuals"); checks++;
					check(completed.shrinking(Long.MAX_VALUE, .5f, (state, partial, scale) -> {
						throw new AssertionError("completed attempt requested body projection");
					}).isEmpty(), "completed attempt emitted frame"); checks++;
					check(snapshot.suppresses(target), "published snapshot mutated after completion"); checks++;
				}
			}
			checks += overlappingContact();
			return checks;
		} finally {
			CritterState.onVisitStarted(); RecatchSpots.clear(); config.set(null, previous);
			catalog.set(null, previousCatalog);
			var cache = StaticEntityCatalog.class.getDeclaredField("positionCache");
			cache.setAccessible(true);
			((java.util.Map<?, ?>) cache.get(null)).clear();
		}
	}

	/** Exercises the actual frame iteration using snapshots emitted by the real tracker/effects. */
	private static int frames(RecatchSpots.PresentationSnapshot snapshot, UUID target, UUID neighbor) {
		if (snapshot.shrinkStates().isEmpty()) {
			check(snapshot.shrinking(0, .5f, (state, partial, scale) -> {
				throw new AssertionError("recatch pin entered guaranteed-catch projection");
			}).isEmpty(), "pin-only snapshot emitted shrink");
			return 1;
		}
		var original = snapshot.shrinkStates().getFirst();
		var clock = original.animation();
		int checks = 0;
		for (int frame = 0; frame <= 120; frame++) {
			long now = clock.startedAtNanos() + clock.durationNanos() * frame / 110;
			double progress = Math.min(1, frame / 110.0);
			double steppedSize = Math.max(.01, 1 - Math.floor(progress * 10) / 10);
			double x = frame * .02, y = 2 + frame * .01, z = -frame * .03;
			var body = new AABB(x - steppedSize, y, z - steppedSize,
				x + steppedSize, y + steppedSize, z + steppedSize);
			int[] calls = {0};
			var projected = snapshot.shrinking(now, .25f, (state, partial, scale) -> {
				calls[0]++;
				check(state.entityId().equals(target) && !state.entityId().equals(neighbor),
					"frame adapter received untouched neighbor");
				check(partial == .25f, "frame interpolation fraction changed");
				return CritterMarkerGeometry.captureFrame(state.critter(), state.box(), body,
					-.125, .0625, .25, scale);
			});
			if (frame >= 110) {
				check(projected.isEmpty() && calls[0] == 0, "finished animation sampled world or emitted tiny remnant");
				checks++;
				continue;
			}
			check(projected.size() == 1 && calls[0] == 1, "owned capture did not project exactly once"); checks++;
			checks += 2; // adapter ownership and partial-tick assertions
			var output = projected.getFirst();
			check(output.entityId().equals(target) && output.critter().equals(original.critter()),
				"frame output changed identity"); checks++;
			var box = output.box();
			check(close(box.getXsize(), original.box().getXsize() * (1 - progress))
				&& close(box.getYsize(), original.box().getYsize() * (1 - progress))
				&& close(box.getZsize(), original.box().getZsize() * (1 - progress)),
				"frame output inherited stepped native dimensions"); checks++;
			double speciesOffset = original.critter().name().equals("Driftling") ? -.25 : 0;
			check(close(box.getCenter().x, x - .125) && close(box.minY, y + speciesOffset + .0625)
				&& close(box.getCenter().z, z + .25), "frame did not follow owned moving body"); checks++;
			check(snapshot.shrinkStates().getFirst().equals(original) && !snapshot.suppresses(neighbor),
				"render mutated ownership or restarted clock"); checks++;
		}
		return checks;
	}
	private static boolean close(double a, double b) { return Math.abs(a - b) < 1e-8; }
	private static int overlappingContact() {
		CritterState.onVisitStarted(); RecatchSpots.clear();
		var species = Critters.byName("Bloodbat");
		UUID a = new UUID(40, 1), b = new UUID(40, 2);
		var box = new AABB(0, 0, 0, 1, 1, 1);
		CritterState.observeBodies(List.of(
			new CritterState.BodyObservation(species, new UUID(41, 1), a, box, false),
			new CritterState.BodyObservation(species, new UUID(41, 2), b, box.move(.5, 0, 0), false)), 1000);
		CritterState.observeInteraction(a, box.getCenter(), 1010);
		CritterState.applyEvent(new CritterEvent(CritterEvent.Type.ATTEMPT, species, null, 1, false), true, 1020,
			new CritterState.Aim(new Vec3(.5, .5, -3), new Vec3(0, 0, 1)));
		CritterState.advanceCaptureEvidence(List.of(new CritterState.CapsuleObservation(new UUID(42, 1), box.getCenter())), List.of(), 1021);
		var snapshot = RecatchSpots.snapshot(true, Set.of());
		check(!snapshot.isAnimated(a) && !snapshot.isAnimated(b), "ambiguous initial capsule animated a peer");
		check(CritterState.capsuleTouches(box, null, box.getCenter()), "initial inside contact missed");
		check(CritterState.capsuleTouches(box, new Vec3(-2, .5, .5), new Vec3(2, .5, .5)), "crossing contact missed");
		check(!CritterState.capsuleTouches(box, null, new Vec3(5, 5, 5)), "distant point treated as contact");
		return 4;
	}
	/** Independent recorded species fixtures, deliberately not derived from the production policy. */
	private static String bodyType(String species) {
		return switch (species) {
			case "Foxtrot" -> "fox";
			case "Bluebird", "Parakeet", "Macaw" -> "parrot";
			case "Honeybug" -> "bee";
			case "Treefrog" -> "frog";
			case "Woodchucker" -> "creaking";
			case "Fluffling" -> "panda";
			case "Hideonfloor", "Hideonwall" -> "shulker";
			case "Cavernfish", "Mantis Shrimp", "Gimmiegold", "Tepid" -> "tropical_fish";
			case "Flitter", "Bloodbat" -> "bat";
			case "Shyworm" -> "slime";
			case "Driftling", "Chuckwalla", "Rockmite", "Troodon" -> "silverfish";
			case "Scrappy" -> "armadillo";
			case "Nozzlenose" -> "dolphin";
			case "Wumpa" -> "ravager";
			case "Billygoat" -> "goat";
			case "Gemzie" -> "vex";
			case "Polaris" -> "polar_bear";
			case "Shuddersquid" -> "glow_squid";
			case "Snoozle" -> "sniffer";
			case "Strongarm" -> "snow_golem";
			case "Areita" -> "cave_spider";
			case "Gazer" -> "armor_stand";
			case "Litterbug" -> "endermite";
			case "Solsnatcher" -> "phantom";
			case "Duplico" -> "interaction";
			case "Doomspiral" -> "warden";
			default -> throw new AssertionError("Missing species body fixture: " + species);
		};
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
	private CapturePresentationRegression() { }
}
