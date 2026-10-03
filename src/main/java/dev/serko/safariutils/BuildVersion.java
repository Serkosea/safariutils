package dev.serko.safariutils;

import java.io.IOException;
import java.net.URI;
import java.util.Properties;

/** Build-time switches shared by every Safari Utils version. */
public final class BuildVersion {
	private static final Properties PROPERTIES = load();
	public static final boolean SAFE = flag("safe");
	public static final boolean PRIVATE = flag("private");
	public static final boolean DEVELOPER = flag("developer");
	private static final URI PUBLIC_DOWNLOAD_PAGE = URI.create(
		"https://github.com/Serkosea/safariutils/releases/latest");

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

	/** Compact suffix used by the settings title for the four distributed builds. */
	public static String titleSuffix() {
		if (DEVELOPER) return "-DEV";
		if (PRIVATE) return "-PRIV";
		return SAFE ? "" : "-EXTRA";
	}

	/** Build-specific update destination; private URLs are supplied only to ignored local builds. */
	public static URI downloadPage() {
		String configured = PROPERTIES.getProperty("downloadPage", "").strip();
		try {
			URI page = URI.create(configured);
			return "https".equalsIgnoreCase(page.getScheme()) ? page : PUBLIC_DOWNLOAD_PAGE;
		} catch (RuntimeException invalid) {
			return PUBLIC_DOWNLOAD_PAGE;
		}
	}
}
