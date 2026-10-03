package dev.serko.safariutils.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.serko.safariutils.BuildVersion;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/** Performs one quiet, asynchronous GitHub release check per game launch. */
public final class UpdateChecker {
	public enum Status { NOT_STARTED, CHECKING, CURRENT, AVAILABLE, UNAVAILABLE }

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(6);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
	private static final URI LATEST_RELEASE = URI.create(
		"https://api.github.com/repos/Serkosea/safariutils/releases/latest");
	private static final String INSTALLED_VERSION = readCurrentVersion();
	private static final AtomicBoolean STARTED = new AtomicBoolean();
	private static volatile String availableVersion = "";
	private static volatile String latestVersion = "";
	private static volatile String releaseTitle = "";
	private static volatile String releaseNotes = "";
	private static volatile String publishedAt = "";
	private static volatile URI releasePage = BuildVersion.downloadPage();
	private static volatile Status status = Status.NOT_STARTED;
	private static boolean notified;

	private UpdateChecker() { }

	public static void start() {
		if (!STARTED.compareAndSet(false, true)) return;
		status = Status.CHECKING;
		String current = INSTALLED_VERSION;
		HttpClient client = HttpClient.newBuilder()
			.connectTimeout(CONNECT_TIMEOUT)
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
		HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE)
			.timeout(REQUEST_TIMEOUT)
			.header("Accept", "application/vnd.github+json")
			.header("User-Agent", "SafariUtils/" + current)
			.GET().build();
		client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
			.thenAccept(response -> acceptResponse(current, response))
			.exceptionally(ignored -> {
				status = Status.UNAVAILABLE;
				return null;
			});
	}

	/** Delivers one clickable notice after chat exists, even if the request finished on the title screen. */
	public static void tick() {
		if (notified || status != Status.AVAILABLE) return;
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) return;
		notified = true;
		ClientMessages.sendUpdate(availableVersion, releasePage, releaseLinkLabel());
	}

	public static Status status() {
		return status;
	}

	public static String availableVersion() {
		return availableVersion;
	}

	public static String currentReleaseVersion() { return INSTALLED_VERSION; }
	public static String latestVersion() { return latestVersion; }
	public static String releaseTitle() { return releaseTitle; }
	public static String releaseNotes() { return releaseNotes; }
	public static String publishedAt() { return publishedAt; }
	public static URI releasePage() { return releasePage; }
	public static String releaseLinkLabel() {
		return BuildVersion.PRIVATE ? "Private Downloads" : "GitHub Release";
	}

	public static boolean updateAvailable() {
		return !availableVersion.isEmpty();
	}

	public static boolean noticePending() {
		return status == Status.AVAILABLE && !notified;
	}

	private static void acceptResponse(String current, HttpResponse<String> response) {
		if (response.statusCode() / 100 != 2) {
			status = Status.UNAVAILABLE;
			return;
		}
		try {
			JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
			if (!release.has("tag_name")) {
				status = Status.UNAVAILABLE;
				return;
			}
			String latest = normalize(release.get("tag_name").getAsString());
			latestVersion = latest;
			releaseTitle = string(release, "name");
			releaseNotes = string(release, "body");
			publishedAt = string(release, "published_at");
			String page = string(release, "html_url");
			if (!BuildVersion.PRIVATE && !page.isBlank()) {
				URI candidate = URI.create(page);
				if ("https".equalsIgnoreCase(candidate.getScheme())
						&& "github.com".equalsIgnoreCase(candidate.getHost())) releasePage = candidate;
			}
			if (newer(latest, current)) {
				availableVersion = latest;
				status = Status.AVAILABLE;
			} else {
				status = Status.CURRENT;
			}
		} catch (RuntimeException ignored) {
			status = Status.UNAVAILABLE;
		}
	}

	private static String string(JsonObject object, String key) {
		return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
	}

	private static String readCurrentVersion() {
		return FabricLoader.getInstance().getModContainer("safariutils")
			.map(container -> normalize(container.getMetadata().getVersion().getFriendlyString()))
			.orElse("0.0.0");
	}

	private static String normalize(String version) {
		String clean = version == null ? "" : version.trim();
		if (clean.startsWith("v") || clean.startsWith("V")) clean = clean.substring(1);
		int build = clean.indexOf('+');
		if (build >= 0) clean = clean.substring(0, build);
		return clean.replaceFirst("-(?:extra|private|developer)$", "");
	}

	private static boolean newer(String candidate, String current) {
		int[] left = numericVersion(candidate);
		int[] right = numericVersion(current);
		if (left == null || right == null) return false;
		for (int index = 0; index < left.length; index++) {
			if (left[index] != right[index]) return left[index] > right[index];
		}
		return false;
	}

	private static int[] numericVersion(String version) {
		String core = version.split("-", 2)[0];
		String[] parts = core.split("\\.");
		if (parts.length < 2 || parts.length > 3) return null;
		int[] values = new int[3];
		try {
			for (int index = 0; index < parts.length; index++) {
				values[index] = Integer.parseInt(parts[index]);
			}
			return values;
		} catch (NumberFormatException ignored) {
			return null;
		}
	}
}
