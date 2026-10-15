package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.SafariBiome;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import dev.serko.safariutils.parse.CritterEvent;
import java.util.List;
import java.util.UUID;

/** Complete observed-body batches run through the live identity reconciliation implementation. */
public final class CaptureObservationRegression {
	public static int run() throws ReflectiveOperationException {
		// Isolate the test from Fabric paths and automatic disk logging, without changing production defaults.
		var field = ConfigManager.class.getDeclaredField("config");
		field.setAccessible(true);
		Object original = field.get(null);
		var settings = new SafariConfig();
		settings.advanced.disableAutomaticLogging = true;
		field.set(null, settings);
		try { return runIsolated(); }
		finally { field.set(null, original); }
	}
	private static int runIsolated() {
		int checks = 0;
		var species = new Critter("Bloodbat", SafariBiome.CAVERN, Critter.Rarity.RARE, 1);
		var first = new UUID(0, 101);
		var second = new UUID(0, 102);
		var labelA = new UUID(0, 201);
		var labelB = new UUID(0, 202);
		var box = new AABB(0, 0, 0, 1, 1, 1);
		CritterState.onVisitStarted();
		try {
			CritterState.observeBodies(List.of(body(species, labelA, first, box),
				body(species, labelB, second, box.move(1, 0, 0))), 1000);
			long firstLogical = logical(first).id(), secondLogical = logical(second).id();
			check(firstLogical != secondLogical, "nearby bodies have separate identities"); checks++;
			for (int frame = 0; frame < 1000; frame++) {
				boolean swapped = (frame & 1) == 0;
				var a = body(species, swapped ? labelB : labelA, first, box.move(frame * .01, 0, 0));
				var b = body(species, swapped ? labelA : labelB, second, box.move(1 - frame * .01, 0, 0));
				CritterState.observeBodies(swapped ? List.of(b, a) : List.of(a, b), 1050 + frame * 50L);
				check(logical(first).id() == firstLogical && logical(second).id() == secondLogical,
					"label exchange or scan order changed body ownership"); checks++;
				check(CritterState.logicalCritters().size() == 2, "label exchange created duplicate identities"); checks++;
			}
			var newcomer = new UUID(0, 104);
			var labelC = new UUID(0, 204);
			CritterState.observeBodies(List.of(body(species, labelA, newcomer, box.move(2, 0, 0)),
				body(species, labelB, first, box), body(species, labelC, second, box.move(1, 0, 0))), 60_000);
			check(logical(first).id() == firstLogical && logical(second).id() == secondLogical,
				"newcomer cannot steal a label owner's still-observed body identity"); checks++;
			check(logical(newcomer).id() != firstLogical && logical(newcomer).id() != secondLogical,
				"newcomer receives independent identity"); checks++;
			CritterState.observeBodies(List.of(), 70_001);
			check(logical(first).life() == CritterState.Life.LOST, "absent body becomes lost after deadline"); checks++;
			CritterState.observeBodies(List.of(body(species, labelA, first, box)), 70_050);
			check(logical(first).id() == firstLogical && logical(first).life() == CritterState.Life.LIVE,
				"returning body keeps identity"); checks++;
			var replacement = new UUID(0, 103);
			CritterState.observeBodies(List.of(body(species, labelA, replacement, box)), 70_100);
			check(logical(replacement).id() == firstLogical, "same-label replacement preserves identity"); checks++;
			var hideyho = new Critter("Hideyho", SafariBiome.HAUNTED, Critter.Rarity.RARE, 1);
			CritterState.observeBodies(List.of(body(hideyho, new UUID(0, 300), new UUID(0, 301), box)), 70_150);
			check(CritterState.logicalCritters().stream().noneMatch(view -> view.critter().equals(hideyho)),
				"Hideyho stays outside capsule identity tracking"); checks++;
		} finally { CritterState.onVisitStarted(); RecatchSpots.clear(); }
		return checks + rapidRecatches() + correctedInteraction() + reorderedArrivals() + evidenceTransitions() + deferredAim() + completion();
	}
	private static int correctedInteraction() {
		int checks = 0;
		var species = dev.serko.safariutils.data.Critters.byName("Gimmiegold");
		var box = new AABB(0, 0, 0, 1, 1, 1);
		var aim = new CritterState.Aim(new Vec3(.5, .5, -3), new Vec3(0, 0, 1));
		UUID a = new UUID(60, 1), b = new UUID(60, 2), c = new UUID(60, 3);
		UUID la = new UUID(61, 1), lb = new UUID(61, 2), lc = new UUID(61, 3);
		try {
			for (boolean ambiguous : List.of(false, true)) {
				CritterState.onVisitStarted(); RecatchSpots.clear();
				var visible = body(species, la, a, box);
				var target = body(species, lb, b, box.move(2, 0, 0));
				CritterState.observeBodies(ambiguous ? List.of(visible, target,
					body(species, lc, c, box.move(3, 0, 0))) : List.of(visible, target), 1000);
				CritterState.observeInteraction(a, box.getCenter(), 1001);
				CritterState.applyEvent(event(CritterEvent.Type.ATTEMPT, species), false, 1010, aim);
				CritterState.observeBodies(List.of(visible), 1050);
				CritterState.advanceCaptureEvidence(List.of(), List.of(new CritterState.CaptureLabelObservation(
					species, new UUID(62, 1), target.box().getCenter())), 1050);
				check(logical(a).pity() == (ambiguous ? 1 : 0) && logical(b).pity() == (ambiguous ? 0 : 1),
					"interaction correction requires a unique disappeared peer"); checks++;
				if (ambiguous) continue;
				long identity = logical(b).id();
				UUID replacement = new UUID(60, 4);
				CritterState.observeBodies(List.of(visible, body(species, lb, replacement, target.box())), 3200);
				CritterState.applyEvent(event(CritterEvent.Type.FAILED, species), false, 3210, aim);
				check(logical(replacement).id() == identity && logical(replacement).pity() == 1
					&& logical(replacement).life() == CritterState.Life.BREAKOUT,
					"corrected disappeared target returns as a breakout, not an initial body"); checks++;
				check(!RecatchSpots.snapshot(true, java.util.Set.of()).suppresses(a),
					"corrected interaction releases the untouched peer"); checks++;
			}
			return checks;
		} finally { CritterState.onVisitStarted(); RecatchSpots.clear(); }
	}
	private static int completion() {
		var species = new Critter("Bloodbat", SafariBiome.CAVERN, Critter.Rarity.RARE, 1);
		var aim = new CritterState.Aim(new Vec3(.5, .5, -3), new Vec3(0, 0, 1));
		UUID id = new UUID(15, 1), label = new UUID(15, 2);
		var observation = body(species, label, id, new AABB(0, 0, 0, 1, 1, 1));
		try {
			CritterState.onVisitStarted(); RecatchSpots.clear();
			CritterState.applyEvent(event(CritterEvent.Type.ATTEMPT, species), false, 1000, aim);
			CritterState.confirmCatchTotal(species, 6, 1010);
			check(!CritterState.captureTrackingActive(), "species completion retires deferred work");
			CritterState.observeBodies(List.of(observation), 1020);
			CritterState.applyEvent(event(CritterEvent.Type.FAILED, species), false, 1030, aim);
			CritterState.applyEvent(event(CritterEvent.Type.ATTEMPT, species), false, 1040, aim);
			check(CritterState.activeAttempts().isEmpty() && CritterState.logicalCritters().isEmpty()
				&& !CritterState.captureTrackingActive(), "late outcomes and attempts cannot resurrect exhausted species");
			CritterState.onVisitStarted(); RecatchSpots.clear();
			CritterState.observeBodies(List.of(observation), 2000);
			CritterState.observeInteraction(id, observation.box().getCenter(), 2001);
			CritterState.applyEvent(event(CritterEvent.Type.ATTEMPT, species), false, 2010, aim);
			CritterState.confirmCatchTotal(species, 5, 2020);
			check(!CritterState.activeAttempts().isEmpty(), "partial totals cannot settle active attempts");
			CritterState.confirmCatchTotal(species, 6, 2030);
			CritterState.confirmCatchTotal(species, 6, 2040);
			var snapshot = RecatchSpots.snapshot(true, java.util.Set.of());
			check(CritterState.activeAttempts().isEmpty() && logical(id).life() == CritterState.Life.CAUGHT
				&& snapshot.pins().isEmpty() && snapshot.shrinkStates().isEmpty(),
				"completion settles logical and visual ownership together and is idempotent");
			return 4;
		} finally { CritterState.onVisitStarted(); RecatchSpots.clear(); }
	}
	private static int deferredAim() {
		var species = new Critter("Bloodbat", SafariBiome.CAVERN, Critter.Rarity.RARE, 1);
		UUID a = new UUID(12, 1), b = new UUID(12, 2), la = new UUID(13, 1), lb = new UUID(13, 2);
		var box = new AABB(0, 0, 0, 1, 1, 1);
		try {
			for (boolean reversed : List.of(false, true)) {
				CritterState.onVisitStarted(); RecatchSpots.clear();
				var aim = new CritterState.Aim(new Vec3(4.5, .5, -3), new Vec3(0, 0, 1));
				CritterState.applyEvent(event(CritterEvent.Type.ATTEMPT, species), false, 1000, aim);
				var left = body(species, la, a, box);
				var right = body(species, lb, b, box.move(4, 0, 0));
				CritterState.observeBodies(reversed ? List.of(right, left) : List.of(left, right), 1050);
				check(logical(b).pity() == 1 && logical(a).pity() == 0,
					"deferred attempt uses original aim, independent of camera availability or scan order");
			}
			return 2;
		} finally { CritterState.onVisitStarted(); RecatchSpots.clear(); }
	}
	private static int evidenceTransitions() {
		int checks = 0;
		var species = new Critter("Bloodbat", SafariBiome.CAVERN, Critter.Rarity.RARE, 1);
		UUID a = new UUID(5, 1), b = new UUID(5, 2), la = new UUID(6, 1), lb = new UUID(6, 2);
		var box = new AABB(0, 0, 0, 1, 1, 1);
		var aim = new CritterState.Aim(new Vec3(.5, .5, -3), new Vec3(0, 0, 1));
		try {
			CritterState.onVisitStarted(); RecatchSpots.clear();
			CritterState.observeBodies(List.of(body(species, la, a, box), body(species, lb, b, box.move(4,0,0))), 1000);
			CritterState.applyEvent(event(CritterEvent.Type.ATTEMPT, species), true, 1001, aim);
			check(RecatchSpots.snapshot(true, java.util.Set.of()).shrinkStates().isEmpty(), "group guarantee waits for body evidence"); checks++;
			CritterState.observeBodies(List.of(body(species, la, a, box),
				body(species, lb, b, new AABB(4.25, 0, .25, 4.75, .5, .75))), 1010);
			CritterState.advanceCaptureEvidence(List.of(), List.of(), 1010);
			var first = RecatchSpots.snapshot(true, java.util.Set.of());
			check(first.isAnimated(b) && !first.suppresses(a), "native body evidence repairs ownership without suppressing peer"); checks++;
			check(logical(a).pity() == 0 && logical(b).pity() == 1, "repair transfers exactly one attempt's pity"); checks++;
			var timeline = first.shrinkStates().getFirst().animation();
			CritterState.advanceCaptureEvidence(List.of(), List.of(), 1020);
			check(RecatchSpots.snapshot(true, java.util.Set.of()).shrinkStates().getFirst().animation().equals(timeline),
				"repeated shrink evidence cannot restart animation"); checks++;

			CritterState.onVisitStarted(); RecatchSpots.clear();
			CritterState.observeBodies(List.of(body(species, la, a, box), body(species, lb, b, box.move(4,0,0))), 2000);
			CritterState.applyEvent(event(CritterEvent.Type.ATTEMPT, species), true, 2001, aim);
			var capsule = new UUID(7, 1);
			CritterState.advanceCaptureEvidence(List.of(new CritterState.CapsuleObservation(capsule, new Vec3(.5,.5,-1))), List.of(), 2010);
			CritterState.advanceCaptureEvidence(List.of(new CritterState.CapsuleObservation(capsule, new Vec3(.5,.5,.5))), List.of(), 2020);
			check(RecatchSpots.snapshot(true, java.util.Set.of()).isAnimated(a)
				&& !RecatchSpots.snapshot(true, java.util.Set.of()).suppresses(b), "capsule intersection only animates its target"); checks++;

			CritterState.onVisitStarted(); RecatchSpots.clear();
			CritterState.observeBodies(List.of(body(species, la, a, box), body(species, lb, b, box.move(4,0,0))), 3000);
			var inferredAim = new CritterState.Aim(new Vec3(.5, .5, -3), new Vec3(0, 1, 0));
			CritterState.applyEvent(event(CritterEvent.Type.ATTEMPT, species), false, 3001, inferredAim);
			CritterState.observeBodies(List.of(body(species, la, a, box)), 3010);
			CritterState.advanceCaptureEvidence(List.of(), List.of(new CritterState.CaptureLabelObservation(
				species, new UUID(8,1), box.move(4,0,0).getCenter())), 3010);
			check(logical(a).pity() == 0 && logical(b).pity() == 1, "disappearance repairs provisional target"); checks++;
			check(RecatchSpots.snapshot(true, java.util.Set.of()).isPinned(b)
				&& !RecatchSpots.snapshot(true, java.util.Set.of()).suppresses(a), "repair releases provisional pin"); checks++;
		} finally { CritterState.onVisitStarted(); RecatchSpots.clear(); }
		return checks;
	}
	private static int reorderedArrivals() {
		int checks = 0;
		var species = new Critter("Bloodbat", SafariBiome.CAVERN, Critter.Rarity.RARE, 1);
		var box = new AABB(0, 0, 0, 1, 1, 1);
		UUID a = new UUID(3, 1), b = new UUID(3, 2), c = new UUID(3, 3);
		UUID la = new UUID(4, 1), lb = new UUID(4, 2), lc = new UUID(4, 3);
		try {
			for (int[] order : new int[][] {{0,1,2}, {0,2,1}, {1,0,2}, {1,2,0}, {2,0,1}, {2,1,0}}) {
				CritterState.onVisitStarted();
				CritterState.observeBodies(List.of(body(species, la, a, box), body(species, lb, b, box.move(1,0,0))), 1000);
				long aid = logical(a).id(), bid = logical(b).id();
				var batch = List.of(body(species, la, c, box.move(2,0,0)), body(species, lb, a, box), body(species, lc, b, box.move(1,0,0)));
				CritterState.observeBodies(List.of(batch.get(order[0]), batch.get(order[1]), batch.get(order[2])), 1050);
				check(logical(a).id() == aid && logical(b).id() == bid && logical(c).id() != aid
					&& logical(c).id() != bid && CritterState.logicalCritters().size() == 3,
					"all scan permutations must preserve existing body ownership"); checks++;
			}
		} finally { CritterState.onVisitStarted(); RecatchSpots.clear(); }
		return checks;
	}
	private static int rapidRecatches() {
		int checks = delayedRetryBody();
		var species = new Critter("Bloodbat", SafariBiome.CAVERN, Critter.Rarity.RARE, 1);
		UUID target = new UUID(1, 1), peer = new UUID(1, 2);
		UUID label = new UUID(2, 1), peerLabel = new UUID(2, 2);
		var box = new AABB(0, 0, 0, 1, 1, 1);
		var other = body(species, peerLabel, peer, box.move(4, 0, 0));
		var aim = new CritterState.Aim(new Vec3(.5, .5, -3), new Vec3(0, 0, 1));
		CritterState.onVisitStarted();
		try {
			CritterState.observeBodies(List.of(body(species, label, target, box), other), 1000);
			long identity = logical(target).id();
			for (int attempt = 1; attempt <= 3; attempt++) {
				long now = 1000 + attempt * 100L;
				CritterState.applyEvent(event(CritterEvent.Type.ATTEMPT, species), false, now, aim);
				check(logical(target).pity() == attempt, "rapid recatch must increment exactly once"); checks++;
				check(logical(peer).pity() == 0, "nearby peer cannot inherit pity"); checks++;
				check(!RecatchSpots.snapshot(true, java.util.Set.of()).suppresses(peer), "uncaught peer stays renderable"); checks++;
				CritterState.observeBodies(List.of(other), now + 10);
				CritterState.applyEvent(event(attempt < 3 ? CritterEvent.Type.FAILED : CritterEvent.Type.OWN_CATCH,
					species), false, now + 20, aim);
				if (attempt < 3) {
					target = new UUID(1, 2 + attempt);
					CritterState.observeBodies(List.of(other, body(species, label, target, box)), now + 21);
					check(logical(target).id() == identity, "replacement body retains logical identity"); checks++;
				}
			}
			check(logical(target).life() == CritterState.Life.CAUGHT, "final outcome retires target"); checks++;
			check(logical(peer).life() == CritterState.Life.LIVE, "final outcome leaves peer live"); checks++;
		} finally { CritterState.onVisitStarted(); RecatchSpots.clear(); }
		return checks;
	}
	private static CritterEvent event(CritterEvent.Type type, Critter species) {
		return new CritterEvent(type, species, null, 1, false);
	}
	private static int delayedRetryBody() {
		int checks = 0;
		var species = dev.serko.safariutils.data.Critters.byName("Hideonfloor");
		var box = new AABB(0, 0, 0, 1, 1, 1);
		var aim = new CritterState.Aim(new Vec3(.5, .5, -3), new Vec3(0, 0, 1));
		for (boolean keepLabel : new boolean[] {false, true}) {
			CritterState.onVisitStarted(); RecatchSpots.clear();
			UUID first = new UUID(50, 1), next = new UUID(50, 2), label = new UUID(51, 1);
			CritterState.observeBodies(List.of(body(species, label, first, box)), 1000);
			CritterState.applyEvent(event(CritterEvent.Type.ATTEMPT, species), false, 1010, aim);
			CritterState.observeBodies(List.of(), 1050);
			CritterState.applyEvent(event(CritterEvent.Type.FAILED, species), false, 3200, aim);
			CritterState.applyEvent(event(CritterEvent.Type.ATTEMPT, species), false, 3700, aim);
			CritterState.observeBodies(List.of(), 3750);
			CritterState.observeBodies(List.of(body(species, keepLabel ? label : new UUID(51, 2), next, box)), 4100);
			check(logical(next).pity() == 2 && logical(next).life() == CritterState.Life.ATTEMPTING,
				"late prior breakout body marked current retry as breakout"); checks++;
			CritterState.observeBodies(List.of(), 4150);
			CritterState.applyEvent(event(CritterEvent.Type.OWN_CATCH, species), false, 5900, aim);
			check(logical(next).life() == CritterState.Life.CAUGHT, "successful out-of-range retry ignored"); checks++;
			check(RecatchSpots.snapshot(true, java.util.Set.of()).pins().isEmpty(), "caught retry left pin until timeout"); checks++;
		}
		CritterState.onVisitStarted(); RecatchSpots.clear();
		return checks;
	}
	private static CritterState.BodyObservation body(Critter critter, UUID label, UUID entity, AABB box) {
		return new CritterState.BodyObservation(critter, label, entity, box, false);
	}
	private static CritterState.LogicalView logical(UUID body) {
		return CritterState.logicalCritters().stream().filter(view -> body.equals(view.entityId())).findFirst().orElseThrow();
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
