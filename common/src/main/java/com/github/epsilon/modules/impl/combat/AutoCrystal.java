package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.events.impl.TickEvent;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.managers.RenderManager;
import com.github.epsilon.managers.RotationManager;
import com.github.epsilon.managers.TargetManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.combat.DamageUtils;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.render.Render3DUtils;
import com.github.epsilon.utils.render.WorldToScreen;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.RaytraceUtils;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.github.epsilon.events.bus.EventHandler;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AutoCrystal extends Module {

    public static final AutoCrystal INSTANCE = new AutoCrystal();

    private AutoCrystal() {
        super("Auto Crystal", Category.COMBAT);
    }

    // General
    private final DoubleSetting targetRange = doubleSetting("Target Range", 6.0, 0.0, 12.0, 0.5);
    private final BoolSetting targetPlayers = boolSetting("Players", true);
    private final BoolSetting targetMobs = boolSetting("Mobs", false);
    private final BoolSetting targetAnimals = boolSetting("Animals", false);
    private final BoolSetting targetVillagers = boolSetting("Villagers", false);
    private final EnumSetting<DamagePriority> damagePriority = enumSetting("Damage Priority", DamagePriority.Efficient);
    private final BoolSetting eatingPause = boolSetting("Eating Pause", false);

    // Calculation
    private final BoolSetting noSuicide = boolSetting("No Suicide", true);
    private final DoubleSetting lethalMaxSelfDamage = doubleSetting("Lethal Max Self Dmg", 8.0, 0.0, 36.0, 0.25);
    private final BoolSetting motionPrediction = boolSetting("Motion Prediction", false);
    private final IntSetting predictTick = intSetting("Predict Tick", 6, 0, 10, 1, motionPrediction::getValue);
    private final EnumSetting<DamageUtils.ArmorEnchantmentMode> armorMode = enumSetting("Armor Mode", DamageUtils.ArmorEnchantmentMode.PPBP);
    private final BoolSetting armorForSelf = boolSetting("Armor Mode For Self", false, () -> !armorMode.is(DamageUtils.ArmorEnchantmentMode.None));
    private final BoolSetting lethalOverride = boolSetting("Lethal Override", true);
    private final DoubleSetting lethalThresholdAddition = doubleSetting("Lethal Threshold Addition", 0.5, -5.0, 5.0, 0.1,
            lethalOverride::getValue);
    private final DoubleSetting safeMaxTargetDmgReduction = doubleSetting("Safe Max Target Dmg Reduction", 1.0, 0.0, 10.0, 0.1);
    private final DoubleSetting safeMinSelfDmgReduction = doubleSetting("Safe Min Self Dmg Reduction", 2.0, 0.0, 10.0, 0.1);

    // Force Place
    private final DoubleSetting forcePlaceHealth = doubleSetting("Force Place Health", 8.0, 0.0, 36.0, 0.5);
    private final IntSetting forcePlaceArmorRate = intSetting("Force Place Armor Rate", 3, 0, 25, 1);
    private final DoubleSetting forcePlaceMinDamage = doubleSetting("Force Place Min Dmg", 2.0, 0.0, 20.0, 0.25);
    private final DoubleSetting forcePlaceBalance = doubleSetting("Force Place Balance", -3.0, -10.0, 10.0, 0.25);

    // Place
    private final EnumSetting<SwingMode> placeSwing = enumSetting("Place Swing", SwingMode.None);
    private final DoubleSetting placeRotationSpeed = doubleSetting("Place Rotation Speed", 10.0, 1.0, 10.0, 0.5);
    private final EnumSetting<SwapMode> placeSwapMode = enumSetting("Place Swap Mode", SwapMode.None);
    private final DoubleSetting placeMinDmg = doubleSetting("Place Min Dmg", 6.0, 0.0, 20.0, 0.25);
    private final DoubleSetting placeMaxSelfDmg = doubleSetting("Place Max Self Dmg", 10.0, 0.0, 36.0, 0.25);
    private final DoubleSetting placeBalance = doubleSetting("Place Balance", -3.0, -10.0, 10.0, 0.25);
    private final IntSetting placeDelay = intSetting("Place Delay", 50, 0, 1000, 10);
    private final DoubleSetting placeRange = doubleSetting("Place Range", 4.0, 1.0, 6.0, 0.1);
    private final DoubleSetting placeWallRange = doubleSetting("Place Wall Range", 3.0, 0.0, 6.0, 0.1);

    // Break
    private final EnumSetting<SwingMode> breakSwing = enumSetting("Break Swing", SwingMode.None);
    private final DoubleSetting breakRotationSpeed = doubleSetting("Break Rotation Speed", 10.0, 1.0, 10.0, 0.5);
    private final BoolSetting antiWeak = boolSetting("Anti Weak", false);
    private final EnumSetting<SwapMode> antiWeakSwapMode = enumSetting("Anti Weak Swap Mode", SwapMode.Silent, antiWeak::getValue);
    private final DoubleSetting breakMinDmg = doubleSetting("Break Min Dmg", 6.0, 0.0, 20.0, 0.25);
    private final DoubleSetting breakMaxSelfDmg = doubleSetting("Break Max Self Dmg", 10.0, 0.0, 36.0, 0.25);
    private final DoubleSetting breakBalance = doubleSetting("Break Balance", -3.0, -10.0, 10.0, 0.25);
    private final IntSetting breakDelay = intSetting("Break Delay", 50, 0, 1000, 10);
    private final DoubleSetting breakRange = doubleSetting("Break Range", 4.0, 1.0, 6.0, 0.1);

    // Render
    private final BoolSetting targetDamage = boolSetting("Target Damage", true);
    private final BoolSetting selfDamage = boolSetting("Self Damage", false);
    private final ColorSetting filledColor = colorSetting("Filled Color", new Color(255, 150, 120, 100));
    private final ColorSetting outlineColor = colorSetting("Outline Color", new Color(255, 150, 120, 170));
    private final DoubleSetting outlineWidth = doubleSetting("Outline Width", 3.0, 1.0, 10.0, 0.5);
    private final IntSetting movingLength = intSetting("Moving Length", 400, 0, 1000, 50);
    private final IntSetting fadeLength = intSetting("Fade Length", 200, 0, 1000, 50);

    private LivingEntity target;
    private final TimerUtils placeTimer = new TimerUtils();
    private final TimerUtils breakTimer = new TimerUtils();

    private BlockPos renderBlockPos;
    private Vec3 renderPrevPos;
    private Vec3 renderCurrentPos;
    private Vec3 renderLastRenderedPos;
    private long renderMoveStartTime;
    private long renderFadeStartTime;
    private float renderScale;
    private float renderDamage;
    private float renderSelfDamage;
    private boolean renderHasTarget;

    private final Supplier<TextRenderer> textRenderer = Suppliers.memoize(() -> new TextRenderer(128 * 1024));

    @Override
    protected void onEnable() {
        placeTimer.reset();
        breakTimer.reset();
        resetRenderState();
    }

    @Override
    protected void onDisable() {
        target = null;
        resetRenderState();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;
        if (eatingPause.getValue() && mc.player.isUsingItem()) return;

        target = TargetManager.INSTANCE.acquirePrimary(
                TargetManager.TargetRequest.of(
                        targetRange.getValue(),
                        360.0f,
                        targetPlayers.getValue(),
                        targetMobs.getValue(),
                        targetAnimals.getValue(),
                        targetVillagers.getValue(),
                        true,
                        1
                )
        );

        if (target == null || !target.isAlive()) {
            deactivateRenderTarget();
            return;
        }

        Vec3 predictedPos = getPredictedTargetPos(target);

        // 在一个 tick 内先敲击后放置
        if (!tryBreakCrystal()) {
            if (!tryPlaceCrystal(predictedPos)) {
                deactivateRenderTarget();
            }
        }
    }

    private boolean tryBreakCrystal() {
        List<BreakCandidate> candidates = new ArrayList<>();

        final var breakRangeSq = breakRange.getValue() * breakRange.getValue();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal crystal)) continue;
            if (!crystal.isAlive()) continue;

            double distToPlayerSq = mc.player.distanceToSqr(crystal);
            if (distToPlayerSq > breakRangeSq) continue;

            Vec3 crystalPos = crystal.position();

            float targetDmg = DamageUtils.crystalDamage(target, crystalPos, target.position(), armorMode.getValue());
            float selfDmg = DamageUtils.selfCrystalDamage(crystalPos, armorForSelf.getValue() ? armorMode.getValue() : DamageUtils.ArmorEnchantmentMode.None);
            float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            if (noSuicide.getValue() && hp - selfDmg <= 0.5f) continue;
            if (!lethalOverride.getValue()) {
                if (exceedsSelfDamageLimit(selfDmg, breakMaxSelfDmg.getValue())) continue;
                if (targetDmg < breakMinDmg.getValue()) continue;
                if (targetDmg - selfDmg < breakBalance.getValue()) continue;
            }

            Vector2f targetRotation = RotationUtils.calculate(crystal);
            candidates.add(new BreakCandidate(crystal, targetDmg, selfDmg, targetRotation));
        }

        EndCrystal bestCrystal = selectBestBreakCandidate(candidates);
        if (bestCrystal == null) return false;

        // 有有效目标：计时器就绪才实际执行，但无论如何都返回 true 以保持渲染
        if (breakTimer.passedMillise(breakDelay.getValue())) {
            doBreakCrystal(bestCrystal);
        }
        return true;
    }

    private void doBreakCrystal(EndCrystal crystal) {
        if (antiWeak.getValue() && mc.player.hasEffect(MobEffects.WEAKNESS)) {
            FindItemResult sword = InvUtils.findInHotbar(Items.DIAMOND_SWORD, Items.NETHERITE_SWORD, Items.IRON_SWORD, Items.STONE_SWORD);
            if (sword.found()) {
                switch (antiWeakSwapMode.getValue()) {
                    case Swap -> InvUtils.swap(sword.slot(), false);
                    case Silent -> InvUtils.swap(sword.slot(), true);
                    default -> {
                    }
                }
            }
        }

        final int requestPriority = Priority.High.priority;
        final int crystalId = crystal.getId();
        final Vector2f rotation = RotationUtils.calculate(crystal);

        RotationManager.INSTANCE.applyRotation(rotation, breakRotationSpeed.getValue(), requestPriority, record -> {
            if (!isEnabled() || nullCheck()) {
                InvUtils.swapBack();
                return;
            }

            Entity current = mc.level.getEntity(crystalId);
            if (!(current instanceof EndCrystal currentCrystal) || !currentCrystal.isAlive()) {
                InvUtils.swapBack();
                return;
            }

            if (!RaytraceUtils.facingEnemy(currentCrystal, breakRange.getValue(), record.currentRotation())) {
                InvUtils.swapBack();
                return;
            }

            mc.gameMode.attack(mc.player, currentCrystal);
            doSwing(breakSwing.getValue());
            breakTimer.reset();
            Vec3 crystalPosition = currentCrystal.position();
            float tgtDmg = DamageUtils.crystalDamage(target, crystalPosition, target.position(), armorMode.getValue());
            float selfDmg = DamageUtils.selfCrystalDamage(crystalPosition, armorForSelf.getValue() ? armorMode.getValue() : DamageUtils.ArmorEnchantmentMode.None);
            updateRenderTarget(currentCrystal.blockPosition().below(), tgtDmg, selfDmg);

            InvUtils.swapBack();
        });
    }

    private boolean tryPlaceCrystal(Vec3 predictedTargetPos) {
        FindItemResult crystalItem = findCrystalItem();
        if (!crystalItem.found()) return false;

        List<PlaceCandidate> candidates = collectPlaceCandidates(predictedTargetPos);

        PlaceCandidate bestPlacement = findBestCandidate(candidates,
                placeMinDmg.getValue().floatValue(),
                placeMaxSelfDmg.getValue().floatValue(),
                placeBalance.getValue().floatValue());

        if (bestPlacement == null && shouldForcePlace()) {
            bestPlacement = findBestCandidate(candidates,
                    forcePlaceMinDamage.getValue().floatValue(),
                    placeMaxSelfDmg.getValue().floatValue(),
                    forcePlaceBalance.getValue().floatValue());
        }

        if (bestPlacement == null) return false;

        // 有有效放置点：计时器就绪才实际执行，但无论如何都返回 true 以保持渲染
        if (!placeTimer.passedMillise(placeDelay.getValue())) return true;

        Vector2f smoothedRotation = RotationUtils.smooth(bestPlacement.targetRotation, placeRotationSpeed.getValue() * 18);
        if (!RaytraceUtils.overBlock(smoothedRotation, bestPlacement.supportPos, null, false, placeRange.getValue())) {

            BlockHitResult hitResult = RaytraceUtils.rayCast(smoothedRotation, placeRange.getValue());
            BlockPos placePos = hitResult.getBlockPos();

            // smooth 旋转未能对准最佳目标，检查当前实际命中位置是否在满足常规 place 限制的候选列表中
            for (PlaceCandidate candidate :
                    getValidCandidates(
                            candidates,
                            placeMinDmg.getValue().floatValue(),
                            placeMaxSelfDmg.getValue().floatValue(),
                            placeBalance.getValue().floatValue()
                    )
            ) {
                if (candidate.supportPos.equals(placePos)) {
                    doPlaceCrystal(candidate, crystalItem);
                    return true;
                }
            }
        }

        // smooth 旋转能对准最佳目标或者实际命中位置不在候选列表中，直接放置
        doPlaceCrystal(bestPlacement, crystalItem);
        return true;
    }

    private boolean shouldForcePlace() {
        if (target.getHealth() <= forcePlaceHealth.getValue()) return true;

        float targetArmor = (float) target.getAttributeValue(Attributes.ARMOR);
        return targetArmor <= forcePlaceArmorRate.getValue();
    }

    /**
     * 收集所有可行的放置位置，计算它们的伤害和可见性，并返回一个候选列表。
     *
     * @param predictedTargetPos 预判后的目标坐标
     * @return 可行的位置
     */
    private List<PlaceCandidate> collectPlaceCandidates(Vec3 predictedTargetPos) {
        List<PlaceCandidate> candidates = new ArrayList<>();

        BlockPos center = BlockPos.containing(predictedTargetPos);
        int range = 5;
        double placeRangeSq = placeRange.getValue() * placeRange.getValue();
        double wallRangeSq = placeWallRange.getValue() * placeWallRange.getValue();
        Vec3 playerEye = mc.player.getEyePosition();

        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos supportPos = center.offset(x, y, z);

                    BlockState supportState = mc.level.getBlockState(supportPos);
                    if (!supportState.is(Blocks.OBSIDIAN) && !supportState.is(Blocks.BEDROCK)) continue;
                    BlockPos crystalBlockPos = supportPos.above();
                    if (!mc.level.getBlockState(crystalBlockPos).isAir()) continue;
                    if (!mc.level.getBlockState(crystalBlockPos.above()).isAir()) continue;
                    AABB crystalBB = new AABB(crystalBlockPos);
                    if (!mc.level.getEntities(null, crystalBB).isEmpty()) continue;
                    Vec3 supportCenter = Vec3.atCenterOf(supportPos);
                    double distanceSq = playerEye.distanceToSqr(supportCenter);
                    if (distanceSq > placeRangeSq) continue;
                    Vec3 crystalPos = new Vec3(
                            supportPos.getX() + 0.5,
                            supportPos.getY() + 1.0,
                            supportPos.getZ() + 0.5
                    );

                    float targetDmg = DamageUtils.crystalDamage(target, crystalPos, predictedTargetPos, armorMode.getValue());
                    float selfDmg = DamageUtils.selfCrystalDamage(crystalPos, armorForSelf.getValue() ? armorMode.getValue() : DamageUtils.ArmorEnchantmentMode.None);
                    Vector2f targetRotation = RotationUtils.calculate(supportPos, Direction.UP);
                    boolean visible = RaytraceUtils.overBlock(targetRotation, supportPos, Direction.UP, false, placeRange.getValue());
                    boolean wallBypassAllowed = !visible && distanceSq <= wallRangeSq;
                    if (!visible && !wallBypassAllowed) continue;

                    candidates.add(new PlaceCandidate(
                            supportPos,
                            crystalPos,
                            targetDmg,
                            selfDmg,
                            targetRotation,
                            !visible,
                            wallBypassAllowed
                    ));
                }
            }
        }

        return candidates;
    }

    private PlaceCandidate findBestCandidate(List<PlaceCandidate> candidates,
                                             float minDmg, float maxSelfDmg, float minBalance) {
        if (candidates.isEmpty()) return null;

        DamagePriority priority = damagePriority.getValue();
        float safeMaxTgtRed = safeMaxTargetDmgReduction.getValue().floatValue();
        float safeMinSelfRed = safeMinSelfDmgReduction.getValue().floatValue();

        // 优先返回致命候选
        if (lethalOverride.getValue()) {
            PlaceCandidate lethal = findLethalCandidate(candidates);
            if (lethal != null) return lethal;
        }

        // 收集所有满足条件的候选，再按分数排序
        List<PlaceCandidate> valid = getValidCandidates(candidates, minDmg, maxSelfDmg, minBalance);
        if (valid.isEmpty()) return null;

        valid.sort((a, b) -> Float.compare(
                priority.score(b.selfDmg, b.targetDmg),
                priority.score(a.selfDmg, a.targetDmg)
        ));

        PlaceCandidate maxCandidate = valid.get(0);

        // 在高分候选之后，寻找自伤更低且伤害损失可接受的安全候选
        for (int i = 1; i < valid.size(); i++) {
            PlaceCandidate c = valid.get(i);
            if (maxCandidate.targetDmg - c.targetDmg <= safeMaxTgtRed
                    && maxCandidate.selfDmg - c.selfDmg >= safeMinSelfRed) {
                return c;
            }
        }

        return maxCandidate;
    }

    /**
     * 从所有候选中找到可击杀目标且自伤最低的致命候选 (跳过常规伤害门槛限制)
     *
     * @param candidates 可行的放置点
     * @return 致命候选，如果没有则返回 null
     */
    private PlaceCandidate findLethalCandidate(List<PlaceCandidate> candidates) {
        float targetHealth = target.getHealth() + target.getAbsorptionAmount();
        float lethalThreshold = lethalThresholdAddition.getValue().floatValue();
        float lethalMaxSelf = lethalMaxSelfDamage.getValue().floatValue();
        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();

        PlaceCandidate best = null;
        float bestSelfDmg = Float.MAX_VALUE;

        for (PlaceCandidate c : candidates) {
            if (noSuicide.getValue() && hp - c.selfDmg <= 0.5f) continue;
            if (c.targetDmg - targetHealth <= lethalThreshold) continue;
            if (c.selfDmg > lethalMaxSelf) continue;
            if (c.selfDmg < bestSelfDmg) {
                best = c;
                bestSelfDmg = c.selfDmg;
            }
        }

        return best;
    }

    /**
     * 收集满足所有常规条件的候选，优先返回可直接看见的候选，否则返回穿墙候选
     *
     * @param candidates 可行的放置点
     * @param minDmg     放置点最小伤害
     * @param maxSelfDmg 最大自伤
     * @param minBalance 对目标伤害与对自伤害的最低可承受值
     * @return 满足条件的放置点列表
     */
    private List<PlaceCandidate> getValidCandidates(List<PlaceCandidate> candidates,
                                                    float minDmg, float maxSelfDmg, float minBalance) {
        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        List<PlaceCandidate> visibleValid = new ArrayList<>();
        List<PlaceCandidate> wallValid = new ArrayList<>();

        for (PlaceCandidate c : candidates) {
            if (noSuicide.getValue() && hp - c.selfDmg <= 0.5f) continue;
            if (exceedsSelfDamageLimit(c.selfDmg, maxSelfDmg)) continue;
            if (c.targetDmg < minDmg) continue;
            if (c.targetDmg - c.selfDmg < minBalance) continue;

            if (c.throughWall) {
                if (c.wallBypassAllowed) wallValid.add(c);
            } else {
                visibleValid.add(c);
            }
        }

        return !visibleValid.isEmpty() ? visibleValid : wallValid;
    }

    private void doPlaceCrystal(PlaceCandidate candidate, FindItemResult crystalItem) {
        InteractionHand hand = crystalItem.getHand();
        if (crystalItem.slot() != mc.player.getInventory().getSelectedSlot() && crystalItem.slot() != 40) {
            switch (placeSwapMode.getValue()) {
                case Swap -> InvUtils.swap(crystalItem.slot(), false);
                case Silent -> InvUtils.swap(crystalItem.slot(), true);
                default -> {
                }
            }
            hand = InteractionHand.MAIN_HAND;
        }

        final InteractionHand placeHand = hand;
        final int requestPriority = Priority.High.priority;
        final Vector2f rotation = candidate.targetRotation;
        final Vec3 hitVec = new Vec3(
                candidate.supportPos.getX() + 0.5,
                candidate.supportPos.getY() + 1.0,
                candidate.supportPos.getZ() + 0.5
        );
        RotationManager.INSTANCE.applyRotation(rotation, placeRotationSpeed.getValue(), requestPriority, record -> {
            if (!isEnabled() || nullCheck()) {
                InvUtils.swapBack();
                return;
            }

            Direction placementDirection = RotationUtils.getDirection(candidate.supportPos, Direction.UP);

            boolean canPlace = candidate.throughWall
                    ? candidate.wallBypassAllowed && isAimingAtBlock(record.currentRotation(), candidate.targetRotation)
                    : RaytraceUtils.overBlock(record.currentRotation(), candidate.supportPos, placementDirection, false, placeRange.getValue());
            if (!canPlace) {
                InvUtils.swapBack();
                return;
            }

            BlockHitResult hitResult = new BlockHitResult(hitVec, placementDirection, candidate.supportPos, false);
            InteractionResult result = mc.gameMode.useItemOn(mc.player, placeHand, hitResult);
            if (result.consumesAction()) {
                doSwing(placeSwing.getValue());
                updateRenderTarget(candidate.supportPos, candidate.targetDmg, candidate.selfDmg);
                placeTimer.reset();
            }

            InvUtils.swapBack();
        });
    }

    private boolean exceedsSelfDamageLimit(float selfDmg, double maxSelfDmg) {
        if (selfDmg > maxSelfDmg) return true;

        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (noSuicide.getValue() && hp - selfDmg <= 0.5f) return true;
        float remainingHp = hp - selfDmg;
        return noSuicide.getValue() && remainingHp <= lethalMaxSelfDamage.getValue()
                && selfDmg > lethalMaxSelfDamage.getValue();
    }

    private Vec3 getPredictedTargetPos(LivingEntity entity) {
        if (!motionPrediction.getValue()) {
            return entity.position();
        }

        double motionX = Mth.clamp(entity.getX() - entity.xo, -0.6, 0.6);
        double motionY = Mth.clamp(entity.getY() - entity.yo, -0.5, 0.5);
        double motionZ = Mth.clamp(entity.getZ() - entity.zo, -0.6, 0.6);

        AABB entityBox = entity.getBoundingBox();
        AABB targetBox = entityBox;
        int ticks = predictTick.getValue();

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
        return entity.position().add(offsetX, offsetY, offsetZ);
    }

    private AABB canMove(Entity entity, AABB box, double motionX, double motionY, double motionZ) {
        AABB moved = box.move(motionX, motionY, motionZ);
        return mc.level.noCollision(entity, moved) ? moved : null;
    }

    private boolean isAimingAtBlock(Vector2f currentRotation, Vector2f targetRotation) {
        return getRotationDelta(currentRotation, targetRotation) <= 2.5f;
    }

    private FindItemResult findCrystalItem() {
        return InvUtils.findInHotbar(Items.END_CRYSTAL);
    }

    private void doSwing(SwingMode mode) {
        switch (mode) {
            case Client -> mc.player.swing(InteractionHand.MAIN_HAND);
            case Packet -> mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            default -> {
            }
        }
    }

    private void updateRenderTarget(BlockPos pos, float damage, float selfDmg) {
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
        // 从隐藏状态重新出现时，重置渐变计时器以播放放大动画
        if (!renderHasTarget) {
            renderFadeStartTime = now;
        }
        renderHasTarget = true;
        renderDamage = damage;
        renderSelfDamage = selfDmg;
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
        renderSelfDamage = 0.0f;
        renderHasTarget = false;
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

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;
        if (renderPrevPos == null || renderCurrentPos == null) return;

        float moveDelta = toDelta(renderMoveStartTime, movingLength.getValue());
        float moveMultiplier = easeOutQuart(moveDelta);
        Vec3 renderPos = renderPrevPos.add(
                renderCurrentPos.subtract(renderPrevPos).scale(moveMultiplier)
        );

        float fadeDelta = toDelta(renderFadeStartTime, fadeLength.getValue());
        if (renderHasTarget) {
            renderScale = easeOutCubic(fadeDelta);
        } else {
            renderScale = 1.0f - easeInCubic(fadeDelta);
        }

        if (renderScale <= 0.01f) return;

        double halfSize = 0.5 * renderScale;
        AABB box = new AABB(
                renderPos.x - halfSize, renderPos.y - halfSize, renderPos.z - halfSize,
                renderPos.x + halfSize, renderPos.y + halfSize, renderPos.z + halfSize
        );

        Color fc = filledColor.getValue();
        Color oc = outlineColor.getValue();

        Color filled = new Color(fc.getRed(), fc.getGreen(), fc.getBlue(),
                Math.clamp((int) (fc.getAlpha() * renderScale), 0, 255));
        Color outline = new Color(oc.getRed(), oc.getGreen(), oc.getBlue(),
                Math.clamp((int) (oc.getAlpha() * renderScale), 0, 255));

        Render3DUtils.drawFilledBox(box, filled);
        Render3DUtils.drawOutlineBox(event.getPoseStack(), box, outline.getRGB(), outlineWidth.getValue().floatValue());

        renderLastRenderedPos = renderPos;

        if (!targetDamage.getValue() && !selfDamage.getValue()) return;

        Vector2f screenPos = projectToScreen(renderPos);
        if (screenPos == null) return;

        StringBuilder sb = new StringBuilder();
        if (targetDamage.getValue()) sb.append(String.format(Locale.ROOT, "%.1f", renderDamage));
        if (selfDamage.getValue()) {
            if (!sb.isEmpty()) sb.append('/');
            sb.append(String.format(Locale.ROOT, "%.1f", renderSelfDamage));
        }
        String text = sb.toString();

        TextRenderer textRenderer = this.textRenderer.get();
        float textScale = 1.0f;
        float textWidth = textRenderer.getWidth(text, textScale);
        float textHeight = textRenderer.getHeight(textScale);
        float textX = screenPos.x - textWidth / 2.0f;
        float textY = screenPos.y - textHeight / 2.0f;
        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();
        if (textX + textWidth < 0.0f || textY + textHeight < 0.0f || textX > screenWidth || textY > screenHeight) {
            return;
        }
        Color textColor = new Color(255, 255, 255, Math.max(0, Math.min(255, (int) (220 * renderScale))));

        RenderManager.INSTANCE.applyRenderAfterWorld(() -> {
            TextRenderer tr = this.textRenderer.get();
            tr.addText(text, textX, textY, textScale, textColor);
            tr.drawAndClear();
        });
    }

    private Vector2f projectToScreen(Vec3 pos) {
        Vector3f projected = WorldToScreen.getWorldPositionToScreen(pos);

        float guiScale = mc.getWindow().getGuiScale();

        if (projected.z < 0.0f || projected.z > 1.0f) return null;

        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();

        float centerX = projected.x / guiScale;
        float centerY = projected.y / guiScale;
        if (centerX < 0.0f || centerY < 0.0f || centerX > screenWidth || centerY > screenHeight) return null;
        return new Vector2f(centerX, centerY);
    }

    private record PlaceCandidate(
            BlockPos supportPos,
            Vec3 crystalPos,
            float targetDmg,
            float selfDmg,
            Vector2f targetRotation,
            boolean throughWall,
            boolean wallBypassAllowed
    ) {
    }

    private EndCrystal selectBestBreakCandidate(List<BreakCandidate> candidates) {
        if (candidates.isEmpty()) return null;

        float targetHealth = target.getHealth() + target.getAbsorptionAmount();
        DamagePriority priority = damagePriority.getValue();
        boolean isLethalOverride = lethalOverride.getValue();
        float lethalThreshold = lethalThresholdAddition.getValue().floatValue();
        float lethalMaxSelf = lethalMaxSelfDamage.getValue().floatValue();
        float safeMaxTgtRed = safeMaxTargetDmgReduction.getValue().floatValue();
        float safeMinSelfRed = safeMinSelfDmgReduction.getValue().floatValue();

        float minDmg;
        float minBalance;
        if (shouldForcePlace()) {
            minDmg = forcePlaceMinDamage.getValue().floatValue();
            minBalance = forcePlaceBalance.getValue().floatValue();
        } else {
            minDmg = breakMinDmg.getValue().floatValue();
            minBalance = breakBalance.getValue().floatValue();
        }
        float maxSelfDmg = breakMaxSelfDmg.getValue().floatValue();

        BreakCandidate maxCandidate = null;
        float maxScore = Float.NEGATIVE_INFINITY;
        BreakCandidate safeCandidate = null;
        BreakCandidate lethalCandidate = null;
        float lethalSelfDmg = Float.MAX_VALUE;

        for (BreakCandidate c : candidates) {
            if (isLethalOverride
                    && c.targetDmg - targetHealth > lethalThreshold
                    && c.selfDmg < lethalSelfDmg
                    && c.selfDmg <= lethalMaxSelf) {
                lethalCandidate = c;
                lethalSelfDmg = c.selfDmg;
            }

            if (exceedsSelfDamageLimit(c.selfDmg, maxSelfDmg)) continue;
            if (c.targetDmg < minDmg) continue;
            if (c.targetDmg - c.selfDmg < minBalance) continue;

            float score = priority.score(c.selfDmg, c.targetDmg);

            if (score > maxScore) {
                maxCandidate = c;
                maxScore = score;
            } else if (maxCandidate != null
                    && maxCandidate.targetDmg - c.targetDmg <= safeMaxTgtRed
                    && maxCandidate.selfDmg - c.selfDmg >= safeMinSelfRed) {
                safeCandidate = c;
            }
        }

        if (maxCandidate != null && safeCandidate != null) {
            if (maxCandidate.targetDmg - safeCandidate.targetDmg > safeMaxTgtRed
                    || maxCandidate.selfDmg - safeCandidate.selfDmg <= safeMinSelfRed) {
                safeCandidate = null;
            }
        }

        BreakCandidate result = lethalCandidate != null ? lethalCandidate
                : (safeCandidate != null ? safeCandidate : maxCandidate);
        return result != null ? result.crystal : null;
    }

    private float getRotationDelta(Vector2f from, Vector2f to) {
        float yawDiff = Math.abs(Mth.wrapDegrees(to.x - from.x));
        float pitchDiff = Math.abs(to.y - from.y);
        return (float) Math.hypot(yawDiff, pitchDiff);
    }

    private record BreakCandidate(
            EndCrystal crystal,
            float targetDmg,
            float selfDmg,
            Vector2f targetRotation
    ) {
    }


    private enum SwapMode {
        None,
        Swap,
        Silent,
    }

    private enum SwingMode {
        None,
        Client,
        Packet,
    }

    private enum DamagePriority {
        Efficient {
            @Override
            float score(float selfDmg, float targetDmg) {
                return targetDmg - selfDmg;
            }
        },
        Aggressive {
            @Override
            float score(float selfDmg, float targetDmg) {
                return targetDmg;
            }
        };

        abstract float score(float selfDmg, float targetDmg);
    }

}