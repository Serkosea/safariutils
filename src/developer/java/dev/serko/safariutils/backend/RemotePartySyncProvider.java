package dev.serko.safariutils.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.serko.safariutils.api.PartySyncNetworkProvider;
import dev.serko.safariutils.api.PartySyncProtocol;
import dev.serko.safariutils.client.DebugLog;
import dev.serko.safariutils.client.NetworkRuntime;
import dev.serko.safariutils.client.OperationalLog;
import dev.serko.safariutils.client.StructuredDiagnostics;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Authenticated, reconnecting WebSocket transport used by Developer builds. */
public final class RemotePartySyncProvider implements PartySyncNetworkProvider {
	private static final URI ENDPOINT = BackendEndpoints.SYNC;
	private static final long BASE_RETRY_MILLIS = 2_000L;
	private static final long MAX_RETRY_MILLIS = 30_000L;
	private static final long PING_INTERVAL_MILLIS = 25_000L;
	private static final int MAX_INCOMING_MESSAGES = 128;
	private static final int MAX_RELAY_TEXT = 8_192;
	private static final Pattern NUMERIC_PAYLOAD = Pattern.compile("[\\d,-]*");
	private record Delivery(long generation, Message message) { }
	private final ArrayBlockingQueue<Delivery> incoming = new ArrayBlockingQueue<>(MAX_INCOMING_MESSAGES);
	private final AtomicLong generation = new AtomicLong();
	private volatile WebSocket socket;
	private volatile boolean connected;
	private volatile boolean connecting;
	private volatile long retryAt;
	private volatile int failures;
	private volatile long nextPingAt;
	private Context context;
	private String signature = "";

	@Override
	public boolean supported() {
		return true;
	}

	@Override
	public void tick(Context next, Consumer<Message> receiver) {
		String nextSignature = signature(next);
		if (!nextSignature.equals(signature)) {
			disconnect();
			context = next;
			signature = nextSignature;
			retryAt = 0L;
			failures = 0;
		}
		if (!valid(next)) return;
		Delivery delivery;
		while ((delivery = incoming.poll()) != null) {
			if (delivery.generation() == generation.get()) receiver.accept(delivery.message());
		}
		long now = System.currentTimeMillis();
		WebSocket current = socket;
		long operationGeneration = generation.get();
		if (current != null) {
			if (now >= nextPingAt) {
				nextPingAt = now + PING_INTERVAL_MILLIS;
				current.sendPing(ByteBuffer.wrap(new byte[] {0x53, 0x55}))
					.exceptionally(error -> { failedSend(operationGeneration, current, error); return null; });
			}
			return;
		}
		if (connecting || now < retryAt) return;
		connect(next, generation.incrementAndGet());
	}

	@Override
	public boolean connected() {
		return connected;
	}

	@Override
	public void send(long sequence, String type, String payload) {
		WebSocket current = socket;
		long operationGeneration = generation.get();
		if (!connected || current == null) return;
		JsonObject state = new JsonObject();
		state.addProperty("sequence", sequence);
		state.addProperty("kind", type);
		state.addProperty("payload", payload);
		current.sendText(state.toString(), true).exceptionally(error -> {
			failedSend(operationGeneration, current, error);
			return null;
		});
	}

	@Override
	public synchronized void disconnect() {
		generation.incrementAndGet();
		connected = false;
		connecting = false;
		incoming.clear();
		WebSocket current = socket;
		socket = null;
		if (current != null) {
			current.sendClose(WebSocket.NORMAL_CLOSURE, "leaving Safari");
			// Do not leave a transport thread waiting on a remote close acknowledgement.
			current.abort();
		}
	}

	@Override
	public void shutdown() {
		disconnect();
	}

	private void connect(Context desired, long attempt) {
		connecting = true;
		if (!NetworkRuntime.execute(() -> {
			try {
				String token = RemoteSession.instance().token();
				if (generation.get() != attempt) return;
				NetworkRuntime.authenticatedHttp().newWebSocketBuilder()
					.connectTimeout(Duration.ofSeconds(10))
					.header("Authorization", "Bearer " + token)
					.header("X-SafariUtils-Lobby", desired.lobbyId())
					.header("X-SafariUtils-Player", desired.localUuid())
					.header("X-SafariUtils-Roster", String.join(",", desired.roster()))
					.buildAsync(ENDPOINT, new Listener(attempt, desired))
					.exceptionally(error -> { failed(attempt, error); return null; });
			} catch (Exception error) {
				failed(attempt, error);
			}
		})) connecting = false;
	}

	private synchronized void opened(long attempt, WebSocket value) {
		if (generation.get() != attempt) {
			value.abort();
			return;
		}
		socket = value;
		connected = false;
		connecting = false;
		failures = 0;
		nextPingAt = System.currentTimeMillis() + PING_INTERVAL_MILLIS;
		DebugLog.line("SYNC_NET", "socket authenticated; awaiting room roster="
			+ (context == null ? 0 : context.roster().size()));
	}

	private synchronized void ready(long attempt, Map<String, String> next) {
		if (generation.get() != attempt || socket == null) return;
		connected = true;
		mappingReady(next.size());
	}

	private void mappingReady(int size) {
		DebugLog.line("SYNC_NET", "room ready mappedRoster=" + size);
		StructuredDiagnostics.decision("party-network", "connected", "roster=" + size);
	}

