package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.dropdown.DropdownRenderer;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.managers.ModuleManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;

import java.util.ArrayList;
import java.util.List;

public class CategoryPanel {

    private final Category category;
    private final List<ModuleButton> moduleButtons = new ArrayList<>();
    private final Animation openAnim = new Animation(Easing.EASE_IN_OUT_CUBIC, DropdownTheme.ANIM_OPEN);

    private float x;
    private float y;
    private float width = DropdownTheme.PANEL_WIDTH;
    private boolean opened = true;
    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;
    private float scroll;
    private float maxScroll;
    private float maxPanelHeight = 300.0f;

    public CategoryPanel(Category category) {
        this.category = category;
        List<Module> modules = ModuleManager.INSTANCE.getModules().stream()
                .filter(m -> m.getCategory() == category)
                .toList();
        for (Module module : modules) {
            moduleButtons.add(new ModuleButton(module));
        }
    }

    public void drawBackground(DropdownRenderer renderer) {
        openAnim.run(opened ? 1.0f : 0.0f);
        float contentHeight = computeContentHeight();
        float visibleHeight = Math.min(contentHeight, maxPanelHeight - DropdownTheme.PANEL_HEADER_HEIGHT);
        float panelHeight = DropdownTheme.PANEL_HEADER_HEIGHT + visibleHeight * openAnim.getValue();

        renderer.shadow().addShadow(x, y, width, panelHeight, DropdownTheme.PANEL_RADIUS, DropdownTheme.PANEL_SHADOW_BLUR, DropdownTheme.panelShadow());
        renderer.roundRect().addRoundRect(x, y, width, panelHeight, DropdownTheme.PANEL_RADIUS, DropdownTheme.panelBackground());
        renderer.roundRect().addRoundRect(x, y, width, DropdownTheme.PANEL_HEADER_HEIGHT, DropdownTheme.PANEL_RADIUS, DropdownTheme.PANEL_RADIUS, 0.0f, 0.0f, DropdownTheme.panelHeader());

        float iconX = x + 5.0f;
        float textX = iconX + 10.0f;
        float textY = y + (DropdownTheme.PANEL_HEADER_HEIGHT - renderer.text().getHeight(DropdownTheme.HEADER_TEXT_SCALE)) * 0.5f;
        renderer.text().addText(category.icon, iconX, textY, DropdownTheme.HEADER_ICON_SCALE, MD3Theme.PRIMARY, StaticFontLoader.ICONS);
        renderer.text().addText(category.getName(), textX, textY, DropdownTheme.HEADER_TEXT_SCALE, MD3Theme.TEXT_PRIMARY);

        if (contentHeight > visibleHeight && opened && openAnim.getValue() > 0.5f) {
            float scrollbarX = x + width - 2.5f;
            float scrollbarTrackY = y + DropdownTheme.PANEL_HEADER_HEIGHT;
            float scrollbarTrackH = visibleHeight * openAnim.getValue();
            float thumbRatio = visibleHeight / contentHeight;
            float thumbH = Math.max(10.0f, scrollbarTrackH * thumbRatio);
            float thumbY = scrollbarTrackY + (scrollbarTrackH - thumbH) * (maxScroll > 0 ? scroll / maxScroll : 0);
            renderer.roundRect().addRoundRect(scrollbarX, thumbY, 2.0f, thumbH, 1.0f, DropdownTheme.scrollbar());
        }
    }

    public void drawContent(DropdownRenderer renderer, int mouseX, int mouseY) {
        openAnim.run(opened ? 1.0f : 0.0f);
        float expand = openAnim.getValue();
        if (expand < 0.01f) return;

        float contentHeight = computeContentHeight();
        float visibleHeight = Math.min(contentHeight, maxPanelHeight - DropdownTheme.PANEL_HEADER_HEIGHT);
        maxScroll = Math.max(0.0f, contentHeight - visibleHeight);
        scroll = Math.max(0.0f, Math.min(scroll, maxScroll));

        float currentY = y + DropdownTheme.PANEL_HEADER_HEIGHT - scroll;
        for (ModuleButton button : moduleButtons) {
            button.setPosition(x, currentY, width);
            float btnH = button.getHeight();

            float visibleTop = y + DropdownTheme.PANEL_HEADER_HEIGHT;
            float visibleBottom = visibleTop + visibleHeight * expand;
            if (currentY + btnH > visibleTop && currentY < visibleBottom) {
                button.draw(renderer, mouseX, mouseY);
            }

            currentY += btnH;
        }
    }

    public float getContentClipY() {
        return y + DropdownTheme.PANEL_HEADER_HEIGHT;
    }

    public float getContentClipHeight() {
        openAnim.run(opened ? 1.0f : 0.0f);
        float contentHeight = computeContentHeight();
        float visibleHeight = Math.min(contentHeight, maxPanelHeight - DropdownTheme.PANEL_HEADER_HEIGHT);
        return visibleHeight * openAnim.getValue();
    }

    public float getPanelHeight() {
        openAnim.run(opened ? 1.0f : 0.0f);
        float contentHeight = computeContentHeight();
        float visibleHeight = Math.min(contentHeight, maxPanelHeight - DropdownTheme.PANEL_HEADER_HEIGHT);
        return DropdownTheme.PANEL_HEADER_HEIGHT + visibleHeight * openAnim.getValue();
    }

    private float computeContentHeight() {
        float total = 0.0f;
        for (ModuleButton button : moduleButtons) {
            total += button.getHeight();
        }
        return total;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHeaderHovered(mouseX, mouseY)) {
            if (button == 0) {
                dragging = true;
                dragOffsetX = (float) (x - mouseX);
                dragOffsetY = (float) (y - mouseY);
                return true;
            }
            if (button == 1) {
                opened = !opened;
                return true;
            }
        }

        if (opened && openAnim.getValue() > 0.5f && isContentHovered(mouseX, mouseY)) {
            for (ModuleButton mb : moduleButtons) {
                if (mb.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        for (ModuleButton mb : moduleButtons) {
            if (mb.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    public void mouseDragged(double mouseX, double mouseY) {
        if (dragging) {
            x = (float) (mouseX + dragOffsetX);
            y = (float) (mouseY + dragOffsetY);
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!opened) return false;
        if (isPanelHovered(mouseX, mouseY)) {
            scroll -= (float) amount * DropdownTheme.SCROLL_SPEED;
            scroll = Math.max(0.0f, Math.min(scroll, maxScroll));
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (ModuleButton mb : moduleButtons) {
            if (mb.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(String typedText) {
        for (ModuleButton mb : moduleButtons) {
            if (mb.charTyped(typedText)) {
                return true;
            }
        }
        return false;
    }

    private boolean isHeaderHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + DropdownTheme.PANEL_HEADER_HEIGHT;
    }

    private boolean isContentHovered(double mouseX, double mouseY) {
        float clipY = getContentClipY();
        float clipH = getContentClipHeight();
        return mouseX >= x && mouseX <= x + width && mouseY >= clipY && mouseY <= clipY + clipH;
    }

    private boolean isPanelHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + getPanelHeight();
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void setMaxPanelHeight(float maxPanelHeight) {
        this.maxPanelHeight = maxPanelHeight;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public Category getCategory() {
        return category;
    }

    public boolean hasActiveInput() {
        for (ModuleButton mb : moduleButtons) {
            if (mb.hasListeningKeybind() || mb.hasFocusedInput()) return true;
        }
        return false;
    }

}
