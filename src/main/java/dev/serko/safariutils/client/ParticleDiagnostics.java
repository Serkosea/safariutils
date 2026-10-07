package dev.serko.safariutils.client;

import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Recognizes the bounded server-particle pattern used by Sparkling critters. */
public final class ParticleDiagnostics {
	private static final double SPARKLING_ASSOCIATION_DISTANCE_SQ = 1.75 * 1.75;
	private static final long REPEAT_WINDOW_MILLIS = 1_500L;
	/** Sparkling effects repeat continuously; older evidence no longer identifies a moving body. */
	private static final long EVIDENCE_FRESH_MILLIS = 2_500L;
	private static final int REQUIRED_PACKETS = 3;
	private static final Map<UUID, Evidence> evidence = new LinkedHashMap<>();
	private static long lastCleanup;

	private static final class Evidence {
		private final dev.serko.safariutils.data.Critter critter;
		private int packets;
		private long lastAt;
		private boolean confirmed;

		private Evidence(dev.serko.safariutils.data.Critter critter, long now) {
			this.critter = critter;
			this.lastAt = now;
		}
	}

	private ParticleDiagnostics() {
	}

	public static void onParticle(ClientboundLevelParticlesPacket packet) {
		if (!HypixelConnection.active() || !SafariLocation.inSafari()) return;
		Vec3 position = new Vec3(packet.getX(), packet.getY(), packet.getZ());
		if (sparklingPattern(packet)) observeSparklingPattern(position);
	}

	/** Packet shape consistently observed for Sparkling effects. */
	private static boolean sparklingPattern(ClientboundLevelParticlesPacket packet) {
		return packet.getParticle().getType() == ParticleTypes.WAX_ON && packet.getCount() == 5
			&& close(packet.getXDist(), 0.30f) && close(packet.getYDist(), 0.50f)
			&& close(packet.getZDist(), 0.30f) && close(packet.getMaxSpeed(), 1.00f);
	}

	private static boolean close(float actual, float expected) {
		return Math.abs(actual - expected) <= 0.011f;
	}

	/** Requires several matching packets around the same plausible loaded critter. */
	private static void observeSparklingPattern(Vec3 position) {
		CritterEntities.Sighting nearest = null;
		double nearestSq = SPARKLING_ASSOCIATION_DISTANCE_SQ;
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			double distanceSq = sighting.body().position().distanceToSqr(position);
			if (distanceSq >= nearestSq) continue;
			nearestSq = distanceSq;
			nearest = sighting;
		}
		if (nearest == null) return;

		// The named stand is the stable identity we detected first. A mob can pair a
		// scan later, which must not reset accumulated particle evidence merely because
		// SparklingWatch's presentation key changed.
		UUID key = nearest.label().getUUID();
		long now = System.currentTimeMillis();
		Evidence current = evidence.get(key);
		if (current != null && current.confirmed && current.critter == nearest.critter()) {
			current.lastAt = now;
			return;
		}
		if (current == null || current.critter != nearest.critter()
			|| now - current.lastAt > REPEAT_WINDOW_MILLIS) {
			current = new Evidence(nearest.critter(), now);
			evidence.put(key, current);
		}
		current.lastAt = now;
		current.packets++;
		if (current.packets == 1) {
			DebugLog.line("SPARKLING", "particle candidate " + nearest.critter().name()
				+ " key=" + shortId(key) + " repeat=" + current.packets + "/" + REQUIRED_PACKETS
				+ " distance=" + String.format("%.2f", Math.sqrt(nearestSq)));
		}
		if (current.confirmed || current.packets < REQUIRED_PACKETS) return;
		current.confirmed = true;
		DebugLog.line("SPARKLING", "particle confirmed " + nearest.critter().name()
			+ " key=" + shortId(key));
	}

	public static boolean confirms(CritterEntities.Sighting sighting) {
		if (sighting == null) return false;
		Evidence found = evidence.get(sighting.label().getUUID());
		return found != null && found.confirmed && found.critter == sighting.critter()
			&& System.currentTimeMillis() - found.lastAt <= EVIDENCE_FRESH_MILLIS;
	}

	/** An authoritative catch invalidates every transient particle association for that species. */
	static void onCaught(dev.serko.safariutils.data.Critter critter) {
		evidence.entrySet().removeIf(entry -> entry.getValue().critter == critter);
	}

	static String source(CritterEntities.Sighting sighting) {
		boolean named = sighting != null && sighting.sparkling();
		boolean particles = confirms(sighting);
		if (named && particles) return "name+particles";
		return named ? "name" : particles ? "particles" : "none";
	}

	public static void reset() {
		evidence.clear();
	}

	private static String shortId(UUID id) {
		return id.toString().substring(0, 8);
	}

	public static void tick() {
		if (!SafariLocation.inSafari()) {
			evidence.clear();
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastCleanup < EVIDENCE_FRESH_MILLIS) return;
		lastCleanup = now;
		evidence.entrySet().removeIf(entry -> now - entry.getValue().lastAt > EVIDENCE_FRESH_MILLIS);
	}
}
