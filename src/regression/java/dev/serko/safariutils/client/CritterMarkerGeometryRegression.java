package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.SafariBiome;
import net.minecraft.world.phys.AABB;

/** Exercises the geometry used by scan, tick and interpolated capture rendering. */
public final class CritterMarkerGeometryRegression {
	public static int run() {
		int checks = 0;
		for (String name : new String[] {"Driftling", "Duplico", "Hideonfloor", "Hideonwall", "Bloodbat"}) {
			Critter critter = new Critter(name, SafariBiome.CAVERN, Critter.Rarity.RARE, 1);
			AABB body = new AABB(-0.7, 10, -0.4, 0.7, 12, 0.4);
			AABB full = CritterMarkerGeometry.recatch(critter, body);
			double offset = name.equals("Driftling") ? -0.25 : 0;
			AABB previous = full;
			for (int step = 0; step <= 20; step++) {
				double scale = 1.0 - step / 20.0;
				AABB nativeShrink = new AABB(step - 0.7 * scale, 10 + step * 0.1,
					-0.4 * scale, step + 0.7 * scale, 10 + step * 0.1 + 2 * scale, 0.4 * scale);
				AABB followed = CritterMarkerGeometry.follow(critter, previous, nativeShrink);
				check(close(followed.getXsize(), full.getXsize())
					&& close(followed.getYsize(), full.getYsize())
					&& close(followed.getZsize(), full.getZsize()), "native shrink changed capture dimensions");
				check(close(followed.minY, nativeShrink.minY + offset)
					&& close(followed.getCenter().x, step), "species anchor lost or offset accumulated");
				AABB repeated = CritterMarkerGeometry.follow(critter, followed, nativeShrink);
				check(close(followed.minX, repeated.minX) && close(followed.minY, repeated.minY)
					&& close(followed.minZ, repeated.minZ) && close(followed.maxX, repeated.maxX)
					&& close(followed.maxY, repeated.maxY) && close(followed.maxZ, repeated.maxZ),
					"repeated tick/scan projection changed geometry");
				previous = followed;
				checks += 3;
			}
			// Native size changes only on ticks; visual scale must still progress on every frame.
			for (int frame = 0; frame <= 110; frame++) {
				double progress = frame / 110.0;
				double stepped = 1.0 - Math.floor(progress * 11) / 11;
				AABB nativeBox = new AABB(4 - stepped, 12, -stepped, 4 + stepped, 12 + stepped, stepped);
				AABB followed = CritterMarkerGeometry.follow(critter, full, nativeBox);
				AABB rendered = CritterMarkerGeometry.scaled(followed, 1.0 - progress);
				check(close(rendered.getXsize(), full.getXsize() * (1.0 - progress))
					&& close(rendered.getYsize(), full.getYsize() * (1.0 - progress))
					&& close(rendered.getZsize(), full.getZsize() * (1.0 - progress)),
					"render dimensions inherited stepped native shrink");
				check(close(rendered.minY, 12 + offset) && close(rendered.getCenter().x, 4),
					"shrinking frame lost its moving-body anchor");
				checks += 2;
			}
			var timeline = new dev.serko.safariutils.state.LinearTimeline(1_000_000_000L, 550_000_000L);
			for (int frame = 0; frame <= 110; frame++) {
				double progress = timeline.progress(1_000_000_000L + frame * 5_000_000L);
				double stepped = 1 - Math.floor(progress * 11) / 11;
				AABB nativeBox = new AABB(4 - stepped, 12, -stepped, 4 + stepped, 12 + stepped, stepped);
				AABB rendered = CritterMarkerGeometry.captureFrame(critter, full, nativeBox, -.25, .125, .5, 1 - progress);
				check(close(rendered.getCenter().x, 3.75) && close(rendered.minY, 12 + offset + .125)
					&& close(rendered.getCenter().z, .5), "frame interpolation loses species anchor");
				check(close(rendered.getXsize(), full.getXsize() * (1 - progress))
					&& close(rendered.getYsize(), full.getYsize() * (1 - progress)), "interpolation changes linear shrink");
				AABB absent = CritterMarkerGeometry.captureFrame(critter, full, null, 0, 0, 0, 1 - progress);
				check(close(absent.minY, full.minY) && close(absent.getXsize(), full.getXsize() * (1 - progress)),
					"missing body restarts or stops capture projection");
				checks += 3;
			}
		}
        Critter macaw = dev.serko.safariutils.data.Critters.MACAW;
        AABB stable = new AABB(0,0,0,1,1,1);
        java.util.UUID owner = java.util.UUID.randomUUID();
        var moving = new RecatchSpots.ActivePin(macaw, stable, owner, false, true);
        var frozen = new RecatchSpots.ActivePin(macaw, stable, owner, false);
        for (int frame=0;frame<=100;frame++) {
            float partial=frame/100f;
            AABB rendered=moving.frameBox(partial,(pin,tick)-> {
                check(pin.entityId().equals(owner), "pin projection changed ownership");
                return CritterMarkerGeometry.captureFrame(pin.critter(),pin.box(),stable,tick,0,0,1);
            });
            check(close(rendered.minX,partial) && close(rendered.getXsize(),1), "recatch movement stepped or resized");
            check(frozen.frameBox(partial,(pin,tick)-> { throw new AssertionError("frozen pin followed body"); }).equals(stable), "frozen anchor moved");
            checks+=3;
        }
        var marker1=new Markers.Marker(stable,"Macaw",0,Markers.Style.WAYPOINT,true,false,true,moving);
        var marker2=new Markers.Marker(stable,"Macaw",0,Markers.Style.WAYPOINT,true,false,true,
            new RecatchSpots.ActivePin(macaw,stable,java.util.UUID.randomUUID(),false,true));
        check(MarkerRegistry.deduplicate(java.util.List.of(marker1,marker2)).size()==2,"overlapping distinct pins merged"); checks++;
        return checks;
	}

	private static boolean close(double a, double b) { return Math.abs(a - b) < 1e-10; }
	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
