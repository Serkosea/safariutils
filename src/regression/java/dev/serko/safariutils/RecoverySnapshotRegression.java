package dev.serko.safariutils;

import dev.serko.safariutils.io.RecoverySnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;

final class RecoverySnapshotRegression {
	static int run() throws Exception {
		int checks = 0;
		var root = Files.createTempDirectory("safari-recovery-test-");
		try {
			var source = root.resolve("protected.json");
			Files.writeString(source, "protected-original");
			var writer = new RecoverySnapshot("working");
			var directory = root.resolve("backups");
			var first = writer.write(directory, "{\"count\":1}");
			check(first.getParent().equals(directory) && Files.readString(first).contains(":1"), "recovery confined to destination"); checks++;
			check(writer.write(directory.resolve("."), "{\"count\":2}").equals(first), "normalized destination and repeated save reuse session"); checks++;
			check(Files.readString(first).contains(":2"), "working snapshot updates"); checks++;
			try { writer.write(root.resolve("elsewhere"), "bad"); throw new AssertionError("redirect accepted"); }
			catch (IOException expected) { checks++; }
			check(Files.readString(first).contains(":2") && !Files.exists(root.resolve("elsewhere")), "redirect rejection changes nothing"); checks++;
			writer.reset();
			var next = writer.write(directory, "new-session");
			check(!next.equals(first) && Files.readString(first).contains(":2"), "reset preserves old recovery"); checks++;
			check(!new RecoverySnapshot("working").write(directory, "other-store").equals(next), "independent writers do not share destination"); checks++;
			var obstacle = root.resolve("obstacle");
			Files.writeString(obstacle, "not-a-directory");
			var retry = new RecoverySnapshot("retry");
			try { retry.write(obstacle, "initial"); throw new AssertionError("failed write accepted"); }
			catch (IOException expected) { checks++; }
			Files.delete(obstacle);
			var recovered = retry.write(obstacle, "retried");
			check(Files.readString(recovered).equals("retried"), "failed session can retry"); checks++;
			check(Files.readString(source).equals("protected-original"), "protected source bytes untouched"); checks++;
			try { new RecoverySnapshot("../escape"); throw new AssertionError("path stem accepted"); }
			catch (IllegalArgumentException expected) { checks++; }
		} finally {
			try (var paths = Files.walk(root)) {
				for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
			}
		}
		return checks;
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
