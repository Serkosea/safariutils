package dev.serko.safariutils.client;

import dev.serko.safariutils.BuildVersion;
import dev.serko.safariutils.api.SharedSparklingProviders;
import dev.serko.safariutils.concurrent.HttpBodies;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicBoolean;

/** Performs one quiet, asynchronous GitHub release check per game launch. */
public final class UpdateChecker {
	public enum Status { NOT_STARTED, CHECKING, CURRENT, AVAILABLE, UNAVAILABLE }

	private static final java.time.Duration REQUEST_TIMEOUT = java.time.Duration.ofSeconds(8);
	private static final int MAX_RESPONSE_BYTES = 512 * 1024;
	private static final URI LATEST_RELEASE = URI.create(
		"https://api.github.com/repos/Serkosea/safariutils/releases/latest");
	private static final String INSTALLED_VERSION = BuildVersion.releaseVersion();
	private static final AtomicBoolean STARTED = new AtomicBoolean();
	private static final URI releasePage =
		SharedSparklingProviders.updatePage(BuildVersion.downloadPage());
	private record Snapshot(Status status, ReleaseMetadata release) { }
	private static volatile Snapshot snapshot = new Snapshot(Status.NOT_STARTED, null);
	private static boolean notified;

	private UpdateChecker() { }

	public static void start() {
		if (!STARTED.compareAndSet(false, true)) return;
		if (NetworkRuntime.stopping()) {
			snapshot = new Snapshot(Status.UNAVAILABLE, null);
			return;
		}
		snapshot = new Snapshot(Status.CHECKING, null);
		String current = INSTALLED_VERSION;
		HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE)
			.timeout(REQUEST_TIMEOUT)
			.header("Accept", "application/vnd.github+json")
			.header("User-Agent", "SafariUtils/" + current)
			.GET().build();
		NetworkRuntime.http().sendAsync(request, HttpBodies.utf8(MAX_RESPONSE_BYTES))
			.thenAccept(response -> acceptResponse(current, response))
			.exceptionally(ignored -> {
				snapshot = new Snapshot(Status.UNAVAILABLE, null);
				DebugLog.line("UPDATES", "release check unavailable: request failed");
				return null;
			});
	}

	/** Delivers one clickable notice after chat exists, even if the request finished on the title screen. */
	public static void tick() {
		Snapshot current = snapshot;
		if (notified || current.status() != Status.AVAILABLE) return;
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) return;
		notified = true;
		ClientMessages.sendUpdate(current.release().version(), releasePage, releaseLinkLabel());
	}

	public static Status status() {
		return snapshot.status();
	}

	public static String availableVersion() {
		Snapshot current = snapshot;
		return current.status() == Status.AVAILABLE ? current.release().version() : "";
	}

	public static String currentReleaseVersion() { return INSTALLED_VERSION; }
	public static String latestVersion() { var value = snapshot.release(); return value == null ? "" : value.version(); }
	public static String releaseTitle() { var value = snapshot.release(); return value == null ? "" : value.title(); }
	public static String releaseNotes() { var value = snapshot.release(); return value == null ? "" : value.notes(); }
	public static String publishedAt() { var value = snapshot.release(); return value == null ? "" : value.publishedAt(); }
	public static URI releasePage() { return releasePage; }
	public static String releaseLinkLabel() {
		return "Mod Website";
	}

	public static boolean updateAvailable() {
		return snapshot.status() == Status.AVAILABLE;
	}

	public static boolean noticePending() {
		return snapshot.status() == Status.AVAILABLE && !notified;
	}

	private static void acceptResponse(String current, HttpResponse<String> response) {
		if (response.statusCode() / 100 != 2) {
			snapshot = new Snapshot(Status.UNAVAILABLE, null);
			DebugLog.line("UPDATES", "release check unavailable: HTTP " + response.statusCode());
			return;
		}
		try {
			ReleaseMetadata release = ReleaseMetadata.parse(response.body());
			snapshot = new Snapshot(release.newerThan(current) ? Status.AVAILABLE : Status.CURRENT, release);
			DebugLog.line("UPDATES", "release check completed status=" + snapshot.status());
		} catch (RuntimeException ignored) {
			snapshot = new Snapshot(Status.UNAVAILABLE, null);
			DebugLog.line("UPDATES", "release check unavailable: invalid metadata");
		}
	}
}
