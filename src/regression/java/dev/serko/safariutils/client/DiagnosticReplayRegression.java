package dev.serko.safariutils.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/** End-to-end parsing fixtures for the same gzip stream used by automatic diagnostics. */
public final class DiagnosticReplayRegression {
	public static int run() throws Exception {
		Path root = Files.createTempDirectory("safari-replay-");
		try {
			Path first = root.resolve("first.jsonl.gz"), second = root.resolve("second.jsonl.gz");
			write(first, line("ATTEMPT A1 bat", "ACTIVE", 1));
			write(second, line("RESOLVE-RETIRED A1 CAUGHT", "IDLE", 1));
			var report = DiagnosticReplay.replay(List.of(first, second));
			check(report.files() == 2 && report.records() == 2 && report.warnings() == 0,
				"retired attempt settles across rotated compressed segments");
			write(first, line("ATTEMPT A1 bat", "ACTIVE", 1)
				+ "{\"k\":\"e\",\"s\":{}}\n"
				+ line("RESOLVE A1 CAUGHT", "IDLE", 1));
			report = DiagnosticReplay.replay(List.of(first));
			check(report.records() == 3 && report.warnings() == 1
				&& report.detail().contains("malformed record fields"),
				"malformed field cannot hide later valid cleanup");
			write(first, line("ATTEMPT A1 bat", "ACTIVE", 1)
				+ "{\"k\":\"future-optional-record\"}\n");
			check(DiagnosticReplay.replay(List.of(first)).warnings() == 0,
				"record without phase cannot falsely end an active run");
			write(first, line("ATTEMPT A1 bat", "ACTIVE", 1)
				+ line("new visit", "IDLE", 2));
			check(DiagnosticReplay.replay(List.of(first)).warnings() == 0,
				"visit boundary retires in-flight evidence without relying on text reset log");
			write(first, line("ATTEMPT A1 bat", "ACTIVE", 1)
				+ line("run ended", "IDLE", 1));
			check(DiagnosticReplay.replay(List.of(first)).detail().contains("active attempt after run"),
				"real orphan remains visible to replay");
			write(first, "not-json\n" + line("RESOLVE A9 CAUGHT", "IDLE", 1));
			report = DiagnosticReplay.replay(List.of(first));
			check(report.warnings() == 2 && report.detail().contains("unknown attempt resolution"),
				"invalid JSON does not skip later semantic problems");
			var input = new CaptureLifecycle.Input(true, true, 1, true, 1, false, true, false);
			var trace = CaptureTransitionTrace.encode(input, 1, CaptureLifecycle.Resolution.CAUGHT);
			write(first, trace + "\n");
			check(DiagnosticReplay.replay(List.of(first)).warnings() == 0, "compressed terminal trace replay");
			trace.addProperty("result", "BREAKOUT_READY");
			write(first, trace + "\n");
			check(DiagnosticReplay.replay(List.of(first)).detail().contains("capture transition mismatch"),
				"terminal decision mismatch is reported");
			trace.addProperty("pending", "true");
			write(first, trace + "\n" + line("RESOLVE A9 CAUGHT", "IDLE", 1));
			report = DiagnosticReplay.replay(List.of(first));
			check(report.warnings() == 2 && report.detail().contains("malformed capture transition"),
				"malformed terminal trace cannot hide subsequent diagnostics");
			var pairing = CapturePairingTrace.encode("breakout", new double[][] {{1, 2}, {1.1, 100}},
				16, 17, new int[] {1, 0});
			write(first, pairing + "\n");
			check(DiagnosticReplay.replay(List.of(first)).warnings() == 0, "compressed pairing trace replays");
			CapturePairingTrace.addIdentities(pairing, List.of(new java.util.UUID(0, 1).toString(),
				new java.util.UUID(0, 2).toString()), List.of(1L, 2L));
			write(first, pairing + "\n");
			check(DiagnosticReplay.replay(List.of(first)).warnings() == 0,
				"pairing context identifies each matrix axis without changing the decision");
			pairing.getAsJsonArray("logicals").set(1, new com.google.gson.JsonPrimitive(1L));
			write(first, pairing + "\n");
			check(DiagnosticReplay.replay(List.of(first)).detail().contains("malformed capture pairing"),
				"duplicate logical ownership in a pairing is rejected");
			pairing.getAsJsonArray("logicals").set(1, new com.google.gson.JsonPrimitive(2L));
			pairing.getAsJsonArray("result").set(0, new com.google.gson.JsonPrimitive(0));
			write(first, pairing + "\n");
			check(DiagnosticReplay.replay(List.of(first)).detail().contains("capture pairing mismatch"), "wrong body assignment reported");
			pairing.getAsJsonArray("result").set(0, new com.google.gson.JsonPrimitive(0.5));
			write(first, pairing + "\n");
			check(DiagnosticReplay.replay(List.of(first)).detail().contains("malformed capture pairing"), "fractional owner rejected");
			write(first, "x".repeat(DiagnosticReplay.MAX_RECORD_CHARACTERS + 4096) + "\n"
				+ line("ATTEMPT A2 bat", "ACTIVE", 1) + line("RESOLVE A2 CAUGHT", "IDLE", 1));
			report = DiagnosticReplay.replay(List.of(first));
			check(report.records() == 3 && report.warnings() == 1
				&& report.detail().contains("oversized record skipped; replay incomplete"),
				"oversized compressed record is bounded without losing later records");
			for (String invalid : List.of("1.5", "9223372036854775808", "\"1\"")) {
				write(first, "{\"k\":\"e\",\"s\":" + invalid + "}\n");
				check(DiagnosticReplay.replay(List.of(first)).detail().contains("malformed record fields"),
					"invalid sequence cannot be silently rounded or coerced");
			}
			write(first, "");
			check(DiagnosticReplay.replay(List.of(first)).warnings() == 1, "empty replay is not a clean validation");
			try (var out = new GZIPOutputStream(Files.newOutputStream(first))) {
				out.write(new byte[] {(byte) 0xc3, 0x28, 0x0a});
			}
			check(DiagnosticReplay.replay(List.of(first)).detail().contains("incomplete segment"),
				"compressed invalid UTF-8 is reported instead of replaced");
			write(first, line("ATTEMPT A1 bat", "ACTIVE", 1));
			Thread.currentThread().interrupt();
			try {
				check(DiagnosticReplay.replay(List.of(first)).detail().contains("incomplete segment"),
					"cancelled replay cannot report successful verification");
			} finally { Thread.interrupted(); }
			write(first, "{\"k\":\"x\",\"g\":\"CAPTURE/REPLAY\",\"a\":\"omitted\"}\n");
			check(DiagnosticReplay.replay(List.of(first)).detail().contains("capture trace omitted; replay incomplete"),
				"bounded trace omission must not report a complete clean replay");
			return 22;
		} finally {
			try (var paths = Files.list(root)) {
				for (Path path : paths.toList()) Files.delete(path);
			}
			Files.delete(root);
		}
	}

	private static String line(String message, String phase, int visit) {
		return "{\"k\":\"l\",\"c\":1,\"v\":" + visit + ",\"r\":1,\"p\":\"" + phase
			+ "\",\"g\":\"IDENTITY\",\"m\":\"" + message + "\"}\n";
	}

	private static void write(Path path, String text) throws Exception {
		try (var out = new GZIPOutputStream(Files.newOutputStream(path))) {
			out.write(text.getBytes(StandardCharsets.UTF_8));
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
