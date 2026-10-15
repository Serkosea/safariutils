package dev.serko.safariutils.client;

import dev.serko.safariutils.state.SafariRuntimeState;
import dev.serko.safariutils.state.PartySnapshot;
import dev.serko.safariutils.state.PartyState;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import dev.serko.safariutils.state.SafariEpoch;

/**
 * Sends outgoing chat lines one at a time with a gap between them.
 *
 * <p>Hypixel rate-limits chat and will swallow — or mute for — a burst of messages
 * sent in the same tick. A four-biome report is four lines, so they are queued and
 * drained on a timer instead of being fired at once.
 */
public final class ChatQueue {

	private static final int MAX_PENDING = 128;
	/** Server-side chat limit; anything longer is rejected outright. */
	private static final int MAX_LENGTH = 250;

	record Queued(String line, long enqueuedAtNanos, long delayNanos, boolean verifiedParty,
		long connectionEpoch, long visitEpoch, long runEpoch, long partyGeneration) {
		boolean ready(long now) { return now - enqueuedAtNanos >= delayNanos; }
		boolean unavailable(PartySnapshot party) {
			return partyCommand(line) && !verifiedParty && party.status() == PartySnapshot.Status.SOLO;
		}
		boolean stale(SafariEpoch epoch, long party) {
			return connectionEpoch != epoch.connection()
				|| visitEpoch > 0L && visitEpoch != epoch.visit()
				|| runEpoch > 0L && runEpoch != epoch.run()
				|| partyGeneration >= 0L && partyGeneration != party;
		}
	}
	private static final Deque<Queued> pending = new ArrayDeque<>();

	private ChatQueue() {
	}

	/**
	 * Queues one line.
	 *
	 * @param command true to send it as a command (no leading slash), false for plain chat
	 */
	public static void enqueue(String line, boolean command) {
		enqueueDelayed(line, command, 0);
	}

	public static void enqueueDelayed(String line, boolean command, long delayMillis) {
		enqueueDelayed(line, command, delayMillis, false, 0L, 0L);
	}

	/** Queues a line owned by the active run, or by the pre-run Safari visit. */
	public static void enqueueSafariScoped(String line, boolean command) {
		enqueueSafariScopedDelayed(line, command, 0L);
	}

	/** Queues a delayed line owned by the active run, or by the pre-run Safari visit. */
	public static void enqueueSafariScopedDelayed(String line, boolean command, long delayMillis) {
		var epoch = SafariRuntimeState.current();
		enqueueDelayed(line, command, delayMillis, false, epoch.visit(), epoch.run());
	}

	/** Queues party chat after a feature independently verified a multi-player roster. */
	public static void enqueueVerifiedParty(String line) {
		enqueueDelayed(line, true, 0L, true, 0L, 0L);
	}

	/** Queues verified party chat owned by the current Safari run. */
	public static void enqueueRunScopedVerifiedParty(String line) {
		var epoch = SafariRuntimeState.current();
		enqueueDelayed(line, true, 0L, true, epoch.visit(), epoch.run());
	}

	/** Places an urgent verified party notice first and expires it with this Safari visit. */
	public static void enqueueVisitScopedVerifiedPartyFirst(String line) {
		long visit = SafariRuntimeState.current().visit();
		enqueue(line, true, 0L, true, visit, 0L, true);
	}

	private static void enqueueDelayed(String line, boolean command, long delayMillis,
			boolean verifiedParty, long visitEpoch, long runEpoch) {
		enqueue(line, command, delayMillis, verifiedParty, visitEpoch, runEpoch, false);
	}

	private static void enqueue(String line, boolean command, long delayMillis,
			boolean verifiedParty, long visitEpoch, long runEpoch, boolean urgent) {
		if (line == null || line.isBlank()) return;
		String trimmed = line.length() > MAX_LENGTH ? line.substring(0, MAX_LENGTH) : line;
		String queuedLine = (command ? "/" : "") + trimmed;
		PartySnapshot party = PartyState.snapshot();
		long partyGeneration = partyCommand(queuedLine) && party.inParty() ? party.generation() : -1L;
		Queued queued = new Queued(queuedLine, System.nanoTime(),
			TimeUnit.MILLISECONDS.toNanos(Math.max(0, delayMillis)), verifiedParty,
			SafariRuntimeState.current().connection(), visitEpoch, runEpoch, partyGeneration);
		if (pending.size() >= MAX_PENDING) {
			StructuredDiagnostics.decision("CHAT/QUEUE", "capacity", "urgent=" + urgent);
			if (!urgent) return;
			pending.removeLast();
		}
		if (urgent) pending.addFirst(queued);
		else pending.addLast(queued);
	}

	public static void clear() {
		pending.clear();
		PartyErrorSuppressor.clear();
	}

	/** Drains at most one queued line per call; wired to the client tick. */
	public static void tick() {
		if (!HypixelConnection.active()) {
			clear();
			return;
		}
		if (pending.isEmpty()) return;

		long now = System.nanoTime();
		var epoch = SafariRuntimeState.current();
		PartySnapshot party = PartyState.snapshot();
		int discarded = 0;
		while (!pending.isEmpty() && (pending.getFirst().stale(epoch, party.generation())
				|| pending.getFirst().unavailable(party))) {
			pending.pollFirst();
			discarded++;
		}
		if (discarded > 0) StructuredDiagnostics.decision("CHAT/QUEUE", "expired", "count=" + discarded);
		if (pending.isEmpty()) return;
		if (!pending.getFirst().ready(now) || CommandCoordinator.remaining(CommandCoordinator.Cooldown.CHAT) > 0) return;

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.player.connection == null) {
			pending.clear();
			return;
		}

		Queued queued = pending.getFirst();
		String line = queued.line();
		if (line.startsWith("/")) {
			boolean partyChat = partyCommand(line);
			if (partyChat) {
				if (party.status() == PartySnapshot.Status.SOLO) return;
				if (!queued.verifiedParty() && !party.inParty()) return;
			}
			CommandCoordinator.Scope scope = queued.runEpoch() > 0L
				? CommandCoordinator.Scope.RUN : queued.visitEpoch() > 0L
					? CommandCoordinator.Scope.VISIT : CommandCoordinator.Scope.CONNECTION;
			var request = new CommandCoordinator.Request(line.substring(1),
				partyChat ? CommandCoordinator.Category.PARTY_CHAT : CommandCoordinator.Category.ALERT_CHAT,
				CommandCoordinator.Cooldown.CHAT,
				scope, queued.connectionEpoch(), queued.visitEpoch(), queued.runEpoch(), queued.partyGeneration(),
				false, false);
			if (!CommandCoordinator.trySend(request)) return;
			if (partyChat) PartyErrorSuppressor.expectResponse();
		} else {
			if (!CommandCoordinator.trySendChat(line, CommandCoordinator.Category.ALERT_CHAT,
				CommandCoordinator.Scope.CONNECTION)) return;
		}
		pending.pollFirst();
	}

	static boolean partyCommand(String line) {
		return line.regionMatches(true, 0, "/pc ", 0, 4)
			|| line.regionMatches(true, 0, "/pchat ", 0, 7)
			|| line.regionMatches(true, 0, "/party chat ", 0, 12);
	}
}
