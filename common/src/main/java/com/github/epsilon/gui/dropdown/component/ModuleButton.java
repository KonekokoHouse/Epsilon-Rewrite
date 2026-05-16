package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.gui.dropdown.DropdownRenderer;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.widget.*;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.client.KeybindUtils;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ModuleButton extends Component {

    private final Module module;
    private final List<SettingWidget<?>> widgets = new ArrayList<>();
    private final Animation expandAnim = new Animation(Easing.EASE_IN_OUT_CUBIC, DropdownTheme.ANIM_EXPAND);
    private final Animation toggleAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_TOGGLE);
    private final Animation hoverAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER);
    private final Animation keybindHoverAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER);
    private boolean expanded;
    private boolean listeningKeybind;

    public ModuleButton(Module module) {
        this.module = module;
        for (Setting<?> setting : module.getSettings()) {
            SettingWidget<?> widget = createWidget(setting);
            if (widget != null) {
                widgets.add(widget);
            }
        }
    }

    private static SettingWidget<?> createWidget(Setting<?> setting) {
        if (setting instanceof BoolSetting s) return new BoolWidget(s);
        if (setting instanceof IntSetting s) return new IntSliderWidget(s);
        if (setting instanceof DoubleSetting s) return new DoubleSliderWidget(s);
        if (setting instanceof EnumSetting<?> s) return new EnumWidget(s);
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

        Color bg = MD3Theme.lerp(DropdownTheme.moduleDisabled(hover), DropdownTheme.moduleEnabled(hover), toggle);
        renderer.rect().addRect(x + 2.0f, y, width - 4.0f, DropdownTheme.MODULE_HEIGHT, bg);
        renderer.rect().addRect(x + 3.0f, y + DropdownTheme.MODULE_HEIGHT - 0.5f, width - 6.0f, 0.5f, DropdownTheme.moduleDivider());

        Color textColor = MD3Theme.lerp(DropdownTheme.moduleTextDisabled(hover), DropdownTheme.moduleTextEnabled(), toggle);
        float textY = y + (DropdownTheme.MODULE_HEIGHT - renderer.text().getHeight(DropdownTheme.MODULE_TEXT_SCALE)) * 0.5f;
        renderer.text().addText(module.getTranslatedName(), x + DropdownTheme.MODULE_PADDING_X, textY, DropdownTheme.MODULE_TEXT_SCALE, textColor);

        drawKeybindButton(renderer, mouseX, mouseY, toggle);

        float expand = expandAnim.getValue();
        if (expand > 0.01f) {
            float settingY = y + DropdownTheme.MODULE_HEIGHT;
            for (SettingWidget<?> widget : widgets) {
                if (!widget.isVisible()) continue;
                widget.setPosition(x + DropdownTheme.SETTING_INDENT, settingY, width - DropdownTheme.SETTING_INDENT * 2.0f);
                if (expand > 0.5f) {
                    widget.draw(renderer, mouseX, mouseY);
                }
                settingY += widget.getHeight() + DropdownTheme.SETTING_GAP;
            }
        }
    }

    private void drawKeybindButton(DropdownRenderer renderer, int mouseX, int mouseY, float toggle) {
        float btnW = DropdownTheme.KEYBIND_WIDTH;
        float btnH = DropdownTheme.KEYBIND_HEIGHT;
        float btnX = x + width - DropdownTheme.MODULE_PADDING_X - btnW;
        float btnY = y + (DropdownTheme.MODULE_HEIGHT - btnH) * 0.5f;
        float radius = DropdownTheme.KEYBIND_RADIUS;
        boolean btnHovered = isHovered(mouseX, mouseY, btnX, btnY, btnW, btnH);
        keybindHoverAnim.run(btnHovered ? 1.0f : 0.0f);
        float kbHover = keybindHoverAnim.getValue();

        String keyText = listeningKeybind ? "..." : formatCompactKeybind(module.getKeyBind());
        float textScale = keyText.length() >= 3 ? 0.46f : 0.52f;
        float textW = renderer.text().getWidth(keyText, textScale);
        float textH = renderer.text().getHeight(textScale);

        Color surface;
        Color text;
        Color outline;
        if (listeningKeybind) {
            surface = DropdownTheme.keybindSurface(true);
            text = DropdownTheme.keybindText(true);
            outline = MD3Theme.withAlpha(MD3Theme.PRIMARY, 200);
        } else {
            Color idleSurface = DropdownTheme.keybindSurface(false);
            Color activeSurface = MD3Theme.lerp(MD3Theme.PRIMARY_CONTAINER, MD3Theme.PRIMARY, 0.38f);
            surface = MD3Theme.lerp(idleSurface, activeSurface, toggle);
            surface = MD3Theme.lerp(surface, MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER_HIGHEST, MD3Theme.PRIMARY, 0.15f), kbHover * 0.4f);
            text = MD3Theme.lerp(DropdownTheme.keybindText(false), MD3Theme.lerp(MD3Theme.ON_PRIMARY_CONTAINER, MD3Theme.PRIMARY, 0.15f), toggle);
            outline = MD3Theme.lerp(MD3Theme.withAlpha(MD3Theme.OUTLINE, 140), MD3Theme.withAlpha(MD3Theme.PRIMARY, 200), toggle);
            outline = MD3Theme.lerp(outline, MD3Theme.withAlpha(MD3Theme.TEXT_PRIMARY, 180), kbHover * 0.45f);
        }

        renderer.roundRect().addRoundRect(btnX, btnY, btnW, btnH, radius, surface);
        renderer.outline().addOutline(btnX, btnY, btnW, btnH, radius, 0.8f, outline);

        float textX = btnX + (btnW - textW) * 0.5f;
        float textY = btnY + (btnH - textH) * 0.5f - 0.5f;
        renderer.text().addText(keyText, textX, textY, textScale, text);
        if (module.getBindMode() == Module.BindMode.Hold && !listeningKeybind) {
            renderer.rect().addRect(textX, textY + textH + 0.5f, textW, 0.75f, text);
        }
    }

    private String formatCompactKeybind(int keyCode) {
        if (keyCode == KeybindUtils.NONE) return "NONE";
        if (KeybindUtils.isMouseButton(keyCode)) return "M" + (KeybindUtils.decodeMouseButton(keyCode) + 1);
        String label = KeybindUtils.format(keyCode).trim();
        if (label.isEmpty()) return "?";

        String[] parts = label.split("[^A-Za-z0-9]+");
        StringBuilder initials = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty() && Character.isLetterOrDigit(part.charAt(0))) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
            if (initials.length() == 3) break;
        }
        if (initials.length() >= 2) return initials.toString();

        String compact = label.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (!compact.isEmpty()) return compact.length() > 3 ? compact.substring(0, 3) : compact;
        return label.length() > 3 ? label.substring(0, 3) : label;
    }

    private boolean isKeybindButtonHovered(double mouseX, double mouseY) {
        float btnX = x + width - DropdownTheme.MODULE_PADDING_X - DropdownTheme.KEYBIND_WIDTH;
        float btnY = y + (DropdownTheme.MODULE_HEIGHT - DropdownTheme.KEYBIND_HEIGHT) * 0.5f;
        return isHovered(mouseX, mouseY, btnX, btnY, DropdownTheme.KEYBIND_WIDTH, DropdownTheme.KEYBIND_HEIGHT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (listeningKeybind) {
            module.setKeyBind(KeybindUtils.encodeMouseButton(button));
            listeningKeybind = false;
            return true;
        }

        if (isHovered(mouseX, mouseY, x, y, width, DropdownTheme.MODULE_HEIGHT)) {
            if (isKeybindButtonHovered(mouseX, mouseY)) {
                if (button == 0) {
                    listeningKeybind = true;
                    return true;
                }
                if (button == 2) {
                    module.setBindMode(module.getBindMode() == Module.BindMode.Toggle ? Module.BindMode.Hold : Module.BindMode.Toggle);
                    return true;
                }
            }
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
        if (listeningKeybind) {
            module.setKeyBind(keyCode == 256 || keyCode == 259 ? KeybindUtils.NONE : keyCode);
            listeningKeybind = false;
            return true;
        }

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
        if (listeningKeybind) return true;
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
