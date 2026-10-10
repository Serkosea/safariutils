package dev.serko.safariutils.client;

import java.util.Arrays;

/** Small rectangular Hungarian assignment used for simultaneous critter reappearances. */
final class MinimumCostAssignment {
	private MinimumCostAssignment() {
	}

	/** Returns the selected column for every row; the matrix must have at least as many columns as rows. */
	static int[] solve(double[][] costs) {
		int rows = costs.length;
		if (rows == 0) return new int[0];
		int columns = costs[0].length;
		if (columns < rows) throw new IllegalArgumentException("Assignment requires columns >= rows");
		double[] rowPotential = new double[rows + 1];
		double[] columnPotential = new double[columns + 1];
		int[] matchedRow = new int[columns + 1];
		int[] previousColumn = new int[columns + 1];

		for (int row = 1; row <= rows; row++) {
			matchedRow[0] = row;
			double[] minimum = new double[columns + 1];
			Arrays.fill(minimum, Double.POSITIVE_INFINITY);
			boolean[] used = new boolean[columns + 1];
			int column = 0;
			do {
				used[column] = true;
				int activeRow = matchedRow[column];
				double delta = Double.POSITIVE_INFINITY;
				int nextColumn = 0;
				for (int candidate = 1; candidate <= columns; candidate++) {
					if (used[candidate]) continue;
					double reduced = costs[activeRow - 1][candidate - 1]
						- rowPotential[activeRow] - columnPotential[candidate];
					if (reduced < minimum[candidate]) {
						minimum[candidate] = reduced;
						previousColumn[candidate] = column;
					}
					if (minimum[candidate] < delta) {
						delta = minimum[candidate];
						nextColumn = candidate;
					}
				}
				for (int candidate = 0; candidate <= columns; candidate++) {
					if (used[candidate]) {
						rowPotential[matchedRow[candidate]] += delta;
						columnPotential[candidate] -= delta;
					} else {
						minimum[candidate] -= delta;
					}
				}
				column = nextColumn;
			} while (matchedRow[column] != 0);

			do {
				int previous = previousColumn[column];
				matchedRow[column] = matchedRow[previous];
				column = previous;
			} while (column != 0);
		}

		int[] assignment = new int[rows];
		Arrays.fill(assignment, -1);
		for (int column = 1; column <= columns; column++) {
			if (matchedRow[column] > 0) assignment[matchedRow[column] - 1] = column - 1;
		}
		return assignment;
	}
}
