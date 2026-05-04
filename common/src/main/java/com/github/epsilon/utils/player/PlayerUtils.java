package com.github.epsilon.utils.player;

import com.github.epsilon.utils.world.BlockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;

public class PlayerUtils {

    private static final Minecraft mc = Minecraft.getInstance();

    public static boolean isEating() {
        return (mc.player.getMainHandItem().getComponents().has(DataComponents.FOOD) || mc.player.getOffhandItem().getComponents().has(DataComponents.FOOD)) && mc.player.isUsingItem();
    }

    public static boolean isInBlock() {
        return (BlockUtils.isSolidBlock(mc.player.blockPosition())
                || BlockUtils.isSolidBlock(BlockUtils.toBlockPos(mc.player.position().add(0.3, 0.0, 0.3)))
                || BlockUtils.isSolidBlock(BlockUtils.toBlockPos(mc.player.position().add(-0.3, 0.0, 0.3)))
                || BlockUtils.isSolidBlock(BlockUtils.toBlockPos(mc.player.position().add(-0.3, 0.0, -0.3)))
                || BlockUtils.isSolidBlock(BlockUtils.toBlockPos(mc.player.position().add(0.3, 0.0, -0.3))));
    }

}
