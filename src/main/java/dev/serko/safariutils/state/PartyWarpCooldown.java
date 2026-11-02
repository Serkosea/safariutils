package dev.serko.safariutils.state;

import java.util.Locale;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** Account-local warp spacing; keep this ledger across Hypixel world/party changes. */
public final class PartyWarpCooldown {
    public static final long INTERVAL_MILLIS = 5_100L;
    private static final Pattern REJECTION = Pattern.compile(
        "^You cannot warp players again for another ([0-9]{1,2}) seconds?\\.?(?:\\s.*)?$",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private final MonotonicCooldowns<String> clock;
    private long duration = INTERVAL_MILLIS;

    public PartyWarpCooldown(LongSupplier nanoTime) { clock = new MonotonicCooldowns<>(nanoTime); }

    public static boolean isWarp(String command) {
        if (command == null) return false;
        String value = command.strip().toLowerCase(Locale.ROOT);
        if (value.startsWith("/")) value = value.substring(1);
        return value.equals("pwarp") || value.matches("(?:p|party)\\s+warp");
    }

    public void observeCommand(String command) {
        if (!isWarp(command)) return;
        duration = INTERVAL_MILLIS;
        clock.mark("warp");
    }

    public static int rejectedSeconds(String line) {
        if (line == null) return -1;
        var match = REJECTION.matcher(line.strip());
        return match.matches() ? Math.min(60, Integer.parseInt(match.group(1))) : -1;
    }

    public boolean observeRejection(String line) {
        int seconds = rejectedSeconds(line);
        if (seconds < 0) return false;
        // Repeated feedback must never shorten an already running restriction.
        duration = Math.max(remainingMillis(), seconds * 1_000L + 100L);
        clock.mark("warp");
        return true;
    }

    public long remainingMillis() { return clock.remainingMillis("warp", duration); }
}
