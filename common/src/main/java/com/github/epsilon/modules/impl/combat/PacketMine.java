package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.AttackBlockEvent;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.events.impl.TickEvent;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.network.PacketUtils;
import com.github.epsilon.utils.player.EnchantmentUtils;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.render.Render3DUtils;
import com.github.epsilon.utils.render.WorldToScreen;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import com.github.epsilon.utils.world.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Vector3f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TimerTask;

public class PacketMine extends Module {

    public static final PacketMine INSTANCE = new PacketMine();

    private PacketMine() {
        super("Packet Mine", Category.COMBAT);
    }

    private enum MineSwitchMode {
        None,
        Delay,
        Silent
    }

    private final BoolSetting usingPause = boolSetting("Pause On Use", true);
    private final BoolSetting onlyMain = boolSetting("Only Main", true, usingPause::getValue);
    private final EnumSetting<MineSwitchMode> autoSwitch = enumSetting("Auto Switch", MineSwitchMode.Silent);
    private final IntSetting range = intSetting("Range", 6, 0, 12, 1);
    private final IntSetting maxBreaks = intSetting("Try Break Time", 6, 0, 10, 1);
    private final BoolSetting farCancel = boolSetting("Far Cancel", true);
    private final BoolSetting swing = boolSetting("Swing Hand", true);
    private final BoolSetting instantMine = boolSetting("Instant Mine", true);
    private final IntSetting instantDelay = intSetting("Instant Delay", 10, 0, 1000, 10);
    private final BoolSetting fastBypass = boolSetting("Fast Bypass", true);
    private final BoolSetting doubleBreak = boolSetting("Double Break", false);
    private final BoolSetting checkGround = boolSetting("Check Ground", true);
    private final BoolSetting bypassGround = boolSetting("Bypass Ground", false);
    private final IntSetting switchDamage = intSetting("Switch Damage", 95, 0, 100, 1);
    private final IntSetting switchTime = intSetting("Switch Time", 100, 0, 1000, 10);
    private final IntSetting mineDelay = intSetting("Mine Delay", 300, 0, 1000, 10);
    private final IntSetting packetDelay = intSetting("Packet Delay", 200, 0, 1000, 10);
    private final DoubleSetting mineDamage = doubleSetting("Damage", 0.8, 0.0, 2.0, 0.05);

    private final DoubleSetting animationExp = doubleSetting("Animation Exponent", 3.0, 0.0, 10.0, 1.0);
    private final BoolSetting renderProgress = boolSetting("Render Progress", true);
    private final ColorSetting targetColor = colorSetting("Target Color", new Color(255, 255, 255, 50));
    private final ColorSetting secondColor = colorSetting("Second Color", new Color(255, 255, 255, 50));
    private final ColorSetting sideStartColor = colorSetting("Side Start", new Color(255, 255, 255, 0));
    private final ColorSetting sideEndColor = colorSetting("Side End", new Color(255, 255, 255, 50));
    private final ColorSetting lineStartColor = colorSetting("Line Start", new Color(255, 255, 255, 0));
    private final ColorSetting lineEndColor = colorSetting("Line End", new Color(255, 255, 255, 255));
    private final ColorSetting secondSideStartColor = colorSetting("Second Side Start", new Color(255, 255, 255, 0));
    private final ColorSetting secondSideEndColor = colorSetting("Second Side End", new Color(255, 255, 255, 50));
    private final ColorSetting secondLineStartColor = colorSetting("Second Line Start", new Color(255, 255, 255, 0));
    private final ColorSetting secondLineEndColor = colorSetting("Second Line End", new Color(255, 255, 255, 255));

    public static BlockPos selfClickPos = null;
    public static int maxBreaksCount;
    public static int publicProgress = 0, secondPublicProgress = 0;
    public static boolean completed = false;
    public static BlockPos targetPos,secondPos;
    private static float progress,secondProgress;
    private long lastTime,secondLastTime;
    private static boolean started, secondStarted;
    private double render = 1, secondRender = 1;
    private int oldSlot = -1;
    private final TimerUtils bypassTimer = new TimerUtils();
    private final TimerUtils timer = new TimerUtils();
    private final TimerUtils secondTimer = new TimerUtils();
    public final TimerUtils mineTimer = new TimerUtils();
    private final TimerUtils instantTimer = new TimerUtils();
    private boolean hasSwitch = false, secondHasSwitch = false;

