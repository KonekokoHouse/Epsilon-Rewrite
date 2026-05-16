package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownRenderer;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.settings.impl.EnumSetting;

import java.awt.*;

public class EnumWidget extends SettingWidget<EnumSetting<?>> {

    private static final float CHIP_HEIGHT = 14.0f;
    private static final float CHIP_RADIUS = 7.0f;
    private static final float CHIP_PADDING_X = 6.0f;
    private static final float CHIP_GAP = 4.0f;
    private static final float CHIP_TEXT_SCALE = 0.48f;

    private float computedHeight = DropdownTheme.SETTING_HEIGHT - 1.0f + CHIP_HEIGHT + 1.0f;
    private float[] chipBoundsX;
    private float[] chipBoundsY;
    private float[] chipBoundsW;

    public EnumWidget(EnumSetting<?> setting) {
        super(setting);
        int count = setting.getModes().length;
        chipBoundsX = new float[count];
        chipBoundsY = new float[count];
        chipBoundsW = new float[count];
    }

    @Override
    public float getHeight() {
        return computedHeight;
    }

    @Override
    public void draw(DropdownRenderer renderer, int mouseX, int mouseY) {
        renderer.text().addText(setting.getDisplayName(), x + DropdownTheme.SETTING_PADDING_X, y + 1.0f, DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.settingLabel());

        Enum<?>[] modes = setting.getModes();
        float chipX = x + DropdownTheme.SETTING_PADDING_X;
        float chipY = y + DropdownTheme.SETTING_HEIGHT - 1.0f;
        float maxX = x + width - DropdownTheme.SETTING_PADDING_X;

        for (int i = 0; i < modes.length; i++) {
            String label = setting.getTranslatedValueByIndex(modes[i].ordinal());
            float textW = renderer.text().getWidth(label, CHIP_TEXT_SCALE);
            float chipW = textW + CHIP_PADDING_X * 2.0f;

            if (chipX + chipW > maxX && chipX > x + DropdownTheme.SETTING_PADDING_X) {
                chipX = x + DropdownTheme.SETTING_PADDING_X;
                chipY += CHIP_HEIGHT + CHIP_GAP;
            }

            chipBoundsX[i] = chipX;
            chipBoundsY[i] = chipY;
            chipBoundsW[i] = chipW;

            boolean selected = setting.getValue().ordinal() == modes[i].ordinal();
            Color bg = selected ? DropdownTheme.chipSelected() : DropdownTheme.chipUnselected();
            Color fg = selected ? DropdownTheme.chipSelectedText() : DropdownTheme.chipUnselectedText();

            renderer.roundRect().addRoundRect(chipX, chipY, chipW, CHIP_HEIGHT, CHIP_RADIUS, bg);
            renderer.text().addText(label, chipX + CHIP_PADDING_X, chipY + (CHIP_HEIGHT - renderer.text().getHeight(CHIP_TEXT_SCALE)) * 0.5f, CHIP_TEXT_SCALE, fg);

            chipX += chipW + CHIP_GAP;
        }

        computedHeight = chipY - y + CHIP_HEIGHT + 1.0f;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        Enum<?>[] modes = setting.getModes();
        for (int i = 0; i < modes.length; i++) {
            if (isHovered(mouseX, mouseY, chipBoundsX[i], chipBoundsY[i], chipBoundsW[i], CHIP_HEIGHT)) {
                setting.setMode(modes[i].name());
                return true;
            }
        }
        return false;
    }

}
