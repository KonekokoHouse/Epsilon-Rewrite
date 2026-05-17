package com.github.epsilon.graphics;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;


public class LuminRenderPipelines {

    private final static BindGroupLayout TTF_INFO_UBO = BindGroupLayout.builder()
            .withUniform("TtfInfo", UniformType.UNIFORM_BUFFER)
            .build();

    private final static RenderPipeline.Snippet NO_BLEND_DEPTH_SNIPPET = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .buildSnippet();

    public final static RenderPipeline RECTANGLE = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/rectangle"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withVertexShader(ResourceLocationUtils.getIdentifier("rectangle"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("rectangle"))
            .withCull(false)
            .build();

    private final static RenderPipeline.Snippet TTF_SNIPPET = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withBindGroupLayout(TTF_INFO_UBO)
            .buildSnippet();

    public final static RenderPipeline TTF_FONT = RenderPipeline.builder(TTF_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/ttf_font"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withVertexShader(ResourceLocationUtils.getIdentifier("ttf_font"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("ttf_font"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withCull(false)
            .build();

    public final static RenderPipeline ROUND_RECT = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/round_rectangle"))
            .withVertexBinding(0, LuminVertexFormats.ROUND_RECT)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withVertexShader(ResourceLocationUtils.getIdentifier("round_rectangle"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("round_rectangle"))
            .withCull(false)
            .build();

    public final static RenderPipeline ROUND_RECT_OUTLINE = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/round_rectangle_outline"))
            .withVertexBinding(0, LuminVertexFormats.ROUND_RECT_OUTLINE)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withVertexShader(ResourceLocationUtils.getIdentifier("round_rectangle_outline"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("round_rectangle_outline"))
            .withCull(false)
            .build();

    public final static RenderPipeline SHADOW = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/shadow"))
            .withVertexBinding(0, LuminVertexFormats.ROUND_RECT)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withVertexShader(ResourceLocationUtils.getIdentifier("shadow"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("shadow"))
            .withCull(false)
            .build();

    public final static RenderPipeline TEXTURE = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/texture"))
            .withVertexBinding(0, LuminVertexFormats.TEXTURE)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withVertexShader(ResourceLocationUtils.getIdentifier("texture"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("texture"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withCull(false)
            .build();

    public final static RenderPipeline TRIANGLE = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/triangle"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withVertexShader(ResourceLocationUtils.getIdentifier("triangle"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("triangle"))
            .withCull(false)
            .build();

}
