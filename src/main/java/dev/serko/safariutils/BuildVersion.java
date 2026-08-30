package dev.serko.safariutils;

import java.io.IOException;
import java.util.Properties;

/** Build-time switches shared by every Safari Utils version. */
public final class BuildVersion {
	private static final Properties PROPERTIES = load();
	public static final boolean SAFE = flag("safe");
	public static final boolean DEVELOPER = flag("developer");

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
}
