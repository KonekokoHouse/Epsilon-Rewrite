package com.github.epsilon.gui.dropdown;

import com.github.epsilon.gui.panel.MD3Theme;

import java.awt.*;

public final class DropdownTheme {

    public static final float PANEL_WIDTH = 120.0f;
    public static final float PANEL_HEADER_HEIGHT = 20.0f;
    public static final float PANEL_RADIUS = 8.0f;
    public static final float PANEL_GAP = 12.0f;
    public static final float PANEL_MARGIN_X = 20.0f;
    public static final float PANEL_MARGIN_Y = 20.0f;
    public static final float PANEL_SHADOW_BLUR = 14.0f;

    public static final float MODULE_HEIGHT = 16.0f;
    public static final float MODULE_RADIUS = 4.0f;
    public static final float MODULE_PADDING_X = 6.0f;
    public static final float MODULE_TEXT_SCALE = 0.52f;

    public static final float SETTING_PADDING_X = 6.0f;
    public static final float SETTING_HEIGHT = 14.0f;
    public static final float SETTING_TEXT_SCALE = 0.46f;
    public static final float SETTING_GAP = 2.0f;
    public static final float SETTING_INDENT = 4.0f;

    public static final float SWITCH_WIDTH = 20.0f;
    public static final float SWITCH_HEIGHT = 10.0f;
    public static final float SWITCH_RADIUS = 5.0f;
    public static final float SWITCH_KNOB_OFF = 4.0f;
    public static final float SWITCH_KNOB_ON = 5.0f;
    public static final float SWITCH_KNOB_INSET_OFF = 3.0f;
    public static final float SWITCH_KNOB_INSET_ON = 2.5f;

    public static final float SLIDER_HEIGHT = 4.0f;
    public static final float SLIDER_RADIUS = 2.0f;
    public static final float SLIDER_KNOB_RADIUS = 3.5f;

    public static final float CHIP_HEIGHT = 12.0f;
    public static final float CHIP_RADIUS = 6.0f;
    public static final float CHIP_PADDING_X = 5.0f;
    public static final float CHIP_GAP = 3.0f;
    public static final float CHIP_TEXT_SCALE = 0.40f;

    public static final float COLOR_PREVIEW_SIZE = 10.0f;
    public static final float COLOR_PICKER_HEIGHT = 50.0f;
    public static final float COLOR_HUE_HEIGHT = 6.0f;
    public static final float COLOR_ALPHA_HEIGHT = 6.0f;
    public static final float COLOR_RADIUS = 4.0f;

    public static final float KEYBIND_WIDTH = 40.0f;
    public static final float KEYBIND_HEIGHT = 12.0f;
    public static final float KEYBIND_RADIUS = 4.0f;

    public static final float INPUT_HEIGHT = 14.0f;
    public static final float INPUT_RADIUS = 4.0f;

    public static final float BUTTON_HEIGHT = 14.0f;
    public static final float BUTTON_RADIUS = 4.0f;

    public static final float SCROLL_SPEED = 12.0f;

    public static final long ANIM_OPEN = 200L;
    public static final long ANIM_TOGGLE = 180L;
    public static final long ANIM_HOVER = 120L;
    public static final long ANIM_EXPAND = 220L;

    public static final float HEADER_TEXT_SCALE = 0.60f;
    public static final float HEADER_ICON_SCALE = 0.70f;

    private DropdownTheme() {
    }

    public static Color panelBackground() {
        return MD3Theme.SURFACE_CONTAINER;
    }

    public static Color panelHeader() {
        return MD3Theme.SURFACE_CONTAINER_HIGH;
    }

    public static Color panelShadow() {
        return MD3Theme.withAlpha(MD3Theme.SHADOW, 48);
    }

    public static Color moduleEnabled(float hoverProgress) {
        return MD3Theme.lerp(MD3Theme.PRIMARY_CONTAINER, MD3Theme.lerp(MD3Theme.PRIMARY_CONTAINER, MD3Theme.PRIMARY, 0.15f), hoverProgress);
    }

    public static Color moduleDisabled(float hoverProgress) {
        return MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER, MD3Theme.SURFACE_CONTAINER_HIGH, hoverProgress);
    }

    public static Color moduleTextEnabled() {
        return MD3Theme.ON_PRIMARY_CONTAINER;
    }

    public static Color moduleTextDisabled(float hoverProgress) {
        return MD3Theme.lerp(MD3Theme.TEXT_SECONDARY, MD3Theme.TEXT_PRIMARY, hoverProgress);
    }

    public static Color settingLabel() {
        return MD3Theme.TEXT_PRIMARY;
    }

    public static Color settingLabelMuted() {
        return MD3Theme.TEXT_MUTED;
    }

    public static Color settingSurface() {
        return MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 160);
    }

    public static Color sliderTrack() {
        return MD3Theme.SURFACE_CONTAINER_HIGHEST;
    }

    public static Color sliderActive() {
        return MD3Theme.PRIMARY;
    }

    public static Color sliderKnob() {
        return MD3Theme.PRIMARY;
    }

    public static Color chipSelected() {
        return MD3Theme.SECONDARY_CONTAINER;
    }

    public static Color chipSelectedText() {
        return MD3Theme.ON_SECONDARY_CONTAINER;
    }

    public static Color chipUnselected() {
        return MD3Theme.SURFACE_CONTAINER_HIGH;
    }

    public static Color chipUnselectedText() {
        return MD3Theme.TEXT_SECONDARY;
    }

    public static Color keybindSurface(boolean listening) {
        return listening ? MD3Theme.PRIMARY_CONTAINER : MD3Theme.SURFACE_CONTAINER_HIGH;
    }

    public static Color keybindText(boolean listening) {
        return listening ? MD3Theme.ON_PRIMARY_CONTAINER : MD3Theme.TEXT_SECONDARY;
    }

    public static Color inputSurface(boolean focused) {
        return focused ? MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER_HIGH, MD3Theme.PRIMARY_CONTAINER, 0.3f) : MD3Theme.SURFACE_CONTAINER_HIGH;
    }

    public static Color inputText() {
        return MD3Theme.TEXT_PRIMARY;
    }

    public static Color inputIndicator(boolean focused) {
        return focused ? MD3Theme.PRIMARY : MD3Theme.withAlpha(MD3Theme.OUTLINE, 96);
    }

    public static Color buttonSurface(float hoverProgress) {
        return MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.SECONDARY, 0.12f), hoverProgress);
    }

    public static Color buttonText() {
        return MD3Theme.ON_SECONDARY_CONTAINER;
    }

    public static Color expandArrow(float toggleProgress) {
        return MD3Theme.lerp(MD3Theme.TEXT_MUTED, MD3Theme.ON_PRIMARY_CONTAINER, toggleProgress);
    }

    public static Color scrollbar() {
        return MD3Theme.withAlpha(MD3Theme.OUTLINE, 64);
    }

    public static Color scrim() {
        return new Color(0, 0, 0, 50);
    }

}
