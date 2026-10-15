package dev.serko.safariutils.backend;

import java.net.URI;

/** One trusted service origin for authentication, lookups and developer room transport. */
public final class BackendEndpoints {
	private static final String HOST = "api-dev.serkosea.dev";
	public static final URI CHALLENGE = URI.create("https://" + HOST + "/v1/auth/challenge");
	public static final URI SESSION = URI.create("https://" + HOST + "/v1/auth/session");
	public static final URI PLAYERS = URI.create("https://" + HOST + "/v1/safari/players");
	public static final URI SYNC = URI.create("wss://" + HOST + "/v1/sync");
	private BackendEndpoints() { }
}
