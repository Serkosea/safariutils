package dev.serko.safariutils.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.serko.safariutils.io.AtomicFiles;
import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/** Compact, versioned settings transfer with validation before any live state changes. */
final class SettingsTransfer {
	private static final String PREFIX_V1 = "SU1-";
	private static final String PREFIX_V2 = "SU2-";
	private static final int FORMAT_V1 = 1;
	private static final int FORMAT_V2 = 2;
	private static final int MAX_CODE_LENGTH = 2_000_000;
	private static final int WHITESPACE_ALLOWANCE = 4_096;
	private static final int MAX_JSON_BYTES = 4_000_000;
	private static final int COMPRESSION_BUFFER_BYTES = 8_192;
	private static final DateTimeFormatter BACKUP_TIME =
		DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

	record Preview(boolean valid, String message, String sourceVersion, int codeLength,
		JsonObject settings) { }
	record ImportResult(boolean success, String message, Path backup) { }

	private SettingsTransfer() { }

	static String exportCode() {
		JsonObject envelope = new JsonObject();
		envelope.addProperty("f", FORMAT_V2);
		envelope.addProperty("v", currentVersion());
		envelope.add("s", ConfigManager.settingsJson());
		byte[] source = envelope.toString().getBytes(StandardCharsets.UTF_8);
		Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(source.length / 2);
			try (DeflaterOutputStream compressed = new DeflaterOutputStream(
					bytes, deflater, COMPRESSION_BUFFER_BYTES)) {
				compressed.write(source);
			}
			return PREFIX_V2 + Base64.getUrlEncoder().withoutPadding()
				.encodeToString(bytes.toByteArray());
		} catch (IOException impossible) {
			throw new IllegalStateException("Could not compress settings", impossible);
		} finally {
			deflater.end();
		}
	}

	static Preview preview(String code) {
		if (code != null && code.length() > MAX_CODE_LENGTH + WHITESPACE_ALLOWANCE) {
			return invalid("Settings code is too large");
		}
		String clean = code == null ? "" : code.strip().replaceAll("\\s+", "");
		boolean versionOne = clean.startsWith(PREFIX_V1);
		boolean versionTwo = clean.startsWith(PREFIX_V2);
		if (!versionOne && !versionTwo) return invalid("Not a SafariUtils settings code");
		if (clean.length() > MAX_CODE_LENGTH) return invalid("Settings code is too large");
		try {
			byte[] compressed = Base64.getUrlDecoder().decode(clean.substring(PREFIX_V1.length()));
			byte[] json = inflateLimited(compressed, versionOne);
			JsonObject envelope = JsonParser.parseString(
				new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
			String formatKey = versionOne ? "format" : "f";
			String versionKey = versionOne ? "version" : "v";
			String settingsKey = versionOne ? "settings" : "s";
			int expectedFormat = versionOne ? FORMAT_V1 : FORMAT_V2;
			if (!envelope.has(formatKey) || envelope.get(formatKey).getAsInt() != expectedFormat
					|| !envelope.has(settingsKey) || !envelope.get(settingsKey).isJsonObject()) {
				return invalid("Unsupported settings code format");
			}
			String version = envelope.has(versionKey)
				? envelope.get(versionKey).getAsString() : "Unknown";
			if (isNewer(version, currentVersion())) {
				return new Preview(false, "Created by newer SafariUtils " + version,
					version, clean.length(), null);
			}
			JsonObject settings = envelope.getAsJsonObject(settingsKey);
			ConfigManager.validateImported(settings.deepCopy());
			return new Preview(true, "Ready to import", version, clean.length(), settings);
		} catch (RuntimeException | IOException invalid) {
			return invalid("Settings code is damaged or incomplete");
		}
	}

	static ImportResult apply(Preview preview) {
		if (preview == null || !preview.valid || preview.settings == null) {
			return new ImportResult(false, "No valid settings code is ready", null);
		}
		Path path = SafariPaths.settings();
		Path backup = SafariPaths.uniqueJsonBackup(SafariPaths.settingsBackups(),
			"safariutils-settings-backup-"
			+ BACKUP_TIME.format(LocalDateTime.now()));
		try {
			ConfigManager.save();
			Files.createDirectories(backup.getParent());
			if (Files.isRegularFile(path)) {
				Files.copy(path, backup, StandardCopyOption.COPY_ATTRIBUTES);
			} else {
				AtomicFiles.writeString(backup, ConfigManager.settingsJson().toString());
			}
			ConfigManager.importSettings(preview.settings.deepCopy());
			return new ImportResult(true, "Settings imported; previous settings backed up", backup);
		} catch (IOException | RuntimeException failed) {
			OperationalLog.error("CONFIG/IMPORT", failed);
			return new ImportResult(false, "Import failed; current settings were not replaced", null);
		}
	}

	private static byte[] inflateLimited(byte[] compressed, boolean gzipFormat) throws IOException {
		Inflater inflater = gzipFormat ? null : new Inflater(true);
		java.io.InputStream compressedInput = gzipFormat
			? new GZIPInputStream(new ByteArrayInputStream(compressed))
			: new InflaterInputStream(new ByteArrayInputStream(compressed), inflater,
				COMPRESSION_BUFFER_BYTES);
		try (java.io.InputStream input = compressedInput;
			 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			byte[] buffer = new byte[COMPRESSION_BUFFER_BYTES];
			int total = 0;
			for (int read; (read = input.read(buffer)) >= 0;) {
				total += read;
				if (total > MAX_JSON_BYTES) throw new IOException("Expanded settings exceed limit");
				output.write(buffer, 0, read);
			}
			return output.toByteArray();
		} finally {
			if (inflater != null) inflater.end();
		}
	}

	private static Preview invalid(String message) {
		return new Preview(false, message, "", 0, null);
	}

	private static String currentVersion() {
		return FabricLoader.getInstance().getModContainer("safariutils")
			.map(container -> normalize(container.getMetadata().getVersion().getFriendlyString()))
			.orElse("0.0.0");
	}

	private static boolean isNewer(String candidate, String current) {
		int[] left = numeric(candidate);
		int[] right = numeric(current);
		if (left == null || right == null) return false;
		for (int index = 0; index < 3; index++) {
			if (left[index] != right[index]) return left[index] > right[index];
		}
		return false;
	}

	private static int[] numeric(String version) {
		String[] parts = normalize(version).split("\\.");
		if (parts.length < 2 || parts.length > 3) return null;
		int[] result = new int[3];
		try {
			for (int index = 0; index < parts.length; index++) result[index] = Integer.parseInt(parts[index]);
			return result;
		} catch (NumberFormatException invalid) {
			return null;
		}
	}

	private static String normalize(String version) {
		String clean = version == null ? "" : version.strip();
		if (clean.startsWith("v") || clean.startsWith("V")) clean = clean.substring(1);
		int build = clean.indexOf('+');
		if (build >= 0) clean = clean.substring(0, build);
		return clean.replaceFirst("-(?:extra|private|developer)$", "");
	}
}
