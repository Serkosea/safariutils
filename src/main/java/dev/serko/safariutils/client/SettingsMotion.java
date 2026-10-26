package dev.serko.safariutils.client;

import java.util.LinkedHashMap;
import java.util.Map;

/** Screen-owned, bounded presentation state. Never changes settings or performs background work. */
public final class SettingsMotion {
	/** Adapts per-frame easing to elapsed time, preserving its coefficient at 60 FPS. */
	public static final class Frame {
		private static final double BASE_FRAME_NANOS = 1_000_000_000d / 60d;
		private static final long MAX_STEP_NANOS = 50_000_000L;
		private final float[] speeds = new float[16];
		private final float[] coefficients = new float[16];
		private long previous;
		private boolean started;
		private double frames = 1d;
		private int cached;

		/** Once per rendered frame. Long pauses do not turn into unbounded animation steps. */
		public void begin(long nowNanos) {
			frames = started ? Math.clamp(nowNanos - previous, 0L, MAX_STEP_NANOS) / BASE_FRAME_NANOS : 1d;
			previous = nowNanos;
			started = true;
			cached = 0;
		}

		public void reset() { started = false; cached = 0; frames = 1d; }

		public float approach(float current, float target, float speedAt60Fps) {
			if (current == target) return target;
			if (!Float.isFinite(speedAt60Fps) || speedAt60Fps <= 0f) return current;
			if (speedAt60Fps >= 1f) return target;
			for (int index = 0; index < cached; index++)
				if (speeds[index] == speedAt60Fps)
					return current + (target - current) * coefficients[index];
			float coefficient = (float) (1d - Math.pow(1d - speedAt60Fps, frames));
			if (cached < speeds.length) {
				speeds[cached] = speedAt60Fps;
				coefficients[cached++] = coefficient;
			}
			return current + (target - current) * coefficient;
		}
	}
	private static final long DURATION = 93_333_333L;
	private static final int LIMIT = 512;
	private static final class Transition {
		float from, target;
		long start;
		Transition(float value, long now) { from = target = value; start = now; }
		float value(long now) {
			if (from == target) return target;
			float progress = Math.clamp((now - start) / (float) DURATION, 0f, 1f);
			if (progress == 1f) return from = target;
			float eased = progress * progress * (3f - 2f * progress);
			return from + (target - from) * eased;
		}
	}
	private final Map<Long, Transition> transitions = new LinkedHashMap<>();
	private long now;
	private int layout;
	private boolean reduced;
	private Transition navigation;

	public void begin(long now, int layout, boolean reduced) {
		if (this.layout != layout) transitions.clear();
		this.layout = layout;
		this.now = now;
		this.reduced = reduced;
	}

	public float value(long key, float target) {
		Transition transition = transitions.get(key);
		if (transition == null) {
			if (transitions.size() >= LIMIT) transitions.remove(transitions.keySet().iterator().next());
			transition = new Transition(target, now);
			transitions.put(key, transition);
		}
		return update(transition, target);
	}

	public float navigation(float target) {
		if (navigation == null) navigation = new Transition(target, now);
		return update(navigation, target);
	}

	private float update(Transition transition, float target) {
		float value = transition.value(now);
		if (reduced) { transition.from = transition.target = target; return target; }
		if (transition.target != target) {
			transition.from = value;
			transition.target = target;
			transition.start = now;
		}
		return value;
	}

	int retained() { return transitions.size(); }
}
