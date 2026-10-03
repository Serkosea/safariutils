package dev.serko.safariutils.client;

import dev.serko.safariutils.BuildVersion;
import dev.serko.safariutils.SafariUtils;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Always-on, asynchronous diagnostics. Gameplay threads only enqueue compact records;
 * formatting, repetition summaries, rotation, and disk I/O happen on one daemon thread.
 */
public final class OperationalLog {
	private static final long MAX_BYTES = 4L * 1_024L * 1_024L;
	private static final int MAX_QUEUED_LINES = 2_048;
	private static final int MAX_MESSAGE_CHARACTERS = 4_000;
	private static final int MAX_ERROR_CHARACTERS = 24_000;
	private static final long ERROR_REPEAT_MILLIS = 5_000L;
	private static final long DEBUG_REPEAT_MILLIS = 2_000L;
	private static final DateTimeFormatter TIME = DateTimeFormatter
		.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
	private static final ConcurrentLinkedQueue<Entry> QUEUE = new ConcurrentLinkedQueue<>();
	private static final AtomicInteger QUEUED = new AtomicInteger();
	private static final AtomicInteger DROPPED = new AtomicInteger();
	private static final Map<String, Long> LAST_ERROR = new ConcurrentHashMap<>();
	private static final Map<String, Repeat> REPEATS = new ConcurrentHashMap<>();
	private static final ScheduledExecutorService WRITER =
		Executors.newSingleThreadScheduledExecutor(task -> {
			Thread thread = new Thread(task, "safariutils-log");
			thread.setDaemon(true);
			return thread;
		});
	private static BufferedWriter writer;
	private static Path activePath;
	private static String sessionName;
	private static int sessionPart;
	private static boolean unavailable;
	private static boolean scheduled;

	private record Entry(long time, String level, String category, String message) { }
	private static final class Repeat {
		private final String category;
		private final String message;
		private long lastAt;
		private int skipped;

		private Repeat(String category, String message, long now) {
			this.category = category;
			this.message = message;
			this.lastAt = now;
		}
	}

	private OperationalLog() { }

	public static synchronized void start() {
		if (disabled() || writer != null || unavailable) return;
		try {
			sessionName = "SafariUtils_" + fileTime(Instant.now());
			sessionPart = 1;
			Path path = nextAvailablePath();
			activePath = path;
			Files.createDirectories(path.getParent());
			writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			info("LIFECYCLE", "SafariUtils initialized build="
				+ (BuildVersion.DEVELOPER ? "developer" : BuildVersion.PRIVATE ? "private"
				: BuildVersion.SAFE ? "safe" : "extra"));
			flush();
			if (!scheduled) {
				scheduled = true;
				WRITER.scheduleAtFixedRate(OperationalLog::flush, 1, 1, TimeUnit.SECONDS);
			}
		} catch (Exception error) {
			unavailable = true;
			SafariUtils.LOGGER.warn("Could not open SafariUtils log", error);
		}
	}

	public static void info(String category, String message) {
		if (disabled()) return;
		enqueue("INFO", category, message);
	}

	/** Records high-volume diagnostics with short-window duplicate coalescing. */
	public static void debug(String category, String message) {
		if (disabled() || unavailable || message == null) return;
		long now = System.currentTimeMillis();
		String key = category + '\u0000' + message;
		Repeat created = new Repeat(category, message, now);
		Repeat previous = REPEATS.putIfAbsent(key, created);
		if (previous == null) {
			enqueue(new Entry(now, "DEBUG", category, message));
			return;
		}
		synchronized (previous) {
			if (now - previous.lastAt <= DEBUG_REPEAT_MILLIS) {
				previous.lastAt = now;
				previous.skipped++;
				return;
			}
			if (previous.skipped > 0) enqueue(new Entry(now, "DEBUG", category,
				"repeat=" + previous.skipped + " previous=" + previous.message));
			previous.lastAt = now;
			previous.skipped = 0;
			enqueue(new Entry(now, "DEBUG", category, message));
		}
		if (REPEATS.size() > 512) expireRepeats(now);
	}

