package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownRenderer;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.settings.impl.KeybindSetting;
import com.github.epsilon.utils.client.KeybindUtils;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;

public class KeybindWidget extends SettingWidget<KeybindSetting> {

    private final Animation hoverAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER);
    private boolean listening;

    public KeybindWidget(KeybindSetting setting) {
        super(setting);
    }

    @Override
    public float getHeight() {
        return DropdownTheme.SETTING_HEIGHT;
    }

    @Override
    public void draw(DropdownRenderer renderer, int mouseX, int mouseY) {
        renderer.text().addText(setting.getDisplayName(), x + DropdownTheme.SETTING_PADDING_X, y + (getHeight() - renderer.text().getHeight(DropdownTheme.SETTING_TEXT_SCALE)) * 0.5f, DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.settingLabel());

        String keyText = listening ? "..." : KeybindUtils.format(setting.getValue());
        float textW = renderer.text().getWidth(keyText, DropdownTheme.SETTING_TEXT_SCALE);
        float btnW = Math.max(DropdownTheme.KEYBIND_WIDTH, textW + 8.0f);
        float btnH = DropdownTheme.KEYBIND_HEIGHT;
        float btnX = x + width - DropdownTheme.SETTING_PADDING_X - btnW;
        float btnY = y + (getHeight() - btnH) * 0.5f;

        renderer.roundRect().addRoundRect(btnX, btnY, btnW, btnH, DropdownTheme.KEYBIND_RADIUS, DropdownTheme.keybindSurface(listening));
        renderer.text().addText(keyText, btnX + (btnW - textW) * 0.5f, btnY + (btnH - renderer.text().getHeight(DropdownTheme.SETTING_TEXT_SCALE)) * 0.5f, DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.keybindText(listening));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float btnW = DropdownTheme.KEYBIND_WIDTH;
        float btnH = DropdownTheme.KEYBIND_HEIGHT;
        float btnX = x + width - DropdownTheme.SETTING_PADDING_X - btnW;
        float btnY = y + (getHeight() - btnH) * 0.5f;

        if (button == 0 && isHovered(mouseX, mouseY, btnX, btnY, btnW, btnH)) {
            listening = !listening;
            return true;
        }

        if (listening && button != 0) {
            setting.setValue(KeybindUtils.encodeMouseButton(button));
            listening = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!listening) return false;

        if (keyCode == 256) {
            setting.setValue(KeybindUtils.NONE);
        } else if (keyCode == 259) {
            setting.setValue(KeybindUtils.NONE);
        } else {
            setting.setValue(keyCode);
        }
        listening = false;
        return true;
    }

    public boolean isListening() {
        return listening;
    }

}
