package com.github.epsilon.gui.hudeditor;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.RectRenderer;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelScreen;
import com.github.epsilon.gui.panel.utils.IMEFocusHelper;
import com.github.epsilon.modules.HudModule;
import com.github.epsilon.modules.impl.hud.notification.NotificationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;

import java.awt.*;
import java.util.List;

public final class HudEditorScreen extends Screen {

    public static final HudEditorScreen INSTANCE = new HudEditorScreen();

    private static final Color BOX_COLOR = new Color(0, 0, 0, 100);
    private static final Color SELECTED_COLOR = new Color(188, 224, 255, 56);
    private static final Color HOVER_COLOR = new Color(255, 255, 255, 70);
    private static final Color DRAGGING_COLOR = new Color(120, 190, 255, 80);
    private static final int MOVE_STEP_NORMAL = 1;
    private static final int MOVE_STEP_FINE = 10;

    private final RectRenderer rectRenderer = new RectRenderer();
    private final HudEditorOverlayRenderer overlayRenderer = new HudEditorOverlayRenderer();
    private final HudEditorInspector inspector = new HudEditorInspector();

    private HudModule dragging;
    private HudModule selected;
    private double dragOffsetX;
    private double dragOffsetY;
    private Float snapPreviewX;
    private Float snapPreviewY;

    private LuminRenderSystem.LuminRenderTarget renderTarget;
    private IMEPreeditOverlay preeditOverlay;

    private HudEditorScreen() {
        super(Component.literal("HUDEditor"));
    }

    @Override
    protected void init() {
        super.init();
        NotificationManager.INSTANCE.clear();
        this.preeditOverlay = null;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft == null) return;
        if (graphics == null) return;

        var window = minecraft.getWindow();
        int scaledWidth = window.getGuiScaledWidth();
        int scaledHeight = window.getGuiScaledHeight();

        if (renderTarget == null) {
            renderTarget = LuminRenderSystem.LuminRenderTarget.create("hud-editor", scaledWidth, scaledHeight);
        }

        try {
            renderTarget.clear();
            renderTarget.resize(scaledWidth, scaledHeight);
        } catch (Exception ignored) {}

        LuminRenderSystem.setActiveTarget(renderTarget);
        MD3Theme.syncFromSettings();

        List<HudModule> hudModules = HudEditorModules.collectEnabledHudModules();
        syncSelectionState(hudModules);

        HudModule hovered = HudEditorModules.findTopmost(hudModules, mouseX, mouseY);
        HudModule focus = dragging != null ? dragging : (selected != null ? selected : hovered);
        boolean draggingFocus = focus != null && focus == dragging;

        if (focus != null) {
            overlayRenderer.addThirdGuides(focus, draggingFocus, scaledWidth, scaledHeight);
            overlayRenderer.flushRenderer();
        }

        for (HudModule hudModule : hudModules) {
            rectRenderer.addRect(hudModule.x, hudModule.y, hudModule.width, hudModule.height, BOX_COLOR);

            if (hudModule == selected)
                rectRenderer.addRect(hudModule.x, hudModule.y, hudModule.width, hudModule.height, SELECTED_COLOR);
            if (hudModule == hovered)
                rectRenderer.addRect(hudModule.x, hudModule.y, hudModule.width, hudModule.height, HOVER_COLOR);
            if (hudModule == dragging)
                rectRenderer.addRect(hudModule.x, hudModule.y, hudModule.width, hudModule.height, DRAGGING_COLOR);
        }
        rectRenderer.drawAndClear();

        for (HudModule hudModule : hudModules) {
            try {
                hudModule.render(graphics, minecraft.getDeltaTracker());
            } catch (Exception ignored) {}
        }

        if (focus != null) {
            overlayRenderer.addAnchorOverlay(focus, draggingFocus, scaledWidth, scaledHeight);
        }
        overlayRenderer.addSnapPreview(snapPreviewX, snapPreviewY, scaledWidth, scaledHeight);
        overlayRenderer.flushRenderer();

