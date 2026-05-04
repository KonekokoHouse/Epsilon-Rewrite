package com.github.epsilon.managers;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.modules.impl.player.MovementFix;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class RotationManager {

    public static final RotationManager INSTANCE = new RotationManager();

    private final Minecraft mc = Minecraft.getInstance();

    private final Vector2f offset = new Vector2f(0, 0);
    public Vector2f rotations;
    public Vector2f lastRotations = new Vector2f(0, 0);
    public Vector2f targetRotations;
    public Vector2f animationRotation = null;
    public Vector2f lastAnimationRotation = null;

    private boolean active;
    private boolean smoothed;
    private double rotationSpeed;
    private Function<Vector2f, Boolean> raycast;
    private float randomAngle;

    private final List<RotationRequest> tickRequests = new ArrayList<>();

    private RotationManager() {
        EventBus.INSTANCE.subscribe(this);
    }

    public void applyRotation(final Vector2f rotations, final double rotationSpeed) {
        applyRotation(rotations, rotationSpeed, null, Priority.Lowest, null);
    }

    public void applyRotation(final Vector2f rotations, final double rotationSpeed, final Priority priority) {
        applyRotation(rotations, rotationSpeed, null, priority, null);
    }

    public void applyRotation(final Vector2f rotations, final double rotationSpeed, final int priority) {
        applyRotation(rotations, rotationSpeed, null, priority, null);
    }

    public void applyRotation(final Vector2f rotations, final double rotationSpeed, final Priority priority, final Consumer<RotationApplyRecord> callback) {
        applyRotation(rotations, rotationSpeed, null, priority, callback);
    }

    public void applyRotation(final Vector2f rotations, final double rotationSpeed, final int priority, final Consumer<RotationApplyRecord> callback) {
        applyRotation(rotations, rotationSpeed, null, priority, callback);
    }

    public void applyRotation(final Vector2f rotations, final double rotationSpeed, final Function<Vector2f, Boolean> raycast, final Priority priority, final Consumer<RotationApplyRecord> callback) {
        if (Double.isNaN(rotations.x) || Double.isNaN(rotations.y) || Double.isInfinite(rotations.x) || Double.isInfinite(rotations.y)) {
            return;
        }

        final Priority safePriority = priority == null ? Priority.Lowest : priority;
        tickRequests.add(new RotationRequest(new Vector2f(rotations.x, rotations.y), rotationSpeed * 18, raycast, safePriority, safePriority.priority, callback));
    }

    public void applyRotation(final Vector2f rotations, final double rotationSpeed, final Function<Vector2f, Boolean> raycast, final int priority, final Consumer<RotationApplyRecord> callback) {
        if (Double.isNaN(rotations.x) || Double.isNaN(rotations.y) || Double.isInfinite(rotations.x) || Double.isInfinite(rotations.y)) {
            return;
        }

        final int safePriority = Math.max(Priority.Lowest.priority, priority);
        tickRequests.add(new RotationRequest(new Vector2f(rotations.x, rotations.y), rotationSpeed * 18, raycast, resolvePriority(safePriority), safePriority, callback));
    }

    private Priority resolvePriority(int priority) {
        if (priority >= Priority.Highest.priority) return Priority.Highest;
        if (priority >= Priority.High.priority) return Priority.High;
        if (priority >= Priority.Medium.priority) return Priority.Medium;
        if (priority >= Priority.Low.priority) return Priority.Low;
        return Priority.Lowest;
    }

    private void prepareRotateBack() {
        targetRotations = new Vector2f(mc.player.getYRot(), mc.player.getXRot());
        rotationSpeed = ClientSetting.INSTANCE.rotateBackSpeed.getValue() * 18;
        raycast = null;
        smoothed = false;
    }

    private void smooth() {
        if (!smoothed) {
            float targetYaw = targetRotations.x;
            float targetPitch = targetRotations.y;
            float currentYaw = rotations != null ? rotations.x : mc.player.getYRot();
            float currentPitch = rotations != null ? rotations.y : mc.player.getXRot();

            if (raycast != null && (Math.abs(targetYaw - currentYaw) > 5 || Math.abs(targetPitch - currentPitch) > 5)) {
                final Vector2f trueTargetRotations = new Vector2f(targetRotations.x, targetRotations.y);

                double speed = (Math.random() * Math.random() * Math.random()) * 20;
                randomAngle += (float) ((20 + (float) (Math.random() - 0.5) * (Math.random() * Math.random() * Math.random() * 360)) * (mc.player.tickCount / 10 % 2 == 0 ? -1 : 1));

                if (Float.isNaN(randomAngle) || Float.isInfinite(randomAngle)) randomAngle = 0;

                offset.x = ((float) (offset.x + -Mth.sin((float) Math.toRadians(randomAngle)) * speed));
                offset.y = ((float) (offset.y + Mth.cos((float) Math.toRadians(randomAngle)) * speed));

                if (Float.isNaN(offset.x) || Float.isInfinite(offset.x)) offset.x = 0;
                if (Float.isNaN(offset.y) || Float.isInfinite(offset.y)) offset.y = 0;

                targetYaw += offset.x;
                targetPitch += offset.y;

                if (!raycast.apply(new Vector2f(targetYaw, targetPitch))) {
                    randomAngle = (float) Math.toDegrees(Math.atan2(trueTargetRotations.x - targetYaw, targetPitch - trueTargetRotations.y)) - 180;
                    if (Float.isNaN(randomAngle)) randomAngle = 0;

                    targetYaw -= offset.x;
                    targetPitch -= offset.y;

                    offset.x = ((float) (offset.x + -Mth.sin((float) Math.toRadians(randomAngle)) * speed));
                    offset.y = ((float) (offset.y + Mth.cos((float) Math.toRadians(randomAngle)) * speed));

                    if (Float.isNaN(offset.x) || Float.isInfinite(offset.x)) offset.x = 0;
                    if (Float.isNaN(offset.y) || Float.isInfinite(offset.y)) offset.y = 0;

                    targetYaw = targetYaw + offset.x;
                    targetPitch = targetPitch + offset.y;
                }

                if (!raycast.apply(new Vector2f(targetYaw, targetPitch))) {
                    offset.x = 0;
                    offset.y = 0;

                    targetYaw = (float) (targetRotations.x + Math.random() * 2);
                    targetPitch = (float) (targetRotations.y + Math.random() * 2);
                }
            }

            rotations = RotationUtils.smooth(new Vector2f(targetYaw, targetPitch), rotationSpeed + Math.random());

            if (Float.isNaN(rotations.x) || Float.isInfinite(rotations.x)) {
                rotations.x = mc.player.getYRot();
            }

            if (Float.isNaN(rotations.y) || Float.isInfinite(rotations.y)) {
                rotations.y = mc.player.getXRot();
            }
        }

        smoothed = true;
    }

    public boolean isSmoothed() {
        return smoothed;
    }

    public void setSmoothed(boolean smoothed) {
        this.smoothed = smoothed;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public float getYaw() {
        if (mc.player == null) {
            return 0.0f;
        } else if (active) {
            return rotations.x;
        } else {
            return mc.player.getYRot();
        }
    }

    public float getPitch() {
        if (mc.player == null) {
            return 0.0f;
        } else if (active) {
            return rotations.y;
        } else {
            return mc.player.getXRot();
        }
    }

    public Vector2f getRotation() {
        if (active) return rotations;
        else return new Vector2f(mc.player.getYRot(), mc.player.getXRot());
    }

    public float[] getRotation(Vec3 vec) {
        return getRotation(mc.player.getEyePosition(), vec);
    }

    public float[] getRotation(Vec3 eyesPos, Vec3 vec) {
        double diffX = vec.x - eyesPos.x;
        double diffY = vec.y - eyesPos.y;
        double diffZ = vec.z - eyesPos.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(diffY, diffXZ)));
        return new float[]{Mth.wrapDegrees(yaw), Mth.wrapDegrees(pitch)};
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) {
            tickRequests.clear();
            return;
        }

        if (!active || rotations == null || lastRotations == null || targetRotations == null) {
            targetRotations = new Vector2f(mc.player.getYRot(), mc.player.getXRot());
            lastRotations = new Vector2f(mc.player.getYRot(), mc.player.getXRot());
            rotations = new Vector2f(mc.player.getYRot(), mc.player.getXRot());
        }

        if (!tickRequests.isEmpty()) {
            RotationRequest selectedRequest = tickRequests.getFirst();
            for (int i = 1; i < tickRequests.size(); i++) {
                RotationRequest request = tickRequests.get(i);
                if (request.priorityValue() > selectedRequest.priorityValue()) {
                    selectedRequest = request;
                }
            }

            this.targetRotations = new Vector2f(selectedRequest.targetRotation().x, selectedRequest.targetRotation().y);
            this.rotationSpeed = selectedRequest.rotationSpeed();
            this.raycast = selectedRequest.raycast();
            this.active = true;
            this.smoothed = false;

            smooth();

            Vector2f currentRot = rotations != null ? new Vector2f(rotations.x, rotations.y) : new Vector2f(mc.player.getYRot(), mc.player.getXRot());

            RotationApplyRecord record = new RotationApplyRecord(currentRot, active);

            if (selectedRequest.callback() != null) {
                try {
                    selectedRequest.callback().accept(record);
                } catch (Exception ignored) {
                }
            }

            tickRequests.clear();
        } else if (active) {
            prepareRotateBack();
        }

        if (active) {
            smooth();
        }
    }

    @EventHandler
    private void onRaytrace(RaytraceEvent event) {
        if (active && rotations != null) {
            event.setYaw(rotations.x);
            event.setPitch(rotations.y);
        }
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        MovementFix moveFix = MovementFix.INSTANCE;
        if (active && moveFix.isEnabled() && rotations != null && !mc.player.isFallFlying()) {
            moveFix.fixMovement(event, rotations.x);
        }
    }

    @EventHandler
    private void onStrafe(StrafeEvent event) {
        if (active && MovementFix.INSTANCE.isEnabled() && rotations != null) {
            event.setYaw(rotations.x);
        }
    }

    @EventHandler
    private void onJump(JumpEvent event) {
        if (active && MovementFix.INSTANCE.isEnabled() && rotations != null) {
            event.setYaw(rotations.x);
        }
    }

    @EventHandler
    public void onFallFlying(FallFlyingEvent event) {
        if (active && MovementFix.INSTANCE.isEnabled() && rotations != null) {
            event.setPitch(rotations.y);
        }
    }

    @EventHandler
    private void onSendPosition(SendPositionEvent event) {
        if (active && rotations != null) {
            float yaw = rotations.x;
            float pitch = rotations.y;

            if (Float.isNaN(yaw) || Float.isInfinite(yaw)) yaw = mc.player.getYRot();
            if (Float.isNaN(pitch) || Float.isInfinite(pitch)) pitch = mc.player.getXRot();
            pitch = Mth.clamp(pitch, -90.0f, 90.0f);

            event.setYaw(yaw);
            event.setPitch(pitch);

            if (Math.abs((rotations.x - mc.player.getYRot()) % 360) < 1 && Math.abs((rotations.y - mc.player.getXRot())) < 1) {
                active = false;
                correctDisabledRotations();
            }

            lastRotations = rotations;
        } else {
            lastRotations = new Vector2f(mc.player.getYRot(), mc.player.getXRot());
        }

        lastAnimationRotation = animationRotation;
        Vector2f currentAnimationRotation;
        if (active && rotations != null) {
            currentAnimationRotation = rotations;
        } else {
            currentAnimationRotation = new Vector2f(event.getYaw(), event.getPitch());
        }

        if (lastAnimationRotation == null) {
            animationRotation = currentAnimationRotation;
        } else {
            float targetYaw = currentAnimationRotation.x;
            float targetPitch = currentAnimationRotation.y;
            float lastYaw = lastAnimationRotation.x;
            float lastPitch = lastAnimationRotation.y;
            float yawDiff = Mth.wrapDegrees(targetYaw - lastYaw);
            float pitchDiff = targetPitch - lastPitch;

            float smoothYaw = lastYaw + yawDiff * 0.5f;
            float smoothPitch = lastPitch + pitchDiff * 0.5f;
            animationRotation = new Vector2f(Mth.wrapDegrees(smoothYaw), Mth.clamp(smoothPitch, -90.0f, 90.0f));
        }

        targetRotations = new Vector2f(mc.player.getYRot(), mc.player.getXRot());
        smoothed = false;
    }

    private void correctDisabledRotations() {
        if (lastRotations == null) return;
        final Vector2f rotations = new Vector2f(mc.player.getYRot(), mc.player.getXRot());
        final Vector2f fixedRotations = RotationUtils.resetRotation(RotationUtils.applySensitivityPatch(rotations, lastRotations));

        if (!Float.isNaN(fixedRotations.x) && !Float.isNaN(fixedRotations.y)) {
            mc.player.setYRot(fixedRotations.x);
            mc.player.setXRot(fixedRotations.y);
        }
    }

    private record RotationRequest(Vector2f targetRotation, double rotationSpeed, Function<Vector2f, Boolean> raycast,
                                   Priority priority, int priorityValue, Consumer<RotationApplyRecord> callback) {
    }

    public record RotationApplyRecord(Vector2f currentRotation, boolean active) {
    }

}
