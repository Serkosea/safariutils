package dev.serko.safariutils.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.serko.safariutils.io.AtomicFiles;
import dev.serko.safariutils.io.RecoverySnapshot;
import dev.serko.safariutils.io.PersistentJson;
import dev.serko.safariutils.BuildVersion;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Persistent last-known-good canonical identities and rank colours. */
public final class PlayerIdentityStore {
	private static final int SCHEMA = 3;
	private static final int LIMIT = 1_024;
	private static final long SAVE_DELAY_MILLIS = 2_000L;
	private static final long TEMPORARY_FRESH_MILLIS = 60 * 60_000L;
	private static final long PERMANENT_FRESH_MILLIS = 24 * 60 * 60_000L;
	private static final long TEMPORARY_FALLBACK_MILLIS = 24 * 60 * 60_000L;
	private static final int NO_RANK = 0xFFAAAAAA;
	private static final int VIP = 0xFF55FF55;
	private static final int MVP = 0xFF55FFFF;
	private static final int MVP_PLUS_PLUS = 0xFFFFAA00;
	private static final Gson GSON = new GsonBuilder().create();
	private static final Map<String, Entry> byUuid = new LinkedHashMap<>();
	private static final Map<String, String> uuidByName = new HashMap<>();
	private static JsonObject preservedRoot;
	private static boolean dirty;
	private static long saveAt;
	private static boolean writesBlocked;
	private static boolean saveBlockReported;
	private static boolean pendingVersionBackup;
	private static boolean pendingUpgradeBackup;
	private static final RecoverySnapshot recoverySnapshot = new RecoverySnapshot("safariutils-player-identities-recovery");

	public static final class Entry {
		public String uuid = "";
		public String name = "";
		public int rankColour = -1;
		public int permanentRankColour = -1;
		public int permanentRankLevel = -1;
		public boolean temporaryRank;
		public long rankUpdatedAt;
		public long updatedAt;
	}

	public record RankSnapshot(int colour, int permanentColour, boolean temporary,
			long updatedAt, boolean currentKnown) {
		public boolean fresh(long now) {
			long maxAge = temporary ? TEMPORARY_FRESH_MILLIS : PERMANENT_FRESH_MILLIS;
			return currentKnown && colour != -1 && updatedAt > 0L
				&& now >= updatedAt && now - updatedAt < maxAge;
		}
	}

	private PlayerIdentityStore() { }

	/** Startup invariant guarding signed ARGB persistence and permanent-rank fallback. */
	public static void verifyPersistenceModel() {
		Entry permanent = new Entry();
		permanent.uuid = "00000000000000000000000000000001";
		permanent.name = "CachedPlayer";
		permanent.rankColour = -1;
		permanent.permanentRankColour = MVP;
		permanent.permanentRankLevel = 4;
		normalize(permanent, permanent.uuid);
		if (permanent.permanentRankColour != MVP
				|| resolvedRankColour(permanent, System.currentTimeMillis()) != MVP) {
			throw new IllegalStateException("Persisted permanent rank fallback was not preserved");
		}
		Entry temporary = new Entry();
		temporary.uuid = "00000000000000000000000000000002";
		temporary.name = "TemporaryPlayer";
		temporary.rankColour = MVP_PLUS_PLUS;
		temporary.permanentRankColour = MVP;
		temporary.permanentRankLevel = 4;
		temporary.temporaryRank = true;
		temporary.rankUpdatedAt = System.currentTimeMillis();
		normalize(temporary, temporary.uuid);
		if (temporary.rankColour != MVP_PLUS_PLUS || temporary.permanentRankColour != MVP) {
			throw new IllegalStateException("Persisted signed ARGB rank colour was not preserved");
		}
	}

