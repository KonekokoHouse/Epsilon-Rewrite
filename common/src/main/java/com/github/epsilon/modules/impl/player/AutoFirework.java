package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.TickEvent;
import com.github.epsilon.managers.HotbarManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.settings.impl.KeybindSetting;
import com.github.epsilon.utils.client.KeybindUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class AutoFirework extends Module {

    public static final AutoFirework INSTANCE = new AutoFirework();

    private AutoFirework() {
        super("Auto Firework", Category.PLAYER);
    }

    private final KeybindSetting activateKey = keybindSetting("Activate Key", -1);
    private final IntSetting delay = intSetting("Delay", 0, 0, 20, 1);
    private final BoolSetting switchBack = boolSetting("Switch Back", true);
    private final IntSetting switchDelay = intSetting("Switch Delay", 0, 0, 20, 1);

    private boolean hasUsedFirework;
    private int useDelayCounter;
    private int previousSelectedSlot;
    private int switchDelayCounter;
    private int cooldownCounter;
    private ItemStack previousItem;
    private boolean isKeyDown;

    @Override
    protected void onEnable() {
        resetState();
        isKeyDown = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        boolean pressed = KeybindUtils.isPressed(activateKey.getValue());
        if (!pressed) {
            isKeyDown = false;
        }

        if (this.cooldownCounter > 0) {
            --this.cooldownCounter;
            return;
        }

        if (pressed) {
            if (isKeyDown && this.previousSelectedSlot == -1) return;
            isKeyDown = true;

            if (this.previousSelectedSlot == -1) {
                this.previousSelectedSlot = mc.player.getInventory().getSelectedSlot();
                this.previousItem = mc.player.getMainHandItem();
            }

            int fireworkSlot = HotbarManager.INSTANCE.findInHotbar(Items.FIREWORK_ROCKET).slot();
            if (fireworkSlot == -1) {
                this.resetState();
                return;
            }

            HotbarManager.INSTANCE.swap(fireworkSlot, false);

            if (this.useDelayCounter < this.delay.getValue()) {
                ++this.useDelayCounter;
                return;
            }

            if (!this.hasUsedFirework) {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                this.hasUsedFirework = true;
                if (!this.switchBack.getValue()) {
                    this.resetState();
                }
            }

            if (this.hasUsedFirework && this.switchBack.getValue()) {
                handleSwitchBack();
            }
        }
    }

    private void handleSwitchBack() {
        if (this.switchDelayCounter < this.switchDelay.getValue()) {
            ++this.switchDelayCounter;
            return;
        }

        if (this.previousSelectedSlot != -1) {
            HotbarManager.INSTANCE.swap(this.previousSelectedSlot, false);
        }

        this.resetState();
    }

    private void resetState() {
        this.previousSelectedSlot = -1;
        this.previousItem = null;
        this.useDelayCounter = 0;
        this.switchDelayCounter = 0;
        this.cooldownCounter = 4;
        this.hasUsedFirework = false;
    }

}
