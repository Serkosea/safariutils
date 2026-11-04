package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.api.PartySyncProtocol;
import java.util.Arrays;

public final class PartySparklingRegression {
	public static int run() {
		SparklingWatch.verifyIndividualTracking();
		int checks = 4;
        var batch = new PartySnapshotBatch();
        batch.schedule(1000, 500, true);
        check(!batch.due(1499) && batch.due(1500) && batch.catchUpOnly(), "chat snapshot batching"); checks++;
        batch.schedule(1400, 500, true);
        check(batch.due(1500), "continuous changes postponed publication"); checks++;
        batch.schedule(1450, 500, false);
        check(!batch.catchUpOnly() && batch.due(1500), "live observation hidden by chat-only batch"); checks++;
        batch.schedule(1490, 500, true);
        check(!batch.catchUpOnly(), "chat update downgraded live delivery"); checks++;
        batch.clear();
        check(!batch.due(9999), "batch survived reset"); checks++;
        batch.schedule(2000, 500, true);
        check(batch.catchUpOnly() && batch.due(2500), "new chat batch inherited live state"); checks++;

		check(dev.serko.safariutils.BuildVersion.has(dev.serko.safariutils.BuildVersion.Capability.REMOTE_PARTY_SYNC),
			"party transport unavailable in this edition"); checks++;
		for (boolean active : new boolean[] {false, true}) {
			for (boolean local : new boolean[] {false, true}) {
				check(!PartyItemSync.inventoryKnown(active, local, false), "unreported inventory treated as known"); checks++;
				check(PartyItemSync.inventoryKnown(active, local, true) == (active || local), "reported inventory availability"); checks++;
			}
		}
		check(PartySyncProtocol.validAggregateShape(38, 2), "legacy inventory rejected"); checks++;
		check(PartySyncProtocol.validAggregateShape(39, 3), "coin inventory rejected"); checks++;
		check(!PartySyncProtocol.validAggregateShape(38, 3)
			&& !PartySyncProtocol.validAggregateShape(39, 2)
			&& !PartySyncProtocol.validAggregateShape(40, 3), "mismatched aggregate accepted"); checks++;
		int[] legacy = new int[38]; legacy[4] = 6;
		check(PartySyncProtocol.heldCoins(legacy) == -1, "discovered coins inferred as held"); checks++;
		int[] current = Arrays.copyOf(legacy, 39); current[33] = 3; current[38] = 3;
		check(PartySyncProtocol.heldCoins(current) == 3, "held coin index"); checks++;
		current[38] = 0;
		check(PartySyncProtocol.heldCoins(current) == 0, "zero coins became unknown"); checks++;
		check(!PartySyncProtocol.validHeldCoins(-1) && !PartySyncProtocol.validHeldCoins(65)
			&& PartySyncProtocol.validHeldCoins(0) && PartySyncProtocol.validHeldCoins(64), "coin bounds"); checks++;
		check(PartyObjectiveHud.hasHauntedItems(0, 1)
			&& PartyObjectiveHud.hasHauntedItems(1, -1)
			&& !PartyObjectiveHud.hasHauntedItems(0, -1)
			&& !PartyObjectiveHud.hasHauntedItems(0, 0), "Haunted known-positive row eligibility"); checks++;
		var haunted = PartyObjectiveHud.hauntedValues("2", "3");
		check(haunted.length == 2 && haunted[0].icon() == HudPanel.HudIcon.INCENSE
			&& haunted[1].icon() == HudPanel.HudIcon.COIN && haunted[1].text().equals("3"), "Haunted item order"); checks++;
		check(PartyItemSync.feederSnapshotApplies(3, 2, 0, 2, 1), "final feed zero lost on next consume cycle"); checks++;
		check(PartyItemSync.feederSnapshotApplies(3, 3, 0, 3, 1), "same reporter cannot empty feeder"); checks++;
		check(!PartyItemSync.feederSnapshotApplies(2, 3, 1, 2, 2), "old cycle restored feed"); checks++;
		check(!PartyItemSync.feederSnapshotApplies(3, 3, 0, -1, 0), "unobserved empty peer erased current feed"); checks++;
		var alerts = new PartySparklingState();
		alerts.accept(new int[]{1,0,0,0}); alerts.discover(1);
		check(!alerts.claimLocalAlert(1), "previous remote find announced again locally"); checks++;
		check(alerts.claimLocalAlert(1), "second distinct local individual suppressed"); checks++;
		alerts.clear(); alerts.discover(1);
		check(alerts.claimLocalAlert(1), "first local find suppressed"); checks++;
		alerts.accept(new int[]{1,0,0,0});
		check(alerts.claimLocalAlert(1), "echo erased local discovery ownership"); checks++;
		check(!SparklingWatch.discoveryAllowed(dev.serko.safariutils.state.SafariEpoch.Phase.TRANSFERRING), "exit transition admits discoveries"); checks++;
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
        var room = new PartySparklingState();
        check(room.acceptRoom(new int[]{3,0,1,0}) && room.active() == 2,
            "late replay must apply catches before exposing discoveries"); checks++;
        check(room.reminder(true, false, 1000) == 2, "late guest reminder contains caught discovery"); checks++;
        room.acceptRoom(new int[]{1,0,0,0});
        check(room.active() == 2, "stale room state resurrected caught discovery"); checks++;
        check(!room.claimLocalAlert(2), "shared discovery repeated on local detection"); checks++;
        room.clear(); room.discover(1); room.acceptRoom(new int[]{1,0,0,0});
        check(room.claimLocalAlert(1), "own room echo stole discovery ownership"); checks++;
        room.clear(); room.acceptRoom(new int[]{1,0,1,0});
        check(room.active() == 0 && room.reminder(true,false,1000) == 0,
            "already caught pre-warp discovery alerted late guest"); checks++;
        room.clear(); check(room.caughtMask() == 0, "new visit inherited room catches"); checks++;
		return checks;
	}
	private static int[] parse(String value) { return Arrays.stream(value.split(",")).mapToInt(Integer::parseInt).toArray(); }
	private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