	public static synchronized void load() {
		byUuid.clear();
		uuidByName.clear();
		preservedRoot = null;
		dirty = false;
		saveAt = 0L;
		writesBlocked = false;
		saveBlockReported = false;
		pendingVersionBackup = false;
		pendingUpgradeBackup = false;
		recoverySnapshot.reset();
		Path path = SafariPaths.playerIdentities();
		if (!Files.isRegularFile(path)) return;
		try {
			try {
				PersistentJson.backupBeforeVersion(path, SafariPaths.identityBackups(),
					"safariutils-player-identities", BuildVersion.releaseVersion());
			} catch (Exception backupFailed) {
				pendingVersionBackup = true;
				OperationalLog.error("IDENTITY/VERSION_BACKUP", backupFailed);
			}
			JsonObject root = JsonParser.parseString(Files.readString(path,
				StandardCharsets.UTF_8)).getAsJsonObject();
			preservedRoot = root.deepCopy();
			int schema = PersistentJson.schema(root, "schemaVersion");
			if (schema > SCHEMA) {
				writesBlocked = true;
				OperationalLog.info("IDENTITY/FUTURE_SCHEMA",
					"Identity data schema is newer or unrecognized; using it read-only");
			} else if (schema < SCHEMA) {
				try {
					PersistentJson.backupBeforeUpgrade(path, SafariPaths.identityBackups(),
						"safariutils-player-identities", SCHEMA);
				} catch (Exception backupFailed) {
					pendingUpgradeBackup = true;
					OperationalLog.error("IDENTITY/BACKUP", backupFailed);
				}
			}
			Decoded decoded = decodeStore(root);
			writesBlocked |= decoded.rejected() > 0;
			decoded.entries().values().stream().sorted(Comparator.comparingLong((Entry entry) -> entry.updatedAt)
				.thenComparing(entry -> entry.uuid)).forEach(PlayerIdentityStore::index);
			if (decoded.rejected() > 0) OperationalLog.info("IDENTITY/PARTIAL",
				"Recovered readable identities; protected source; rejected=" + decoded.rejected());
			trim();
			StructuredDiagnostics.decision("identity", "loaded", "entries=" + byUuid.size());
		} catch (Exception failed) {
			OperationalLog.error("IDENTITY/LOAD", failed);
			boolean preserved = false;
			try {
				preserved = SafariPaths.quarantineUnreadableJson(path,
					SafariPaths.identityBackups(), "safariutils-player-identities") != null;
			} catch (Exception backupFailed) {
				OperationalLog.error("IDENTITY/RECOVERY", backupFailed);
			}
			writesBlocked |= !preserved && Files.exists(path);
		}
	}

	public static synchronized void remember(String name, String uuid) {
		String canonical = validName(name);
		String compact = compactUuid(uuid);
		if (canonical.isEmpty() || compact.isEmpty()) return;
		Entry entry = byUuid.get(compact);
		boolean created = entry == null;
		if (created) {
			entry = new Entry();
			byUuid.put(compact, entry);
		}
		String previous = entry.name;
		boolean changed = created || !compact.equals(entry.uuid) || !canonical.equals(entry.name);
		entry.uuid = compact;
		entry.name = canonical;
		if (changed) entry.updatedAt = System.currentTimeMillis();
		if (!previous.isEmpty() && !previous.equalsIgnoreCase(canonical)) {
			uuidByName.remove(previous.toLowerCase(Locale.ROOT), compact);
		}
		uuidByName.put(canonical.toLowerCase(Locale.ROOT), compact);
		if (changed) {
			markDirty();
			trim();
		}
	}

	public static synchronized void rememberApiRank(String name, String uuid, int colour,
			int permanentColour, int permanentLevel, boolean temporary, long fetchedAt) {
		remember(name, uuid);
		Entry entry = find(name, uuid);
		if (entry == null) return;
		long observedAt = Math.max(0L, fetchedAt);
		boolean changed = mergePermanent(entry, permanentColour, permanentLevel);
		if (colour != -1 && observedAt >= entry.rankUpdatedAt
				&& (entry.rankColour != colour || entry.temporaryRank != temporary
					|| entry.rankUpdatedAt != observedAt)) {
			entry.rankColour = colour;
			entry.temporaryRank = temporary;
			entry.rankUpdatedAt = observedAt;
			entry.updatedAt = Math.max(entry.updatedAt, observedAt);
			changed = true;
		}
		if (changed) {
			entry.updatedAt = Math.max(entry.updatedAt, observedAt);
			markDirty();
		}
	}

