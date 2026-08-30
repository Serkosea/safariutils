package dev.serko.safariutils.client;

/** Expands the documented dynamic tags in user-configurable alert text. */
public final class AlertText {
	private AlertText() {
	}

	public static String format(String template, String... replacements) {
		String result = template == null ? "" : template;
		for (int i = 0; i + 1 < replacements.length; i += 2) {
			result = result.replace(replacements[i], replacements[i + 1]);
		}
		return result;
	}
}
