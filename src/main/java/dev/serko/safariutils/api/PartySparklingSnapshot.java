package dev.serko.safariutils.api;

import java.util.List;

/** Party members and whether their shared collection is currently API-managed. */
public record PartySparklingSnapshot(List<String> members, boolean apiManaged) {
	public PartySparklingSnapshot {
		members = List.copyOf(members);
	}
}
