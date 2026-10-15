package dev.serko.safariutils.client;

import com.google.gson.JsonObject;
import dev.serko.safariutils.BuildVersion;
import dev.serko.safariutils.SafariUtils;
import dev.serko.safariutils.state.SafariEpoch;
import dev.serko.safariutils.state.SafariRuntimeState;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.zip.GZIPOutputStream;

/**
 * Asynchronous compact diagnostics. Gameplay threads only enqueue records; JSON
 * serialization, compression, repetition summaries, rotation, and disk I/O happen on
 * one daemon thread. One gzip stream replaces the former duplicate human and structured
 * logs while retaining every field needed for deterministic replay.
 */
public final class OperationalLog {
	private static final long MAX_BYTES = 4L * 1_024L * 1_024L;
	private static final int MAX_QUEUED_LINES = 2_048;
	private static final int MAX_MESSAGE_CHARACTERS = 4_000;
	private static final int MAX_ERROR_CHARACTERS = 24_000;
	private static final long ERROR_REPEAT_MILLIS = 5_000L;
	private static final long DEBUG_REPEAT_MILLIS = 2_000L;
	private static final DiagnosticQueue<Queued> QUEUE = new DiagnosticQueue<>(MAX_QUEUED_LINES);
	private static final Map<String, Long> LAST_ERROR = new ConcurrentHashMap<>();
	private static final Map<String, Long> LAST_SLOW_OPERATION = new ConcurrentHashMap<>();
	private static final long SLOW_OPERATION_NANOS = 5_000_000L;
	private static final long SLOW_OPERATION_INTERVAL_NANOS = 10_000_000_000L;
	private static final DiagnosticRepeats REPEATS = new DiagnosticRepeats(512, DEBUG_REPEAT_MILLIS);
	private static final ScheduledExecutorService WRITER =
		Executors.newSingleThreadScheduledExecutor(task -> {
			Thread thread = new Thread(task, "safariutils-log");
			thread.setDaemon(true);
			return thread;
		});
	private static volatile BufferedWriter writer;
	private static volatile Path activePath;
	private static final List<Path> STRUCTURED_PATHS = new ArrayList<>();
	private static String sessionName;
	private static int sessionPart;
	private static volatile boolean unavailable;
	private static boolean scheduled;
	private static volatile boolean stopping;
	private static boolean shutdownHookRegistered;

	private record Entry(long time, String level, String category, String message,
			long connection, long visit, long run, String phase) { }
	private record Queued(Entry entry, String structured, Supplier<String> deferred) { }

	private OperationalLog() { }

	public static synchronized void start() {
		if (disabled() || writer != null || unavailable) return;
		try {
			if (!shutdownHookRegistered) {
				shutdownHookRegistered = true;
				Runtime.getRuntime().addShutdownHook(new Thread(OperationalLog::shutdown,
					"safariutils-log-close"));
			}
			sessionName = "SafariUtils_" + fileTime(Instant.now());
			sessionPart = 1;
			Path path = nextAvailablePath();
			activePath = path;
			Files.createDirectories(path.getParent());
			writer = openWriter(path);
			STRUCTURED_PATHS.clear();
			STRUCTURED_PATHS.add(activePath);
			info("LIFECYCLE", "SafariUtils initialized build="
				+ (BuildVersion.DEVELOPER ? "developer"
				: BuildVersion.SAFE ? "safe" : "extra")
				+ " version=" + BuildVersion.releaseVersion()
				+ " source=" + BuildVersion.sourceFingerprint());
			flush();
			if (!scheduled) {
				scheduled = true;
				WRITER.scheduleAtFixedRate(OperationalLog::flush, 1, 1, TimeUnit.SECONDS);
			}
		} catch (Exception error) {
			unavailable = true;
			try { if (writer != null) writer.close(); } catch (Exception ignored) { }
			writer = null;
			SafariUtils.LOGGER.warn("Could not open SafariUtils log", error);
		}
	}

	public static void info(String category, String message) {
		if (disabled()) return;
		enqueue("INFO", category, message);
	}

	/** Queues one already-serialized JSON object for deterministic replay. */
	public static void structured(String jsonLine) {
		if (disabled() || unavailable || jsonLine == null || jsonLine.isBlank()) return;
		if (writer == null) start();
		if (writer == null) return;
		QUEUE.offer(new Queued(null, jsonLine, null));
	}

	/** The supplier must capture immutable data only; it runs on the bounded log writer. */
	static void structuredDeferred(Supplier<String> encoder) {
		if (disabled() || unavailable || encoder == null) return;
		if (writer == null) start();
		if (writer != null) QUEUE.offer(new Queued(null, null, encoder));
	}

	public static Path activeStructuredPath() {
		return activePath;
	}

	public static synchronized List<Path> structuredSessionPaths() {
		return List.copyOf(STRUCTURED_PATHS);
	}

