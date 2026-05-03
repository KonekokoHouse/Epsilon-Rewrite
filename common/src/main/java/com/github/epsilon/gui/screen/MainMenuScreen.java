package com.github.epsilon.gui.screen;

import com.github.epsilon.Epsilon;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.RoundRectRenderer;
import com.github.epsilon.graphics.renderers.ShadowRenderer;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.graphics.video.VideoManager;
import com.github.epsilon.graphics.video.VideoUtil;
import com.github.epsilon.gui.panel.MD3Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainMenuScreen extends Screen {

    private static final float VIDEO_ZOOM = 1.08f;
    private static final float VIDEO_PARALLAX_STRENGTH = 0.35f;

    public static final MainMenuScreen INSTANCE = new MainMenuScreen();

    private final ShadowRenderer shadowRenderer = new ShadowRenderer();
    private final RoundRectRenderer roundRectRenderer = new RoundRectRenderer();
    private final TextRenderer textRenderer = new TextRenderer();

    private final List<MenuEntry> entries = new ArrayList<>();

    private LuminRenderSystem.LuminRenderTarget renderTarget;

    private boolean videoStarted;
    private long introStartMs;

    private MainMenuScreen() {
        super(Component.literal("MainMenuScreen"));
        entries.add(new MenuEntry("S", "Singleplayer", "Create and manage your worlds", () -> minecraft.setScreen(new SelectWorldScreen(this))));
        entries.add(new MenuEntry("M", "Multiplayer", "Servers, friends and online play", () -> {
            Screen screen = this.minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this);
            this.minecraft.setScreen(screen);
        }));
        entries.add(new MenuEntry("O", "Options", "Video, controls and client settings", () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options, false))));
        entries.add(new MenuEntry("Q", "Quit", "Leave Epsilon and close the game", minecraft::stop));
    }

    @Override
    protected void init() {
        super.init();
        introStartMs = Util.getMillis();
        for (MenuEntry entry : entries) {
            entry.hoverProgress = 0.0f;
            entry.setBounds(0.0f, 0.0f, 0.0f, 0.0f);
        }
    }

    @Override
    public void added() {
        super.added();

        if (videoStarted) {
            return;
        }

        try {
            VideoManager.loadBackground(60);
            videoStarted = true;
        } catch (IOException e) {
            Epsilon.LOGGER.error("MainMenu视频加载失败!", e);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Identifier videoTexture = videoStarted ? VideoUtil.getGuiTexture() : null;
        if (videoTexture != null) {
            int videoWidth = VideoUtil.getGuiTextureWidth();
            int videoHeight = VideoUtil.getGuiTextureHeight();
            if (videoWidth > 0 && videoHeight > 0) {
                float screenAspect = (float) this.width / (float) this.height;
                float videoAspect = (float) videoWidth / (float) videoHeight;

                float visibleWidth = 1.0f;
                float visibleHeight = 1.0f;
                if (videoAspect > screenAspect) {
                    visibleWidth = screenAspect / videoAspect;
                } else {
                    visibleHeight = videoAspect / screenAspect;
                }

                visibleWidth /= VIDEO_ZOOM;
                visibleHeight /= VIDEO_ZOOM;

                float mouseNormX = this.width <= 0 ? 0.0f : Mth.clamp((mouseX / (float) this.width) * 2.0f - 1.0f, -1.0f, 1.0f);
                float mouseNormY = this.height <= 0 ? 0.0f : Mth.clamp((mouseY / (float) this.height) * 2.0f - 1.0f, -1.0f, 1.0f);

                float uRange = 1.0f - visibleWidth;
                float vRange = 1.0f - visibleHeight;
                float u0 = Mth.clamp((uRange * 0.5f) - mouseNormX * uRange * VIDEO_PARALLAX_STRENGTH, 0.0f, uRange);
                float v0 = Mth.clamp((vRange * 0.5f) - mouseNormY * vRange * VIDEO_PARALLAX_STRENGTH, 0.0f, vRange);
                float u1 = u0 + visibleWidth;
                float v1 = v0 + visibleHeight;

                graphics.blit(videoTexture, 0, 0, this.width, this.height, u0, u1, v0, v1);
                return;
            }
            return;
        }

        // 视频已经启动但当前帧尚未准备好时，回退到全景背景，避免黑屏。
        this.minecraft.gameRenderer.getPanorama().extractRenderState(graphics, this.width, this.height, this.panoramaShouldSpin());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        final var window = minecraft.getWindow();
        if (renderTarget == null) {
            renderTarget = LuminRenderSystem.LuminRenderTarget.create("main-menu", window.getWidth(), window.getHeight());
        }

        renderTarget.clear();
        renderTarget.resize(window.getWidth(), window.getHeight());
        LuminRenderSystem.setActiveTarget(renderTarget);

        MD3Theme.syncFromSettings();
        drawMenu(mouseX, mouseY);

        LuminRenderSystem.setActiveTarget(null);
        graphics.blit(renderTarget.getIdentifier(), 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 0, 1, 1, 0);
    }

    private void drawMenu(int mouseX, int mouseY) {
        long now = Util.getMillis();
        float introProgress = easeOutCubic(Mth.clamp((now - introStartMs) / 650.0f, 0.0f, 1.0f));
        Layout layout = Layout.resolve(width, height, entries.size());

        Color panelShadow = applyAlpha(MD3Theme.SHADOW, 0.90f);
        Color panelColor = applyAlpha(MD3Theme.SURFACE, 0.74f);
        Color titleColor = applyAlpha(MD3Theme.TEXT_PRIMARY, 0.96f);
        Color subtitleColor = applyAlpha(MD3Theme.TEXT_SECONDARY, 0.90f);

        shadowRenderer.addShadow(layout.panelX, layout.panelY, layout.panelWidth, layout.panelHeight, layout.panelRadius, 22.0f * layout.scale, panelShadow);
        roundRectRenderer.addRoundRect(layout.panelX, layout.panelY, layout.panelWidth, layout.panelHeight, layout.panelRadius, panelColor);

        float titleScale = 1.04f * layout.scale;
        float versionScale = 0.56f * layout.scale;
        float titleY = layout.panelY + layout.padding + 2.0f * layout.scale;
        float subtitleY = titleY + textRenderer.getLineHeight(titleScale, StaticFontLoader.DUCKSANS) + 2.0f * layout.scale;

        textRenderer.addText("Epsilon", layout.panelX + layout.padding, titleY, titleScale, titleColor, StaticFontLoader.DUCKSANS);
        textRenderer.addText("Minecraft 26.1.2  |  " + Epsilon.VERSION, layout.panelX + layout.padding, subtitleY, versionScale, subtitleColor);

        for (int i = 0; i < entries.size(); i++) {
            renderEntry(entries.get(i), i, mouseX, mouseY, introProgress, layout);
        }

        shadowRenderer.drawAndClear();
        roundRectRenderer.drawAndClear();
        textRenderer.drawAndClear();
    }

    private void renderEntry(MenuEntry entry, int index, int mouseX, int mouseY, float introProgress, Layout layout) {
        float staged = Mth.clamp((introProgress - index * 0.09f) / 0.60f, 0.0f, 1.0f);
        float appear = easeOutCubic(staged);
        if (appear <= 0.001f) {
            entry.setBounds(0.0f, 0.0f, 0.0f, 0.0f);
            return;
        }

        float y = layout.buttonsY + index * (layout.buttonHeight + layout.buttonGap);
        float slideX = (1.0f - appear) * (-18.0f * layout.scale);
        float drawX = layout.buttonsX + slideX;

        entry.setBounds(drawX, y, layout.buttonWidth, layout.buttonHeight);

        boolean hovered = entry.isHovered(mouseX, mouseY);
        entry.hoverProgress = Mth.lerp(hovered ? 0.24f : 0.16f, entry.hoverProgress, hovered ? 1.0f : 0.0f);

        float hover = entry.hoverProgress;
        float cardLift = hover * 2.0f * layout.scale;
        float cardY = y - cardLift;

        Color baseColor = applyAlpha(MD3Theme.SURFACE_CONTAINER_HIGH, 0.80f * appear);
        Color hoverColor = applyAlpha(MD3Theme.PRIMARY_CONTAINER, 0.92f * appear);
        Color cardColor = blend(baseColor, hoverColor, hover);
        Color titleColor = blend(
                applyAlpha(MD3Theme.TEXT_PRIMARY, 0.95f * appear),
                applyAlpha(MD3Theme.ON_PRIMARY_CONTAINER, 0.98f * appear),
                hover
        );
        Color subColor = blend(
                applyAlpha(MD3Theme.TEXT_SECONDARY, 0.88f * appear),
                applyAlpha(MD3Theme.ON_PRIMARY_CONTAINER, 0.78f * appear),
                hover * 0.8f
        );

        Color badgeBase = entry.title.equals("Quit")
                ? applyAlpha(MD3Theme.TERTIARY_CONTAINER, 0.90f * appear)
                : applyAlpha(MD3Theme.SECONDARY_CONTAINER, 0.92f * appear);
        Color badgeHover = entry.title.equals("Quit")
                ? applyAlpha(MD3Theme.ERROR, 0.92f * appear)
                : applyAlpha(MD3Theme.PRIMARY, 0.95f * appear);
        Color badgeColor = blend(badgeBase, badgeHover, hover);
        Color badgeTextColor = entry.title.equals("Quit")
                ? applyAlpha(MD3Theme.ON_TERTIARY, 0.98f * appear)
                : applyAlpha(MD3Theme.ON_SECONDARY_CONTAINER, 0.98f * appear);

        shadowRenderer.addShadow(drawX, cardY, layout.buttonWidth, layout.buttonHeight, layout.buttonRadius, (10.0f + hover * 6.0f) * layout.scale, applyAlpha(MD3Theme.SHADOW, (0.52f + hover * 0.12f) * appear));

        roundRectRenderer.addRoundRect(drawX, cardY, layout.buttonWidth, layout.buttonHeight, layout.buttonRadius, cardColor);

        float badgeX = drawX + 10.0f * layout.scale;
        float badgeY = cardY + (layout.buttonHeight - layout.badgeSize) / 2.0f;
        roundRectRenderer.addRoundRect(badgeX, badgeY, layout.badgeSize, layout.badgeSize, layout.badgeRadius, badgeColor);

        float badgeScale = 0.62f * layout.scale;
        float badgeTextX = badgeX + (layout.badgeSize - textRenderer.getWidth(entry.badge, badgeScale, StaticFontLoader.DUCKSANS)) / 2.0f;
        float badgeTextY = badgeY + (layout.badgeSize - textRenderer.getHeight(badgeScale, StaticFontLoader.DUCKSANS)) / 2.0f - 1.0f * layout.scale;
        textRenderer.addText(entry.badge, badgeTextX, badgeTextY, badgeScale, badgeTextColor, StaticFontLoader.DUCKSANS);

        float textX = badgeX + layout.badgeSize + 11.0f * layout.scale;
        float titleScale = 0.74f * layout.scale;
        float subtitleScale = 0.54f * layout.scale;
        float titleY = cardY + 8.0f * layout.scale;
        float subtitleY = titleY + textRenderer.getLineHeight(titleScale, StaticFontLoader.DUCKSANS) + 3.5f * layout.scale;

        String title = switch (entry.title) {
            case "Singleplayer" -> I18n.get("menu.singleplayer");
            case "Multiplayer" -> I18n.get("menu.multiplayer");
            case "Options" -> I18n.get("menu.options");
            case "Quit" -> I18n.get("menu.quit");
            default -> entry.title;
        };

        textRenderer.addText(title, textX, titleY, titleScale, titleColor, StaticFontLoader.DUCKSANS);
        textRenderer.addText(entry.subtitle, textX, subtitleY, subtitleScale, subColor);

        String tip = entry.title.equals("Quit") ? "Close" : "Open";
        float tipScale = 0.50f * layout.scale;
        float tipWidth = textRenderer.getWidth(tip, tipScale);
        float tipX = drawX + layout.buttonWidth - tipWidth - 14.0f * layout.scale;
        float tipY = cardY + (layout.buttonHeight - textRenderer.getHeight(tipScale)) / 2.0f - 1.0f * layout.scale;
        textRenderer.addText(tip, tipX, tipY, tipScale, applyAlpha(MD3Theme.TEXT_MUTED, (0.86f + hover * 0.10f) * appear));
    }

    private static float easeOutCubic(float value) {
        float t = Mth.clamp(value, 0.0f, 1.0f);
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    private static Color applyAlpha(Color color, float alphaFactor) {
        float factor = Mth.clamp(alphaFactor, 0.0f, 1.0f);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * factor));
    }

    private static Color blend(Color start, Color end, float delta) {
        float t = Mth.clamp(delta, 0.0f, 1.0f);
        int r = (int) (start.getRed() + (end.getRed() - start.getRed()) * t);
        int g = (int) (start.getGreen() + (end.getGreen() - start.getGreen()) * t);
        int b = (int) (start.getBlue() + (end.getBlue() - start.getBlue()) * t);
        int a = (int) (start.getAlpha() + (end.getAlpha() - start.getAlpha()) * t);
        return new Color(r, g, b, a);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            for (MenuEntry entry : entries) {
                if (entry.isHovered(event.x(), event.y())) {
                    entry.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void removed() {
        super.removed();
        if (renderTarget != null) {
            renderTarget.close();
            renderTarget = null;
        }
        if (videoStarted) {
            videoStarted = false;
            VideoUtil.stop();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class MenuEntry {
        private final String badge;
        private final String title;
        private final String subtitle;
        private final Runnable action;

        private float x;
        private float y;
        private float width;
        private float height;
        private float hoverProgress;

        private MenuEntry(String badge, String title, String subtitle, Runnable action) {
            this.badge = badge;
            this.title = title;
            this.subtitle = subtitle;
            this.action = action;
        }

        private void setBounds(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private boolean isHovered(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private record Layout(float scale, float panelX, float panelY, float panelWidth, float panelHeight,
                          float panelRadius, float padding, float buttonsX, float buttonsY, float buttonWidth,
                          float buttonHeight, float buttonGap, float buttonRadius, float badgeSize, float badgeRadius) {
        private static Layout resolve(int width, int height, int entryCount) {
            float minEdgePadding = 10.0f;
            float baseScale = Mth.clamp(Math.min(width / 480.0f, height / 320.0f), 0.92f, 1.18f);

            float panelWidthAtScaleOne = 252.0f;
            float panelHeightAtScaleOne = 45.0f + entryCount * 40.0f + Math.max(0, entryCount - 1) * 8.0f + 24.0f;
            float availableWidth = Math.max(1.0f, width - minEdgePadding * 2.0f);
            float availableHeight = Math.max(1.0f, height - minEdgePadding * 2.0f);

            float fitScale = Math.min(1.0f, Math.min(availableWidth / panelWidthAtScaleOne, availableHeight / panelHeightAtScaleOne));
            float scale = Mth.clamp(baseScale * fitScale, 0.58f, 1.18f);

            float buttonWidth = 228.0f * scale;
            float buttonHeight = 40.0f * scale;
            float buttonGap = 8.0f * scale;
            float padding = 12.0f * scale;
            float headerHeight = 45.0f * scale;
            float panelWidth = buttonWidth + padding * 2.0f;
            float panelHeight = headerHeight + entryCount * buttonHeight + (entryCount - 1) * buttonGap + padding * 2.0f;

            float panelX = Mth.clamp(width * 0.115f, minEdgePadding, Math.max(minEdgePadding, width - panelWidth - minEdgePadding));
            float targetY = height * 0.62f - panelHeight / 2.0f;
            float panelY = Mth.clamp(targetY, minEdgePadding, Math.max(minEdgePadding, height - panelHeight - minEdgePadding));
            return new Layout(
                    scale,
                    panelX,
                    panelY,
                    panelWidth,
                    panelHeight,
                    16.0f * scale,
                    padding,
                    panelX + padding,
                    panelY + headerHeight,
                    buttonWidth,
                    buttonHeight,
                    buttonGap,
                    10.0f * scale,
                    24.0f * scale,
                    7.0f * scale
            );
        }
    }

}
