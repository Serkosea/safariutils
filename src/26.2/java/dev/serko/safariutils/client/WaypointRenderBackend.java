package dev.serko.safariutils.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Minecraft 26.2 render-state submission adapter. */
final class WaypointRenderBackend {
	private final PoseStack poses;
	private final SubmitNodeCollector nodes;
	private final Vec3 cameraPosition;
	private final Quaternionf cameraRotation;

	WaypointRenderBackend(LevelRenderContext context) {
		poses = context.poseStack();
		nodes = context.submitNodeCollector();
		cameraPosition = context.levelState().cameraRenderState.pos;
		cameraRotation = new Quaternionf(context.levelState().cameraRenderState.orientation);
	}

	static void register(Consumer<LevelRenderContext> renderer) {
		LevelRenderEvents.COLLECT_SUBMITS.register(renderer::accept);
	}

	void geometry(RenderType type, BiConsumer<PoseStack.Pose, VertexConsumer> draw) {
		nodes.submitCustomGeometry(poses, type, draw::accept);
	}

	void flush(RenderType type) {
		// Submission is deferred and needs no immediate-buffer flush.
	}

	Vec3 cameraPosition() {
		return cameraPosition;
	}

	Quaternionf cameraRotation() {
		return cameraRotation;
	}

	void text(PoseStack poses, FormattedCharSequence text, float x, Font.DisplayMode mode,
				  int colour, int background, int light) {
		nodes.submitText(poses, x, 0, text, false, mode, light, colour, background, 0);
	}
}
