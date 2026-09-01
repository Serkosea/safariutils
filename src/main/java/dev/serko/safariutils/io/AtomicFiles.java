package dev.serko.safariutils.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes UTF-8 data without leaving a half-written config if the game closes. */
public final class AtomicFiles {
	private static volatile boolean writesSuspended;

	private AtomicFiles() {
	}

	public static void writeString(Path path, String contents) throws IOException {
		writeString(path, contents, false);
	}

	/** Writes while suspended only for an explicitly approved testing-data exception. */
	public static void writeString(Path path, String contents, boolean allowWhileSuspended)
			throws IOException {
		if (writesSuspended && !allowWhileSuspended) return;
		Files.createDirectories(path.getParent());
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		Files.writeString(temporary, contents, StandardCharsets.UTF_8);
		try {
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING,
				StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** Suspends every persistent data write while an isolated test session is active. */
	public static void suspendWrites(boolean suspended) {
		writesSuspended = suspended;
	}
}
