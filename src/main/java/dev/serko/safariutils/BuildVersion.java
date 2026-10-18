package dev.serko.safariutils;

import java.io.IOException;
import java.net.URI;
import java.util.Properties;
import java.util.EnumSet;

/** Build-time switches shared by every SafariUtils version. */
public final class BuildVersion {
	private static final Properties PROPERTIES = load("/safariutils-build.properties");
	private static final Properties PROVENANCE = load("/safariutils-provenance.properties");
	public static final boolean SAFE = Boolean.parseBoolean(PROPERTIES.getProperty("safe", "true"));
	public static final boolean DEVELOPER = flag("developer");
	public enum Capability {
		SAFE_MODE_CONTROLS,
		AUTHENTICATED_API,
		DEVELOPER_TOOLS,
		TICKET_TRADING,
		REMOTE_PARTY_SYNC
	}
	private static final EnumSet<Capability> CAPABILITIES = capabilities();
	private static final URI MOD_WEBSITE = URI.create("https://serkosea.dev/safariutils/");

	private BuildVersion() {
	}

	private static Properties load(String resource) {
		Properties properties = new Properties();
		try (var stream = BuildVersion.class.getResourceAsStream(resource)) {
			if (stream != null) properties.load(stream);
		} catch (IOException ignored) {
		}
		return properties;
	}

	private static boolean flag(String name) {
		return Boolean.parseBoolean(PROPERTIES.getProperty(name, "false"));
	}

	private static EnumSet<Capability> capabilities() {
		EnumSet<Capability> result = EnumSet.of(Capability.TICKET_TRADING);
		if (!SAFE) result.add(Capability.SAFE_MODE_CONTROLS);
		result.add(Capability.AUTHENTICATED_API);
		result.add(Capability.REMOTE_PARTY_SYNC);
		if (DEVELOPER) {
			result.add(Capability.DEVELOPER_TOOLS);
		}
		return result;
	}

	public static boolean has(Capability capability) {
		return CAPABILITIES.contains(capability);
	}

	/** Standalone SafariUtils release version, also available when embedded in another mod. */
	public static String releaseVersion() {
		return PROPERTIES.getProperty("version", "unknown").strip();
	}

	/** Content fingerprint shared with the standalone component built from these exact sources. */
	public static String sourceFingerprint() {
		String digest = PROVENANCE.getProperty("source_sha256", "unknown");
		return digest.matches("[0-9a-f]{64}") ? digest : "unknown";
	}

	/** Relative config directory used by standalone and embedded distributions. */
	public static String configDirectory() {
		return relativeDirectory("config_dir", "safariutils");
	}

	/** Host distribution may share one diagnostic journal across its components. */
	public static String logDirectory() {
		return relativeDirectory("log_dir", configDirectory() + "/logs");
	}

	public static String logName() {
		String name = PROPERTIES.getProperty("log_name", "SafariUtils").strip();
		return name.matches("[A-Za-z0-9_-]{1,64}") ? name : "SafariUtils";
	}

	public static boolean hostLoggingControls() {
		return flag("host_logging_controls");
	}

	private static String relativeDirectory(String key, String fallback) {
		String directory = PROPERTIES.getProperty(key, fallback).strip();
		if (directory.isEmpty() || directory.startsWith("/") || directory.startsWith("\\")
				|| directory.contains("..") || directory.contains(":")) {
			return fallback;
		}
		return directory;
	}

	/** Compact suffix used by the settings title for the three distributed builds. */
	public static String titleSuffix() {
		if (DEVELOPER) return "-DEV";
		return SAFE ? "" : "-EXTRA";
	}

	/** Public update destination shared by every distributed build. */
	public static URI downloadPage() {
		return MOD_WEBSITE;
	}
}
