package dev.serko.safariutils.client;
abstract class GuiPipelineState implements net.minecraft.client.renderer.state.gui.GuiElementRenderState {
    @Override public com.mojang.blaze3d.pipeline.RenderPipeline pipeline() {return net.minecraft.client.renderer.RenderPipelines.GUI;}
}
