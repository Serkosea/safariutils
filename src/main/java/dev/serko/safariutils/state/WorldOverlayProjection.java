package dev.serko.safariutils.state;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
/** Perspective-equivalent projection for through-wall static overlays beyond the world far plane. */
public final class WorldOverlayProjection {
    private WorldOverlayProjection() { }
    public static double scale(Vec3 anchor,Vec3 camera) {return Math.min(1,96/Math.max(1,anchor.distanceTo(camera)));}
    public static AABB project(AABB box,Vec3 camera,double scale) {
        if(scale>=1)return box;
        return new AABB(camera.x+(box.minX-camera.x)*scale,camera.y+(box.minY-camera.y)*scale,camera.z+(box.minZ-camera.z)*scale,
            camera.x+(box.maxX-camera.x)*scale,camera.y+(box.maxY-camera.y)*scale,camera.z+(box.maxZ-camera.z)*scale);
    }
}
