package dev.serko.safariutils.client;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import dev.serko.safariutils.SafariUtils;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
/** Minecraft pipeline bindings; marker policy and geometry remain shared. */
final class MarkerPipelines {
	private static final RenderPipeline LINES_THROUGH_WALLS = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(SafariUtils.MOD_ID, "pipeline/lines_through_walls"))
            // Unlike earlier renderers, 26.3 LINES_SNIPPET declares no color attachment.
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build());

	static final RenderType LINES = RenderType.create(
		SafariUtils.MOD_ID + ":lines_through_walls",
		RenderSetup.builder(LINES_THROUGH_WALLS)
			.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
			.createRenderSetup());

	/** Textured translucent beacon pipeline that remains visible through terrain. */
	private static final RenderPipeline BEACON_THROUGH_WALLS_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.BEACON_BEAM_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(SafariUtils.MOD_ID,
				"pipeline/beacon_through_walls"))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build());

	static final RenderType BEACON_THROUGH_WALLS = RenderType.create(
		SafariUtils.MOD_ID + ":beacon_through_walls",
		RenderSetup.builder(BEACON_THROUGH_WALLS_PIPELINE)
			.withTexture("Sampler0", BeaconRenderer.BEAM_LOCATION)
			.sortOnUpload()
			.createRenderSetup());

	/** Ordinary depth-tested beam used when Safe Mode requires visual confirmation. */
	private static final RenderPipeline BEACON_DEPTH_TESTED_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.BEACON_BEAM_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(SafariUtils.MOD_ID,
				"pipeline/beacon_depth_tested"))
            .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.build());

	static final RenderType BEACON_DEPTH_TESTED = RenderType.create(
		SafariUtils.MOD_ID + ":beacon_depth_tested",
		RenderSetup.builder(BEACON_DEPTH_TESTED_PIPELINE)
			.withTexture("Sampler0", BeaconRenderer.BEAM_LOCATION)
			.sortOnUpload()
			.createRenderSetup());

    /** Never write marker depth: real models must remain able to cover early geometry. */
    private static final RenderPipeline DEPTH_LINES_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(SafariUtils.MOD_ID, "pipeline/marker_depth_lines"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false)).build());
    static final RenderType DEPTH_LINES = RenderType.create(SafariUtils.MOD_ID + ":marker_depth_lines",
        RenderSetup.builder(DEPTH_LINES_PIPELINE).createRenderSetup());
    private static final RenderPipeline FACE_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withCull(false)
            .withLocation(Identifier.fromNamespaceAndPath(SafariUtils.MOD_ID, "pipeline/marker_face"))
            .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false)).build());

    private static final RenderPipeline FACE_THROUGH_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withCull(false)
            .withLocation(Identifier.fromNamespaceAndPath(SafariUtils.MOD_ID, "pipeline/marker_face_through"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false)).build());
    static final RenderType FACE_THROUGH = RenderType.create(SafariUtils.MOD_ID + ":face_through",
        RenderSetup.builder(FACE_THROUGH_PIPELINE).createRenderSetup());

	/** Depth-tested fill used for the visible top face of a floor drop. */
	static final RenderType FILLED_FACE = RenderType.create(
		SafariUtils.MOD_ID + ":filled_face",
		RenderSetup.builder(FACE_PIPELINE)
			.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
			.createRenderSetup());

}
