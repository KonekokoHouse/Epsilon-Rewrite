package com.github.epsilon.modules.impl;

import com.github.epsilon.gui.compose.ComposeDemoScreen;
import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ButtonSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.KeybindSetting;
import org.lwjgl.glfw.GLFW;

public class ClientSetting extends Module {

    public enum ThemePreset {
        TonalSpot,
        Neutral,
        Vibrant,
        Expressive,
        Fidelity,
        Content,
        Rainbow,
        FruitSalad,
        Monochrome
    }

    public enum ThemeMode {
        Dark,
        Light
    }

    private ClientSetting() {
        super("Client Setting", null);
    }

    public static final ClientSetting INSTANCE = new ClientSetting();

    public final KeybindSetting guiKeybind = keybindSetting("Gui Keybind", GLFW.GLFW_KEY_RIGHT_SHIFT);

    private final ButtonSetting openHudEditor = buttonSetting("Open Hud Editor", () -> {
        mc.setScreen(HudEditorScreen.INSTANCE);
    });

    private final ButtonSetting openComposeDemo = buttonSetting("Open Compose Demo", () -> {
        mc.setScreen(new ComposeDemoScreen());
    });

    public final BoolSetting i18nFallback = boolSetting("I18n Fallback", true);

    public final BoolSetting fontAntiAliasing = boolSetting("Font Anti Aliasing", true);

    public final BoolSetting closeOnOutside = boolSetting("Close Gui On Outside", false);

    public final EnumSetting<ThemeMode> themeMode = enumSetting("Theme Mode", ThemeMode.Dark);
    public final EnumSetting<ThemePreset> themePreset = enumSetting("Theme Preset", ThemePreset.TonalSpot);

    public ThemePreset getThemePreset() {
        return themePreset.getValue();
    }

    public ThemeMode getThemeMode() {
        return themeMode.getValue();
    }

}
