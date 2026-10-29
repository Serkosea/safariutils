package dev.serko.safariutils.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded change detector; never used to decide live critter ownership. */
final class EntityResearchIndex {
    record Identity(UUID uuid, String type, String name, String item, int count, String bodyState) {
        Identity(UUID uuid, String type, String name, String item, int count) { this(uuid, type, name, item, count, ""); }
    }
    enum Change { NEW, METADATA, REUSED, SAME, FULL }
    private final int limit;
    private final Map<Integer, Identity> identities = new HashMap<>();
    EntityResearchIndex(int limit) { this.limit = limit; }
    Change observe(int networkId, Identity value) {
        Identity prior = identities.get(networkId);
        if (value.equals(prior)) return Change.SAME;
        if (prior == null && identities.size() >= limit) return Change.FULL;
        identities.put(networkId, value);
        return prior == null ? Change.NEW : prior.uuid().equals(value.uuid()) ? Change.METADATA : Change.REUSED;
    }
    void unload(int networkId, UUID uuid) {
        Identity prior = identities.get(networkId);
        if (prior != null && prior.uuid().equals(uuid)) identities.remove(networkId);
    }
    int size() { return identities.size(); }
    void clear() { identities.clear(); }
}