	private synchronized void closed(long attempt, int statusCode) {
		if (generation.get() != attempt) return;
		generation.incrementAndGet();
		incoming.clear();
		socket = null;
		connected = false;
		connecting = false;
		scheduleRetry();
		DebugLog.line("SYNC_NET", "closed status=" + statusCode + " retryMs="
			+ Math.max(0L, retryAt - System.currentTimeMillis()));
		StructuredDiagnostics.decision("party-network", "closed", "status=" + statusCode);
	}

	private synchronized void failedSend(long attempt, WebSocket origin, Throwable error) {
		if (socket == origin) failed(attempt, error);
	}

	private synchronized void failed(long attempt, Throwable error) {
		if (generation.get() != attempt) return;
		generation.incrementAndGet();
		incoming.clear();
		WebSocket current = socket;
		socket = null;
		if (current != null) current.abort();
		connected = false;
		connecting = false;
		scheduleRetry();
		OperationalLog.error("SYNC/NETWORK", unwrap(error));
		StructuredDiagnostics.decision("party-network", "retry",
			"attempt=" + failures);
	}

	private void scheduleRetry() {
		long delay = Math.min(MAX_RETRY_MILLIS, BASE_RETRY_MILLIS << Math.min(4, failures++));
		retryAt = System.currentTimeMillis() + delay;
	}

	private final class Listener implements WebSocket.Listener {
		private final long attempt;
		private final Context desired;
		private final StringBuilder text = new StringBuilder();
		private Map<String, String> mapping = Map.of();

		private Listener(long attempt, Context desired) {
			this.attempt = attempt;
			this.desired = desired;
		}

		@Override
		public void onOpen(WebSocket webSocket) {
			opened(attempt, webSocket);
			webSocket.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			if (text.length() + data.length() > MAX_RELAY_TEXT) {
				text.setLength(0);
				webSocket.abort();
				failed(attempt, new IllegalStateException("Oversized party sync relay message"));
				return null;
			}
			text.append(data);
			if (last) {
				receiveMessage(text.toString());
				text.setLength(0);
			}
			webSocket.request(1);
			return null;
		}

		private void receiveMessage(String value) {
			if (generation.get() != attempt) return;
			try {
				JsonObject root = JsonParser.parseString(value).getAsJsonObject();
				String type = root.get("type").getAsString();
				if ("welcome".equals(type)) {
					JsonArray tags = root.getAsJsonArray("roster");
					if (tags == null || tags.size() != desired.roster().size()) {
						rejectWelcome("size");
						return;
					}
					Map<String, String> next = new HashMap<>();
					for (int index = 0; index < tags.size(); index++) {
						String tag = tags.get(index).getAsString();
						if (tag.length() < 32 || next.put(tag, desired.roster().get(index)) != null) {
							rejectWelcome("tag");
							return;
						}
					}
					String self = root.get("self").getAsString();
					if (!desired.localUuid().equals(next.get(self))) {
						rejectWelcome("identity");
						return;
					}
					mapping = Map.copyOf(next);
					ready(attempt, mapping);
					return;
				}
				if (!"state".equals(type)) return;
				String uuid = mapping.get(root.get("sender").getAsString());
				if (uuid == null) return;
				long sequence = root.get("sequence").getAsLong();
				String kind = root.get("kind").getAsString();
				String payload = root.get("payload").getAsString();
				if (!validRelay(sequence, kind, payload)) {
					StructuredDiagnostics.decision("party-network", "rejected", "relayShape");
					return;
				}
				if (!incoming.offer(new Delivery(attempt, new Message(uuid, sequence, kind, payload)))) {
					StructuredDiagnostics.decision("party-network", "rejected", "queueFull");
					return;
				}
				DebugLog.line("SYNC_NET", "relay received type="
					+ kind);
			} catch (RuntimeException malformed) {
				DebugLog.line("SYNC_NET", "discarded malformed relay message");
				StructuredDiagnostics.decision("party-network", "rejected", "malformedRelay");
			}
		}

		private void rejectWelcome(String reason) {
			DebugLog.line("SYNC_NET", "discarded invalid room welcome reason=" + reason);
			StructuredDiagnostics.decision("party-network", "rejected", "welcome=" + reason);
			failed(attempt, new IllegalStateException("Invalid party sync room mapping"));
		}

		@Override
		public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
			webSocket.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			closed(attempt, statusCode);
			return null;
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			failed(attempt, error);
		}
	}

	private static boolean validRelay(long sequence, String kind, String payload) {
		if (sequence <= 0 || kind == null || payload == null
			|| payload.length() > PartySyncProtocol.MAX_PAYLOAD_LENGTH
			|| !NUMERIC_PAYLOAD.matcher(payload).matches()) return false;
		String[] values = payload.split(",", -1);
		if ("A".equals(kind)) return values.length == PartySyncProtocol.AGGREGATE_VALUES
			&& String.valueOf(PartySyncProtocol.VERSION).equals(
				values[PartySyncProtocol.AGGREGATE_VERSION_INDEX]);
		if ("U".equals(kind)) return values.length == PartySyncProtocol.USAGE_VALUES;
		return "N".equals(kind) && values.length == PartySyncProtocol.NEST_VALUES;
	}

	private static boolean valid(Context value) {
		return value != null && !value.lobbyId().isBlank() && !value.localUuid().isBlank()
			&& !value.roster().isEmpty() && value.roster().size() <= 4
			&& value.roster().contains(value.localUuid());
	}

	private static String signature(Context value) {
		return value == null ? "" : value.lobbyId() + "|" + value.localUuid() + "|"
			+ String.join(",", value.roster());
	}

	private static Throwable unwrap(Throwable error) {
		return error.getCause() == null ? error : error.getCause();
	}
}
