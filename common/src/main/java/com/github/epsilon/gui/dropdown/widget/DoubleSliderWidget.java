package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownRenderer;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.util.Mth;

import java.text.DecimalFormat;

public class DoubleSliderWidget extends SettingWidget<DoubleSetting> {

    private static final DecimalFormat FORMAT = new DecimalFormat("#0.00");

    private final Animation slideAnim = new Animation(Easing.EASE_OUT_CUBIC, 100L);
    private boolean dragging;

    public DoubleSliderWidget(DoubleSetting setting) {
        super(setting);
        float initial = (float) ((setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin()));
        slideAnim.setStartValue(initial);
    }

    @Override
    public float getHeight() {
        return DropdownTheme.SETTING_HEIGHT + 8.0f;
    }

    @Override
    public void draw(DropdownRenderer renderer, int mouseX, int mouseY) {
        float ratio = (float) ((setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin()));
        slideAnim.run(ratio);
        float animatedRatio = slideAnim.getValue();

        renderer.text().addText(setting.getDisplayName(), x + DropdownTheme.SETTING_PADDING_X, y + 1.0f, DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.settingLabel());

        String valueStr = setting.isPercentageMode() ? FORMAT.format(setting.getValue() * 100) + "%" : FORMAT.format(setting.getValue());
        float valueWidth = renderer.text().getWidth(valueStr, DropdownTheme.SETTING_TEXT_SCALE);
        renderer.text().addText(valueStr, x + width - DropdownTheme.SETTING_PADDING_X - valueWidth, y + 1.0f, DropdownTheme.SETTING_TEXT_SCALE, MD3Theme.PRIMARY);

        float trackX = x + DropdownTheme.SETTING_PADDING_X;
        float trackY = y + DropdownTheme.SETTING_HEIGHT;
        float trackW = width - DropdownTheme.SETTING_PADDING_X * 2.0f;
        float trackH = DropdownTheme.SLIDER_HEIGHT;

        renderer.roundRect().addRoundRect(trackX, trackY, trackW, trackH, DropdownTheme.SLIDER_RADIUS, DropdownTheme.sliderTrack());

        float activeW = trackW * Mth.clamp(animatedRatio, 0.0f, 1.0f);
        if (activeW > 0.5f) {
            renderer.roundRect().addRoundRect(trackX, trackY, activeW, trackH, DropdownTheme.SLIDER_RADIUS, DropdownTheme.sliderActive());
        }

        float knobX = trackX + trackW * Mth.clamp(animatedRatio, 0.0f, 1.0f);
        float knobY = trackY + trackH * 0.5f;
        float kr = DropdownTheme.SLIDER_KNOB_RADIUS;
        renderer.roundRect().addRoundRect(knobX - kr, knobY - kr, kr * 2.0f, kr * 2.0f, kr, DropdownTheme.sliderKnob());

        if (dragging) {
            float rawRatio = Mth.clamp((float) (mouseX - trackX) / trackW, 0.0f, 1.0f);
            double range = setting.getMax() - setting.getMin();
            double step = setting.getStep();
            double value = setting.getMin() + Math.round(rawRatio * range / step) * step;
            setting.setValue(Mth.clamp(value, setting.getMin(), setting.getMax()));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            float trackX = x + DropdownTheme.SETTING_PADDING_X;
            float trackY = y + DropdownTheme.SETTING_HEIGHT - 3.0f;
            float trackW = width - DropdownTheme.SETTING_PADDING_X * 2.0f;
            if (isHovered(mouseX, mouseY, trackX, trackY, trackW, DropdownTheme.SLIDER_HEIGHT + 6.0f)) {
                dragging = true;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

}
