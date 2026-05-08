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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.OptionalInt;

public class FXAAShader {

    public static final FXAAShader INSTANCE = new FXAAShader();

    private static final Identifier vertexShader = ResourceLocationUtils.getIdentifier("fullscreen");
    private static final Identifier fragmentShader = ResourceLocationUtils.getIdentifier("fxaa");

    private static final int UNIFORMS_SIZE = new Std140SizeCalculator()
            .putVec4()
            .get();

    private final Minecraft mc = Minecraft.getInstance();

    private RenderPipeline pipeline;
    private MappableRingBuffer uniforms;
    private RenderTarget input;

    private void ensureProgram() {
        if (this.uniforms == null) {
            this.uniforms = new MappableRingBuffer(() -> "EpsilonFXAAUniforms", GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM, UNIFORMS_SIZE);
        }
        if (this.pipeline == null) {
            this.pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(ResourceLocationUtils.getIdentifier("pipeline/fxaa"))
                    .withVertexShader(vertexShader)
                    .withFragmentShader(fragmentShader)
                    .withUniform("FxaaInfo", UniformType.UNIFORM_BUFFER)
                    .withSampler("InputSampler")
                    .withCull(false)
                    .build();
        }
    }

    private void ensureInput(RenderTarget framebuffer) {
        int fbWidth = framebuffer.width;
        int fbHeight = framebuffer.height;

        if (this.input == null) {
            this.input = new TextureTarget("Epsilon FXAA Input", fbWidth, fbHeight, false);
        }

        if (this.input.width != fbWidth || this.input.height != fbHeight) {
            this.input.resize(fbWidth, fbHeight);
        }
    }

    public void renderMainTarget() {
        render(this.mc.getMainRenderTarget());
    }

    public void render(RenderTarget framebuffer) {
        this.ensureProgram();

        if (framebuffer == null || framebuffer.width <= 0 || framebuffer.height <= 0) {
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

        try (GpuBuffer.MappedView view = encoder.mapBuffer(this.uniforms.currentBuffer(), false, true)) {
            Std140Builder.intoBuffer(view.data())
                    .putVec4(framebuffer.width, framebuffer.height, 1.0f / framebuffer.width, 1.0f / framebuffer.height);
        }

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "Epsilon FXAA",
                framebuffer.getColorTextureView(),
                OptionalInt.empty()
        )) {
            renderPass.setPipeline(this.pipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("FxaaInfo", this.uniforms.currentBuffer());
            renderPass.bindTexture("InputSampler", this.input.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            renderPass.draw(0, 6);
        }

        this.uniforms.rotate();
    }

}

