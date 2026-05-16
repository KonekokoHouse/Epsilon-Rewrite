package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.gui.dropdown.DropdownRenderer;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.widget.*;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;

import java.util.ArrayList;
import java.util.List;

public class ModuleButton extends Component {

    private final Module module;
    private final List<SettingWidget<?>> widgets = new ArrayList<>();
    private final Animation expandAnim = new Animation(Easing.EASE_IN_OUT_CUBIC, DropdownTheme.ANIM_EXPAND);
    private final Animation toggleAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_TOGGLE);
    private final Animation hoverAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER);
    private boolean expanded;

    public ModuleButton(Module module) {
        this.module = module;
        for (Setting<?> setting : module.getSettings()) {
            SettingWidget<?> widget = createWidget(setting);
            if (widget != null) {
                widgets.add(widget);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static SettingWidget<?> createWidget(Setting<?> setting) {
        if (setting instanceof BoolSetting s) return new BoolWidget(s);
        if (setting instanceof IntSetting s) return new IntSliderWidget(s);
        if (setting instanceof DoubleSetting s) return new DoubleSliderWidget(s);
        if (setting instanceof EnumSetting<?> s) return new EnumWidget((EnumSetting<?>) s);
        if (setting instanceof ColorSetting s) return new ColorWidget(s);
        if (setting instanceof KeybindSetting s) return new KeybindWidget(s);
        if (setting instanceof StringSetting s) return new StringWidget(s);
        if (setting instanceof ButtonSetting s) return new ButtonWidget(s);
        return null;
    }

    @Override
    public float getHeight() {
        expandAnim.run(expanded ? 1.0f : 0.0f);
        float settingsHeight = 0.0f;
        for (SettingWidget<?> widget : widgets) {
            if (widget.isVisible()) {
                settingsHeight += widget.getHeight() + DropdownTheme.SETTING_GAP;
            }
        }
        return DropdownTheme.MODULE_HEIGHT + settingsHeight * expandAnim.getValue();
    }

    @Override
    public void draw(DropdownRenderer renderer, int mouseX, int mouseY) {
        expandAnim.run(expanded ? 1.0f : 0.0f);
        toggleAnim.run(module.isEnabled() ? 1.0f : 0.0f);
        boolean headerHovered = isHovered(mouseX, mouseY, x, y, width, DropdownTheme.MODULE_HEIGHT);
        hoverAnim.run(headerHovered ? 1.0f : 0.0f);

        float hover = hoverAnim.getValue();
        float toggle = toggleAnim.getValue();

        java.awt.Color bg = MD3Theme.lerp(DropdownTheme.moduleDisabled(hover), DropdownTheme.moduleEnabled(hover), toggle);
        renderer.rect().addRect(x + 2.0f, y, width - 4.0f, DropdownTheme.MODULE_HEIGHT, bg);

        java.awt.Color textColor = MD3Theme.lerp(DropdownTheme.moduleTextDisabled(hover), DropdownTheme.moduleTextEnabled(), toggle);
        float textY = y + (DropdownTheme.MODULE_HEIGHT - renderer.text().getHeight(DropdownTheme.MODULE_TEXT_SCALE)) * 0.5f;
        renderer.text().addText(module.getTranslatedName(), x + DropdownTheme.MODULE_PADDING_X, textY, DropdownTheme.MODULE_TEXT_SCALE, textColor);

        if (!widgets.isEmpty()) {
            float arrowX = x + width - DropdownTheme.MODULE_PADDING_X - 4.0f;
            float arrowY = y + DropdownTheme.MODULE_HEIGHT * 0.5f;
            renderer.triangle().addChevronTriangle(arrowX, arrowY, 3.0f, expandAnim.getValue(), DropdownTheme.expandArrow(toggle));
        }

        float expand = expandAnim.getValue();
        if (expand > 0.01f) {
            float settingY = y + DropdownTheme.MODULE_HEIGHT;
            for (SettingWidget<?> widget : widgets) {
                if (!widget.isVisible()) continue;
                widget.setPosition(x + DropdownTheme.SETTING_INDENT, settingY, width - DropdownTheme.SETTING_INDENT * 2.0f);
                if (expand > 0.5f) {
                    widget.draw(renderer, mouseX, mouseY);
                }
                settingY += (widget.getHeight() + DropdownTheme.SETTING_GAP) * expand;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY, x, y, width, DropdownTheme.MODULE_HEIGHT)) {
            if (button == 0) {
                module.toggle();
                return true;
            }
            if (button == 1) {
                expanded = !expanded;
                return true;
            }
        }

        if (expanded && expandAnim.getValue() > 0.5f) {
            for (SettingWidget<?> widget : widgets) {
                if (!widget.isVisible()) continue;
                if (widget.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (expanded) {
            for (SettingWidget<?> widget : widgets) {
                if (!widget.isVisible()) continue;
                if (widget.mouseReleased(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (expanded) {
            for (SettingWidget<?> widget : widgets) {
                if (!widget.isVisible()) continue;
                if (widget.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(String typedText) {
        if (expanded) {
            for (SettingWidget<?> widget : widgets) {
                if (!widget.isVisible()) continue;
                if (widget.charTyped(typedText)) {
                    return true;
                }
            }
        }
        return false;
    }

    public Module getModule() {
        return module;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public boolean hasListeningKeybind() {
        for (SettingWidget<?> widget : widgets) {
            if (widget instanceof KeybindWidget kw && kw.isListening()) return true;
        }
        return false;
    }

    public boolean hasFocusedInput() {
        for (SettingWidget<?> widget : widgets) {
            if (widget instanceof StringWidget sw && sw.isFocused()) return true;
        }
        return false;
    }

}
