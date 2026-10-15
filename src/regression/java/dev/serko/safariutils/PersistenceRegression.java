package dev.serko.safariutils;

import com.google.gson.JsonParser;
import dev.serko.safariutils.io.AtomicFiles;
import dev.serko.safariutils.io.PersistentJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class PersistenceRegression {
	static int run() throws Exception {
		int checks = 0;
		checks += check(PersistentJson.schema(JsonParser.parseString("{}").getAsJsonObject(), "s") == 0,
			"missing schema remains legacy");
		for (String value : List.of("null", "true", "[]", "[1]", "{}", "-1", "1.5", "999999999999", "\"invalid\"")) {
			var root = JsonParser.parseString("{\"s\":" + value + "}").getAsJsonObject();
			checks += check(PersistentJson.schema(root, "s") == Integer.MAX_VALUE, "unrecognized schema is protected");
			try {
				PersistentJson.requireSupportedSchema(root, "s", 4);
				throw new AssertionError("invalid schema imported");
			} catch (IllegalArgumentException expected) { }
		}
		checks += check(PersistentJson.schema(JsonParser.parseString("{\"s\":\"2\"}").getAsJsonObject(), "s") == 2,
			"legacy numeric string accepted");
		var original = JsonParser.parseString("{\"nested\":{\"known\":1,\"future\":2},\"extra\":3}").getAsJsonObject();
		var known = JsonParser.parseString("{\"nested\":{\"known\":4}}").getAsJsonObject();
		var merged = PersistentJson.merge(original, known);
		checks += check(merged.getAsJsonObject("nested").get("future").getAsInt() == 2 && merged.has("extra"),
			"unknown fields preserved");
		checks += check(original.getAsJsonObject("nested").get("known").getAsInt() == 1, "merge does not mutate source");
		Path directory = Files.createTempDirectory("safari-persistence-regression-");
		try {
			Path target = directory.resolve("data.json");
			AtomicFiles.writeString(target, "original");
			Path backup = PersistentJson.backupBeforeUpgrade(target, directory.resolve("backups"), "data", 1);
			AtomicFiles.writeString(target, "changed");
			PersistentJson.backupBeforeUpgrade(target, directory.resolve("backups"), "data", 1);
			checks += check(Files.readString(backup).equals("original"), "upgrade backup remains immutable");
			Path competing = directory.resolve("competing.json");
			AtomicFiles.writeString(competing, "competing");
			Path once = directory.resolve("once.json");
			List<Callable<Void>> backupWriters = new ArrayList<>();
			for (int index = 0; index < 16; index++) {
				Path input = index % 2 == 0 ? target : competing;
				backupWriters.add(() -> { AtomicFiles.copyOnce(input, once); return null; });
			}
			try (var executor = Executors.newFixedThreadPool(4)) {
				for (var future : executor.invokeAll(backupWriters, 20, TimeUnit.SECONDS)) future.get();
			}
			String winner = Files.readString(once);
			checks += check(List.of("changed", "competing").contains(winner), "concurrent backups publish one complete source");
			AtomicFiles.copyOnce(backup, once);
			checks += check(Files.readString(once).equals(winner), "later backup cannot overwrite first source");
			Path missingBackup = directory.resolve("missing-backup.json");
			try {
				AtomicFiles.copyOnce(directory.resolve("missing.json"), missingBackup);
				throw new AssertionError("missing source copied");
			} catch (IOException expected) { }
			checks += check(!Files.exists(missingBackup), "failed backup leaves no published destination");
			List<String> payloads = new ArrayList<>();
			List<Callable<Void>> writers = new ArrayList<>();
			for (int index = 0; index < 16; index++) {
				String payload = ("writer-" + index + "\n").repeat(1_000);
				payloads.add(payload);
				writers.add(() -> { AtomicFiles.writeString(target, payload); return null; });
			}
			try (var executor = Executors.newFixedThreadPool(4)) {
				for (var future : executor.invokeAll(writers, 20, TimeUnit.SECONDS)) future.get();
			}
			checks += check(payloads.contains(Files.readString(target)), "overlapping writes publish one complete payload");
			Path blocked = Files.createDirectory(directory.resolve("blocked"));
			Files.writeString(blocked.resolve("keep"), "untouched");
			try {
				AtomicFiles.writeString(blocked, "must-not-replace");
				throw new AssertionError("nonempty directory replaced");
			} catch (IOException expected) { }
			checks += check(Files.readString(blocked.resolve("keep")).equals("untouched"), "failed replacement preserves destination");
			try {
				AtomicFiles.copyOnce(target, blocked);
				throw new AssertionError("backup replaced directory");
			} catch (IOException expected) { }
			checks += check(Files.readString(blocked.resolve("keep")).equals("untouched"), "backup destination conflict preserves directory");
			try (var children = Files.list(directory)) {
				checks += check(children.noneMatch(path -> path.getFileName().toString().startsWith(".safari-")),
					"successful and failed writes clean staging files");
			}
		} finally {
			try (var children = Files.walk(directory)) {
				for (Path path : children.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
			}
		}
		return checks;
	}

	private static int check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
		return 1;
	}
}
