package dev.serko.safariutils.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;

/** Bounded visual-flight evidence, not ownership attribution or an automatic physics fit. */
public final class CapsuleFlightDiagnostics {
    private static final Map<UUID,Flight> flights=new LinkedHashMap<>();
    private static final Set<UUID> seen=new HashSet<>();
    private static Object world;
    private static int recorded;
    private record Launch(long tick,Vec3 eye,Vec3 aim,Vec3 velocity,String capsule) { }
    private static Launch launch;
    public static void install() {
        if(!dev.serko.safariutils.BuildVersion.has(dev.serko.safariutils.BuildVersion.Capability.DEVELOPER_TOOLS))return;
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player,level,hand)->{
            if(level==Minecraft.getInstance().level && StructuredDiagnostics.enabled() && SafariLocation.inside()) {
                String name=SafariLocation.strip(player.getItemInHand(hand).getHoverName().getString());
                if(name.equals("Critter Capsule")||name.equals("Masterful Critter Capsule")) {
                    launch=new Launch(level.getGameTime(),player.getEyePosition(),player.getViewVector(1),player.getDeltaMovement(),name);
                    DebugLog.line("CAPSULE_THROW",JSON.toJson(launch));
                }
            }
            return net.minecraft.world.InteractionResult.PASS;
        });
    }
    private static final com.google.gson.Gson JSON=new com.google.gson.Gson();
    private record Sample(long tick,double x,double y,double z) { }
    private static final class Flight {
        final Display.ItemDisplay body;
        final Vec3 eye,aim,velocity;
        final long start;
        final String capsule;
        final List<Sample> samples=new ArrayList<>();
        Flight(Display.ItemDisplay body) {
            this.body=body;var p=Minecraft.getInstance().player;
            eye=launch.eye;aim=launch.aim;velocity=launch.velocity;capsule=launch.capsule;
            start=launch.tick;
        }
    }
    static void tick() {
        var client=Minecraft.getInstance();
        if(!StructuredDiagnostics.enabled()||!SafariLocation.inside()||client.level==null||client.player==null) {
            flights.clear();seen.clear();world=null;recorded=0;launch=null;return;
        }
        if(world!=client.level) {flights.clear();seen.clear();world=client.level;recorded=0;launch=null;}
        if(launch!=null&&client.level.getGameTime()-launch.tick<=40&&recorded<64&&flights.size()<4&&seen.size()<512) for(var entity:WorldEntities.current()) {
            if(entity instanceof Display.ItemDisplay display && display.distanceToSqr(client.player)<36
                &&!seen.contains(display.getUUID())&&CritterCapsules.matches(display)) {
                seen.add(display.getUUID());flights.put(display.getUUID(),new Flight(display));recorded++;
                if(flights.size()==4||recorded==64)break;
            }
        }
        long tick=client.level.getGameTime();
        var iterator=flights.values().iterator();
        while(iterator.hasNext()) {
            Flight f=iterator.next();
            if(!f.body.isRemoved()) f.samples.add(new Sample(tick-f.start,f.body.getX(),f.body.getY(),f.body.getZ()));
            if(f.body.isRemoved()||f.samples.size()>=80) {
                // Stay below the central logger's 4,000-character message bound.
                for(int offset=0;offset<f.samples.size();offset+=16) {
                    DebugLog.line("CAPSULE_FLIGHT",JSON.toJson(Map.of("id",f.body.getId(),"eye",f.eye,"aim",f.aim,
                        "velocity",f.velocity,"capsule",f.capsule,"part",offset/16,
                        "samples",f.samples.subList(offset,Math.min(offset+16,f.samples.size())),
                        "attribution","nearby visual capsule; thrower unverified")));
                }
                iterator.remove();
            }
        }
    }
    private CapsuleFlightDiagnostics() { }
}
