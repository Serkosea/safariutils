package dev.serko.safariutils.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/** One atomic working snapshot per recovery session; resetting never deletes prior recovery. */
public final class RecoverySnapshot {
	private final String stem;
	private Path directory;
	private Path target;

	public RecoverySnapshot(String stem) {
		if (stem == null || !stem.matches("[A-Za-z0-9][A-Za-z0-9._-]*"))
			throw new IllegalArgumentException("Invalid recovery filename stem");
		this.stem = stem;
	}

	public synchronized Path write(Path requestedDirectory, String contents) throws IOException {
		Objects.requireNonNull(contents);
		Path requested = requestedDirectory.toAbsolutePath().normalize();
		if (directory != null && !directory.equals(requested))
			throw new IOException("Recovery directory changed without a session reset");
		if (target == null) {
			directory = requested;
			target = directory.resolve(stem + "-" + UUID.randomUUID() + ".json");
		}
		AtomicFiles.writeString(target, contents);
		return target;
	}

	public synchronized void reset() {
		directory = null;
		target = null;
	}
}
