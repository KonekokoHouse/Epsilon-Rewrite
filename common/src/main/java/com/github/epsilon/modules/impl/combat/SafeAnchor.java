package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.listeners.ConsumerListener;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.events.impl.TickEvent;
import com.github.epsilon.managers.RotationManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.combat.DamageUtils;
import com.github.epsilon.utils.math.MathUtils;
import com.github.epsilon.utils.player.ChatUtils;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.render.Render3DUtils;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.RotationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class SafeAnchor extends Module {

    public static final SafeAnchor INSTANCE = new SafeAnchor();

    private SafeAnchor() {
        super("Safe Anchor", Category.COMBAT);
        EventBus.INSTANCE.subscribe(new ConsumerListener<>(Render3DEvent.class,
                event -> {
                    if (!render.getValue() || renderBoxes.isEmpty()) return;

                    long time = System.currentTimeMillis();
                    long fadeTime = this.fadeTime.getValue().longValue();

                    renderBoxes.removeIf(box -> time - box.startTime() > fadeTime);

                    for (RenderBox box : renderBoxes) {
                        long age = time - box.startTime();
                        float progress = Mth.clamp((float) age / fadeTime, 0.0f, 1.0f);
                        float alphaFactor = Mth.clamp(1.0f - progress, 0.0f, 1.0f);

                        Color sideColor = box.sideColor();
                        Color lineColor = box.lineColor();

                        Color side = new Color(sideColor.getRed(), sideColor.getGreen(), sideColor.getBlue(), (int) (sideColor.getAlpha() * alphaFactor));
                        Color line = new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), (int) (lineColor.getAlpha() * alphaFactor));

                        Render3DUtils.drawFilledBox(box.aabb, side);
                        Render3DUtils.drawOutlineBox(event.getPoseStack(), box.aabb, line);
                    }
                }
        ));
    }

    private enum PlaceMode {
        Smart,
        AnchorSide
    }

    private final EnumSetting<PlaceMode> placeMode = enumSetting("Place Mode", PlaceMode.Smart);
    private final DoubleSetting placeRotationSpeed = doubleSetting("Place Speed", 2.0, 0.1, 15.0, 0.1);
    private final DoubleSetting explodeRotationSpeed = doubleSetting("Explode Speed", 4.0, 0.1, 15.0, 0.1);
    private final BoolSetting silentRotation = boolSetting("Silent Rotation", true);
    private final BoolSetting dynamicSpeed = boolSetting("DynamicSpeed", true);
    private final DoubleSetting farBoost = doubleSetting("FarBoost", 20.0, 0.0, 100.0, 1.0, dynamicSpeed::getValue);
    private final DoubleSetting farBoostThreshold = doubleSetting("FarBoostThreshold", 5.0, 1.0, 20.0, 0.5, dynamicSpeed::getValue);
    private final DoubleSetting nearReduction = doubleSetting("NearReduction", 15.0, 0.0, 100.0, 1.0, dynamicSpeed::getValue);
    private final DoubleSetting nearReductionThreshold = doubleSetting("NearReductionThreshold", 2.0, 0.1, 10.0, 0.5, dynamicSpeed::getValue);
    private final BoolSetting autoCharge = boolSetting("AutoCharge", true);
    private final BoolSetting autoPlace = boolSetting("AutoPlace", true);
    private final BoolSetting autoExplode = boolSetting("AutoExplode", true);
    private final BoolSetting ownAnchorOnly = boolSetting("OwnAnchorOnly", false);
    private final DoubleSetting minHealth = doubleSetting("Min Health", 4.0, 0.0, 20.0, 0.5);

    private final BoolSetting render = boolSetting("Render", true);
    private final IntSetting fadeTime = intSetting("Fade Time", 500, 0, 3000, 50, render::getValue);
    private final ColorSetting glowStoneSide = colorSetting("GlowStone Side", new Color(255, 220, 90, 100), render::getValue);
    private final ColorSetting glowStoneLine = colorSetting("GlowStone Line", new Color(255, 200, 40), render::getValue);

    private final BoolSetting debug = boolSetting("Debug", false);

    private final Set<BlockPos> ownAnchors = Collections.synchronizedSet(new LinkedHashSet<>());

    private BlockPos currentAnchorPos = null;
    private Vector2f targetRotation = null;
    private BlockPos targetActionPos = null;
    private Direction targetPlaceSide = null;
    private boolean isSidePlacement = false;
    private boolean isDiagonalPlacement = false;
    private boolean explodeNoRotate = false;
    private double currentRotationSpeed = 2.0;
    private int delay = 0;
    private long lastDebugMs;
    private int debugSeq;

    private enum Stage {
        None,
        Charging,
        RotToPlace,
        RotToExplode
    }

    private Stage stage = Stage.None;

    private final List<RenderBox> renderBoxes = new ArrayList<>();

    @Override
    protected void onEnable() {
        stage = Stage.None;
        delay = 0;
        currentAnchorPos = null;
        ownAnchors.clear();
        targetRotation = null;
        targetActionPos = null;
        targetPlaceSide = null;
        isSidePlacement = false;
        explodeNoRotate = false;
    }

    @Override
    protected void onDisable() {
        ownAnchors.clear();
        renderBoxes.clear();
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (nullCheck()) return;

        if (event.getPacket() instanceof ServerboundUseItemOnPacket interactPacket) {
            if (mc.player.getItemInHand(interactPacket.getHand()).is(Items.RESPAWN_ANCHOR)) {
                BlockHitResult hit = interactPacket.getHitResult();
                BlockPos pos = hit.getBlockPos();
                BlockState state = mc.level.getBlockState(pos);

                if (state.is(Blocks.RESPAWN_ANCHOR) || state.canBeReplaced()) {
                    ownAnchors.add(pos);
                } else {
                    BlockPos offsetPos = pos.relative(hit.getDirection());
                    ownAnchors.add(offsetPos);

                    if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                        ownAnchors.add(pos);
                    }
                }

                if (ownAnchors.size() > 30) {
                    synchronized (ownAnchors) {
                        BlockPos oldest = ownAnchors.iterator().next();
                        ownAnchors.remove(oldest);
                    }
                }
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.level == null) return;

        Packet<?> packet = event.getPacket();

        if (packet instanceof ClientboundBlockUpdatePacket updatePacket) {
            if (updatePacket.getBlockState().is(Blocks.RESPAWN_ANCHOR)) {

            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        if (silentRotation.getValue() && targetRotation != null) {
            RotationManager.INSTANCE.applyRotation(targetRotation, currentRotationSpeed, Priority.High);
        }

        if (delay > 0) {
            delay--;
            return;
        }

        HitResult hit = getCrosshairHit();
        debugState("Tick", hit);
        if (stage == Stage.None) {
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHit = (BlockHitResult) hit;
                BlockPos pos = blockHit.getBlockPos();
                if (mc.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR)) {
                    if (ownAnchorOnly.getValue() && !ownAnchors.contains(pos)) {
                        return;
                    }

                    FindItemResult glowstone = InvUtils.find(Items.GLOWSTONE);
                    if (!glowstone.found()) {
                        return;
                    }

                    currentAnchorPos = pos;
                    stage = Stage.Charging;
                }
            }
        }

        if (currentAnchorPos == null) return;

        switch (stage) {
            case Charging -> {
                if (autoCharge.getValue()) {
                    handleCharge();
                } else {
                    preparePlace();
                }
            }
            case RotToPlace -> {
                if (autoPlace.getValue()) {
                    handleRotatingToPlace();
                } else {
                    prepareExplode();
                }
            }
            case RotToExplode -> {
                if (autoExplode.getValue()) {
                    handleRotatingToExplode();
                } else {
                    resetState();
                }
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (nullCheck() || targetRotation == null) return;

        HitResult hit = getCrosshairHit();
        if (isSidePlacement && isLookingAtPlayerSide(hit)) {
            debugState("RenderSkip", hit);
            targetRotation = null;
            return;
        }

        if (isSidePlacement && stage == Stage.RotToPlace && targetPlaceSide != null) {
            debugState("RenderAim", hit);
        }

        if (!silentRotation.getValue()) {
            smoothAim(targetRotation, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
        }

        Vector2f currentRot = silentRotation.getValue() ? RotationManager.INSTANCE.getRotation() : new Vector2f(mc.player.getYRot(), mc.player.getXRot());

        float yawDiff = Math.abs(Mth.wrapDegrees(targetRotation.x - currentRot.x));
        float pitchDiff = Math.abs(targetRotation.y - currentRot.y);
        if (yawDiff < 1.0f && pitchDiff < 1.0f) {
            targetRotation = null;
        }
    }

    private void smoothAim(Vector2f targetRotation, float tickDelta) {
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();

        float targetYaw = targetRotation.x;
        float targetPitch = targetRotation.y;

        float yawDiff = Mth.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;

        double aimSpeed = currentRotationSpeed * 0.5;

        if (dynamicSpeed.getValue()) {
            double totalDiff = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
            if (totalDiff > farBoostThreshold.getValue()) {
                aimSpeed *= (1.0 + farBoost.getValue() / 100.0);
            } else if (totalDiff < nearReductionThreshold.getValue()) {
                aimSpeed *= (1.0 - nearReduction.getValue() / 100.0);
            }
        }

        aimSpeed += ThreadLocalRandom.current().nextDouble(-0.1, 0.1);
        aimSpeed = Math.max(0.1, aimSpeed);

        float yawChange = (float) Mth.clamp(yawDiff, -aimSpeed, aimSpeed);
        float pitchChange = (float) Mth.clamp(pitchDiff, -aimSpeed, aimSpeed);

        float sens = (float) (mc.options.sensitivity().get() * 0.6 + 0.2);
        float gcd = sens * sens * sens * 8.0f * 0.15f;

        yawChange = Math.round(yawChange / gcd) * gcd;
        pitchChange = Math.round(pitchChange / gcd) * gcd;

        mc.player.setYRot(currentYaw + yawChange);
        mc.player.setXRot(currentPitch + pitchChange);
    }

    private Vector2f getTargetRotation(Vec3 targetPos) {
        Vector2f rot = RotationUtils.calculate(mc.player.getEyePosition(), targetPos);

        float yawOffset = MathUtils.getRandom(-0.15f, 0.15f);
        float pitchOffset = MathUtils.getRandom(-0.15f, 0.15f);

        return new Vector2f(rot.x + yawOffset, rot.y + pitchOffset);
    }

    private void setShiftState(boolean state) {
        mc.player.setShiftKeyDown(state);
    }

    private boolean placeAt(BlockPos placePos, Direction avoidSide) {
        if (placePos == null) return false;

        setShiftState(true);
        Direction[] dirs = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (Direction dir : dirs) {
            if (avoidSide != null && dir == avoidSide) continue;
            BlockPos neighbor = placePos.relative(dir);
            BlockState state = mc.level.getBlockState(neighbor);
            if (state.isAir() || state.canBeReplaced()) continue;
            interactBlock(neighbor, dir.getOpposite());
            setShiftState(false);
            return true;
        }
        setShiftState(false);
        return false;
    }

    private void handleCharge() {
        int charges = mc.level.getBlockState(currentAnchorPos).getValue(RespawnAnchorBlock.CHARGE);
        if (placeMode.is(PlaceMode.AnchorSide) && isTooCloseToAnchor()) {
            BlockPos placePos = findPlacePos();
            if (placePos == null) {
                if (charges <= 0) {
                    FindItemResult glowstone = InvUtils.findInHotbar(Items.GLOWSTONE);
                    if (glowstone.found()) {
                        InvUtils.swap(glowstone.slot(), false);
                        interactBlock(currentAnchorPos, Direction.UP);
                        delay = 1;
                        explodeNoRotate = true;
                        prepareExplode();
                        return;
                    }
                } else {
                    explodeNoRotate = true;
                    prepareExplode();
                    return;
                }
            }
        }
        if (placeMode.is(PlaceMode.Smart) && isTooCloseToAnchor()) {
            BlockPos placePos = findPlacePos();
            if (placePos == null) {
                if (charges <= 0) {
                    FindItemResult glowstone = InvUtils.findInHotbar(Items.GLOWSTONE);
                    if (glowstone.found()) {
                        InvUtils.swap(glowstone.slot(), false);
                        interactBlock(currentAnchorPos, Direction.UP);
                        delay = 1;
                        prepareExplode();
                        return;
                    }
                } else {
                    prepareExplode();
                    return;
                }
            }
        }
        if (shouldExplodeNow()) {
            prepareExplode();
            return;
        }
        if (charges < 4) {
            FindItemResult glowstone = InvUtils.findInHotbar(Items.GLOWSTONE);
            if (glowstone.found()) {
                InvUtils.swap(glowstone.slot(), false);
                interactBlock(currentAnchorPos, Direction.UP);
                delay = 1;
            } else {
                resetState();
                return;
            }
        }

        if (autoPlace.getValue() && placeMode.is(PlaceMode.AnchorSide)) {
            BlockPos placePos = findPlacePos();

            if (isDiagonalPlacement) {
                preparePlace();
                return;
            }

            if (placePos != null) {
                FindItemResult block = InvUtils.findInHotbar(Items.GLOWSTONE, Items.RESPAWN_ANCHOR);
                if (block.found()) {
                    InvUtils.swap(block.slot(), false);
                    if (isSidePlacement && targetPlaceSide != null) {
                        if (mc.player.getMainHandItem().is(Items.GLOWSTONE)) {
                            if (placeAt(placePos, targetPlaceSide.getOpposite())) {
                                addRenderBox(placePos);
                            }
                        } else {
                            setShiftState(true);
                            interactBlock(currentAnchorPos, targetPlaceSide);
                            setShiftState(false);
                            addRenderBox(placePos);
                        }
                    } else {
                        interactBlock(placePos.below(), Direction.UP);
                        addRenderBox(placePos);
                    }
                    prepareExplode();
                    return;
                }
            }

            if (isExplosionSafe()) {
                prepareExplode();
            } else {
                resetState();
            }
        } else {
            preparePlace();
        }
    }

    private boolean shouldExplodeNow() {
        if (!autoExplode.getValue()) return false;
        if (!isTooCloseToAnchor()) return false;
        if (isExplosionSafe()) return true;
        return ownAnchors.contains(currentAnchorPos);
    }

    private boolean isTooCloseToAnchor() {
        double dx = mc.player.getX() - (currentAnchorPos.getX() + 0.5);
        double dz = mc.player.getZ() - (currentAnchorPos.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz) < 1.7;
    }

    private void preparePlace() {
        if (!autoPlace.getValue()) {
            if (isExplosionSafe()) {
                prepareExplode();
            } else {
                resetState();
            }
            return;
        }

        BlockPos placePos = findPlacePos();

        if (placePos != null) {
            FindItemResult block = InvUtils.findInHotbar(Items.GLOWSTONE, Items.RESPAWN_ANCHOR);
            if (block.found()) {
                InvUtils.swap(block.slot(), false);
                targetActionPos = placePos;
                currentRotationSpeed = placeRotationSpeed.getValue();

                if (isSidePlacement && targetPlaceSide != null) {
                    HitResult hit = getCrosshairHit();
                    if (isLookingAtPlayerSide(hit)) {
                        debugState("PreparePlaceNoRotate", hit);
                        targetRotation = null;
                    } else {
                        debugState("PreparePlaceRotate", hit);
                        Vec3 sideVec = Vec3.atCenterOf(currentAnchorPos).add(
                                targetPlaceSide.getStepX() * 0.45,
                                targetPlaceSide.getStepY() * 0.45,
                                targetPlaceSide.getStepZ() * 0.45
                        );
                        targetRotation = getTargetRotation(sideVec);
                    }
                } else {
                    targetRotation = getTargetRotation(Vec3.atCenterOf(placePos));
                }

                stage = Stage.RotToPlace;
                return;
            }
        }

        if (isExplosionSafe()) {
            prepareExplode();
        } else {
            resetState();
        }
    }

    private BlockPos findPlacePos() {
        isSidePlacement = false;
        isDiagonalPlacement = false;
        if (placeMode.is(PlaceMode.AnchorSide)) {
            double dx = mc.player.getX() - (currentAnchorPos.getX() + 0.5);
            double dz = mc.player.getZ() - (currentAnchorPos.getZ() + 0.5);

            double absX = Math.abs(dx);
            double absZ = Math.abs(dz);

            Direction xDir = dx > 0 ? Direction.EAST : Direction.WEST;
            Direction zDir = dz > 0 ? Direction.SOUTH : Direction.NORTH;

            boolean isDiagonalArea = (absX > 0 && absZ > 0) && (Math.min(absX, absZ) / Math.max(absX, absZ) > 0.5);

            if (isDiagonalArea) {
                Direction[] sides = {xDir, zDir};
                for (Direction side : sides) {
                    BlockPos neighbor = currentAnchorPos.relative(side);
                    if (mc.level.getBlockState(neighbor).canBeReplaced() &&
                            mc.level.getEntities(null, new AABB(neighbor)).isEmpty() &&
                            isSideShielding(side)) {
                        targetPlaceSide = side;
                        isSidePlacement = true;
                        isDiagonalPlacement = true;
                        return neighbor;
                    }
                }
            }

            if (Math.sqrt(dx * dx + dz * dz) < 1.5) {
                Direction[] sides = {xDir, zDir};
                for (Direction side : sides) {
                    BlockPos neighbor = currentAnchorPos.relative(side);
                    if (mc.level.getBlockState(neighbor).canBeReplaced() &&
                            mc.level.getEntities(null, new AABB(neighbor)).isEmpty() &&
                            isSideShielding(side)) {
                        targetPlaceSide = side;
                        isSidePlacement = true;
                        return neighbor;
                    }
                }

                if (isExplosionSafe()) {
                    prepareExplode();
                    return null;
                }
            }

            Direction bestDir;
            if (absX > absZ) {
                bestDir = dx > 0 ? Direction.EAST : Direction.WEST;
            } else {
                bestDir = dz > 0 ? Direction.SOUTH : Direction.NORTH;
            }

            BlockPos neighbor = currentAnchorPos.relative(bestDir);
            if (mc.level.getBlockState(neighbor).canBeReplaced() && mc.level.getEntities(null, new AABB(neighbor)).isEmpty() && isSideShielding(bestDir)) {
                targetPlaceSide = bestDir;
                isSidePlacement = true;
                return neighbor;
            }
        }

        Vec3 playerPos = mc.player.position();
        Vec3 anchorPosVec = currentAnchorPos.getCenter();

        for (double i = 0.3; i <= 0.7; i += 0.1) {
            Vec3 posVec = playerPos.lerp(anchorPosVec, i);
            BlockPos pos = BlockPos.containing(posVec);
            if (isValidPlacePos(pos)) {
                targetPlaceSide = Direction.UP;
                return pos;
            }
        }

        BlockPos frontPos = mc.player.blockPosition().relative(mc.player.getDirection());
        if (isValidPlacePos(frontPos)) {
            targetPlaceSide = Direction.UP;
            return frontPos;
        }

        return null;
    }

    private boolean isSideShielding(Direction side) {
        Vec3 a = Vec3.atCenterOf(currentAnchorPos);
        Vec3 p = mc.player.position();
        Vec3 b = Vec3.atCenterOf(currentAnchorPos.relative(side));
        double ax = a.x, az = a.z;
        double px = p.x, pz = p.z;
        double bx = b.x, bz = b.z;
        double vx = px - ax, vz = pz - az;
        double wx = bx - ax, wz = bz - az;
        double vv = vx * vx + vz * vz;
        if (vv < 1.0e-6) return false;
        double t = (wx * vx + wz * vz) / vv;
        if (t <= 0.0 || t >= 1.0) return false;
        double nx = wx - t * vx;
        double nz = wz - t * vz;
        double d2 = nx * nx + nz * nz;
        return d2 <= 0.36;
    }

    private boolean isValidPlacePos(BlockPos pos) {
        if (pos.equals(currentAnchorPos)) return false;

        AABB blockBox = new AABB(pos);
        if (!mc.level.getEntities(null, blockBox).isEmpty()) return false;

        return !mc.level.getBlockState(pos.below()).isAir() &&
                !mc.level.getBlockState(pos.below()).canBeReplaced() &&
                mc.level.getBlockState(pos).canBeReplaced();
    }

    private boolean isExplosionSafe() {
        float damage = DamageUtils.anchorDamage(mc.player, currentAnchorPos.getCenter(), DamageUtils.ArmorEnchantmentMode.PPBP);
        float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();

        return (health - damage) > minHealth.getValue().floatValue();
    }

    private void handleRotatingToPlace() {
        HitResult hit = getCrosshairHit();
        if (isSidePlacement && isLookingAtPlayerSide(hit)) {
            debugState("TickSkip", hit);
            targetRotation = null;
        }
        if (targetRotation == null || isLookingAt(targetActionPos)) {
            if (isSidePlacement) {
                if (targetPlaceSide != null && mc.player.getMainHandItem().is(Items.GLOWSTONE)) {
                    if (placeAt(targetActionPos, targetPlaceSide.getOpposite())) {
                        addRenderBox(targetActionPos);
                    }
                } else {
                    setShiftState(true);
                    interactBlock(currentAnchorPos, targetPlaceSide);
                    setShiftState(false);
                    addRenderBox(targetActionPos);
                }
            } else {
                interactBlock(targetActionPos.below(), Direction.UP);
                addRenderBox(targetActionPos);
            }

            prepareExplode();
        }
    }

    private void prepareExplode() {
        if (!autoExplode.getValue()) {
            resetState();
            return;
        }


        FindItemResult totem = InvUtils.find(stack -> stack.is(Items.TOTEM_OF_UNDYING), 0, 8);
        if (totem.found()) {
            InvUtils.swap(totem.slot(), false);
        } else {

            FindItemResult safeItem = InvUtils.find(stack -> !stack.is(Items.RESPAWN_ANCHOR) && !stack.is(Items.GLOWSTONE), 0, 8);
            if (safeItem.found()) {
                InvUtils.swap(safeItem.slot(), false);
            }
        }

        targetActionPos = currentAnchorPos;
        currentRotationSpeed = explodeRotationSpeed.getValue();
        targetRotation = explodeNoRotate ? null : getTargetRotation(Vec3.atCenterOf(currentAnchorPos));
        stage = Stage.RotToExplode;
    }

    private void handleRotatingToExplode() {
        if (targetRotation == null || isLookingAt(targetActionPos)) {

            interactBlock(targetActionPos, Direction.UP);

            resetState();
        }
    }

    private boolean isLookingAt(BlockPos pos) {
        HitResult hit = getCrosshairHit();
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;
            BlockPos hitPos = blockHit.getBlockPos();

            if (isSidePlacement && currentAnchorPos != null) {
                return hitPos.equals(currentAnchorPos) && blockHit.getDirection() != Direction.UP;
            }

            return hitPos.equals(pos) || hitPos.equals(pos.below());
        }
        return false;
    }

    private HitResult getCrosshairHit() {
        if (mc.player == null) return null;
        double reach = mc.player.blockInteractionRange();
        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        return mc.player.pick(reach, tickDelta, false);
    }

    private void debugState(String tag, HitResult hit) {
        if (!debug.getValue()) return;
        if (mc.player == null) return;

        long now = System.currentTimeMillis();
        if (now - lastDebugMs < 250L) return;
        lastDebugMs = now;
        debugSeq++;

        String hitInfo;
        if (hit == null) {
            hitInfo = "hit=null";
        } else if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) hit;
            hitInfo = "hit=BLOCK pos=" + bhr.getBlockPos().toShortString() + " side=" + bhr.getDirection();
        } else {
            hitInfo = "hit=" + hit.getType();
        }

        String cur = currentAnchorPos == null ? "null" : currentAnchorPos.toShortString();
        String act = targetActionPos == null ? "null" : targetActionPos.toShortString();
        String side = targetPlaceSide == null ? "null" : targetPlaceSide.getName();
        Direction playerSide = getPlayerSideOfAnchor();
        String pside = playerSide == null ? "null" : playerSide.getName();
        boolean look = isLookingAtPlayerSide(hit);
        String rot = targetRotation == null ? "null" : String.format("%.2f/%.2f", targetRotation.x, targetRotation.y);
        String yawPitch = String.format("%.2f/%.2f", mc.player.getYRot(), mc.player.getXRot());

        String msg = "§7[SafeAnchor/Debug] §f" + tag + "#" + debugSeq
                + " st=" + stage
                + " sidePlace=" + isSidePlacement
                + " cur=" + cur
                + " act=" + act
                + " side=" + side
                + " pside=" + pside
                + " look=" + look
                + " rot=" + rot
                + " yp=" + yawPitch
                + " " + hitInfo;

        ChatUtils.addChatMessage(msg);
    }

    private boolean isLookingAtPlayerSide(HitResult hit) {
        if (!isSidePlacement) return false;
        if (currentAnchorPos == null || mc.player == null) return false;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;

        Direction playerSide = getPlayerSideOfAnchor();
        if (playerSide == null) return false;

        BlockHitResult bhr = (BlockHitResult) hit;
        BlockPos hitPos = bhr.getBlockPos();
        Direction hitSide = bhr.getDirection();
        if (hitPos.equals(currentAnchorPos) && hitSide == playerSide) return true;

        BlockPos placePos = currentAnchorPos.relative(playerSide);
        return hitPos.equals(placePos) && (hitSide == playerSide || hitSide == playerSide.getOpposite());
    }

    private Direction getPlayerSideOfAnchor() {
        if (currentAnchorPos == null) return null;
        double dx = mc.player.getX() - (currentAnchorPos.getX() + 0.5);
        double dz = mc.player.getZ() - (currentAnchorPos.getZ() + 0.5);
        double absX = Math.abs(dx);
        double absZ = Math.abs(dz);
        if (absX >= absZ) {
            return dx >= 0.0 ? Direction.EAST : Direction.WEST;
        } else {
            return dz >= 0.0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private void interactBlock(BlockPos pos, Direction side) {
        Vec3 hitVec = new Vec3(pos.getX() + 0.5 + side.getStepX() * 0.45,
                pos.getY() + 0.5 + side.getStepY() * 0.45,
                pos.getZ() + 0.5 + side.getStepZ() * 0.45);
        BlockHitResult bhr = new BlockHitResult(hitVec, side, pos, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void addRenderBox(BlockPos pos) {
        renderBoxes.add(new RenderBox(new AABB(pos), glowStoneLine.getValue(), glowStoneSide.getValue(), System.currentTimeMillis()));
    }

    private void resetState() {
        stage = Stage.None;
        currentAnchorPos = null;
        targetRotation = null;
        targetActionPos = null;
        targetPlaceSide = null;
        isSidePlacement = false;
        isDiagonalPlacement = false;
        delay = 2;
        explodeNoRotate = false;
    }

    private record RenderBox(AABB aabb, Color lineColor, Color sideColor, long startTime) {
    }

}
