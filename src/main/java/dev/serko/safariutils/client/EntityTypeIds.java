package dev.serko.safariutils.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Stable registry-ID checks across EntityType constant moves in Minecraft 26.2. */
final class EntityTypeIds {
	private EntityTypeIds() {
	}

	static boolean is(Entity entity, String path) {
		return is(entity.getType(), path);
	}

	static boolean is(EntityType<?> type, String path) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath().equals(path);
	}
}