	/** Stores the current API rank while preserving any stronger permanent-rank evidence. */
	public static synchronized void rememberApiRank(String name, String uuid, int colour,
			long fetchedAt) {
		boolean temporary = colour == MVP_PLUS_PLUS;
		rememberApiRank(name, uuid, colour, temporary ? -1 : colour, -1, temporary, fetchedAt);
	}

	/** A live tab-list observation is the cheapest and most current rank authority. */
	public static synchronized void rememberLiveRank(String name, String uuid, int colour,
			int permanentLevel) {
		remember(name, uuid);
		Entry entry = find(name, uuid);
		if (entry == null || colour == -1) return;
		long now = System.currentTimeMillis();
		boolean temporary = colour == MVP_PLUS_PLUS;
		boolean changed = !temporary && mergePermanent(entry, colour, permanentLevel);
		boolean refreshTimestamp = now - entry.rankUpdatedAt >= TEMPORARY_FRESH_MILLIS;
		if (entry.rankColour != colour || entry.temporaryRank != temporary || refreshTimestamp) {
			entry.rankColour = colour;
			entry.temporaryRank = temporary;
			entry.rankUpdatedAt = now;
			entry.updatedAt = now;
			changed = true;
		}
		if (changed) {
			markDirty();
		}
	}

	public static synchronized String canonicalName(String name) {
		Entry entry = find(name, "");
		return entry == null ? "" : entry.name;
	}

	public static synchronized String uuid(String name) {
		return uuidByName.getOrDefault(validName(name).toLowerCase(Locale.ROOT), "");
	}

	public static synchronized int rankColour(String name, String uuid) {
		Entry entry = find(name, uuid);
		return entry == null ? -1 : resolvedRankColour(entry, System.currentTimeMillis());
	}

	public static synchronized RankSnapshot rankSnapshot(String name, String uuid) {
		Entry entry = find(name, uuid);
		return entry == null ? new RankSnapshot(-1, -1, false, 0L, false)
			: new RankSnapshot(rankColour(name, uuid), entry.permanentRankColour,
				entry.temporaryRank, entry.rankUpdatedAt, entry.rankColour != -1);
	}

	public static synchronized void tick() {
		if (dirty && System.currentTimeMillis() >= saveAt) save();
	}

	public static synchronized void shutdown() {
		if (dirty) save();
	}

	private static Entry find(String name, String uuid) {
		String compact = compactUuid(uuid);
		if (!compact.isEmpty()) {
			Entry entry = byUuid.get(compact);
			if (entry != null) return entry;
		}
		String key = validName(name).toLowerCase(Locale.ROOT);
		String knownUuid = uuidByName.get(key);
		return knownUuid == null ? null : byUuid.get(knownUuid);
	}

	private static void index(Entry entry) {
		Entry previous = byUuid.put(entry.uuid, entry);
		if (previous != null) uuidByName.remove(previous.name.toLowerCase(Locale.ROOT), entry.uuid);
		uuidByName.put(entry.name.toLowerCase(Locale.ROOT), entry.uuid);
	}

	private static void normalize(Entry entry, String fallbackUuid) {
		if (entry == null) return;
		entry.uuid = compactUuid(entry.uuid == null || entry.uuid.isEmpty() ? fallbackUuid : entry.uuid);
		entry.name = validName(entry.name);
		if (!storedColour(entry.rankColour)) entry.rankColour = -1;
		if (!storedColour(entry.permanentRankColour)) entry.permanentRankColour = -1;
		if (entry.permanentRankLevel < -1 || entry.permanentRankLevel > 4) {
			entry.permanentRankLevel = -1;
		}
		if (entry.permanentRankLevel == -1) {
			entry.permanentRankLevel = conservativePermanentLevel(entry.permanentRankColour);
		}
		if (entry.permanentRankColour == -1 && entry.rankColour != MVP_PLUS_PLUS) {
			entry.permanentRankColour = conservativePermanentLevel(entry.rankColour) >= 0
				? entry.rankColour : -1;
			entry.permanentRankLevel = conservativePermanentLevel(entry.rankColour);
		}
		if (entry.rankUpdatedAt <= 0L && entry.rankColour != -1) {
			entry.rankUpdatedAt = Math.max(0L, entry.updatedAt);
		}
		entry.temporaryRank = entry.temporaryRank || entry.rankColour == MVP_PLUS_PLUS;
		entry.updatedAt = Math.max(0L, entry.updatedAt);
	}

