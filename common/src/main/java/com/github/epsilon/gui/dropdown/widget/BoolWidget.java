package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownRenderer;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.util.Mth;

public class BoolWidget extends SettingWidget<BoolSetting> {

    private final Animation toggleAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_TOGGLE);

    public BoolWidget(BoolSetting setting) {
        super(setting);
    }

    @Override
    public float getHeight() {
        return DropdownTheme.SETTING_HEIGHT;
    }

    @Override
    public void draw(DropdownRenderer renderer, int mouseX, int mouseY) {
        toggleAnim.run(setting.getValue() ? 1.0f : 0.0f);
        float t = toggleAnim.getValue();

        renderer.text().addText(setting.getDisplayName(), x + DropdownTheme.SETTING_PADDING_X, y + (getHeight() - renderer.text().getHeight(DropdownTheme.SETTING_TEXT_SCALE)) * 0.5f, DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.settingLabel());

        float sw = DropdownTheme.SWITCH_WIDTH;
        float sh = DropdownTheme.SWITCH_HEIGHT;
        float sx = x + width - DropdownTheme.SETTING_PADDING_X - sw;
        float sy = y + (getHeight() - sh) * 0.5f;

        renderer.roundRect().addRoundRect(sx, sy, sw, sh, DropdownTheme.SWITCH_RADIUS, MD3Theme.switchTrack(t));

        float outlineW = MD3Theme.switchTrackOutlineWidth(t);
        if (outlineW > 0.01f) {
            renderer.outline().addOutline(sx, sy, sw, sh, DropdownTheme.SWITCH_RADIUS, outlineW, MD3Theme.switchTrackOutline(t, 0.0f));
        }

        float knobSize = Mth.lerp(t, DropdownTheme.SWITCH_KNOB_OFF, DropdownTheme.SWITCH_KNOB_ON);
        float inset = Mth.lerp(t, DropdownTheme.SWITCH_KNOB_INSET_OFF, DropdownTheme.SWITCH_KNOB_INSET_ON);
        float knobMinX = sx + inset + knobSize * 0.5f;
        float knobMaxX = sx + sw - inset - knobSize * 0.5f;
        float knobCx = Mth.lerp(t, knobMinX, knobMaxX);
        float knobCy = sy + sh * 0.5f;
        renderer.roundRect().addRoundRect(knobCx - knobSize * 0.5f, knobCy - knobSize * 0.5f, knobSize, knobSize, knobSize * 0.5f, MD3Theme.switchKnob(t));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            float sw = DropdownTheme.SWITCH_WIDTH;
            float sh = DropdownTheme.SWITCH_HEIGHT;
            float sx = x + width - DropdownTheme.SETTING_PADDING_X - sw;
            float sy = y + (getHeight() - sh) * 0.5f;
            if (isHovered(mouseX, mouseY, sx - 2, sy - 2, sw + 4, sh + 4)) {
                setting.setValue(!setting.getValue());
                return true;
            }
        }
        return false;
    }

}
