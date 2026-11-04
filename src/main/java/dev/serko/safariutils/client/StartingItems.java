package dev.serko.safariutils.client;

import java.util.ArrayList;
import java.util.List;

/** Canonical inventory names and biome ordering for Safari starting items. */
public final class StartingItems {
	public record Entry(String inventoryName, String singular, String plural) {
		String format(int count) {
			return count + " " + (count == 1 ? singular : plural);
		}
	}

	public static final List<Entry> ORDERED = List.of(
		new Entry("Lime Gem", "Lime Gem", "Lime Gems"),
		new Entry("Orange Gem", "Orange Gem", "Orange Gems"),
		new Entry("Purple Gem", "Purple Gem", "Purple Gems"),
		new Entry("Icebreaker", "Icebreaker", "Icebreakers"),
		new Entry("Shining Coin", "Coin", "Coins"),
		new Entry("Soothing Incense", "Incense", "Incense"),
		new Entry("Yogi Berry", "Berry", "Berries"),
		new Entry("Wriggleworm", "Worm", "Worms"),
		new Entry("Bag of Seeds", "Seed", "Seeds")
	);

	private static final int[] DISPLAY_ORDER = {2, 0, 1, 3, 5, 4, 6, 7, 8};

	public static final int ALL = (1 << ORDERED.size()) - 1;
	public static final int BIRD_FEED = 1 << 6 | 1 << 7 | 1 << 8;

	private StartingItems() {
	}

	public static int indexOf(String inventoryName) {
		for (int i = 0; i < ORDERED.size(); i++) {
			if (ORDERED.get(i).inventoryName().equals(inventoryName)) return i;
		}
		return -1;
	}

	/** Formats only selected, present items; an empty result means no alert should send. */
	public static String format(int[] counts, int selectedMask) {
		List<String> items = new ArrayList<>(ORDERED.size());
		for (int i : DISPLAY_ORDER) {
            if (i >= counts.length) continue;
			if ((selectedMask & 1 << i) != 0 && counts[i] > 0) {
				items.add(ORDERED.get(i).format(counts[i]));
			}
		}
		return String.join(", ", items);
	}

    static String mutualsFormat(int[] counts) {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < counts.length && i < ORDERED.size(); i++)
            if ((i == 4 || i >= 6) && counts[i] > 0) items.add(counts[i] + "x " + ORDERED.get(i).inventoryName());
        return String.join("; ", items);
    }
}