        inspector.queueRender(graphics, selected, scaledWidth, scaledHeight, mouseX, mouseY, partialTick, graphics.guiHeight());
        inspector.renderPopups(graphics, mouseX, mouseY, partialTick);

        LuminRenderSystem.setActiveTarget(null);

        if (preeditOverlay != null) {
            preeditOverlay.updateInputPosition((int) IMEFocusHelper.activeCursorX, (int) IMEFocusHelper.activeCursorY);
            graphics.setPreeditOverlay(preeditOverlay);
        }

        graphics.blit(renderTarget.getIdentifier(), 0, 0, scaledWidth, scaledHeight, 0, 1, 1, 0);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (minecraft == null || event == null) return false;

        if (inspector.mouseClicked(event, isDoubleClick)) {
            return true;
        }

        if (event.button() == 0) {
            double mx = event.x();
            double my = event.y();
            List<HudModule> hudModules = HudEditorModules.collectEnabledHudModules();
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
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        if (minecraft == null || event == null || dragging == null) return false;

        if (inspector.mouseDragged(event, mouseX, mouseY)) {
            return true;
        }

        if (event.button() == 0) {
            int w = minecraft.getWindow().getGuiScaledWidth();
            int h = minecraft.getWindow().getGuiScaledHeight();
            List<HudModule> hudModules = HudEditorModules.collectEnabledHudModules();

            float targetX = (float) (event.x() - dragOffsetX);
            float targetY = (float) (event.y() - dragOffsetY);

            HudEditorSnapper.SnapPosition snap = event.hasAltDown()
                    ? new HudEditorSnapper.SnapPosition(targetX, targetY, null, null)
                    : HudEditorSnapper.snapPosition(dragging, targetX, targetY, w, h, hudModules);

            dragging.moveTo(snap.renderX(), snap.renderY());
            snapPreviewX = snap.guideX();
            snapPreviewY = snap.guideY();
            return true;
        }

        return super.mouseDragged(event, mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event == null) return false;

        if (inspector.mouseReleased(event)) {
            return true;
        }

        if (dragging != null && event.button() == 0) {
            dragging = null;
            clearSnapPreview();
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
        if (event == null) return false;

        if (event.isEscape()) {
            if (inspector.keyPressed(event)) return true;

            if (dragging != null) {
                dragging = null;
                clearSnapPreview();
                return true;
            }
            if (selected != null) {
                clearSelection();
                return true;
            }

            onClose();
            return true;
        }

        if (selected != null) {
            int step = event.hasShiftDown() ? MOVE_STEP_FINE : MOVE_STEP_NORMAL;
            boolean moved = false;

            switch (event.getKey()) {
                263 -> { selected.moveTo(selected.x - step, selected.y); moved = true; }
                262 -> { selected.moveTo(selected.x + step, selected.y); moved = true; }
                264 -> { selected.moveTo(selected.x, selected.y + step); moved = true; }
                265 -> { selected.moveTo(selected.x, selected.y - step); moved = true; }
            }

            if (moved) {
                clearSnapPreview();
                return true;
            }
        }

        if (inspector.keyPressed(event)) {
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (event == null) return false;
        if (inspector.charTyped(event)) return true;
        return super.charTyped(event);
    }

    @Override
    public boolean preeditUpdated(PreeditEvent event) {
        this.preeditOverlay = (event != null) ? new IMEPreeditOverlay(event, font, 10) : null;
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        dragging = null;
        selected = null;
        clearSnapPreview();
        IMEFocusHelper.deactivate();
        inspector.clearFocus();

        super.onClose();

        if (minecraft != null) {
            minecraft.setScreen(PanelScreen.INSTANCE);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

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

    public void destroyRenderTarget() {
        if (renderTarget != null) {
            try {
                renderTarget.destroy();
            } catch (Exception ignored) {}
            renderTarget = null;
        }
    }
}
