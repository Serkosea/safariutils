package dev.serko.safariutils.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes UTF-8 data without leaving a half-written config if the game closes. */
public final class AtomicFiles {
	private AtomicFiles() {
	}

	public static void writeString(Path path, String contents) throws IOException {
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
}
