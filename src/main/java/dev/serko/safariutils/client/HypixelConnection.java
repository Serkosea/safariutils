package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.Locale;
import java.util.Objects;

/** Authoritative boundary for features that read from or send commands to Hypixel. */
public final class HypixelConnection {
	private static String checkedAddress;
	private static boolean active;

	private HypixelConnection() {
	}

	/** Refreshes the cached answer and returns whether the active play connection is Hypixel. */
	public static boolean refresh() {
		Minecraft client = Minecraft.getInstance();
		ServerData server = client.getCurrentServer();
		String address = client.getConnection() == null || server == null ? null : server.ip;
		if (!Objects.equals(address, checkedAddress)) {
			checkedAddress = address;
			active = isHypixelAddress(address);
		}
		if (client.getConnection() == null) active = false;
		return active;
	}

	/** Fast cached check for hot paths; {@link #refresh()} runs at every client tick. */
	public static boolean active() {
		return active && Minecraft.getInstance().getConnection() != null;
	}

	/** Clears the cached connection immediately when Fabric reports a disconnect. */
	public static void onDisconnect() {
		checkedAddress = null;
		active = false;
	}

	static boolean isHypixelAddress(String address) {
		if (address == null || address.isBlank()) return false;
		String host = address.trim().toLowerCase(Locale.ROOT);
		int scheme = host.indexOf("://");
		if (scheme >= 0) host = host.substring(scheme + 3);
		int path = host.indexOf('/');
		if (path >= 0) host = host.substring(0, path);
		// Server-list addresses are normally host:port. Do not reinterpret IPv6 text.
		int colon = host.lastIndexOf(':');
		if (colon > 0 && host.indexOf(':') == colon) host = host.substring(0, colon);
		while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
		return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
	}
}
