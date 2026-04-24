package com.github.epsilon.graphics;

import com.github.epsilon.assets.holders.RenderTargetHolder;
import com.github.epsilon.assets.holders.RendererHolder;
import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.graphics.vulkan.LuminVulkanContext;
import com.github.epsilon.modules.impl.combat.crystalaura.CrystalAura;
import com.github.epsilon.modules.impl.world.ComputeTest;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import org.joml.Vector4f;

import javax.annotation.Nullable;
import java.util.OptionalDouble;

import static com.github.epsilon.Constants.mc;

public class LuminRenderSystem {

    public static final Projection guiOrthoProjection = new Projection();

    private static final ProjectionMatrixBuffer guiProjectionMatrixBuffer = new ProjectionMatrixBuffer("lumin-gui");

    @Nullable
    private static LuminRenderTarget activeTarget = null;

    public static final LuminVulkanContext vulkanContext = new LuminVulkanContext();

    public static void setActiveTarget(@Nullable LuminRenderTarget target) {
        activeTarget = target;
    }

    public static void destroyAll() {
        CrystalAura.INSTANCE.destroy();
        ComputeTest.INSTANCE.destroy();

        guiProjectionMatrixBuffer.close();
        RenderTargetHolder.INSTANCE.destroyAll();
        StaticFontLoader.destroyAll();
        RendererHolder.INSTANCE.destroyAll();
        vulkanContext.destroy();
    }

    public static void destroyAll() {
        guiProjectionMatrixBuffer.close();
        RenderTargetHolder.INSTANCE.destroyAll();
        RendererHolder.INSTANCE.destroyAll();
    }

    @Nullable
    public static LuminRenderTarget getActiveTarget() {
        return activeTarget;
    }

    public static void applyOrthoProjection() {
        WindowRenderState windowState = mc.gameRenderer.gameRenderState().windowRenderState;

        guiOrthoProjection
                .setupOrtho(-1000.0F, 1000.0F,
                        (float) windowState.width / windowState.guiScale,
                        (float) windowState.height / windowState.guiScale,
                        true
                );
        RenderSystem.setProjectionMatrix(
                guiProjectionMatrixBuffer.getBuffer(guiOrthoProjection), ProjectionType.ORTHOGRAPHIC);
    }

    /**
     * 获取当前活动目标的 colorTextureView 和 depthTextureView。
     * 如果设置了 activeTarget，则使用 activeTarget；否则使用主 RenderTarget。
     */
    public static GpuTextureView resolveColorView() {
        if (activeTarget != null) return activeTarget.colorView();
        return mc.mainRenderTarget().getColorTextureView();
    }

    @Nullable
    public static GpuTextureView resolveDepthView() {
        if (activeTarget != null) return activeTarget.depthView();
        return Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTextureView();
    }

    public static QuadRenderingInfo prepareQuadRendering(int vertexCount) {
        LuminRenderSystem.applyOrthoProjection();

        GpuTextureView colorView = resolveColorView();
        GpuTextureView depthView = resolveDepthView();
        if (colorView == null) return null;

        final var indexCount = vertexCount / 4 * 6;

        RenderSystem.AutoStorageIndexBuffer autoIndices =
                RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer ibo = autoIndices.getBuffer(indexCount);

        GpuBufferSlice dynamicUniforms = RenderSystem.getDynamicUniforms().writeTransform(
                RenderSystem.getModelViewMatrixCopy(),
                new Vector4f(1, 1, 1, 1),
                new Vector3f(0, 0, 0),
                TextureTransform.DEFAULT_TEXTURING.createMatrix()
        );

        return new QuadRenderingInfo(colorView, depthView, autoIndices, ibo, indexCount, dynamicUniforms);
    }

    public record QuadRenderingInfo(
            GpuTextureView colorView,
            @Nullable GpuTextureView depthView,
            RenderSystem.AutoStorageIndexBuffer autoIndices,
            GpuBuffer ibo,
            int indexCount,
            GpuBufferSlice dynamicUniforms
    ) {
    }

    public static final class LuminRenderTarget implements AutoCloseable {

        private LuminTexture colorTexture;
        private GpuTexture depthTexture;
        private GpuTextureView depthView;
        private final Identifier identifier;
        private int width;
        private int height;

        private LuminRenderTarget(String name, int width, int height) {
            this.width = width;
            this.height = height;
            this.identifier = ResourceLocationUtils.getIdentifier("epsilon-rt" + name);
            createTextures();
        }

        public static LuminRenderTarget create(String name, int width, int height) {
            return RenderTargetHolder.INSTANCE.register(new LuminRenderTarget(name, width, height));
        }

        private void createTextures() {
            var device = RenderSystem.getDevice();

            final var colorTexture = device.createTexture(
                    "lumin-rt-color",
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC,
                    GpuFormat.RGBA8_UNORM,
                    width, height, 1, 1
            );
            final var colorView = device.createTextureView(colorTexture);

            depthTexture = device.createTexture(
                    "lumin-rt-depth",
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC,
                    GpuFormat.D32_FLOAT,
                    width, height, 1, 1
            );
            depthView = device.createTextureView(depthTexture);

            final var sampler = RenderSystem.getDevice().createSampler(
                    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.NEAREST, FilterMode.NEAREST,
                    1, OptionalDouble.empty()
            );

            this.colorTexture = new LuminTexture(colorTexture, colorView, sampler);

            mc.getTextureManager().register(identifier, getColorTexture());
        }

        public void resize(int newWidth, int newHeight) {
            if (newWidth == width && newHeight == height) return;
            destroyTextures();
            width = newWidth;
            height = newHeight;
            createTextures();
        }

        public Identifier getIdentifier() {
            return identifier;
        }

        public void clear() {
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.clearColorAndDepthTextures(colorTexture.getTexture(), 0, depthTexture, 1.0);
        }

        public GpuTextureView colorView() {
            return colorTexture.getTextureView();
        }

        public GpuTextureView depthView() {
            return depthView;
        }

        public GpuTexture colorTexture() {
            return colorTexture.getTexture();
        }

        public GpuSampler sampler() {
            return colorTexture.getSampler();
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public LuminTexture getColorTexture() {
            return colorTexture;
        }

        private void destroyTextures() {
            mc.getTextureManager().release(identifier);
            if (depthView != null) depthView.close();
            if (depthTexture != null) depthTexture.close();
        }

        @Override
        public void close() {
            destroyTextures();
        }
    }

}
