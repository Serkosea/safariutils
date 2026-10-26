package dev.serko.safariutils.backend;

import java.net.URI;

/** One trusted service origin for authentication, lookups and developer room transport. */
public final class BackendEndpoints {
	private static final String HOST = "api-dev.serkosea.dev";
	public static final URI UUID_SESSION = URI.create("https://" + HOST + "/v1/auth/uuid/session");
	public static final URI PLAYERS = URI.create("https://" + HOST + "/v1/safari/players");
	public static final URI AUCTIONS = URI.create("https://" + HOST + "/v2/market/auctions");
	public static final URI SYNC = URI.create("wss://" + HOST + "/v1/sync");
	private BackendEndpoints() { }
}