    @Override
    protected void onEnable() {
        maxBreaksCount = 0;
        hasSwitch = false;
        secondHasSwitch = false;
        bypassTimer.setMs(999999);
        mineTimer.setMs(999999);
        instantTimer.setMs(999999);
        timer.setMs(999999);
        secondTimer.setMs(999999);
        targetPos = null;
        secondPos = null;
        started = false;
        secondStarted = false;
        publicProgress = 0;
        secondPublicProgress = 0;
        progress = 0;
        secondProgress = 0;
        lastTime = System.currentTimeMillis();
        secondLastTime = System.currentTimeMillis();
        render = 1;
    }

    @Override
    protected void onDisable() {
        if (hasSwitch) {
            InvUtils.swap(oldSlot, false);
            hasSwitch = false;
        }
        if (secondHasSwitch) {
            InvUtils.swap(oldSlot,false);
            secondHasSwitch = false;
        }
    }

    @EventHandler
    private void onStartBreakingBlock(AttackBlockEvent event) {
        if (!canBreak(event.getBlockPos())) return;
        event.setCancelled(true);
        if (!mineTimer.passedMillise(mineDelay.getValue())) return;
        selfClickPos = event.getBlockPos();
        mine(event.getBlockPos());
    }
    public void mine(BlockPos pos) {
        mineTimer.reset();
        maxBreaksCount = 0;
        if (doubleBreak.getValue()) {
            if (targetPos != null && secondPos == null && !targetPos.equals(pos)) {
                if (completed) {
                    targetPos = pos;
                    secondStarted = false;
                    secondProgress = 0;
                    secondPublicProgress = 0;
                    publicProgress = 0;
                    started = false;
                    progress = 0;
                    completed = false;
                } else {
                    secondPos = targetPos;
                    targetPos = pos;
                    secondStarted = false;
                    secondProgress = 0;
                    secondPublicProgress = 0;
                    started = false;
                }
            } else if (targetPos == null || !targetPos.equals(pos)){
                publicProgress = 0;
                targetPos = pos;
                started = false;
                progress = 0;
                completed = false;
            }
        } else {
            if (!pos.equals(targetPos)) {
                publicProgress = 0;
                targetPos = pos;
                started = false;
                progress = 0;
                completed = false;
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (targetPos == null && secondPos == null) selfClickPos = null;
        if (publicProgress >= 100) {
            if (!instantMine.getValue()) targetPos = null;
        }
        if (secondPublicProgress >= 100) {
            secondPos = null;
        }
        if (timer.passedMillise(switchTime.getValue()) && hasSwitch && autoSwitch.getValue() != MineSwitchMode.None) {
            if (autoSwitch.getValue() == MineSwitchMode.Delay) InvUtils.swap(oldSlot, false);
            if (autoSwitch.getValue() == MineSwitchMode.Silent) mc.getConnection().send(new ServerboundSetCarriedItemPacket(oldSlot));
            hasSwitch = false;
        }
        if (maxBreaksCount >= maxBreaks.getValue() * 10) {
            maxBreaksCount = 0;
            targetPos = null;
        }
        if (secondPos != null && doubleBreak.getValue()) {
            if (farCancel.getValue() && Math.sqrt(mc.player.getEyePosition().distanceToSqr(secondPos.getCenter())) > range.getValue()){
                secondPos = null;
                return;
            }
            double secondMax = getMineTicks2(getTool(secondPos));
            double secondDelta = (System.currentTimeMillis() - secondLastTime) / 1000d;
            secondPublicProgress = (int) (secondProgress / (secondMax * mineDamage.getValue()) * 100);
            secondLastTime = System.currentTimeMillis();
            if (!secondStarted) {
                sendStart(secondPos);
                secondStarted = true;
                secondProgress = 0;
                return;
            }
            Double secondDamage = mineDamage.getValue();
            if (!checkGround.getValue() || mc.player.onGround()) {
                secondProgress += (float) (secondDelta * 20);
            } else if (checkGround.getValue() && !mc.player.onGround()){
                secondProgress += (float) (secondDelta * 4);
            }
            renderSecondAnimation(event, secondDelta, secondDamage);
            if (secondProgress >= secondMax * secondDamage) {
                sendStopSecond();
            }
        }
        if (doubleBreak.getValue()) {
            if (!usingPause.getValue() || !checkPause(onlyMain.getValue())) {
                if ((secondPublicProgress >= switchDamage.getValue() || publicProgress >= switchDamage.getValue())&& !hasSwitch && secondPos != null) {
                    int bestSlot = getTool(secondPos);
                    if (!hasSwitch) oldSlot = mc.player.getInventory().getSelectedSlot();
                    if (!autoSwitch.is(MineSwitchMode.None) && bestSlot != -1) {
                        if (autoSwitch.getValue() == MineSwitchMode.Delay) InvUtils.swap(bestSlot,false);
                        if (autoSwitch.getValue() == MineSwitchMode.Silent) mc.getConnection().send(new ServerboundSetCarriedItemPacket(bestSlot));
                        timer.reset();
                        hasSwitch = true;
                    }
                }
            }
        }
        if (targetPos != null) {
            if (farCancel.getValue() && Math.sqrt(mc.player.getEyePosition().distanceToSqr(targetPos.getCenter())) > range.getValue()){
                targetPos = null;
                return;
            }
            double max = getMineTicks(getTool(targetPos));
            publicProgress = (int) (progress / (max * mineDamage.getValue()) * 100);
            if (progress >= max * mineDamage.getValue() && completed) {
                if (isAir(targetPos) || mc.level.getBlockState(targetPos).canBeReplaced()) maxBreaksCount = 0;
                if (!isAir(targetPos) && !mc.level.getBlockState(targetPos).canBeReplaced() && !(usingPause.getValue() && checkPause(onlyMain.getValue())))
                    maxBreaksCount++;
            }
            if (instantMine.getValue() && completed) {
                Color side = getColor(sideStartColor.getValue(), sideEndColor.getValue(), 1);
                Color line = getColor(lineStartColor.getValue(), lineEndColor.getValue(), 1);
                Render3DUtils.drawFilledBox(new AABB(targetPos), side);
                if (!mc.level.getBlockState(targetPos).isAir() && !mc.level.getBlockState(targetPos).canBeReplaced() && instantTimer.passedMillise(instantDelay.getValue())) {
                    sendStop();
                    instantTimer.reset();
                }
                return;
            }
            double delta = (System.currentTimeMillis() - lastTime) / 1000d;
            lastTime = System.currentTimeMillis();
            if (!started) {
                sendStart(targetPos);
                return;
            }
            Double damage = mineDamage.getValue();
            if (!checkGround.getValue() || mc.player.onGround()) {
                progress += delta * 20;
            } else if (checkGround.getValue() && !mc.player.onGround()) {
                progress += delta * 4;
            }
            renderAnimation(event, delta, damage);
            if (progress >= max * damage) {
                sendStop();
                completed = true;
                if (!instantMine.getValue() && secondPos == null) targetPos = null;
            }
        }
    }

    private void sendStart(BlockPos pos) {
        mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, RotationUtils.getClickSide(pos)));
        if (fastBypass.getValue()) {
            BlockPos bypassPos = BlockPos.containing(mc.player.getX(), 321, mc.player.getZ());
            mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, bypassPos, Direction.DOWN, mc.level.getBlockStatePredictionHandler().startPredicting().currentSequence()));
        }
        if (doubleBreak.getValue()) {
            long delay = packetDelay.getValue();
            java.util.Timer timer = new java.util.Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    mc.execute(() -> {
                        mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, RotationUtils.getClickSide(pos)));
                    });
                    timer.cancel();
                }
            }, delay);
        }
        mc.player.swing(InteractionHand.MAIN_HAND);
        if (pos.equals(targetPos)) {
            started = true;
            progress = 0;
        } else {
            secondStarted = true;
            secondProgress = 0;
        }
    }

    private void sendStop() {
        if (usingPause.getValue() && checkPause(onlyMain.getValue())) {
            return;
        }
        if (!doubleBreak.getValue() || secondPos == null) {
            int bestSlot = getTool(targetPos);
            if (!hasSwitch) oldSlot = mc.player.getInventory().getSelectedSlot();
            if (autoSwitch.getValue() != MineSwitchMode.None && bestSlot != -1) {
                if (autoSwitch.is(MineSwitchMode.Delay)) {
                    InvUtils.swap(bestSlot, false);
                }
                if (autoSwitch.is(MineSwitchMode.Silent)) {
                    mc.getConnection().send(new ServerboundSetCarriedItemPacket(bestSlot));
                }
                timer.reset();
                hasSwitch = true;
            }
        }
        if (bypassGround.getValue() && !mc.player.isFallFlying() && targetPos != null && !isAir(targetPos) && !mc.player.onGround()){
            mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), mc.player.getY() + 1.0e-9, mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot(), true, mc.player.horizontalCollision));
            mc.player.respawn();
        }
        if (swing.getValue()) mc.player.swing(InteractionHand.MAIN_HAND);
        mc.getConnection().send( new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, targetPos, RotationUtils.getClickSide(targetPos), mc.level.getBlockStatePredictionHandler().startPredicting().currentSequence()));
    }
    private void sendStopSecond() {
        if (bypassGround.getValue() && !mc.player.isFallFlying() && secondPos != null && !isAir(secondPos) && !mc.player.onGround()){
            mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), mc.player.getY() + 1.0e-9, mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot(), true, mc.player.horizontalCollision));
            mc.player.resetFallDistance();
        }
        if (swing.getValue()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }
    private boolean isAir(BlockPos breakPos) {
        return mc.level.getBlockState(breakPos).isAir() || mc.level.getBlockState(breakPos).getBlock() == Blocks.FIRE && hasCrystal(breakPos);
    }

    private boolean hasCrystal(BlockPos pos) {
        for (Entity entity : mc.level.getEntities(null, new AABB(pos))) {
            if (entity instanceof EndCrystal endCrystal && endCrystal.isAlive()) {
                return true;
            }
        }
        return false;
    }

    private float getMineTicks(int slot) {
        if (targetPos == null) return 20;
        BlockState state = mc.level.getBlockState(targetPos);
        float hardness = state.getDestroySpeed(mc.level, targetPos);
        if (hardness < 0) return Float.MAX_VALUE;
        if (hardness == 0) return 1;
        ItemStack stack = slot == -1
                ? ItemStack.EMPTY
                : mc.player.getInventory().getItem(slot);
        boolean canHarvest = stack.isCorrectToolForDrops(state);
        float speed = stack.getDestroySpeed(state);
        int efficiency = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.EFFICIENCY);
        if (efficiency > 0 && speed > 1.0f) {
            speed += efficiency * efficiency + 1;
        }
        if (mc.player.hasEffect(MobEffects.HASTE)) {
            int amp = mc.player.getEffect(MobEffects.HASTE).getAmplifier();
            speed *= 1.0f + (amp + 1) * 0.2f;
        }
        if (mc.player.hasEffect(MobEffects.MINING_FATIGUE)) {
            int amp = mc.player.getEffect(MobEffects.MINING_FATIGUE).getAmplifier();
            speed *= switch (amp) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                case 2 -> 0.0027f;
                default -> 0.00081f;
            };
        }
        float damage = speed / hardness / (canHarvest ? 30f : 100f);
        if (damage <= 0) return Float.MAX_VALUE;
        return 1f / damage;
    }
    private float getMineTicks2(int slot) {
        if (secondPos == null) return 20;
        BlockState state = mc.level.getBlockState(secondPos);
        float hardness = state.getDestroySpeed(mc.level, secondPos);
        if (hardness < 0) return Float.MAX_VALUE;
        if (hardness == 0) return 1;
        ItemStack stack = slot == -1
                ? ItemStack.EMPTY
                : mc.player.getInventory().getItem(slot);
        boolean canHarvest = stack.isCorrectToolForDrops(state);
        float speed = stack.getDestroySpeed(state);
        int efficiency = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.EFFICIENCY);
        if (efficiency > 0 && speed > 1.0f) {
            speed += efficiency * efficiency + 1;
        }
        if (mc.player.hasEffect(MobEffects.HASTE)) {
            int amp = mc.player.getEffect(MobEffects.HASTE).getAmplifier();
            speed *= 1.0f + (amp + 1) * 0.2f;
        }
        if (mc.player.hasEffect(MobEffects.MINING_FATIGUE)) {
            int amp = mc.player.getEffect(MobEffects.MINING_FATIGUE).getAmplifier();
            speed *= switch (amp) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                case 2 -> 0.0027f;
                default -> 0.00081f;
            };
        }
        float damage = speed / hardness / (canHarvest ? 30f : 100f);
        if (damage <= 0) return Float.MAX_VALUE;
        return 1f / damage;
    }

    private void renderAnimation(Render3DEvent event, double delta, double damage) {
        render = Mth.clamp(render + delta * 2, -2, 2);
        double max = getMineTicks(getTool(targetPos));
        double p = 1 - Mth.clamp(progress / (max * damage), 0, 1);
        p = Math.pow(p, animationExp.getValue());
        p = 1 - p;
        double size = p / 2;
        AABB box = new AABB(
                targetPos.getX() + 0.5 - size,
                targetPos.getY() + 0.5 - size,
                targetPos.getZ() + 0.5 - size,
                targetPos.getX() + 0.5 + size,
                targetPos.getY() + 0.5 + size,
                targetPos.getZ() + 0.5 + size
        );

        Color side = getColor(sideStartColor.getValue(), sideEndColor.getValue(), p);
        Color line = getColor(lineStartColor.getValue(), lineEndColor.getValue(), p);

        Render3DUtils.drawFilledBox(box, side);
    }
    private void renderSecondAnimation(Render3DEvent event, double delta, double damage) {
        secondRender = Mth.clamp(secondRender + delta * 2, -2, 2);
        double max = getMineTicks2(getTool(secondPos));
        double p = 1 - Mth.clamp(secondProgress / (max * damage), 0, 1);
        p = Math.pow(p, animationExp.getValue());
        p = 1 - p;

        double size = p / 2;
        AABB box = new AABB(
                secondPos.getX() + 0.5 - size,
                secondPos.getY() + 0.5 - size,
                secondPos.getZ() + 0.5 - size,
                secondPos.getX() + 0.5 + size,
                secondPos.getY() + 0.5 + size,
                secondPos.getZ() + 0.5 + size
        );

        Color side = getColor(secondSideStartColor.getValue(), secondSideEndColor.getValue(), p);
        Color line = getColor(secondLineStartColor.getValue(), secondLineEndColor.getValue(), p);

        Render3DUtils.drawFilledBox(box, side);
    }

    private Color getColor(Color start, Color end, double progress) {
        return new Color(
                lerp(start.getRed(), end.getRed(), progress),
                lerp(start.getGreen(), end.getGreen(), progress),
                lerp(start.getBlue(), end.getBlue(), progress),
                lerp(start.getAlpha(), end.getAlpha(), progress)
        );
    }

    private int lerp(double start, double end, double d) {
        return (int) Math.round(start + (end - start) * d);
    }

    private int getTool(BlockPos pos) {
        int index = -1;
        float CurrentFastest = 1.0f;
        for (int i = 0; i < 9; ++i) {
            final ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack != ItemStack.EMPTY) {
                final float digSpeed = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.EFFICIENCY);
                final float destroySpeed = stack.getDestroySpeed(mc.level.getBlockState(pos));
                if (digSpeed + destroySpeed > CurrentFastest) {
                    CurrentFastest = digSpeed + destroySpeed;
                    index = i;
                }
            }
        }
        return index;
    }

    public boolean checkPause(boolean onlyMain) {
        return mc.options.keyUse.isDown() && (!onlyMain || mc.player.getUsedItemHand() == InteractionHand.MAIN_HAND);
    }

    public boolean canBreak(BlockPos blockPos) {
        BlockState state = mc.level.getBlockState(blockPos);
        if (!mc.player.isCreative() && state.getDestroySpeed(mc.level, blockPos) < 0) return false;
        return state.getCollisionShape(mc.level, blockPos) != Shapes.empty();
    }

}
