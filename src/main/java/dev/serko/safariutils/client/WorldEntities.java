package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One immutable view of the client world's rendered entities per game tick.
 *
 * <p>Several independent Safari trackers inspect the same world entity collection.
 * Sharing the traversal keeps those trackers independent while avoiding repeated
 * walks of Minecraft's rendering collection during the same tick.
 */
final class WorldEntities {
	private record Snapshot(List<Entity> entities, Map<UUID, Entity> byId) { }
	private static final TickCache<Snapshot> CACHE = new TickCache<>();

	private WorldEntities() {
	}

	static List<Entity> current() {
		return CACHE.get(WorldEntities::snapshot).entities();
	}

	/** Constant-time lookup shared by moving presentation projections. */
	static Entity find(UUID entityId) {
		return entityId == null ? null : CACHE.get(WorldEntities::snapshot).byId().get(entityId);
	}

	private static Snapshot snapshot() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return new Snapshot(List.of(), Map.of());
		List<Entity> entities = new ArrayList<>();
		Map<UUID, Entity> byId = new HashMap<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			entities.add(entity);
			byId.put(entity.getUUID(), entity);
		}
		return new Snapshot(List.copyOf(entities), Map.copyOf(byId));
	}
}
