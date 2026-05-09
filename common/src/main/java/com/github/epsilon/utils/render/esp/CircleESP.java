package com.github.epsilon.utils.render.esp;

import com.github.epsilon.graphics.LuminRenderPipelines;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

import java.awt.*;

public class CircleESP {

    private static final Minecraft mc = Minecraft.getInstance();

    public static void render(PoseStack poseStack, LivingEntity target, float radius, Color sideColor, Color lineColor) {
        float ticks = (float) (System.currentTimeMillis() % 1000000) * 0.004f;
        float alpha = 0.35f + 0.65f * ((Mth.sin(ticks * 1.8f) + 1.0f) * 0.5f);
        if (alpha <= 0.01f) return;

        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        double x = Mth.lerp(tickDelta, target.xo, target.getX()) - mc.getEntityRenderDispatcher().camera.position().x;
        double y = Mth.lerp(tickDelta, target.yo, target.getY()) - mc.getEntityRenderDispatcher().camera.position().y + Math.sin(ticks) + 1;
        double z = Mth.lerp(tickDelta, target.zo, target.getZ()) - mc.getEntityRenderDispatcher().camera.position().z;

        poseStack.pushPose();
        poseStack.translate(x, y, z);

        BufferBuilder triBuffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();

        for (float i = 0; i <= (Math.PI * 2); i += ((float) Math.PI * 2) / 64.F) {
            float vecX = (float) (radius * Math.cos(i));
            float vecZ = (float) (radius * Math.sin(i));

            triBuffer.addVertex(matrix, vecX, (float) (-Math.sin(ticks + 1) / 2.7f), vecZ).setColor(sideColor.getAlpha() / 255.0f, sideColor.getGreen() / 255.0f, sideColor.getBlue() / 255.0f, 0.0f);
            triBuffer.addVertex(matrix, vecX, 0, vecZ).setColor(sideColor.getAlpha() / 255.0f, sideColor.getGreen() / 255.0f, sideColor.getBlue() / 255.0f, 0.52f * alpha);
        }

        LuminRenderPipelines.TRIANGLE_STRIP.draw(triBuffer.buildOrThrow());

        BufferBuilder lineBuffer = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);
        PoseStack.Pose entry = poseStack.last();

        for (int i = 0; i <= 180; i++) {
            float radAngle = (float) (i * Math.PI * 2 / 90);

            float lineX = (float) (-Math.sin(radAngle) * radius);
            float lineZ = (float) (Math.cos(radAngle) * radius);
            float nextAngle = (float) ((i + 1) * Math.PI * 2 / 90);
            float nextX = (float) (-Math.sin(nextAngle) * radius);
            float nextZ = (float) (Math.cos(nextAngle) * radius);

            float dx = nextX - lineX;
            float dz = nextZ - lineZ;
            float len = Mth.sqrt(dx * dx + dz * dz);
            if (len < 1.0E-6f) continue;
            float nx = dx / len;
            float nz = dz / len;

            lineBuffer.addVertex(entry, lineX, 0, lineZ).setColor(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), Math.round(lineColor.getAlpha() * alpha)).setNormal(entry, nx, 0.0f, nz).setLineWidth(2f);
            lineBuffer.addVertex(entry, nextX, 0, nextZ).setColor(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), Math.round(lineColor.getAlpha() * alpha)).setNormal(entry, nx, 0.0f, nz).setLineWidth(2f);
        }

        LuminRenderPipelines.LINES.draw(lineBuffer.buildOrThrow());

        poseStack.popPose();
    }

}
