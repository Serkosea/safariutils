package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Accepts Hideyho's current prompt before its clickable choices reach chat. */
public final class HideyhoAutoAccept {
	private static final String PROMPT_START = "Select an option:";
	private static final String ACCEPT_COMMAND = "selectnpcoption hideyho r_4_1";
	private static final java.util.regex.Pattern WHITESPACE = java.util.regex.Pattern.compile("\\s+");
	private static long lastAcceptedAt;

	private HideyhoAutoAccept() {
	}

	/** Returns false only for the Hideyho choice line consumed by this feature. */
	public static boolean allow(Component message, boolean overlay) {
		String line = WHITESPACE.matcher(message.getString()).replaceAll(" ").trim();
		boolean hideyhoPrompt = hasAcceptAction(message)
			|| (line.contains(PROMPT_START) && line.contains("[Sure]") && line.contains("[No thanks...]"));
		if (overlay || !ConfigManager.get().gameplay.autoAcceptHideyho || !hideyhoPrompt) return true;

		long now = System.currentTimeMillis();
		if (now - lastAcceptedAt < 1_000L) return false;
		lastAcceptedAt = now;
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && client.getConnection() != null) {
			client.getConnection().sendCommand(ACCEPT_COMMAND);
			ClientMessages.send("Accepted Hide 'N Seek", ClientMessages.Tone.SUCCESS);
		}
		return false;
	}

	private static boolean hasAcceptAction(Component component) {
		if (component.getStyle().getClickEvent() != null
			&& component.getStyle().getClickEvent().toString().contains("/" + ACCEPT_COMMAND)) return true;
		for (Component sibling : component.getSiblings()) {
			if (hasAcceptAction(sibling)) return true;
		}
		return false;
	}
}
