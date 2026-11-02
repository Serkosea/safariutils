package dev.serko.safariutils.client;

import dev.serko.safariutils.state.CapsulePath;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** Tick-cached targets, aim-responsive collision work and immutable render frames. Never sends an action. */
public final class CapsulePrediction {
    record Target(AABB box,String name) { }
    public record Frame(Object level,List<Vec3> points,List<Vec3> ring,Vec3 normal,String contact,boolean entity,boolean estimated) { }
    private static Frame current;
    private static Object level;
    private static List<Target> targets=List.of();
    private static Vec3 cachedEye,cachedAim;
    private static long revision,cachedRevision=-1;
    private static String diagnostic="";
    private CapsulePrediction() { }
    public static Frame frame(float partialTick) {
        if(!eligible())return null;
        var player=Minecraft.getInstance().player;
        Vec3 eye=player.getEyePosition(partialTick),aim=player.getViewVector(1);
        if(current==null||cachedRevision!=revision||!eye.equals(cachedEye)||!aim.equals(cachedAim)) {
            long started=PerformanceDiagnostics.begin();
            try { solve(eye,aim);cachedEye=eye;cachedAim=aim;cachedRevision=revision; }
            finally { PerformanceDiagnostics.end("render/capsule-prediction",started); }
        }
        return current;
    }
    private static boolean eligible() {
        var client=Minecraft.getInstance();
        if(!HypixelConnection.active()||!SafariLocation.inside()||!ConfigManager.get().gameplay.capsulePrediction
            ||client.level==null||client.player==null||ClientCompat.screen()!=null||ClientCompat.hudHidden()
            ||client.player.isPassenger()||client.player.isInLava()||client.player.getAbilities().flying)return false;
        String name=SafariLocation.strip(client.player.getMainHandItem().getHoverName().getString());
        return name.equals("Critter Capsule")||name.equals("Masterful Critter Capsule");
    }
    public static void tick() {
        var client=Minecraft.getInstance();
        if(level!=client.level) {level=client.level;current=null;cachedEye=null;cachedAim=null;}
        revision++;
        CapsuleFlightDiagnostics.tick();
        if(!eligible()) {current=null;targets=List.of();return;}
        var player=client.player;
        List<Target> next=new ArrayList<>();Set<UUID> seen=new HashSet<>();
        for(var sighting:CritterEntities.all()) {
            var body=sighting.mob();
            if(body==null||body.isRemoved()||body.distanceToSqr(player)>128*128||!seen.add(body.getUUID()))continue;
            if(SafeMode.hiddenCritter(sighting.critter(),sighting.sparkling())
                && !VisibilityCheck.canSee(body.getBoundingBox().getCenter())) continue;
            next.add(new Target(body.getBoundingBox(),sighting.critter().name()));
        }
        targets=List.copyOf(next);
    }
    private static void solve(Vec3 eye,Vec3 aim) {
        var client=Minecraft.getInstance();var player=client.player;
        boolean estimated=true, knownTerrain=true;
        Vec3 endpoint=null;
        double end=CapsulePath.END;String contact="Range limit";boolean entity=false;Vec3 normal=aim.scale(-1);
        Vec3 from=CapsulePath.point(eye,aim,CapsulePath.START); if(from==null){current=null;return;}
        for(int index=1;index<CapsulePath.SAMPLES;index++) {
            double n=CapsulePath.START+(CapsulePath.END-CapsulePath.START)*index/(CapsulePath.SAMPLES-1);
            double n0=CapsulePath.START+(CapsulePath.END-CapsulePath.START)*(index-1)/(CapsulePath.SAMPLES-1);
            Vec3 to=CapsulePath.point(eye,aim,n);
            if(to==null) {end=n0;break;}
            if(to.y<client.level.getMinY()) {end=n0;contact="World boundary";break;}
            if(!client.level.hasChunkAt(BlockPos.containing(to))) {knownTerrain=false;contact="Unloaded terrain";from=to;continue;}
            if(!knownTerrain) {from=to;continue;}
            var hit=client.level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
            Vec3 collision=hit.getType()==HitResult.Type.MISS?null:hit.getLocation();
            double distance=collision==null?Double.POSITIVE_INFINITY:from.distanceToSqr(collision);
            if(collision!=null) {var face=hit.getDirection();normal=new Vec3(face.getStepX(),face.getStepY(),face.getStepZ());contact="Terrain";}
            for(Target target:targets) {
                Vec3 candidate=CapsulePath.contact(target.box,from,to);
                if(candidate!=null&&from.distanceToSqr(candidate)<distance) {collision=candidate;distance=from.distanceToSqr(candidate);contact=target.name;entity=true;normal=aim.scale(-1);}
            }
            if(collision!=null) {endpoint=collision;double fraction=Math.sqrt(distance/Math.max(1e-12,from.distanceToSqr(to)));end=n0+(n-n0)*Math.clamp(fraction,0,1);break;}
            from=to;
        }
        List<Vec3> points=new ArrayList<>(CapsulePath.SAMPLES);
        for(int i=0;i<CapsulePath.SAMPLES;i++) points.add(CapsulePath.point(eye,aim,CapsulePath.START+(end-CapsulePath.START)*i/(CapsulePath.SAMPLES-1)));
        if(endpoint!=null) points.set(points.size()-1,endpoint);
        current=new Frame(client.level,List.copyOf(points),endpoint==null?List.of():CapsulePath.ring(endpoint,normal),normal,contact,entity,estimated);
        String next=contact+"/"+estimated;
        if(cachedRevision!=revision&&!next.equals(diagnostic)) {diagnostic=next;DebugLog.line("CAPSULE_PREDICTION","contact="+contact+" estimated="+estimated+" targets="+targets.size()+" radius="+CapsulePath.CONTACT_RADIUS);}
    }
}
