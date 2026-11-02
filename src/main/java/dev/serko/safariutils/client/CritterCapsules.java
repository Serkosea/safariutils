package dev.serko.safariutils.client;

import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;

/** Positive capsule evidence shared by capture tracking and discovery, for every thrower. */
final class CritterCapsules {
	private CritterCapsules() { }
	static boolean matches(Display.ItemDisplay display) {
		Display.ItemDisplay.ItemRenderState state = display.itemRenderState();
		if (state == null) return false;
		ItemStack stack = state.itemStack();
		return !stack.isEmpty() && stack.getHoverName().getString().endsWith("Critter Capsule");
	}
}
