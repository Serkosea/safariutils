package dev.serko.safariutils.client;

import java.util.LinkedHashMap;
import java.util.Map;

/** Screen-owned, bounded presentation state. Never changes settings or performs background work. */
public final class SettingsMotion {
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
