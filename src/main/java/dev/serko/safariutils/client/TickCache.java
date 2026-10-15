package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;

import java.util.function.Supplier;

/** Reuses derived client state for render frames within the same game tick. */
final class TickCache<T> {
	private Object level;
	private long tick = Long.MIN_VALUE;
	private long configRevision = Long.MIN_VALUE;
	private T value;
	private boolean initialized;
	private long invalidation;

	T get(Supplier<T> factory) {
		Minecraft client = Minecraft.getInstance();
		long currentTick = client.level == null ? Long.MIN_VALUE : client.level.getGameTime();
		long currentConfigRevision = ConfigManager.revision();
		return get(client.level, currentTick, currentConfigRevision, factory);
	}

	/** Explicit context keeps cache publication testable without a running client. */
	T get(Object currentLevel, long currentTick, long currentConfigRevision, Supplier<T> factory) {
		if (!initialized || currentLevel != level || currentTick != tick
			|| currentConfigRevision != configRevision) {
			long generation = invalidation;
			T fresh = factory.get();
			// An invalidation raised during construction must survive publication.
			if (generation != invalidation) return fresh;
			initialized = true;
			level = currentLevel;
			tick = currentTick;
			configRevision = currentConfigRevision;
			value = fresh;
		}
		return value;
	}

	void invalidate() {
		invalidation++;
		initialized = false;
		level = null;
		tick = Long.MIN_VALUE;
		configRevision = Long.MIN_VALUE;
		value = null;
	}
}
