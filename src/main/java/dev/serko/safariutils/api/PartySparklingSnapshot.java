package dev.serko.safariutils.api;

import java.util.List;
import java.util.Set;

/** Cached API result for the party composition currently visible to the provider. */
public record PartySparklingSnapshot(List<String> members, Set<String> sharedSpecies,
		boolean apiManaged) {
	public PartySparklingSnapshot {
		members = List.copyOf(members);
		sharedSpecies = Set.copyOf(sharedSpecies);
	}
}
