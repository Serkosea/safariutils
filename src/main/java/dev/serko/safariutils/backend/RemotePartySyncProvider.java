package dev.serko.safariutils.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.serko.safariutils.api.PartySyncNetworkProvider;
import dev.serko.safariutils.api.PartySyncProtocol;
import dev.serko.safariutils.client.DebugLog;
import dev.serko.safariutils.client.NetworkRuntime;
import dev.serko.safariutils.client.StructuredDiagnostics;

import java.net.URI;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import dev.serko.safariutils.state.MonotonicTime;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Authenticated, reconnecting WebSocket transport shared by every edition. */
public final class RemotePartySyncProvider implements PartySyncNetworkProvider {
	private static final URI ENDPOINT = BackendEndpoints.SYNC;
	private static final long BASE_RETRY_MILLIS = 2_000L;
	private static final long MAX_RETRY_MILLIS = 30_000L;
	private static final long PING_INTERVAL_MILLIS = 25_000L;
	private static final int MAX_INCOMING_MESSAGES = 128;
	private static final int MAX_RELAY_TEXT = 8_192;
	private static final Pattern NUMERIC_PAYLOAD = Pattern.compile("[\\d,-]*");
	private record Delivery(long generation, Message message, long queuedAt) {
        Delivery(long generation, Message message) { this(generation, message, MonotonicTime.nowMillis()); }
    }
    private volatile boolean runSync, completion, replayReady;
    private volatile String runId = "";
    @Override public boolean runSyncSupported() { return connected && runSync; }
    @Override public boolean completionSupported() { return connected && completion && replayReady; }
    @Override public boolean replayReady() { return connected && (!runSync || replayReady); }
    @Override public String runId() { return runId; }
	private final ArrayBlockingQueue<Delivery> incoming = new ArrayBlockingQueue<>(MAX_INCOMING_MESSAGES);
	private final AtomicLong generation = new AtomicLong();
	private volatile WebSocket socket;
	private volatile WebSocketOutbox outbox;
	private volatile boolean connected;
    private volatile boolean sparklingSnapshotReady;
    private volatile boolean snapshotExpected;
    @Override public boolean sparklingSnapshotReady() { return connected && sparklingSnapshotReady && (!runSync || replayReady); }
    private volatile java.util.Set<String> connectedPlayers = java.util.Set.of();
    @Override public java.util.Set<String> connectedPlayers() { return connected ? connectedPlayers : java.util.Set.of(); }
    private synchronized void presence(long attempt, java.util.Set<String> players) {
        if (generation.get() == attempt && connected) connectedPlayers = java.util.Set.copyOf(players);
    }
	private volatile boolean coordination;
	@Override public boolean coordinationSupported() { return connected && coordination; }
	private volatile boolean connecting;
	private volatile long retryAt;
	private volatile int failures;
	private volatile long nextPingAt;
    private volatile long openedAt, lastInboundAt, connectingAt;
    private volatile String attemptToken;
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
            boolean differentVisit = context == null || next == null || !java.util.Objects.equals(context.visitId(), next.visitId());
			disconnect();
            if (differentVisit) runId = "";
			context = next;
			signature = nextSignature;
			// Retain service backoff across roster changes; a new room cannot bypass a quota delay.
		}
		if (!valid(next)) return;
		Delivery delivery;
		while ((delivery = incoming.poll()) != null) {
			if (delivery.generation() == generation.get()) {
                Message message = delivery.message();
                if ("RECONCILE".equals(message.type())) {
                    var value=JsonParser.parseString(message.payload()).getAsJsonObject();
                    String status=value.get("status").getAsString();
                    replayReady=false;
                    if (!"pending".equals(status)) runId=value.get("runId").getAsString();
                    DebugLog.line("SYNC_NET", "run reconciliation="+status);
                    receiver.accept(message);
                } else if ("REPLAY_END".equals(message.type())) {
                    if (message.payload().equals(runId)) {
                        if (!replayReady) DebugLog.line("SYNC_NET", "run replay complete");
                        replayReady=true; sparklingSnapshotReady=true; failures=0;
                    }
                } else {
                    if ("C".equals(message.type())) message=new Message(message.senderUuid(),message.sequence(),message.type(),message.payload(),
                        message.ageMillis()+Math.max(0,MonotonicTime.nowMillis()-delivery.queuedAt()));
                    receiver.accept(message);
                }
                if (delivery.generation() == generation.get() && "S_ROOM".equals(delivery.message().type()))
                    { sparklingSnapshotReady = true; failures = 0; }
            }
		}
		long now = MonotonicTime.nowMillis();
		WebSocket current = socket;
		long operationGeneration = generation.get();
		if (current != null) {
            if ((!connected || snapshotExpected && !runSync && !sparklingSnapshotReady) && now - openedAt >= 12_000L
                    || connected && now - lastInboundAt >= 75_000L) {
                failed(operationGeneration, new java.io.IOException("Party sync response timeout"));
                return;
            }
			if (now >= nextPingAt) {
				nextPingAt = now + PING_INTERVAL_MILLIS;
				current.sendPing(ByteBuffer.wrap(new byte[] {0x53, 0x55}))
					.exceptionally(error -> { failedSend(operationGeneration, current, error); return null; });
			}
			return;
		}
		if (connecting && now - connectingAt >= 45_000L) {
            failed(operationGeneration, new java.io.IOException("Party sync connection timeout"));
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
		send(sequence, type, payload, false);
	}

	@Override
	public void sendSnapshot(long sequence, String payload, boolean catchUpOnly) {
		send(sequence, "A", payload, catchUpOnly);
	}

	private void send(long sequence, String type, String payload, boolean catchUpOnly) {
		WebSocket current = socket;
		long operationGeneration = generation.get();
		WebSocketOutbox queue = outbox;
		if (!connected || current == null || queue == null) return;
		JsonObject state = new JsonObject();
		state.addProperty("sequence", sequence);
		state.addProperty("kind", type);
        if (runSync) {
            if ("H".equals(type)) {
                JsonObject hello=JsonParser.parseString(payload).getAsJsonObject();
                hello.addProperty("visitId",context.visitId());
                if ("hello".equals(hello.get("action").getAsString())) {
                    JsonArray capabilities=new JsonArray();capabilities.add("run-reconciliation-v1");capabilities.add("critter-completion-v1");hello.add("capabilities",capabilities);
                    if(runId.isEmpty())hello.add("resumeRunId",com.google.gson.JsonNull.INSTANCE);else hello.addProperty("resumeRunId",runId);
                } else { if(runId.isEmpty())hello.add("runId",com.google.gson.JsonNull.INSTANCE);else hello.addProperty("runId",runId); }
                payload=hello.toString();
            } else {
                if(!replayReady || runId.isEmpty())return;
                if("C".equals(type)) { var value=JsonParser.parseString(payload).getAsJsonObject();value.addProperty("runId",runId);value.addProperty("visitId",context.visitId());payload=value.toString(); }
            }
        }
		state.addProperty("payload", payload);
		if (catchUpOnly) state.addProperty("catchUpOnly", true);
		queue.send(state.toString()).exceptionally(error -> {
			failedSend(operationGeneration, current, error);
			return null;
		});
	}

	@Override
	public synchronized void disconnect() {
		generation.incrementAndGet();
		connected = false; sparklingSnapshotReady = false; coordination = false; runSync=false; completion=false; replayReady=false; connectedPlayers = java.util.Set.of();
		connecting = false;
		incoming.clear();
		WebSocket current = socket;
		socket = null;
        WebSocketOutbox queue = outbox; outbox = null;
        if (queue != null) queue.close();
        else if (current != null) current.abort();
	}

	@Override
	public void shutdown() {
		disconnect();
	}

	private void connect(Context desired, long attempt) {
		connecting = true;
        connectingAt = MonotonicTime.nowMillis();
		if (!NetworkRuntime.execute(() -> {
			try {
				String token = RemoteSession.instance().token();
				if (!rememberToken(attempt, token)) return;
				NetworkRuntime.authenticatedHttp().newWebSocketBuilder()
					.connectTimeout(Duration.ofSeconds(10))
					.header("Authorization", "Bearer " + token)
					.header("X-SafariUtils-Lobby", desired.lobbyId())
					.header("X-SafariUtils-Sync-Protocol", "2")
                    .header("X-SafariUtils-Player", desired.localUuid())
					.header("X-SafariUtils-Roster", String.join(",", desired.roster()))
					.buildAsync(ENDPOINT, new Listener(attempt, desired))
					.exceptionally(error -> { failed(attempt, error); return null; });
			} catch (Exception error) {
				failed(attempt, error);
			}
		})) { connecting = false; scheduleRetry(); }
	}

	private synchronized void opened(long attempt, WebSocket value) {
		if (generation.get() != attempt) {
			value.abort();
			return;
		}
        outbox = new WebSocketOutbox(value);
		connected = false; sparklingSnapshotReady = false; coordination = false; runSync=false; completion=false; replayReady=false; connectedPlayers = java.util.Set.of();
		connecting = false;
		openedAt = lastInboundAt = MonotonicTime.nowMillis();
		nextPingAt = MonotonicTime.nowMillis() + PING_INTERVAL_MILLIS;
        socket = value; // Publish only after watchdog timestamps and queue are initialized.
		DebugLog.line("SYNC_NET", "socket authenticated; awaiting room roster="
			+ (context == null ? 0 : context.roster().size()));
	}

	private synchronized void ready(long attempt, Map<String, String> next, boolean expectsSnapshot) {
		if (generation.get() != attempt || socket == null) return;
		connected = true;
        snapshotExpected = expectsSnapshot;
        if (!expectsSnapshot) failures = 0;
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
        WebSocketOutbox queue = outbox; outbox = null;
        if (queue != null) queue.close();
		socket = null;
		connected = false; sparklingSnapshotReady = false; coordination = false; runSync=false; completion=false; replayReady=false; connectedPlayers = java.util.Set.of();
		connecting = false;
		scheduleRetry();
		DebugLog.line("SYNC_NET", "closed status=" + statusCode + " retryMs="
			+ Math.max(0L, retryAt - MonotonicTime.nowMillis()));
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
        WebSocketOutbox queue = outbox; outbox = null;
        if (queue != null) queue.close();
		if (current != null) current.abort();
		connected = false; sparklingSnapshotReady = false; coordination = false; runSync=false; completion=false; replayReady=false; connectedPlayers = java.util.Set.of();
		connecting = false;
        Throwable cause = unwrap(error);
        long serverDelay = 0;
        int status = 0;
        if (cause instanceof WebSocketHandshakeException handshake) {
            status = handshake.getResponse().statusCode();
            serverDelay = RemoteSession.retryAfterMillis(handshake.getResponse().headers().firstValue("Retry-After").orElse(""));
        } else if (cause instanceof dev.serko.safariutils.api.ServiceRequestException service) {
            status = service.status(); serverDelay = Math.min(86_400L, service.retrySeconds()) * 1000L;
        }
        if (status == 401 && attemptToken != null) RemoteSession.instance().invalidate(attemptToken);
        attemptToken = null;
        scheduleRetry();
        retryAt = Math.max(retryAt, MonotonicTime.nowMillis() + serverDelay);
        // HTTP status and bounded timing are enough; never log handshake response headers.
        DebugLog.line("SYNC_NET", "retry status=" + status + " delayMs=" + Math.max(0, retryAt - MonotonicTime.nowMillis()));
		StructuredDiagnostics.decision("party-network", "retry",
			"attempt=" + failures);
	}

	private void scheduleRetry() {
		long delay = Math.min(MAX_RETRY_MILLIS, BASE_RETRY_MILLIS << Math.min(4, failures));
		failures = Math.min(5, failures + 1);
		retryAt = MonotonicTime.nowMillis() + delay;
	}

	private final class Listener implements WebSocket.Listener {
		private final long attempt;
		private final Context desired;
		private final StringBuilder text = new StringBuilder();
		private Map<String, String> mapping = Map.of();
		private boolean roomSparklings;
        private String connectionId="", reconciledRun="";

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
            inbound(attempt);
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
					coordination = root.has("features") && root.getAsJsonArray("features").asList().stream()
                        .anyMatch(feature -> "hotspots-v1".equals(feature.getAsString()));
                    roomSparklings = root.has("features") && root.getAsJsonArray("features").asList().stream()
                        .anyMatch(feature -> "room-sparklings-v1".equals(feature.getAsString()));
                    runSync = root.has("features") && root.getAsJsonArray("features").asList().stream().anyMatch(f -> "run-reconciliation-v1".equals(f.getAsString()));
                    completion = runSync && root.getAsJsonArray("features").asList().stream().anyMatch(f -> "critter-completion-v1".equals(f.getAsString()));
                    if(runSync)connectionId=root.get("connectionId").getAsString();
                    mapping = Map.copyOf(next);
					ready(attempt, mapping, roomSparklings);
					return;
				}
                if (runSync && ("reconcile".equals(type) || "replay-complete".equals(type))) {
                    if (!connectionId.equals(root.get("connectionId").getAsString()) || !desired.visitId().equals(root.get("visitId").getAsString())) return;
                    if("reconcile".equals(type)) {
                        String status=root.get("status").getAsString();
                        if(!java.util.Set.of("pending","resumed","joined","new").contains(status))return;
                        reconciledRun="pending".equals(status)?"":root.get("runId").getAsString();
                        enqueue(new Message("",1,"RECONCILE",root.toString()));
                    } else if(!reconciledRun.isEmpty() && reconciledRun.equals(root.get("runId").getAsString())) enqueue(new Message("",1,"REPLAY_END",reconciledRun));
                    return;
                }
                if(runSync && java.util.Set.of("sparklings","state","hotspots").contains(type)) {
                    String frameRun="hotspots".equals(type) ? root.getAsJsonObject("payload").get("run").getAsString()
                        : root.has("runId")?root.get("runId").getAsString():"";
                    if(reconciledRun.isEmpty() || !reconciledRun.equals(frameRun))return;
                }
                if ("sparklings".equals(type) && roomSparklings && !mapping.isEmpty()) {
                    String payload = root.get("payload").getAsString();
                    if (validRelay(1, "S", payload)
                        && !incoming.offer(new Delivery(attempt, new Message("", 1, "S_ROOM", payload))))
                        failed(attempt,new java.io.IOException("Party replay queue overflow"));
                    return;
                }
                if ("presence".equals(type)) {
                    JsonArray members = root.getAsJsonArray("members");
                    if (members == null || members.size() > desired.roster().size()) return;
                    var players = new java.util.HashSet<String>();
                    for (var member : members) {
                        String id = mapping.get(member.getAsString());
                        if (id == null || !players.add(id)) return;
                    }
                    presence(attempt, players);
                    return;
                }
				if ("hotspots-unavailable".equals(type)) {
                    if (coordination) DebugLog.line("SYNC_NET", "coordination temporarily unavailable; preserving run reconciliation");
                    coordination = false; return;
                }
                if ("hotspots".equals(type) && (coordination || runSync)) {
                    coordination = true;
                    JsonObject payload = root.getAsJsonObject("payload");
                    if (payload != null && payload.toString().length() <= 4096)
                        enqueue(new Message("", 1, "H", payload.toString()));
                    return;
                }
                if (!"state".equals(type)) return;
				String uuid = mapping.get(root.get("sender").getAsString());
				if (uuid == null) return;
				long sequence = root.get("sequence").getAsLong();
				String kind = root.get("kind").getAsString();
				if (roomSparklings && "S".equals(kind)) return;
				String payload = root.get("payload").getAsString();
                if ("C".equals(kind)) {
                    if(!completion || sequence<=0 || !root.has("ageMillis"))return;
                    long age=root.get("ageMillis").getAsLong();
                    if(age<0 || age>=45000 || dev.serko.safariutils.state.CompletionSnapshot.parse(payload,reconciledRun)==null)return;
                    enqueue(new Message(uuid,sequence,kind,payload,age)); return;
                }
				if (!validRelay(sequence, kind, payload)) {
					StructuredDiagnostics.decision("party-network", "rejected", "relayShape");
					return;
				}
				if (!incoming.offer(new Delivery(attempt, new Message(uuid, sequence, "A".equals(kind) && root.has("inventoryFresh") && !root.get("inventoryFresh").getAsBoolean()?"A_RETAINED":kind, payload)))) {
                    failed(attempt,new java.io.IOException("Party replay queue overflow"));
					return;
				}
				DebugLog.line("SYNC_NET", "relay received type="
					+ kind);
			} catch (RuntimeException malformed) {
				DebugLog.line("SYNC_NET", "discarded malformed relay message");
				StructuredDiagnostics.decision("party-network", "rejected", "malformedRelay");
			}
		}

        private void enqueue(Message message) {
            if(!incoming.offer(new Delivery(attempt,message))) failed(attempt,new java.io.IOException("Party replay queue overflow"));
        }
		private void rejectWelcome(String reason) {
			DebugLog.line("SYNC_NET", "discarded invalid room welcome reason=" + reason);
			StructuredDiagnostics.decision("party-network", "rejected", "welcome=" + reason);
			failed(attempt, new IllegalStateException("Invalid party sync room mapping"));
		}

		@Override
		public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            inbound(attempt);
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
		if (sequence > 0 && "B".equals(kind) && payload != null && payload.matches("[fcih]d")) return true;
		if (sequence <= 0 || kind == null || payload == null
			|| payload.length() > PartySyncProtocol.MAX_PAYLOAD_LENGTH
			|| !NUMERIC_PAYLOAD.matcher(payload).matches()) return false;
		String[] values = payload.split(",", -1);
		if ("A".equals(kind)) return values.length > PartySyncProtocol.AGGREGATE_VERSION_INDEX
			&& ((values.length == PartySyncProtocol.AGGREGATE_VALUES
				&& String.valueOf(PartySyncProtocol.VERSION).equals(values[PartySyncProtocol.AGGREGATE_VERSION_INDEX]))
				|| (values.length == PartySyncProtocol.LEGACY_AGGREGATE_VALUES
					&& "2".equals(values[PartySyncProtocol.AGGREGATE_VERSION_INDEX])));
		if ("U".equals(kind)) return values.length == PartySyncProtocol.USAGE_VALUES;
		if ("S".equals(kind)) return values.length == PartySyncProtocol.SPARKLING_VALUES;
		return "N".equals(kind) && values.length == PartySyncProtocol.NEST_VALUES;
	}

	private static boolean valid(Context value) {
		return value != null && !value.lobbyId().isBlank() && !value.localUuid().isBlank()
			&& !value.roster().isEmpty() && value.roster().size() <= 4
			&& value.roster().contains(value.localUuid());
	}

	private static String signature(Context value) {
		return value == null ? "" : value.lobbyId() + "|" + value.visitId() + "|" + value.localUuid() + "|"
			+ String.join(",", value.roster());
	}

    private synchronized boolean rememberToken(long attempt, String token) {
        if (generation.get() != attempt) return false;
        attemptToken = token;
        return true;
    }

    private synchronized void inbound(long attempt) {
        if (generation.get() == attempt) lastInboundAt = MonotonicTime.nowMillis();
    }

	private static Throwable unwrap(Throwable error) {
        while ((error instanceof java.util.concurrent.CompletionException || error instanceof java.util.concurrent.ExecutionException)
                && error.getCause() != null) error = error.getCause();
        return error;
	}
}
