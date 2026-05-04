package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.AttackBlockEvent;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.events.impl.TickEvent;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.network.PacketUtils;
import com.github.epsilon.utils.player.EnchantmentUtils;
import com.github.epsilon.utils.render.Render3DUtils;
import com.github.epsilon.utils.render.WorldToScreen;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.timer.TimerUtils;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PacketMine extends Module {

    public static final PacketMine INSTANCE = new PacketMine();

    private PacketMine() {
        super("Packet Mine", Category.COMBAT);
    }

    private enum SwitchMode {
        None,
        Silent,
        Normal,
        Alternative
    }

    private final BoolSetting usingPause = boolSetting("Using Pause", true);
    private final BoolSetting onlyMain = boolSetting("Only Main", true, usingPause::getValue);
    private final EnumSetting<SwitchMode> autoSwitch = enumSetting("Auto Switch", SwitchMode.Silent);
    private final DoubleSetting range = doubleSetting("Range", 6.0, 0.0, 12.0, 0.1);
    private final IntSetting maxBreaks = intSetting("Try Break Time", 6, 1, 20, 1);
    private final BoolSetting farCancel = boolSetting("Far Cancel", true);
    private final BoolSetting swing = boolSetting("Swing Hand", true);
    private final BoolSetting instantMine = boolSetting("Instant Mine", true);
    private final IntSetting instantDelay = intSetting("Instant Delay", 10, 0, 1000, 1);
    private final BoolSetting fastBypass = boolSetting("Fast Bypass", true);
    private final BoolSetting doubleBreak = boolSetting("Double Break", false);
    private final BoolSetting checkGround = boolSetting("Check Ground", true);
    private final BoolSetting bypassGround = boolSetting("Bypass Ground", false);
    private final IntSetting switchDamage = intSetting("Switch Damage", 95, 0, 100, 1, () -> autoSwitch.getValue() != SwitchMode.None, true);
    private final IntSetting switchTime = intSetting("Switch Time", 100, 0, 1000, 1, () -> autoSwitch.getValue() != SwitchMode.None);
    private final IntSetting mineDelay = intSetting("Mine Delay", 300, 0, 1000, 1);
    private final IntSetting packetDelay = intSetting("Packet Delay", 200, 0, 1000, 1);
    private final DoubleSetting mineDamage = doubleSetting("Damage", 0.8, 0.1, 2.0, 0.05);
    private final BoolSetting clientRemove = boolSetting("Client Remove", false);

    private final BoolSetting render = boolSetting("Render", true);
    private final DoubleSetting animationExp = doubleSetting("Animation Exponent", 3.0, 0.0, 10.0, 0.1, render::getValue);
    private final BoolSetting renderProgress = boolSetting("Render Progress", true);
    private final ColorSetting targetColor = colorSetting("Target Color", new Color(255, 255, 255, 255), renderProgress::getValue);
    private final ColorSetting secondColor = colorSetting("Second Color", new Color(255, 255, 255, 255), renderProgress::getValue);
    private final ColorSetting sideStartColor = colorSetting("Side Start", new Color(255, 255, 255, 0), render::getValue);
    private final ColorSetting sideEndColor = colorSetting("Side End", new Color(255, 255, 255, 50), render::getValue);
    private final ColorSetting lineStartColor = colorSetting("Line Start", new Color(255, 255, 255, 0), render::getValue);
    private final ColorSetting lineEndColor = colorSetting("Line End", new Color(255, 255, 255, 255), render::getValue);
    private final ColorSetting secondSideStartColor = colorSetting("Second Side Start", new Color(255, 255, 255, 0), render::getValue);
    private final ColorSetting secondSideEndColor = colorSetting("Second Side End", new Color(255, 255, 255, 50), render::getValue);
    private final ColorSetting secondLineStartColor = colorSetting("Second Line Start", new Color(255, 255, 255, 0), render::getValue);
    private final ColorSetting secondLineEndColor = colorSetting("Second Line End", new Color(255, 255, 255, 255), render::getValue);

    private final List<MineAction> actions = new ArrayList<>();
    private final List<DelayedAction> delayedActions = new ArrayList<>();
    private final TimerUtils mineTimer = new TimerUtils();
    private final TextRenderer textRenderer = new TextRenderer();

    private SwapState swapState;

    @Override
    protected void onEnable() {
        resetState(false);
        mineTimer.setMs(mineDelay.getValue().longValue());
    }

    @Override
    protected void onDisable() {
        resetState(true);
        textRenderer.clear();
    }

    @EventHandler
    private void onAttackBlock(AttackBlockEvent event) {
        if (nullCheck()) return;

        var player = mc.player;
        if (player == null || player.getAbilities().instabuild) return;
        if (!canMine(event.getBlockPos())) return;

        event.setCancelled(true);

        if (!mineTimer.passedMillise(mineDelay.getValue())) return;

        mineTimer.reset();
        beginMining(event.getBlockPos(), event.getDirection());
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (nullCheck()) {
            resetState(false);
            return;
        }

        var player = mc.player;
        if (player == null || player.getAbilities().instabuild) {
            resetState(false);
            return;
        }

        long now = System.currentTimeMillis();
        runDelayedActions(now);
        handleSwapRestore(now);

        for (int i = actions.size() - 1; i >= 0; i--) {
            MineAction action = actions.get(i);
            if (updateAction(action, now)) {
                actions.remove(i);
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (nullCheck() || !render.getValue() || actions.isEmpty()) return;

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        for (int i = 0; i < actions.size(); i++) {
            MineAction action = actions.get(i);
            float progress = Mth.lerp(partialTick, action.prevRenderProgress, action.renderProgress);
            progress = Mth.clamp(progress, 0.0f, 1.0f);
            double eased = 1.0 - Math.pow(1.0 - progress, animationExp.getValue());
            double size = Mth.clamp((float) eased, 0.0f, 1.0f) * 0.5;

            AABB box = new AABB(
                    action.pos.getX() + 0.5 - size,
                    action.pos.getY() + 0.5 - size,
                    action.pos.getZ() + 0.5 - size,
                    action.pos.getX() + 0.5 + size,
                    action.pos.getY() + 0.5 + size,
                    action.pos.getZ() + 0.5 + size
            );

            boolean secondary = i > 0;
            Color side = secondary
                    ? lerpColor(secondSideStartColor.getValue(), secondSideEndColor.getValue(), progress)
                    : lerpColor(sideStartColor.getValue(), sideEndColor.getValue(), progress);
            Color line = secondary
                    ? lerpColor(secondLineStartColor.getValue(), secondLineEndColor.getValue(), progress)
                    : lerpColor(lineStartColor.getValue(), lineEndColor.getValue(), progress);

            Render3DUtils.drawFilledBox(box, side);
            Render3DUtils.drawOutlineBox(event.getPoseStack(), box, line.getRGB(), 2.0f);
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (nullCheck() || !renderProgress.getValue() || actions.isEmpty()) return;

        boolean hasText = false;

        for (int i = 0; i < actions.size(); i++) {
            MineAction action = actions.get(i);
            String text = action.completed ? "Done" : action.publicProgress + "%";
            Vector3f projected = WorldToScreen.getWorldPositionToScreen(action.pos.getCenter());
            if (projected.z < 0.0f || projected.z > 1.0f) continue;

            float guiScale = mc.getWindow().getGuiScale();
            float x = projected.x / guiScale;
            float y = projected.y / guiScale;
            float screenWidth = mc.getWindow().getGuiScaledWidth();
            float screenHeight = mc.getWindow().getGuiScaledHeight();
            if (x < 0.0f || y < 0.0f || x > screenWidth || y > screenHeight) continue;

            Color color = i > 0 ? secondColor.getValue() : targetColor.getValue();
            float scale = 1.0f;
            float width = textRenderer.getWidth(text, scale);
            float height = textRenderer.getHeight(scale);
            textRenderer.addText(text, x - width / 2.0f, y - height / 2.0f, scale, color);
            hasText = true;
        }

        if (hasText) {
            textRenderer.drawAndClear();
        } else {
            textRenderer.clear();
        }
    }

    private void beginMining(BlockPos pos, Direction direction) {
        MineAction existing = findAction(pos);
        if (existing != null) {
            existing.direction = direction;
            actions.remove(existing);
            actions.addFirst(existing);
            return;
        }

        if (!doubleBreak.getValue()) {
            clearActions(true);
        } else {
            while (actions.size() >= 2) {
                MineAction removed = actions.removeLast();
                removed.cancel();
            }
        }

        actions.addFirst(new MineAction(pos, direction));
    }

    private boolean updateAction(MineAction action, long now) {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) return true;

        if (action.removed) return true;
        if (farCancel.getValue() && !withinRange(action.pos)) {
            action.cancel();
            return true;
        }

        BlockState state = level.getBlockState(action.pos);
        boolean blockPresent = !state.isAir() && !state.canBeReplaced();
        if (!blockPresent && !action.completed) {
            return true;
        }

        if (!action.started) {
            queueStart(action);
            return false;
        }

        if (action.startScheduled) return false;

        float maxTicks = getMineTicks(action.pos);
        if (!Float.isFinite(maxTicks) || maxTicks <= 0.0f) {
            action.cancel();
            return true;
        }

        float threshold = Math.max(1.0f, maxTicks * mineDamage.getValue().floatValue());

        action.prevRenderProgress = action.renderProgress;

        if (action.completed) {
            action.renderProgress = 1.0f;
            action.publicProgress = 100;

            if (!blockPresent) {
                action.breakAttempts = 0;
                return !instantMine.getValue();
            }

            if (action.breakAttempts >= maxBreaks.getValue()) {
                return true;
            }

            boolean readyForInstant = !instantMine.getValue() || action.instantRetryTimer.passedMillise(instantDelay.getValue());
            if (readyForInstant && action.retryTimer.passedMillise(packetDelay.getValue()) && !shouldPause()) {
                performBreak(action);
            }

            return false;
        }

        if (action.lastUpdate <= 0L) {
            action.lastUpdate = now;
            return false;
        }

        double delta = (now - action.lastUpdate) / 1000.0;
        action.lastUpdate = now;

        double increment = (!checkGround.getValue() || player.onGround()) ? delta * 20.0 : delta * 4.0;
        action.progressTicks += (float) increment;
        action.renderProgress = Mth.clamp(action.progressTicks / threshold, 0.0f, 1.0f);
        action.publicProgress = Mth.clamp((int) (action.renderProgress * 100.0f), 0, 100);

        if (autoSwitch.getValue() != SwitchMode.None && action.publicProgress >= switchDamage.getValue()) {
            int bestSlot = getBestTool(action.pos);
            if (bestSlot != -1) {
                switchTo(bestSlot);
            }
        }

        if (action.progressTicks < threshold) {
            return false;
        }

        action.progressTicks = threshold;
        action.renderProgress = 1.0f;
        action.publicProgress = 100;

        if (!shouldPause()) {
            performBreak(action);
            action.completed = true;
        }

        return false;
    }

    private void queueStart(MineAction action) {
        if (action.startScheduled || action.removed) return;

        action.startScheduled = true;
        Runnable startTask = () -> {
            action.startScheduled = false;
            if (action.removed || !actions.contains(action) || nullCheck()) return;

            var player = mc.player;
            if (player == null) return;

            sendStartPackets(action.pos, action.getDirection());
            if (swing.getValue()) {
                player.swing(InteractionHand.MAIN_HAND);
            }

            action.started = true;
            action.completed = false;
            action.progressTicks = 0.0f;
            action.prevRenderProgress = 0.0f;
            action.renderProgress = 0.0f;
            action.publicProgress = 0;
            action.breakAttempts = 0;
            action.lastUpdate = System.currentTimeMillis();
            action.retryTimer.reset();
            action.instantRetryTimer.setMs(instantDelay.getValue().longValue());
        };

        boolean hasOther = false;
        for (MineAction other : actions) {
            if (other != action && other.started && !other.removed) {
                hasOther = true;
                sendStopPacket(other.pos, other.getDirection());
            }
        }

        if (hasOther && packetDelay.getValue() > 0) {
            schedule(packetDelay.getValue(), startTask);
        } else {
            startTask.run();
        }
    }

    private void performBreak(MineAction action) {
        if (action.removed || nullCheck() || mc.player == null || mc.gameMode == null) return;

        var player = mc.player;
        var gameMode = mc.gameMode;

        int bestSlot = getBestTool(action.pos);
        if (bestSlot != -1) {
            switchTo(bestSlot);
        }

        if (bypassGround.getValue() && !player.isFallFlying() && !player.onGround()) {
            PacketUtils.sendSilently(new ServerboundMovePlayerPacket.Pos(player.getX(), player.getY() + 1.0E-9, player.getZ(), true, false));
        }

        if (swing.getValue()) {
            player.swing(InteractionHand.MAIN_HAND);
        }

        sendStopPacket(action.pos, action.getDirection());

        if (clientRemove.getValue()) {
            gameMode.destroyBlock(action.pos);
        }

        action.breakAttempts++;
        action.retryTimer.reset();
        action.instantRetryTimer.reset();
    }

    private void sendStartPackets(BlockPos pos, Direction direction) {
        sendAction(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, direction);
        sendFastBypassPackets();
        sendAction(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, direction);
        sendFastBypassPackets();
    }

    private void sendStopPacket(BlockPos pos, Direction direction) {
        sendAction(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, direction);
    }

    private void sendAction(ServerboundPlayerActionPacket.Action action, BlockPos pos, Direction direction) {
        if (mc.getConnection() == null) return;
        mc.getConnection().send(new ServerboundPlayerActionPacket(action, pos, direction));
    }

    private void sendFastBypassPackets() {
        if (!fastBypass.getValue() || mc.player == null) return;

        BlockPos bypassPos = mc.player.blockPosition().offset(0, 7891, 0);
        for (int i = 0; i < 5; i++) {
            sendAction(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, bypassPos, Direction.DOWN);
        }
    }

    private void switchTo(int slot) {
        if (autoSwitch.getValue() == SwitchMode.None || mc.player == null || mc.gameMode == null || mc.getConnection() == null) return;

        var player = mc.player;
        var gameMode = mc.gameMode;
        var connection = mc.getConnection();

        int selectedSlot = player.getInventory().getSelectedSlot();
        if (autoSwitch.getValue() != SwitchMode.Alternative && slot == selectedSlot) return;
        if (swapState != null && swapState.matches(slot, autoSwitch.getValue())) {
            swapState.restoreAt = System.currentTimeMillis() + switchTime.getValue();
            return;
        }

        restoreSwap();

        switch (autoSwitch.getValue()) {
            case None -> {
            }
            case Silent -> {
                if (slot > 8) return;
                PacketUtils.sendSilently(new ServerboundSetCarriedItemPacket(slot));
                swapState = new SwapState(SwitchMode.Silent, selectedSlot, slot, -1, System.currentTimeMillis() + switchTime.getValue());
            }
            case Normal -> {
                if (slot > 8) return;
                player.getInventory().setSelectedSlot(slot);
                swapState = new SwapState(SwitchMode.Normal, selectedSlot, slot, -1, System.currentTimeMillis() + switchTime.getValue());
            }
            case Alternative -> {
                if (slot == selectedSlot) return;
                int containerSlot = toContainerSlot(slot);
                gameMode.handleContainerInput(player.containerMenu.containerId, containerSlot, selectedSlot, ContainerInput.SWAP, player);
                connection.send(new ServerboundContainerClosePacket(player.containerMenu.containerId));
                swapState = new SwapState(SwitchMode.Alternative, selectedSlot, slot, containerSlot, System.currentTimeMillis() + switchTime.getValue());
            }
        }
    }

    private void handleSwapRestore(long now) {
        if (swapState != null && now >= swapState.restoreAt) {
            restoreSwap();
        }
    }

    private void restoreSwap() {
        if (swapState == null || nullCheck() || mc.player == null || mc.gameMode == null || mc.getConnection() == null) return;

        var player = mc.player;
        var gameMode = mc.gameMode;
        var connection = mc.getConnection();

        switch (swapState.mode) {
            case Silent -> PacketUtils.sendSilently(new ServerboundSetCarriedItemPacket(swapState.originalSlot));
            case Normal -> player.getInventory().setSelectedSlot(swapState.originalSlot);
            case Alternative -> {
                gameMode.handleContainerInput(player.containerMenu.containerId, swapState.containerSlot, swapState.originalSlot, ContainerInput.SWAP, player);
                connection.send(new ServerboundContainerClosePacket(player.containerMenu.containerId));
            }
            case None -> {
            }
        }

        swapState = null;
    }

    private int toContainerSlot(int slot) {
        if (slot < 9) return slot + 36;
        return slot;
    }

    private MineAction findAction(BlockPos pos) {
        for (MineAction action : actions) {
            if (action.pos.equals(pos)) {
                return action;
            }
        }
        return null;
    }

    private void clearActions(boolean cancelPackets) {
        if (cancelPackets) {
            for (MineAction action : actions) {
                action.cancel();
            }
        }
        actions.clear();
    }

    private void resetState(boolean cancelPackets) {
        clearActions(cancelPackets);
        delayedActions.clear();
        restoreSwap();
    }

    private void schedule(int delayMs, Runnable runnable) {
        long runAt = System.currentTimeMillis() + Math.max(0, delayMs);
        delayedActions.add(new DelayedAction(runAt, runnable));
    }

    private void runDelayedActions(long now) {
        delayedActions.removeIf(action -> {
            if (action.runAt > now) return false;
            action.runnable.run();
            return true;
        });
    }

    private boolean shouldPause() {
        if (!usingPause.getValue() || mc.player == null) return false;
        if (!mc.options.keyUse.isDown()) return false;
        if (!onlyMain.getValue() || !mc.player.isUsingItem()) return true;
        return mc.player.getUseItem() == mc.player.getMainHandItem();
    }

    private boolean withinRange(BlockPos pos) {
        return mc.player != null && mc.player.getEyePosition().distanceToSqr(pos.getCenter()) <= range.getValue() * range.getValue();
    }

    private boolean canMine(BlockPos pos) {
        if (nullCheck() || mc.level == null) return false;
        if (!withinRange(pos)) return false;

        var level = mc.level;
        return level.getBlockState(pos).getDestroySpeed(level, pos) >= 0.0f;
    }

    private int getBestTool(BlockPos pos) {
        if (nullCheck() || mc.player == null || mc.level == null) return -1;

        var player = mc.player;
        var level = mc.level;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return -1;

        int end = autoSwitch.getValue() == SwitchMode.Alternative ? 35 : 8;
        int bestSlot = -1;
        float bestScore = 1.0f;

        for (int i = 0; i <= end; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            float destroySpeed = stack.getDestroySpeed(state);
            int efficiency = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.EFFICIENCY);
            float score = destroySpeed + efficiency;

            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private float getMineTicks(BlockPos pos) {
        int bestSlot = getBestTool(pos);
        return getMineTicks(pos, bestSlot);
    }

    private float getMineTicks(BlockPos pos, int slot) {
        if (nullCheck() || mc.player == null || mc.level == null) return 20.0f;

        var player = mc.player;
        var level = mc.level;
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0f) return Float.MAX_VALUE;
        if (hardness == 0.0f) return 1.0f;

        ItemStack stack = slot >= 0 ? player.getInventory().getItem(slot) : ItemStack.EMPTY;
        boolean canHarvest = !stack.isEmpty() && stack.isCorrectToolForDrops(state);
        float speed = getDigSpeed(state, slot);
        float damage = speed / hardness / (canHarvest ? 30.0f : 100.0f);

        if (damage <= 0.0f) return Float.MAX_VALUE;
        return 1.0f / damage;
    }

    private float getDigSpeed(BlockState state, int slot) {
        if (mc.player == null) return 1.0f;

        var player = mc.player;
        ItemStack stack = slot >= 0 ? player.getInventory().getItem(slot) : ItemStack.EMPTY;
        float speed = stack.isEmpty() ? 1.0f : stack.getDestroySpeed(state);

        int efficiency = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.EFFICIENCY);
        if (efficiency > 0 && speed > 1.0f) {
            speed += (float) (StrictMath.pow(efficiency, 2) + 1);
        }

        if (player.hasEffect(MobEffects.HASTE)) {
            speed *= 1.0f + (Objects.requireNonNull(player.getEffect(MobEffects.HASTE)).getAmplifier() + 1) * 0.2f;
        }

        if (player.hasEffect(MobEffects.MINING_FATIGUE)) {
            speed *= switch (Objects.requireNonNull(player.getEffect(MobEffects.MINING_FATIGUE)).getAmplifier()) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                case 2 -> 0.0027f;
                default -> 0.00081f;
            };
        }

        if (player.isEyeInFluid(FluidTags.WATER)) {
            speed *= (float) player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
        }

        return Math.max(speed, 0.0f);
    }

    private Color lerpColor(Color start, Color end, float progress) {
        float clamped = Mth.clamp(progress, 0.0f, 1.0f);
        return new Color(
                Mth.lerpInt(clamped, start.getRed(), end.getRed()),
                Mth.lerpInt(clamped, start.getGreen(), end.getGreen()),
                Mth.lerpInt(clamped, start.getBlue(), end.getBlue()),
                Mth.lerpInt(clamped, start.getAlpha(), end.getAlpha())
        );
    }

    private final class MineAction {
        private final BlockPos pos;
        private Direction direction;
        private boolean started;
        private boolean startScheduled;
        private boolean completed;
        private boolean removed;
        private float progressTicks;
        private float prevRenderProgress;
        private float renderProgress;
        private int publicProgress;
        private int breakAttempts;
        private long lastUpdate;
        private final TimerUtils retryTimer = new TimerUtils();
        private final TimerUtils instantRetryTimer = new TimerUtils();

        private MineAction(BlockPos pos, Direction direction) {
            this.pos = pos;
            this.direction = direction;
            this.instantRetryTimer.setMs(instantDelay.getValue().longValue());
        }

        private Direction getDirection() {
            return RotationUtils.getDirection(pos, direction);
        }

        private void cancel() {
            removed = true;
            if (mc.getConnection() != null && (started || progressTicks > 0.0f || completed)) {
                sendAction(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, pos, getDirection());
            }
        }
    }

    private static final class DelayedAction {
        private final long runAt;
        private final Runnable runnable;

        private DelayedAction(long runAt, Runnable runnable) {
            this.runAt = runAt;
            this.runnable = runnable;
        }
    }

    private static final class SwapState {
        private final SwitchMode mode;
        private final int originalSlot;
        private final int targetSlot;
        private final int containerSlot;
        private long restoreAt;

        private SwapState(SwitchMode mode, int originalSlot, int targetSlot, int containerSlot, long restoreAt) {
            this.mode = mode;
            this.originalSlot = originalSlot;
            this.targetSlot = targetSlot;
            this.containerSlot = containerSlot;
            this.restoreAt = restoreAt;
        }

        private boolean matches(int slot, SwitchMode mode) {
            return this.mode == mode && this.targetSlot == slot;
        }
    }
}
