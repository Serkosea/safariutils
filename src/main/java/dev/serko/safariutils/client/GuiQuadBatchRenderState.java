package dev.serko.safariutils.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.serko.safariutils.mixin.GuiGraphicsExtractorAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/** One GUI render state containing any number of solid coloured rectangles. */
final class GuiQuadBatchRenderState implements GuiElementRenderState {
	private static final TextureSetup NO_TEXTURE = TextureSetup.noTexture();

	private final Matrix3x2fc pose;
	private final int originX;
	private final int originY;
	private final int[] quads;
	private final int quadLength;
	private final ScreenRectangle bounds;
	private final ScreenRectangle scissorArea;

	private GuiQuadBatchRenderState(Matrix3x2fc pose, int originX, int originY,
			int width, int height, int[] quads, int quadLength, ScreenRectangle scissorArea) {
		this.pose = pose;
		this.originX = originX;
		this.originY = originY;
		this.quads = quads;
		this.quadLength = quadLength;
		this.bounds = new ScreenRectangle(originX, originY, width, height).transformMaxBounds(pose);
		this.scissorArea = scissorArea;
	}

	static void submit(GuiGraphicsExtractor graphics, int originX, int originY,
			int width, int height, int[] quads) {
		submit(graphics, originX, originY, width, height, quads, null);
	}

	static void submit(GuiGraphicsExtractor graphics, int originX, int originY,
			int width, int height, int[] quads, int quadLength) {
		submit(graphics, originX, originY, width, height, quads, quadLength, null);
	}

	static void submit(GuiGraphicsExtractor graphics, int originX, int originY,
			int width, int height, int[] quads, ScreenRectangle scissorArea) {
		submit(graphics, originX, originY, width, height, quads, quads.length, scissorArea);
	}

	static void submit(GuiGraphicsExtractor graphics, int originX, int originY,
			int width, int height, int[] quads, int quadLength, ScreenRectangle scissorArea) {
		int used = Math.clamp(quadLength - Math.floorMod(quadLength, 5), 0, quads.length);
		if (used == 0 || width <= 0 || height <= 0) return;
		Matrix3x2f pose = new Matrix3x2f(graphics.pose());
		GuiQuadBatchRenderState state = new GuiQuadBatchRenderState(
			pose, originX, originY, width, height, quads, used, scissorArea);
		((GuiGraphicsExtractorAccessor) graphics).safariutils$getGuiRenderState().addGuiElement(state);
	}

	@Override
	public void buildVertices(VertexConsumer vertices) {
		for (int i = 0; i < quadLength; i += 5) {
			float x0 = originX + quads[i];
			float y0 = originY + quads[i + 1];
			float x1 = originX + quads[i + 2];
			float y1 = originY + quads[i + 3];
			int colour = quads[i + 4];
			vertices.addVertexWith2DPose(pose, x0, y0).setColor(colour);
			vertices.addVertexWith2DPose(pose, x0, y1).setColor(colour);
			vertices.addVertexWith2DPose(pose, x1, y1).setColor(colour);
			vertices.addVertexWith2DPose(pose, x1, y0).setColor(colour);
		}
	}

	@Override
	public RenderPipeline pipeline() {
		return RenderPipelines.GUI;
	}

	@Override
	public TextureSetup textureSetup() {
		return NO_TEXTURE;
	}

	@Override
	public ScreenRectangle scissorArea() {
		return scissorArea;
	}

	@Override
	public ScreenRectangle bounds() {
		return bounds;
	}
}
