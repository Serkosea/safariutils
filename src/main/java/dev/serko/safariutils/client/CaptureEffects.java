package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import java.util.UUID;

/** One projection boundary for authoritative capture decisions; implementations do not choose targets. */
interface CaptureEffects {
	void started(long attempt, Critter species);
	void bodyReplaced(Critter species, UUID before, UUID after);
	void reassign(long attempt, Critter species, UUID before, UUID after, AABB visual, AABB body, boolean sparkling, int pity);
	void anchor(long attempt, UUID entity, Critter species, AABB box);
	void freeze(long attempt, UUID entity);
	void restored(UUID entity);
	void correct(long attempt, Critter species, UUID before, UUID after, AABB visual, AABB body, boolean sparkling, int pity, boolean guaranteed);
	void present(long attempt, Critter species, UUID entity, AABB visual, AABB body, boolean sparkling, int pity, boolean guaranteed, long started);
	void expired(long attempt, Critter species, UUID entity);
	void resolved(long attempt, Critter species, UUID before, UUID after, AABB visual, AABB body, boolean sparkling, int pity, CritterState.Outcome outcome, boolean visible);
	void completed(Critter species, int catches);

	CaptureEffects LIVE = new CaptureEffects() {
		public void started(long attempt, Critter species) { RecatchSpots.onCaptureStarted(attempt, species); }
		public void bodyReplaced(Critter species, UUID before, UUID after) {
			RecatchSpots.onBodyReplaced(species, before, after);
            SparklingWatch.onBodyReplaced(species,before,after);
			StillCritters.onBodyReplaced(species, before);
		}
		public void reassign(long attempt, Critter species, UUID before, UUID after, AABB visual, AABB body, boolean sparkling, int pity) {
			RecatchSpots.reassignIdentity(attempt, species, before, after, visual, sparkling, pity);
			StillCritters.reassignIdentity(attempt, species, before, after, body);
		}
		public void anchor(long attempt, UUID entity, Critter species, AABB box) { RecatchSpots.updateIdentityAnchor(attempt, entity, species, box); }
		public void freeze(long attempt, UUID entity) { RecatchSpots.freezeIdentityAnchor(attempt, entity); }
		public void restored(UUID entity) { RecatchSpots.onCaughtBodyRestored(entity); }
		public void correct(long attempt, Critter species, UUID before, UUID after, AABB visual, AABB body, boolean sparkling, int pity, boolean guaranteed) {
			RecatchSpots.correctIdentityTarget(attempt, species, before, after, visual, sparkling, pity, guaranteed);
			StillCritters.correctResolvingTarget(attempt, species, before, after, BlockPos.containing(body.getCenter()));
		}
		public void present(long attempt, Critter species, UUID entity, AABB visual, AABB body, boolean sparkling, int pity, boolean guaranteed, long started) {
			RecatchSpots.onIdentityAttempt(attempt, species, entity, visual, sparkling, pity, guaranteed, started);
			StillCritters.onIdentityAttempt(attempt, species, entity, body);
		}
		public void expired(long attempt, Critter species, UUID entity) {
			RecatchSpots.onIdentityExpired(attempt, species, entity); StillCritters.onIdentityExpired(attempt, species, entity);
		}
		public void resolved(long attempt, Critter species, UUID before, UUID after, AABB visual, AABB body, boolean sparkling, int pity, CritterState.Outcome outcome, boolean visible) {
			RecatchSpots.onIdentityResolved(attempt, species, before, after, visual, sparkling, pity, outcome, visible);
            SparklingWatch.onCaptureResolved(species,before,after,outcome);
			StillCritters.onIdentityResolved(attempt, species, before, after, body, outcome);
		}
		public void completed(Critter species, int catches) {
			StillCritters.onConfirmedCatchTotal(species, catches); RecatchSpots.onConfirmedCatchTotal(species, catches);
		}
	};
}
