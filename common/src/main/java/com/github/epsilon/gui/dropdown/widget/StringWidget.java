package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownRenderer;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.settings.impl.StringSetting;

public class StringWidget extends SettingWidget<StringSetting> {

    private boolean focused;

    public StringWidget(StringSetting setting) {
        super(setting);
    }

    @Override
    public float getHeight() {
        return DropdownTheme.SETTING_HEIGHT + DropdownTheme.INPUT_HEIGHT + 2.0f;
    }

    @Override
    public void draw(DropdownRenderer renderer, int mouseX, int mouseY) {
        renderer.text().addText(setting.getDisplayName(), x + DropdownTheme.SETTING_PADDING_X, y + 1.0f, DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.settingLabel());

        float fieldX = x + DropdownTheme.SETTING_PADDING_X;
        float fieldY = y + DropdownTheme.SETTING_HEIGHT;
        float fieldW = width - DropdownTheme.SETTING_PADDING_X * 2.0f;
        float fieldH = DropdownTheme.INPUT_HEIGHT;

        renderer.roundRect().addRoundRect(fieldX, fieldY, fieldW, fieldH, DropdownTheme.INPUT_RADIUS, DropdownTheme.inputSurface(focused));

        float indicatorH = focused ? 1.5f : 1.0f;
        renderer.rect().addRect(fieldX + 2.0f, fieldY + fieldH - indicatorH, fieldW - 4.0f, indicatorH, DropdownTheme.inputIndicator(focused));

        String displayText = setting.getValue();
        if (displayText.isEmpty() && !focused) {
            displayText = "...";
        }
        if (focused && System.currentTimeMillis() % 1000 > 500) {
            displayText = displayText + "|";
        }

        float textY = fieldY + (fieldH - renderer.text().getHeight(DropdownTheme.SETTING_TEXT_SCALE)) * 0.5f;
        renderer.text().addText(displayText, fieldX + 4.0f, textY, DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.inputText());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float fieldX = x + DropdownTheme.SETTING_PADDING_X;
        float fieldY = y + DropdownTheme.SETTING_HEIGHT;
        float fieldW = width - DropdownTheme.SETTING_PADDING_X * 2.0f;
        float fieldH = DropdownTheme.INPUT_HEIGHT;

        if (button == 0 && isHovered(mouseX, mouseY, fieldX, fieldY, fieldW, fieldH)) {
            focused = !focused;
            return true;
        }
        if (focused) {
            focused = false;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused) return false;

        if (keyCode == 259) {
            String current = setting.getValue();
            if (!current.isEmpty()) {
                setting.setValue(current.substring(0, current.length() - 1));
            }
            return true;
        }
        if (keyCode == 256) {
            focused = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(String typedText) {
        if (!focused || typedText.isEmpty()) return false;

        String current = setting.getValue();
        if (current.length() < 100) {
            setting.setValue(current + typedText);
        }
        return true;
    }

    public boolean isFocused() {
        return focused;
    }

}
