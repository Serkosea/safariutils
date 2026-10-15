package dev.serko.safariutils.api;

import java.util.List;
import java.util.function.Consumer;

/** Optional authenticated transport for party objective synchronization. */
public interface PartySyncNetworkProvider {
	record Context(String lobbyId, String localUuid, List<String> roster) {
		public Context {
			lobbyId = lobbyId == null ? "" : lobbyId;
			localUuid = localUuid == null ? "" : localUuid;
			roster = roster == null ? List.of() : List.copyOf(roster);
		}
	}

	record Message(String senderUuid, long sequence, String type, String payload) { }

	default boolean supported() {
		return false;
	}

	default void tick(Context context, Consumer<Message> receiver) { }

	default boolean connected() {
		return false;
	}

	default void send(long sequence, String type, String payload) { }

	default void disconnect() { }

	default void shutdown() {
		disconnect();
	}
}
