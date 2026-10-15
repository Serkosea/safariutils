package dev.serko.safariutils.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** Deterministically validates the structured observations produced by a test session. */
public final class DiagnosticReplay {
	private static final dev.serko.safariutils.concurrent.BoundedRequests WORKER =
		new dev.serko.safariutils.concurrent.BoundedRequests("SafariUtils-Replay", 1, 1);
	// Larger than a maximum-size pairing matrix, independent of compressed file size.
	static final int MAX_RECORD_CHARACTERS = 256 * 1024;
	private static final Pattern ATTEMPT_ID = Pattern.compile("(?:^|\\s)A(\\d+)(?:\\s|$)");
	public record Report(Path path, int files, int records, int events, int transitions,
			int diagnostics, int warnings, String detail) {
		public String display() {
			return "Structured replay\n  file " + path.toAbsolutePath()
				+ "\n  files " + files + " · records " + records + " · events " + events + " · transitions "
				+ transitions + " · diagnostics " + diagnostics + "\n  warnings " + warnings
				+ (detail.isBlank() ? "" : "\n" + detail);
		}
	}

	private DiagnosticReplay() { }

	/** Local decompression/replay must not occupy the network request pool. */
	public static java.util.concurrent.CompletableFuture<Report> submitCurrent() {
		return WORKER.submit(DiagnosticReplay::replayCurrent);
	}

	public static void shutdown() {
		WORKER.close();
	}

	public static Report replayCurrent() {
		java.util.List<Path> paths;
		try {
			paths = OperationalLog.sealForReplay().stream().filter(Files::isRegularFile).toList();
		} catch (java.io.IOException failure) {
			OperationalLog.error("REPLAY", failure);
			return new Report(SafariPaths.logs(), 0, 0, 0, 0, 0, 1,
				"  unable to capture a complete diagnostic segment");
		}
		Path path = paths.isEmpty() ? OperationalLog.activeStructuredPath() : paths.getLast();
		if (paths.isEmpty()) {
			return new Report(path == null ? SafariPaths.logs() : path,
				0, 0, 0, 0, 0, 1, "  no structured diagnostic file is active");
		}
		return replay(paths);
	}