	private static boolean mergePermanent(Entry entry, int colour, int level) {
		int incoming = level >= 0 && level <= 4 ? level : conservativePermanentLevel(colour);
		if (incoming < 0) return false;
		int existing = entry.permanentRankLevel;
		if (existing >= incoming) return false;
		entry.permanentRankColour = colour;
		entry.permanentRankLevel = incoming;
		return true;
	}

	private static int conservativePermanentLevel(int colour) {
		return switch (colour) {
			case NO_RANK -> 0;
			case VIP -> 1;
			case MVP -> 3;
			default -> -1;
		};
	}

	private static int resolvedRankColour(Entry entry, long now) {
		if (entry.rankColour == -1) return entry.permanentRankColour;
		if (entry.temporaryRank && entry.rankUpdatedAt > 0L
				&& entry.permanentRankColour != -1
				&& now - entry.rankUpdatedAt >= TEMPORARY_FALLBACK_MILLIS) {
			return entry.permanentRankColour;
		}
		return entry.rankColour;
	}

	/** Rank colours are opaque ARGB values; Java represents all of them as signed integers. */
	private static boolean storedColour(int colour) {
		return colour == -1 || (colour & 0xFF000000) == 0xFF000000;
	}

	private static void trim() {
		if (byUuid.size() <= LIMIT) return;
		ArrayList<Entry> oldest = new ArrayList<>(byUuid.values());
		oldest.sort(Comparator.comparingLong(entry -> entry.updatedAt));
		for (int index = 0; byUuid.size() > LIMIT && index < oldest.size(); index++) {
			Entry removed = oldest.get(index);
			byUuid.remove(removed.uuid);
			uuidByName.remove(removed.name.toLowerCase(Locale.ROOT), removed.uuid);
		}
		markDirty();
	}

	private static void markDirty() {
		dirty = true;
		saveAt = System.currentTimeMillis() + SAVE_DELAY_MILLIS;
	}

	private static void save() {
		if (writesBlocked) {
			if (!saveBlockReported) {
				saveBlockReported = true;
				OperationalLog.info("IDENTITY/SAVE_BLOCKED",
					"Identity source is protected; refusing to overwrite it");
			}
			try {
				JsonObject recovery = new JsonObject();
				recovery.addProperty("schemaVersion", SCHEMA);
				recovery.add("players", GSON.toJsonTree(byUuid));
				recoverySnapshot.write(SafariPaths.identityBackups(), GSON.toJson(recovery));
				dirty = false;
				StructuredDiagnostics.decision("identity", "recovery-saved", "entries=" + byUuid.size());
			} catch (Exception failed) {
				OperationalLog.error("IDENTITY/RECOVERY_SAVE", failed);
			}
			saveAt = System.currentTimeMillis() + 30_000L;
			return;
		}
		try {
			Path path = SafariPaths.playerIdentities();
			if (pendingVersionBackup && Files.isRegularFile(path)) {
				PersistentJson.backupBeforeVersion(path, SafariPaths.identityBackups(),
					"safariutils-player-identities", BuildVersion.releaseVersion());
				pendingVersionBackup = false;
			}
			if (pendingUpgradeBackup && Files.isRegularFile(path)) {
				PersistentJson.backupBeforeUpgrade(path, SafariPaths.identityBackups(),
					"safariutils-player-identities", SCHEMA);
				pendingUpgradeBackup = false;
			}
			JsonObject players = new JsonObject();
			JsonObject sourcePlayers = preservedRoot != null && preservedRoot.has("players")
				&& preservedRoot.get("players").isJsonObject()
				? preservedRoot.getAsJsonObject("players") : null;
			for (Entry entry : byUuid.values()) {
				JsonObject sourceEntry = sourcePlayers != null && sourcePlayers.has(entry.uuid)
					&& sourcePlayers.get(entry.uuid).isJsonObject()
					? sourcePlayers.getAsJsonObject(entry.uuid) : null;
				players.add(entry.uuid, PersistentJson.mergeRecord(sourceEntry,
					GSON.toJsonTree(entry).getAsJsonObject()));
			}
			JsonObject known = new JsonObject();
			known.addProperty("schemaVersion", Math.max(SCHEMA,
				PersistentJson.schema(preservedRoot, "schemaVersion")));
			known.add("players", players);
			JsonObject written = PersistentJson.mergeRecord(preservedRoot, known);
			AtomicFiles.writeString(path, GSON.toJson(written));
			preservedRoot = written;
			dirty = false;
			StructuredDiagnostics.decision("identity", "saved", "entries=" + byUuid.size());
		} catch (Exception failed) {
			OperationalLog.error("IDENTITY/SAVE", failed);
			saveAt = System.currentTimeMillis() + SAVE_DELAY_MILLIS;
		}
	}

