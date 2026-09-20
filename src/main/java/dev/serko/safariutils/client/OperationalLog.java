package dev.serko.safariutils.client;

import dev.serko.safariutils.SafariUtils;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Small always-on rolling log for actionable SafariUtils failures and lifecycle events.
 * Writes are queued and flushed once per second so ordinary gameplay never performs
 * disk I/O for each event. Verbose opt-in diagnostics remain in {@link DebugLog}.
 */
public final class OperationalLog {
	private static final long MAX_BYTES = 1_048_576;
	private static final int MAX_QUEUED_LINES = 512;
	private static final int MAX_ERROR_CHARACTERS = 24_000;
	private static final long REPEAT_WINDOW_MILLIS = 5_000;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
	private static final ConcurrentLinkedQueue<String> QUEUE = new ConcurrentLinkedQueue<>();
	private static final AtomicInteger QUEUED = new AtomicInteger();
	private static final Map<String, Long> LAST_ERROR = new ConcurrentHashMap<>();
	private static final ScheduledExecutorService WRITER =
		Executors.newSingleThreadScheduledExecutor(task -> {
			Thread thread = new Thread(task, "safariutils-operational-log");
			thread.setDaemon(true);
			return thread;
		});
	private static BufferedWriter writer;
	private static boolean unavailable;
	private static boolean scheduled;

	private OperationalLog() { }

	public static synchronized void start() {
		if (writer != null || unavailable) return;
		try {
			var path = SafariPaths.operationalLog();
			Files.createDirectories(path.getParent());
			if (Files.isRegularFile(path) && Files.size(path) >= MAX_BYTES) {
				Files.move(path, path.resolveSibling("safariutils.previous.log"),
					StandardCopyOption.REPLACE_EXISTING);
			}
			writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			info("LIFECYCLE", "SafariUtils initialized");
			flush();
			if (!scheduled) {
				scheduled = true;
				WRITER.scheduleAtFixedRate(OperationalLog::flush, 1, 1, TimeUnit.SECONDS);
			}
		} catch (Exception error) {
			unavailable = true;
			SafariUtils.LOGGER.warn("Could not open SafariUtils operational log", error);
		}
	}

	public static void info(String category, String message) {
		enqueue("INFO", category, message);
	}

	public static void error(String category, Throwable error) {
		if (error == null) return;
		String signature = category + '|' + error.getClass().getName() + '|' + error.getMessage();
		long now = System.currentTimeMillis();
		Long previous = LAST_ERROR.put(signature, now);
		if (previous != null && now - previous < REPEAT_WINDOW_MILLIS) return;
		if (LAST_ERROR.size() > 128) LAST_ERROR.clear();
		SafariUtils.LOGGER.error("SafariUtils {} failure", category, error);
		StringWriter trace = new StringWriter(1024);
		error.printStackTrace(new PrintWriter(trace));
		String detail = trace.toString();
		if (detail.length() > MAX_ERROR_CHARACTERS) {
			detail = detail.substring(0, MAX_ERROR_CHARACTERS) + "\n... stack trace truncated";
		}
		enqueue("ERROR", category, detail);
	}

	public static void run(String category, Runnable action) {
		try {
			action.run();
		} catch (RuntimeException | LinkageError error) {
			error(category, error);
		}
	}

	public static <T> T get(String category, Supplier<T> action, T fallback) {
		try {
			return action.get();
		} catch (RuntimeException | LinkageError error) {
			error(category, error);
			return fallback;
		}
	}

	/** Wraps a registered HUD once without allocating a per-frame callback. */
	public static HudElement hud(String name, HudElement delegate) {
		String category = "RENDER/" + name;
		return (graphics, delta) -> {
			try {
				delegate.extractRenderState(graphics, delta);
			} catch (RuntimeException | LinkageError error) {
				error(category, error);
			}
		};
	}

	public static synchronized void flush() {
		if (writer == null) return;
		try {
			String line;
			while ((line = QUEUE.poll()) != null) {
				QUEUED.decrementAndGet();
				writer.write(line);
				writer.newLine();
			}
			writer.flush();
			var path = SafariPaths.operationalLog();
			if (Files.isRegularFile(path) && Files.size(path) >= MAX_BYTES) {
				writer.close();
				Files.move(path, path.resolveSibling("safariutils.previous.log"),
					StandardCopyOption.REPLACE_EXISTING);
				writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			}
		} catch (Exception error) {
			unavailable = true;
			try { writer.close(); } catch (Exception ignored) { }
			writer = null;
			SafariUtils.LOGGER.warn("Could not write SafariUtils operational log", error);
		}
	}

	public static synchronized void shutdown() {
		info("LIFECYCLE", "SafariUtils stopping");
		WRITER.shutdown();
		flush();
		if (writer == null) return;
		try { writer.close(); } catch (Exception ignored) { }
		writer = null;
	}

	private static void enqueue(String level, String category, String message) {
		if (unavailable || message == null) return;
		while (QUEUED.get() >= MAX_QUEUED_LINES) {
			if (QUEUE.poll() == null) break;
			QUEUED.decrementAndGet();
		}
		String cleaned = message.replace("\r\n", "\n").replace('\r', '\n');
		QUEUE.add("[" + TIME.format(LocalDateTime.now()) + "] [" + level + "] ["
			+ category + "] " + cleaned);
		QUEUED.incrementAndGet();
	}
}
