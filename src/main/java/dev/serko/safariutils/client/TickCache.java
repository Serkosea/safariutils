package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;

import java.util.function.Supplier;

/** Reuses derived client state for render frames within the same game tick. */
final class TickCache<T> {
	private Object level;
	private long tick = Long.MIN_VALUE;
	private T value;
	private boolean initialized;

	T get(Supplier<T> factory) {
		Minecraft client = Minecraft.getInstance();
		long currentTick = client.level == null ? Long.MIN_VALUE : client.level.getGameTime();
		if (!initialized || client.level != level || currentTick != tick) {
			initialized = true;
			level = client.level;
			tick = currentTick;
			value = factory.get();
		}
		return value;
	}
}
