package dev.serko.safariutils.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.parse.CritterEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Headless-process-only harness. Never packaged into either mod or exposed by an in-game command. */
final class CaptureReplaySession implements AutoCloseable {
	private final Field config = field(ConfigManager.class, "config");
	private final Field effects = field(CritterState.class, "effects");
	private final Object originalConfig;
	private final Object originalEffects;
	private final SafariConfig settings = new SafariConfig();
	private List<CritterState.BodyObservation> previousBodies;
	private long sequence;
	private boolean started;
	private int records;

	CaptureReplaySession() throws Exception {
		originalConfig = config.get(null); originalEffects = effects.get(null);
		settings.advanced.disableAutomaticLogging = true;
		config.set(null, settings);
		// Inert effects avoid world/catalog/presentation access. The production tracker itself is unchanged.
		effects.set(null, Proxy.newProxyInstance(CaptureEffects.class.getClassLoader(),
			new Class<?>[] {CaptureEffects.class}, (proxy, method, args) -> null));
	}

	void accept(JsonObject json) throws Exception {
		if (integer(json.get("schema")) != 1) throw new IllegalArgumentException("Unsupported capture input schema");
		String op = json.get("op").getAsString();
		if (op.equals("reset")) {
			var reset = CritterState.class.getDeclaredMethod("resetForVisit", long.class); reset.setAccessible(true);
			reset.invoke(null, integer(json.get("visit")));
			field(CritterState.class, "nextLogicalId").setLong(null, integer(json.get("logicalSeed")));
			field(CritterState.class, "nextAttemptId").setLong(null, integer(json.get("attemptSeed")));
			previousBodies = null; sequence = 0; started = true;
		}
		if (!started || integer(json.get("seq")) != ++sequence) throw new IllegalArgumentException("Missing capture input/boundary");
		settings.display.eagleRarity = Math.toIntExact(integer(json.get("eagle")));
		long now = integer(json.get("at"));
		switch (op) {
			case "reset" -> { }
			case "bodies" -> {
				if (json.has("same") && bool(json.get("same"))) {
					if (previousBodies == null) throw new IllegalArgumentException("Body baseline missing");
				} else {
					List<CritterState.BodyObservation> bodies = new ArrayList<>();
					for (JsonElement value : bounded(json.getAsJsonArray("bodies"))) {
						JsonArray row = tuple(value, 5);
						bodies.add(new CritterState.BodyObservation(species(row.get(0)), uuid(row.get(1)), uuid(row.get(2)), box(row.get(3)), bool(row.get(4))));
					}
					previousBodies = List.copyOf(bodies);
				}
				CritterState.observeBodies(previousBodies, now);
			}
			case "evidence" -> {
				List<CritterState.CapsuleObservation> capsules = new ArrayList<>();
				List<CritterState.CaptureLabelObservation> labels = new ArrayList<>();
				for (JsonElement value : bounded(json.getAsJsonArray("capsules"))) {
					JsonArray row = tuple(value, 2); capsules.add(new CritterState.CapsuleObservation(uuid(row.get(0)), vector(row.get(1))));
				}
				for (JsonElement value : bounded(json.getAsJsonArray("labels"))) {
					JsonArray row = tuple(value, 3); labels.add(new CritterState.CaptureLabelObservation(species(row.get(0)), uuid(row.get(1)), vector(row.get(2))));
				}
				CritterState.advanceCaptureEvidence(capsules, labels, now);
			}
			case "event" -> {
				CritterState.Aim aim = null;
				StillCritters.AttemptTarget remembered = null;
				if (json.has("aim")) {
					JsonArray row = tuple(json.get("aim"), 2); aim = new CritterState.Aim(vector(row.get(0)), vector(row.get(1)));
				}
				if (json.has("remembered")) {
					JsonArray row = tuple(json.get("remembered"), 3);
					remembered = new StillCritters.AttemptTarget(uuid(row.get(0)), box(row.get(1)), bool(row.get(2)));
				}
				var event = new CritterEvent(CritterEvent.Type.valueOf(json.get("type").getAsString()), species(json.get("critter")), null, 1, false);
				CritterState.applyEvent(event, bool(json.get("masterful")), now, new CritterState.AttemptEvidence(aim, remembered));
			}
			case "interaction" -> CritterState.observeInteraction(uuid(json.get("entity")), vector(json.get("position")), now);
			case "total" -> CritterState.confirmCatchTotal(species(json.get("critter")), Math.toIntExact(integer(json.get("catches"))), now);
			default -> throw new IllegalArgumentException("Incomplete/unknown capture input: " + op);
		}
		if (CaptureReplayTrace.fingerprint() != integer(json.get("state"))) {
			throw new IllegalStateException("Capture state mismatch at input " + sequence + " (" + op + ")");
		}
		records++;
	}
	int records() { return records; }
	@Override public void close() throws Exception {
		try { CritterState.onVisitStarted(); }
		finally { effects.set(null, originalEffects); config.set(null, originalConfig); }
	}
	private static Field field(Class<?> owner, String name) {
		try { Field field = owner.getDeclaredField(name); field.setAccessible(true); return field; }
		catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
	}
	private static JsonArray bounded(JsonArray values) {
		if (values == null || values.size() > CaptureReplayTrace.MAX_OBSERVATIONS) throw new IllegalArgumentException("Invalid observation count");
		return values;
	}
	private static JsonArray tuple(JsonElement value, int length) {
		JsonArray values = value.getAsJsonArray();
		if (values.size() != length) throw new IllegalArgumentException("Invalid input tuple"); return values;
	}
	private static long integer(JsonElement value) {
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected integer");
		return value.getAsBigDecimal().longValueExact();
	}
	private static double number(JsonElement value) {
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected number");
		double result = value.getAsDouble(); if (!Double.isFinite(result)) throw new IllegalArgumentException("Nonfinite geometry"); return result;
	}
	private static boolean bool(JsonElement value) {
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Expected boolean");
		return value.getAsBoolean();
	}
	private static UUID uuid(JsonElement value) { return UUID.fromString(value.getAsString()); }
	private static Critter species(JsonElement value) {
		Critter critter = Critters.byName(value.getAsString());
		if (critter == null) throw new IllegalArgumentException("Unknown critter"); return critter;
	}
	private static Vec3 vector(JsonElement value) {
		JsonArray row = tuple(value, 3); return new Vec3(number(row.get(0)), number(row.get(1)), number(row.get(2)));
	}
	private static AABB box(JsonElement value) {
		JsonArray row = tuple(value, 6);
		double x = number(row.get(0)), y = number(row.get(1)), z = number(row.get(2));
		double xx = number(row.get(3)), yy = number(row.get(4)), zz = number(row.get(5));
		if (x > xx || y > yy || z > zz) throw new IllegalArgumentException("Inverted geometry");
		return new AABB(x, y, z, xx, yy, zz);
	}
}
