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

    /** Cached cross-version container click entry point; fail closed when the API shape changes. */
    private record ContainerClick(Method method, Object pickup) { }
    private static final ClassValue<ContainerClick> CONTAINER_CLICK = new ClassValue<>() {
        @Override protected ContainerClick computeValue(Class<?> type) {
            for (Method method : type.getMethods()) {
                if (!method.getName().equals("handleContainerInput") && !method.getName().equals("handleInventoryMouseClick")) continue;
                Class<?>[] args = method.getParameterTypes();
                if (args.length != 5 || args[0] != int.class || args[1] != int.class || args[2] != int.class || !args[3].isEnum()
                    || !args[4].isAssignableFrom(net.minecraft.client.player.LocalPlayer.class)) continue;
                for (Object constant : args[3].getEnumConstants())
                    if (((Enum<?>) constant).name().equals("PICKUP")) return new ContainerClick(method, constant);
            }
            return new ContainerClick(null, null);
        }
    };
    static boolean pickupSlot(int containerId, int slot) {
        Minecraft client = Minecraft.getInstance();
        if (client.gameMode == null || client.player == null || client.player.containerMenu.containerId != containerId) return false;
        ContainerClick click = CONTAINER_CLICK.get(client.gameMode.getClass());
        if (click.method() == null) return false;
        try {
            click.method().invoke(client.gameMode, containerId, slot, 0, click.pickup(), client.player);
            return true;
        } catch (ReflectiveOperationException failure) {
            DebugLog.line("TICKET/SELECT", "container input failed: " + failure.getClass().getSimpleName());
            return false;
        }
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

    static void rebuildWorldGeometry() {
        var client=Minecraft.getInstance();
        // Vanilla's full rebuild API is shared with Sodium; only run when the setting/area changes.
        try { invoke(client.levelRenderer,"allChanged"); }
        catch(IllegalStateException olderMethodAbsent) {
            invoke(client.levelRenderer,"invalidateCompiledGeometry",
                new Class<?>[]{net.minecraft.client.multiplayer.ClientLevel.class,net.minecraft.client.Options.class,Camera.class,net.minecraft.client.color.block.BlockColors.class},
                client.level,client.options,camera(),client.getBlockColors());
        }
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
