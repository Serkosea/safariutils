package dev.serko.safariutils.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Preserves disk bytes and unsaved working values before an explicit replacement. */
public final class ImportBackup {
	private ImportBackup() { }

	public static Path preserve(Path source, Path directory, String workingJson) throws IOException {
		Objects.requireNonNull(workingJson);
		Files.createDirectories(directory);
		Path session = Files.createTempDirectory(directory, "settings-import-");
		// Do not treat an unreadable/non-regular source as a missing file. A failed
		// preservation must abort the import, leaving the source and live state alone.
		if (!Files.notExists(source, LinkOption.NOFOLLOW_LINKS)) {
			AtomicFiles.copyOnce(source, session.resolve("original.json"));
		}
		Path working = session.resolve("settings.json");
		AtomicFiles.writeString(working, workingJson);
		return working;
	}
}
