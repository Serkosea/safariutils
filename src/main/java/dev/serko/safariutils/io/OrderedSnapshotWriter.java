package dev.serko.safariutils.io;

import java.io.IOException;

/** Serializes one store's writes and prevents delayed snapshots replacing newer committed data. */
public final class OrderedSnapshotWriter {
	@FunctionalInterface
	public interface Write { void run() throws IOException; }
	private long committed = -1;

	public synchronized boolean write(long revision, Write action) throws IOException {
		if (revision < 0) throw new IllegalArgumentException("Negative snapshot revision");
		if (revision <= committed) return false;
		action.run();
		committed = revision;
		return true;
	}
}
