package dev.serko.safariutils.client;

import com.google.gson.JsonParser;
import java.nio.file.Path;

/** Separate JVM entry point: recorded inputs must never be replayed into the running client's state. */
public final class ReplayCaptures {
	public static void main(String[] args) throws Exception {
		if (args.length == 0) throw new IllegalArgumentException("Supply sealed segments from one session in order");
		try (var replay = new CaptureReplaySession()) {
			int preVisitNotices = 0;
			for (String arg : args) {
				try (var reader = DiagnosticReplay.openReader(Path.of(arg))) {
					String line;
					while ((line = DiagnosticReplay.readRecord(reader)) != null) {
						if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
						if (line.length() > DiagnosticReplay.MAX_RECORD_CHARACTERS) throw new IllegalArgumentException("Oversized record; replay incomplete");
						if (line.isBlank()) continue;
						var json = JsonParser.parseString(line).getAsJsonObject();
						if (json.has("g") && "LOG".equals(json.get("g").getAsString())
							&& json.has("m") && json.get("m").getAsString().startsWith("dropped=")) {
							throw new IllegalArgumentException("Diagnostic queue dropped records; replay incomplete");
						}
						if (json.has("g") && "CAPTURE/REPLAY".equals(json.get("g").getAsString())
							&& json.has("a") && "omitted".equals(json.get("a").getAsString())) {
							if (replay.records() == 0 && json.has("d")
								&& "input journal awaits visit boundary".equals(json.get("d").getAsString())) {
								preVisitNotices++;
								continue;
							}
							throw new IllegalArgumentException("Capture records omitted; replay incomplete");
						}
						if (json.has("k") && "ci".equals(json.get("k").getAsString())) replay.accept(json);
					}
				}
			}
			if (replay.records() == 0) throw new IllegalArgumentException("No recorded capture inputs; older logs cannot perform this audit");
			System.out.println("Verified " + replay.records() + " recorded capture inputs against the production tracker");
			if (preVisitNotices > 0) System.out.println("Excluded " + preVisitNotices + " pre-visit waiting notices; no run inputs skipped");
		}
	}
	private ReplayCaptures() { }
}
