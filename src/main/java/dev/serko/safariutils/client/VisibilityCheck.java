package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Safe Mode visibility checks: the target must be inside the camera's field of view
 * and unobstructed. Stationary features may remember a successful check; moving
 * critter overlays must pass it while rendered.
 */
public final class VisibilityCheck {

	/** A generous half-angle that mainly rejects targets behind the player. */
	private static final double FOV_HALF_ANGLE_DEGREES = 60.0;
	private static final double FOV_COSINE = Math.cos(Math.toRadians(FOV_HALF_ANGLE_DEGREES));

	private VisibilityCheck() {
	}

	/** Whether {@code target} is currently visible to the player — in view, and unobstructed. */
	public static boolean canSee(Vec3 target) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) return false;

		Vec3 eye = client.player.getEyePosition();

		if (!inFieldOfView(eye, client.player.getViewVector(1.0f), target)) return false;

		// OUTLINE matches what the player sees; COLLIDER answers a movement question.
		ClipContext clip = new ClipContext(eye, target,
			ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, client.player);
		HitResult hit = client.level.clip(clip);
		return hit.getType() == HitResult.Type.MISS;
	}

	/** Whether an entity is on screen, even when its rendered nametag is visible through terrain. */
	public static boolean onScreen(Entity entity) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || entity == null) return false;
		Vec3 eye = client.player.getEyePosition();
		Vec3 target = entity.position().add(0, entity.getBbHeight() / 2.0, 0);
		return inFieldOfView(eye, client.player.getViewVector(1.0f), target);
	}

	/** A name the game deliberately renders counts as visible even through terrain. */
	public static boolean canSeeVisibleName(Entity entity) {
		return entity != null && entity.isCustomNameVisible() && onScreen(entity);
	}

	/** Same as {@link #canSee(Vec3)}, for the centre of a block position. */
	public static boolean canSee(BlockPos pos) {
		return canSee(Vec3.atCenterOf(pos));
	}

	/** Checks a point above a solid block so the block cannot occlude itself. */
	public static boolean canSeeSolidBlock(BlockPos pos) {
		return canSee(new Vec3(pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5));
	}

	/** Checks several sides because leaves often block a nest's center point. */
	public static boolean canSeeBeeNest(BlockPos pos) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.5;
		double z = pos.getZ() + 0.5;
		return canSee(new Vec3(x, y + 0.8, z))
			|| canSee(new Vec3(x + 0.8, y, z))
			|| canSee(new Vec3(x - 0.8, y, z))
			|| canSee(new Vec3(x, y, z + 0.8))
			|| canSee(new Vec3(x, y, z - 0.8));
	}

	/** Tries several open points around a hidden critter's fixed spawn block. */
	public static boolean canInspect(BlockPos pos) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.5;
		double z = pos.getZ() + 0.5;
		return canSee(new Vec3(x, y, z))
			|| canSee(new Vec3(x, y + 1.0, z))
			|| canSee(new Vec3(x + 0.8, y, z))
			|| canSee(new Vec3(x - 0.8, y, z))
			|| canSee(new Vec3(x, y, z + 0.8))
			|| canSee(new Vec3(x, y, z - 0.8));
	}

	/** Nearby foliage should not preserve a fixed waypoint after its area was inspected. */
	public static boolean canInspectCandidate(BlockPos pos) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) return false;
		if (canInspect(pos)) return true;
		Vec3 eye = client.player.getEyePosition();
		Vec3 target = Vec3.atCenterOf(pos);
		return eye.distanceToSqr(target) <= 36.0
			&& inFieldOfView(eye, client.player.getViewVector(1.0f), target);
	}

	/** A Hideonwall's painting is its visible cover, so inspecting that cover counts. */
	public static boolean canInspectPaintingCandidate(BlockPos pos) {
		if (canInspectCandidate(pos)) return true;
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return false;
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!EntityTypeIds.is(entity, "painting") || entity.blockPosition().distSqr(pos) > 9.0) continue;
			if (onScreen(entity)) return true;
		}
		return false;
	}

	/** Same as {@link #canSee(Vec3)}, for wherever an entity currently is. */
	public static boolean canSee(Entity entity) {
		Minecraft client = Minecraft.getInstance();
		// Minecraft successfully targeting the entity is stronger evidence than our
		// centre-point ray, which can clip the decorative block surrounding Duplico's
		// otherwise directly targetable interaction box.
		if (client.hitResult instanceof EntityHitResult hit
			&& hit.getEntity().getUUID().equals(entity.getUUID()) && onScreen(entity)) return true;
		return canSee(entity.position().add(0, entity.getBbHeight() / 2.0, 0));
	}

	/**
	 * Visibility for an interaction entity embedded in its own visible prop. The ray
	 * may hit that prop instead of reaching the invisible interaction-box centre; a
	 * hit immediately at the target is still direct sight, while an intervening wall
	 * farther away is not.
	 */
	public static boolean canSeeDecoratedEntity(Entity entity) {
		if (canSee(entity)) return true;
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || !onScreen(entity)) return false;
		Vec3 eye = client.player.getEyePosition();
		Vec3 target = entity.position().add(0, entity.getBbHeight() / 2.0, 0);
		HitResult hit = client.level.clip(new ClipContext(eye, target,
			ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, client.player));
		return hit.getType() == HitResult.Type.BLOCK
			&& hit.getLocation().distanceToSqr(target) <= 0.81;
	}

	private static boolean inFieldOfView(Vec3 eye, Vec3 lookDirection, Vec3 target) {
		Vec3 toTarget = target.subtract(eye);
		double distanceSq = toTarget.lengthSqr();
		if (distanceSq < 1.0e-6) return true; // Standing right on top of it.
		// Comparing cosines avoids normalizing two vectors and calling acos for every target.
		double denominator = Math.sqrt(lookDirection.lengthSqr() * distanceSq);
		return denominator > 0.0 && lookDirection.dot(toTarget) / denominator >= FOV_COSINE;
	}
}
