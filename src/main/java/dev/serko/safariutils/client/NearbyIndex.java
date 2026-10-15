package dev.serko.safariutils.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/** Scan-local spatial candidates; exact distance and eligibility remain the caller's decision. */
final class NearbyIndex<T> {
	private record Cell(int x, int y, int z) { }
	private record Entry<T>(int order, T value) { }
	private final double cellSize;
	private final Map<Cell, List<Entry<T>>> cells = new HashMap<>();
	private final Map<Cell, List<T>> neighborhoods = new HashMap<>();

	NearbyIndex(List<T> values, double cellSize, ToDoubleFunction<T> x,
			ToDoubleFunction<T> y, ToDoubleFunction<T> z) {
		if (!Double.isFinite(cellSize) || cellSize <= 0) throw new IllegalArgumentException("Invalid cell size");
		this.cellSize = cellSize;
		for (int index = 0; index < values.size(); index++) {
			T value = values.get(index);
			cells.computeIfAbsent(cell(x.applyAsDouble(value), y.applyAsDouble(value), z.applyAsDouble(value)),
				ignored -> new ArrayList<>()).add(new Entry<>(index, value));
		}
	}

	List<T> near(double x, double y, double z) {
		if (cells.isEmpty()) return List.of();
		Cell centre = cell(x, y, z);
		List<T> cached = neighborhoods.get(centre);
		if (cached != null) return cached;
		List<Entry<T>> found = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					List<Entry<T>> bucket = cells.get(new Cell(centre.x() + dx, centre.y() + dy, centre.z() + dz));
					if (bucket != null) found.addAll(bucket);
				}
			}
		}
		if (found.isEmpty()) return List.of();
		found.sort(Comparator.comparingInt(Entry::order));
		List<T> result = new ArrayList<>(found.size());
		for (Entry<T> entry : found) result.add(entry.value());
		List<T> immutable = List.copyOf(result);
		// Empty neighborhoods are not retained. Each entry can occur in only 27 cached cells.
		neighborhoods.put(centre, immutable);
		return immutable;
	}

	private Cell cell(double x, double y, double z) {
		return new Cell((int) Math.floor(x / cellSize), (int) Math.floor(y / cellSize),
			(int) Math.floor(z / cellSize));
	}
}
