package dev.serko.safariutils.client;

/** Client-thread publication: build all scan data before replacing the current value. */
final class ScanPublication<T> {
	record Snapshot<T>(long revision, T value) { }
	private Snapshot<T> current;

	ScanPublication(T initial) {
		current = new Snapshot<>(0, java.util.Objects.requireNonNull(initial));
	}

	Snapshot<T> current() { return current; }

	void publish(T complete) {
		current = new Snapshot<>(current.revision() + 1, java.util.Objects.requireNonNull(complete));
	}
}
