package com.github.epsilon.neoforge;

import com.github.epsilon.CommonListeners;
import com.github.epsilon.Epsilon;
import com.github.epsilon.addon.AddonBootstrap;
import com.github.epsilon.neoforge.addon.EpsilonAddonSetupEvent;
import com.github.epsilon.neoforge.addon.NeoForgeSelfAddonRegistrar;
import com.github.epsilon.neoforge.compat.NeoForgePlatformCompat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Epsilon.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Epsilon.MODID, value = Dist.CLIENT)
public class EpsilonNeo {

    public EpsilonNeo() {
        // Some NeoForge client events fire before FMLClientSetupEvent.
        if (Epsilon.platform == null) {
            Epsilon.platform = new NeoForgePlatformCompat();
        }
        NeoForgeSelfAddonRegistrar.register();
    }

    @SubscribeEvent
    private static void onClientSetup(FMLClientSetupEvent event) {
        Epsilon.VERSION = event.getContainer().getModInfo().getVersion().toString();
        if (Epsilon.platform == null) {
            Epsilon.platform = new NeoForgePlatformCompat();
        }

        EpsilonAddonSetupEvent addonEvent = new EpsilonAddonSetupEvent();
        NeoForge.EVENT_BUS.post(addonEvent);
        AddonBootstrap.registerAddons(addonEvent.getAddons());

        // Common initialization
        Epsilon.init();
        CommonListeners.register();
    }

}
