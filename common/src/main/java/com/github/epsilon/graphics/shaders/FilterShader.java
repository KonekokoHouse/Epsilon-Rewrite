package com.github.epsilon.graphics.shaders;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.RenderPipelines;

import java.awt.*;
import java.util.OptionalInt;

import static com.github.epsilon.Constants.mc;

public class FilterShader {

    public static final FilterShader INSTANCE = new FilterShader();

    private static final int UNIFORMS_SIZE = new Std140SizeCalculator()
            .putVec4()
            .get();

    private RenderPipeline pipeline;
    private GpuBuffer uniforms;
    private RenderTarget input;

    private void ensureProgram() {
        if (this.uniforms == null) {
            this.uniforms = RenderSystem.getDevice().createBuffer(() -> "EpsilonFilterUniforms", GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM, UNIFORMS_SIZE);
        }
        if (this.pipeline == null) {
            this.pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(ResourceLocationUtils.getIdentifier("pipeline/filter"))
                    .withVertexShader(ResourceLocationUtils.getIdentifier("fullscreen"))
                    .withFragmentShader(ResourceLocationUtils.getIdentifier("filter"))
                    .withUniform("FilterColor", UniformType.UNIFORM_BUFFER)
                    .withSampler("InputSampler")
                    .withCull(false)
                    .build();
        }
    }

    private void ensureInput(RenderTarget framebuffer) {
        int fbWidth = framebuffer.width;
        int fbHeight = framebuffer.height;

        if (this.input == null) {
            this.input = new TextureTarget("Epsilon Filter Input", fbWidth, fbHeight, false);
        }

        if (this.input.width != fbWidth || this.input.height != fbHeight) {
            this.input.resize(fbWidth, fbHeight);
        }
    }

    public void renderMainTarget(Color color) {
        render(mc.getMainRenderTarget(), color);
    }

    public void render(RenderTarget framebuffer, Color color) {
        this.ensureProgram();

        if (framebuffer == null || color == null || framebuffer.width <= 0 || framebuffer.height <= 0) {
            return;
        }

        if (framebuffer.getColorTexture() == null || framebuffer.getColorTextureView() == null) {
            return;
        }

        this.ensureInput(framebuffer);

        if (this.input.getColorTexture() == null || this.input.getColorTextureView() == null) {
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(
                framebuffer.getColorTexture(),
                this.input.getColorTexture(),
                0, 0, 0, 0, 0,
                framebuffer.width, framebuffer.height
        );

        try (GpuBuffer.MappedView view = encoder.mapBuffer(this.uniforms, false, true)) {
            Std140Builder.intoBuffer(view.data())
                    .putVec4(color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, color.getAlpha() / 255.0f);
        }

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "Epsilon Filter",
                framebuffer.getColorTextureView(),
                OptionalInt.empty()
        )) {
            renderPass.setPipeline(this.pipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("FilterColor", this.uniforms);
            renderPass.bindTexture("InputSampler", this.input.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            renderPass.draw(0, 6);
        }
    }

}