	/** Recover readable fields without allowing Gson's truncating numeric coercions. */
	static Decoded decodeStore(JsonObject root) {
		Map<String, Entry> entries = new LinkedHashMap<>();
		if (!root.has("players")) return new Decoded(entries, 0);
		if (!root.get("players").isJsonObject()) return new Decoded(entries, 1);
		int rejected = 0;
		for (var row : root.getAsJsonObject("players").entrySet()) {
			if (!row.getValue().isJsonObject()) { rejected++; continue; }
			JsonObject safe = row.getValue().getAsJsonObject().deepCopy();
			for (String field : java.util.List.of("rankColour", "permanentRankColour", "permanentRankLevel",
					"rankUpdatedAt", "updatedAt")) {
				if (!safe.has(field)) continue;
				try {
					var value = safe.get(field);
					if (!value.isJsonPrimitive()) throw new IllegalArgumentException();
					long number = value.getAsBigDecimal().longValueExact();
					boolean timestamp = field.endsWith("At");
					if (timestamp ? number < 0 : number < Integer.MIN_VALUE || number > Integer.MAX_VALUE)
						throw new IllegalArgumentException();
					if (field.endsWith("Colour") && !storedColour((int) number)) throw new IllegalArgumentException();
					if (field.equals("permanentRankLevel") && (number < -1 || number > 4)) throw new IllegalArgumentException();
				} catch (RuntimeException invalid) { safe.remove(field); rejected++; }
			}
			if (safe.has("temporaryRank") && (!safe.get("temporaryRank").isJsonPrimitive()
				|| !safe.getAsJsonPrimitive("temporaryRank").isBoolean())) {
				safe.remove("temporaryRank"); rejected++;
			}
			try {
				Entry entry = GSON.fromJson(safe, Entry.class);
				normalize(entry, row.getKey());
				if (entry.uuid.isEmpty() || entry.name.isEmpty()) throw new IllegalArgumentException();
				Entry previous = entries.get(entry.uuid);
				if (previous == null || entry.updatedAt > previous.updatedAt) entries.put(entry.uuid, entry);
				if (previous != null) rejected++; // Preserve duplicate source records for explicit recovery.
			} catch (RuntimeException invalid) { rejected++; }
		}
		return new Decoded(Map.copyOf(entries), rejected);
	}

	record Decoded(Map<String, Entry> entries, int rejected) { }

	private static String compactUuid(String value) {
		if (value == null) return "";
		String compact = value.replace("-", "").toLowerCase(Locale.ROOT);
		return compact.matches("[0-9a-f]{32}") ? compact : "";
	}

	private static String validName(String value) {
		if (value == null) return "";
		String trimmed = value.trim();
		return trimmed.matches("[A-Za-z0-9_]{1,16}") ? trimmed : "";
	}
}
