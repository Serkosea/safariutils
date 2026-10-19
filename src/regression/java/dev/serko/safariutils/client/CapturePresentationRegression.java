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
		settings.advanced.enableAutomaticLogging = false;
		settings.display.eagleRarity = 0;
		config.set(null, settings);
		int checks = 0;
		try {
			var titleCritter = Critters.byName("Hideonwall");
			settings.display.hitboxPityTitle = false;
			settings.display.recatchPityTitle = true;
			check(Markers.pityLabel(titleCritter, new UUID(29, 1)).isEmpty(),
				"legacy recatch preference bypassed shared pity toggle"); checks++;
			settings.display.hitboxPityTitle = true;
			settings.display.recatchPityTitle = false;
			check(!Markers.pityLabel(titleCritter, new UUID(29, 1)).isEmpty(),
				"legacy recatch preference suppressed shared pity title"); checks++;
			settings.display.hitboxEntityColorOverride = false;
			settings.display.hideonwallColour = "#123456";
			check(WaypointRenderer.critterHitboxColour(Critters.byName("Hideonwall"), false) == 0xFF123456,
				"capture color resolver lost dedicated Hideon color"); checks++;
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
			checks += hideonRecatches();
			checks += returningHelperLabel();
			checks += compoundHideonRetries();
			checks += continuousHideonFormRetry();
			checks += hideyhoMarkerEligibility(settings);
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
	private static int continuousHideonFormRetry() {
		int checks = 0;
		for (String name : List.of("Hideonfloor", "Hideonwall")) {
			CritterState.onVisitStarted(); RecatchSpots.clear();
			var species = Critters.byName(name);
			var box = new AABB(0, 0, 0, 1, 1, 1);
			var aim = new CritterState.Aim(new Vec3(.5, .5, -3), new Vec3(0, 0, 1));
			UUID initial = new UUID(95, 1), returned = new UUID(95, 2), carrier = new UUID(95, 3);
			CritterState.observeBodies(List.of(new CritterState.BodyObservation(species, initial, initial, box, false)), 1000);
			CritterState.applyEvent(new CritterEvent(CritterEvent.Type.ATTEMPT, species, null, 1, false), false, 1010, aim);
			CritterState.observeBodies(List.of(), 1500);
			CritterState.applyEvent(new CritterEvent(CritterEvent.Type.FAILED, species, null, 1, false), false, 3000, aim);
			CritterState.observeBodies(List.of(new CritterState.BodyObservation(species, returned, returned, box, false)), 3500);
			CritterState.applyEvent(new CritterEvent(CritterEvent.Type.ATTEMPT, species, null, 1, false), false, 3675, aim);
			CritterState.observeBodies(List.of(new CritterState.BodyObservation(species, carrier, carrier,
				new AABB(.3, 0, .3, .7, .3, .7), false)), 3700);
			var logical = CritterState.logicalCritters().stream().filter(v -> carrier.equals(v.entityId())).findFirst().orElseThrow();
			check(logical.pity() == 2, name + ": form substitution reset retry pity"); checks++;
			CritterState.observeBodies(List.of(), 4200);
			CritterState.applyEvent(new CritterEvent(CritterEvent.Type.OWN_CATCH, species, null, 1, false), false, 6000, aim);
			check(RecatchSpots.snapshot(true, Set.of()).pins().isEmpty(), name + ": caught form substitution retained pin"); checks++;
		}
		return checks;
	}

	private static int compoundHideonRetries() {
		int checks = 0;
		for (boolean offset : List.of(false, true)) for (String name : List.of("Hideonwall", "Hideonfloor")) {
			CritterState.onVisitStarted(); RecatchSpots.clear();
			var species = Critters.byName(name);
			var box = new AABB(0, 0, 0, 1, 1, 1);
			var aim = new CritterState.Aim(new Vec3(.5, .5, -3), new Vec3(0, 0, 1));
			long owner = -1;
			for (int attempt = 1; attempt <= 3; attempt++) {
				long now = 1000 + attempt * 3000L;
				UUID shell = new UUID(90, attempt), carrier = new UUID(91, attempt);
				UUID shellLabel = new UUID(92, attempt), carrierLabel = new UUID(93, attempt);
				var scan = CritterScan.scan(List.of(
					new CritterScan.Sample(carrierLabel, "armor_stand", name, .5, .5, .5),
					new CritterScan.Sample(carrier, "silverfish", null, offset ? .96875 : .5, 0, offset ? .0625 : .5),
					new CritterScan.Sample(shellLabel, "armor_stand", name, .5, 1.5, .5),
					new CritterScan.Sample(shell, "shulker", null, .5, 0, .5)),
					java.util.Map.of(), null);
				CritterState.observeBodies(scan.stream().map(match -> new CritterState.BodyObservation(
					match.critter(), match.label().id(), match.body().id(), box, match.sparkling())).toList(), now);
				CritterState.applyEvent(new CritterEvent(CritterEvent.Type.ATTEMPT, species, null, 1, false), false, now + 1, aim);
				var logical = CritterState.logicalCritters().stream().filter(v -> shell.equals(v.entityId())).findFirst().orElseThrow();
				if (owner == -1) owner = logical.id();
				check(logical.id() == owner && logical.pity() == attempt, name + ": compound return reset pity"); checks++;
				check(RecatchSpots.snapshot(true, Set.of()).isAnimated(shell) == (attempt == 3),
					name + ": final compound retry did not use guaranteed shrink"); checks++;
				CritterState.observeBodies(List.of(), now + 100);
				CritterState.applyEvent(new CritterEvent(attempt == 3 ? CritterEvent.Type.OWN_CATCH
					: CritterEvent.Type.FAILED, species, null, 1, false), false, now + 500, aim);
			}
			check(RecatchSpots.snapshot(true, Set.of()).pins().isEmpty(), "compound catch left a pin"); checks++;
		}
		return checks;
	}

	private static int hideyhoMarkerEligibility(SafariConfig settings) throws Exception {
		var position = HideyhoSolver.class.getDeclaredField("position");
		var confirmed = HideyhoSolver.class.getDeclaredField("visiblyConfirmed");
		position.setAccessible(true); confirmed.setAccessible(true);
		Object oldPosition = position.get(null), oldConfirmed = confirmed.get(null);
		boolean oldMarkers = settings.display.enableHitboxes, oldMode = settings.sparkling.sparklingMode;
		boolean oldOnly = settings.sparkling.sparklingOnlyShowSparkling;
		long oldAlways = settings.sparkling.sparklingAlwaysActiveCritters;
		try {
			position.set(null, new net.minecraft.core.BlockPos(0, 80, 0)); confirmed.setBoolean(null, true);
			settings.display.enableHitboxes = true;
			settings.sparkling.sparklingMode = false;
			check(HideyhoSolver.markerEnabled(), "confirmed Hideyho marker eligible");
			settings.display.enableHitboxes = false;
			check(!HideyhoSolver.markerEnabled(), "disabled markers must silence Hideyho");
			settings.display.enableHitboxes = true;
			settings.sparkling.sparklingMode = true;
			settings.sparkling.sparklingOnlyShowSparkling = true;
			settings.sparkling.sparklingAlwaysActiveCritters = 0;
			check(!HideyhoSolver.markerEnabled(), "hidden ordinary Hideyho must be silent");
			settings.sparkling.sparklingAlwaysActiveCritters = Critters.allSelectionMask();
			check(HideyhoSolver.markerEnabled(), "always-active Hideyho remains eligible");
			position.set(null, null);
			check(!HideyhoSolver.markerEnabled(), "unconfirmed Hideyho must be silent");
			return 5;
		} finally {
			position.set(null, oldPosition); confirmed.set(null, oldConfirmed);
			settings.display.enableHitboxes = oldMarkers; settings.sparkling.sparklingMode = oldMode;
			settings.sparkling.sparklingOnlyShowSparkling = oldOnly;
			settings.sparkling.sparklingAlwaysActiveCritters = oldAlways;
		}
	}

	private static int returningHelperLabel() {
		int checks = 0;
		for (String name : List.of("Hideonwall", "Hideonfloor")) {
			CritterState.onVisitStarted(); RecatchSpots.clear();
			var species = Critters.byName(name);
			UUID original = new UUID(80, 1), helper = new UUID(80, 2), returned = new UUID(80, 3);
			UUID label = new UUID(81, 1), helperLabel = new UUID(81, 2);
			var box = new AABB(0, 0, 0, 1, 1, 1);
			var aim = new CritterState.Aim(new Vec3(.5, .5, -3), new Vec3(0, 0, 1));
			CritterState.observeBodies(List.of(new CritterState.BodyObservation(species, label, original, box, false)), 1000);
			long identity = CritterState.logicalCritters().getFirst().id();
			CritterState.applyEvent(new CritterEvent(CritterEvent.Type.ATTEMPT, species, null, 1, false), false, 1200, aim);
			CritterState.observeBodies(List.of(
				new CritterState.BodyObservation(species, label, original, new AABB(.3, 0, .3, .7, .4, .7), false),
				new CritterState.BodyObservation(species, helperLabel, helper, new AABB(.3, 0, .3, .7, .3, .7), false)), 1300);
			CritterState.observeBodies(List.of(), 1400);
			CritterState.applyEvent(new CritterEvent(CritterEvent.Type.FAILED, species, null, 1, false), false, 1500, aim);
			CritterState.observeBodies(List.of(new CritterState.BodyObservation(species, helperLabel, returned, box, false)), 1600);
			var result = CritterState.logicalCritters().stream().filter(v -> returned.equals(v.entityId())).findFirst().orElseThrow();
			check(result.id() == identity && result.pity() == 1, name + ": helper label stole returning body's pity"); checks++;
			CritterState.observeInteraction(returned, box.getCenter(), 1610);
			CritterState.applyEvent(new CritterEvent(CritterEvent.Type.ATTEMPT, species, null, 1, false), false, 1620, aim);
			check(CritterState.pityFor(returned) == 2, name + ": second attempt failed to advance to 2/3"); checks++;
			check(!RecatchSpots.snapshot(true, Set.of()).isPinned(helper), name + ": helper left stale pin"); checks++;
		}
		return checks;
	}

	private static int hideonRecatches() {
		int checks = 0;
		for (String name : List.of("Hideonwall", "Hideonfloor")) {
			CritterState.onVisitStarted(); RecatchSpots.clear();
			var species = Critters.byName(name);
			var box = new AABB(0, 0, 0, 1, 1, 1);
			var aim = new CritterState.Aim(new Vec3(.5, .5, -3), new Vec3(0, 0, 1));
			long identity = -1;
			for (int attempt = 1; attempt <= 3; attempt++) {
				long now = 1000 + attempt * 3000L;
				UUID body = new UUID(50, attempt), helper = new UUID(51, attempt), label = new UUID(52, attempt);
				var samples = List.of(
					new CritterScan.Sample(label, "armor_stand", name, .5, 1, .5),
					new CritterScan.Sample(helper, "silverfish", null, .5, .5, .5),
					new CritterScan.Sample(body, "shulker", null, .5, 0, .5));
				// Even an old retained helper assignment must yield to the actual returning body.
				var scan = CritterScan.scan(samples, java.util.Map.of(label, helper), null);
				check(scan.size() == 1 && scan.getFirst().body().id().equals(body), name + ": helper stole body"); checks++;
				CritterState.observeBodies(List.of(new CritterState.BodyObservation(species, label, body, box, false)), now);
				CritterState.observeInteraction(body, box.getCenter(), now + 1);
				CritterState.applyEvent(new CritterEvent(CritterEvent.Type.ATTEMPT, species, null, 1, false), false, now + 2, aim);
				var logical = CritterState.logicalCritters().stream().filter(view -> body.equals(view.entityId())).findFirst().orElseThrow();
				if (identity == -1) identity = logical.id();
				check(logical.id() == identity && logical.pity() == attempt, name + ": instant retry split pity identity"); checks++;
				var helpersOnly = CritterScan.scan(samples.subList(0, 2), java.util.Map.of(label, helper), null);
				// A silverfish without the concealed body can be the awakened critter.
				// Type alone cannot classify that legitimate moving form as scaffolding.
				check(helpersOnly.getFirst().body().id().equals(helper), name + ": awakened form rejected"); checks++;
				CritterState.observeBodies(List.of(), now + 20);
				CritterState.advanceCaptureEvidence(List.of(), List.of(new CritterState.CaptureLabelObservation(species, label, box.getCenter())), now + 21);
				if (attempt < 3) CritterState.applyEvent(new CritterEvent(CritterEvent.Type.FAILED, species, null, 1, false), false, now + 2999, aim);
			}
		}
		return checks + guaranteedRetryBody();
	}
	private static int guaranteedRetryBody() {
		int checks = 0;
		for (String name : List.of("Hideonwall", "Hideonfloor")) {
		for (boolean beforeRetry : new boolean[] {false, true}) {
		for (boolean sameLabel : new boolean[] {false, true}) {
			CritterState.onVisitStarted(); RecatchSpots.clear();
			var species = Critters.byName(name);
			var box = new AABB(0, 0, 0, 1, 1, 1);
			var aim = new CritterState.Aim(new Vec3(.5, .5, -3), new Vec3(0, 0, 1));
			UUID body = new UUID(70, 1), label = new UUID(71, 1);
			var observed = new CritterState.BodyObservation(species, label, body, box, false);
			var peer = new CritterState.BodyObservation(species, new UUID(71, 9), new UUID(70, 9), box.move(10, 0, 0), false);
			CritterState.observeBodies(List.of(observed, peer), 1000);
			long owner = CritterState.logicalCritters().getFirst().id();
			for (int attempt = 1; attempt <= 2; attempt++) {
				long now = attempt * 3000;
				CritterState.applyEvent(new CritterEvent(CritterEvent.Type.ATTEMPT, species, null, 1, false), false, now, aim);
				CritterState.observeBodies(List.of(peer), now + 100);
				CritterState.applyEvent(new CritterEvent(CritterEvent.Type.FAILED, species, null, 1, false), false, now + 500, aim);
				CritterState.observeBodies(List.of(observed, peer), now + 550);
				if (attempt == 2) CritterState.observeBodies(List.of(peer), now + 600);
			}
			// Reproduce the absent-breakout hold independently of the model's visible resolution.
			RecatchSpots.onIdentityResolved(999, species, body, body, box, false, 2,
				CritterState.Outcome.BREAKOUT, false);
			check(RecatchSpots.snapshot(true, Set.of()).isPinned(body), name + ": missing breakout hold fixture"); checks++;
			UUID replacement = new UUID(70, 2), nextLabel = sameLabel ? label : new UUID(71, 2);
			if (!beforeRetry) CritterState.applyEvent(new CritterEvent(CritterEvent.Type.ATTEMPT, species, null, 1, false), false, 6950, aim);
			CritterState.observeBodies(List.of(new CritterState.BodyObservation(species, nextLabel, replacement, box, false), peer), 7000);
			if (beforeRetry) {
				check(!RecatchSpots.snapshot(true, Set.of()).isPinned(body), name + ": resolved form handoff retained old hold"); checks++;
				CritterState.applyEvent(new CritterEvent(CritterEvent.Type.ATTEMPT, species, null, 1, false), false, 7001, aim);
			}
			var logical = CritterState.logicalCritters().stream().filter(v -> v.entityId().equals(replacement)).findFirst().orElseThrow();
			check(logical.id() == owner && logical.pity() == 3 && logical.life() == CritterState.Life.ATTEMPTING,
				name + ": final retry's delayed first body lost identity/pity " + CritterState.logicalCritters() + " attempts=" + CritterState.activeAttempts()); checks++;
			CritterState.observeBodies(List.of(peer), 7100);
			CritterState.advanceCaptureEvidence(List.of(), List.of(new CritterState.CaptureLabelObservation(species,
				new UUID(71, 3), box.getCenter())), 7100);
			check(RecatchSpots.snapshot(true, Set.of()).isAnimated(replacement)
				&& !RecatchSpots.snapshot(true, Set.of()).isPinned(body), name + ": final retry retained old pin instead of shrink"); checks++;
			CritterState.applyEvent(new CritterEvent(CritterEvent.Type.OWN_CATCH, species, null, 1, false), false, 9000, aim);
			check(RecatchSpots.snapshot(true, Set.of()).pins().isEmpty()
				&& RecatchSpots.snapshot(true, Set.of()).shrinkStates().isEmpty(), name + ": final catch left presentation"); checks++;
		}
		}
		}
		CritterState.onVisitStarted(); RecatchSpots.clear();
		return checks;
	}
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
