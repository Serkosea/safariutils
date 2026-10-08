package dev.serko.safariutils;

import java.io.IOException;
import java.net.URI;
import java.util.Properties;

/** Build-time switches shared by every SafariUtils version. */
public final class BuildVersion {
	private static final Properties PROPERTIES = load();
	public static final boolean SAFE = flag("safe");
	public static final boolean PRIVATE = flag("private");
	public static final boolean DEVELOPER = flag("developer");
	private static final URI MOD_WEBSITE = URI.create("https://serkosea.dev/safariutils/");

	private BuildVersion() {
	}

	private static Properties load() {
		Properties properties = new Properties();
		try (var stream = BuildVersion.class.getResourceAsStream("/safariutils-build.properties")) {
			if (stream != null) properties.load(stream);
		} catch (IOException ignored) {
		}
		return properties;
	}

	private static boolean flag(String name) {
		return Boolean.parseBoolean(PROPERTIES.getProperty(name, "false"));
	}

	/** Standalone SafariUtils release version, also available when embedded in another mod. */
	public static String releaseVersion() {
		return PROPERTIES.getProperty("version", "unknown").strip();
	}

	/** Relative config directory used by standalone and embedded distributions. */
	public static String configDirectory() {
		String directory = PROPERTIES.getProperty("config_dir", "safariutils").strip();
		if (directory.isEmpty() || directory.startsWith("/") || directory.startsWith("\\")
				|| directory.contains("..") || directory.contains(":")) {
			return "safariutils";
		}
		return directory;
	}

	/** Compact suffix used by the settings title for the four distributed builds. */
	public static String titleSuffix() {
		if (DEVELOPER) return "-DEV";
		if (PRIVATE) return "-PRIV";
		return SAFE ? "" : "-EXTRA";
	}

	/** Public update destination shared by every distributed build. */
	public static URI downloadPage() {
		return MOD_WEBSITE;
	}
}
