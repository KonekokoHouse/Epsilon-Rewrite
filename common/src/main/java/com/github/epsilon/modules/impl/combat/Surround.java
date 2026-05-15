package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.TickEvent;
import com.github.epsilon.managers.RotationManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.managers.HotbarManager;
import com.github.epsilon.managers.HotbarManager.SwapMode;
import com.github.epsilon.utils.player.MoveUtils;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.RaytraceUtils;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import com.github.epsilon.utils.world.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Surround extends Module {

    public static final Surround INSTANCE = new Surround();

    private Surround() {
        super("Surround", Category.COMBAT);
    }

    private final EnumSetting<SwapMode> swapMode = enumSetting("Swap Mode", SwapMode.Silent);
    private final IntSetting placeDelay = intSetting("Place Delay", 50, 0, 1000, 1);
    private final IntSetting blocksPerTick = intSetting("Blocks Per Tick", 1, 1, 5, 1);
    private final BoolSetting groundCheck = boolSetting("Ground Check", true);
    private final BoolSetting autoCenter = boolSetting("Auto Center", true);
    private final BoolSetting rotate = boolSetting("Rotate", false);
    private final BoolSetting attackCrystal = boolSetting("Attack Crystal", true);
    private final BoolSetting disableOnMove = boolSetting("Disable On Move", true);
    private final BoolSetting useEnderChest = boolSetting("Use Ender Chest", true);
    private final BoolSetting onlyStatic = boolSetting("Only Static", true);

    private BlockPos anchorPos;
    private final TimerUtils placeTimer = new TimerUtils();

    private static final BlockPos[] SURROUND_OFFSETS = {
            new BlockPos(0, -1, 0),
            new BlockPos(0, 0, -1),
            new BlockPos(1, 0, 0),
            new BlockPos(0, 0, 1),
            new BlockPos(-1, 0, 0)
    };

    @Override
    protected void onEnable() {
        placeTimer.setMs(placeDelay.getValue().longValue());
        anchorPos = nullCheck() ? null : mc.player.blockPosition();
        if (!nullCheck() && autoCenter.getValue()) {
            centerPlayer();
        }
    }

    @Override
    protected void onDisable() {
        anchorPos = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;
        if (groundCheck.getValue() && !mc.player.onGround()) return;

        BlockPos currentPos = mc.player.blockPosition();
        if (anchorPos == null) {
            anchorPos = currentPos;
        }

        if (disableOnMove.getValue() && !currentPos.equals(anchorPos) && MoveUtils.isMoving()) {
            toggle();
            return;
        }

        if (!MoveUtils.isMoving()) {
            anchorPos = currentPos;
        }

        if (!placeTimer.passedMillise(placeDelay.getValue())) return;

        boolean isMoving = MoveUtils.isMoving() || mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();

        if (onlyStatic.getValue() && isMoving && swapMode.is(SwapMode.InvSwitch)) {
            return;
        }

        FindItemResult itemResult = HotbarManager.INSTANCE.find(swapMode.getValue(), this::isValidBlock);
        if (!itemResult.found()) return;

        int placedCount = 0;
        int maxPlace = blocksPerTick.getValue();

        for (BlockPos offset : SURROUND_OFFSETS) {
            if (placedCount >= maxPlace) break;

            BlockPos targetPos = anchorPos.offset(offset);
            if (!mc.level.getBlockState(targetPos).canBeReplaced()) continue;

            if (attackCrystal.getValue()) {
                attackCrystalAt(targetPos);
            }

            if (!BlockUtils.canPlaceAt(targetPos)) continue;

            PlaceInfo placeInfo = findBestPlacement(targetPos, anchorPos);
            if (placeInfo == null) continue;

            executePlacement(placeInfo, itemResult);
            placedCount++;
        }
    }

    private void centerPlayer() {
        double centeredX = Math.floor(mc.player.getX()) + 0.5;
        double centeredZ = Math.floor(mc.player.getZ()) + 0.5;
        mc.player.setPos(centeredX, mc.player.getY(), centeredZ);
        anchorPos = mc.player.blockPosition();
    }

    private boolean isValidBlock(ItemStack itemStack) {
        Item item = itemStack.getItem();
        return item == Items.OBSIDIAN || (useEnderChest.getValue() && item == Items.ENDER_CHEST);
    }

    private void attackCrystalAt(BlockPos pos) {
        for (Entity entity : mc.level.getEntities(null, new AABB(pos))) {
            if (entity instanceof EndCrystal crystal && crystal.isAlive()) {
                mc.gameMode.attack(mc.player, crystal);
                mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
                return;
            }
        }
    }

    private PlaceInfo findBestPlacement(BlockPos targetPos, BlockPos origin) {
        List<PlaceInfo> candidates = new ArrayList<>();

        for (Direction side : Direction.values()) {
            BlockPos neighborPos = targetPos.relative(side);
            if (mc.level.getBlockState(neighborPos).canBeReplaced()) continue;

            Direction hitSide = side.getOpposite();
            Vec3 hitVec = new Vec3(
                    neighborPos.getX() + 0.5 + hitSide.getStepX() * 0.5,
                    neighborPos.getY() + 0.5 + hitSide.getStepY() * 0.5,
                    neighborPos.getZ() + 0.5 + hitSide.getStepZ() * 0.5
            );

            if (mc.player.getEyePosition().distanceToSqr(hitVec) > 18.0) continue;

            Vector2f rotation = RotationUtils.calculate(neighborPos, hitSide);
            Vector2f reverseYaw = new Vector2f(Mth.wrapDegrees(mc.player.getYRot() - 180.0f), rotation.y);
            if (RaytraceUtils.overBlock(reverseYaw, neighborPos, hitSide, false)) {
                rotation = reverseYaw;
            }

            int distance = origin.distManhattan(neighborPos);
            candidates.add(new PlaceInfo(targetPos, neighborPos, hitSide, hitVec, rotation, distance));
        }

        if (candidates.isEmpty()) return null;

        candidates.sort(Comparator.comparingInt(PlaceInfo::distance));
        return candidates.getFirst();
    }

    private void executePlacement(PlaceInfo info, FindItemResult itemResult) {
        if (!rotate.getValue()) {
            doPlaceBlock(info, itemResult);
            return;
        }

        RotationManager.INSTANCE.applyRotation(info.rotation(), 10, Priority.High.priority, record -> {
            if (!RaytraceUtils.overBlock(record.currentRotation(), info.neighborPos(), info.side(), false)) return;

            doPlaceBlock(info, itemResult);
        });
    }

    private void doPlaceBlock(PlaceInfo info, FindItemResult itemResult) {
        if (!mc.level.getBlockState(info.targetPos()).canBeReplaced()) return;

        if (attackCrystal.getValue()) {
            attackCrystalAt(info.targetPos());
            if (!BlockUtils.canPlaceAt(info.targetPos())) return;
        }

        InteractionHand hand = itemResult.getHand();
        if (hand == InteractionHand.MAIN_HAND) {
            HotbarManager.INSTANCE.swap(swapMode.getValue(), itemResult);
        }

        BlockHitResult hitResult = new BlockHitResult(info.hitVec(), info.side(), info.neighborPos(), false);
        InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, hitResult);
        if (result.consumesAction()) {
            mc.getConnection().send(new ServerboundSwingPacket(hand));
            placeTimer.reset();
        }

    }

    private record PlaceInfo(
            BlockPos targetPos,
            BlockPos neighborPos,
            Direction side,
            Vec3 hitVec,
            Vector2f rotation,
            int distance
    ) {
    }
}
