package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.events.impl.TickEvent;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.managers.FriendManager;
import com.github.epsilon.managers.RotationManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.combat.DamageUtils;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.render.Render3DUtils;
import com.github.epsilon.utils.render.WorldToScreen;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ZealotCrystalPlus extends Module {

    public static final ZealotCrystalPlus INSTANCE = new ZealotCrystalPlus();

    private ZealotCrystalPlus() {
        super("Zealot Crystal+", Category.COMBAT);
    }

    private final EnumSetting<Page> page = enumSetting("Page", Page.General);

    // General
    private final BoolSetting players = boolSetting("Players", true, () -> page.getValue() == Page.General);
    private final BoolSetting mobs = boolSetting("Mobs", false, () -> page.getValue() == Page.General);
    private final BoolSetting animals = boolSetting("Animals", false, () -> page.getValue() == Page.General);
    private final IntSetting maxTargets = intSetting("Max Targets", 4, 1, 10, 1, () -> page.getValue() == Page.General);
    private final DoubleSetting targetRange = doubleSetting("Target Range", 16.0, 0.0, 32.0, 0.5, () -> page.getValue() == Page.General);
    private final DoubleSetting yawSpeed = doubleSetting("Yaw Speed", 45.0, 5.0, 180.0, 5.0, () -> page.getValue() == Page.General);
    private final DoubleSetting placeRotationRange = doubleSetting("Place Rotation Range", 0.0, 0.0, 180.0, 5.0, () -> page.getValue() == Page.General);
    private final DoubleSetting breakRotationRange = doubleSetting("Break Rotation Range", 90.0, 0.0, 180.0, 5.0, () -> page.getValue() == Page.General);
    private final BoolSetting eatingPause = boolSetting("Eating Pause", false, () -> page.getValue() == Page.General);
    private final IntSetting updateDelay = intSetting("Update Delay", 5, 0, 250, 1, () -> page.getValue() == Page.General);

    // Force place
    private final DoubleSetting forcePlaceHealth = doubleSetting("Force Place Health", 8.0, 0.0, 36.0, 0.5, () -> page.getValue() == Page.ForcePlace);
    private final IntSetting forcePlaceArmorRate = intSetting("Force Place Armor Rate", 3, 0, 25, 1, () -> page.getValue() == Page.ForcePlace);
    private final DoubleSetting forcePlaceMinDamage = doubleSetting("Force Place Min Damage", 1.5, 0.0, 10.0, 0.25, () -> page.getValue() == Page.ForcePlace);
    private final DoubleSetting forcePlaceMotion = doubleSetting("Force Place Motion", 4.0, 0.0, 10.0, 0.25, () -> page.getValue() == Page.ForcePlace);
    private final DoubleSetting forcePlaceBalance = doubleSetting("Force Place Balance", -1.0, -10.0, 10.0, 0.25, () -> page.getValue() == Page.ForcePlace);
    private final BoolSetting forcePlaceWhileSwording = boolSetting("Force Place While Swording", false, () -> page.getValue() == Page.ForcePlace);

    // Calculation
    private final BoolSetting assumeInstantMine = boolSetting("Assume Instant Mine", true, () -> page.getValue() == Page.Calculation);
    private final DoubleSetting noSuicide = doubleSetting("No Suicide", 2.0, 0.0, 20.0, 0.25, () -> page.getValue() == Page.Calculation);
    private final DoubleSetting wallRange = doubleSetting("Wall Range", 3.0, 0.0, 8.0, 0.1, () -> page.getValue() == Page.Calculation);
    private final BoolSetting motionPredict = boolSetting("Motion Predict", true, () -> page.getValue() == Page.Calculation);
    private final IntSetting predictTicks = intSetting("Predict Ticks", 8, 0, 20, 1, () -> page.getValue() == Page.Calculation && motionPredict.getValue());
    private final EnumSetting<DamagePriority> damagePriority = enumSetting("Damage Priority", DamagePriority.Efficient, () -> page.getValue() == Page.Calculation);
    private final BoolSetting lethalOverride = boolSetting("Lethal Override", true, () -> page.getValue() == Page.Calculation);
    private final DoubleSetting lethalThresholdAddition = doubleSetting("Lethal Threshold Addition", 0.5, -5.0, 5.0, 0.1, () -> page.getValue() == Page.Calculation && lethalOverride.getValue());
    private final DoubleSetting lethalMaxSelfDamage = doubleSetting("Lethal Max Self Damage", 16.0, 0.0, 20.0, 0.25, () -> page.getValue() == Page.Calculation && lethalOverride.getValue());
    private final DoubleSetting safeMaxTargetDamageReduction = doubleSetting("Safe Max Target Damage Reduction", 1.0, 0.0, 10.0, 0.1, () -> page.getValue() == Page.Calculation);
    private final DoubleSetting safeMinSelfDamageReduction = doubleSetting("Safe Min Self Damage Reduction", 2.0, 0.0, 10.0, 0.1, () -> page.getValue() == Page.Calculation);
    private final DoubleSetting collidingCrystalExtraSelfDamageThreshold = doubleSetting("Colliding Crystal Extra Self Damage Threshold", 4.0, 0.0, 10.0, 0.1, () -> page.getValue() == Page.Calculation);

    // Place
    private final EnumSetting<PlaceMode> placeMode = enumSetting("Place Mode", PlaceMode.Single, () -> page.getValue() == Page.Place);
    private final EnumSetting<PacketPlaceMode> packetPlace = enumSetting("Packet Place", PacketPlaceMode.Weak, () -> page.getValue() == Page.Place);
    private final BoolSetting spamPlace = boolSetting("Spam Place", false, () -> page.getValue() == Page.Place);
    private final EnumSetting<SwitchMode> placeSwitchMode = enumSetting("Place Switch Mode", SwitchMode.Off, () -> page.getValue() == Page.Place);
    private final BoolSetting placeSwing = boolSetting("Place Swing", false, () -> page.getValue() == Page.Place);
    private final EnumSetting<PlaceBypass> placeSideBypass = enumSetting("Place Side Bypass", PlaceBypass.Up, () -> page.getValue() == Page.Place);
    private final DoubleSetting placeMinDamage = doubleSetting("Place Min Damage", 5.0, 0.0, 20.0, 0.25, () -> page.getValue() == Page.Place);
    private final DoubleSetting placeMaxSelfDamage = doubleSetting("Place Max Self Damage", 6.0, 0.0, 20.0, 0.25, () -> page.getValue() == Page.Place);
    private final DoubleSetting placeBalance = doubleSetting("Place Balance", -3.0, -10.0, 10.0, 0.25, () -> page.getValue() == Page.Place);
    private final IntSetting placeDelay = intSetting("Place Delay", 50, 0, 500, 1, () -> page.getValue() == Page.Place);
    private final DoubleSetting placeRange = doubleSetting("Place Range", 5.0, 0.0, 8.0, 0.1, () -> page.getValue() == Page.Place);
    private final EnumSetting<RangeMode> placeRangeMode = enumSetting("Place Range Mode", RangeMode.Feet, () -> page.getValue() == Page.Place);

    // Break
    private final EnumSetting<BreakMode> breakMode = enumSetting("Break Mode", BreakMode.Smart, () -> page.getValue() == Page.Break);
    private final BoolSetting bbtt = boolSetting("2B2T", false, () -> page.getValue() == Page.Break);
    private final IntSetting bbttFactor = intSetting("2B2T Factor", 200, 0, 1000, 25, () -> page.getValue() == Page.Break && bbtt.getValue());
    private final EnumSetting<BreakMode> packetBreak = enumSetting("Packet Break", BreakMode.Target, () -> page.getValue() == Page.Break && !bbtt.getValue());
    private final IntSetting ownTimeout = intSetting("Own Timeout", 100, 0, 2000, 25, () -> page.getValue() == Page.Break && (breakMode.getValue() == BreakMode.Own || packetBreak.getValue() == BreakMode.Own));
    private final EnumSetting<SwitchMode> antiWeakness = enumSetting("Anti Weakness", SwitchMode.Off, () -> page.getValue() == Page.Break);
    private final IntSetting swapDelay = intSetting("Swap Delay", 0, 0, 20, 1, () -> page.getValue() == Page.Break);
    private final DoubleSetting breakMinDamage = doubleSetting("Break Min Damage", 4.0, 0.0, 20.0, 0.25, () -> page.getValue() == Page.Break);
    private final DoubleSetting breakMaxSelfDamage = doubleSetting("Break Max Self Damage", 8.0, 0.0, 20.0, 0.25, () -> page.getValue() == Page.Break);
    private final DoubleSetting breakBalance = doubleSetting("Break Balance", -4.0, -10.0, 10.0, 0.25, () -> page.getValue() == Page.Break);
    private final IntSetting breakDelay = intSetting("Break Delay", 100, 0, 500, 1, () -> page.getValue() == Page.Break);
    private final DoubleSetting breakRange = doubleSetting("Break Range", 5.0, 0.0, 8.0, 0.1, () -> page.getValue() == Page.Break);
    private final EnumSetting<RangeMode> breakRangeMode = enumSetting("Break Range Mode", RangeMode.Feet, () -> page.getValue() == Page.Break);

    // Render
    private final EnumSetting<SwingMode> swingMode = enumSetting("Swing Mode", SwingMode.Client, () -> page.getValue() == Page.Render);
    private final EnumSetting<SwingHand> swingHand = enumSetting("Swing Hand", SwingHand.Auto, () -> page.getValue() == Page.Render);
    private final IntSetting filledAlpha = intSetting("Filled Alpha", 63, 0, 255, 1, () -> page.getValue() == Page.Render);
    private final IntSetting outlineAlpha = intSetting("Outline Alpha", 200, 0, 255, 1, () -> page.getValue() == Page.Render);
    private final BoolSetting renderTargetDamage = boolSetting("Target Damage", true, () -> page.getValue() == Page.Render);
    private final BoolSetting renderSelfDamage = boolSetting("Self Damage", true, () -> page.getValue() == Page.Render);
    private final ColorSetting renderColor = colorSetting("Render Color", new Color(255, 150, 120, 255), () -> page.getValue() == Page.Render, true);
    private final DoubleSetting outlineWidth = doubleSetting("Outline Width", 3.0, 1.0, 10.0, 0.5, () -> page.getValue() == Page.Render);
    private final IntSetting movingLength = intSetting("Moving Length", 400, 0, 1000, 50, () -> page.getValue() == Page.Render);
    private final IntSetting fadeLength = intSetting("Fade Length", 200, 0, 1000, 50, () -> page.getValue() == Page.Render);

    private final TimerUtils placeTimer = new TimerUtils();
    private final TimerUtils breakTimer = new TimerUtils();
    private final TimerUtils calcTimer = new TimerUtils();

    private final Map<Long, Long> placedPosMap = new HashMap<>();
    private final Map<Integer, Long> crystalSpawnMap = new HashMap<>();
    private final Map<Integer, Long> attackedCrystalMap = new HashMap<>();
    private final Map<Long, Long> attackedPosMap = new HashMap<>();

    private List<TargetInfo> cachedTargets = List.of();
    private List<BlockPos> cachedRawPosList = List.of();
    private PlaceInfo cachedPlaceInfo;
    private LivingEntity target;
    private long lastActiveTime;

    private BlockPos renderBlockPos;
    private Vec3 renderPrevPos;
    private Vec3 renderCurrentPos;
    private Vec3 renderLastRenderedPos;
    private long renderMoveStartTime;
    private long renderFadeStartTime;
    private float renderScale;
    private float renderDamage;
    private float renderSelfDamageValue;
    private boolean renderHasTarget;

    private final Supplier<TextRenderer> textRenderer = Suppliers.memoize(() -> new TextRenderer(128 * 1024));

    @Override
    protected void onEnable() {
        placeTimer.reset();
        breakTimer.reset();
        calcTimer.setMs(updateDelay.getValue().longValue());
        cachedTargets = List.of();
        cachedRawPosList = List.of();
        cachedPlaceInfo = null;
        target = null;
        lastActiveTime = 0L;
        placedPosMap.clear();
        crystalSpawnMap.clear();
        attackedCrystalMap.clear();
        attackedPosMap.clear();
        resetRenderState();
    }

    @Override
    protected void onDisable() {
        cachedTargets = List.of();
        cachedRawPosList = List.of();
        cachedPlaceInfo = null;
        target = null;
        placedPosMap.clear();
        crystalSpawnMap.clear();
        attackedCrystalMap.clear();
        attackedPosMap.clear();
        resetRenderState();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        updateTimeouts();

        if (eatingPause.getValue() && mc.player.isUsingItem()) {
            return;
        }

        PlaceInfo placeInfo = updateCache();
        target = placeInfo != null ? placeInfo.target() : (cachedTargets.isEmpty() ? null : cachedTargets.getFirst().entity());

        boolean active = false;
        if (breakMode.getValue() != BreakMode.Off && breakTimer.passedMillise(breakDelay.getValue())) {
            active = doBreak(placeInfo) || active;
        }
        if (placeMode.getValue() != PlaceMode.Off && placeTimer.passedMillise(placeDelay.getValue())) {
            active = doPlace(placeInfo) || active;
        }

        if (!active && placeInfo == null) {
            deactivateRenderTarget();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck() || !isEnabled()) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundAddEntityPacket addPacket && addPacket.getType() == EntityType.END_CRYSTAL) {
            handleSpawnPacket(addPacket);
        } else if (packet instanceof ClientboundSoundPacket soundPacket) {
            handleExplosionPacket(soundPacket);
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (nullCheck() || renderPrevPos == null || renderCurrentPos == null) return;

        float moveDelta = toDelta(renderMoveStartTime, movingLength.getValue());
        float moveMultiplier = easeOutQuart(moveDelta);
        Vec3 renderPos = renderPrevPos.add(renderCurrentPos.subtract(renderPrevPos).scale(moveMultiplier));

        float fadeDelta = toDelta(renderFadeStartTime, fadeLength.getValue());
        renderScale = renderHasTarget ? easeOutCubic(fadeDelta) : 1.0f - easeInCubic(fadeDelta);
        if (renderScale <= 0.01f) return;

        double halfSize = 0.5 * renderScale;
        AABB box = new AABB(
                renderPos.x - halfSize, renderPos.y - halfSize, renderPos.z - halfSize,
                renderPos.x + halfSize, renderPos.y + halfSize, renderPos.z + halfSize
        );

        Color base = renderColor.getValue();
        Color filled = new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.clamp((int) (filledAlpha.getValue() * renderScale), 0, 255));
        Color outline = new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.clamp((int) (outlineAlpha.getValue() * renderScale), 0, 255));

        if (filledAlpha.getValue() > 0) {
            Render3DUtils.drawFilledBox(box, filled);
        }
        if (outlineAlpha.getValue() > 0) {
            Render3DUtils.drawOutlineBox(event.getPoseStack(), box, outline.getRGB(), outlineWidth.getValue().floatValue());
        }

        renderLastRenderedPos = renderPos;
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (nullCheck()) return;
        if (!renderTargetDamage.getValue() && !renderSelfDamage.getValue()) return;
        if (renderPrevPos == null || renderCurrentPos == null) return;

        float moveDelta = toDelta(renderMoveStartTime, movingLength.getValue());
        float moveMultiplier = easeOutQuart(moveDelta);
        Vec3 renderPos = renderPrevPos.add(renderCurrentPos.subtract(renderPrevPos).scale(moveMultiplier));

        Vector2f screenPos = projectToScreen(renderPos);
        if (screenPos == null) return;

        StringBuilder text = new StringBuilder();
        if (renderTargetDamage.getValue()) {
            text.append(String.format(Locale.ROOT, "%.1f", renderDamage));
        }
        if (renderSelfDamage.getValue()) {
            if (!text.isEmpty()) text.append('/');
            text.append(String.format(Locale.ROOT, "%.1f", renderSelfDamageValue));
        }
        if (text.isEmpty()) return;

        TextRenderer renderer = textRenderer.get();
        float scale = 1.0f;
        float width = renderer.getWidth(text.toString(), scale);
        float height = renderer.getHeight(scale);
        Color color = new Color(255, 255, 255, Math.max(0, Math.min(255, (int) (220 * renderScale))));
        renderer.addText(text.toString(), screenPos.x - width / 2.0f, screenPos.y - height / 2.0f, scale, color);
        renderer.drawAndClear();
    }

    private PlaceInfo updateCache() {
        if (!calcTimer.passedMillise(updateDelay.getValue())) {
            return cachedPlaceInfo;
        }

        calcTimer.reset();
        cachedTargets = getTargets();
        if (cachedTargets.isEmpty()) {
            cachedRawPosList = List.of();
            cachedPlaceInfo = null;
            return null;
        }

        cachedRawPosList = getRawPosList();
        cachedPlaceInfo = calcPlaceInfo();
        return cachedPlaceInfo;
    }

    private List<TargetInfo> getTargets() {
        if (nullCheck()) return List.of();

        double rangeSq = targetRange.getValue() * targetRange.getValue();
        int ticks = motionPredict.getValue() ? predictTicks.getValue() : 0;
        List<TargetInfo> list = new ArrayList<>();
        Vec3 eyePos = mc.player.getEyePosition();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living == mc.player) continue;
            if (!living.isAlive() || living.isDeadOrDying()) continue;
            if (AntiBot.INSTANCE.isBot(living)) continue;
            if (living.position().y <= -64.0) continue;
            if (eyePos.distanceToSqr(living.getBoundingBox().getCenter()) > rangeSq) continue;

            boolean allowed = false;
            if (living instanceof Player playerEntity) {
                if (players.getValue() && !FriendManager.INSTANCE.isFriend(playerEntity)) {
                    allowed = true;
                }
            } else if (living instanceof Monster) {
                allowed = mobs.getValue();
            } else if (living instanceof Animal) {
                allowed = animals.getValue();
            }
            if (!allowed) continue;

            list.add(buildTargetInfo(living, ticks));
        }

        list.sort(Comparator.comparingDouble(info -> mc.player.distanceToSqr(info.entity())));
        if (list.size() > maxTargets.getValue()) {
            return List.copyOf(list.subList(0, maxTargets.getValue()));
        }
        return List.copyOf(list);
    }

    private TargetInfo buildTargetInfo(LivingEntity entity, int ticks) {
        double motionX = Mth.clamp(entity.getX() - entity.xo, -0.6, 0.6);
        double motionY = Mth.clamp(entity.getY() - entity.yo, -0.5, 0.5);
        double motionZ = Mth.clamp(entity.getZ() - entity.zo, -0.6, 0.6);

        AABB entityBox = entity.getBoundingBox();
        AABB targetBox = entityBox;
        for (int tick = 0; tick <= ticks; tick++) {
            AABB moved = canMove(entity, targetBox, motionX, motionY, motionZ);
            if (moved == null) moved = canMove(entity, targetBox, motionX, 0.0, motionZ);
            if (moved == null) moved = canMove(entity, targetBox, 0.0, motionY, 0.0);
            if (moved == null) break;
            targetBox = moved;
        }

        double offsetX = targetBox.minX - entityBox.minX;
        double offsetY = targetBox.minY - entityBox.minY;
        double offsetZ = targetBox.minZ - entityBox.minZ;
        Vec3 motion = new Vec3(offsetX, offsetY, offsetZ);
        Vec3 pos = entity.position();

        return new TargetInfo(entity, pos.add(motion), targetBox, pos, motion);
    }

    private AABB canMove(Entity entity, AABB box, double motionX, double motionY, double motionZ) {
        AABB moved = box.move(motionX, motionY, motionZ);
        return mc.level.noCollision(entity, moved) ? moved : null;
    }

    private List<BlockPos> getRawPosList() {
        if (nullCheck()) return List.of();

        List<BlockPos> list = new ArrayList<>();
        double range = placeRange.getValue();
        double rangeSq = range * range;
        double wallRangeSq = wallRange.getValue() * wallRange.getValue();
        int floor = Mth.floor(range);
        int ceil = Mth.ceil(range);
        Vec3 feetPos = mc.player.position();
        Vec3 eyePos = mc.player.getEyePosition();

        int feetX = Mth.floor(feetPos.x);
        int feetY = Mth.floor(feetPos.y);
        int feetZ = Mth.floor(feetPos.z);

        for (int x = feetX - floor; x <= feetX + ceil; x++) {
            for (int z = feetZ - floor; z <= feetZ + ceil; z++) {
                for (int y = feetY - floor; y <= feetY + ceil; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!mc.level.getWorldBorder().isWithinBounds(pos)) continue;

                    double crystalX = x + 0.5;
                    double crystalY = y + 1.0;
                    double crystalZ = z + 0.5;

                    if (placeDistanceSq(mc.player, crystalX, crystalY, crystalZ) > rangeSq) continue;
                    if (!isPlaceable(pos)) continue;

                    double feetDistSq = feetPos.distanceToSqr(crystalX, crystalY, crystalZ);
                    if (feetDistSq > wallRangeSq && !rayTraceVisible(eyePos, new Vec3(crystalX, crystalY + 1.7, crystalZ))) {
                        continue;
                    }

                    list.add(pos);
                }
            }
        }

        list.sort(Comparator.comparingDouble((BlockPos pos) -> pos.distToCenterSqr(feetX, feetY, feetZ)).reversed());
        return list;
    }

    private PlaceInfo calcPlaceInfo() {
        if (cachedTargets.isEmpty() || cachedRawPosList.isEmpty()) {
            return null;
        }

        PlaceInfo.Mutable max = new PlaceInfo.Mutable();
        PlaceInfo.Mutable safe = new PlaceInfo.Mutable();
        PlaceInfo.Mutable lethal = new PlaceInfo.Mutable();
        List<CrystalDamageEntry> crystalEntries = getCrystalDamageEntries();

        for (BlockPos pos : getPlaceablePos()) {
            AABB placeBox = getCrystalPlaceBox(pos);
            Vec3 crystalPos = getCrystalPos(pos);
            float selfDamage = getSelfDamage(crystalPos);
            float collidingDamage = calcCollidingCrystalDamage(crystalEntries, placeBox);
            float adjustedDamage = Math.max(selfDamage, collidingDamage - collidingCrystalExtraSelfDamageThreshold.getValue().floatValue());

            if (remainingHealth(adjustedDamage) <= noSuicide.getValue()) continue;
            if (remainingHealth(collidingDamage) <= noSuicide.getValue()) continue;
            if (!lethalOverride.getValue() && adjustedDamage > placeMaxSelfDamage.getValue()) continue;

            for (TargetInfo info : cachedTargets) {
                if (info.box().intersects(placeBox)) continue;

                float targetDamage = getTargetDamage(info, crystalPos);
                if (lethalOverride.getValue()
                        && targetDamage - getTotalHealth(info.entity()) > lethalThresholdAddition.getValue()
                        && selfDamage < lethal.selfDamage
                        && selfDamage <= lethalMaxSelfDamage.getValue()) {
                    lethal.update(info.entity(), pos, adjustedDamage, targetDamage, false);
                }

                if (adjustedDamage > placeMaxSelfDamage.getValue()) continue;

                float minDamage = shouldForcePlace(info.entity())
                        ? forcePlaceMinDamage.getValue().floatValue()
                        : placeMinDamage.getValue().floatValue();
                float balance = shouldForcePlace(info.entity())
                        ? forcePlaceBalance.getValue().floatValue()
                        : placeBalance.getValue().floatValue();

                if (targetDamage < minDamage || targetDamage - adjustedDamage < balance) continue;

                float score = damagePriority.getValue().score(adjustedDamage, targetDamage);
                float maxScore = damagePriority.getValue().score(max.selfDamage, max.targetDamage);
                if (score > maxScore) {
                    max.update(info.entity(), pos, adjustedDamage, targetDamage, false);
                } else if (max.targetDamage - targetDamage <= safeMaxTargetDamageReduction.getValue()
                        && max.selfDamage - adjustedDamage >= safeMinSelfDamageReduction.getValue()) {
                    safe.update(info.entity(), pos, adjustedDamage, targetDamage, false);
                }
            }
        }

        if (max.targetDamage - safe.targetDamage > safeMaxTargetDamageReduction.getValue()
                || max.selfDamage - safe.selfDamage <= safeMinSelfDamageReduction.getValue()) {
            safe.clear();
        }

        PlaceInfo.Mutable chosen = lethal.takeValid();
        if (chosen == null) chosen = safe.takeValid();
        if (chosen == null) chosen = max.takeValid();
        if (chosen == null) return null;
        chosen.calcPlacement();
        return chosen.toImmutable();
    }

    private List<BlockPos> getPlaceablePos() {
        List<BlockPos> list = new ArrayList<>();
        List<Entity> collidingEntities = getCollidingEntities();
        for (BlockPos pos : cachedRawPosList) {
            if (!checkPlaceRotation(pos)) continue;
            if (!checkPlaceCollision(pos, collidingEntities)) continue;
            list.add(pos);
        }
        return list;
    }

    private List<Entity> getCollidingEntities() {
        List<Entity> colliding = new ArrayList<>();
        double rangeSq = placeRange.getValue() * placeRange.getValue();
        int feetX = Mth.floor(mc.player.getX());
        int feetY = Mth.floor(mc.player.getY());
        int feetZ = Mth.floor(mc.player.getZ());
        boolean single = placeMode.getValue() == PlaceMode.Single;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!entity.isAlive()) continue;

            double adjustedRange = Mth.ceil(rangeSq) - Math.ceil((entity.getBbWidth() / 2.0f) * (entity.getBbWidth() / 2.0f) * 2.0f);
            double dist = entity.distanceToSqr(feetX + 0.5, feetY + 0.5, feetZ + 0.5);
            if (dist > adjustedRange) continue;

            if (!(entity instanceof EndCrystal crystal)) {
                colliding.add(entity);
            } else if (!single || !checkBreakRange(crystal.position())) {
                colliding.add(entity);
            }
        }

        return colliding;
    }

    private boolean checkPlaceCollision(BlockPos pos, List<Entity> collidingEntities) {
        double minX = pos.getX() + 0.001;
        double minY = pos.getY() + 1.0;
        double minZ = pos.getZ() + 0.001;
        double maxX = pos.getX() + 0.999;
        double maxY = pos.getY() + 3.0;
        double maxZ = pos.getZ() + 0.999;

        for (Entity entity : collidingEntities) {
            if (entity.getBoundingBox().intersects(minX, minY, minZ, maxX, maxY, maxZ)) {
                return false;
            }
        }
        return true;
    }

    private boolean doPlace(PlaceInfo placeInfo) {
        if (placeInfo == null) return false;
        if (!spamPlace.getValue() && !checkPlaceCollision(placeInfo)) return false;
        return placeDirect(placeInfo, false);
    }

    private boolean placeDirect(PlaceInfo placeInfo, boolean ignoreTimer) {
        if (!ignoreTimer && !placeTimer.passedMillise(placeDelay.getValue())) return false;

        FindItemResult crystals = findCrystalItem();
        if (!crystals.found()) return false;

        InteractionHand hand = crystals.getHand();
        if (hand == InteractionHand.MAIN_HAND
                && crystals.slot() != mc.player.getInventory().getSelectedSlot()
                && crystals.slot() != 40) {
            switch (placeSwitchMode.getValue()) {
                case Off -> {
                    return false;
                }
                case Legit -> InvUtils.swap(crystals.slot(), false);
                case Ghost -> InvUtils.swap(crystals.slot(), true);
            }
            hand = InteractionHand.MAIN_HAND;
        }

        InteractionHand finalHand = hand;
        Vector2f rotation = placeInfo.rotation();
        BlockHitResult hitResult = new BlockHitResult(placeInfo.hitVec(), placeInfo.side(), placeInfo.blockPos(), false);
        RotationManager.INSTANCE.applyRotation(rotation, getRotationSpeed(), Priority.High.priority, record -> {
            if (!isEnabled() || nullCheck()) {
                InvUtils.swapBack();
                return;
            }

            InteractionResult result = mc.gameMode.useItemOn(mc.player, finalHand, hitResult);
            if (result.consumesAction()) {
                if (placeSwing.getValue()) {
                    doSwing(finalHand);
                }
                placedPosMap.put(placeInfo.blockPos().asLong(), System.currentTimeMillis() + ownTimeout.getValue());
                placeTimer.reset();
                lastActiveTime = System.currentTimeMillis();
                updateRenderTarget(placeInfo.blockPos(), placeInfo.targetDamage(), placeInfo.selfDamage());
            }

            InvUtils.swapBack();
        });
        return true;
    }

    private boolean doBreak(PlaceInfo placeInfo) {
        List<EndCrystal> crystalList = getCrystalList();
        if (crystalList.isEmpty()) return false;

        EndCrystal crystal = switch (breakMode.getValue()) {
            case Own -> {
                EndCrystal targetCrystal = getTargetCrystal(placeInfo, crystalList);
                yield targetCrystal != null ? targetCrystal : getCrystal(crystalList.stream()
                        .filter(entry -> placedPosMap.containsKey(toLong(entry.getX(), entry.getY() - 1.0, entry.getZ())))
                        .toList());
            }
            case Target -> getTargetCrystal(placeInfo, crystalList);
            case Smart -> {
                EndCrystal targetCrystal = getTargetCrystal(placeInfo, crystalList);
                yield targetCrystal != null ? targetCrystal : getCrystal(crystalList);
            }
            case All -> {
                Entity ref = target != null ? target : mc.player;
                yield crystalList.stream().min(Comparator.comparingDouble(ref::distanceToSqr)).orElse(null);
            }
            case Off -> null;
        };

        return crystal != null && breakDirect(crystal);
    }

    private boolean breakDirect(EndCrystal crystal) {
        if (placeSwitchMode.getValue() != SwitchMode.Ghost
                && antiWeakness.getValue() != SwitchMode.Ghost
                && System.currentTimeMillis() - lastActiveTime < swapDelay.getValue() * 50L) {
            return false;
        }

        int weaponSlot = -1;
        boolean swapped = false;
        if (isWeaknessActive() && !isHoldingTool()) {
            switch (antiWeakness.getValue()) {
                case Off -> {
                    return false;
                }
                case Legit, Ghost -> {
                    weaponSlot = findWeaponSlot();
                    if (weaponSlot == -1) return false;
                    InvUtils.swap(weaponSlot, antiWeakness.getValue() == SwitchMode.Ghost);
                    swapped = true;
                }
            }
        }

        final int crystalId = crystal.getId();
        final Vector2f rotation = RotationUtils.calculate(crystal);
        RotationManager.INSTANCE.applyRotation(rotation, getRotationSpeed(), Priority.High.priority, record -> {
            if (!isEnabled() || nullCheck()) {
                InvUtils.swapBack();
                return;
            }

            Entity current = mc.level.getEntity(crystalId);
            if (!(current instanceof EndCrystal currentCrystal) || !currentCrystal.isAlive()) {
                InvUtils.swapBack();
                return;
            }

            if (!checkBreakRange(currentCrystal.position())) {
                InvUtils.swapBack();
                return;
            }

            mc.gameMode.attack(mc.player, currentCrystal);
            doSwing(resolveSwingHand(false));
            breakTimer.reset();
            lastActiveTime = System.currentTimeMillis();
            attackedCrystalMap.put(currentCrystal.getId(), System.currentTimeMillis() + 1000L);
            attackedPosMap.put(toLong(currentCrystal.getX(), currentCrystal.getY(), currentCrystal.getZ()), System.currentTimeMillis() + 1000L);

            Vec3 crystalPos = currentCrystal.position();
            float targetDmg = target != null ? DamageUtils.crystalDamage(target, crystalPos, target.position(), DamageUtils.ArmorEnchantmentMode.None) : 0.0f;
            float selfDmg = getSelfDamage(crystalPos);
            updateRenderTarget(currentCrystal.blockPosition().below(), targetDmg, selfDmg);

            PlaceInfo placeInfo = updateCache();
            if (packetPlace.getValue().onBreak && placeInfo != null && crystalPlaceBoxIntersects(placeInfo.blockPos(), currentCrystal.getBoundingBox())) {
                placeDirect(placeInfo, true);
            }

            InvUtils.swapBack();
        });
        return true;
    }

    private List<EndCrystal> getCrystalList() {
        List<EndCrystal> list = new ArrayList<>();
        long current = System.currentTimeMillis();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal crystal)) continue;
            if (!crystal.isAlive()) continue;
            if (bbtt.getValue() && current - getSpawnTime(crystal) < bbttFactor.getValue()) continue;
            if (!checkBreakRange(crystal.position())) continue;
            if (!checkCrystalRotation(RotationUtils.calculate(crystal), breakRotationRange.getValue())) continue;
            list.add(crystal);
        }
        return list;
    }

    private EndCrystal getTargetCrystal(PlaceInfo placeInfo, List<EndCrystal> crystalList) {
        if (placeInfo == null) return null;
        for (EndCrystal crystal : crystalList) {
            if (crystalPlaceBoxIntersects(placeInfo.blockPos(), crystal.getBoundingBox())) {
                return crystal;
            }
        }
        return null;
    }

    private EndCrystal getCrystal(List<EndCrystal> crystalList) {
        if (crystalList.isEmpty()) return null;

        BreakInfo.Mutable max = new BreakInfo.Mutable();
        BreakInfo.Mutable safe = new BreakInfo.Mutable();
        BreakInfo.Mutable lethal = new BreakInfo.Mutable();

        for (EndCrystal crystal : crystalList) {
            Vec3 crystalPos = crystal.position();
            float selfDamage = getSelfDamage(crystalPos);
            if (remainingHealth(selfDamage) <= noSuicide.getValue()) continue;
            if (!lethalOverride.getValue() && selfDamage > breakMaxSelfDamage.getValue()) continue;

            for (TargetInfo info : cachedTargets) {
                float targetDamage = getTargetDamage(info, crystalPos);
                if (lethalOverride.getValue()
                        && targetDamage - getTotalHealth(info.entity()) > lethalThresholdAddition.getValue()
                        && selfDamage < lethal.selfDamage
                        && selfDamage <= lethalMaxSelfDamage.getValue()) {
                    lethal.update(crystal, selfDamage, targetDamage);
                }

                if (selfDamage > breakMaxSelfDamage.getValue()) continue;

                float minDamage = shouldForcePlace(info.entity())
                        ? forcePlaceMinDamage.getValue().floatValue()
                        : breakMinDamage.getValue().floatValue();
                float balance = shouldForcePlace(info.entity())
                        ? forcePlaceBalance.getValue().floatValue()
                        : breakBalance.getValue().floatValue();

                if (targetDamage < minDamage || targetDamage - selfDamage < balance) continue;

                float score = damagePriority.getValue().score(selfDamage, targetDamage);
                float maxScore = damagePriority.getValue().score(max.selfDamage, max.targetDamage);
                if (score > maxScore) {
                    max.update(crystal, selfDamage, targetDamage);
                } else if (max.targetDamage - targetDamage <= safeMaxTargetDamageReduction.getValue()
                        && max.selfDamage - selfDamage >= safeMinSelfDamageReduction.getValue()) {
                    safe.update(crystal, selfDamage, targetDamage);
                }
            }
        }

        if (max.targetDamage - safe.targetDamage > safeMaxTargetDamageReduction.getValue()
                || max.selfDamage - safe.selfDamage <= safeMinSelfDamageReduction.getValue()) {
            safe.clear();
        }

        BreakInfo valid = lethal.takeValid();
        if (valid == null) valid = safe.takeValid();
        if (valid == null) valid = max.takeValid();
        return valid != null ? valid.crystal : null;
    }

    private void handleSpawnPacket(ClientboundAddEntityPacket packet) {
        crystalSpawnMap.put(packet.getId(), System.currentTimeMillis());
        if (bbtt.getValue()) return;

        Vec3 crystalPos = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        if (!checkBreakRange(crystalPos)) return;

        PlaceInfo placeInfo = updateCache();
        if (placeInfo == null) return;

        switch (packetBreak.getValue()) {
            case Target -> {
                if (crystalPlaceBoxIntersects(placeInfo.blockPos(), getCrystalBoundingBox(crystalPos))) {
                    breakSpawnedCrystal(packet, crystalPos);
                }
            }
            case Own -> {
                if (crystalPlaceBoxIntersects(placeInfo.blockPos(), getCrystalBoundingBox(crystalPos))
                        || (placedPosMap.containsKey(toLong(packet.getX(), packet.getY() - 1.0, packet.getZ())) && checkBreakDamage(crystalPos))) {
                    breakSpawnedCrystal(packet, crystalPos);
                }
            }
            case Smart -> {
                if (crystalPlaceBoxIntersects(placeInfo.blockPos(), getCrystalBoundingBox(crystalPos)) || checkBreakDamage(crystalPos)) {
                    breakSpawnedCrystal(packet, crystalPos);
                }
            }
            case All -> breakSpawnedCrystal(packet, crystalPos);
            default -> {
            }
        }
    }

    private void breakSpawnedCrystal(ClientboundAddEntityPacket packet, Vec3 crystalPos) {
        Entity spawned = mc.level.getEntity(packet.getId());
        if (spawned instanceof EndCrystal endCrystal) {
            breakDirect(endCrystal);
            return;
        }

        Vector2f rotation = RotationUtils.calculate(crystalPos);
        RotationManager.INSTANCE.applyRotation(rotation, getRotationSpeed(), Priority.High.priority, record -> {
            if (!isEnabled() || nullCheck()) return;
            Entity current = mc.level.getEntity(packet.getId());
            if (current instanceof EndCrystal currentCrystal && currentCrystal.isAlive()) {
                breakDirect(currentCrystal);
            }
        });
    }

    private void handleExplosionPacket(ClientboundSoundPacket packet) {
        if (packet.getSound() != SoundEvents.GENERIC_EXPLODE) return;

        Vec3 soundPos = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        PlaceInfo placeInfo = updateCache();
        if (placeInfo != null) {
            Vec3 placePos = getCrystalPos(placeInfo.blockPos());
            if (placePos.distanceToSqr(soundPos) <= 144.0) {
                placedPosMap.clear();
                crystalSpawnMap.clear();
                attackedCrystalMap.clear();
                attackedPosMap.clear();
                if (packetPlace.getValue().onRemove) {
                    placeDirect(placeInfo, true);
                }
                return;
            }
        }

        if (mc.player.distanceToSqr(soundPos) <= 144.0) {
            placedPosMap.clear();
            crystalSpawnMap.clear();
            attackedCrystalMap.clear();
            attackedPosMap.clear();
        }
    }

    private boolean checkBreakDamage(Vec3 crystalPos) {
        float selfDamage = getSelfDamage(crystalPos);
        if (remainingHealth(selfDamage) <= noSuicide.getValue()) return false;

        for (TargetInfo info : cachedTargets) {
            if (checkBreakDamage(crystalPos, selfDamage, info)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkBreakDamage(Vec3 crystalPos, float selfDamage, TargetInfo info) {
        float targetDamage = getTargetDamage(info, crystalPos);
        if (lethalOverride.getValue()
                && targetDamage - getTotalHealth(info.entity()) > lethalThresholdAddition.getValue()
                && targetDamage <= lethalMaxSelfDamage.getValue()) {
            return true;
        }

        if (selfDamage > breakMaxSelfDamage.getValue()) return false;

        float minDamage = shouldForcePlace(info.entity())
                ? forcePlaceMinDamage.getValue().floatValue()
                : breakMinDamage.getValue().floatValue();
        float balance = shouldForcePlace(info.entity())
                ? forcePlaceBalance.getValue().floatValue()
                : breakBalance.getValue().floatValue();

        return targetDamage >= minDamage && targetDamage - selfDamage >= balance;
    }

    private List<CrystalDamageEntry> getCrystalDamageEntries() {
        List<CrystalDamageEntry> entries = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof EndCrystal crystal && crystal.isAlive()) {
                entries.add(new CrystalDamageEntry(crystal, getSelfDamage(crystal.position())));
            }
        }
        return entries;
    }

    private float calcCollidingCrystalDamage(List<CrystalDamageEntry> crystals, AABB placeBox) {
        float max = 0.0f;
        for (CrystalDamageEntry entry : crystals) {
            if (!placeBox.intersects(entry.crystal().getBoundingBox())) continue;
            if (entry.selfDamage() > max) {
                max = entry.selfDamage();
            }
        }
        return max;
    }

    private boolean shouldForcePlace(LivingEntity entity) {
        if (entity == null) return false;
        if (!forcePlaceWhileSwording.getValue() && mc.player.getMainHandItem().is(ItemTags.SWORDS)) {
            return false;
        }
        return getTotalHealth(entity) <= forcePlaceHealth.getValue()
                || getRealSpeed(entity) >= forcePlaceMotion.getValue()
                || getMinArmorRate(entity) <= forcePlaceArmorRate.getValue();
    }

    private double getRealSpeed(LivingEntity entity) {
        double motionX = entity.getX() - entity.xo;
        double motionZ = entity.getZ() - entity.zo;
        return Math.hypot(motionX, motionZ) * 20.0;
    }

    private int getMinArmorRate(LivingEntity entity) {
        int minDura = 100;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armor = entity.getItemBySlot(slot);
            if (!armor.isDamageableItem()) continue;
            int maxDamage = armor.getMaxDamage();
            if (maxDamage <= 0) continue;
            int remaining = maxDamage - armor.getDamageValue();
            int percent = Math.max(0, Math.min(100, (int) ((remaining / (double) maxDamage) * 100.0)));
            minDura = Math.min(minDura, percent);
        }
        return minDura;
    }

    private boolean isPlaceable(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK)) {
            return false;
        }

        BlockPos crystalBlock = pos.above();
        if (!mc.level.getBlockState(crystalBlock).canBeReplaced()) return false;
        if (!mc.level.getBlockState(crystalBlock.above()).canBeReplaced()) return false;
        AABB crystalBox = getCrystalPlaceBox(pos);
        return mc.level.getEntities(null, crystalBox).isEmpty();
    }

    private boolean checkPlaceCollision(PlaceInfo placeInfo) {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal crystal)) continue;
            if (!crystal.isAlive()) continue;
            if (attackedCrystalMap.containsKey(crystal.getId())) continue;
            if (crystalPlaceBoxIntersects(placeInfo.blockPos(), crystal.getBoundingBox())) {
                return false;
            }
        }
        return true;
    }

    private boolean checkPlaceRotation(BlockPos pos) {
        if (placeRotationRange.getValue() <= 0.0) return true;
        Vector2f rotation = RotationUtils.calculate(getCrystalPos(pos));
        return getRotationDelta(RotationManager.INSTANCE.getRotation(), rotation) <= placeRotationRange.getValue();
    }

    private boolean checkCrystalRotation(Vector2f rotation, double range) {
        if (range <= 0.0) return true;
        return getRotationDelta(RotationManager.INSTANCE.getRotation(), rotation) <= range;
    }

    private boolean checkBreakRange(Vec3 crystalPos) {
        double rangeSq = breakRange.getValue() * breakRange.getValue();
        if (breakDistanceSq(mc.player, crystalPos.x, crystalPos.y, crystalPos.z) > rangeSq) {
            return false;
        }

        Vec3 eyePos = mc.player.getEyePosition();
        return eyePos.distanceToSqr(crystalPos) <= wallRange.getValue() * wallRange.getValue()
                || rayTraceVisible(eyePos, new Vec3(crystalPos.x, crystalPos.y + 1.7, crystalPos.z));
    }

    private double placeDistanceSq(Entity entity, double x, double y, double z) {
        return placeRangeMode.getValue() == RangeMode.Feet ? entity.distanceToSqr(x, y, z) : eyeDistanceSq(entity, x, y, z);
    }

    private double breakDistanceSq(Entity entity, double x, double y, double z) {
        return breakRangeMode.getValue() == RangeMode.Feet ? entity.distanceToSqr(x, y, z) : eyeDistanceSq(entity, x, y, z);
    }

    private double eyeDistanceSq(Entity entity, double x, double y, double z) {
        double dx = entity.getX() - x;
        double dy = entity.getY() + entity.getEyeHeight() - y;
        double dz = entity.getZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private boolean rayTraceVisible(Vec3 from, Vec3 to) {
        HitResult result = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return result.getType() == HitResult.Type.MISS;
    }

    private float getSelfDamage(Vec3 crystalPos) {
        return DamageUtils.selfCrystalDamage(crystalPos, DamageUtils.ArmorEnchantmentMode.None);
    }

    private float getTargetDamage(TargetInfo info, Vec3 crystalPos) {
        return DamageUtils.crystalDamage(info.entity(), crystalPos, info.pos(), DamageUtils.ArmorEnchantmentMode.None);
    }

    private float getTotalHealth(LivingEntity entity) {
        return entity.getHealth() + entity.getAbsorptionAmount();
    }

    private float remainingHealth(float damage) {
        return mc.player.getHealth() + mc.player.getAbsorptionAmount() - damage;
    }

    private long getSpawnTime(EndCrystal crystal) {
        return crystalSpawnMap.computeIfAbsent(crystal.getId(), id -> System.currentTimeMillis() - crystal.tickCount * 50L);
    }

    private boolean isWeaknessActive() {
        return mc.player.hasEffect(MobEffects.WEAKNESS)
                && (!mc.player.hasEffect(MobEffects.STRENGTH) || mc.player.getEffect(MobEffects.STRENGTH).getAmplifier() <= 0);
    }

    private boolean isHoldingTool() {
        ItemStack mainHand = mc.player.getMainHandItem();
        return mainHand.is(ItemTags.SWORDS)
                || mainHand.is(ItemTags.AXES)
                || mainHand.is(ItemTags.PICKAXES)
                || mainHand.is(ItemTags.SHOVELS)
                || mainHand.is(ItemTags.HOES);
    }

    private int findWeaponSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(ItemTags.SWORDS)
                    || stack.is(ItemTags.AXES)
                    || stack.is(ItemTags.PICKAXES)
                    || stack.is(ItemTags.SHOVELS)
                    || stack.is(ItemTags.HOES)) {
                return i;
            }
        }
        return -1;
    }

    private FindItemResult findCrystalItem() {
        return InvUtils.findInHotbar(Items.END_CRYSTAL);
    }

    private InteractionHand resolveSwingHand(boolean placing) {
        return switch (swingHand.getValue()) {
            case OffHand -> InteractionHand.OFF_HAND;
            case MainHand -> InteractionHand.MAIN_HAND;
            case Auto -> {
                if (placing && mc.player.getOffhandItem().is(Items.END_CRYSTAL)) {
                    yield InteractionHand.OFF_HAND;
                }
                yield InteractionHand.MAIN_HAND;
            }
        };
    }

    private void doSwing(InteractionHand hand) {
        switch (swingMode.getValue()) {
            case Client -> mc.player.swing(hand);
            case Packet -> mc.getConnection().send(new ServerboundSwingPacket(hand));
            case None -> {
            }
        }
    }

    private double getRotationSpeed() {
        return Math.max(0.1, yawSpeed.getValue() / 18.0);
    }

    private float getRotationDelta(Vector2f from, Vector2f to) {
        float yawDiff = Math.abs(Mth.wrapDegrees(to.x - from.x));
        float pitchDiff = Math.abs(to.y - from.y);
        return (float) Math.hypot(yawDiff, pitchDiff);
    }

    private Vec3 getCrystalPos(BlockPos supportPos) {
        return new Vec3(supportPos.getX() + 0.5, supportPos.getY() + 1.0, supportPos.getZ() + 0.5);
    }

    private AABB getCrystalPlaceBox(BlockPos supportPos) {
        return new AABB(
                supportPos.getX(), supportPos.getY() + 1.0, supportPos.getZ(),
                supportPos.getX() + 1.0, supportPos.getY() + 3.0, supportPos.getZ() + 1.0
        );
    }

    private AABB getCrystalBoundingBox(Vec3 crystalPos) {
        return new AABB(
                crystalPos.x - 1.0, crystalPos.y, crystalPos.z - 1.0,
                crystalPos.x + 1.0, crystalPos.y + 2.0, crystalPos.z + 1.0
        );
    }

    private boolean crystalPlaceBoxIntersects(BlockPos supportPos, AABB crystalBox) {
        return getCrystalPlaceBox(supportPos).intersects(crystalBox);
    }

    private long toLong(double x, double y, double z) {
        return BlockPos.containing(x, y, z).asLong();
    }

    private void updateTimeouts() {
        long current = System.currentTimeMillis();
        placedPosMap.values().removeIf(time -> time < current);
        crystalSpawnMap.values().removeIf(time -> time + 5000L < current);
        attackedCrystalMap.values().removeIf(time -> time < current);
        attackedPosMap.values().removeIf(time -> time < current);
    }

    private void updateRenderTarget(BlockPos pos, float damage, float selfDamage) {
        long now = System.currentTimeMillis();
        if (!pos.equals(renderBlockPos)) {
            renderCurrentPos = Vec3.atCenterOf(pos);
            renderPrevPos = renderLastRenderedPos != null ? renderLastRenderedPos : renderCurrentPos;
            renderMoveStartTime = now;
            if (renderBlockPos == null) {
                renderFadeStartTime = now;
            }
            renderBlockPos = pos;
        }
        if (!renderHasTarget) {
            renderFadeStartTime = now;
        }
        renderHasTarget = true;
        renderDamage = damage;
        renderSelfDamageValue = selfDamage;
    }

    private void deactivateRenderTarget() {
        if (renderHasTarget) {
            renderHasTarget = false;
            renderFadeStartTime = System.currentTimeMillis();
        }
    }

    private void resetRenderState() {
        renderBlockPos = null;
        renderPrevPos = null;
        renderCurrentPos = null;
        renderLastRenderedPos = null;
        renderMoveStartTime = 0L;
        renderFadeStartTime = 0L;
        renderScale = 0.0f;
        renderDamage = 0.0f;
        renderSelfDamageValue = 0.0f;
        renderHasTarget = false;
    }

    private Vector2f projectToScreen(Vec3 pos) {
        Vector3f projected = WorldToScreen.getWorldPositionToScreen(pos);
        float guiScale = mc.getWindow().getGuiScale();
        if (projected.z < 0.0f || projected.z > 1.0f) return null;

        float centerX = projected.x / guiScale;
        float centerY = projected.y / guiScale;
        if (centerX < 0.0f || centerY < 0.0f
                || centerX > mc.getWindow().getGuiScaledWidth()
                || centerY > mc.getWindow().getGuiScaledHeight()) {
            return null;
        }
        return new Vector2f(centerX, centerY);
    }

    private static float easeOutQuart(float t) {
        float u = 1.0f - t;
        return 1.0f - u * u * u * u;
    }

    private static float easeOutCubic(float t) {
        float u = 1.0f - t;
        return 1.0f - u * u * u;
    }

    private static float easeInCubic(float t) {
        return t * t * t;
    }

    private static float toDelta(long startTime, int lengthMs) {
        long elapsed = System.currentTimeMillis() - startTime;
        return Math.clamp((float) elapsed / Math.max(1, lengthMs), 0.0f, 1.0f);
    }

    private enum Page {
        General,
        ForcePlace,
        Calculation,
        Place,
        Break,
        Render
    }

    private enum DamagePriority {
        Efficient {
            @Override
            float score(float selfDamage, float targetDamage) {
                return targetDamage - selfDamage;
            }
        },
        Aggressive {
            @Override
            float score(float selfDamage, float targetDamage) {
                return targetDamage;
            }
        };

        abstract float score(float selfDamage, float targetDamage);
    }

    private enum SwingHand {
        Auto,
        OffHand,
        MainHand
    }

    private enum SwitchMode {
        Off,
        Legit,
        Ghost
    }

    private enum PlaceMode {
        Off,
        Single,
        Multi
    }

    private enum PacketPlaceMode {
        Off(false, false),
        Weak(true, false),
        Strong(true, true);

        private final boolean onRemove;
        private final boolean onBreak;

        PacketPlaceMode(boolean onRemove, boolean onBreak) {
            this.onRemove = onRemove;
            this.onBreak = onBreak;
        }
    }

    private enum PlaceBypass {
        Up,
        Down,
        Closest
    }

    private enum BreakMode {
        Off,
        Target,
        Own,
        Smart,
        All
    }

    private enum RangeMode {
        Feet,
        Eyes
    }

    private enum SwingMode {
        None,
        Client,
        Packet
    }

    private record TargetInfo(LivingEntity entity, Vec3 pos, AABB box, Vec3 currentPos, Vec3 predictMotion) {
    }

    private record CrystalDamageEntry(EndCrystal crystal, float selfDamage) {
    }

    private static class PlaceInfo {
        private final LivingEntity target;
        private final BlockPos blockPos;
        private final float selfDamage;
        private final float targetDamage;
        private final Direction side;
        private final Vec3 hitVec;
        private final Vector2f rotation;

        private PlaceInfo(LivingEntity target, BlockPos blockPos, float selfDamage, float targetDamage, Direction side, Vec3 hitVec, Vector2f rotation) {
            this.target = target;
            this.blockPos = blockPos;
            this.selfDamage = selfDamage;
            this.targetDamage = targetDamage;
            this.side = side;
            this.hitVec = hitVec;
            this.rotation = rotation;
        }

        private LivingEntity target() {
            return target;
        }

        private BlockPos blockPos() {
            return blockPos;
        }

        private float selfDamage() {
            return selfDamage;
        }

        private float targetDamage() {
            return targetDamage;
        }

        private Direction side() {
            return side;
        }

        private Vec3 hitVec() {
            return hitVec;
        }

        private Vector2f rotation() {
            return rotation;
        }

        private static class Mutable {
            private LivingEntity target;
            private BlockPos blockPos = BlockPos.ZERO;
            private float selfDamage = Float.MAX_VALUE;
            private float targetDamage = Float.NEGATIVE_INFINITY;
            private Direction side = Direction.UP;
            private Vec3 hitVec = Vec3.atCenterOf(BlockPos.ZERO);
            private Vector2f rotation = new Vector2f(0.0f, 0.0f);

            private void update(LivingEntity target, BlockPos blockPos, float selfDamage, float targetDamage, boolean wallBypass) {
                this.target = target;
                this.blockPos = blockPos;
                this.selfDamage = selfDamage;
                this.targetDamage = targetDamage;
            }

            private void clear() {
                this.target = null;
                this.blockPos = BlockPos.ZERO;
                this.selfDamage = Float.MAX_VALUE;
                this.targetDamage = Float.NEGATIVE_INFINITY;
                this.side = Direction.UP;
                this.hitVec = Vec3.atCenterOf(BlockPos.ZERO);
                this.rotation = new Vector2f(0.0f, 0.0f);
            }

            private Mutable takeValid() {
                return target != null && selfDamage != Float.MAX_VALUE && targetDamage > Float.NEGATIVE_INFINITY ? this : null;
            }

            private void calcPlacement() {
                switch (INSTANCE.placeSideBypass.getValue()) {
                    case Up -> {
                        side = Direction.UP;
                        hitVec = new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 1.0, blockPos.getZ() + 0.5);
                    }
                    case Down -> {
                        side = Direction.DOWN;
                        hitVec = new Vec3(blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5);
                    }
                    case Closest -> {
                        side = RotationUtils.getDirection(blockPos);
                        hitVec = new Vec3(
                                blockPos.getX() + 0.5 + side.getStepX() * 0.5,
                                blockPos.getY() + 0.5 + side.getStepY() * 0.5,
                                blockPos.getZ() + 0.5 + side.getStepZ() * 0.5
                        );
                    }
                }
                rotation = RotationUtils.calculate(hitVec);
            }

            private PlaceInfo toImmutable() {
                return new PlaceInfo(target, blockPos, selfDamage, targetDamage, side, hitVec, rotation);
            }
        }
    }

    private static class BreakInfo {
        private final EndCrystal crystal;
        private final float selfDamage;
        private final float targetDamage;

        private BreakInfo(EndCrystal crystal, float selfDamage, float targetDamage) {
            this.crystal = crystal;
            this.selfDamage = selfDamage;
            this.targetDamage = targetDamage;
        }

        private static class Mutable {
            private EndCrystal crystal;
            private float selfDamage = Float.MAX_VALUE;
            private float targetDamage = Float.NEGATIVE_INFINITY;

            private void update(EndCrystal crystal, float selfDamage, float targetDamage) {
                this.crystal = crystal;
                this.selfDamage = selfDamage;
                this.targetDamage = targetDamage;
            }

            private void clear() {
                this.crystal = null;
                this.selfDamage = Float.MAX_VALUE;
                this.targetDamage = Float.NEGATIVE_INFINITY;
            }

            private BreakInfo takeValid() {
                return crystal != null && selfDamage != Float.MAX_VALUE && targetDamage > Float.NEGATIVE_INFINITY
                        ? new BreakInfo(crystal, selfDamage, targetDamage)
                        : null;
            }
        }
    }
}


