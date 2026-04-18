package com.github.epsilon.gui.panel.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public class IMEFocusHelper {

    public static float activeCursorX = 0.0f;
    public static float activeCursorY = 0.0f;

    private IMEFocusHelper() {
    }

    /**
     * Enables OS-level text / IME input.
     * Call this whenever a custom text field gains focus.
     */
    public static void activate() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen != null) {
            mc.onTextInputFocusChange(screen, true);
        }
    }

    /**
     * Disables OS-level text / IME input and cancels any active IME composition.
     * Call this whenever a custom text field loses focus.
     *
     * <p>Must pass the active {@link Screen} as the element so that
     * {@code KeyboardHandler.submitPreeditEvent(screen, null)} can correctly call
     * {@code screen.preeditUpdated(null)}, clearing the preedit overlay and
     * releasing the IME composition lock.</p>
     */
    public static void deactivate() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen != null) {
            mc.onTextInputFocusChange(screen, false);
        }
    }

    /**
     * Updates the cursor position used for preedit overlay placement.
     *
     * @param x cursor left edge in GUI (scaled) coordinates
     * @param y cursor top edge in GUI (scaled) coordinates
     */
    public static void updateCursorPos(float x, float y) {
        activeCursorX = x;
        activeCursorY = y;
    }

}
