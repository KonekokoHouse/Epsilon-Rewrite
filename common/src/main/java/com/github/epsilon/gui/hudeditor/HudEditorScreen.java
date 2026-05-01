package com.github.epsilon.gui.hudeditor;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.RectRenderer;
import com.github.epsilon.gui.compose.ComposePanelScreen;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.util.IMEFocusHelper;
import com.github.epsilon.managers.ConfigManager;
import com.github.epsilon.managers.RenderManager;
import com.github.epsilon.modules.HudModule;
import com.github.epsilon.modules.impl.render.notification.NotificationManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.List;

public class HudEditorScreen extends Screen {

    private static final Color BOX_COLOR = new Color(0, 0, 0, 100);
    private static final Color SELECTED_COLOR = new Color(188, 224, 255, 56);
    private static final Color HOVER_COLOR = new Color(255, 255, 255, 70);
    private static final Color DRAGGING_COLOR = new Color(120, 190, 255, 80);

    public static final HudEditorScreen INSTANCE = new HudEditorScreen();

    private final RectRenderer rectRenderer = new RectRenderer();
    private final HudEditorOverlayRenderer overlayRenderer = new HudEditorOverlayRenderer();
    private final HudEditorInspector inspector = new HudEditorInspector();

    private HudModule dragging;
    private HudModule selected;
    private double dragOffsetX;
    private double dragOffsetY;
    private Float snapPreviewX;
    private Float snapPreviewY;

    private @Nullable LuminRenderSystem.LuminRenderTarget renderTarget;
    private @Nullable IMEPreeditOverlay preeditOverlay;

    private HudEditorScreen() {
        super(Component.literal("HUDEditor"));
    }