	/** Worker-only: closes the gzip trailer before replay and continues in a new segment. */
	static synchronized List<Path> sealForReplay() throws java.io.IOException {
		if (disabled()) return List.of();
		flush();
		if (unavailable || writer == null) throw new java.io.IOException("Diagnostic writer unavailable");
		List<Path> sealed = List.copyOf(STRUCTURED_PATHS);
		try {
			rotate();
			return sealed;
		} catch (Exception failure) {
			unavailable = true;
			try { if (writer != null) writer.close(); } catch (Exception ignored) { }
			writer = null;
			throw new java.io.IOException("Could not seal diagnostic replay", failure);
		}
	}

	/** Records high-volume diagnostics with short-window duplicate coalescing. */
	public static void debug(String category, String message) {
		if (disabled() || unavailable || message == null) return;
		long now = System.currentTimeMillis();
		if (message.length() > MAX_MESSAGE_CHARACTERS) message = message.substring(0, MAX_MESSAGE_CHARACTERS);
		DiagnosticRepeats.Decision decision = REPEATS.accept(category, message, now);
		writeRepeatSummaries(decision.summaries(), now);
		if (decision.emit()) enqueue(entry(now, "DEBUG", category, message));
	}

	public static void error(String category, Throwable error) {
		if (error == null) return;
		String message = error.getMessage();
		if (message != null && message.length() > MAX_MESSAGE_CHARACTERS)
			message = message.substring(0, MAX_MESSAGE_CHARACTERS);
		String signature = category + '|' + error.getClass().getName() + '|' + message;
		long monotonicNow = System.nanoTime();
		Long previous = LAST_ERROR.put(signature, monotonicNow);
		if (previous != null && monotonicNow - previous >= 0
				&& monotonicNow - previous < TimeUnit.MILLISECONDS.toNanos(ERROR_REPEAT_MILLIS)) return;
		if (LAST_ERROR.size() > 128) LAST_ERROR.clear();
		SafariUtils.LOGGER.error("SafariUtils {} failure", category, error);
		// Release variants also coalesce repeated provider/render faults, but only
		// Developer builds serialize their traces to SafariUtils diagnostics.
		if (disabled()) return;
		StringWriter trace = new StringWriter(1024);
		error.printStackTrace(new PrintWriter(trace));
		String detail = trace.toString();
		if (detail.length() > MAX_ERROR_CHARACTERS) {
			detail = detail.substring(0, MAX_ERROR_CHARACTERS) + "\n... stack trace truncated";
		}
		enqueue(entry(System.currentTimeMillis(), "ERROR", category, detail));
	}

	public static void run(String category, Runnable action) {
		boolean measure = DebugLog.isEnabled();
		long started = measure ? System.nanoTime() : 0L;
		try { action.run(); }
		catch (RuntimeException | LinkageError error) { error(category, error); }
		finally {
			if (measure) {
				long ended = System.nanoTime(), elapsed = ended - started;
				if (elapsed >= SLOW_OPERATION_NANOS) {
					Long previous = LAST_SLOW_OPERATION.get(category);
					if (previous == null || ended - previous >= SLOW_OPERATION_INTERVAL_NANOS) {
						if (LAST_SLOW_OPERATION.size() >= 128) LAST_SLOW_OPERATION.clear();
						LAST_SLOW_OPERATION.put(category, ended);
						debug("PERF", "operation=" + category + " micros=" + elapsed / 1_000L
							+ " biome=" + SafariLocation.biome());
					}
				}
			}
		}
	}

	public static <T> T get(String category, Supplier<T> action, T fallback) {
		try { return action.get(); }
		catch (RuntimeException | LinkageError error) {
			error(category, error);
			return fallback;
		}
	}

	/** Wraps a registered HUD once without allocating a per-frame callback. */
	public static HudElement hud(String name, HudElement delegate) {
		String category = "RENDER/" + name;
		return (graphics, delta) -> {
			if (!HypixelConnection.active()) return;
			try { delegate.extractRenderState(graphics, delta); }
			catch (RuntimeException | LinkageError error) { error(category, error); }
		};
	}

	/** Wraps the one HUD explicitly allowed outside Hypixel. */
	public static HudElement hudEverywhere(String name, HudElement delegate) {
		String category = "RENDER/" + name;
		return (graphics, delta) -> {
			try { delegate.extractRenderState(graphics, delta); }
			catch (RuntimeException | LinkageError error) { error(category, error); }
		};
	}

	public static String status() {
		Path path = activePath != null ? activePath : SafariPaths.logs();
		long bytes = 0L;
		try { if (Files.isRegularFile(path)) bytes = Files.size(path); }
		catch (Exception ignored) { }
		return "Automatic diagnostics\n  compact events " + path.toAbsolutePath()
			+ "\n  available " + !unavailable + " · queued " + QUEUE.size()
			+ " · dropped " + QUEUE.dropped() + " · bytes " + bytes + '\n';
	}

