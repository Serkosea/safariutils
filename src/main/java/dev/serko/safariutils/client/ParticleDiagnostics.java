package dev.serko.safariutils.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Aggregates server particle packets so Alpha tests can identify Sparkling effects. */
public final class ParticleDiagnostics {
	private static final long FLUSH_MILLIS = 1_000L;
	private static final double ASSOCIATION_DISTANCE_SQ = 8.0 * 8.0;
	private static final double SPARKLING_ASSOCIATION_DISTANCE_SQ = 1.75 * 1.75;
	private static final long REPEAT_WINDOW_MILLIS = 1_500L;
	private static final int REQUIRED_PACKETS = 3;
	private static final Map<Key, Sample> samples = new LinkedHashMap<>();
	private static final Map<UUID, Evidence> evidence = new LinkedHashMap<>();
	private static long lastFlush;

	private record Key(String particle, String critter, boolean sparkling) {}
	private record Sample(int packets, int particles, double nearestDistance,
		Vec3 position, int count, float spreadX, float spreadY, float spreadZ, float speed) {}
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
		if (!SafariLocation.inSafari()) return;
		Vec3 position = new Vec3(packet.getX(), packet.getY(), packet.getZ());
		if (sparklingPattern(packet)) observeSparklingPattern(position);
		if (!DebugLog.isEnabled() || !ConfigManager.get().advanced.logParticles) return;
		String particle = String.valueOf(BuiltInRegistries.PARTICLE_TYPE
			.getKey(packet.getParticle().getType()));
		CritterEntities.Sighting nearest = null;
		double nearestSq = ASSOCIATION_DISTANCE_SQ;
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			double distanceSq = sighting.body().position().distanceToSqr(position);
			if (distanceSq >= nearestSq) continue;
			nearestSq = distanceSq;
			nearest = sighting;
		}
		String critter = nearest == null ? "none" : nearest.critter().name();
		boolean sparkling = nearest != null && nearest.sparkling();
		Key key = new Key(particle, critter, sparkling);
		Sample old = samples.get(key);
		int amount = Math.max(1, packet.getCount());
		if (old == null) {
			samples.put(key, new Sample(1, amount, Math.sqrt(nearestSq), position,
				packet.getCount(), packet.getXDist(), packet.getYDist(), packet.getZDist(),
				packet.getMaxSpeed()));
		} else {
			samples.put(key, new Sample(old.packets() + 1, old.particles() + amount,
				Math.min(old.nearestDistance(), Math.sqrt(nearestSq)), old.position(), old.count(),
				old.spreadX(), old.spreadY(), old.spreadZ(), old.speed()));
		}
	}

	/** Exact packet shape repeatedly observed on every Alpha Sparkling tested so far. */
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

		UUID key = SparklingWatch.keyOf(nearest);
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
		if (DebugLog.isEnabled() && ConfigManager.get().advanced.logParticles) {
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
		Evidence found = evidence.get(SparklingWatch.keyOf(sighting));
		return found != null && found.confirmed && found.critter == sighting.critter();
	}

	static String source(CritterEntities.Sighting sighting) {
		boolean named = sighting != null && sighting.sparkling();
		boolean particles = confirms(sighting);
		if (named && particles) return "name+particles";
		return named ? "name" : particles ? "particles" : "none";
	}

	public static void reset() {
		evidence.clear();
		samples.clear();
	}

	private static String shortId(UUID id) {
		return id.toString().substring(0, 8);
	}

	public static void tick() {
		long now = System.currentTimeMillis();
		if (!SafariLocation.inSafari()) evidence.clear();
		if (now - lastFlush < FLUSH_MILLIS) return;
		lastFlush = now;
		if (!DebugLog.isEnabled() || !ConfigManager.get().advanced.logParticles) {
			samples.clear();
			return;
		}
		for (Map.Entry<Key, Sample> entry : samples.entrySet()) {
			Key key = entry.getKey();
			Sample sample = entry.getValue();
			String message = ("%s packets=%d particles=%d near=%s%s distance=%.2f "
				+ "sample=(%.2f,%.2f,%.2f) count=%d spread=(%.2f,%.2f,%.2f) speed=%.2f")
				.formatted(key.particle(), sample.packets(), sample.particles(), key.critter(),
					key.sparkling() ? " [SPARKLING]" : "", sample.nearestDistance(),
					sample.position().x, sample.position().y, sample.position().z, sample.count(),
					sample.spreadX(), sample.spreadY(), sample.spreadZ(), sample.speed());
			DebugLog.line("PARTICLE", message);
		}
		samples.clear();
	}
}
