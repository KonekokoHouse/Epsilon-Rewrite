package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.gui.dropdown.DropdownRenderer;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.managers.ModuleManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;

import java.util.ArrayList;
import java.util.List;

public class CategoryPanel extends AbstractDropdownPanel {

    private final Category category;
    private final List<ModuleButton> moduleButtons = new ArrayList<>();
    private String searchQuery = "";

    public CategoryPanel(Category category, int panelIndex) {
        super("category:" + category, category::getName, category.icon, panelIndex);
        this.category = category;
        List<Module> modules = ModuleManager.INSTANCE.getModules().stream()
                .filter(m -> m.getCategory() == category)
                .toList();
        for (Module module : modules) {
            moduleButtons.add(new ModuleButton(module));
        }
    }

    @Override
    protected void drawPanelContent(DropdownRenderer renderer, int mouseX, int mouseY, float visibleHeight) {
        float expand = openAnim.getValue();
        float currentY = y + DropdownTheme.PANEL_HEADER_HEIGHT - scroll;
        for (ModuleButton button : moduleButtons) {
            if (!matchesSearch(button)) continue;
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

    @Override
    protected float computeContentHeight() {
        float total = 0.0f;
        for (ModuleButton button : moduleButtons) {
            if (!matchesSearch(button)) continue;
            total += button.getHeight();
        }
        return total;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean mouseClickedContent(double mouseX, double mouseY, int button) {
        for (ModuleButton mb : moduleButtons) {
            if (!matchesSearch(mb)) continue;
            if (mb.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean mouseReleasedContent(double mouseX, double mouseY, int button) {
        for (ModuleButton mb : moduleButtons) {
            if (!matchesSearch(mb)) continue;
            if (mb.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (ModuleButton mb : moduleButtons) {
            if (!matchesSearch(mb)) continue;
            if (mb.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(String typedText) {
        for (ModuleButton mb : moduleButtons) {
            if (!matchesSearch(mb)) continue;
            if (mb.charTyped(typedText)) {
                return true;
            }
        }
        return false;
    }

    public Category getCategory() {
        return category;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery == null ? "" : searchQuery.trim().toLowerCase();
        scroll = 0.0f;
    }

    @Override
    public boolean hasActiveInput() {
        for (ModuleButton mb : moduleButtons) {
            if (!matchesSearch(mb)) continue;
            if (mb.hasListeningKeybind() || mb.hasFocusedInput()) return true;
        }
        return false;
    }

    private boolean matchesSearch(ModuleButton button) {
        if (searchQuery.isBlank()) return true;
        Module module = button.getModule();
        String translated = module.getTranslatedName() == null ? "" : module.getTranslatedName();
        String name = module.getName() == null ? "" : module.getName();
        String categoryName = module.getCategory() == null ? "" : module.getCategory().getName();
        String addon = module.getAddonId() == null ? "" : module.getAddonId();
        return translated.toLowerCase().contains(searchQuery)
                || name.toLowerCase().contains(searchQuery)
                || categoryName.toLowerCase().contains(searchQuery)
                || addon.toLowerCase().contains(searchQuery);
    }

}
