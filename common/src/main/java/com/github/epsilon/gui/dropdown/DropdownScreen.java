package com.github.epsilon.gui.dropdown;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.gui.dropdown.component.CategoryPanel;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.modules.Category;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class DropdownScreen extends Screen {

    public static final DropdownScreen INSTANCE = new DropdownScreen();

    private final List<CategoryPanel> panels = new ArrayList<>();
    private final DropdownRenderer renderer = new DropdownRenderer();
    private final Animation openAnim = new Animation(Easing.EASE_OUT_CUBIC, 300L);

    private LuminRenderSystem.LuminRenderTarget renderTarget;
    private boolean initialized;

    private DropdownScreen() {
        super(Component.literal("DropdownGui"));
    }

    @Override
    protected void init() {
        super.init();
        openAnim.setStartValue(0.0f);
        openAnim.run(1.0f);

        if (!initialized) {
            panels.clear();
            float offsetX = DropdownTheme.PANEL_MARGIN_X;
            for (Category category : Category.values()) {
                CategoryPanel panel = new CategoryPanel(category);
                panel.setPosition(offsetX, DropdownTheme.PANEL_MARGIN_Y);
                panels.add(panel);
                offsetX += panel.getWidth() + DropdownTheme.PANEL_GAP;
            }
            initialized = true;
        }

        float maxH = height * 0.82f;
        for (CategoryPanel panel : panels) {
            panel.setMaxPanelHeight(maxH);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        final var window = minecraft.getWindow();
        if (renderTarget == null) {
            renderTarget = LuminRenderSystem.LuminRenderTarget.create("dropdown-gui", window.getWidth(), window.getHeight());
        }
        renderTarget.clear();
        renderTarget.resize(window.getWidth(), window.getHeight());
        LuminRenderSystem.setActiveTarget(renderTarget);

        MD3Theme.syncFromSettings();
        drawGui(mouseX, mouseY);

        LuminRenderSystem.setActiveTarget(null);
        graphics.blit(renderTarget.getIdentifier(), 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 0, 1, 1, 0);
    }

    private void drawGui(int mouseX, int mouseY) {
        openAnim.run(1.0f);
        renderer.beginFrame();

        renderer.beginPass();
        renderer.rect().addRect(0, 0, width, height, DropdownTheme.scrim());
        for (CategoryPanel panel : panels) {
            panel.drawBackground(renderer);
        }
        renderer.flush();

        for (CategoryPanel panel : panels) {
            float clipY = panel.getContentClipY();
            float clipH = panel.getContentClipHeight();
            if (clipH > 0.5f) {
                renderer.beginPass();
                renderer.setScissor(panel.getX(), clipY, panel.getWidth(), clipH, height);
                panel.drawContent(renderer, mouseX, mouseY);
                renderer.flush();
                renderer.clearScissor();
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();

        for (int i = panels.size() - 1; i >= 0; i--) {
            if (panels.get(i).mouseClicked(mx, my, button)) {
                return true;
            }
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();

        for (CategoryPanel panel : panels) {
            if (panel.mouseReleased(mx, my, button)) {
                return true;
            }
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        for (CategoryPanel panel : panels) {
            panel.mouseDragged(mouseX, mouseY);
        }
        return super.mouseDragged(event, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (CategoryPanel panel : panels) {
            if (panel.mouseScrolled(mouseX, mouseY, scrollY)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean hasActiveInput = panels.stream().anyMatch(CategoryPanel::hasActiveInput);

        if (hasActiveInput) {
            for (CategoryPanel panel : panels) {
                if (panel.keyPressed(event.key(), event.scancode(), event.modifiers())) {
                    return true;
                }
            }
        }

        if (event.isEscape()) {
            onClose();
            return true;
        }

        for (CategoryPanel panel : panels) {
            if (panel.keyPressed(event.key(), event.scancode(), event.modifiers())) {
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        for (CategoryPanel panel : panels) {
            String typed = event.codepointAsString();
            if (!typed.isEmpty() && panel.charTyped(typed)) {
                return true;
            }
        }
        return super.charTyped(event);
    }

    @Override
    public void removed() {
        super.removed();
        if (renderTarget != null) {
            renderTarget.close();
            renderTarget = null;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

}
