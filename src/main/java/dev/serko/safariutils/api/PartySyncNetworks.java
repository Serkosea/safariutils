package dev.serko.safariutils.api;

import java.util.function.Consumer;

/** Failure-isolated facade around the optional authenticated sync transport. */
public final class PartySyncNetworks {
	private static final ServiceAccess<PartySyncNetworkProvider> PROVIDER =
		ServiceAccess.load(PartySyncNetworkProvider.class, "SYNC/NETWORK",
			dev.serko.safariutils.BuildVersion.has(dev.serko.safariutils.BuildVersion.Capability.REMOTE_PARTY_SYNC));

	private PartySyncNetworks() { }

	public static boolean supported() {
		return PROVIDER.read("supported", PartySyncNetworkProvider::supported, false);
	}

	public static void tick(PartySyncNetworkProvider.Context context,
			Consumer<PartySyncNetworkProvider.Message> receiver) {
		PROVIDER.run("tick", provider -> provider.tick(context, receiver));
	}

	public static boolean connected() {
		return PROVIDER.read("connected", PartySyncNetworkProvider::connected, false);
	}

	public static void send(long sequence, String type, String payload) {
		PROVIDER.run("send", provider -> provider.send(sequence, type, payload));
	}

	public static void disconnect() {
		PROVIDER.run("disconnect", PartySyncNetworkProvider::disconnect);
	}

	public static void shutdown() {
		PROVIDER.run("shutdown", PartySyncNetworkProvider::shutdown);
	}
}
