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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Minecraft 26.2 render-state submission adapter. */
final class WaypointRenderBackend {
	private final PoseStack poses;
	private final SubmitNodeCollector nodes;
	private final Vec3 cameraPosition;
	private final Quaternionf cameraRotation;
	private final Map<RenderType, List<Geometry>> pending = new HashMap<>();

	private record Geometry(PoseStack.Pose pose,
		BiConsumer<PoseStack.Pose, VertexConsumer> draw) {
	}

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
		pending.computeIfAbsent(type, ignored -> new ArrayList<>())
			.add(new Geometry(poses.last().copy(), draw));
	}

	void flush(RenderType type) {
		List<Geometry> geometry = pending.remove(type);
		if (geometry == null || geometry.isEmpty()) return;
		// One render-state submission per type replaces one submission per box while
		// retaining every box's own copied transform.
		nodes.submitCustomGeometry(poses, type, (ignored, vertices) -> {
			for (Geometry item : geometry) item.draw().accept(item.pose(), vertices);
		});
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
