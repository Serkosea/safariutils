package dev.serko.safariutils.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.parse.CritterEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Developer-only, bounded input journal. Replaying it is an offline operation, never a live mutation. */
final class CaptureReplayTrace {
	static final int MAX_OBSERVATIONS = 256;
	private static final Recorder LIVE = new Recorder(json -> OperationalLog.structuredDeferred(() -> json.get().toString()));
	private static boolean recording;
	private static boolean waitingReported;

	static void reset(long visit, long logicalSeed, long attemptSeed) {
		recording = StructuredDiagnostics.enabled();
		waitingReported = false;
		if (recording) LIVE.reset(visit, logicalSeed, attemptSeed);
		else LIVE.clear();
	}

	static Recorder current() {
		if (!StructuredDiagnostics.enabled()) { recording = false; LIVE.clear(); waitingReported = false; }
		else if (!recording && !waitingReported) {
			waitingReported = true;
			StructuredDiagnostics.decision("CAPTURE/REPLAY", "omitted", "input journal awaits visit boundary");
		}
		return recording ? LIVE : null;
	}

	static final class Recorder {
		private final Consumer<java.util.function.Supplier<JsonObject>> sink;
		private List<CritterState.BodyObservation> previousBodies;
		private long sequence;
		Recorder(Consumer<java.util.function.Supplier<JsonObject>> sink) { this.sink = sink; }
		void clear() { previousBodies = null; sequence = 0; }
		void reset(long visit, long logicalSeed, long attemptSeed) {
			clear();
			emit("reset", 0, json -> {
				json.addProperty("visit", visit); json.addProperty("logicalSeed", logicalSeed); json.addProperty("attemptSeed", attemptSeed);
			});
		}
		void bodies(List<CritterState.BodyObservation> bodies, long now) {
			if (bodies.size() > MAX_OBSERVATIONS) { gap(now); return; }
			if (bodies.equals(previousBodies)) { emit("bodies", now, json -> json.addProperty("same", true)); return; }
			List<CritterState.BodyObservation> snapshot = List.copyOf(bodies);
			previousBodies = snapshot;
			emit("bodies", now, json -> {
				JsonArray rows = new JsonArray();
				for (var body : snapshot) {
					JsonArray row = new JsonArray();
					row.add(body.critter().name()); row.add(body.labelId().toString()); row.add(body.entityId().toString());
					row.add(box(body.box())); row.add(body.sparkling()); rows.add(row);
				}
				json.add("bodies", rows);
			});
		}
		void evidence(List<CritterState.CapsuleObservation> capsules,
				List<CritterState.CaptureLabelObservation> labels, long now) {
			if (capsules.size() > MAX_OBSERVATIONS || labels.size() > MAX_OBSERVATIONS) { gap(now); return; }
			var capturedCapsules = List.copyOf(capsules);
			var capturedLabels = List.copyOf(labels);
			emit("evidence", now, json -> {
				JsonArray cs = new JsonArray(), ls = new JsonArray();
				for (var capsule : capturedCapsules) {
					JsonArray row = new JsonArray(); row.add(capsule.entityId().toString()); row.add(vector(capsule.position())); cs.add(row);
				}
				for (var label : capturedLabels) {
					JsonArray row = new JsonArray(); row.add(label.critter().name()); row.add(label.labelId().toString());
					row.add(vector(label.position())); ls.add(row);
				}
				json.add("capsules", cs); json.add("labels", ls);
			});
		}
		void event(CritterEvent event, boolean masterful, CritterState.AttemptEvidence evidence, long now) {
			emit("event", now, json -> {
				json.addProperty("type", event.type().name()); json.addProperty("critter", event.critter().name());
				json.addProperty("masterful", masterful);
				if (evidence != null && evidence.aim() != null) {
					JsonArray aim = new JsonArray(); aim.add(vector(evidence.aim().eye())); aim.add(vector(evidence.aim().direction()));
					json.add("aim", aim);
				}
				if (evidence != null && evidence.remembered() != null) {
					var target = evidence.remembered();
					JsonArray remembered = new JsonArray(); remembered.add(target.id().toString());
					remembered.add(box(target.box())); remembered.add(target.sparkling()); json.add("remembered", remembered);
				}
			});
		}
		void interaction(UUID id, Vec3 position, long now) {
			emit("interaction", now, json -> { json.addProperty("entity", id.toString()); json.add("position", vector(position)); });
		}
		void total(Critter critter, int catches, long now) {
			emit("total", now, json -> { json.addProperty("critter", critter.name()); json.addProperty("catches", catches); });
		}
		private void emit(String operation, long now, Consumer<JsonObject> fields) {
			long inputSequence = ++sequence, state = fingerprint();
			int eagle = ConfigManager.get().display.eagleRarity;
			sink.accept(() -> {
				JsonObject json = new JsonObject(); json.addProperty("k", "ci"); json.addProperty("schema", 1);
				json.addProperty("seq", inputSequence); json.addProperty("op", operation); json.addProperty("at", now);
				json.addProperty("eagle", eagle); json.addProperty("state", state); fields.accept(json);
				return json;
			});
		}
		private void gap(long now) {
			previousBodies = null; emit("gap", now, json -> { });
		}
	}

	/** Deterministic across JVMs; intentionally not a security hash or proof of visual correctness. */
	static long fingerprint() {
		return CritterState.replayFingerprint();
	}
	static long mix(long h, long value) { return (h ^ value) * 0x100000001b3L; }
	static long mix(long h, UUID id) { return id == null ? mix(h, 0) : mix(mix(h, id.getMostSignificantBits()), id.getLeastSignificantBits()); }
	static long mix(long h, Vec3 v) {
		return v == null ? mix(h, 0) : mix(mix(mix(h, Double.doubleToLongBits(v.x)), Double.doubleToLongBits(v.y)), Double.doubleToLongBits(v.z));
	}
	static long mix(long h, AABB box) {
		h = mix(h, Double.doubleToLongBits(box.minX)); h = mix(h, Double.doubleToLongBits(box.minY));
		h = mix(h, Double.doubleToLongBits(box.minZ)); h = mix(h, Double.doubleToLongBits(box.maxX));
		h = mix(h, Double.doubleToLongBits(box.maxY)); return mix(h, Double.doubleToLongBits(box.maxZ));
	}
	static JsonArray vector(Vec3 vector) {
		JsonArray result = new JsonArray(); result.add(vector.x); result.add(vector.y); result.add(vector.z); return result;
	}
	static JsonArray box(AABB box) {
		JsonArray result = new JsonArray();
		result.add(box.minX); result.add(box.minY); result.add(box.minZ);
		result.add(box.maxX); result.add(box.maxY); result.add(box.maxZ); return result;
	}
	private CaptureReplayTrace() { }
}
