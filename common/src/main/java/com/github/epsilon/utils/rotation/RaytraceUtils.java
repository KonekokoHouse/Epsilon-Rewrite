package com.github.epsilon.utils.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector2f;

import java.util.function.Predicate;

public class RaytraceUtils {

    static Minecraft mc = Minecraft.getInstance();

    public static Vec3 getRotationVector(Vector2f rotation) {
        return getRotationVector(rotation.x, rotation.y);
    }

    public static Vec3 getRotationVector(float yaw, float pitch) {
        return Vec3.directionFromRotation(pitch, yaw);
    }

    public static EntityHitResult rayTraceEntity(double range, Vector2f rotation, Predicate<Entity> filter) {
        if (mc.getCameraEntity() == null) return null;
        Entity entity = mc.getCameraEntity();

        Vec3 cameraVec = entity.getEyePosition();
        Vec3 rotationVec = getRotationVector(rotation);

        Vec3 endVec = cameraVec.add(rotationVec.x * range, rotationVec.y * range, rotationVec.z * range);
        AABB box = entity.getBoundingBox().expandTowards(rotationVec.multiply(range, range, range)).inflate(1.0, 1.0, 1.0);

        return ProjectileUtil.getEntityHitResult(entity, cameraVec, endVec, box, e -> !e.isSpectator() && e.isPickable() && filter.test(e), range * range);
    }

    public static BlockHitResult rayTraceBlock(double range, Vector2f rotation, BlockPos pos, BlockState state) {
        Entity entity = mc.getCameraEntity();

        Vec3 start = entity.getEyePosition();
        Vec3 rotationVec = getRotationVector(rotation);

        Vec3 end = start.add(rotationVec.x * range, rotationVec.y * range, rotationVec.z * range);

        return state.getCollisionShape(mc.level, pos, CollisionContext.of(mc.player)).clip(start, end, pos);
    }

    public static BlockHitResult rayCast(Vector2f rotation, double range) {
        return rayCast(rotation, range, false, 1.0f);
    }

    public static BlockHitResult rayCast(Vector2f rotation, double range, boolean includeFluids, float tickDelta) {
        return rayCast(range, includeFluids, mc.player.getPosition(tickDelta), getRotationVector(rotation), mc.getCameraEntity());
    }

    public static BlockHitResult rayCast(double range, boolean includeFluids, Vec3 start, Vec3 direction, Entity entity) {
        Vec3 end = start.add(direction.x * range, direction.y * range, direction.z * range);

        return mc.level.clip(
                new ClipContext(
                        start,
                        end,
                        ClipContext.Block.OUTLINE,
                        includeFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE,
                        entity
                )
        );
    }

    public static boolean canSeePointFrom(Vec3 eyes, Vec3 vec3) {
        return mc.level.clip(new ClipContext(eyes, vec3, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player)).getType() == HitResult.Type.MISS;
    }

    public static boolean facingEnemy(Entity toEntity, double range, Vector2f rotation) {
        return rayTraceEntity(range, rotation, entity -> entity == toEntity) != null;
    }

    public static boolean facingEnemy(Entity fromEntity, Entity toEntity, Vector2f rotation, double range, double wallsRange) {
        Vec3 cameraVec = fromEntity.getEyePosition();
        Vec3 rotationVec = getRotationVector(rotation);

        double rangeSquared = range * range;
        double wallsRangeSquared = wallsRange * wallsRange;

        Vec3 endVec = cameraVec.add(rotationVec.x * range, rotationVec.y * range, rotationVec.z * range);
        AABB box = fromEntity.getBoundingBox().expandTowards(rotationVec.scale(range)).inflate(1.0);

        EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(
                fromEntity, cameraVec, endVec, box, e -> !e.isSpectator() && e.isPickable() && e == toEntity, rangeSquared
        );

        if (entityHitResult == null) return false;

        double distance = cameraVec.distanceToSqr(entityHitResult.getLocation());

        return distance <= rangeSquared && canSeePointFrom(cameraVec, entityHitResult.getLocation()) || distance <= wallsRangeSquared;
    }

    public static boolean overBlock(Vector2f rotation, Direction dir, BlockPos pos, boolean strict) {
        Vec3 lookVec = Vec3.directionFromRotation(rotation.y, rotation.x);

        Vec3 eyePos = mc.player.getEyePosition(1.0F);
        double reach = 4.5D;
        Vec3 endVec = eyePos.add(lookVec.scale(reach));

        BlockHitResult result = mc.level.clip(new ClipContext(
                eyePos,
                endVec,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player
        ));

        if (result.getType() == HitResult.Type.MISS) {
            return false;
        }

        return result.getBlockPos().equals(pos) && (!strict || result.getDirection() == dir);
    }

    public static boolean overBlock(Vector2f rotation, BlockPos pos, boolean strict) {
        return overBlock(rotation, Direction.UP, pos, strict);
    }

    public static boolean overBlock(Vector2f rotation, BlockPos pos) {
        return overBlock(rotation, Direction.UP, pos, false);
    }

    public static boolean overBlock(Vector2f rotation, BlockPos pos, Direction enumFacing) {
        return overBlock(rotation, enumFacing, pos, true);
    }

}
