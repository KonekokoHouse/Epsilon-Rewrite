package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.TickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.MoveUtils;
import com.github.epsilon.utils.player.PlayerUtils;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Velocity extends Module {

    public static final Velocity INSTANCE = new Velocity();

    private Velocity() {
        super("Velocity", Category.COMBAT);
    }

    private enum Mode {
        Cancel,
        Legit,
        NoXZ
    }

    private enum VelocityStage {
        NONE,
        DELAY,
        ATTACK,
        CLEAR,
        LAG
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Cancel);
    private final BoolSetting serverMotion = boolSetting("Server Motion", true, () -> mode.is(Mode.Cancel));
    private final BoolSetting explosion = boolSetting("Explosion", true, () -> mode.is(Mode.Cancel));
    private final BoolSetting explosionOnlyBlock = boolSetting("Explosion Only Block", false, () -> mode.is(Mode.Cancel) && explosion.getValue());
    public final BoolSetting waterPush = boolSetting("No Water Push", true, () -> mode.is(Mode.Cancel));
    public final BoolSetting entityPush = boolSetting("No Entity Push", true, () -> mode.is(Mode.Cancel));
    public final BoolSetting blockPush = boolSetting("No Block Push", true, () -> mode.is(Mode.Cancel));
    private final IntSetting attacks = intSetting("Attacks", 4, 1, 5, 1, () -> mode.is(Mode.NoXZ));
    private final DoubleSetting delayTime = doubleSetting("Max Delay Time", 2500.0, 50.0, 10000.0, 50.0, () -> mode.is(Mode.NoXZ));

    private Player target;

    private boolean lag;
    private boolean jump;

    private Vec3 velocity;
    private long velocityTime;

    private VelocityStage stage;

    private final Map<Entity, Vec3> targets = new HashMap<>();
    private final Queue<Packet<? super ClientPacketListener>> packets = new ConcurrentLinkedQueue<>();

    @Override
    protected void onEnable() {
        jump = false;
        lag = false;
        targets.clear();
        target = null;
        stage = VelocityStage.NONE;
    }

    @Override
    protected void onDisable() {
        jump = false;
        lag = false;
        targets.clear();
        target = null;
        stage = VelocityStage.NONE;
        clear(true);
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck()) return;

        switch (mode.getValue()) {
            case Cancel -> {
                if (nullCheck()) return;

                if (serverMotion.getValue() && event.getPacket() instanceof ClientboundSetEntityMotionPacket packet && packet.id() == mc.player.getId()) {
                    event.setCancelled(true);
                    return;
                }

                if (explosion.getValue() && event.getPacket() instanceof ClientboundExplodePacket packet) {
                    if (explosionOnlyBlock.getValue()) {
                        if (!PlayerUtils.isInBlock()) return;
                    }

                    event.setPacket(new ClientboundExplodePacket(
                            packet.center(),
                            packet.radius(),
                            packet.blockCount(),
                            Optional.empty(),
                            packet.explosionParticle(),
                            packet.explosionSound(),
                            packet.blockParticles()
                    ));
                }
            }
            case NoXZ -> {
                if (event.getPacket() instanceof ClientboundPlayerPositionPacket && stage == VelocityStage.NONE) {
                    lag = true;
                    return;
                }
                if (event.getPacket() instanceof ClientboundSetEntityMotionPacket(
                        int id, Vec3 movement
                ) && id == mc.player.getId()) {
                    if (stage == VelocityStage.NONE) {
                        if (!lag) {
                            stage = VelocityStage.DELAY;
                            velocityTime = System.currentTimeMillis();
                            event.setCancelled(true);
                            velocity = movement;
                        } else {
                            lag = false;
                        }
                    } else {
                        velocity = movement;
                        stage = VelocityStage.LAG;
                        event.setCancelled(true);
                    }
                    return;
                }

                if (stage != VelocityStage.NONE) {
                    Packet<? super ClientPacketListener> packet = (Packet<? super ClientPacketListener>) event.getPacket();

                    if (packet instanceof ClientboundPlayerPositionPacket) {
                        stage = VelocityStage.LAG;
                        return;
                    }

                    if (packet instanceof ClientboundPlayerLookAtPacket) {
                        stage = VelocityStage.LAG;
                        return;
                    }

                    if (packet instanceof ClientboundDisconnectPacket || packet instanceof ClientboundRespawnPacket) {
                        clear(false);
                        return;
                    }

                    if (!(packet instanceof ClientboundPingPacket) && !(packet instanceof ClientboundMoveEntityPacket) && !(packet instanceof ClientboundTeleportEntityPacket)) {
                        return;
                    }

                    if (packet instanceof ClientboundMoveEntityPacket movePacket) {
                        Entity entity = movePacket.getEntity(mc.level);
                        if (entity != null) {
                            Vec3 currentPos = targets.getOrDefault(entity, entity.position());

                            if (movePacket.hasPosition()) {
                                double dx = movePacket.getXa() / 4096.0D;
                                double dy = movePacket.getYa() / 4096.0D;
                                double dz = movePacket.getZa() / 4096.0D;
                                targets.put(entity, currentPos.add(dx, dy, dz));
                            }
                        }
                    }

                    if (packet instanceof ClientboundTeleportEntityPacket teleportPacket) {
                        Entity entity = mc.level.getEntity(teleportPacket.id());
                        targets.put(entity, teleportPacket.change().position());
                    }

                    packets.add(packet);
                    event.setCancelled(true);
                }
            }
            case Legit -> {
                if (event.getPacket() instanceof ClientboundSetEntityMotionPacket packet && packet.id() == mc.player.getId()) {
                    jump = true;
                }
            }
        }
    }

    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        switch (mode.getValue()) {
            case NoXZ -> {
                if (stage == VelocityStage.ATTACK) {
                    if (mc.hitResult instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof Player player && !AntiBot.INSTANCE.isBot(player)) {
                        var motionXZ = 1.0D;
                        for (int i = 0; i < attacks.getValue(); i++) {
                            if (mc.player.isSprinting()) mc.player.setSprinting(false);
                            mc.gameMode.attack(mc.player, target);
                            mc.player.swing(InteractionHand.MAIN_HAND);
                            motionXZ *= 0.6D;
                        }
                        mc.player.setDeltaMovement(velocity.x * motionXZ, velocity.y, velocity.z * motionXZ);
                        stage = VelocityStage.CLEAR;
                    }
                } else if (System.currentTimeMillis() - velocityTime >= delayTime.getValue() && stage == VelocityStage.DELAY) {
                    mc.player.setDeltaMovement(velocity.x, velocity.y, velocity.z);
                    stage = VelocityStage.CLEAR;
                }

                if (lag && mc.player.hurtTime == 0) {
                    lag = false;
                }
            }
        }
    }

    private void clear(boolean handle) {
        lag = false;
        stage = VelocityStage.NONE;
        targets.clear();
        target = null;
        if (!handle) {
            packets.clear();
            return;
        }
        while (!packets.isEmpty()) {
            Packet<? super ClientPacketListener> packet = packets.poll();
            packet.handle(mc.getConnection());
        }
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (mode.is(Mode.NoXZ)) {
            if (stage == VelocityStage.DELAY && velocity != null && mc.hitResult instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof Player player && !AntiBot.INSTANCE.isBot(player)) {
                stage = VelocityStage.ATTACK;
                this.target = player;
            }
        }
        if (jump) {
            if (mc.player.onGround() && MoveUtils.isMoving()) {
                mc.player.input.makeJump();
            }
            jump = false;
        }
    }

}
