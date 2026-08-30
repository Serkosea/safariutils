package dev.serko.safariutils.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Small reflection bridge for client APIs renamed between Minecraft 26.1 and 26.2. */
final class ClientCompat {
	private ClientCompat() {
	}

	static Screen screen() {
		Minecraft client = Minecraft.getInstance();
		try {
			Field field = Minecraft.class.getField("screen");
			return (Screen) field.get(client);
		} catch (ReflectiveOperationException ignored) {
			return (Screen) invoke(client.gui, "screen");
		}
	}

	static void setScreen(Screen screen) {
		Minecraft client = Minecraft.getInstance();
		try {
			Method method = Minecraft.class.getMethod("setScreen", Screen.class);
			method.invoke(client, screen);
		} catch (ReflectiveOperationException ignored) {
			invoke(client.gui, "setScreen", new Class<?>[]{Screen.class}, screen);
		}
	}

	static boolean hudHidden() {
		Minecraft client = Minecraft.getInstance();
		try {
			Field field = client.options.getClass().getField("hideGui");
			return field.getBoolean(client.options);
		} catch (ReflectiveOperationException ignored) {
			Object hud = field(client.gui, "hud");
			return Boolean.TRUE.equals(invoke(hud, "isHidden"));
		}
	}

	static void addSystemMessage(Component message) {
		Minecraft client = Minecraft.getInstance();
		Object chat;
		try {
			chat = invoke(client.gui, "getChat");
		} catch (IllegalStateException ignored) {
			chat = invoke(field(client.gui, "hud"), "getChat");
		}
		invoke(chat, "addClientSystemMessage", new Class<?>[]{Component.class}, message);
	}

	static Camera camera() {
		Object renderer = Minecraft.getInstance().gameRenderer;
		try {
			return (Camera) invoke(renderer, "getMainCamera");
		} catch (IllegalStateException ignored) {
			return (Camera) invoke(renderer, "mainCamera");
		}
	}

	private static Object field(Object target, String name) {
		try {
			return target.getClass().getField(name).get(target);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Missing compatibility field " + name, exception);
		}
	}

	private static Object invoke(Object target, String name) {
		return invoke(target, name, new Class<?>[0]);
	}

	private static Object invoke(Object target, String name, Class<?>[] types, Object... arguments) {
		try {
			return target.getClass().getMethod(name, types).invoke(target, arguments);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Missing compatibility method " + name, exception);
		}
	}
}
