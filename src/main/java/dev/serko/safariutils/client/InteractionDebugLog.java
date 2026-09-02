package dev.serko.safariutils.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Focused NPC and container diagnostics used to identify Safari interaction rules. */
public final class InteractionDebugLog {
	private static Screen lastScreen;
	private static String lastContainerState;

	private InteractionDebugLog() {
	}

	/** Registers click and close listeners on each screen while this diagnostic is active. */
	public static void onScreenInit(Minecraft client, Screen screen, int width, int height) {
		if (!active()) return;
		DebugLog.line("INTERACT", "screen open " + describeScreen(screen, width, height));
		DebugLog.line("INTERACT", "screen contents " + containerState(screen));
		ScreenMouseEvents.beforeMouseClick(screen).register(InteractionDebugLog::beforeClick);
		ScreenMouseEvents.afterMouseClick(screen).register((opened, event, consumed) -> {
			if (!active()) return consumed;
			DebugLog.line("INTERACT", "mouse after consumed=" + consumed + " "
				+ describeClick(opened, event) + " contents=" + containerState(opened));
			return consumed;
		});
		ScreenEvents.remove(screen).register(closed -> {
			if (active()) DebugLog.line("INTERACT", "screen close " + describeScreen(closed,
				closed.width, closed.height) + " contents=" + containerState(closed));
		});
	}

	/** Records client-side uses before Hypixel responds or opens a menu. */
	public static void onEntityInteraction(String action, Entity entity, String hand) {
		if (!active() || entity == null) return;
		DebugLog.line("INTERACT", "entity " + action + " hand=" + hand + " type="
			+ EntityTypeIds.key(entity)
			+ " name=\"" + entity.getName().getString() + "\" custom=\""
			+ (entity.getCustomName() == null ? "" : entity.getCustomName().getString())
			+ "\" customVisible=" + entity.isCustomNameVisible() + " uuid=" + entity.getUUID()
			+ " exact=" + decimal(entity.getX()) + "," + decimal(entity.getY()) + ","
			+ decimal(entity.getZ()) + " box=" + decimal(entity.getBbWidth()) + "x"
			+ decimal(entity.getBbHeight()));
	}

	/** Preserves clickable server actions that are lost when chat is flattened to text. */
	public static void onGameMessage(Component message, boolean overlay) {
		if (!active() || message == null) return;
		List<String> actions = new ArrayList<>();
		collectClickActions(message, actions);
		if (!actions.isEmpty()) {
			DebugLog.line("INTERACT", "chat component overlay=" + overlay + " text=\""
				+ message.getString() + "\" clicks=[" + String.join(" | ", actions) + "]");
		}
	}

	/** Captures server-driven slot and menu-data changes without repeating stable screens. */
	public static void tick() {
		if (!active()) {
			lastScreen = null;
			lastContainerState = null;
			return;
		}
		Screen screen = ClientCompat.screen();
		if (screen == null) {
			lastScreen = null;
			lastContainerState = null;
			return;
		}
		String state = containerState(screen);
		if (screen != lastScreen || !state.equals(lastContainerState)) {
			DebugLog.line("INTERACT", "screen state " + describeScreen(screen, screen.width,
				screen.height) + " contents=" + state);
			lastScreen = screen;
			lastContainerState = state;
		}
	}

	private static void beforeClick(Screen screen, MouseButtonEvent event) {
		if (!active()) return;
		DebugLog.line("INTERACT", "mouse before " + describeClick(screen, event)
			+ " contents=" + containerState(screen));
	}

	private static String describeClick(Screen screen, MouseButtonEvent event) {
		return "button=" + event.button() + " x=" + decimal(event.x()) + " y="
			+ decimal(event.y()) + " slot=" + slotAt(screen, event.x(), event.y());
	}

	private static String slotAt(Screen screen, double mouseX, double mouseY) {
		if (!(screen instanceof AbstractContainerScreen<?> container)) return "none";
		int left = intField(container, "leftPos", 0);
		int top = intField(container, "topPos", 0);
		List<Slot> slots = container.getMenu().slots;
		for (int i = 0; i < slots.size(); i++) {
			Slot slot = slots.get(i);
			if (mouseX >= left + slot.x && mouseX < left + slot.x + 16
				&& mouseY >= top + slot.y && mouseY < top + slot.y + 16) {
				return describeSlot(i, slot);
			}
		}
		return "outside";
	}

	private static String describeScreen(Screen screen, int width, int height) {
		if (screen == null) return "none";
		String result = "class=" + screen.getClass().getName() + " title=\""
			+ screen.getTitle().getString() + "\" size=" + width + "x" + height;
		if (screen instanceof AbstractContainerScreen<?> container) {
			AbstractContainerMenu menu = container.getMenu();
			result += " menu=" + menu.getClass().getName() + " containerId=" + menu.containerId
				+ " slots=" + menu.slots.size();
		}
		return result;
	}

	private static String containerState(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> container)) return "not-container";
		AbstractContainerMenu menu = container.getMenu();
		List<String> state = new ArrayList<>(menu.slots.size() + 2);
		for (int i = 0; i < menu.slots.size(); i++) state.add(describeSlot(i, menu.slots.get(i)));
		state.add("carried=" + describeItem(menu.getCarried()));
		String data = dataSlots(menu);
		if (!data.isEmpty()) state.add("data=" + data);
		return "[" + String.join(" | ", state) + "]";
	}

	private static String describeSlot(int menuIndex, Slot slot) {
		return menuIndex + "{container=" + slot.getContainerSlot() + ",xy=" + slot.x + ","
			+ slot.y + ",item=" + describeItem(slot.getItem()) + "}";
	}

	private static String describeItem(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return "empty";
		return stack.getCount() + "x " + BuiltInRegistries.ITEM.getKey(stack.getItem())
			+ " name=\"" + stack.getHoverName().getString() + "\"";
	}

	private static void collectClickActions(Component component, List<String> actions) {
		if (component.getStyle().getClickEvent() != null) {
			actions.add("text=\"" + component.getString() + "\" event="
				+ component.getStyle().getClickEvent());
		}
		for (Component sibling : component.getSiblings()) collectClickActions(sibling, actions);
	}

	private static String dataSlots(AbstractContainerMenu menu) {
		try {
			Field field = field(menu.getClass(), "dataSlots");
			field.setAccessible(true);
			Object value = field.get(menu);
			if (!(value instanceof List<?> slots) || slots.isEmpty()) return "";
			List<String> values = new ArrayList<>(slots.size());
			for (int i = 0; i < slots.size(); i++) {
				Method get = slots.get(i).getClass().getMethod("get");
				values.add(i + "=" + get.invoke(slots.get(i)));
			}
			return "[" + String.join(",", values) + "]";
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return "";
		}
	}

	private static int intField(Object target, String name, int fallback) {
		try {
			Field field = field(target.getClass(), name);
			field.setAccessible(true);
			return field.getInt(target);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return fallback;
		}
	}

	private static Field field(Class<?> type, String name) throws NoSuchFieldException {
		for (Class<?> current = type; current != null; current = current.getSuperclass()) {
			try {
				return current.getDeclaredField(name);
			} catch (NoSuchFieldException ignored) {
			}
		}
		throw new NoSuchFieldException(name);
	}

	private static boolean active() {
		return DebugLog.isEnabled() && ConfigManager.get().advanced.logInterfaces
			&& SafariLocation.inside();
	}

	private static String decimal(double value) {
		return String.format(java.util.Locale.ROOT, "%.2f", value);
	}
}
