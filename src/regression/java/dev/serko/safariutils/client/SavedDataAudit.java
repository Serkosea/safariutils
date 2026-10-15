package dev.serko.safariutils.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Explicit offline audit: production decoders on in-memory copies, never live stores. */
public final class SavedDataAudit {
	public static void main(String[] args) throws Exception {
		if (args.length == 0) throw new IllegalArgumentException("Supply savedDataFiles; no implicit player-directory reads");
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		int settings = 0, histories = 0, collections = 0;
		for (String arg : args) {
			Path path = Path.of(arg);
			byte[] before = Files.readAllBytes(path);
			JsonElement input = JsonParser.parseString(new String(before, java.nio.charset.StandardCharsets.UTF_8));
			String name = path.getFileName().toString();
			if (name.contains("runs")) {
				Class<?> history = Class.forName("dev.serko.safariutils.session.RunHistory");
				Object decoded = invoke(history, null, "decodeStore", new Class<?>[] {JsonElement.class, boolean.class}, input.deepCopy(), true);
				if ((int) accessor(decoded, "rejected") != 0 || (boolean) accessor(decoded, "readOnly"))
					throw new AssertionError("History requires recovery: " + name);
				List<?> runs = (List<?>) accessor(decoded, "runs");
				int sourceCount = input.isJsonArray() ? input.getAsJsonArray().size() : input.getAsJsonObject().getAsJsonArray("runs").size();
				if (runs.size() != sourceCount) throw new AssertionError("History count changed: " + name);
				histories++;
			} else if (name.contains("sparkling")) {
				Class<?> stats = Class.forName("dev.serko.safariutils.session.SparklingStats");
				Object decoded = invoke(stats, null, "decodeStore", new Class<?>[] {JsonObject.class}, input.deepCopy().getAsJsonObject());
				if ((int) accessor(decoded, "rejected") != 0) throw new AssertionError("Collection requires recovery: " + name);
				collections++;
			} else if (name.equals("safariutils.json") || name.startsWith("safariutils-settings-") || name.startsWith("safariutils-before-")) {
				JsonObject copy = input.deepCopy().getAsJsonObject();
				SafariConfig decoded = ConfigManager.validateImported(copy);
				JsonObject encoded = (JsonObject) invoke(ConfigManager.class, null, "mergedSettingsJson",
					new Class<?>[] {SafariConfig.class, JsonObject.class}, decoded, copy);
				JsonObject next = encoded.deepCopy();
				SafariConfig again = ConfigManager.validateImported(next);
				Object roundTrip = invoke(ConfigManager.class, null, "mergedSettingsJson",
					new Class<?>[] {SafariConfig.class, JsonObject.class}, again, next);
				if (!encoded.equals(roundTrip)) throw new AssertionError("Settings migration is not stable: " + name);
				settings++;
			} else throw new IllegalArgumentException("Unsupported audit file: " + name);
			if (!Arrays.equals(before, Files.readAllBytes(path))) throw new AssertionError("Source changed during audit: " + name);
		}
		System.out.println("Saved-data audit passed: settings=" + settings + " histories=" + histories
			+ " collections=" + collections + "; source bytes unchanged; no writes");
	}
	private static Object accessor(Object target, String name) throws Exception {
		return invoke(target.getClass(), target, name, new Class<?>[0]);
	}
	private static Object invoke(Class<?> type, Object target, String name, Class<?>[] signature, Object... args) throws Exception {
		Method method = type.getDeclaredMethod(name, signature);
		method.setAccessible(true);
		return method.invoke(target, args);
	}
	private SavedDataAudit() { }
}
