package dev.serko.safariutils.client;

import java.util.ArrayDeque;
import java.util.Objects;

/** Short-lock bounded handoff. No serialization, logging or disk work runs under this lock. */
final class DiagnosticQueue<T> {
	private final int capacity;
	private final ArrayDeque<T> entries = new ArrayDeque<>();
	private long dropped;
	private boolean closed;

	DiagnosticQueue(int capacity) {
		if (capacity < 1) throw new IllegalArgumentException("Invalid queue capacity");
		this.capacity = capacity;
	}

	synchronized boolean offer(T entry) {
		Objects.requireNonNull(entry);
		if (closed) return false;
		if (entries.size() >= capacity) { dropped++; return false; }
		entries.addLast(entry);
		return true;
	}

	synchronized T poll() { return entries.pollFirst(); }
	synchronized int size() { return entries.size(); }
	synchronized long dropped() { return dropped; }
	synchronized long takeDropped() { long count = dropped; dropped = 0; return count; }
	synchronized void clear() { entries.clear(); dropped = 0; }
	synchronized void close() { closed = true; }
}
