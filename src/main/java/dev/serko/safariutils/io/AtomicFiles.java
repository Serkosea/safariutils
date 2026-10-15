package dev.serko.safariutils.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes UTF-8 data without leaving a half-written config if the game closes. */
public final class AtomicFiles {
	private static final Object[] WRITE_LOCKS = new Object[32];
	static {
		for (int index = 0; index < WRITE_LOCKS.length; index++) WRITE_LOCKS[index] = new Object();
	}
	private AtomicFiles() {
	}

	public static void writeString(Path path, String contents) throws IOException {
		Path target = path.toAbsolutePath().normalize();
		// Windows cannot reliably replace one destination concurrently, even with distinct staging files.
		synchronized (WRITE_LOCKS[Math.floorMod(target.hashCode(), WRITE_LOCKS.length)]) {
			writeTarget(target, contents);
		}
	}

	/** Publishes a complete immutable backup; never truncates or replaces an existing target. */
	public static void copyOnce(Path source, Path destination) throws IOException {
		Path target = destination.toAbsolutePath().normalize();
		synchronized (WRITE_LOCKS[Math.floorMod(target.hashCode(), WRITE_LOCKS.length)]) {
			if (Files.isRegularFile(target)) return;
			if (!Files.isRegularFile(source)) throw new IOException("Backup source is not a regular file");
			Files.createDirectories(target.getParent());
			Path temporary = Files.createTempFile(target.getParent(), ".safari-backup-", ".tmp");
			try {
				Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
				try {
					// Same-directory rename of a complete file. ATOMIC_MOVE is deliberately not
					// requested: its existing-target replacement semantics vary by provider.
					Files.move(temporary, target);
				} catch (FileAlreadyExistsException raced) {
					if (!Files.isRegularFile(target)) throw raced;
				}
			} catch (IOException | RuntimeException failure) {
				try { Files.deleteIfExists(temporary); }
				catch (IOException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
				throw failure;
			}
			Files.deleteIfExists(temporary);
		}
	}

	private static void writeTarget(Path target, String contents) throws IOException {
		Files.createDirectories(target.getParent());
		// Each writer owns its staging file; concurrent saves cannot truncate one another.
		Path temporary = Files.createTempFile(target.getParent(), ".safari-write-", ".tmp");
		try {
			Files.writeString(temporary, contents, StandardCharsets.UTF_8);
			try {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException | RuntimeException failure) {
			try { Files.deleteIfExists(temporary); }
			catch (IOException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
			throw failure;
		}
	}
}