	public static void error(String category, Throwable error) {
		if (error == null) return;
		if (disabled()) {
			// Release variants still report failures through Minecraft's ordinary log,
			// but only Developer builds create SafariUtils diagnostic files.
			SafariUtils.LOGGER.error("SafariUtils {} failure", category, error);
			return;
		}
		String signature = category + '|' + error.getClass().getName() + '|' + error.getMessage();
		long now = System.currentTimeMillis();
		Long previous = LAST_ERROR.put(signature, now);
		if (previous != null && now - previous < ERROR_REPEAT_MILLIS) return;
		if (LAST_ERROR.size() > 128) LAST_ERROR.clear();
		SafariUtils.LOGGER.error("SafariUtils {} failure", category, error);
		StringWriter trace = new StringWriter(1024);
		error.printStackTrace(new PrintWriter(trace));
		String detail = trace.toString();
		if (detail.length() > MAX_ERROR_CHARACTERS) {
			detail = detail.substring(0, MAX_ERROR_CHARACTERS) + "\n... stack trace truncated";
		}
		enqueue(new Entry(now, "ERROR", category, detail));
	}

	public static void run(String category, Runnable action) {
		try { action.run(); }
		catch (RuntimeException | LinkageError error) { error(category, error); }
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
		Path path = activePath != null ? activePath : SafariPaths.operationalLog();
		long bytes = 0L;
		try { if (Files.isRegularFile(path)) bytes = Files.size(path); }
		catch (Exception ignored) { }
		return "Automatic diagnostics\n  path " + path.toAbsolutePath()
			+ "\n  available " + !unavailable + " · queued " + QUEUED.get()
			+ " · dropped " + DROPPED.get() + " · bytes " + bytes + '\n';
	}

	public static synchronized void flush() {
		if (disabled()) {
			QUEUE.clear();
			QUEUED.set(0);
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
			int dropped = DROPPED.getAndSet(0);
			if (dropped > 0) write(new Entry(now, "WARN", "LOG", "dropped=" + dropped));
			Entry entry;
			while ((entry = QUEUE.poll()) != null) {
				QUEUED.decrementAndGet();
				write(entry);
			}
			writer.flush();
			Path path = activePath;
			if (Files.isRegularFile(path) && Files.size(path) >= MAX_BYTES) {
				writer.close();
				sessionPart++;
				path = nextAvailablePath();
				activePath = path;
				writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			}
		} catch (Exception error) {
			unavailable = true;
			try { writer.close(); } catch (Exception ignored) { }
			writer = null;
			SafariUtils.LOGGER.warn("Could not write SafariUtils log", error);
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
		if (message != null) enqueue(new Entry(System.currentTimeMillis(), level, category, message));
	}

	private static void enqueue(Entry entry) {
		if (disabled() || unavailable || entry == null) return;
		if (writer == null) start();
		if (writer == null) return;
		if (QUEUED.incrementAndGet() > MAX_QUEUED_LINES) {
			QUEUED.decrementAndGet();
			DROPPED.incrementAndGet();
			return;
		}
		QUEUE.add(entry);
	}

	private static boolean disabled() {
		return !BuildVersion.DEVELOPER || ConfigManager.automaticLoggingDisabled();
	}

	private static void expireRepeats(long now) {
		REPEATS.entrySet().removeIf(entry -> {
			Repeat repeat = entry.getValue();
			synchronized (repeat) {
				if (now - repeat.lastAt <= DEBUG_REPEAT_MILLIS) return false;
				if (repeat.skipped > 0) enqueue(new Entry(now, "DEBUG", repeat.category,
					"repeat=" + repeat.skipped + " previous=" + repeat.message));
				return true;
			}
		});
	}

	private static void write(Entry entry) throws Exception {
		String message = entry.message().replace("\r\n", "\n").replace('\r', '\n');
		if (!"ERROR".equals(entry.level()) && message.length() > MAX_MESSAGE_CHARACTERS) {
			message = message.substring(0, MAX_MESSAGE_CHARACTERS) + "...";
		}
		writer.write("[" + TIME.format(Instant.ofEpochMilli(entry.time())) + "] ["
			+ entry.level() + "] [" + entry.category() + "] " + message);
		writer.newLine();
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
		Path path = SafariPaths.operationalLog(sessionName, sessionPart);
		while (Files.exists(path)) {
			sessionPart++;
			path = SafariPaths.operationalLog(sessionName, sessionPart);
		}
		return path;
	}
}
