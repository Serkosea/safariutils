package dev.serko.safariutils;

import dev.serko.safariutils.state.PartyWarpCooldown;
import java.util.concurrent.atomic.AtomicLong;

final class PartyWarpCooldownRegression {
    static int run() {
        int checks = 0;
        for (String command : new String[]{"p warp", "/party warp", "PARTY WARP", "pwarp", " /p   warp "}) {
            check(PartyWarpCooldown.isWarp(command), "warp alias " + command); checks++;
        }
        for (String command : new String[]{"party invite a", "p disband", "warp safari", "msg a p warp", "party warp extra", ""}) {
            check(!PartyWarpCooldown.isWarp(command), "unrelated command " + command); checks++;
        }
        AtomicLong now = new AtomicLong(-100_000_000_000L);
        PartyWarpCooldown cooldown = new PartyWarpCooldown(now::get);
        check(cooldown.remainingMillis() == 0, "initially ready"); checks++;
        cooldown.observeCommand("/p warp");
        now.addAndGet(5_000_000_000L);
        check(cooldown.remainingMillis() == 100, "five seconds plus network margin"); checks++;
        cooldown.observeCommand("party invite friend");
        now.addAndGet(100_000_000L);
        check(cooldown.remainingMillis() == 0, "invites do not extend warp interval"); checks++;
        check(cooldown.observeRejection("You cannot warp players again for another 5 seconds.\nPlease wait before warping your party again."), "server feedback parsed"); checks++;
        check(cooldown.remainingMillis() == 5100, "rejection supplies actual remaining interval"); checks++;
        cooldown.observeRejection("You cannot warp players again for another 1 second.");
        check(cooldown.remainingMillis() == 5100, "feedback cannot shorten existing interval"); checks++;
        check(!cooldown.observeRejection("From Player: You cannot warp players again for another 5 seconds."), "chat cannot masquerade as feedback"); checks++;
        now.addAndGet(5_100_000_000L);
        check(cooldown.remainingMillis() == 0, "retry becomes eligible"); checks++;
        return checks;
    }
    private static void check(boolean valid, String detail) {
        if (!valid) throw new AssertionError(detail);
    }
}
