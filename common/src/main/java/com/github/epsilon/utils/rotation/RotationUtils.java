package com.github.epsilon.utils.rotation;

import com.github.epsilon.managers.RotationManager;
import com.github.epsilon.utils.math.MathUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector2f;

public class RotationUtils {

    private static final Minecraft mc = Minecraft.getInstance();

    public static Direction getClickSide(BlockPos pos) {
        // 第一阶段：尝试找到玩家视线可见且距离最近的面
        Direction bestSide = findBestDirection(pos, true);

        if (bestSide != null) {
            return bestSide;
        }

        // 第二阶段：如果找不到可见面，则尝试符合 Grim AC 规则的面（作为 fallback）
        // 注意：这里重置 range 为最大值，以便在 Grim 规则下重新寻找最近的面
        bestSide = findBestDirection(pos, false);

        // 如果连 Grim 规则下的面都找不到，默认返回 UP (或其他安全默认值)
        return bestSide != null ? bestSide : Direction.UP;
    }

    /**
     * 寻找最佳点击方向
     *
     * @param pos          目标方块位置
     * @param useGrimCheck 如果为 true，使用 canSee 检查；如果为 false，使用 isGrimDirection 检查
     * @return 最佳方向，如果没有符合条件的方向则返回 null
     */
    private static Direction findBestDirection(BlockPos pos, boolean useGrimCheck) {
        Direction bestSide = null;
        double minDistSqr = Double.MAX_VALUE; // 使用平方距离进行比较，避免开方运算

        Vec3 eyePos = mc.player.getEyePosition();

        for (Direction side : Direction.values()) {
            // 根据模式选择检查条件
            if (useGrimCheck) {
                if (!canSee(pos, side)) {
                    continue;
                }
            } else {
                if (!isGrimDirection(pos, side)) {
                    continue;
                }
            }

            // 计算当前面中心点到眼睛的平方距离
            double distSqr = eyePos.distanceToSqr(Vec3.atCenterOf(pos.relative(side)));

            // 如果当前距离更近，则更新最佳方向
            if (distSqr < minDistSqr) {
                minDistSqr = distSqr;
                bestSide = side;
            }
        }

        return bestSide;
    }

    public static boolean canSee(BlockPos pos, Direction side) {
        Vec3 testVec = pos.getCenter().add(side.getUnitVec3i().getX() * 0.5, side.getUnitVec3i().getY() * 0.5, side.getUnitVec3i().getZ() * 0.5);
        HitResult result = mc.level.clip(new ClipContext(mc.player.getEyePosition(), testVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return result.getType() == HitResult.Type.MISS;
    }

    private static boolean isIntersected(AABB bb, AABB other) {
        return other.maxX - Shapes.EPSILON > bb.minX
                && other.minX + Shapes.EPSILON < bb.maxX
                && other.maxY - Shapes.EPSILON > bb.minY
                && other.minY + Shapes.EPSILON < bb.maxY
                && other.maxZ - Shapes.EPSILON > bb.minZ
                && other.minZ + Shapes.EPSILON < bb.maxZ;
    }

    private static AABB getCombinedBox(BlockPos pos, Level level) {
        VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos).move(pos);
        AABB combined = new AABB(pos);
        for (AABB box : shape.toAabbs()) {
            double minX = Math.max(box.minX, combined.minX);
            double minY = Math.max(box.minY, combined.minY);
            double minZ = Math.max(box.minZ, combined.minZ);
            double maxX = Math.min(box.maxX, combined.maxX);
            double maxY = Math.min(box.maxY, combined.maxY);
            double maxZ = Math.min(box.maxZ, combined.maxZ);
            combined = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }

        return combined;
    }

    public static boolean isGrimDirection(BlockPos pos, Direction direction) {
        // see ac.grim.grimac.checks.impl.scaffolding.PositionPlace
        AABB combined = getCombinedBox(pos, mc.level);
        LocalPlayer player = mc.player;
        AABB eyePositions = new AABB(player.getX(), player.getY() + 0.4, player.getZ(), player.getX(), player.getY() + 1.62, player.getZ()).inflate(0.0002);
        if (isIntersected(eyePositions, combined)) {
            return true;
        }
        return !switch (direction) {
            case NORTH -> eyePositions.minZ > combined.minZ;
            case SOUTH -> eyePositions.maxZ < combined.maxZ;
            case EAST -> eyePositions.maxX < combined.maxX;
            case WEST -> eyePositions.minX > combined.minX;
            case UP -> eyePositions.maxY < combined.maxY;
            case DOWN -> eyePositions.minY > combined.minY;
        };
    }

    public static Direction getDirection(BlockPos blockPos) {
        double eyePos = mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose());
        VoxelShape outline = mc.level.getBlockState(blockPos).getCollisionShape(mc.level, blockPos);

        if (eyePos > blockPos.getY() + outline.max(Direction.Axis.Y) && mc.level.getBlockState(blockPos.above()).canBeReplaced()) {
            return Direction.UP;
        } else if (eyePos < blockPos.getY() + outline.min(Direction.Axis.Y) && mc.level.getBlockState(blockPos.below()).canBeReplaced()) {
            return Direction.DOWN;
        } else {
            BlockPos difference = blockPos.subtract(mc.player.blockPosition());

            if (Math.abs(difference.getX()) > Math.abs(difference.getZ())) {
                return difference.getX() > 0 ? Direction.WEST : Direction.EAST;
            } else {
                return difference.getZ() > 0 ? Direction.NORTH : Direction.SOUTH;
            }
        }
    }

