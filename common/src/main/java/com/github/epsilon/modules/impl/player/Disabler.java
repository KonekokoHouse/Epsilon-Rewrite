package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.utils.player.ChatUtils;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;

public class Disabler extends Module {

    public static final Disabler INSTANCE = new Disabler();

    private Disabler() {
        super("Disabler", Category.PLAYER);
    }

    private final BoolSetting logging = boolSetting("Logging", false);
    private final BoolSetting badPacketsA = boolSetting("BadPacketsA", true);
    private final BoolSetting aimModulo360 = boolSetting("AimModulo360", true);
    private final BoolSetting duplicateRotPlace = boolSetting("DuplicateRotPlace", true);

    private int lastSlot = -1;
    private float lastYaw = Float.NaN, lastDeltaYaw = Float.NaN;

    private boolean flip;

    @Override
    protected void onEnable() {
        lastSlot = -1;
        lastYaw = Float.NaN;
        lastDeltaYaw = Float.NaN;
        flip = false;
    }

    @EventHandler
    private void onPacket(PacketEvent.Send event) {
        if (badPacketsA.getValue()) {
            if (event.getPacket() instanceof ServerboundSetCarriedItemPacket packet) {
                int slot = packet.getSlot();
                if (slot == lastSlot && slot != -1) {
                    event.setCancelled(true);
                    log("Disabled BadPacketsA");
                }
                lastSlot = packet.getSlot();
            }
        }

        if (aimModulo360.getValue()) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacket packet && packet.hasRotation()) {
                float yaw = packet.yRot;
                if (yaw < 360.0f && yaw > -360.0f) {
                    packet.yRot = yaw + 720.0f;
                    log("Disabled AimModulo360");
                }
            }
        }

        if (duplicateRotPlace.getValue()) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacket packet && packet.hasRotation()) {
                float yaw = packet.yRot;
                if (Float.isFinite(lastYaw)) {
                    float deltaYaw = Math.abs(yaw - lastYaw);
                    if (deltaYaw > 2.0f) {
                        if (Float.isFinite(lastDeltaYaw) && Math.abs(deltaYaw - lastDeltaYaw) < 0.0001f) {
                            packet.yRot = yaw + (flip ? 0.00047f : 0.00031f); // 主播这是我猜的，现在一直 UpTelly 应该是不会出现了
                            yaw = packet.yRot;
                            deltaYaw = Math.abs(yaw - lastYaw);
                            flip = !flip;
                            log("Disabled DuplicateRotPlace");
                        }
                        lastDeltaYaw = deltaYaw;
                    }
                }
                lastYaw = yaw;
            }
        }
    }

    private void log(String message) {
        if (logging.getValue()) ChatUtils.addChatMessage("[Disabler] " + message);
    }

}
