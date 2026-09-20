package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * One immutable view of the client world's rendered entities per game tick.
 *
 * <p>Several independent Safari trackers inspect the same world entity collection.
 * Sharing the traversal keeps those trackers independent while avoiding repeated
 * walks of Minecraft's rendering collection during the same tick.
 */
final class WorldEntities {
	private static final TickCache<List<Entity>> CACHE = new TickCache<>();

	private WorldEntities() {
	}

	static List<Entity> current() {
		return CACHE.get(WorldEntities::snapshot);
	}

	private static List<Entity> snapshot() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return List.of();
		List<Entity> entities = new ArrayList<>();
		for (Entity entity : client.level.entitiesForRendering()) entities.add(entity);
		return List.copyOf(entities);
	}
}
