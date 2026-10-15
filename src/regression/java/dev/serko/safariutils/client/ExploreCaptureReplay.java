package dev.serko.safariutils.client;

import com.google.gson.JsonParser;
import java.nio.file.Path;

/** Explicit counterfactual exploration of changed logic, NOT a recorded-state verification. */
public final class ExploreCaptureReplay {
	public static void main(String[] args) throws Exception {
		if (args.length != 2) throw new IllegalArgumentException("Supply sealed journal and species");
		int inputs = 0, differences = 0;
		try (var replay = new CaptureReplaySession(); var reader = DiagnosticReplay.openReader(Path.of(args[0]))) {
			String line;
			while ((line = DiagnosticReplay.readRecord(reader)) != null) {
				if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
				if (line.isBlank()) continue;
				var json = JsonParser.parseString(line).getAsJsonObject();
				if (json.has("g") && "LOG".equals(json.get("g").getAsString())
					&& json.has("m") && json.get("m").getAsString().startsWith("dropped="))
					throw new IllegalArgumentException("Diagnostic queue dropped records; exploration incomplete");
				if (json.has("g") && "CAPTURE/REPLAY".equals(json.get("g").getAsString())
					&& json.has("a") && "omitted".equals(json.get("a").getAsString())) {
					if (inputs == 0 && json.has("d")
						&& "input journal awaits visit boundary".equals(json.get("d").getAsString())) continue;
					throw new IllegalArgumentException("Capture records omitted; exploration incomplete");
				}
				if (!json.has("k") || !"ci".equals(json.get("k").getAsString())) continue;
				try { replay.accept(json); }
				catch (IllegalStateException mismatch) {
					if (!mismatch.getMessage().startsWith("Capture state mismatch at input ")) throw mismatch;
					differences++;
				}
				inputs++;
				if (json.has("critter") && args[1].equals(json.get("critter").getAsString())) {
					System.out.println("input=" + json.get("seq") + " op=" + json.get("op") + " type=" + json.get("type"));
					for (var view : CritterState.logicalCritters()) if (view.critter().name().equals(args[1]))
						System.out.println("logical=" + view.id() + " life=" + view.life() + " pity=" + view.pity());
				}
			}
		}
		if (inputs == 0) throw new IllegalArgumentException("No recorded capture inputs");
		System.out.println("COUNTERFACTUAL ONLY: inputs=" + inputs + " differing fingerprints=" + differences);
	}
}
