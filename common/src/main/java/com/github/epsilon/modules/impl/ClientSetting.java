package com.github.epsilon.modules.impl;

import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.gui.screen.MainMenuScreen;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.*;
import com.mojang.blaze3d.platform.IconSet;
import net.minecraft.SharedConstants;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.io.IOException;

public class ClientSetting extends Module {

    public static final ClientSetting INSTANCE = new ClientSetting();

    private ClientSetting() {
        super("Client Setting", null);
    }

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

    public final KeybindSetting guiKeybind = keybindSetting("Gui Keybind", GLFW.GLFW_KEY_RIGHT_SHIFT);

    private final ButtonSetting openHudEditor = buttonSetting("Open Hud Editor", () -> mc.setScreen(HudEditorScreen.INSTANCE));

    public final BoolSetting i18nFallback = boolSetting("I18n Fallback", true);

    public final BoolSetting fontAntiAliasing = boolSetting("Font Anti Aliasing", true);

    public final BoolSetting closeOnOutside = boolSetting("Close Gui On Outside", false);

    public final DoubleSetting rotateBackSpeed = doubleSetting("Rotate Back Speed", 5.0f, 1.0f, 10.0f, 0.5f);

    public final BoolSetting rotateJitter = boolSetting("Rotate Jitter", false);

    public final DoubleSetting rotateJitterSize = doubleSetting("Rotate Jitter Size", 0.5f, 0.0f, 10.0f, 0.1f, rotateJitter::getValue);

    public final EnumSetting<ThemeMode> themeMode = enumSetting("Theme Mode", ThemeMode.Dark);

    public final EnumSetting<ThemePreset> themePreset = enumSetting("Theme Preset", ThemePreset.Expressive);

    public final BoolSetting customIcon = boolSetting("Custom Icon", true, _ -> {
        try {
            mc.getWindow().setIcon(mc.getVanillaPackResources(), SharedConstants.getCurrentVersion().stable() ? IconSet.RELEASE : IconSet.SNAPSHOT);
        } catch (IOException ignored) {
        }
    });

    public final BoolSetting customTitle = boolSetting("Custom Title", true, _ -> mc.updateTitle());

    public final BoolSetting useMainMenu = boolSetting("Use MainMenu", true);

    public final EnumSetting<MainMenuScreen.Background> mainMenuBackground = enumSetting("MainMenu Background", MainMenuScreen.Background.PLANET, useMainMenu::getValue);

    public final BoolSetting soundNotify = boolSetting("Sound Notify", true);

    public final BoolSetting chatNotify = boolSetting("Chat Notify", true);

    public final BoolSetting animatedChatPrefix = boolSetting("Animated Chat Prefix", true);

    public final ColorSetting chatPrefixColorStart = colorSetting("Chat Prefix Color Start", new Color(255, 175, 210), animatedChatPrefix::getValue);

    public final ColorSetting chatPrefixColorEnd = colorSetting("Chat Prefix Color End", new Color(150, 220, 255), animatedChatPrefix::getValue);

    public final DoubleSetting chatPrefixGradientSpeed = doubleSetting("Chat Prefix Gradient Speed", 0.5, 0.1, 1, 0.1, animatedChatPrefix::getValue);

}
