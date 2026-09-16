package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;

/** Accepts Hideyho's current prompt before its clickable choices reach chat. */
public final class HideyhoAutoAccept {
	private static final String DIALOGUE_RESPONSE = "skyblock:dialogue_response";
	private static final String LEGACY_COMMAND_START = "selectnpcoption hideyho ";
	private static long lastAcceptedAt;

	private HideyhoAutoAccept() {
	}

	/** Returns false only for the Hideyho choice line consumed by this feature. */
	public static boolean allow(Component message, boolean overlay) {
		ClickEvent accept = findAcceptAction(message);
		if (overlay || !ConfigManager.get().gameplay.autoAcceptHideyho || accept == null) return true;

		long now = System.currentTimeMillis();
		if (now - lastAcceptedAt < 1_000L) return false;
		if (!sendAccept(accept)) return true;
		lastAcceptedAt = now;
		ClientMessages.send("Accepted Hide 'N Seek", ClientMessages.Tone.SUCCESS);
		return false;
	}

	/** Returns true only when a validated action was actually handed to the connection. */
	private static boolean sendAccept(ClickEvent accept) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.getConnection() == null) return false;
		try {
			if (accept instanceof ClickEvent.Custom custom) {
				client.getConnection().send(new ServerboundCustomClickActionPacket(
					custom.id(), custom.payload()));
			} else if (accept instanceof ClickEvent.RunCommand command) {
				String value = command.command();
				client.getConnection().sendCommand(value.startsWith("/") ? value.substring(1) : value);
			} else return false;
			return true;
		} catch (RuntimeException error) {
			DebugLog.line("INTERACT", "Hideyho auto-accept dispatch failed: " + error);
			return false;
		}
	}

	/** Finds only the affirmative action belonging to Hideyho, preserving its live payload. */
	private static ClickEvent findAcceptAction(Component component) {
		ClickEvent click = component.getStyle().getClickEvent();
		if ("[Sure]".equals(component.getString().trim()) && isHideyhoAccept(click)) return click;
		for (Component sibling : component.getSiblings()) {
			ClickEvent found = findAcceptAction(sibling);
			if (found != null) return found;
		}
		return null;
	}

	private static boolean isHideyhoAccept(ClickEvent click) {
		if (click instanceof ClickEvent.Custom custom) {
			if (!DIALOGUE_RESPONSE.equals(custom.id().toString())) return false;
			return custom.payload().orElse(null) instanceof CompoundTag payload
				&& "hideyho".equalsIgnoreCase(payload.getStringOr("npcId", ""))
				&& !payload.getStringOr("responseKey", "").isBlank();
		}
		if (click instanceof ClickEvent.RunCommand command) {
			String value = command.command();
			if (value.startsWith("/")) value = value.substring(1);
			return value.startsWith(LEGACY_COMMAND_START);
		}
		return false;
	}
}
