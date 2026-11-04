package dev.serko.safariutils.api;

/** Expected local admission delay, shared by command and screen lookups. */
public final class LookupCooldownException extends IllegalStateException {
    private final long seconds;
    public LookupCooldownException(long remainingMillis) {
        super("Lookup cooldown active");
        seconds = Math.max(1, (remainingMillis - 1) / 1000 + 1);
    }
    public long seconds() { return seconds; }
}