	public static synchronized void flush() {
		if (disabled()) {
			QUEUE.clear();
			REPEATS.clear();
			return;
		}
		// A persisted disabled setting can be switched off while the client is open.
		// Start lazily on the first flush/log event instead of requiring a restart.
		if (writer == null && !unavailable) start();
		if (writer == null) return;
		try {
			long now = System.currentTimeMillis();
			expireRepeats(now);
			long dropped = QUEUE.takeDropped();
			if (dropped > 0) write(entry(now, "WARN", "LOG", "dropped=" + dropped));
			for (int count = 0; count < MAX_QUEUED_LINES; count++) {
				Queued queued = QUEUE.poll();
				if (queued == null) break;
				if (queued.entry() != null) write(queued.entry());
				else {
					writer.write(queued.deferred() == null ? queued.structured() : queued.deferred().get());
					writer.newLine();
				}
			}
			writer.flush();
			Path path = activePath;
			if (Files.isRegularFile(path) && Files.size(path) >= MAX_BYTES) {
				rotate();
			}
		} catch (Exception error) {
			unavailable = true;
			try { writer.close(); } catch (Exception ignored) { }
			writer = null;
			SafariUtils.LOGGER.warn("Could not write SafariUtils log", error);
		}
	}

	/** Called only under the writer monitor; publishes the new path after opening succeeds. */
	private static void rotate() throws Exception {
		writer.close();
		writer = null;
		sessionPart++;
		Path next = nextAvailablePath();
		BufferedWriter replacement = openWriter(next);
		activePath = next;
		writer = replacement;
		STRUCTURED_PATHS.add(next);
	}

	public static synchronized void shutdown() {
		if (stopping) return;
		info("LIFECYCLE", "SafariUtils stopping");
		writeRepeatSummaries(REPEATS.drain(), System.currentTimeMillis());
		QUEUE.close();
		flush();
		stopping = true;
		WRITER.shutdown();
		if (writer == null) return;
		try { writer.close(); } catch (Exception ignored) { }
		writer = null;
	}

	private static void enqueue(String level, String category, String message) {
		if (message != null) enqueue(entry(System.currentTimeMillis(), level, category, message));
	}

	private static void enqueue(Entry entry) {
		if (disabled() || unavailable || entry == null) return;
		if (writer == null) start();
		if (writer == null) return;
		QUEUE.offer(new Queued(entry, null, null));
	}

	private static boolean disabled() {
		return stopping || !BuildVersion.has(BuildVersion.Capability.DEVELOPER_TOOLS)
			|| ConfigManager.automaticLoggingDisabled();
	}

	private static void expireRepeats(long now) {
		writeRepeatSummaries(REPEATS.expire(now), now);
	}

	private static void writeRepeatSummaries(List<DiagnosticRepeats.Summary> summaries, long now) {
		for (var summary : summaries) enqueue(entry(now, "DEBUG", summary.category(),
			"repeat=" + summary.count() + " previous=" + summary.message()));
	}

	private static void write(Entry entry) throws Exception {
		String message = entry.message().replace("\r\n", "\n").replace('\r', '\n');
		if (!"ERROR".equals(entry.level()) && message.length() > MAX_MESSAGE_CHARACTERS) {
			message = message.substring(0, MAX_MESSAGE_CHARACTERS) + "...";
		}
		JsonObject json = new JsonObject();
		json.addProperty("k", "l");
		json.addProperty("t", entry.time());
		json.addProperty("l", entry.level().charAt(0));
		json.addProperty("g", entry.category());
		json.addProperty("m", message);
		json.addProperty("c", entry.connection());
		json.addProperty("v", entry.visit());
		json.addProperty("r", entry.run());
		json.addProperty("p", entry.phase());
		writer.write(json.toString());
		writer.newLine();
	}

	private static Entry entry(long time, String level, String category, String message) {
		SafariEpoch epoch = SafariRuntimeState.current();
		return new Entry(time, level, category, message, epoch.connection(), epoch.visit(),
			epoch.run(), epoch.phase().name());
	}

	private static BufferedWriter openWriter(Path path) throws Exception {
		return new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(
			Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), true),
			StandardCharsets.UTF_8));
	}

	private static String fileTime(Instant instant) {
		ZonedDateTime time = instant.atZone(ZoneId.systemDefault());
		String month = time.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
		return "%s-%d%s-%d_%02dH-%02dM".formatted(month, time.getDayOfMonth(),
			ordinalSuffix(time.getDayOfMonth()), time.getYear(), time.getHour(), time.getMinute());
	}

	private static String ordinalSuffix(int day) {
		if (day >= 11 && day <= 13) return "th";
		return switch (day % 10) {
			case 1 -> "st";
			case 2 -> "nd";
			case 3 -> "rd";
			default -> "th";
		};
	}

	private static Path nextAvailablePath() {
		Path path = SafariPaths.structuredLog(sessionName, sessionPart);
		while (Files.exists(path)) {
			sessionPart++;
			path = SafariPaths.structuredLog(sessionName, sessionPart);
		}
		return path;
	}
}
