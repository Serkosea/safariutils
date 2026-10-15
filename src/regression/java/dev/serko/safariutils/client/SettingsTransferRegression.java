package dev.serko.safariutils.client;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

public final class SettingsTransferRegression {
	public static int run() throws Exception {
		int checks = 0;
		for (String format : new String[] {"2.5", "\"2\"", "true", "null", "[]", "{}", "4294967298", "3"}) {
			check(!SettingsTransfer.preview(encode("{\"f\":" + format + ",\"s\":{}}", false)).valid(),
				"invalid format rejected: " + format);
			checks++;
		}
		check(!SettingsTransfer.preview("SU2-invalid!").valid(), "invalid base64 rejected"); checks++;
		check(!SettingsTransfer.preview(encode("not json", false)).valid(), "invalid JSON rejected"); checks++;
		check(!SettingsTransfer.preview(encode("{\"format\":1.5,\"settings\":{}}", true)).valid(),
			"legacy fractional format rejected"); checks++;
		check(!SettingsTransfer.preview(encode(" ".repeat(4_000_001), false)).valid(),
			"compressed expansion bounded"); checks++;
		check(!SettingsTransfer.preview("SU2-" + "a".repeat(2_004_097)).valid(), "input bounded"); checks++;
		check(!SettingsTransfer.apply(null).success(), "missing preview cannot import"); checks++;
		return checks;
	}
	private static String encode(String text, boolean legacy) throws Exception {
		var bytes = new ByteArrayOutputStream();
		var deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
		try {
			try (var stream = legacy ? new GZIPOutputStream(bytes) : new DeflaterOutputStream(bytes, deflater)) {
				stream.write(text.getBytes(StandardCharsets.UTF_8));
			}
			return (legacy ? "SU1-" : "SU2-") + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
		} finally { deflater.end(); }
	}
	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}
}
