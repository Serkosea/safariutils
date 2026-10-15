package dev.serko.safariutils.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded repeat coalescing. Returns work to the caller; never performs I/O under its lock. */
final class DiagnosticRepeats {
	record Summary(String category, String message, long count) { }
	record Decision(boolean emit, List<Summary> summaries) { }
	private record Key(String category, String message) { }
	private static final class Repeat {
		long last;
		long skipped;
		Repeat(long now) { last = now; }
	}
	private final int limit;
	private final long window;
	private final Map<Key, Repeat> entries = new LinkedHashMap<>(16, 0.75f, true);

	DiagnosticRepeats(int limit, long window) {
		if (limit < 1 || window < 0) throw new IllegalArgumentException("Invalid diagnostic repeat bounds");
		this.limit = limit;
		this.window = window;
	}

	synchronized Decision accept(String category, String message, long now) {
		Key key = new Key(category, message);
		Repeat previous = entries.get(key);
		if (previous != null && now >= previous.last && now - previous.last <= window) {
			previous.last = now;
			previous.skipped++;
			return new Decision(false, List.of());
		}
		List<Summary> summaries = new ArrayList<>();
		if (previous != null) summarize(key, previous, summaries);
		else if (entries.size() >= limit) {
			var iterator = entries.entrySet().iterator();
			var oldest = iterator.next();
			summarize(oldest.getKey(), oldest.getValue(), summaries);
			iterator.remove();
		}
		entries.put(key, new Repeat(now));
		return new Decision(true, List.copyOf(summaries));
	}

	synchronized List<Summary> expire(long now) {
		List<Summary> summaries = new ArrayList<>();
		entries.entrySet().removeIf(entry -> {
			Repeat repeat = entry.getValue();
			if (now >= repeat.last && now - repeat.last <= window) return false;
			summarize(entry.getKey(), repeat, summaries);
			return true;
		});
		return List.copyOf(summaries);
	}

	synchronized void clear() { entries.clear(); }
	synchronized List<Summary> drain() {
		List<Summary> summaries = new ArrayList<>();
		entries.forEach((key, repeat) -> summarize(key, repeat, summaries));
		entries.clear();
		return List.copyOf(summaries);
	}
	synchronized int size() { return entries.size(); }

	private static void summarize(Key key, Repeat repeat, List<Summary> output) {
		if (repeat.skipped > 0) output.add(new Summary(key.category, key.message, repeat.skipped));
	}
}
