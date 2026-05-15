package com.github.epsilon.modules.impl;

import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.gui.screen.MainMenuScreen;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
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

    private final SettingGroup sgGeneral = settingGroup("General");
    private final SettingGroup sgAntiCheat = settingGroup("Anti Cheat");
    private final SettingGroup sgAppearance = settingGroup("Appearance");
    private final SettingGroup sgNotification = settingGroup("Notification");

    // General
    public final KeybindSetting guiKeybind = keybindSetting("Gui Keybind", GLFW.GLFW_KEY_RIGHT_SHIFT).group(sgGeneral);

    private final ButtonSetting openHudEditor = buttonSetting("Open Hud Editor", () -> mc.setScreen(HudEditorScreen.INSTANCE)).group(sgGeneral);

    public final BoolSetting i18nFallback = boolSetting("I18n Fallback", true).group(sgGeneral);

    public final BoolSetting fontAntiAliasing = boolSetting("Font Anti Aliasing", true).group(sgGeneral);

    public final BoolSetting closeOnOutside = boolSetting("Close Gui On Outside", false).group(sgGeneral);

    // Anti Cheat
    public final DoubleSetting rotateBackSpeed = doubleSetting("Rotate Back Speed", 5.0f, 1.0f, 10.0f, 0.5f).group(sgAntiCheat);

    // Appearance
    public final EnumSetting<ThemeMode> themeMode = enumSetting("Theme Mode", ThemeMode.Dark).group(sgAppearance);

    public final EnumSetting<ThemePreset> themePreset = enumSetting("Theme Preset", ThemePreset.Expressive).group(sgAppearance);

    public final BoolSetting customIcon = boolSetting("Custom Icon", true, _ -> {
        try {
            mc.getWindow().setIcon(mc.getVanillaPackResources(), SharedConstants.getCurrentVersion().stable() ? IconSet.RELEASE : IconSet.SNAPSHOT);
        } catch (IOException ignored) {
        }
    }).group(sgAppearance);

    public final BoolSetting customTitle = boolSetting("Custom Title", true, _ -> mc.updateTitle()).group(sgAppearance);

    public final BoolSetting useMainMenu = boolSetting("Use MainMenu", true).group(sgAppearance);

    public final EnumSetting<MainMenuScreen.Background> mainMenuBackground = enumSetting("MainMenu Background", MainMenuScreen.Background.PLANET, useMainMenu::getValue).group(sgAppearance);

    // Notification
    public final BoolSetting soundNotify = boolSetting("Sound Notify", true).group(sgNotification);

    public final BoolSetting chatNotify = boolSetting("Chat Notify", true).group(sgNotification);

    public final BoolSetting animatedChatPrefix = boolSetting("Animated Chat Prefix", true).group(sgNotification);

    public final ColorSetting chatPrefixColorStart = colorSetting("Chat Prefix Color Start", new Color(255, 175, 210), animatedChatPrefix::getValue).group(sgNotification);

    public final ColorSetting chatPrefixColorEnd = colorSetting("Chat Prefix Color End", new Color(150, 220, 255), animatedChatPrefix::getValue).group(sgNotification);

    public final DoubleSetting chatPrefixGradientSpeed = doubleSetting("Chat Prefix Gradient Speed", 0.5, 0.1, 1, 0.1, animatedChatPrefix::getValue).group(sgNotification);

}
