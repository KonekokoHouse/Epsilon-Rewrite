package com.github.epsilon.managers;

import com.github.epsilon.Epsilon;
import com.github.epsilon.addon.EpsilonAddon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AddonManager {

    public static final AddonManager INSTANCE = new AddonManager();

    private final List<EpsilonAddon> addons = new ArrayList<>();
    private final Set<String> addonIds = new HashSet<>();

    private boolean setupComplete;

    private AddonManager() {
    }

    public synchronized void registerAddon(EpsilonAddon addon) {
        if (addon == null) {
            return;
        }

        String addonId = addon.getAddonId();
        if (addonId == null || addonId.isBlank()) {
            Epsilon.LOGGER.warn("Ignoring Epsilon addon with blank addonId: {}", addon.getClass().getName());
            return;
        }

        if (!addonIds.add(addonId)) {
            Epsilon.LOGGER.warn("Duplicate Epsilon addon id ignored: {}", addonId);
            return;
        }

        addons.add(addon);
    }

    public synchronized void registerAddons(Iterable<EpsilonAddon> addonIterable) {
        if (addonIterable == null) {
            return;
        }
        for (EpsilonAddon addon : addonIterable) {
            registerAddon(addon);
        }
    }

    public synchronized void setupAddons() {
        if (setupComplete) {
            return;
        }
        setupComplete = true;

        for (EpsilonAddon addon : addons) {
            try {
                addon.initAddonI18n();
                addon.onSetup();
                Epsilon.LOGGER.info("Loaded Epsilon addon: {}", addon.getAddonId());
            } catch (Throwable throwable) {
                Epsilon.LOGGER.error("Failed to setup Epsilon addon: {}", addon.getAddonId(), throwable);
            }
        }
    }

    public synchronized List<EpsilonAddon> getAddons() {
        return List.copyOf(addons);
    }
}

