package dev.serko.safariutils.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.parse.CritterEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Serialize real tracker inputs, discard state, and replay the serialized journal through that tracker. */
public final class CaptureReplayRegression {
	public static int run() throws Exception {
		List<JsonObject> journal = new ArrayList<>();
		List<java.util.function.Supplier<JsonObject>> pending = new ArrayList<>();
		var recorder = new CaptureReplayTrace.Recorder(pending::add);
		try (var isolated = new CaptureReplaySession()) {
			for (var species : Critters.all()) {
				if (species.name().equals("Hideyho")) continue;
				CritterState.onVisitStarted();
				recorder.reset(0, counter("nextLogicalId"), counter("nextAttemptId"));
				UUID a = new UUID(1, 1), b = new UUID(1, 2), la = new UUID(2, 1), lb = new UUID(2, 2);
				var box = new AABB(0, 0, 0, 1, 1, 1);
				var bodies = List.of(new CritterState.BodyObservation(species, la, a, box, false),
					new CritterState.BodyObservation(species, lb, b, box.move(4, 0, 0), true));
				CritterState.observeBodies(bodies, 1000); recorder.bodies(bodies, 1000);
				CritterState.observeBodies(bodies, 1010); recorder.bodies(bodies, 1010);
				CritterState.observeInteraction(a, box.getCenter(), 1020); recorder.interaction(a, box.getCenter(), 1020);
				var evidence = new CritterState.AttemptEvidence(new CritterState.Aim(new Vec3(.5, .5, -3), new Vec3(0, 0, 1)), null);
				var event = new CritterEvent(CritterEvent.Type.ATTEMPT, species, null, 1, false);
				CritterState.applyEvent(event, true, 1030, evidence); recorder.event(event, true, evidence, 1030);
				CritterState.advanceCaptureEvidence(List.of(), List.of(), 1040); recorder.evidence(List.of(), List.of(), 1040);
				var remaining = List.of(bodies.get(1));
				CritterState.observeBodies(remaining, 1050); recorder.bodies(remaining, 1050);
				var caught = new CritterEvent(CritterEvent.Type.OWN_CATCH, species, null, 1, false);
				CritterState.applyEvent(caught, false, 1060, evidence); recorder.event(caught, false, evidence, 1060);
				CritterState.confirmCatchTotal(species, 100, 1070); recorder.total(species, 100, 1070);
			}
			// A remembered far target, delayed breakout, replacement body and immediate retry.
			var species = Critters.byName("Bloodbat");
			CritterState.onVisitStarted();
			recorder.reset(0, counter("nextLogicalId"), counter("nextAttemptId"));
			UUID old = new UUID(20, 1), replacement = new UUID(20, 2), label = new UUID(21, 1), capsule = new UUID(22, 1);
			var box = new AABB(0, 0, 0, 1, 1, 1);
			var evidence = new CritterState.AttemptEvidence(new CritterState.Aim(new Vec3(.5, .5, -3), new Vec3(0, 0, 1)),
				new StillCritters.AttemptTarget(old, box, true));
			var attempt = new CritterEvent(CritterEvent.Type.ATTEMPT, species, null, 1, false);
			CritterState.applyEvent(attempt, false, 2000, evidence); recorder.event(attempt, false, evidence, 2000);
			var capsules = List.of(new CritterState.CapsuleObservation(capsule, new Vec3(.5, .5, -1)));
			CritterState.advanceCaptureEvidence(capsules, List.of(), 2010); recorder.evidence(capsules, List.of(), 2010);
			capsules = List.of(new CritterState.CapsuleObservation(capsule, box.getCenter()));
			var labels = List.of(new CritterState.CaptureLabelObservation(species, label, box.getCenter()));
			CritterState.advanceCaptureEvidence(capsules, labels, 2020); recorder.evidence(capsules, labels, 2020);
			var failed = new CritterEvent(CritterEvent.Type.FAILED, species, null, 1, false);
			CritterState.applyEvent(failed, false, 2030, evidence); recorder.event(failed, false, evidence, 2030);
			var bodies = List.of(new CritterState.BodyObservation(species, label, replacement, box, true));
			CritterState.observeBodies(bodies, 2040); recorder.bodies(bodies, 2040);
			CritterState.applyEvent(attempt, false, 2041, evidence); recorder.event(attempt, false, evidence, 2041);
			CritterState.advanceCaptureEvidence(List.of(), List.of(), 50_000); recorder.evidence(List.of(), List.of(), 50_000);
		}
		// Serialize only after tracker mutations and resets, like a delayed writer thread.
		for (var input : pending) journal.add(JsonParser.parseString(input.get().toString()).getAsJsonObject());
		try (var replay = new CaptureReplaySession()) {
			for (var record : journal) replay.accept(record);
			if (replay.records() != journal.size()) throw new AssertionError("Skipped replay inputs");
		}
		var compressed = java.nio.file.Files.createTempFile("safari-capture-replay-", ".jsonl.gz");
		try {
			try (var writer = new java.io.OutputStreamWriter(new java.util.zip.GZIPOutputStream(
					java.nio.file.Files.newOutputStream(compressed)), java.nio.charset.StandardCharsets.UTF_8)) {
				for (var record : journal) { writer.write(record.toString()); writer.write('\n'); }
			}
			ReplayCaptures.main(new String[] {compressed.toString()});
		} finally { java.nio.file.Files.delete(compressed); }
		try (var replay = new CaptureReplaySession()) {
			JsonObject altered = journal.getFirst().deepCopy(); altered.addProperty("state", 0);
			try { replay.accept(altered); throw new AssertionError("Changed recorded state was accepted"); }
			catch (IllegalStateException expected) {
				if (!expected.getMessage().contains("state mismatch")) throw expected;
			}
		}
		try (var replay = new CaptureReplaySession()) {
			try { replay.accept(journal.get(1)); throw new AssertionError("Missing boundary was accepted"); }
			catch (IllegalArgumentException expected) {
				if (!expected.getMessage().contains("Missing capture")) throw expected;
			}
		}
		for (String mutation : List.of("gap", "sequence", "body-size", "nonfinite")) {
			try (var replay = new CaptureReplaySession()) {
				replay.accept(journal.getFirst());
				JsonObject changed = journal.get(1).deepCopy();
				switch (mutation) {
					case "gap" -> changed.addProperty("op", "gap");
					case "sequence" -> changed.addProperty("seq", 999);
					case "body-size" -> changed.getAsJsonArray("bodies").get(0).getAsJsonArray().remove(4);
					case "nonfinite" -> changed.getAsJsonArray("bodies").get(0).getAsJsonArray().get(3).getAsJsonArray()
						.set(0, new com.google.gson.JsonPrimitive(Double.NaN));
				}
				try { replay.accept(changed); throw new AssertionError("Malformed journal accepted: " + mutation); }
				catch (IllegalArgumentException expected) { }
			}
		}
		return journal.size() + 8;
	}
	private static long counter(String name) throws Exception {
		var field = CritterState.class.getDeclaredField(name); field.setAccessible(true); return field.getLong(null);
	}
}
