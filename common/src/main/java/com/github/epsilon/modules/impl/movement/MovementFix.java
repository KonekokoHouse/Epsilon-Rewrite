package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.impl.MoveInputEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import net.minecraft.util.Mth;

public class MovementFix extends Module {

    public static final MovementFix INSTANCE = new MovementFix();

    private MovementFix() {
        super("Movement Fix", Category.MOVEMENT);
    }

    public void fixMovement(MoveInputEvent event, float yaw) {
        float forward = event.getForward();
        float strafe = event.getStrafe();

        int angleUnit = 45;
        float angleTolerance = 22.5f;
        float directionFactor = Math.max(Math.abs(forward), Math.abs(strafe));
        double angleDifference = Mth.wrapDegrees(getDirection(forward, strafe) - yaw);
        double angleDistance = Math.abs(angleDifference);

        forward = 0.0F;
        strafe = 0.0F;

        if (angleDistance <= (double) ((float) angleUnit + angleTolerance)) {
            forward++;
        } else if (angleDistance >= (double) (180.0F - (float) angleUnit - angleTolerance)) {
            forward--;
        }

        if (angleDifference >= (double) ((float) angleUnit - angleTolerance) && angleDifference <= (double) (180.0F - (float) angleUnit + angleTolerance)) {
            strafe--;
        } else if (angleDifference <= (double) ((float) (-angleUnit) + angleTolerance) && angleDifference >= (double) (-180.0F + (float) angleUnit - angleTolerance)) {
            strafe++;
        }

        forward *= directionFactor;
        strafe *= directionFactor;

        event.setForward(forward);
        event.setStrafe(strafe);
    }

    private float getDirection(float forward, float strafe) {
        float yaw = mc.player.getYRot();

        boolean isMovingForward = forward > 0;
        boolean isMovingBack = forward < 0;
        boolean isMovingRight = strafe > 0;
        boolean isMovingLeft = strafe < 0;
        boolean isMovingSideways = isMovingRight || isMovingLeft;
        boolean isMovingStraight = isMovingForward || isMovingBack;

        if (forward != 0.0F || strafe != 0.0F) {
            if (isMovingBack && !isMovingSideways) {
                return yaw + 180.0f;
            }
            if (isMovingForward && isMovingLeft) {
                return yaw + 45.0f;
            }
            if (isMovingForward && isMovingRight) {
                return yaw - 45.0f;
            }
            if (!isMovingStraight && isMovingLeft) {
                return yaw + 90.0f;
            }
            if (!isMovingStraight) {
                return yaw - 90.0f;
            }
            if (isMovingBack && isMovingLeft) {
                return yaw + 135.0f;
            }
            if (isMovingBack) {
                return yaw - 135.0f;
            }
        }

        return yaw;
    }

}

