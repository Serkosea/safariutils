package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/** Shared custom marker dimensions for live hitboxes and frozen recatch pins. */
final class CritterMarkerGeometry {
	private static final double DRIFTLING_SIZE = 0.9;
	private static final double DRIFTLING_Y_OFFSET = -0.25;
	private static final Set<String> BLOCK_SIZED = Set.of(
		"Duplico", "Hideonwall", "Hideonfloor", "Driftling");

	private CritterMarkerGeometry() {
	}

	static AABB blockSized(Vec3 base) {
		return new AABB(base.x - 0.5, base.y, base.z - 0.5,
			base.x + 0.5, base.y + 1.0, base.z + 0.5);
	}

	static boolean usesBlockSize(Critter critter) {
		return critter != null && BLOCK_SIZED.contains(critter.name());
	}

	/** Species-specific presentation box at full size. */
	static AABB presented(Critter critter, Vec3 base) {
		if (critter != null && "Driftling".equals(critter.name())) {
			double half = DRIFTLING_SIZE * 0.5;
			double bottom = base.y + DRIFTLING_Y_OFFSET;
			return new AABB(base.x - half, bottom, base.z - half,
				base.x + half, bottom + DRIFTLING_SIZE, base.z + half);
		}
		return blockSized(base);
	}

	/** Applies a uniform capture animation without changing the species anchor. */
	static AABB presented(Critter critter, Vec3 base, double scale) {
		AABB full = presented(critter, base);
		double clamped = Math.max(0.0, Math.min(1.0, scale));
		double centreX = (full.minX + full.maxX) * 0.5;
		double centreZ = (full.minZ + full.maxZ) * 0.5;
		double halfWidth = full.getXsize() * clamped * 0.5;
		return new AABB(centreX - halfWidth, full.minY, centreZ - halfWidth,
			centreX + halfWidth, full.minY + full.getYsize() * clamped,
			centreZ + halfWidth);
	}

	/** Applies the same presentation dimensions after a moving body becomes a fixed pin. */
	static AABB recatch(Critter critter, AABB nativeBox) {
		if (!usesBlockSize(critter)) return nativeBox;
		return presented(critter, new Vec3(
			(nativeBox.minX + nativeBox.maxX) * 0.5,
			nativeBox.minY,
			(nativeBox.minZ + nativeBox.maxZ) * 0.5));
	}
}
