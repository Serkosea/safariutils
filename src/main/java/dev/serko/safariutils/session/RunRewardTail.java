package dev.serko.safariutils.session;

/** Bounded reconciliation of reward messages already in flight at a run boundary. */
final class RunRewardTail {
    private static final long WINDOW_MILLIS = 3_000;
    private SafariSession session;
    private long closedAt;
    private boolean suppressed;

    void close(SafariSession finished, boolean excluded, long now) {
        session = finished;
        suppressed = excluded;
        closedAt = now;
    }

    SafariSession get(long now) {
        if (now < closedAt || now - closedAt > WINDOW_MILLIS) clear();
        return session;
    }

    boolean suppressed() { return suppressed; }

    void clear() { session = null; suppressed = false; }
}
