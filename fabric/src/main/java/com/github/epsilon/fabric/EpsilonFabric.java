package com.github.epsilon.fabric;

import com.github.epsilon.CommonListeners;
import com.github.epsilon.Epsilon;
import com.github.epsilon.addon.AddonBootstrap;
import com.github.epsilon.addon.EpsilonAddonSetupEvent;
import com.github.epsilon.assets.i18n.LanguageReloadListener;
import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.fabric.addon.FabricEpsilonAddonEntrypoint;
import com.github.epsilon.fabric.compat.FabricPlatformCompat;
import com.github.epsilon.graphics.LuminRenderPipelines;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class EpsilonFabric implements ClientModInitializer {

    public static final String ADDON_ENTRYPOINT_KEY = "open_epsilon:addon";

    @Override
    public void onInitializeClient() {
        Epsilon.VERSION = FabricLoader.getInstance().getModContainer(Epsilon.MODID).get()
                .getMetadata().getVersion().getFriendlyString();
        Epsilon.platform = new FabricPlatformCompat();

        // Load addon providers registered through Fabric custom entrypoints.
        EpsilonAddonSetupEvent addonEvent = new EpsilonAddonSetupEvent();
        for (EntrypointContainer<FabricEpsilonAddonEntrypoint> container : FabricLoader.getInstance().getEntrypointContainers(ADDON_ENTRYPOINT_KEY, FabricEpsilonAddonEntrypoint.class)) {
            String providerId = container.getProvider().getMetadata().getId();
            try {
                FabricEpsilonAddonEntrypoint entrypoint = container.getEntrypoint();
                entrypoint.registerAddon(addonEvent);
            } catch (Throwable t) {
                Epsilon.LOGGER.error("Failed to register addon entrypoint from mod: {}", providerId, t);
            }
        }
        AddonBootstrap.registerAddons(addonEvent);

        Epsilon.init();
        CommonListeners.register();

        // Register render pipelines
        LuminRenderPipelines.registerAll(pipeline -> {
            // Pipelines are auto-registered when referenced in vanilla 26.1.2+
        });

        // Register resource reload listener (equivalent to NeoForge's AddClientReloadListenersEvent)
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new FabricReloadListenerWrapper(
                        ResourceLocationUtils.getIdentifier("objects/reload_listener"),
                        new LanguageReloadListener()
                )
        );

        Epsilon.LOGGER.info("Epsilon Fabric loaded successfully!");
    }

    private record FabricReloadListenerWrapper(
            Identifier id,
            PreparableReloadListener delegate
    ) implements IdentifiableResourceReloadListener {

        @Override
        public Identifier getFabricId() {
            return id;
        }

        @Override
        public CompletableFuture<Void> reload(
                PreparableReloadListener.SharedState sharedState,
                Executor backgroundExecutor,
                PreparableReloadListener.PreparationBarrier barrier,
                Executor gameExecutor
        ) {
            return delegate.reload(sharedState, backgroundExecutor, barrier, gameExecutor);
        }
    }

}
