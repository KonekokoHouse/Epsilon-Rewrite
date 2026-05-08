package com.github.epsilon.utils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.ResourceLoadStateTracker;

public class ClientUtils {

    private static final Minecraft mc = Minecraft.getInstance();

    public static boolean isLoading() {
        ResourceLoadStateTracker.ReloadState state = mc.reloadStateTracker.reloadState;
        return state == null || !state.finished;
    }

}
