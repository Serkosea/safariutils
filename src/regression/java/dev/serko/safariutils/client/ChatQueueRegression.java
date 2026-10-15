package dev.serko.safariutils.client;

import dev.serko.safariutils.state.SafariEpoch;

public final class ChatQueueRegression {
	public static int run() {
		var epoch = new SafariEpoch(1, 2, 3, "test", SafariEpoch.Phase.ACTIVE);
		var queued = new ChatQueue.Queued("/pc test", Long.MAX_VALUE - 100, 200, true, 1, 2, 3, 4);
		check(!queued.ready(Long.MAX_VALUE - 1), "delay cannot send early");
		check(queued.ready(Long.MIN_VALUE + 99), "delay crosses nanoTime wrap");
		check(!queued.stale(epoch, 4), "same owner retains message");
		check(queued.stale(epoch, 5), "changed party drops old message");
		check(queued.stale(new SafariEpoch(2, 2, 3, "test", SafariEpoch.Phase.ACTIVE), 4), "connection changed");
		check(queued.stale(new SafariEpoch(1, 4, 3, "test", SafariEpoch.Phase.ACTIVE), 4), "visit changed");
		check(queued.stale(new SafariEpoch(1, 2, 4, "test", SafariEpoch.Phase.ACTIVE), 4), "run changed");
		var verified = new ChatQueue.Queued("/pc test", 0, 0, true, 1, 2, 0, -1);
		check(!verified.stale(epoch, 5), "independently verified pre-run notice permits roster confirmation");
		var solo = new dev.serko.safariutils.state.PartySnapshot(5, 0,
			dev.serko.safariutils.state.PartySnapshot.Status.SOLO, false, 1, java.util.List.of(), 0, java.util.List.of());
		check(!verified.unavailable(solo), "independently verified notice waits for lagging solo roster");
		var unverified = new ChatQueue.Queued("/pc test", 0, 0, false, 1, 2, 0, -1);
		check(unverified.unavailable(solo), "unverified solo-party message cannot block unrelated chat");
		int checks = 10;
		for (String command : new String[] {"/pc hi", "/PC hi", "/pchat hi", "/party chat hi"}) {
			check(ChatQueue.partyCommand(command), "party chat alias"); checks++;
		}
		for (String command : new String[] {"/gc hi", "/party kick person", "pc hi", "/pchatty hi"}) {
			check(!ChatQueue.partyCommand(command), "not a party chat command"); checks++;
		}
		return checks;
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
