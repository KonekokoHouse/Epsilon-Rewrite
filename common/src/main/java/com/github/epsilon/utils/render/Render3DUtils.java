package com.github.epsilon.utils.render;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.*;

public class Render3DUtils {

    static Minecraft mc = Minecraft.getInstance();

    private static final RenderPipeline FILLED_BOX_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipeline/filled_box"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    private static final RenderPipeline LINES_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipeline/lines"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    public static void drawFilledBox(BlockPos blockPos, Color color) {
        drawFilledBox(new AABB(blockPos), color.getRGB());
    }

    public static void drawFilledBox(AABB box, Color color) {
        int c = color.getRGB();
        drawFilledFadeBox(box, c, c);
    }

    public static void drawFilledBox(AABB box, int c) {
        drawFilledFadeBox(box, c, c);
    }

    public static void drawFilledFadeBox(AABB box, int c, int c1) {
        LuminImmediateRenderer.PosColorQuads builder = LuminImmediateRenderer.beginPosColorQuads(FILLED_BOX_PIPELINE);

        Vec3 camPos = mc.getEntityRenderDispatcher().camera.position();
        float minX = (float) (box.minX - camPos.x);
        float minY = (float) (box.minY - camPos.y);
        float minZ = (float) (box.minZ - camPos.z);
        float maxX = (float) (box.maxX - camPos.x);
        float maxY = (float) (box.maxY - camPos.y);
        float maxZ = (float) (box.maxZ - camPos.z);

        Matrix4f matrix = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.viewRotationMatrix;

        vertex(builder, matrix, minX, minY, minZ, c);
        vertex(builder, matrix, minX, minY, maxZ, c);
        vertex(builder, matrix, maxX, minY, maxZ, c);
        vertex(builder, matrix, maxX, minY, minZ, c);

        vertex(builder, matrix, minX, maxY, minZ, c1);
        vertex(builder, matrix, maxX, maxY, minZ, c1);
        vertex(builder, matrix, maxX, maxY, maxZ, c1);
        vertex(builder, matrix, minX, maxY, maxZ, c);

        vertex(builder, matrix, minX, minY, minZ, c);
        vertex(builder, matrix, minX, maxY, minZ, c1);
        vertex(builder, matrix, maxX, maxY, minZ, c1);
        vertex(builder, matrix, maxX, minY, minZ, c);

        vertex(builder, matrix, maxX, minY, minZ, c);
        vertex(builder, matrix, maxX, maxY, minZ, c1);
        vertex(builder, matrix, maxX, maxY, maxZ, c1);
        vertex(builder, matrix, maxX, minY, maxZ, c);

        vertex(builder, matrix, minX, minY, maxZ, c);
        vertex(builder, matrix, maxX, minY, maxZ, c);
        vertex(builder, matrix, maxX, maxY, maxZ, c1);
        vertex(builder, matrix, minX, maxY, maxZ, c1);

        vertex(builder, matrix, minX, minY, minZ, c);
        vertex(builder, matrix, minX, minY, maxZ, c);
        vertex(builder, matrix, minX, maxY, maxZ, c1);
        vertex(builder, matrix, minX, maxY, minZ, c1);

        builder.end();
    }

    public static void drawOutlineBox(PoseStack stack, AABB box, int color, float thickness) {
        LuminImmediateRenderer.Lines builder = LuminImmediateRenderer.beginLines(LINES_PIPELINE);

        Vec3 camPos = mc.getEntityRenderDispatcher().camera.position();
        float minX = (float) (box.minX - camPos.x);
        float minY = (float) (box.minY - camPos.y);
        float minZ = (float) (box.minZ - camPos.z);
        float maxX = (float) (box.maxX - camPos.x);
        float maxY = (float) (box.maxY - camPos.y);
        float maxZ = (float) (box.maxZ - camPos.z);

        Matrix4f matrix = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.viewRotationMatrix;
        PoseStack.Pose entry = stack.last();

        vertexLine(builder, matrix, entry, minX, minY, minZ, maxX, minY, minZ, color, thickness);
        vertexLine(builder, matrix, entry, maxX, minY, minZ, maxX, minY, maxZ, color, thickness);
        vertexLine(builder, matrix, entry, maxX, minY, maxZ, minX, minY, maxZ, color, thickness);
        vertexLine(builder, matrix, entry, minX, minY, maxZ, minX, minY, minZ, color, thickness);

        vertexLine(builder, matrix, entry, minX, maxY, minZ, maxX, maxY, minZ, color, thickness);
        vertexLine(builder, matrix, entry, maxX, maxY, minZ, maxX, maxY, maxZ, color, thickness);
        vertexLine(builder, matrix, entry, maxX, maxY, maxZ, minX, maxY, maxZ, color, thickness);
        vertexLine(builder, matrix, entry, minX, maxY, maxZ, minX, maxY, minZ, color, thickness);

        vertexLine(builder, matrix, entry, minX, minY, minZ, minX, maxY, minZ, color, thickness);
        vertexLine(builder, matrix, entry, maxX, minY, minZ, maxX, maxY, minZ, color, thickness);
        vertexLine(builder, matrix, entry, maxX, minY, maxZ, maxX, maxY, maxZ, color, thickness);
        vertexLine(builder, matrix, entry, minX, minY, maxZ, minX, maxY, maxZ, color, thickness);

        builder.end();
    }

    private static void vertex(LuminImmediateRenderer.PosColorQuads builder, Matrix4f matrix, float x, float y, float z, int color) {
        builder.vertex(matrix, x, y, z, color);
    }

    private static void vertexLine(LuminImmediateRenderer.Lines builder, Matrix4f matrix, PoseStack.Pose entry, float x1, float y1, float z1, float x2, float y2, float z2, int color, float thickness) {
        Vector3f normal = getNormal(x1, y1, z1, x2, y2, z2);
        builder.vertex(matrix, entry, x1, y1, z1, color, normal.x, normal.y, normal.z, thickness);
        builder.vertex(matrix, entry, x2, y2, z2, color, normal.x, normal.y, normal.z, thickness);
    }

    private static Vector3f getNormal(float x1, float y1, float z1, float x2, float y2, float z2) {
        float xNormal = x2 - x1;
        float yNormal = y2 - y1;
        float zNormal = z2 - z1;
        float normalSqrt = Mth.sqrt(xNormal * xNormal + yNormal * yNormal + zNormal * zNormal);
        return new Vector3f(xNormal / normalSqrt, yNormal / normalSqrt, zNormal / normalSqrt);
    }

}
