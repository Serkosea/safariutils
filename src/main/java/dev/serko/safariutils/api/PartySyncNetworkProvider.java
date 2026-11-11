package dev.serko.safariutils.api;

import java.util.List;
import java.util.function.Consumer;

/** Optional authenticated transport for party objective synchronization. */
public interface PartySyncNetworkProvider {
	record Context(String lobbyId, String localUuid, List<String> roster, String visitId) {
        public Context(String lobbyId, String localUuid, List<String> roster) { this(lobbyId, localUuid, roster, ""); }
		public Context {
			lobbyId = lobbyId == null ? "" : lobbyId;
			localUuid = localUuid == null ? "" : localUuid;
			roster = roster == null ? List.of() : List.copyOf(roster);
		}
	}

	record Message(String senderUuid, long sequence, String type, String payload, long ageMillis) {
        public Message(String senderUuid, long sequence, String type, String payload) { this(senderUuid, sequence, type, payload, 0); }
    }
    default boolean runSyncSupported() { return false; }
    default boolean replayReady() { return connected(); }
    default boolean completionSupported() { return false; }
    default String runId() { return ""; }

	default boolean supported() {
		return false;
	}

	default void tick(Context context, Consumer<Message> receiver) { }

	default boolean coordinationSupported() { return false; }

	/** Server-confirmed sockets in this party room, including this client. */
	default java.util.Set<String> connectedPlayers() { return java.util.Set.of(); }

	/** Initial atomic Sparkling snapshot has been applied on the client thread. */
	default boolean sparklingSnapshotReady() { return false; }

	default boolean connected() {
		return false;
	}

	default void send(long sequence, String type, String payload) { }

	/** Store chat-derived state for catch-up without repeating it to current peers. */
	default void sendSnapshot(long sequence, String payload, boolean catchUpOnly) {
		send(sequence, "A", payload);
	}

	default void disconnect() { }

	default void shutdown() {
		disconnect();
	}
}
