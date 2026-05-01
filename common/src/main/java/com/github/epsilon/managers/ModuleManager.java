package com.github.epsilon.managers;

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.events.bus.EpsilonEventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.render.RenderFrameEvent;
import com.github.epsilon.gui.compose.ComposePanelScreen;
import com.github.epsilon.modules.HudModule;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.modules.impl.combat.*;
import com.github.epsilon.modules.impl.player.*;
import com.github.epsilon.modules.impl.render.*;
import com.github.epsilon.modules.impl.render.notification.Notifications;
import com.github.epsilon.modules.impl.world.AutoAccount;
import com.github.epsilon.modules.impl.world.AutoFarm;
import com.github.epsilon.modules.impl.world.FakePlayer;
import com.github.epsilon.modules.impl.world.Stealer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {

    public static final ModuleManager INSTANCE = new ModuleManager();

    private List<Module> modules;

    private ModuleManager() {
        EpsilonEventBus.INSTANCE.subscribe(this);
    }

    public void initModules() {
        modules = new ArrayList<>(List.of(

                ClientSetting.INSTANCE,

                // Combat
                AimAssist.INSTANCE,
                AntiBot.INSTANCE,
                AutoClicker.INSTANCE,
                AutoMend.INSTANCE,
                AutoTotem.INSTANCE,
                CrystalAura.INSTANCE,
                KillAura.INSTANCE,
                MaceAura.INSTANCE,
                PacketMine.INSTANCE,
                SpearKill.INSTANCE,
                Surround.INSTANCE,
                TriggerBot.INSTANCE,
                Velocity.INSTANCE,

                // Player
                AutoKouZi.INSTANCE,
                AutoSprint.INSTANCE,
                AutoTool.INSTANCE,
                BreakCooldown.INSTANCE,
                Disabler.INSTANCE,
                ElytraFly.INSTANCE,
                InvManager.INSTANCE,
                IQBoost.INSTANCE,
                JumpCooldown.INSTANCE,
                MovementFix.INSTANCE,
                NoFall.INSTANCE,
                NoRotate.INSTANCE,
                NoSlow.INSTANCE,
                Phase.INSTANCE,
                PacketEat.INSTANCE,
                SafeWalk.INSTANCE,
                Scaffold.INSTANCE,
                Stuck.INSTANCE,
                UseCooldown.INSTANCE,
                VClip.INSTANCE,
                Blink.INSTANCE,

                // Render
                CameraClip.INSTANCE,
                ESP.INSTANCE,
                Fullbright.INSTANCE,
                NameTags.INSTANCE,
                NoRender.INSTANCE,
                PopChams.INSTANCE,

                // World
                AutoFarm.INSTANCE,
                Stealer.INSTANCE,
                FakePlayer.INSTANCE,
                AutoAccount.INSTANCE,

                // Hud
                Notifications.INSTANCE,
                ModuleList.INSTANCE

        ));

        // Initialize i18n for all epsilon modules
        for (Module module : modules) {
            module.setAddonId("epsilon");
            module.initI18n(EpsilonTranslateComponent.create("modules", module.getName().toLowerCase()));
        }
    }

    /**
     * Registers a module from an addon and initializes its i18n.
     *
     * @param module          the module to register
     * @param moduleComponent the TranslateComponent for this module (e.g. "myaddon.modules.fly")
     */
    public void registerAddonModule(String addonId, Module module, TranslateComponent moduleComponent) {
        module.setAddonId(addonId);
        module.initI18n(moduleComponent);
        modules.add(module);
    }

    public List<Module> getModules() {
        return modules;
    }

    public void onKeyEvent(int keyCode, int action) {
        if (keyCode == ClientSetting.INSTANCE.guiKeybind.getValue()
                && action == InputConstants.PRESS
                && Minecraft.getInstance().screen == null
        ) {
            Minecraft.getInstance().setScreen(ComposePanelScreen.INSTANCE);
        }

        for (final var module : modules) {
            if (module.getKeyBind() == keyCode) {
                if (module.getBindMode() == Module.BindMode.Hold) {
                    if (action == InputConstants.PRESS || action == InputConstants.REPEAT) {
                        module.setEnabled(true);
                    } else if (action == InputConstants.RELEASE) {
                        module.setEnabled(false);
                    }
                } else {
                    if (action == InputConstants.PRESS) {
                        module.toggle();
                    }
                }
            }
        }
    }

    @EventHandler
    public void onRenderGui(RenderFrameEvent.Pre event) {

        if (Minecraft.getInstance().level == null) return;

        modules.forEach(module -> {
            if (module.isEnabled() && module instanceof HudModule hudModule) {
                RenderManager.INSTANCE.applyRenderHud(delta -> {
                    hudModule.updateLayout(delta);
                    hudModule.render(delta);
                });
            }
        });

    }

}
