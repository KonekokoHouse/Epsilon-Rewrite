package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.gui.dropdown.DropdownRenderer;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.SettingGroup;

import java.util.List;

public class SettingsListPanel extends AbstractDropdownPanel {

    private final SettingsContent settingsContent;

    public SettingsListPanel(String id, String title, String icon, int panelIndex, List<Setting<?>> settings) {
        this(id, title, icon, panelIndex, settings, List.of());
    }

    public SettingsListPanel(String id, TranslateComponent titleComponent, String icon, int panelIndex, List<Setting<?>> settings, List<SettingGroup> orderedGroups) {
        super(id, titleComponent, icon, panelIndex);
        this.settingsContent = new SettingsContent(settings, orderedGroups);
    }

    public SettingsListPanel(String id, String title, String icon, int panelIndex, List<Setting<?>> settings, List<SettingGroup> orderedGroups) {
        super(id, title, icon, panelIndex);
        this.settingsContent = new SettingsContent(settings, orderedGroups);
    }

    @Override
    protected float computeContentHeight() {
        return settingsContent.computeContentHeight();
    }

    @Override
    protected void drawPanelContent(DropdownRenderer renderer, int mouseX, int mouseY, float visibleHeight) {
        settingsContent.draw(renderer, mouseX, mouseY, x, y + DropdownTheme.PANEL_HEADER_HEIGHT - scroll, width);
    }

    @Override
    protected boolean mouseClickedContent(double mouseX, double mouseY, int button) {
        return settingsContent.mouseClicked(mouseX, mouseY, button, x, y + DropdownTheme.PANEL_HEADER_HEIGHT - scroll, width);
    }

    @Override
    protected boolean mouseReleasedContent(double mouseX, double mouseY, int button) {
        return settingsContent.mouseReleased(mouseX, mouseY, button, x, y + DropdownTheme.PANEL_HEADER_HEIGHT - scroll, width);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return settingsContent.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(String typedText) {
        return settingsContent.charTyped(typedText);
    }

    @Override
    public boolean hasActiveInput() {
        return settingsContent.hasActiveInput();
    }

    public static com.github.epsilon.gui.dropdown.widget.SettingWidget<?> createWidget(Setting<?> setting) {
        return SettingsContent.createWidget(setting);
    }

}
