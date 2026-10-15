package dev.serko.safariutils.client;

import java.util.Arrays;

/** Pure one-to-one assignment shared by breakout reappearance and resolved-body form swaps. */
final class CapturePairing {
	private CapturePairing() { }

	/**
	 * Returns a candidate index per observed body, or -1 for an unmatched observation.
	 * Every body has its own unmatched option, so proximity never forces an invalid pairing.
	 */
	static int[] match(double[][] distances, double maximumDistance, double unmatchedCost) {
		if (!Double.isFinite(unmatchedCost) || unmatchedCost <= 0
				|| Double.isNaN(maximumDistance) || maximumDistance < 0) {
			throw new IllegalArgumentException("Invalid capture pairing limits");
		}
		int rows = distances.length;
		if (rows == 0) return new int[0];
		int columns = distances[0].length;
		double[][] costs = new double[rows][columns + rows];
		for (int row = 0; row < rows; row++) {
			if (distances[row].length != columns) throw new IllegalArgumentException("Ragged distance matrix");
			for (int column = 0; column < columns; column++) {
				double distance = distances[row][column];
				costs[row][column] = Double.isFinite(distance) && distance >= 0
					&& distance <= maximumDistance && distance <= unmatchedCost
					? distance : Math.nextUp(unmatchedCost);
			}
			Arrays.fill(costs[row], columns, columns + rows, unmatchedCost);
		}
		int[] matched = MinimumCostAssignment.solve(costs);
		for (int row = 0; row < rows; row++) {
			if (matched[row] >= columns) matched[row] = -1;
		}
		return matched;
	}
}
