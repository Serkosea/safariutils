package dev.serko.safariutils.api;

import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.LinkedHashMap;

/** Everything shown by one remote-player lookup, derived from one profile response. */
public record SparklingPlayerLookup(String username, Set<String> species, int duplicates,
		Map<String, Long> tickets, long fetchedAt, Double huntingLevel) {
    public SparklingPlayerLookup(String username, Set<String> species, int duplicates, Map<String,Long> tickets,long fetchedAt) {
        this(username,species,duplicates,tickets,fetchedAt,null);
    }
    public static String speciesId(String name) { return name.trim().toUpperCase(java.util.Locale.ROOT).replace(' ','_'); }
    public boolean owns(String speciesName) { return species.contains(speciesId(speciesName)); }

	public SparklingPlayerLookup {
		species = species.stream().map(SparklingPlayerLookup::speciesId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if(huntingLevel!=null&&(!Double.isFinite(huntingLevel)||huntingLevel<0))huntingLevel=null;
		tickets = Collections.unmodifiableMap(new LinkedHashMap<>(tickets));
	}
}
