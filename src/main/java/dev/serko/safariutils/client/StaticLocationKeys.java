package dev.serko.safariutils.client;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashSet;
import java.util.Set;

/** Persist block centers while retaining BlockPos as the render/tracking anchor. */
final class StaticLocationKeys {
	private StaticLocationKeys() { }

	static String encode(BlockPos pos) {
		return encode(pos, 0);
	}

	/** A negative Y offset stores a cube below the BlockPos anchor. */
	static String encode(BlockPos pos, int cubeYOffset) {
		return (pos.getX() + 0.5) + "," + (pos.getY() + 0.5 + cubeYOffset)
			+ "," + (pos.getZ() + 0.5);
	}

	/** Accepts old integer block-corner keys as well as current half-block centers. */
	static BlockPos decode(String value) {
		if (value == null) return null;
		String[] parts = value.split(",", -1);
		if (parts.length != 3) return null;
		try {
			return new BlockPos(blockCoordinate(parts[0]), blockCoordinate(parts[1]), blockCoordinate(parts[2]));
		} catch (IllegalArgumentException invalid) {
			return null;
		}
	}

	static BlockPos decode(String value, int cubeYOffset) {
		BlockPos cube = decode(value);
		return cube == null ? null : cube.offset(0, -cubeYOffset, 0);
	}

	private static int blockCoordinate(String value) {
		double coordinate = Double.parseDouble(value);
		if (!Double.isFinite(coordinate) || coordinate < Integer.MIN_VALUE
			|| coordinate >= (double) Integer.MAX_VALUE + 1) throw new NumberFormatException(value);
		double floor = Math.floor(coordinate);
		if (coordinate != floor && coordinate != floor + 0.5) throw new NumberFormatException(value);
		return (int) floor;
	}

	static boolean normalize(Set<String> positions) {
		if (positions == null) return false;
		Set<String> centered = new LinkedHashSet<>();
		for (String key : positions) {
			BlockPos pos = decode(key);
			centered.add(pos == null ? key : encode(pos));
		}
		if (centered.equals(positions)) return false;
		positions.clear();
		positions.addAll(centered);
		return true;
	}

}
