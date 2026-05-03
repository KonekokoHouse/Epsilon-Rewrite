package com.github.epsilon.modules.impl.hud;

import com.github.epsilon.Epsilon;
import com.github.epsilon.graphics.renderers.RoundRectRenderer;
import com.github.epsilon.graphics.renderers.ShadowRenderer;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.HudModule;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.*;
import java.util.function.Supplier;

public class WatermarkHud extends HudModule {

    public static final WatermarkHud INSTANCE = new WatermarkHud();

    private WatermarkHud() {
        super("Watermark Hud", Category.HUD, 0f, 0f, 200f, 28f);
    }

    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.5, 2.0, 0.1);
    private final DoubleSetting animSpeed = doubleSetting("Animation Speed", 8.0, 1.0, 20.0, 0.5);
    private final DoubleSetting cornerRadius = doubleSetting("Corner Radius", 7.0, 0.0, 14.0, 0.5);

    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(15, 15, 15, 200));
    private final ColorSetting brandColor = colorSetting("Brand Color", new Color(255, 105, 180, 255));
    private final ColorSetting accentColor = colorSetting("Accent Color", new Color(255, 105, 180, 255));
    private final ColorSetting separatorColor = colorSetting("Separator Color", new Color(255, 255, 255, 100));
    private final ColorSetting textColor = colorSetting("Text Color", new Color(255, 255, 255, 235));

    private final BoolSetting showAccentLine = boolSetting("Show Accent Line", false);

    private final BoolSetting drawShadow = boolSetting("Drop Shadow", true);
    private final DoubleSetting shadowBlur = doubleSetting("Shadow Blur", 4.5, 0.1, 32.0, 0.5, drawShadow::getValue);
    private final ColorSetting shadowColor = colorSetting("Shadow Color", new Color(0, 0, 0, 150), drawShadow::getValue);

    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::new);
    private final Supplier<RoundRectRenderer> roundRectRendererSupplier = Suppliers.memoize(RoundRectRenderer::new);
    private final Supplier<ShadowRenderer> shadowRendererSupplier = Suppliers.memoize(ShadowRenderer::new);

    private static final float INNER_PADDING_X = 8.0f;
    private static final float INNER_PADDING_Y = 5.0f;
    private static final float SEPARATOR_GAP = 4.0f;
    private static final float ACCENT_LINE_HEIGHT = 1.5f;

    private float animTimer = 0f;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (nullCheck()) return;

        TextRenderer textRenderer = textRendererSupplier.get();
        RoundRectRenderer roundRectRenderer = roundRectRendererSupplier.get();
        ShadowRenderer shadowRenderer = shadowRendererSupplier.get();

        float s = scale.getValue().floatValue();
        float padX = INNER_PADDING_X * s;
        float padY = INNER_PADDING_Y * s;
        float sepGap = SEPARATOR_GAP * s;
        float accentLineHeight = ACCENT_LINE_HEIGHT * s;
        float radius = cornerRadius.getValue().floatValue() * s;

        float frameTime = deltaTracker == null ? 0.05f : deltaTracker.getGameTimeDeltaTicks() / 20.0f;
        animTimer += frameTime;

        String fullBrand = "Epsilon";
        String separator = "|";

        String brandText = computeBrand(fullBrand, animSpeed.getValue().floatValue());

        String fpsText = "FPS:" + Minecraft.getInstance().getFps();
        String versionText = Epsilon.VERSION;

        float brandW = textRenderer.getWidth(fullBrand, s);
        float fpsW = textRenderer.getWidth(fpsText, s);
        float verW = textRenderer.getWidth(versionText, s);
        float sepW = textRenderer.getWidth(separator, s);

        float contentWidth = brandW + fpsW + verW + sepW * 3f + sepGap * 6f;
        float totalWidth = padX * 2f + contentWidth;
        float textH = textRenderer.getHeight(s);
        float totalHeight = padY * 2f + textH;

        if (drawShadow.getValue()) {
            shadowRenderer.addShadow(this.x, this.y, totalWidth, totalHeight, radius, shadowBlur.getValue().floatValue(), shadowColor.getValue());
            shadowRenderer.drawAndClear();
        }

        roundRectRenderer.addRoundRect(this.x, this.y, totalWidth, totalHeight, radius, backgroundColor.getValue());

        if (showAccentLine.getValue()) {
            roundRectRenderer.addRoundRect(this.x + radius, this.y, totalWidth - radius * 2f, accentLineHeight, 0f, accentColor.getValue());
        }

        roundRectRenderer.drawAndClear();

        float textY = this.y + padY;
        float cursX = this.x + padX;
        Color txtColor = textColor.getValue();
        Color sepColor = separatorColor.getValue();

        textRenderer.addText(brandText, cursX, textY, s, brandColor.getValue());
        cursX += brandW + sepGap;
        textRenderer.addText(separator, cursX, textY, s, sepColor);
        cursX += sepW + sepGap;

        textRenderer.addText(fpsText, cursX, textY, s, txtColor);
        cursX += fpsW + sepGap;
        textRenderer.addText(separator, cursX, textY, s, sepColor);
        cursX += sepW + sepGap;

        textRenderer.addText(versionText, cursX, textY, s, txtColor);

        textRenderer.drawAndClear();

        setBounds(totalWidth, totalHeight);
    }

    private String computeBrand(String full, float speed) {
        int maxLen = full.length();

        float stepDuration = 1.0f / speed;
        int totalSteps = maxLen * 2 - 2;
        int step = (int) (animTimer / stepDuration) % totalSteps;

        int visibleChars = step < maxLen ? step + 1 : totalSteps - step + 1;
        return full.substring(0, Math.min(visibleChars, maxLen));
    }

}
