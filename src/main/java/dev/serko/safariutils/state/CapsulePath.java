package dev.serko.safariutils.state;
import net.minecraft.world.phys.Vec3;
/** Measured ordinary-capsule curve, extrapolated beyond CALIBRATED_END; not a guarantee of server contact. */
public final class CapsulePath {
    public static final double START=0, CALIBRATED_END=17.68350296294565, END=120;
    public static final int SAMPLES=161;
    // Conservative swept-box estimate, not a measured server catch radius.
    public static final double CONTACT_RADIUS=.25;
    public static Vec3 contact(net.minecraft.world.phys.AABB body, Vec3 from, Vec3 to) {
        var swept=body.inflate(CONTACT_RADIUS);
        return swept.contains(from)?from:swept.clip(from,to).orElse(null);
    }
    /** Cached by the prediction frame; orient the ring to the actual surface normal. */
    public static java.util.List<Vec3> ring(Vec3 contact, Vec3 normal) {
        Vec3 n=normal.normalize();
        Vec3 u=n.cross(Math.abs(n.y)<.9?new Vec3(0,1,0):new Vec3(1,0,0)).normalize();
        Vec3 v=n.cross(u), center=contact.add(n.scale(.015));
        var points=new java.util.ArrayList<Vec3>(33);
        for(int i=0;i<32;i++) {
            double angle=i*Math.PI/16;
            double radius=(.4*2/3)*(.94+.06*Math.cos(4*angle));
            points.add(center.add(u.scale(radius*Math.cos(angle))).add(v.scale(radius*Math.sin(angle))));
        }
        points.add(points.getFirst());
        return java.util.List.copyOf(points);
    }
    public static Vec3 point(Vec3 eye,Vec3 aim,double parameter) {
        if(!finite(eye)||!finite(aim)||!Double.isFinite(parameter)||parameter<START||parameter>END||aim.lengthSqr()<1e-12)return null;
        Vec3 direction=aim.normalize();
        return eye.add(direction.scale(.8482482249942948*parameter)).add(0,-.010042776655590361*parameter*(parameter-1),0);
    }
    private static boolean finite(Vec3 vector) {return Double.isFinite(vector.x)&&Double.isFinite(vector.y)&&Double.isFinite(vector.z);}
    private CapsulePath() { }
}
