package com.github.epsilon.modules.impl.hud;

import com.github.epsilon.graphics.LuminTexture;
import com.github.epsilon.graphics.renderers.*;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.managers.HealthManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.HudModule;
import com.github.epsilon.modules.impl.combat.KillAura;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.awt.*;
import java.util.Locale;
import java.util.function.Supplier;

public class TargetHud extends HudModule {

    public static final TargetHud INSTANCE = new TargetHud();

    private TargetHud() {
        super("Target Hud", Category.HUD, 0f, 0f, 180f, 80f);
    }

    private final DoubleSetting scale = doubleSetting("Scale", 0.9, 0.5, 2.0, 0.1);
    private final DoubleSetting width = doubleSetting("Width", 160.0, 100.0, 300.0, 1.0);
    private final DoubleSetting height = doubleSetting("Height", 60.0, 30.0, 100.0, 1.0);
    private final DoubleSetting radius = doubleSetting("Radius", 10.0, 0.0, 20.0, 1.0);
    private final DoubleSetting blurStrength = doubleSetting("Blur Strength", 8.0, 1.0, 20.0, 1.0);
    private final DoubleSetting healthBarHeight = doubleSetting("Bar Height", 4.0, 2.0, 20.0, 1.0);
    private final DoubleSetting healthBarRadius = doubleSetting("Bar Radius", 1.8, 0.0, 15.0, 1.0);
    private final DoubleSetting nameSize = doubleSetting("Name Size", 11.5, 8.0, 18.0, 0.5);
    private final BoolSetting delayBar = boolSetting("Delay Bar", true);
    private final BoolSetting delayWait = boolSetting("Delay Wait", true, delayBar::getValue);
    private final DoubleSetting delayTime = doubleSetting("Delay Time", 600.0, 0.0, 2000.0, 50.0, () -> delayBar.getValue() && delayWait.getValue());
    private final DoubleSetting delaySpeed = doubleSetting("Delay Speed", 2.0, 0.1, 10.0, 0.1, delayBar::getValue);
    private final BoolSetting barOutline = boolSetting("Bar Outline", true);
    private final DoubleSetting barOutlineWidth = doubleSetting("Bar Outline Width", 1.0, 0.5, 5.0, 0.5, barOutline::getValue);
    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(15, 15, 15, 200));
    private final ColorSetting barBackgroundColor = colorSetting("Bar Background Color", new Color(255, 255, 255, 70));
    private final ColorSetting barFillColor = colorSetting("Bar Fill Color", new Color(255, 236, 248, 245));
    private final ColorSetting delayBarColor = colorSetting("Delay Bar Color", new Color(190, 190, 190, 130), delayBar::getValue);
    private final ColorSetting barOutlineColor = colorSetting("Bar Outline Color", new Color(255, 255, 255, 120), barOutline::getValue);
    private final ColorSetting textColor = colorSetting("Text Color", new Color(255, 255, 255, 235));
    private final BoolSetting drawShadow = boolSetting("Drop Shadow", true);
    private final DoubleSetting shadowBlur = doubleSetting("Shadow Blur", 4.5, 0.1, 32.0, 0.5, drawShadow::getValue);
    private final ColorSetting shadowColor = colorSetting("Shadow Color", new Color(0, 0, 0, 150), drawShadow::getValue);

    private final Supplier<RoundRectRenderer> roundRectRendererSupplier = Suppliers.memoize(RoundRectRenderer::create);
    private final Supplier<RoundRectOutlineRenderer> roundRectOutlineRendererSupplier = Suppliers.memoize(RoundRectOutlineRenderer::create);
    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);
    private final Supplier<TextureRenderer> textureRendererSupplier = Suppliers.memoize(TextureRenderer::create);
    private final Supplier<ShadowRenderer> shadowRendererSupplier = Suppliers.memoize(ShadowRenderer::create);

    private int lastTargetId = Integer.MIN_VALUE;
    private float displayedHealth = 0.0f;
    private float delayedHealth = 0.0f;
    private float lastKnownHealth = -1.0f;
    private long lastDamageTimeMs = 0L;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (nullCheck()) return;

        float panelScale = scale.getValue().floatValue();
        float panelWidth = width.getValue().floatValue() * panelScale;
        float panelHeight = height.getValue().floatValue() * panelScale;
        setBounds(panelWidth, panelHeight);

        LivingEntity target = resolveTarget();
        if (target == null) return;

        RoundRectRenderer roundRectRenderer = roundRectRendererSupplier.get();
        RoundRectOutlineRenderer roundRectOutlineRenderer = roundRectOutlineRendererSupplier.get();
        TextRenderer textRenderer = textRendererSupplier.get();
        TextureRenderer textureRenderer = textureRendererSupplier.get();
        ShadowRenderer shadowRenderer = shadowRendererSupplier.get();

        float frameTime = deltaTracker == null ? 0.05f : deltaTracker.getGameTimeDeltaTicks() / 20.0f;
        float health = HealthManager.INSTANCE.getHealth(target);
        float maxHealth = Math.max(1.0f, target.getMaxHealth() + Math.max(0.0f, target.getAbsorptionAmount()));
        float healthPercent = updateAnimatedHealth(target, health, maxHealth, frameTime);
        float delayHealthPercent = Mth.clamp(delayedHealth / maxHealth, 0.0f, 1.0f);

        float pad = 6.0f * panelScale;
        float cornerRadius = radius.getValue().floatValue() * panelScale;
        float barHeight = healthBarHeight.getValue().floatValue() * panelScale;
        float barRadius = healthBarRadius.getValue().floatValue() * panelScale;
        float barWidth = Math.max(1.0f, panelWidth - pad * 2.0f);
        float delayedBarWidth = Math.max(0.0f, Math.min(barWidth, barWidth * delayHealthPercent));
        float filledBarWidth = Math.max(0.0f, Math.min(barWidth, barWidth * healthPercent));

        float innerHeight = Math.max(1.0f, panelHeight - pad * 2.0f);
        float contentAreaHeight = Math.max(1.0f, innerHeight - pad - barHeight);
        float headSize = Math.min(contentAreaHeight, Math.max(30.0f * panelScale, panelHeight * 0.6f) * 1.1f);
        float textScale = Math.max(0.45f, nameSize.getValue().floatValue() / 14.0f) * panelScale;
        float textHeight = textRenderer.getHeight(textScale);
        float contentRowHeight = Math.max(headSize, textHeight);
        float contentBlockHeight = contentRowHeight + pad + barHeight;
        float contentStartY = this.y + pad + Math.max(0.0f, (innerHeight - contentBlockHeight) / 2.0f);
        float headY = contentStartY + (contentRowHeight - headSize) / 2.0f;
        float headX = this.x + pad;
        float barY = contentStartY + contentRowHeight + pad;

        float textStartX = headX + headSize + pad;

        String nameText = target.getName().getString();
        String healthText = String.format(Locale.ROOT, "%.1f", displayedHealth);

        float contentY = headY + 2.0f * panelScale;
        float healthTextWidth = textRenderer.getWidth(healthText, textScale);
        float healthTextX = this.x + panelWidth - pad - healthTextWidth;

        BlurShader.INSTANCE.render(this.x, this.y, panelWidth, panelHeight, cornerRadius, blurStrength.getValue().floatValue());

        if (drawShadow.getValue()) {
            shadowRenderer.addShadow(this.x, this.y, panelWidth, panelHeight, cornerRadius, shadowBlur.getValue().floatValue(), shadowColor.getValue());
            shadowRenderer.drawAndClear();
        }

        roundRectRenderer.addRoundRect(this.x, this.y, panelWidth, panelHeight, cornerRadius, backgroundColor.getValue());
        roundRectRenderer.addRoundRect(this.x + pad, barY, barWidth, barHeight, barRadius, barBackgroundColor.getValue());
        if (delayBar.getValue() && delayedHealth > displayedHealth) {
            roundRectRenderer.addRoundRect(this.x + pad, barY, delayedBarWidth, barHeight, barRadius, delayBarColor.getValue());
        }
        roundRectRenderer.addRoundRect(this.x + pad, barY, filledBarWidth, barHeight, barRadius, barFillColor.getValue());
        roundRectRenderer.drawAndClear();

        if (barOutline.getValue()) {
            roundRectOutlineRenderer.addOutline(
                    this.x + pad, barY, barWidth, barHeight, barRadius,
                    barOutlineWidth.getValue().floatValue() * panelScale, barOutlineColor.getValue()
            );
            roundRectOutlineRenderer.drawAndClear();
        }

        if (target instanceof AbstractClientPlayer player) {
            AbstractTexture abstractTexture = mc.getTextureManager().getTexture(player.getSkin().body().texturePath());
            textureRenderer.addPlayerHead(
                    new LuminTexture(abstractTexture.getTexture(), abstractTexture.getTextureView(), abstractTexture.getSampler()),
                    headX, headY, headSize, headSize * 0.23f, Color.WHITE
            );
            textureRenderer.drawAndClear();
        } else {
            roundRectRenderer.addRoundRect(headX, headY, headSize, headSize, headSize * 0.23f, new Color(80, 80, 80, 200));
        }

        textRenderer.addText(nameText, textStartX, contentY, textScale, textColor.getValue());
        textRenderer.addText(healthText, healthTextX, contentY, textScale, textColor.getValue());
        textRenderer.drawAndClear();
    }

    private LivingEntity resolveTarget() {
        LivingEntity target = KillAura.INSTANCE.target;
        if (isRenderableTarget(target)) {
            return target;
        }
        return mc.screen instanceof HudEditorScreen ? mc.player : null;
    }

    private boolean isRenderableTarget(LivingEntity target) {
        return target != null && target.isAlive() && !target.isDeadOrDying();
    }

    private float updateAnimatedHealth(LivingEntity target, float currentHealth, float maxHealth, float frameTime) {
        int targetId = target.getId();
        if (targetId != lastTargetId) {
            lastTargetId = targetId;
            displayedHealth = currentHealth;
            delayedHealth = currentHealth;
            lastKnownHealth = currentHealth;
            lastDamageTimeMs = 0L;
        } else {
            if (lastKnownHealth >= 0.0f && currentHealth < lastKnownHealth) {
                lastDamageTimeMs = System.currentTimeMillis();
            }
            float speed = Mth.clamp(frameTime * 10.0f, 0.0f, 1.0f);
            displayedHealth = Mth.lerp(speed, displayedHealth, currentHealth);
            delayedHealth = updateDelayedHealth(currentHealth, frameTime);
            lastKnownHealth = currentHealth;
        }
        displayedHealth = Mth.clamp(displayedHealth, 0.0f, maxHealth);
        delayedHealth = Mth.clamp(delayedHealth, 0.0f, maxHealth);
        return Mth.clamp(displayedHealth / maxHealth, 0.0f, 1.0f);
    }

    private float updateDelayedHealth(float currentHealth, float frameTime) {
        if (!delayBar.getValue()) {
            return currentHealth;
        }
        if (currentHealth >= delayedHealth) {
            return currentHealth;
        }
        if (delayWait.getValue() && System.currentTimeMillis() - lastDamageTimeMs < delayTime.getValue().longValue()) {
            return delayedHealth;
        }
        float speed = Mth.clamp(frameTime * delaySpeed.getValue().floatValue() * 2.0f, 0.0f, 1.0f);
        return Mth.lerp(speed, delayedHealth, currentHealth);
    }

}
