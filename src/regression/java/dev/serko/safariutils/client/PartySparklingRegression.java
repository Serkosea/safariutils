package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critters;
import java.util.Arrays;

public final class PartySparklingRegression {
	public static int run() {
		int checks = 0;
		check(dev.serko.safariutils.BuildVersion.has(dev.serko.safariutils.BuildVersion.Capability.REMOTE_PARTY_SYNC),
			"party transport unavailable in this edition"); checks++;
		for (boolean active : new boolean[] {false, true}) {
			for (boolean local : new boolean[] {false, true}) {
				check(!PartyItemSync.inventoryKnown(active, local, false), "unreported inventory treated as known"); checks++;
				check(PartyItemSync.inventoryKnown(active, local, true) == (active || local), "reported inventory availability"); checks++;
			}
		}
		var sender = new PartySparklingState();
		var receiver = new PartySparklingState();
		for (int i = 0; i < Critters.selectionOrder().size(); i++) {
			long bit = 1L << i;
			sender.discover(bit);
			long revision = sender.localRevision;
			sender.discover(bit);
			check(sender.localRevision == revision, "duplicate discovery resends"); checks++;
			int[] snapshot = parse(sender.payload());
			check(receiver.accept(snapshot) && receiver.active() == sender.active(), "late join snapshot"); checks++;
			check(receiver.localRevision == 0 && receiver.payload().equals("0,0,0,0"), "remote echo"); checks++;
			sender.caught(bit);
			check(receiver.accept(parse(sender.payload())) && receiver.active() == 0, "catch clears reminder"); checks++;
			receiver.accept(snapshot);
			check(receiver.active() == 0, "old snapshot revived catch"); checks++;
		}
		for (int[] invalid : new int[][] {{}, {0,0,0}, {-1,0,0,0}, {0,128,0,0}, {0,0,0,128}, {Integer.MAX_VALUE,0,0,0}}) {
			check(!receiver.accept(invalid), "invalid masks accepted"); checks++;
		}
		receiver.clear();
		check(receiver.active() == 0 && receiver.shared() == 0 && receiver.caughtMask() == 0, "visit reset"); checks++;
		receiver.accept(new int[] {1,0,0,0});
		check(receiver.reminder(true, false, 1) == 1, "pre-ticket reminder missing"); checks++;
		check(receiver.reminder(true, true, 1000) == 0, "ticketed reminder retained"); checks++;
		check(receiver.reminder(false, false, 1000) == 0, "reminder outside visit"); checks++;
		check(receiver.reminder(true, false, 0) == 0 && receiver.reminder(true, false, -1) == 0, "expired reminder"); checks++;
		receiver.discover(1);
		check(receiver.shared() == 1, "local sighting hid shared Safe Mode knowledge"); checks++;
		return checks;
	}
	private static int[] parse(String value) { return Arrays.stream(value.split(",")).mapToInt(Integer::parseInt).toArray(); }
	private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
