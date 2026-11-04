package dev.serko.safariutils.state;

import java.util.*;

/** Small chronological in-memory feed. Never writes logs or retains protocol payloads. */
public final class SyncActivityFeed {
    public record Entry(long time, String text, int colour) { }
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    public void add(long time, String text, int colour) {
        if (text == null || text.isBlank()) return;
        String clean = text.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        if (clean.length() > 88) clean = clean.substring(0, 85) + "...";
        if (!entries.isEmpty() && entries.getLast().text().equals(clean)) return;
        if (entries.size() == 8) entries.removeFirst();
        entries.addLast(new Entry(time, clean, colour));
    }
    public List<Entry> entries() { return List.copyOf(entries); }
    public void clear() { entries.clear(); }
}
