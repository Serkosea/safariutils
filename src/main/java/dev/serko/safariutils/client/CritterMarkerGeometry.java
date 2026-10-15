package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/** Shared custom marker dimensions for live hitboxes and frozen recatch pins. */
final class CritterMarkerGeometry {
	private record Shape(double width, double height, double yOffset) { }
	private static final Shape BLOCK = new Shape(1.0, 1.0, 0.0);
	/** Stable species geometry is resolved once rather than rediscovered every frame. */
	private static final Map<String, Shape> SHAPES = Map.of(
		"Duplico", BLOCK,
		"Hideonwall", BLOCK,
		"Hideonfloor", BLOCK,
		"Driftling", new Shape(0.9, 0.9, -0.25));

	private CritterMarkerGeometry() {
	}

	static AABB blockSized(Vec3 base) {
		return new AABB(base.x - 0.5, base.y, base.z - 0.5,
			base.x + 0.5, base.y + 1.0, base.z + 0.5);
	}

	static boolean usesBlockSize(Critter critter) {
		return shape(critter) != null;
	}

	/** Species-specific presentation box at full size. */
	static AABB presented(Critter critter, Vec3 base) {
		Shape shape = shape(critter);
		if (shape == null) shape = BLOCK;
		double half = shape.width() * 0.5;
		double bottom = base.y + shape.yOffset();
		return new AABB(base.x - half, bottom, base.z - half,
			base.x + half, bottom + shape.height(), base.z + half);
	}

	/** Applies a uniform capture animation without changing the species anchor. */
	static AABB presented(Critter critter, Vec3 base, double scale) {
		return scaled(presented(critter, base), scale);
	}

	/** Shared bottom-anchored scaling for live special shapes and capture presentations. */
	static AABB scaled(AABB full, double scale) {
		double clamped = Math.max(0.0, Math.min(1.0, scale));
		double centreX = (full.minX + full.maxX) * 0.5;
		double centreZ = (full.minZ + full.maxZ) * 0.5;
		double halfWidth = full.getXsize() * clamped * 0.5;
		double halfDepth = full.getZsize() * clamped * 0.5;
		return new AABB(centreX - halfWidth, full.minY, centreZ - halfDepth,
			centreX + halfWidth, full.minY + full.getYsize() * clamped,
			centreZ + halfDepth);
	}

	/** Applies the same presentation dimensions after a moving body becomes a fixed pin. */
	static AABB recatch(Critter critter, AABB nativeBox) {
		if (!usesBlockSize(critter)) return nativeBox;
		return presented(critter, new Vec3(
			(nativeBox.minX + nativeBox.maxX) * 0.5,
			nativeBox.minY,
			(nativeBox.minZ + nativeBox.maxZ) * 0.5));
	}

	/** Moves a full-size capture box without inheriting the body's native shrink. */
	static AABB captureFrame(Critter critter, AABB stable, AABB body,
			double offsetX, double offsetY, double offsetZ, double scale) {
		AABB followed = body == null ? stable : follow(critter, stable, body);
		return scaled(followed.move(offsetX, offsetY, offsetZ), scale);
	}

	/** Moves a full-size capture box without inheriting the body's native shrink. */
	static AABB follow(Critter critter, AABB stable, AABB body) {
		Shape shape = shape(critter);
		double bottom = body.minY + (shape == null ? 0.0 : shape.yOffset());
		double x = (body.minX + body.maxX) * 0.5;
		double z = (body.minZ + body.maxZ) * 0.5;
		double halfX = stable.getXsize() * 0.5;
		double halfZ = stable.getZsize() * 0.5;
		return new AABB(x - halfX, bottom, z - halfZ,
			x + halfX, bottom + stable.getYsize(), z + halfZ);
	}

	private static Shape shape(Critter critter) {
		return critter == null ? null : SHAPES.get(critter.name());
	}
}
