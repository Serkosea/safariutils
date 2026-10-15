package dev.serko.safariutils;

import dev.serko.safariutils.io.ImportBackup;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;

final class ImportBackupRegression {
	static int run() throws Exception {
		var root = Files.createTempDirectory("safari-import-test-");
		try {
			var source = root.resolve("source.json");
			var backups = root.resolve("backups");
			Files.writeString(source, "unreadable or future-version original");
			var backup = ImportBackup.preserve(source, backups, "{\"live\":1}");
			check(Files.readString(backup).equals("{\"live\":1}"), "unsaved working values preserved");
			check(Files.readString(backup.resolveSibling("original.json")).equals(Files.readString(source)),
				"original retained byte-for-byte without parsing");
			var second = ImportBackup.preserve(source, backups, "{\"live\":2}");
			check(!second.equals(backup) && Files.readString(backup).equals("{\"live\":1}"),
				"repeated imports never overwrite previous working snapshots");
			var missing = ImportBackup.preserve(root.resolve("absent.json"), backups, "{}");
			check(!Files.exists(missing.resolveSibling("original.json")), "missing source needs only working snapshot");
			try {
				ImportBackup.preserve(root, backups, "{}");
				throw new AssertionError("non-file source accepted");
			} catch (IOException expected) { }
			try {
				ImportBackup.preserve(source, source, "{}");
				throw new AssertionError("unwritable backup directory accepted");
			} catch (IOException expected) { }
			check(Files.readString(source).equals("unreadable or future-version original"),
				"all backup paths leave source untouched");
			return 7;
		} finally {
			try (var paths = Files.walk(root)) {
				for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
			}
		}
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