    @Override
    protected void init() {
        NotificationManager.INSTANCE.clear();
        super.init();
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float a) {

        final var window = minecraft.getWindow();
        if (renderTarget == null) {
            renderTarget = LuminRenderSystem.LuminRenderTarget.create("hud-editor", window.getWidth(), window.getHeight());
        }
        renderTarget.clear();
        renderTarget.resize(window.getWidth(), window.getHeight());

        LuminRenderSystem.setActiveTarget(renderTarget);

        MD3Theme.syncFromSettings();

        RenderManager.INSTANCE.applyRender(delta -> {
            int screenWidth = minecraft.getWindow().getGuiScaledWidth();
            int screenHeight = minecraft.getWindow().getGuiScaledHeight();
            List<HudModule> hudModules = HudEditorModules.collectEnabledHudModules(delta);
            syncSelectionState(hudModules);

            HudModule hovered = HudEditorModules.findTopmost(hudModules, mouseX, mouseY);
            HudModule focus = dragging != null ? dragging : (selected != null ? selected : hovered);
            boolean draggingFocus = focus != null && focus == dragging;

            if (focus != null) {
                overlayRenderer.addThirdGuides(focus, draggingFocus, screenWidth, screenHeight);
                overlayRenderer.flushRenderer();
            }

            for (HudModule hudModule : hudModules) {
                rectRenderer.addRect(hudModule.x, hudModule.y, hudModule.width, hudModule.height, BOX_COLOR);
                if (hudModule == selected) {
                    rectRenderer.addRect(hudModule.x, hudModule.y, hudModule.width, hudModule.height, SELECTED_COLOR);
                }
                if (hudModule == hovered) {
                    rectRenderer.addRect(hudModule.x, hudModule.y, hudModule.width, hudModule.height, HOVER_COLOR);
                }
                if (hudModule == dragging) {
                    rectRenderer.addRect(hudModule.x, hudModule.y, hudModule.width, hudModule.height, DRAGGING_COLOR);
                }
            }

            rectRenderer.drawAndClear();

            for (HudModule hudModule : hudModules) {
                hudModule.render(delta);
            }

            if (focus != null) {
                overlayRenderer.addAnchorOverlay(focus, draggingFocus, screenWidth, screenHeight);
            }

            overlayRenderer.addSnapPreview(snapPreviewX, snapPreviewY, screenWidth, screenHeight);
            overlayRenderer.flushRenderer();
            inspector.queueRender(guiGraphics, selected, screenWidth, screenHeight, mouseX, mouseY, a, guiGraphics.guiHeight());
        });

        inspector.renderPopups(guiGraphics, mouseX, mouseY, a);

        LuminRenderSystem.setActiveTarget(null);

        if (preeditOverlay != null) {
            preeditOverlay.updateInputPosition((int) IMEFocusHelper.activeCursorX, (int) IMEFocusHelper.activeCursorY);
            guiGraphics.setPreeditOverlay(preeditOverlay);
        }

        guiGraphics.blit(renderTarget.getIdentifier(), 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 0, 1, 1, 0);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean isDoubleClick) {
        if (inspector.mouseClicked(event, isDoubleClick)) {
            return true;
        }

        if (event.button() == 0) {
            double mx = event.x();
            double my = event.y();
            List<HudModule> hudModules = HudEditorModules.collectEnabledHudModules(null);
            syncSelectionState(hudModules);
            HudModule hovered = HudEditorModules.findTopmost(hudModules, mx, my);
            if (hovered != null) {
                inspector.clearFocus();
                selected = hovered;
                dragging = hovered;
                dragOffsetX = mx - hovered.x;
                dragOffsetY = my - hovered.y;
                clearSnapPreview();
                return true;
            }

            clearSelection();
            return true;
        }

        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double mouseX, double mouseY) {
        if (inspector.mouseDragged(event, mouseX, mouseY)) {
            return true;
        }

        if (dragging != null && event.button() == 0) {
            int screenWidth = minecraft.getWindow().getGuiScaledWidth();
            int screenHeight = minecraft.getWindow().getGuiScaledHeight();
            List<HudModule> hudModules = HudEditorModules.collectEnabledHudModules(null);
            float targetX = (float) (event.x() - dragOffsetX);
            float targetY = (float) (event.y() - dragOffsetY);
            HudEditorSnapper.SnapPosition snap = event.hasAltDown()
                    ? new HudEditorSnapper.SnapPosition(targetX, targetY, null, null)
                    : HudEditorSnapper.snapPosition(dragging, targetX, targetY, screenWidth, screenHeight, hudModules);

            dragging.moveTo(snap.renderX(), snap.renderY());
            snapPreviewX = snap.guideX();
            snapPreviewY = snap.guideY();
            return true;
        }

        return super.mouseDragged(event, mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (inspector.mouseReleased(event)) {
            ConfigManager.INSTANCE.saveNow();
            return true;
        }

        if (dragging != null && event.button() == 0) {
            dragging = null;
            clearSnapPreview();
            ConfigManager.INSTANCE.saveNow();
            return true;
        }

        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inspector.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) {
            if (inspector.keyPressed(event)) {
                return true;
            }
            if (dragging != null) {
                dragging = null;
                clearSnapPreview();
                ConfigManager.INSTANCE.saveNow();
                return true;
            }
            if (selected != null) {
                clearSelection();
                return true;
            }
            onClose();
            return true;
        }
        if (inspector.keyPressed(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        if (inspector.charTyped(event)) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean preeditUpdated(@Nullable PreeditEvent event) {
        this.preeditOverlay = event != null ? new IMEPreeditOverlay(event, this.font, 10) : null;
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        dragging = null;
        clearSnapPreview();
        IMEFocusHelper.deactivate();
        ConfigManager.INSTANCE.saveNow();
        super.onClose();
        minecraft.setScreen(ComposePanelScreen.INSTANCE);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
    }

    private void clearSnapPreview() {
        snapPreviewX = null;
        snapPreviewY = null;
    }

    private void clearSelection() {
        selected = null;
        inspector.clearFocus();
    }

    private void syncSelectionState(List<HudModule> hudModules) {
        if (dragging != null && !hudModules.contains(dragging)) {
            dragging = null;
            clearSnapPreview();
        }
        if (selected != null && !hudModules.contains(selected)) {
            clearSelection();
        }
    }

}
