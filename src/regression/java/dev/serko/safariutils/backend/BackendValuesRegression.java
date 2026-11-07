package dev.serko.safariutils.backend;

import com.google.gson.JsonParser;
import dev.serko.safariutils.state.PartySnapshot;
import java.util.List;

public final class BackendValuesRegression {
	public static int run() throws Exception {
		int checks = 0;
        check(RemoteSession.retryAfterMillis("120") == 120000, "Retry-After seconds"); checks++;
        check(RemoteSession.retryAfterMillis("-10") == 0, "negative retry delay rejected"); checks++;
        check(RemoteSession.retryAfterMillis("999999999") == 86400000, "retry delay bounded"); checks++;
        check(RemoteSession.retryAfterMillis("invalid") == 0, "malformed retry falls back"); checks++;
        String date = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(120)
            .format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);
        long delay = RemoteSession.retryAfterMillis(date);
        check(delay > 118000 && delay <= 120000, "Retry-After HTTP date"); checks++;
        var client = new HypixelApiClient();
        var lastRequest = HypixelApiClient.class.getDeclaredField("lastManualProfileRequestAt");
        lastRequest.setAccessible(true);
        long now = System.currentTimeMillis();
        lastRequest.setLong(client, now);
        var cacheField = HypixelApiClient.class.getDeclaredField("players");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked") var cache = (java.util.Map<String, Object>) cacheField.get(client);
        var cacheType = Class.forName("dev.serko.safariutils.backend.HypixelApiClient$CachedPlayer");
        var constructor = cacheType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var data = new SafariPlayerData(java.util.Set.of("Wumpa"), 0, java.util.Map.of(), now, null);
        String cachedId = "a".repeat(32);
        cache.put(cachedId, constructor.newInstance(data, null, now));
        check(client.collectionManual(cachedId) == data, "cached lookup bypasses active cooldown"); checks++;
        check(lastRequest.getLong(client) == now, "cache reads do not extend cooldown"); checks++;
        for (String uncached : List.of("b".repeat(32), cachedId)) {
            cache.put(cachedId, constructor.newInstance(data, null, now - 300001));
            try { client.collectionManual(uncached); throw new AssertionError("Uncached lookup bypassed cooldown"); }
            catch (dev.serko.safariutils.api.LookupCooldownException expected) {
                check(expected.seconds() > 0 && expected.seconds() <= 10, "uncached lookup reports wait without sleeping or networking"); checks++;
            }
        }

		for (long colour : new long[]{0xff55ffffL, (int) 0xff55ffffL}) {
			var source = JsonParser.parseString("{\"rankColour\":" + colour
				+ ",\"permanentRankColour\":" + colour
				+ ",\"permanentRankLevel\":4,\"temporaryRank\":false,\"rankFetchedAt\":123}").getAsJsonObject();
			var rank = HypixelApiClient.RankSnapshot.decode(source);
			check(rank.colour() == 0xff55ffff && rank.permanentColour() == rank.colour()
				&& rank.permanentLevel() == 4 && !rank.temporary() && rank.fetchedAt() == 123, "ARGB forms agree");
			checks++;
		}
		var legacy = JsonParser.parseString("{\"rankColour\":4294945280,\"rankFetchedAt\":1}").getAsJsonObject();
		var rank = HypixelApiClient.RankSnapshot.decode(legacy);
		check(rank.temporary() && rank.permanentColour() == -1 && rank.permanentLevel() == -1,
			"legacy temporary rank never invents a permanent rank"); checks++;
		for (String field : List.of("\"rankColour\":1.5", "\"rankColour\":4294967296",
			"\"rankColour\":-2147483649", "\"rankColour\":\"42\"", "\"rankColour\":null",
			"\"permanentRankLevel\":5", "\"permanentRankLevel\":-2", "\"permanentRankLevel\":1.5",
			"\"temporaryRank\":\"false\"", "\"temporaryRank\":1", "\"rankFetchedAt\":-1",
			"\"rankFetchedAt\":1.5", "\"rankFetchedAt\":9223372036854775808")) {
			var source = legacy.deepCopy();
			var invalid = JsonParser.parseString("{" + field + "}").getAsJsonObject();
			invalid.entrySet().forEach(entry -> source.add(entry.getKey(), entry.getValue()));
			try { HypixelApiClient.RankSnapshot.decode(source); throw new AssertionError("Accepted " + field); }
			catch (IllegalArgumentException | ArithmeticException expected) { checks++; }
		}
		for (String value : List.of("1.5", "\"1\"", "null", "true", "9223372036854775808")) {
			try { BackendValues.integer(JsonParser.parseString("{\"schema\":" + value + "}").getAsJsonObject(), "schema");
				throw new AssertionError("Invalid schema accepted"); }
			catch (IllegalArgumentException | ArithmeticException expected) { checks++; }
		}
		return checks + entryRoster();
	}

	private static int entryRoster() {
		var selection = new BackendSparklingProvider.EntryRoster();
		long entered = 1_792_100_766_000L;
		var solo = party(entered + 359, PartySnapshot.Status.SOLO, 1, "Host");
		var guests = party(entered + 20_000, PartySnapshot.Status.PARTY, 2, "Host", "Guest");
		selection.observe(party(entered - 1, PartySnapshot.Status.SOLO, 1, "Old"), entered);
		check(selection.claim(true) == null, "previous visit roster cannot start loading");
		selection.observe(party(entered + 100, PartySnapshot.Status.UNKNOWN, 1, "Host"), entered);
		check(selection.claim(true) == null, "unknown party cannot start loading");
		selection.observe(party(entered + 200, PartySnapshot.Status.TRANSITIONING, 1, "Host"), entered);
		check(selection.claim(true) == null, "transitioning party cannot start loading");
		selection.observe(party(entered + 250, PartySnapshot.Status.PARTY, 2, "Host"), entered);
		check(selection.claim(true) == null, "incomplete party cannot start loading");
		selection.observe(solo, entered);
		check(selection.claim(true) == solo, "active ticket run loads immediately on central solo confirmation");
		selection.observe(guests, entered);
		for (int i = 0; i < 100; i++) {
			check(selection.claim(true) == null, "late guests and repeated ticks cannot reload or swap composition");
		}
		selection.reset();
		selection.observe(solo, entered);
		check(selection.claim(false) == null, "scouting visit does not load before ticket activation");
		selection.observe(guests, entered);
		check(selection.claim(true) == solo, "pre-ticket confirmation survives later membership changes");
		selection.reset();
		selection.observe(guests, entered + 10_000);
		check(selection.claim(true) == guests, "new visit selects its new composition");
		selection.reset();
		selection.observe(guests, entered + 10_000);
		check(selection.claim(true) == guests, "same composition on another visit reaches existing cache path");
		selection.reset();
		selection.observe(party(entered + 1, PartySnapshot.Status.PARTY, 5,
			"Host", "A", "B", "C", "D"), entered);
		selection.observe(solo, entered);
		check(selection.claim(true) == null, "oversized entry roster cannot become a smaller late composition");
		return 110;
	}

	private static PartySnapshot party(long capturedAt, PartySnapshot.Status status,
			int expected, String... names) {
		return new PartySnapshot(1, capturedAt, status, true, expected,
			java.util.Arrays.stream(names).map(name -> new PartySnapshot.Member(name, "")).toList(),
			0, List.of());
	}
	private static void check(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}
}
