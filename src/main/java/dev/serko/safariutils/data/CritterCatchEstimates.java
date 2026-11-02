package dev.serko.safariutils.data;

/** Expected full-run catches for profit estimates, never population limits or completion quotas. */
public final class CritterCatchEstimates {
    // One player's two starting-item rolls, assuming equal chances across nine quest items.
    public static final double STARTING_PER_ITEM = 2.0 / 9.0;
    private static final double FEED_PER_TYPE = 3 + STARTING_PER_ITEM;
    private CritterCatchEstimates() { }

    public static double perRun(Critter critter) {
        return switch (critter.name()) {
            // Mutuals 3.6.4 spreadsheet estimate; implies 16.5 mounds at 25%.
            // The source does not establish a mound range. Keep this provisional.
            case "Rockmite" -> 4.125;
            case "Snoozle" -> 5 * 0.5;
            // Supplied probabilities are rounded percentages; do not invent extra precision.
            case "Bluebird" -> FEED_PER_TYPE * (0.72 + 0.5454 + 0.6154);
            case "Parakeet" -> FEED_PER_TYPE * (0.24 + 0.4091 + 0.3077);
            case "Macaw" -> FEED_PER_TYPE * (0.04 + 0.0454 + 0.0769) * 2;
            case "Gimmiegold" -> (3 + 6) / 2.0 + STARTING_PER_ITEM;
            default -> {
                var range = CritterSpawnRanges.forCritter(critter);
                yield (range.minimum() + range.maximum()) / 2.0;
            }
        };
    }
}
