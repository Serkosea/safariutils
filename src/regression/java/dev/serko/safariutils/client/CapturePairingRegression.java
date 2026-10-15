package dev.serko.safariutils.client;

import java.util.Arrays;
import java.util.Random;

/** Deterministic matching fixtures plus exhaustive-optimum comparisons for generated groups. */
public final class CapturePairingRegression {
	private CapturePairingRegression() { }

	public static int run() {
		expect(new double[][] {{1, 2}, {1.1, 100}}, 16, 17, new int[] {1, 0});
		expect(new double[][] {{100}, {0.2}}, 16, 17, new int[] {-1, 0});
		expect(new double[][] {{}, {}}, 16, 17, new int[] {-1, -1});
		expect(new double[][] {{Double.NaN, Double.POSITIVE_INFINITY, -1}}, 16, 17, new int[] {-1});
		expect(new double[][] {{300}}, Double.POSITIVE_INFINITY, 999999, new int[] {0});
		expect(new double[][] {{2.25}}, 2.25, 3.25, new int[] {0});
		expect(new double[][] {{2.26}}, 2.25, 3.25, new int[] {-1});
		Random random = new Random(0x534146415249L);
		for (int sample = 0; sample < 500; sample++) {
			int rows = 1 + random.nextInt(5);
			int columns = random.nextInt(6);
			double[][] distances = new double[rows][columns];
			for (double[] row : distances) {
				for (int col = 0; col < columns; col++) row[col] = random.nextInt(250) / 10.0;
			}
			int[] assignment = CapturePairing.match(distances, 16, 17);
			boolean[] used = new boolean[columns];
			double total = 0;
			for (int row = 0; row < rows; row++) {
				int col = assignment[row];
				if (col == -1) { total += 17; continue; }
				if (col < 0 || col >= columns || used[col] || distances[row][col] > 16) {
					throw new AssertionError("Invalid/duplicate capture owner in sample " + sample);
				}
				used[col] = true;
				total += distances[row][col];
			}
			double optimum = optimum(distances, 0, new boolean[columns]);
			if (Math.abs(total - optimum) > 1e-8) {
				throw new AssertionError("Suboptimal capture assignment in sample " + sample);
			}
			if (!Arrays.equals(assignment, CapturePairing.match(distances, 16, 17))) {
				throw new AssertionError("Nondeterministic capture assignment");
			}
			var trace = CapturePairingTrace.encode("breakout", distances, 16, 17, assignment);
			if (!CapturePairingTrace.matches(com.google.gson.JsonParser.parseString(trace.toString()).getAsJsonObject())) {
				throw new AssertionError("Pairing trace cannot reproduce live result");
			}
		}
		return 1007;
	}

	private static double optimum(double[][] distances, int row, boolean[] used) {
		if (row == distances.length) return 0;
		double best = 17 + optimum(distances, row + 1, used);
		for (int col = 0; col < used.length; col++) {
			if (used[col] || distances[row][col] > 16) continue;
			used[col] = true;
			best = Math.min(best, distances[row][col] + optimum(distances, row + 1, used));
			used[col] = false;
		}
		return best;
	}

	private static void expect(double[][] distances, double maximum, double unmatched, int[] expected) {
		int[] actual = CapturePairing.match(distances, maximum, unmatched);
		if (!Arrays.equals(actual, expected)) throw new AssertionError("Capture fixture failed: " + Arrays.toString(actual));
		if (!CapturePairingTrace.matches(CapturePairingTrace.encode("fixture", distances, maximum, unmatched, actual)))
			throw new AssertionError("Special-value pairing trace failed");
	}
}