    public static Vector2f calculate(Vec3 from, Vec3 to) {
        final Vec3 diff = to.subtract(from);
        final double distance = Math.hypot(diff.x, diff.z);
        final float yaw = (float) Math.toDegrees(Mth.atan2(diff.z, diff.x)) - 90.0F;
        final float pitch = (float) Math.toDegrees(-(Mth.atan2(diff.y, distance)));
        return new Vector2f(yaw, pitch);
    }

    public static boolean isInFov(LivingEntity entity, float fov) {
        if (fov >= 360.0) return true;
        float yawDiff = Math.abs(Mth.wrapDegrees(RotationUtils.getRotationsToEntity(entity).x - mc.player.getYRot()));
        return yawDiff <= fov / 2.0;
    }

    public static Vector2f getRotationsToEntity(LivingEntity entity) {
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 targetPos = entity.position().add(0, entity.getBbHeight() / 2.0, 0);
        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(-Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, dist));

        return new Vector2f(yaw, Mth.clamp(pitch, -90, 90));
    }

    public static double getEyeDistanceToEntity(LivingEntity entity) {
        Vec3 eyePos = mc.player.getEyePosition();
        AABB box = entity.getBoundingBox();
        double dx = Math.max(box.minX - eyePos.x, Math.max(0, eyePos.x - box.maxX));
        double dy = Math.max(box.minY - eyePos.y, Math.max(0, eyePos.y - box.maxY));
        double dz = Math.max(box.minZ - eyePos.z, Math.max(0, eyePos.z - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static Vector2f calculate(final Entity entity) {
        return calculate(entity.position().add(0, Mth.clamp(
                mc.player.getY() - entity.getY() + mc.player.getEyeHeight(mc.player.getPose()),
                0,
                (entity.getBoundingBox().maxY - entity.getBoundingBox().minY) * 0.9
        ), 0));
    }

    public static Vector2f calculate(final Entity entity, final boolean adaptive, final double range) {
        Vector2f normalRotations = calculate(entity);
        if (!adaptive || RaytraceUtils.facingEnemy(mc.player, entity, normalRotations, range, 0)) {
            return normalRotations;
        }

        for (double yPercent = 1; yPercent >= 0; yPercent -= 0.25 + Math.random() * 0.1) {
            for (double xPercent = 1; xPercent >= -0.5; xPercent -= 0.5) {
                for (double zPercent = 1; zPercent >= -0.5; zPercent -= 0.5) {
                    Vector2f adaptiveRotations = calculate(entity.position().add(
                            (entity.getBoundingBox().maxX - entity.getBoundingBox().minX) * xPercent,
                            (entity.getBoundingBox().maxY - entity.getBoundingBox().minY) * yPercent,
                            (entity.getBoundingBox().maxZ - entity.getBoundingBox().minZ) * zPercent)
                    );
                    if (RaytraceUtils.facingEnemy(mc.player, entity, adaptiveRotations, range, 0)) {
                        return adaptiveRotations;
                    }
                }
            }
        }

        return normalRotations;
    }

    public static Vector2f calculate(Vec3 to) {
        return calculate(mc.player.position().add(0, mc.player.getEyeHeight(mc.player.getPose()), 0), to);
    }

    public static Vector2f calculate(BlockPos to) {
        return calculate(mc.player.position().add(0, mc.player.getEyeHeight(mc.player.getPose()), 0), to.getCenter());
    }

    public static Vector2f calculate(Vec3 to, Direction direction) {
        double x = to.x + 0.5;
        double y = to.y + 0.5;
        double z = to.z + 0.5;

        x += (double) direction.getStepX() * 0.5;
        y += (double) direction.getStepY() * 0.5;
        z += (double) direction.getStepZ() * 0.5;
        return calculate(new Vec3(x, y, z));
    }

    public static Vector2f calculate(BlockPos position, Direction direction) {
        double x = position.getX() + 0.5D;
        double y = position.getY() + 0.5D;
        double z = position.getZ() + 0.5D;

        x += (double) direction.getUnitVec3i().getX() * 0.5;
        y += (double) direction.getUnitVec3i().getY() * 0.5;
        z += (double) direction.getUnitVec3i().getZ() * 0.5;
        return calculate(new Vec3(x, y, z));
    }

    public static Vector2f applySensitivityPatch(final Vector2f rotation) {
        final Vector2f previousRotation = new Vector2f(mc.player.yRotO, mc.player.xRotO);
        final float mouseSensitivity = (float) (mc.options.sensitivity().get() * (1 + Math.random() / 10000000) * 0.6F + 0.2F);
        final double multiplier = mouseSensitivity * mouseSensitivity * mouseSensitivity * 8.0F * 0.15D;
        final float yaw = previousRotation.x + (float) (Math.round((rotation.x - previousRotation.x) / multiplier) * multiplier);
        final float pitch = previousRotation.y + (float) (Math.round((rotation.y - previousRotation.y) / multiplier) * multiplier);
        return new Vector2f(yaw, Mth.clamp(pitch, -90, 90));
    }

    public static Vector2f applySensitivityPatch(final Vector2f rotation, final Vector2f previousRotation) {
        final float mouseSensitivity = (float) (mc.options.sensitivity().get() * (1 + Math.random() / 10000000) * 0.6F + 0.2F);
        final double multiplier = mouseSensitivity * mouseSensitivity * mouseSensitivity * 8.0F * 0.15D;
        final float yaw = previousRotation.x + (float) (Math.round((rotation.x - previousRotation.x) / multiplier) * multiplier);
        final float pitch = previousRotation.y + (float) (Math.round((rotation.y - previousRotation.y) / multiplier) * multiplier);
        return new Vector2f(yaw, Mth.clamp(pitch, -90, 90));
    }

    public static Vector2f relateToPlayerRotation(final Vector2f rotation) {
        final Vector2f previousRotation = new Vector2f(mc.player.yRotO, mc.player.xRotO);
        final float yaw = previousRotation.x + Mth.wrapDegrees(rotation.x - previousRotation.x);
        final float pitch = Mth.clamp(rotation.y, -90, 90);
        return new Vector2f(yaw, pitch);
    }

    public static Vector2f resetRotation(final Vector2f rotation) {
        if (rotation == null) return null;
        final float yaw = rotation.x + Mth.wrapDegrees(mc.player.getYRot() - rotation.x);
        final float pitch = mc.player.getXRot();
        return new Vector2f(yaw, pitch);
    }

    public static Vector2f move(final Vector2f lastRotation, final Vector2f targetRotation, double speed) {
        if (speed != 0) {

            double deltaYaw = Mth.wrapDegrees(targetRotation.x - lastRotation.x);
            final double deltaPitch = (targetRotation.y - lastRotation.y);

            final double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
            if (distance < 1.0E-6) {
                return new Vector2f(0, 0);
            }
            final double distributionYaw = Math.abs(deltaYaw / distance);
            final double distributionPitch = Math.abs(deltaPitch / distance);

            final double maxYaw = speed * distributionYaw;
            final double maxPitch = speed * distributionPitch;

            final float moveYaw = (float) Math.max(Math.min(deltaYaw, maxYaw), -maxYaw);
            final float movePitch = (float) Math.max(Math.min(deltaPitch, maxPitch), -maxPitch);

            return new Vector2f(moveYaw, movePitch);
        }

        return new Vector2f(0, 0);
    }

    public static Vector2f smooth(final Vector2f targetRotation, final double speed) {
        return smooth(RotationManager.INSTANCE.lastRotations, targetRotation, speed);
    }

    public static Vector2f smooth(final Vector2f lastRotation, final Vector2f targetRotation, final double speed) {
        float yaw = targetRotation.x;
        float pitch = targetRotation.y;
        final float lastYaw = lastRotation.x;
        final float lastPitch = lastRotation.y;

        if (speed != 0) {
            Vector2f move = move(lastRotation, targetRotation, speed);

            yaw = lastYaw + move.x;
            pitch = lastPitch + move.y;

            float motion = Math.abs(move.x) + Math.abs(move.y);
            int iterations;
            if (motion < 0.02f) iterations = 1;
            else if (motion < 0.2f) iterations = 2;
            else if (motion < 2.0f) iterations = 3;
            else iterations = 4;

            for (int i = 0; i < iterations; i++) {
                if (motion > 0.0001f) {
                    yaw += (float) MathUtils.getRandom(-0.0006, 0.0006);
                    pitch += (float) MathUtils.getRandom(-0.0035, 0.0035);
                }

                final Vector2f rotations = new Vector2f(yaw, pitch);
                final Vector2f fixedRotations = applySensitivityPatch(rotations);

                yaw = shortestYaw(lastYaw, fixedRotations.x);
                pitch = Math.max(-90, Math.min(90, fixedRotations.y));
            }
        }

        return new Vector2f(yaw, pitch);
    }

    private static float shortestYaw(float from, float to) {
        return from + Mth.wrapDegrees(to - from);
    }

}