	/** Ordered segments of one session; independent of a running client for regression replay. */
	static Report replay(java.util.List<Path> segments) {
		java.util.List<Path> paths = java.util.List.copyOf(segments);
		if (paths.isEmpty()) throw new IllegalArgumentException("Replay requires at least one segment");
		Path path = paths.getLast();
		int records = 0;
		int events = 0;
		int transitions = 0;
		int diagnostics = 0;
		int warnings = 0;
		int captureInputs = 0;
		long sequence = 0L;
		long connection = -1L;
		long visit = -1L;
		long run = -1L;
		String phase = "";
		Map<String, Integer> warningKinds = new HashMap<>();
		Set<Long> activeAttempts = new HashSet<>();
		Set<Long> correctedTargets = new HashSet<>();
		for (Path segment : paths) try (BufferedReader reader = openReader(segment)) {
			String line;
			while ((line = readRecord(reader)) != null) {
				if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
				if (line.length() > MAX_RECORD_CHARACTERS) {
					records++;
					warnings++;
					warningKinds.merge("oversized record skipped; replay incomplete", 1, Integer::sum);
					continue;
				}
				if (line.isBlank()) continue;
				records++;
				JsonObject json;
				try {
					json = JsonParser.parseString(line).getAsJsonObject();
				} catch (RuntimeException malformed) {
					warnings++;
					warningKinds.merge("malformed JSON", 1, Integer::sum);
					continue;
				}
				try {
					String kind = text(json, "kind", "k");
					long nextConnection = number(json, connection, "connection", "c");
					long nextVisit = number(json, visit, "visit", "v");
					long nextRun = number(json, run, "run", "r");
					if (nextConnection < connection
						|| nextConnection == connection && nextVisit > 0 && nextVisit < visit
						|| nextConnection == connection && nextVisit == visit
							&& nextRun > 0 && nextRun < run) {
						warnings++;
						warningKinds.merge("epoch regression", 1, Integer::sum);
					}
					if (nextConnection != connection || nextVisit != visit) {
						activeAttempts.clear();
						correctedTargets.clear();
					}
					connection = nextConnection;
					visit = nextVisit;
					run = nextRun;
					if (json.has("phase") || json.has("p")) phase = text(json, "phase", "p");
					if ("ci".equals(kind)) {
						captureInputs++;
					} else if ("co".equals(kind)) {
						diagnostics++;
						try {
							if (!CaptureDecisionTrace.matches(json)) {
								warnings++;
								warningKinds.merge("capture decision mismatch or unsupported schema", 1, Integer::sum);
							}
						} catch (RuntimeException malformed) {
							warnings++;
							warningKinds.merge("malformed capture decision", 1, Integer::sum);
						}
					} else if ("cp".equals(kind)) {
						diagnostics++;
						try {
							if (!CapturePairingTrace.matches(json)) {
								warnings++;
								warningKinds.merge("capture pairing mismatch or unsupported schema", 1, Integer::sum);
							}
						} catch (RuntimeException malformed) {
							warnings++;
							warningKinds.merge("malformed capture pairing", 1, Integer::sum);
						}
					} else if ("ct".equals(kind)) {
						diagnostics++;
						try {
							if (!CaptureTransitionTrace.matches(json)) {
								warnings++;
								warningKinds.merge("capture transition mismatch or unsupported schema", 1, Integer::sum);
							}
						} catch (RuntimeException malformed) {
							warnings++;
							warningKinds.merge("malformed capture transition", 1, Integer::sum);
						}
					} else if ("event".equals(kind) || "e".equals(kind)) {
						events++;
						long nextSequence = number(json, sequence, "sequence", "s");
						if (nextSequence <= sequence) {
							warnings++;
							warningKinds.merge("event sequence", 1, Integer::sum);
						}
						sequence = nextSequence;
					} else if ("transition".equals(kind) || "t".equals(kind)) {
						transitions++;
					} else if ("diagnostic".equals(kind) || "d".equals(kind) || "l".equals(kind)
							|| "decision".equals(kind) || "x".equals(kind)) {
						diagnostics++;
						String category = text(json, "category", "g");
						String message = text(json, "message", "m");
						if ("LOG".equals(category) && message.startsWith("dropped=")) {
							warnings++;
							warningKinds.merge("diagnostic data gap", 1, Integer::sum);
						}
						if ("CAPTURE/REPLAY".equals(category) && "omitted".equals(text(json, "action", "a"))) {
							warnings++;
							warningKinds.merge("capture trace omitted; replay incomplete", 1, Integer::sum);
						}
						if (category.startsWith("INVARIANT")) {
							warnings++;
							warningKinds.merge("runtime invariant", 1, Integer::sum);
						}
						if (category.startsWith("IDENTITY")) {
							if (message.startsWith("RESET ")) {
								// A world/visit boundary intentionally abandons any server-side
								// capsule animations still in flight. They are not orphaned attempts.
								activeAttempts.clear();
								correctedTargets.clear();
								continue;
							}
							Matcher matcher = ATTEMPT_ID.matcher(message);
							if (matcher.find()) {
								long attempt = Long.parseLong(matcher.group(1));
								if (message.startsWith("ATTEMPT A")
									|| message.startsWith("DEFERRED-ATTEMPT A")) {
									if (!activeAttempts.add(attempt)) {
										warnings++;
										warningKinds.merge("duplicate attempt", 1, Integer::sum);
									}
								} else if (message.startsWith("TARGET-RETARGET A")) {
									if (!correctedTargets.add(attempt)) {
										warnings++;
										warningKinds.merge("repeated target correction", 1, Integer::sum);
									}
								} else if (message.startsWith("RESOLVE A")
									|| message.startsWith("RESOLVE-ORPHAN A")
									|| message.startsWith("RESOLVE-RETIRED A")
									|| message.startsWith("ATTEMPT-EXPIRE A")) {
									correctedTargets.remove(attempt);
									if (!activeAttempts.remove(attempt)) {
										warnings++;
										warningKinds.merge("unknown attempt resolution", 1, Integer::sum);
									}
								}
							}
						}
					}
				} catch (RuntimeException malformed) {
					warnings++;
					warningKinds.merge("malformed record fields", 1, Integer::sum);
				}
			}
		} catch (Exception error) {
			warnings++;
			warningKinds.merge("incomplete segment: " + error.getClass().getSimpleName(), 1, Integer::sum);
			if (Thread.currentThread().isInterrupted()) break;
		}
		if (records == 0) {
			warnings++;
			warningKinds.merge("no diagnostic records", 1, Integer::sum);
		}
		if (!"ACTIVE".equals(phase) && !activeAttempts.isEmpty()) {
			warnings += activeAttempts.size();
			warningKinds.merge("active attempt after run", activeAttempts.size(), Integer::sum);
		}
		StringBuilder detail = new StringBuilder();
		if (captureInputs > 0) detail.append("  capture inputs ").append(captureInputs)
			.append(" (use offline replayCaptures to verify tracker state)\n");
		warningKinds.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
			detail.append("  ").append(entry.getKey()).append(' ').append(entry.getValue()).append('\n'));
		return new Report(path, paths.size(), records, events, transitions, diagnostics, warnings,
			detail.toString().stripTrailing());
	}

	static BufferedReader openReader(Path path) throws Exception {
		if (path.getFileName().toString().endsWith(".gz")) {
			var input = Files.newInputStream(path);
			try {
				return new BufferedReader(new InputStreamReader(new GZIPInputStream(input),
					StandardCharsets.UTF_8.newDecoder()));
			} catch (Exception failure) {
				try { input.close(); } catch (Exception closing) { failure.addSuppressed(closing); }
				throw failure;
			}
		}
		return Files.newBufferedReader(path, StandardCharsets.UTF_8);
	}

	/** Drain oversized records without retaining them, then continue at the next JSONL boundary. */
	static String readRecord(BufferedReader reader) throws java.io.IOException {
		StringBuilder record = new StringBuilder(512);
		int value;
		int read = 0;
		while ((value = reader.read()) != -1) {
			if (value == '\n') break;
			if (record.length() <= MAX_RECORD_CHARACTERS) record.append((char) value);
			if ((++read & 4095) == 0 && Thread.currentThread().isInterrupted()) {
				throw new java.io.InterruptedIOException();
			}
		}
		return value == -1 && read == 0 ? null : record.toString();
	}

	private static String text(JsonObject json, String... keys) {
		for (String key : keys) if (json.has(key)) return json.get(key).getAsString();
		return "";
	}

	private static long number(JsonObject json, long fallback, String... keys) {
		for (String key : keys) if (json.has(key)) {
			var value = json.get(key);
			if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
				throw new IllegalArgumentException("Expected numeric " + key);
			}
			return value.getAsBigDecimal().longValueExact();
		}
		return fallback;
	}
}
