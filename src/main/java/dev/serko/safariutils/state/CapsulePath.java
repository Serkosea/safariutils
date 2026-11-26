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
        return ring(contact,normal,0);
    }
    // Keep saved shape IDs stable. ID 7 was Ripple and falls back to Contour.
    private static final java.util.List<Integer> IDS = java.util.List.of(0,1,2,3,4,5,6,8);
    private static final java.util.List<String> NAMES = java.util.List.of("Contour","Circle","Off","Diamond","Clover","Star","Sunburst","Heart");
    public static String markerName(int id) { return NAMES.get(Math.max(0, IDS.indexOf(id))); }
    public static int nextMarker(int id) { return IDS.get((Math.max(0,IDS.indexOf(id))+1)%IDS.size()); }

    private static final java.util.List<java.util.List<Vec3>> OUTLINES = IDS.stream().map(CapsulePath::outline).toList();
    public static java.util.List<Vec3> markerOutline(int id) {
        return OUTLINES.get(Math.max(0, IDS.indexOf(id)));
    }
    private static java.util.List<Vec3> outline(int shape) {
        if (shape == 2) return java.util.List.of();
        int sides = shape == 5 ? 10 : shape == 3 ? 4 : 48;
        double minX=1, maxX=-1, minY=1, maxY=-1;
        var points = new java.util.ArrayList<Vec3>(sides + 1);
        for (int i=0; i<sides; i++) {
            double t = 2*Math.PI*i/sides;
            double sin = Math.sin(t), cos = Math.cos(t);
            double radius = switch (shape) {
                case 0 -> .86*(.94+.06*Math.cos(4*t));
                case 4 -> .80+.20*Math.cos(3*t);
                case 5 -> i%2 == 0 ? 1 : .382;
                case 6 -> .78+.22*Math.cos(8*t);
                default -> 1;
            };
            double x = radius*sin, y = radius*cos;
            if (shape == 8) {
                x = sin*sin*sin;
                y = (13*cos-5*Math.cos(2*t)-2*Math.cos(3*t)-Math.cos(4*t)+2)/17;
            }
            points.add(new Vec3(x,y,0));
            minX=Math.min(minX,x); maxX=Math.max(maxX,x);
            minY=Math.min(minY,y); maxY=Math.max(maxY,y);
        }
        // Equal longest dimension preserves the distinctive proportions of each outline.
        double scale = (.4 * 2 / 3 * .8 * .9 * 2) / Math.max(maxX-minX,maxY-minY);
        points.replaceAll(point -> point.scale(scale));
        points.add(points.getFirst());
        return java.util.List.copyOf(points);
    }
    public static java.util.List<Vec3> ring(Vec3 contact,Vec3 normal,int shape){
        return ring(contact,normal,shape,new Vec3(1,0,0),normal.normalize(),100);
    }
    public static java.util.List<Vec3> ring(Vec3 contact,Vec3 normal,int shape,Vec3 right,Vec3 towardCamera,int sizePercent){
        if(shape==2)return java.util.List.of();
        Vec3 n=normal.normalize();
        // Rotate the camera frame onto the contact plane without adding roll.
        Vec3 axis = towardCamera.cross(n);
        double denominator = 1 + towardCamera.dot(n);
        Vec3 rotated = denominator > 1e-6 ? right.add(axis.cross(right))
            .add(axis.cross(axis.cross(right)).scale(1 / denominator)) : right;
        Vec3 u = rotated.subtract(n.scale(rotated.dot(n))).normalize();
        if (u.lengthSqr() < .5) u = n.cross(new Vec3(0,1,0)).normalize();
        Vec3 v=n.cross(u), center=contact.add(n.scale(.015));
        double scale = sizeScale(sizePercent);
        var outline = markerOutline(shape);
        var points = new java.util.ArrayList<Vec3>(outline.size());
        for (Vec3 vertex : outline)
            points.add(center.add(u.scale(vertex.x*scale)).add(v.scale(vertex.y*scale)));
        return java.util.List.copyOf(points);
    }
    public static final int MIN_SIZE=50, MAX_SIZE=150;
    public static double sizeScale(int percent) { return Math.clamp(percent,MIN_SIZE,MAX_SIZE)/100.0; }

    public static Vec3 point(Vec3 eye,Vec3 aim,double parameter) {
        if(!finite(eye)||!finite(aim)||!Double.isFinite(parameter)||parameter<START||parameter>END||aim.lengthSqr()<1e-12)return null;
        Vec3 direction=aim.normalize();
        return eye.add(direction.scale(.8482482249942948*parameter)).add(0,-.010042776655590361*parameter*(parameter-1),0);
    }
    private static boolean finite(Vec3 vector) {return Double.isFinite(vector.x)&&Double.isFinite(vector.y)&&Double.isFinite(vector.z);}
    private CapsulePath() { }
}
