package dev.serko.safariutils.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Candidate broad phase checked against a full source-order scan, without Minecraft entities. */
public final class NearbyIndexRegression {
	private record Point(int id, double x, double y, double z) { }
	public static int run() {
		int checks = 0;
		var random = new Random(20261014);
		for (double size : new double[] {1, 3, 4}) {
			var points = new ArrayList<Point>();
			for (int i = 0; i < 300; i++) {
				points.add(new Point(i, coordinate(random, size), coordinate(random, size), coordinate(random, size)));
			}
			points.add(points.getFirst()); // preserve source multiplicity, not only identity
			Collections.shuffle(points, random);
			var index = index(points, size);
			for (int query = 0; query < 1000; query++) {
				double x = coordinate(random, size), y = coordinate(random, size), z = coordinate(random, size);
				var expected = points.stream().filter(p -> adjacent(p.x(), x, size)
					&& adjacent(p.y(), y, size) && adjacent(p.z(), z, size)).toList();
				var actual = index.near(x, y, z);
				check(actual.equals(expected), "candidate index differs from source-order reference"); checks++;
				check(index.near(x, y, z) == actual, "repeated query rebuilt its candidate list"); checks++;
				var withinRadius = points.stream().filter(p -> square(p.x() - x) + square(p.y() - y)
					+ square(p.z() - z) < size * size).toList();
				check(actual.containsAll(withinRadius), "broad phase omitted eligible nearby body"); checks++;
				try { actual.add(new Point(-1, 0, 0, 0)); throw new AssertionError("mutable query result"); }
				catch (UnsupportedOperationException expectedFailure) { checks++; }
			}
			var first = points.getFirst();
			var before = index.near(first.x(), first.y(), first.z());
			points.clear();
			check(index.near(first.x(), first.y(), first.z()).equals(before), "source list mutation changed scan"); checks++;
			check(index(points, size).near(first.x(), first.y(), first.z()).isEmpty(), "fresh scan reused previous candidates"); checks++;
		}
		for (double size : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
			try { index(List.of(), size); throw new AssertionError("invalid cell size accepted"); }
			catch (IllegalArgumentException expected) { checks++; }
		}
		return checks;
	}
	private static NearbyIndex<Point> index(List<Point> points, double size) {
		return new NearbyIndex<>(points, size, Point::x, Point::y, Point::z);
	}
	private static double coordinate(Random random, double size) {
		return (random.nextInt(11) - 5) * size + switch (random.nextInt(4)) {
			case 0 -> 0;
			case 1 -> -.000001;
			case 2 -> .000001;
			default -> random.nextDouble() * size;
		};
	}
	private static boolean adjacent(double value, double anchor, double size) {
		return Math.abs(Math.floor(value / size) - Math.floor(anchor / size)) <= 1;
	}
	private static double square(double value) { return value * value; }
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
	private NearbyIndexRegression() { }
}
