package dev.serko.safariutils.client;

import com.google.gson.JsonObject;
import dev.serko.safariutils.BuildVersion;
import dev.serko.safariutils.state.SafariEpoch;
import dev.serko.safariutils.state.SafariEvent;
import dev.serko.safariutils.state.SafariRuntimeState;

/** Adds lifecycle and parsed-event records to the compact machine-readable diagnostic stream. */
public final class StructuredDiagnostics {
	private StructuredDiagnostics() { }

	public static void install() {
		if (!BuildVersion.has(BuildVersion.Capability.DEVELOPER_TOOLS)) return;
		SafariRuntimeState.setTransitionListener(transition -> {
			String reason = transition.reason();
			SafariEpoch before = transition.before();
			SafariEpoch after = transition.after().epoch();
			if ("run-started".equals(reason) && before.visit() == 0L) {
				diagnostic("INVARIANT", "run started without an active visit");
			}
			if ("run-closed".equals(reason) && before.phase() != SafariEpoch.Phase.ACTIVE
					&& before.phase() != SafariEpoch.Phase.TRANSFERRING) {
				diagnostic("INVARIANT", "run closed from " + before.phase());
			}
			transition(reason, before, after);
			OperationalLog.visitChanged(before.visit(), after.visit());
		});
	}

	public static void event(SafariEvent event) {
		if (!enabled() || event == null) return;
		JsonObject json = base("e", event.epoch());
		json.addProperty("s", event.sequence());
		json.addProperty("y", event.type().name());
		json.addProperty("e", event.evidence().name());
		json.addProperty("o", event.source());
		json.addProperty("x", event.rawText());
		if (event.critterEvent() != null) {
			json.addProperty("ce", event.critterEvent().type().name());
			if (event.critterEvent().critter() != null) {
				json.addProperty("cr", event.critterEvent().critter().name());
			}
		}
		OperationalLog.structured(json.toString());
	}

	public static void transition(String reason, SafariEpoch before, SafariEpoch after) {
		if (!enabled()) return;
		JsonObject json = base("t", after);
		json.addProperty("q", reason);
		json.addProperty("f", before.phase().name());
		json.addProperty("z", after.phase().name());
		OperationalLog.structured(json.toString());
	}

	/** Compact decision record for new coordinators; callers must not include secrets. */
	public static void decision(String subsystem, String action, String detail) {
		if (!enabled()) return;
		JsonObject json = base("x", SafariRuntimeState.current());
		json.addProperty("g", subsystem == null ? "unknown" : subsystem);
		json.addProperty("a", action == null ? "unknown" : action);
		if (detail != null && !detail.isBlank()) json.addProperty("d", detail);
		OperationalLog.structured(json.toString());
	}

	private static void diagnostic(String category, String message) {
		if (!enabled()) return;
		JsonObject json = base("d", SafariRuntimeState.current());
		json.addProperty("g", category);
		json.addProperty("m", message);
		OperationalLog.structured(json.toString());
	}

	private static JsonObject base(String kind, SafariEpoch epoch) {
		JsonObject json = new JsonObject();
		json.addProperty("k", kind);
		json.addProperty("t", System.currentTimeMillis());
		json.addProperty("n", System.nanoTime());
		json.addProperty("c", epoch.connection());
		json.addProperty("v", epoch.visit());
		json.addProperty("r", epoch.run());
		json.addProperty("p", epoch.phase().name());
		if (epoch.lobbyId() != null) json.addProperty("b", epoch.lobbyId());
		return json;
	}

	static void captureOutcome(CaptureOutcomeSelection selection, boolean breakout, boolean direct,
			long now, long fallback, long chosen) {
		if (!enabled() || selection.candidates() == null) return;
		JsonObject json = CaptureDecisionTrace.encode(selection, breakout, direct, now, fallback, chosen);
		JsonObject context = base("co", SafariRuntimeState.current());
		context.asMap().forEach(json::add);
		OperationalLog.structured(json.toString());
	}

	static void captureTransition(CaptureLifecycle.Input input, long logicalId,
			CaptureLifecycle.Resolution result) {
		if (!enabled()) return;
		JsonObject json = CaptureTransitionTrace.encode(input, logicalId, result);
		base("ct", SafariRuntimeState.current()).asMap().forEach(json::add);
		OperationalLog.structured(json.toString());
	}

	static boolean enabled() {
		return BuildVersion.has(BuildVersion.Capability.DEVELOPER_TOOLS)
			&& ConfigManager.automaticLoggingEnabled();
	}

	static void capturePairing(String stage, double[][] distances, double maximum,
			double unmatched, int[] assignment, java.util.function.Supplier<java.util.List<String>> bodies,
			java.util.function.Supplier<java.util.List<Long>> logicals) {
		if (!enabled()) return;
		JsonObject json = CapturePairingTrace.encode(stage, distances, maximum, unmatched, assignment);
		if (json == null) {
			decision("CAPTURE/REPLAY", "omitted", "pairing exceeds trace bounds");
			return;
		}
		CapturePairingTrace.addIdentities(json, bodies.get(), logicals.get());
		base("cp", SafariRuntimeState.current()).asMap().forEach(json::add);
		OperationalLog.structured(json.toString());
	}

}
