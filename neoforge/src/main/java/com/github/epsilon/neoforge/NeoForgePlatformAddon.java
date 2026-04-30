package com.github.epsilon.neoforge;

import com.github.epsilon.Epsilon;
import com.github.epsilon.addon.EpsilonAddon;
import com.github.epsilon.neoforge.modules.NeoModuleTest;
import com.github.epsilon.settings.impl.BoolSetting;

import java.util.List;

/**
 * Built-in NeoForge addon for NeoForge-only features.
 */
public class NeoForgePlatformAddon extends EpsilonAddon {

    public static final NeoForgePlatformAddon INSTANCE = new NeoForgePlatformAddon();

    private NeoForgePlatformAddon() {
        super("epsilon_neoforge");
    }

    @Override
    public void onSetup() {
        registerModule(NeoModuleTest.INSTANCE);
        Epsilon.LOGGER.info("NeoForge platform addon initialized.");
    }

    @Override
    public String getDisplayName() {
        return "NeoForge Platform";
    }

    @Override
    public String getDescription() {
        return "Built-in addon for NeoForge-specific integrations.";
    }

    @Override
    public String getVersion() {
        return Epsilon.VERSION;
    }

    @Override
    public List<String> getAuthors() {
        return List.of("Open Epsilon");
    }

    public final BoolSetting testSetting0 = boolSetting("Test Setting 0", true);
    public final BoolSetting testSetting1 = boolSetting("Test Setting 1", true);
    public final BoolSetting testSetting2 = boolSetting("Test Setting 2", true);
    public final BoolSetting testSetting3 = boolSetting("Test Setting 3", true);
    public final BoolSetting testSetting4 = boolSetting("Test Setting 4", true);
    public final BoolSetting testSetting5 = boolSetting("Test Setting 01", true);
    public final BoolSetting testSetting6 = boolSetting("Test Setting 12", true);
    public final BoolSetting testSetting7 = boolSetting("Test Setting 23", true);
    public final BoolSetting testSetting8 = boolSetting("Test Setting 34", true);
    public final BoolSetting testSetting9 = boolSetting("Test Setting 45", true);

}

