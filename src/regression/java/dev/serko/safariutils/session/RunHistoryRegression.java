package dev.serko.safariutils.session;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/** Uses the production decoder; never loads or changes installed history. */
public final class RunHistoryRegression {
	private static int checks;
	public static int run() throws Exception {
		checks = 0;
		var legacy = JsonParser.parseString("[{\"started\":11,\"ended\":29,\"future\":7}]");
		var decoded = RunHistory.decodeStore(legacy, true);
		check(decoded.migrated() && !decoded.readOnly(), "valid legacy remains writable");
		check(decoded.runs().getFirst().durationMillis() == 18, "legacy values retained");
		check(decoded.runs().getFirst().toJson().get("future").getAsInt() == 7, "unknown record field retained");
		String partial = "{\"schemaVersion\":1,\"runs\":[{\"started\":11},null,7,{\"started\":{}},{\"started\":29}]}";
		var damaged = RunHistory.decodeStore(JsonParser.parseString(partial), true);
		check(damaged.readOnly() && damaged.rejected() == 3, "malformed entries protect source");
		check(damaged.runs().size() == 2 && damaged.runs().getLast().started == 29, "readable neighbors survive");
		check(damaged.root().getAsJsonArray("runs").size() == 5, "raw source retains all entries");
		for (String version : List.of("2", "null", "1.5")) {
			var input = JsonParser.parseString("{\"schemaVersion\":" + version + ",\"futureLayout\":true}");
			var future = RunHistory.decodeStore(input, true);
			check(future.readOnly() && !future.migrated() && future.root().equals(input), "unsupported layout remains untouched");
		}
		var path = Files.createTempFile("safari-history-regression-", ".json");
		try {
			Files.writeString(path, partial);
			try { RunHistory.readForIntegrity(path); throw new AssertionError("partial audit accepted"); }
			catch (IOException expected) { checks++; }
			try { RunHistory.writeForIntegrity(path, decoded.runs()); throw new AssertionError("partial repair accepted"); }
			catch (IOException expected) { checks++; }
			check(Files.readString(path).equals(partial), "refused repair leaves bytes identical");
			var recovery = Files.createTempFile("safari-history-recovery-", ".json");
			try {
				RunHistory.writeRecovery(recovery, decoded.runs());
				check(RunHistory.readForIntegrity(recovery).size() == 1, "new runs have a separately readable recovery store");
				RunHistory.writeRecovery(recovery, List.of(decoded.runs().getFirst(), decoded.runs().getFirst()));
				check(RunHistory.readForIntegrity(recovery).size() == 2, "recovery snapshot retains subsequent runs");
				check(Files.readString(path).equals(partial), "recovery never changes protected source");
			} finally { Files.deleteIfExists(recovery); }
			Files.writeString(path, legacy.toString());
			RunHistory.writeForIntegrity(path, decoded.runs());
			check(RunHistory.readForIntegrity(path).getFirst().toJson().has("future"), "valid repair round trip preserves unknown fields");
			String valid = Files.readString(path);
			try { RunHistory.writeForIntegrity(path, java.util.Arrays.asList((RunRecord) null)); throw new AssertionError("null record saved"); }
			catch (IllegalArgumentException expected) { checks++; }
			check(Files.readString(path).equals(valid), "invalid save cannot drop records");
		} finally { Files.deleteIfExists(path); }
        // Isolated in-memory production lifecycle: no installed files or backup paths.
        check(RunHistory.runs().isEmpty(), "history test starts isolated");
        try {
            var empty = new SafariSession("Test", 100);
            empty.finishInterrupted(200);
            RunHistory.record(empty);
            check(RunHistory.runs().size() == 1 && RunHistory.runs().getFirst().partyTotal() == 0,
                "paid zero-catch run survives disconnect");
            var tail = new RunRewardTail();
            tail.close(empty, false, 200);
            check(tail.get(201) == empty, "reward tail targets closed run");
            var doom = dev.serko.safariutils.data.Critters.byName("Doomspiral");
            empty.record(new dev.serko.safariutils.parse.CritterEvent(
                dev.serko.safariutils.parse.CritterEvent.Type.OWN_CATCH, doom, null, 2, false), 201);
            empty.confirmSafariEssence(85, 202);
            RunHistory.updateFinished(empty);
            check(RunHistory.runs().size() == 1 && RunHistory.runs().getFirst().own.get("Doomspiral") == 1,
                "late catch amends existing history without duplicate");
            check(RunHistory.runs().getFirst().shards.get("Doomspiral") == 2
                && RunHistory.runs().getFirst().safariEssence == 85, "late rewards survive");
            check(RunHistory.runs().getFirst().ended == 200, "late delivery does not inflate duration");
            var lateRecorder = SessionManager.class.getDeclaredMethod("recordLateReward", String.class,
                dev.serko.safariutils.parse.CritterEvent.class, long.class);
            lateRecorder.setAccessible(true);
            var tailField = SessionManager.class.getDeclaredField("rewardTail"); tailField.setAccessible(true);
            var managerTail = (RunRewardTail) tailField.get(null);
            try {
                managerTail.close(empty, false, 200);
                String line = "CAPTURE! You caught a Gazer and gained a Gazer Shard!";
                check((boolean) lateRecorder.invoke(null, line,
                    dev.serko.safariutils.parse.ChatParser.parse(line, "Test"), 203L), "production late reward route accepts catch");
                check(RunHistory.runs().getFirst().own.get("Gazer") == 1, "late reward route updates history");
                managerTail.close(empty, true, 200);
                int saved = RunHistory.runs().getFirst().own.get("Gazer");
                lateRecorder.invoke(null, line, dev.serko.safariutils.parse.ChatParser.parse(line, "Test"), 204L);
                check(RunHistory.runs().getFirst().own.get("Gazer") == saved, "suppressed tail never updates saved stats");
            } finally { managerTail.clear(); }

            check(tail.get(3201) == null, "old reward tail expires");
            tail.close(empty, false, 5000); tail.clear();
            check(tail.get(5001) == null, "next visit clears old reward target");
            var alpha = new SafariSession("Test", 6000);
            alpha.excludeFromHistory(); alpha.finishInterrupted(7000);
            RunHistory.record(alpha); RunHistory.updateFinished(alpha);
            check(RunHistory.runs().size() == 1, "Alpha session cannot enter history in any edition");
            var stranger = new SafariSession("Test", 8000);
            RunHistory.updateFinished(stranger);
            check(RunHistory.runs().size() == 1, "late update cannot manufacture a run");
        } finally { RunHistory.load(null); }

		return checks;
	}
	private static void check(boolean value, String message) {
		if (!value) throw new AssertionError(message);
		checks++;
	}
}
