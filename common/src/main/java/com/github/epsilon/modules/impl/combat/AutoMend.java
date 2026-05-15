package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.TickEvent;
import com.github.epsilon.managers.HotbarManager;
import com.github.epsilon.managers.RotationManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.rotation.Priority;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import org.joml.Vector2f;

public class AutoMend extends Module {

    public static final AutoMend INSTANCE = new AutoMend();

    private AutoMend() {
        super("Auto Mend", Category.COMBAT);
    }

    private enum SwitchMode {
        Normal,
        Silent
    }

    private final EnumSetting<SwitchMode> switchMode = enumSetting("Switch Mode", SwitchMode.Normal);
    private final BoolSetting swingHand = boolSetting("Swing Hand", false);

    @EventHandler
    private void onClientTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        FindItemResult result = HotbarManager.INSTANCE.findInHotbar(Items.EXPERIENCE_BOTTLE);
        if (!result.found()) return;

        RotationManager.INSTANCE.applyRotation(new Vector2f(mc.player.getYRot(), 90), 10, Priority.High, _ -> {
            HotbarManager.INSTANCE.swap(result.slot(), true);

            InteractionHand hand = result.getHand();
            mc.gameMode.useItem(mc.player, hand);
            if (swingHand.getValue()) {
                mc.player.swing(hand);
            } else {
                mc.getConnection().send(new ServerboundSwingPacket(hand));
            }

        });
    }

}
