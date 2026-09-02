package dev.serko.safariutils.client;

import dev.serko.safariutils.session.SessionManager;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

import java.lang.reflect.Field;

/** Stops a ticket submission while a known party member is missing from Safari. */
public final class TicketProtection {
	private static final double MANAGER_X = -51.5;
	private static final double MANAGER_Y = 68.0;
	private static final double MANAGER_Z = 21.5;
	private static long lastBlockedMessageAt;
	// Cache the field lookup, not its value: resizing the menu can change its origin.
	private static final ClassValue<java.util.Map<String, Field>> SCREEN_FIELDS = new ClassValue<>() {
		@Override protected java.util.Map<String, Field> computeValue(Class<?> screenClass) {
			java.util.Map<String, Field> fields = new java.util.HashMap<>();
			for (String name : new String[]{"leftPos", "topPos"}) {
				for (Class<?> type = screenClass; type != null; type = type.getSuperclass()) {
					try {
						Field field = type.getDeclaredField(name);
						field.setAccessible(true);
						fields.put(name, field);
						break;
					} catch (ReflectiveOperationException ignored) {
					}
				}
			}
			return java.util.Map.copyOf(fields);
		}
	};

	private TicketProtection() {}

	public static void onScreenInit(Screen screen) {
		if (!isEntryScreen(screen)) return;
		ScreenMouseEvents.allowMouseClick(screen).register(TicketProtection::allowMouseClick);
		ScreenKeyboardEvents.allowKeyPress(screen).register(TicketProtection::allowKeyPress);
	}

	public static boolean blockManagerInteraction(Entity entity) {
		if (!shouldBlock() || !PartyRosterWatch.localPlayerIsLeader()
			|| !(entity instanceof Player)) return false;
		if (entity.distanceToSqr(MANAGER_X, MANAGER_Y, MANAGER_Z) > 9.0) return false;
		blockedMessage();
		return true;
	}

	private static boolean allowMouseClick(Screen screen, MouseButtonEvent event) {
		if (!shouldBlock()) return true;
		Slot slot = slotAt(screen, event.x(), event.y());
		if (slot == null || !slot.getItem().getHoverName().getString().endsWith("Safari Experience")) {
			return true;
		}
		blockedMessage();
		return false;
	}

	private static boolean allowKeyPress(Screen screen, KeyEvent event) {
		if (!shouldBlock()) return true;
		int key = event.key();
		if ((key < 49 || key > 57) && (key < 321 || key > 329)) return true;
		blockedMessage();
		return false;
	}

	private static boolean shouldBlock() {
		return ConfigManager.get().gameplay.protectSafariTicket && SafariLocation.inside()
			&& SessionManager.current() == null && PartyRosterWatch.known()
			&& SafariPartyWatch.joinedPlayers() < PartyRosterWatch.expectedPlayers();
	}

	private static boolean isEntryScreen(Screen screen) {
		return screen instanceof AbstractContainerScreen<?>
			&& "Critter Safari Entry".equals(screen.getTitle().getString());
	}

	private static Slot slotAt(Screen screen, double mouseX, double mouseY) {
		if (!(screen instanceof AbstractContainerScreen<?> container)) return null;
		int left = intField(container, "leftPos");
		int top = intField(container, "topPos");
		for (Slot slot : container.getMenu().slots) {
			if (mouseX >= left + slot.x && mouseX < left + slot.x + 16
				&& mouseY >= top + slot.y && mouseY < top + slot.y + 16) return slot;
		}
		return null;
	}

	private static int intField(Object target, String name) {
		Field field = SCREEN_FIELDS.get(target.getClass()).get(name);
		try {
			return field == null ? 0 : field.getInt(target);
		} catch (IllegalAccessException ignored) {
			return 0;
		}
	}

	private static void blockedMessage() {
		long now = System.currentTimeMillis();
		if (now - lastBlockedMessageAt < 500L) return;
		lastBlockedMessageAt = now;
		ClientMessages.send("Your full party has not joined the Safari", ClientMessages.Tone.ERROR);
	}
}
