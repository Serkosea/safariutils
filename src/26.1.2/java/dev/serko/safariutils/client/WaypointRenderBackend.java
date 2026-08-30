package dev.serko.safariutils.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Minecraft 26.1 immediate-buffer rendering adapter. */
final class WaypointRenderBackend {
	private final MultiBufferSource.BufferSource buffers;
	private final PoseStack poses;

	WaypointRenderBackend(LevelRenderContext context) {
		poses = context.poseStack();
		buffers = context.bufferSource();
	}

	static void register(Consumer<LevelRenderContext> renderer) {
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(renderer::accept);
	}

	void geometry(RenderType type, BiConsumer<PoseStack.Pose, VertexConsumer> draw) {
		draw.accept(poses.last(), buffers.getBuffer(type));
	}

	void flush(RenderType type) {
		buffers.endBatch(type);
	}

	Vec3 cameraPosition() {
		return ClientCompat.camera().position();
	}

	Quaternionf cameraRotation() {
		return ClientCompat.camera().rotation();
	}

	void text(PoseStack poses, FormattedCharSequence text, float x, Font.DisplayMode mode,
				  int colour, int background, int light) {
		Minecraft.getInstance().font.drawInBatch(text, x, 0, colour, false,
			new Matrix4f(poses.last().pose()), buffers, mode, background, light);
	}
}
