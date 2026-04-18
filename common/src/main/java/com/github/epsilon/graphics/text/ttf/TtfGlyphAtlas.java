package com.github.epsilon.graphics.text.ttf;

import com.github.epsilon.graphics.LuminTexture;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;

import java.util.OptionalDouble;

public class TtfGlyphAtlas {

    private static final int SIZE = 512;
    private final LuminTexture texture;

    private int currentX = 0;
    private int currentY = 0;
    private int currentRowHeight = 0;

    public TtfGlyphAtlas(int atlasId) {
        final var texture = RenderSystem.getDevice().createTexture(
                () -> "Lumin-TtfGlyphAtlas",
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                GpuFormat.R8_UNORM,
                SIZE, SIZE,
                1, 1
        );

        final var textureView = RenderSystem.getDevice().createTextureView(texture);
        final var sampler = RenderSystem.getDevice().createSampler(
                AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.LINEAR,
                1, OptionalDouble.empty()
        );

        this.texture = new LuminTexture(texture, textureView, sampler);
    }

    /**
     * Try to append a glyph to atlas
     * <p>
     * Return null if glyph atlas is full
     */
    public GlyphUV appendGlyph(TtfGlyph glyph) {
        if (glyph.glyphData() == null) return null;

        if (currentX + glyph.width() >= SIZE) {
            currentX = 0;
            currentY += currentRowHeight;
            currentRowHeight = 0;
        }

        // Return null if glyph atlas is full
        if (currentY + glyph.height() >= SIZE) {
            return null;
        }

        RenderSystem.getDevice().createCommandEncoder().writeToTexture(
                this.texture.getTexture(),
                glyph.glyphData(),
                NativeImage.Format.LUMINANCE,
                0,
                0,
                currentX, currentY,
                glyph.width(),
                glyph.height()
        );

        int spacePixel = 1;

        GlyphUV uv = new GlyphUV(
                (float) (currentX + spacePixel) / SIZE,
                (float) (currentY + spacePixel) / SIZE,
                (float) (currentX + glyph.width() - 2 * spacePixel) / SIZE,
                (float) (currentY + glyph.height() - 2 * spacePixel) / SIZE
        );

        currentX += glyph.width();
        currentRowHeight = Math.max(currentRowHeight, glyph.height());

        return uv;
    }

    public LuminTexture getTexture() {
        return texture;
    }

    public void destroy() {
        texture.close();
    }

    public record GlyphUV(float u0, float v0, float u1, float v1) {
    }

}
