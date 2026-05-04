package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.renderers.RectRenderer;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.managers.FriendManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.utils.render.WorldToScreen;
import com.google.common.base.Suppliers;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector4d;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class NameTags extends Module {

    public static final NameTags INSTANCE = new NameTags();

    private final DoubleSetting range = doubleSetting("Range", 64.0, 4.0, 128.0, 1.0);
    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.1, 1.5, 0.1);
    private final BoolSetting showEquipment = boolSetting("Show Equipment", true);
    private final BoolSetting showHands = boolSetting("Show Hands", true, showEquipment::getValue);
    private final BoolSetting showSelf = boolSetting("Show Self", true);

    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::new);
    private final Supplier<RectRenderer> rectRendererSupplier = Suppliers.memoize(RectRenderer::new);

    private NameTags() {
        super("Name Tags", Category.RENDER);
    }

    private final List<TagDrawData> drawList = new ArrayList<>();

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;

        drawList.clear();

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double maxDistanceSq = range.getValue() * range.getValue();
        float textScale = scale.getValue().floatValue();

        TextRenderer textRenderer = textRendererSupplier.get();

        for (Player target : mc.level.players()) {
            if (!target.isAlive() || target.isSpectator()) continue;
            if (mc.options.getCameraType().isFirstPerson() && target == mc.player) continue;
            if (target == mc.player && !showSelf.getValue()) continue;
            double distanceSq = mc.player.distanceToSqr(target);
            if (distanceSq > maxDistanceSq) continue;

            Vector4d projected = WorldToScreen.getEntityPositionsOn2D(target, partialTick);
            if (projected == null) continue;

            float screenWidth = mc.getWindow().getGuiScaledWidth();
            float screenHeight = mc.getWindow().getGuiScaledHeight();
            if (projected.z < 0 || projected.w < 0 || projected.x > screenWidth || projected.y > screenHeight) continue;

            float projectedHeight = (float) Math.max(1.0, projected.w - projected.y);
            float renderScale = getPerspectiveScale(textScale, projectedHeight);

            List<String> equipmentLines = buildEquipmentLines(target);
            String nameText = target.getName().getString();
            float totalHealth = target.getHealth() + target.getAbsorptionAmount();
            String healthText = String.format("[%.1f HP]", totalHealth);

            float padding = 3.0f * renderScale;
            float lineGap = 1.5f * renderScale;
            float lineHeight = textRenderer.getHeight(renderScale);
            float equipmentWidth = 0.0f;
            for (String line : equipmentLines) {
                equipmentWidth = Math.max(equipmentWidth, textRenderer.getWidth(line, renderScale));
            }
            float headerWidth = textRenderer.getWidth(nameText, renderScale)
                    + textRenderer.getWidth(" ", renderScale)
                    + textRenderer.getWidth(healthText, renderScale);

            int lineCount = equipmentLines.size() + 1;
            float width = Math.max(equipmentWidth, headerWidth);
            float boxWidth = width + padding * 2.0f;
            float boxHeight = padding * 2.0f + lineHeight * lineCount + lineGap * Math.max(0, lineCount - 1);

            final var currentPosition = WorldToScreen.interpolate(target, partialTick);

            final var projectedPosition = WorldToScreen.getWorldPositionToScreen(currentPosition.add(0.0f, 0.5f + target.getEyeHeight(), 0.0f));
            if (projectedPosition.z > 1.0f || projectedPosition.z < 0.0f) continue;

            float guiScale = mc.getWindow().getGuiScale();

            float x = (projectedPosition.x / guiScale) - boxWidth / 2.0f;
            float y = projectedPosition.y / guiScale - boxHeight - 4.0f * renderScale;

            if (x + boxWidth < 0.0f || y + boxHeight < 0.0f || x > screenWidth || y > screenHeight) continue;

            Color healthColor = totalHealth < 10.0f ? new Color(255, 214, 64, 240) : new Color(120, 255, 120, 240);
            final var isFriend = FriendManager.INSTANCE.isFriend(nameText);

            drawList.add(new TagDrawData(equipmentLines, nameText, isFriend, healthText, healthColor, x, y, boxWidth, boxHeight, renderScale, padding, lineGap));
        }

    }

    @EventHandler
    private void renderTagList(Render2DEvent event) {
        RectRenderer rectRenderer = rectRendererSupplier.get();
        TextRenderer textRenderer = textRendererSupplier.get();

        for (TagDrawData data : drawList) {
            rectRenderer.addRect(data.x, data.y, data.width, data.height, new Color(0, 0, 0, 130));

            float lineY = data.y + data.padding;
            float lineHeight = textRenderer.getHeight(data.scale);

            // Draw equipment lines first, then keep name+health as the bottom row.
            for (String equipmentLine : data.equipmentLines) {
                textRenderer.addText(equipmentLine, data.x + data.padding, lineY, data.scale, new Color(210, 210, 210, 220));
                lineY += lineHeight + data.lineGap;
            }

            float nameWidth = textRenderer.getWidth(data.nameText, data.scale);
            float spaceWidth = textRenderer.getWidth(" ", data.scale);
            float healthWidth = textRenderer.getWidth(data.healthText, data.scale);
            float headerWidth = nameWidth + spaceWidth + healthWidth;
            float headerX = data.x + (data.width - headerWidth) * 0.5f;

            textRenderer.addText(data.nameText, headerX, lineY, data.scale, data.isFriend ? new Color(20, 255, 20, 235) : new Color(255, 255, 255, 235));
            textRenderer.addText(data.healthText, headerX + nameWidth + spaceWidth, lineY, data.scale, data.healthColor);
        }

        rectRenderer.drawAndClear();
        textRenderer.drawAndClear();

        drawList.clear();
    }

    private List<String> buildEquipmentLines(Player player) {
        List<String> lines = new ArrayList<>();
        if (!showEquipment.getValue()) {
            return lines;
        }

        if (showHands.getValue()) {
            lines.add("Main: " + itemName(player.getMainHandItem()));
            lines.add("Off: " + itemName(player.getOffhandItem()));
        }

        lines.add("Head: " + itemName(player.getItemBySlot(EquipmentSlot.HEAD)));
        lines.add("Chest: " + itemName(player.getItemBySlot(EquipmentSlot.CHEST)));
        lines.add("Legs: " + itemName(player.getItemBySlot(EquipmentSlot.LEGS)));
        lines.add("Boots: " + itemName(player.getItemBySlot(EquipmentSlot.FEET)));
        return lines;
    }

    private float getPerspectiveScale(float baseScale, float projectedHeight) {
        float perspectiveFactor = Mth.clamp(projectedHeight / 36.0f, 0.55f, 2.2f);
        return baseScale * perspectiveFactor;
    }

    private String itemName(ItemStack stack) {
        if (stack.isEmpty()) return "-";
        return stack.getHoverName().getString();
    }

    private record TagDrawData(
            List<String> equipmentLines,
            String nameText,
            boolean isFriend,
            String healthText,
            Color healthColor,
            float x,
            float y,
            float width,
            float height,
            float scale,
            float padding,
            float lineGap
    ) {
    }

}
