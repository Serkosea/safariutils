package dev.serko.safariutils.client;

import com.google.gson.JsonObject;

/** No game, disk paths or worker startup is needed while diagnostic logging is off. */
public final class LoggingOptInRegression {
	public static int run() throws Exception {
		var configField = ConfigManager.class.getDeclaredField("config");
		configField.setAccessible(true);
		Object saved = configField.get(null);
		int checks = 0;
		var propertiesField = dev.serko.safariutils.BuildVersion.class.getDeclaredField("PROPERTIES");
		propertiesField.setAccessible(true);
		var properties = (java.util.Properties) propertiesField.get(null);
		var originalProperties = (java.util.Properties) properties.clone();
		try {
			properties.remove("log_dir");
			properties.remove("log_name");
			if (!dev.serko.safariutils.BuildVersion.logDirectory().equals(
					dev.serko.safariutils.BuildVersion.configDirectory() + "/logs")
					|| !dev.serko.safariutils.BuildVersion.logName().equals("SafariUtils"))
				throw new AssertionError("Standalone logging defaults changed");
			properties.setProperty("log_dir", "serkoaddons/logs");
			properties.setProperty("log_name", "SerkoAddons");
			if (!dev.serko.safariutils.BuildVersion.logDirectory().equals("serkoaddons/logs")
					|| !dev.serko.safariutils.BuildVersion.logName().equals("SerkoAddons"))
				throw new AssertionError("Host logging metadata ignored");
			properties.setProperty("log_dir", "../outside");
			properties.setProperty("log_name", "../outside");
			if (!dev.serko.safariutils.BuildVersion.logDirectory().equals(
					dev.serko.safariutils.BuildVersion.configDirectory() + "/logs")
					|| !dev.serko.safariutils.BuildVersion.logName().equals("SafariUtils"))
				throw new AssertionError("Unsafe logging metadata accepted");
			properties.clear();
			properties.putAll(originalProperties);
			checks += 3;
			for (SafariConfig config : new SafariConfig[] {null, new SafariConfig()}) {
				configField.set(null, config);
				if (ConfigManager.automaticLoggingEnabled()) throw new AssertionError("Logging must fail closed");
				if (EntityResearchLog.ready()) throw new AssertionError("Disabled research accessed game state");
				EntityResearchLog.onChatMessage("The Scrappy ate the Wholesale Wheat and came out of its shell!");
				PerformanceDiagnostics.frame();
                if (PerformanceDiagnostics.begin() != 0) throw new AssertionError("Disabled profiling active");
				OperationalLog.start();
				OperationalLog.info("TEST", "must not create a file");
				OperationalLog.structuredDeferred(() -> { throw new AssertionError("Disabled encoder executed"); });
				OperationalLog.flush();
				var worker = OperationalLog.class.getDeclaredField("writerExecutor");
				worker.setAccessible(true);
				if (worker.get(null) != null || OperationalLog.activeStructuredPath() != null)
					throw new AssertionError("Disabled startup created diagnostic resources");
				checks += 2;
			}
			for (boolean disabled : new boolean[] {false, true}) {
				var migrate = ConfigManager.class.getDeclaredMethod("migrateSettings", JsonObject.class);
				migrate.setAccessible(true);
				var root = new JsonObject();
				root.addProperty("settingsSchemaVersion", 4);
				var advanced = new JsonObject();
				advanced.addProperty("disableAutomaticLogging", disabled);
				root.add("advanced", advanced);
				migrate.invoke(null, root);
				if (advanced.get("enableAutomaticLogging").getAsBoolean() == disabled
					|| advanced.has("disableAutomaticLogging"))
					throw new AssertionError("Legacy logging preference changed");
				advanced.addProperty("enableAutomaticLogging", disabled);
				root.addProperty("settingsSchemaVersion", 4);
				migrate.invoke(null, root);
				if (advanced.get("enableAutomaticLogging").getAsBoolean() != disabled)
					throw new AssertionError("Explicit new preference lost");
				checks += 2;
			}
			var workerField = OperationalLog.class.getDeclaredField("writerExecutor");
			workerField.setAccessible(true);
			var executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
			workerField.set(null, executor);
			OperationalLog.settingsChanged();
			if (!executor.isShutdown() || workerField.get(null) != null)
				throw new AssertionError("Disabling logging must stop the background worker");
			checks++;
			return checks;
		} finally {
			configField.set(null, saved);
			properties.clear();
			properties.putAll(originalProperties);
		}
	}
}
