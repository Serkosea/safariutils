package dev.serko.safariutils.client;

public final class ReleaseMetadataRegression {
	public static int run() {
		var release = ReleaseMetadata.parse("{\"tag_name\":\"v3.0.0\",\"name\":\"SafariUtils\",\"body\":\"Notes\"}");
		check(release.version().equals("3.0.0") && release.notes().equals("Notes"), "complete release parsed");
		check(release.publishedAt().isEmpty(), "optional metadata can be absent");
		check(release.newerThan("2.9.0+mc26.2"), "major update");
		check(!release.newerThan("v3.0.0-developer"), "edition does not create false update");
		check(!release.newerThan("3.1.0"), "older release is not an update");
		check(!release.newerThan("unknown"), "unknown installed version does not invent an update");
		check(ReleaseMetadata.parse("{\"tag_name\":\"v3.1\"}").newerThan("3.0.9"), "two-part versions retain compatibility");
		for (String json : new String[]{"{}", "[]", "{\"tag_name\":\"garbage\"}",
			"{\"tag_name\":\"3.0.0\",\"body\":{}}", "{\"tag_name\":3}",
			"{\"tag_name\":\"999999999999.0.0\"}", "{\"tag_name\":\"3.0.0.\"}"}) {
			boolean rejected = false;
			try { ReleaseMetadata.parse(json); }
			catch (RuntimeException expected) { rejected = true; }
			check(rejected, "invalid metadata rejected before publication");
		}
		return 14;
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
