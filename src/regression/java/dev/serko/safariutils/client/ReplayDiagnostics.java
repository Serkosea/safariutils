package dev.serko.safariutils.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Offline entry point; reads explicit sealed segments without loading client settings or a world. */
public final class ReplayDiagnostics {
	public static void main(String[] args) {
		if (args.length == 0) throw new IllegalArgumentException("Supply diagnostic segments in chronological order");
		var paths = Arrays.stream(args).map(Path::of).toList();
		for (Path path : paths) {
			if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Diagnostic file not found: " + path);
		}
		var report = DiagnosticReplay.replay(paths);
		System.out.println(report.display());
		if (report.warnings() != 0) throw new IllegalStateException("Diagnostic audit requires review; see warnings above");
	}
	private ReplayDiagnostics() { }
}
