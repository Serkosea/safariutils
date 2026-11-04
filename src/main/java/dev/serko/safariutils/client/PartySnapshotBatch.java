package dev.serko.safariutils.client;

/** Bounded coalescing: live observations win over catch-up-only chat updates. */
final class PartySnapshotBatch {
    private long dueAt;
    private boolean live;
    void schedule(long now, long delay, boolean catchUpOnly) {
        // Do not postpone forever while inventory/observations keep changing.
        if (dueAt == 0) dueAt = now + delay;
        live |= !catchUpOnly;
    }
    boolean due(long now) { return dueAt != 0 && now >= dueAt; }
    boolean catchUpOnly() { return !live; }
    void clear() { dueAt = 0; live = false; }
}
