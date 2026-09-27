package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/** Shared custom marker dimensions for live hitboxes and frozen recatch pins. */
final class CritterMarkerGeometry {
	private static final Set<String> BLOCK_SIZED = Set.of(
		"Duplico", "Hideonwall", "Hideonfloor");

	private CritterMarkerGeometry() {
	}

	static AABB blockSized(Vec3 base) {
		return new AABB(base.x - 0.5, base.y, base.z - 0.5,
			base.x + 0.5, base.y + 1.0, base.z + 0.5);
	}

	static boolean usesBlockSize(Critter critter) {
		return critter != null && BLOCK_SIZED.contains(critter.name());
	}

	/** Applies the same presentation dimensions after a moving body becomes a fixed pin. */
	static AABB recatch(Critter critter, AABB nativeBox) {
		if (!usesBlockSize(critter)) return nativeBox;
		return blockSized(new Vec3(
			(nativeBox.minX + nativeBox.maxX) * 0.5,
			nativeBox.minY,
			(nativeBox.minZ + nativeBox.maxZ) * 0.5));
	}
}
