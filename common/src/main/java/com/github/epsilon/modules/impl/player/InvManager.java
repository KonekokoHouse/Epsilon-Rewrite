package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.TickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.InventoryUtils;
import com.github.epsilon.utils.player.MoveUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class InvManager extends Module {

    public static final InvManager INSTANCE = new InvManager();

    private enum OffhandItem {
        None,
        GoldenApple,
        Projectile,
        FishingRod,
        Block
    }

    private enum BowPriority {
        Crossbow,
        PowerBow,
        PunchBow
    }

    private final IntSetting minDelay = intSetting("Min Delay", 50, 0, 1000, 50);
    private final IntSetting delay = intSetting("Delay", 50, 0, 1000, 50);
    private final EnumSetting<OffhandItem> offhandItems = enumSetting("Offhand Items", OffhandItem.None);
    private final BoolSetting autoArmor = boolSetting("Auto Armor", true);
    private final BoolSetting inventoryOnly = boolSetting("Inventory Only", true);
    private final BoolSetting switchSword = boolSetting("Switch Sword", true);
    private final IntSetting swordSlot = intSetting("Sword Slot", 1, 1, 9, 1, switchSword::getValue);
    private final BoolSetting switchBlock = boolSetting("Switch Block", true, () -> !offhandItems.is(OffhandItem.Block));
    private final IntSetting blockSlot = intSetting("Block Slot", 2, 1, 9, 1, () -> switchBlock.getValue() && !offhandItems.is(OffhandItem.Block));
    public final IntSetting maxBlockSize = intSetting("Max Block Size", 256, 64, 512, 64, switchBlock::getValue);
    private final BoolSetting switchPickaxe = boolSetting("Switch Pickaxe", true);
    private final IntSetting pickaxeSlot = intSetting("Pickaxe Slot", 3, 1, 9, 1, switchPickaxe::getValue);
    private final BoolSetting switchAxe = boolSetting("Switch Axe", true);
    private final IntSetting axeSlot = intSetting("Axe Slot", 4, 1, 9, 1, switchAxe::getValue);
    private final BoolSetting switchBow = boolSetting("Switch Bow or Crossbow", true);
    private final IntSetting bowSlot = intSetting("Bow Slot", 5, 1, 9, 1, switchBow::getValue);
    private final EnumSetting<BowPriority> preferBow = enumSetting("Bow Priority", BowPriority.Crossbow, switchBow::getValue);
    public final IntSetting maxArrowSize = intSetting("Max Arrow Size", 256, 64, 512, 64, switchBow::getValue);
    private final BoolSetting switchWaterBucket = boolSetting("Switch Water Bucket", true);
    private final IntSetting waterBucketSlot = intSetting("Water Bucket Slot", 6, 1, 9, 1, switchWaterBucket::getValue);
    private final BoolSetting switchEnderPearl = boolSetting("Switch Ender Pearl", true);
    private final IntSetting enderPearlSlot = intSetting("Ender Pearl Slot", 7, 1, 9, 1, switchEnderPearl::getValue);
    private final BoolSetting switchFireball = boolSetting("Switch Fireball", true);
    private final IntSetting fireballSlot = intSetting("Fireball Slot", 8, 1, 9, 1, switchFireball::getValue);
    private final BoolSetting switchGoldenApple = boolSetting("Switch Golden Apple", true, () -> !offhandItems.is(OffhandItem.GoldenApple));
    private final IntSetting goldenAppleSlot = intSetting("Golden Apple Slot", 9, 1, 9, 1, () -> switchGoldenApple.getValue() && !offhandItems.is(OffhandItem.GoldenApple));
    private final BoolSetting throwItems = boolSetting("Throw Items", true);
    public final IntSetting waterBucketCount = intSetting("Keep Water Buckets", 1, 0, 5, 1, throwItems::getValue);
    public final IntSetting lavaBucketCount = intSetting("Keep Lava Buckets", 1, 0, 5, 1, throwItems::getValue);
    public final BoolSetting keepProjectile = boolSetting("Keep Eggs & Snowballs", true);
    private final BoolSetting switchProjectile = boolSetting("Switch Eggs & Snowballs", false, () -> keepProjectile.getValue() && !offhandItems.is(OffhandItem.Projectile));
    private final IntSetting projectileSlot = intSetting("Eggs & Snowballs Slot", 9, 1, 9, 1, () -> switchProjectile.getValue() && keepProjectile.getValue() && !offhandItems.is(OffhandItem.Projectile));
    public final IntSetting maxProjectileSize = intSetting("Max Eggs & Snowballs Size", 64, 16, 256, 16, keepProjectile::getValue);
    private final BoolSetting switchRod = boolSetting("Switch Rod", false, () -> !offhandItems.is(OffhandItem.FishingRod));
    private final IntSetting rodSlot = intSetting("Rod Slot", 9, 1, 9, 1, () -> switchRod.getValue() && !offhandItems.is(OffhandItem.FishingRod));

    private static final TimerUtils timer = new TimerUtils();
    private static final Random random = new Random();

    private int noMoveTicks = 0;
    private boolean clickOffHand = false;
    private boolean inventoryOpen = false;

    private InvManager() {
        super("Inv Manager", Category.PLAYER);
    }

    public boolean isItemUseful(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (InventoryUtils.isGodItem(stack)) return true;
        if (stack.getDisplayName().getString().contains("点击使用")) return true;
        if (InventoryUtils.isArmor(stack)) {
            float protection = InventoryUtils.getProtection(stack);
            if (InventoryUtils.getCurrentArmorScore(InventoryUtils.getArmorSlot(stack)) >= protection) return false;
            float bestArmor = InventoryUtils.getBestArmorScore(InventoryUtils.getArmorSlot(stack));
            return !(protection < bestArmor);
        }
        if (InventoryUtils.isSword(stack)) return InventoryUtils.getBestSword() == stack;
        if (InventoryUtils.isPickaxe(stack)) return InventoryUtils.getBestPickaxe() == stack;
        if (stack.getItem() instanceof AxeItem && !InventoryUtils.isSharpnessAxe(stack))
            return InventoryUtils.getBestAxe() == stack;
        if (stack.getItem() instanceof ShovelItem) return InventoryUtils.getBestShovel() == stack;
        if (stack.getItem() instanceof CrossbowItem) return InventoryUtils.getBestCrossbow() == stack;
        if (stack.getItem() instanceof BowItem && InventoryUtils.isPunchBow(stack))
            return InventoryUtils.getBestPunchBow() == stack;
        if (stack.getItem() instanceof BowItem && InventoryUtils.isPowerBow(stack))
            return InventoryUtils.getBestPowerBow() == stack;
        if (stack.getItem() instanceof BowItem && InventoryUtils.getItemCount(Items.BOW) > 1) return false;
        if (stack.getItem() == Items.WATER_BUCKET && InventoryUtils.getItemCount(Items.WATER_BUCKET) > waterBucketCount.getValue())
            return false;
        if (stack.getItem() == Items.LAVA_BUCKET && InventoryUtils.getItemCount(Items.LAVA_BUCKET) > lavaBucketCount.getValue())
            return false;
        if (stack.getItem() instanceof FishingRodItem && InventoryUtils.getItemCount(Items.FISHING_ROD) > 1)
            return false;
        if ((stack.getItem() == Items.SNOWBALL || stack.getItem() == Items.EGG) && !keepProjectile.getValue())
            return false;
        if (stack.getItem() == Items.GOLDEN_APPLE || stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE) return true;
        return !(stack.getItem() instanceof StandingAndWallBlockItem) && InventoryUtils.isCommonItemUseful(stack);
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundContainerClosePacket) this.inventoryOpen = false;
        if (this.inventoryOpen && !this.inventoryOnly.getValue()) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacket) {
                if (MoveUtils.isMoving()) {
                    mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));
                }
            } else if (event.getPacket() instanceof ServerboundUseItemOnPacket || event.getPacket() instanceof ServerboundUseItemPacket || event.getPacket() instanceof ServerboundInteractPacket || event.getPacket() instanceof ServerboundPlayerActionPacket) {
                mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));
            }
        }
    }

    private boolean checkConfig() {
        List<Pair<BoolSetting, IntSetting>> pairs = new ArrayList<>();
        if (!this.keepProjectile.getValue()) this.switchProjectile.setValue(false);
        pairs.add(Pair.of(this.switchSword, this.swordSlot));
        pairs.add(Pair.of(this.switchPickaxe, this.pickaxeSlot));
        pairs.add(Pair.of(this.switchAxe, this.axeSlot));
        pairs.add(Pair.of(this.switchBow, this.bowSlot));
        pairs.add(Pair.of(this.switchWaterBucket, this.waterBucketSlot));
        pairs.add(Pair.of(this.switchEnderPearl, this.enderPearlSlot));
        pairs.add(Pair.of(this.switchFireball, this.fireballSlot));
        if (!this.offhandItems.is(OffhandItem.GoldenApple))
            pairs.add(Pair.of(this.switchGoldenApple, this.goldenAppleSlot));
        if (!this.offhandItems.is(OffhandItem.Projectile))
            pairs.add(Pair.of(this.switchProjectile, this.projectileSlot));
        if (!this.offhandItems.is(OffhandItem.FishingRod)) pairs.add(Pair.of(this.switchRod, this.rodSlot));
        if (!this.offhandItems.is(OffhandItem.Block)) pairs.add(Pair.of(this.switchBlock, this.blockSlot));
        Set<Integer> usedSlot = new HashSet<>();
        for (Pair<BoolSetting, IntSetting> pair : pairs) {
            if (pair.getKey().getValue()) {
                int targetSlot = pair.getValue().getValue() - 1;
                if (usedSlot.contains(targetSlot)) return false;
                usedSlot.add(targetSlot);
            }
        }
        return true;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        if (InventoryUtils.shouldDisableFeatures()) return;
        if (MoveUtils.isMoving()) this.noMoveTicks = 0;
        else this.noMoveTicks++;
        boolean allowMove = !this.inventoryOnly.getValue();
        if (Stealer.INSTANCE.isWorking() || (this.inventoryOnly.getValue() ? !(mc.screen instanceof InventoryScreen) : (!allowMove && this.noMoveTicks <= 1))) {
            this.clickOffHand = false;
            return;
        }
        if (mc.screen instanceof AbstractContainerScreen container && container.getMenu().containerId != mc.player.inventoryMenu.containerId)
            return;
        int nextDelay = Math.max(minDelay.getValue(), (int) (this.delay.getValue() + random.nextGaussian() * 50));

        if (this.autoArmor.getValue()) {
            EquipmentSlot[] armorSlots = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
            for (int i = 0; i < armorSlots.length; i++) {
                ItemStack stack = InventoryUtils.getArmorStack(armorSlots[i]);
                if (InventoryUtils.isArmor(stack)) {
                    if (!stack.isEmpty() && timer.passedMillise(nextDelay) && InventoryUtils.getBestArmorScore(armorSlots[i]) > InventoryUtils.getProtection(stack)) {
                        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, 4 + (4 - i), 1, ContainerInput.THROW, mc.player);
                        this.inventoryOpen = true;
                        timer.reset();
                    }
                }
            }
            for (int ix = 0; ix < mc.player.getInventory().getNonEquipmentItems().size(); ix++) {
                ItemStack stack = InventoryUtils.getInventoryStack(ix);
                if (!stack.isEmpty() && InventoryUtils.isArmor(stack)) {
                    float currentItemScore = InventoryUtils.getProtection(stack);
                    boolean isBestItem = InventoryUtils.getBestArmorScore(InventoryUtils.getArmorSlot(stack)) == currentItemScore;
                    boolean isBetterItem = InventoryUtils.getCurrentArmorScore(InventoryUtils.getArmorSlot(stack)) < currentItemScore;
                    if (isBestItem && isBetterItem && timer.passedMillise(nextDelay)) {
                        if (ix < 9)
                            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, ix + 36, 0, ContainerInput.QUICK_MOVE, mc.player);
                        else
                            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, ix, 0, ContainerInput.QUICK_MOVE, mc.player);
                        this.inventoryOpen = true;
                        timer.reset();
                    }
                }
            }
        }

        if (this.clickOffHand && timer.passedMillise(nextDelay)) {
            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, 45, 0, ContainerInput.PICKUP, mc.player);
            this.inventoryOpen = true;
            this.clickOffHand = false;
            timer.reset();
        }

        if (this.offhandItems.is(OffhandItem.GoldenApple)) {
            ItemStack offHand = InventoryUtils.getOffhandStack();
            Item offHandItem = offHand.getItem();

            int egapSlot = InventoryUtils.getItemSlot(Items.ENCHANTED_GOLDEN_APPLE);
            int gapSlot = InventoryUtils.getItemSlot(Items.GOLDEN_APPLE);

            if (offHandItem == Items.ENCHANTED_GOLDEN_APPLE) {
                if (offHand.getCount() < offHand.getMaxStackSize() && egapSlot != -1) {
                    if (timer.passedMillise(nextDelay)) {
                        int targetSlot = egapSlot;
                        if (targetSlot < 9)
                            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, targetSlot + 36, 0, ContainerInput.PICKUP, mc.player);
                        else
                            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, targetSlot, 0, ContainerInput.PICKUP, mc.player);
                        this.inventoryOpen = true;
                        this.clickOffHand = true;
                        timer.reset();
                    }
                }
            } else if (offHandItem == Items.GOLDEN_APPLE) {
                if (egapSlot != -1) {
                    if (timer.passedMillise(nextDelay)) {
                        this.swapOffHand(egapSlot);
                        timer.reset();
                    }
                } else if (offHand.getCount() < offHand.getMaxStackSize() && gapSlot != -1) {
                    if (timer.passedMillise(nextDelay)) {
                        int targetSlot = gapSlot;
                        if (targetSlot < 9)
                            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, targetSlot + 36, 0, ContainerInput.PICKUP, mc.player);
                        else
                            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, targetSlot, 0, ContainerInput.PICKUP, mc.player);
                        this.inventoryOpen = true;
                        this.clickOffHand = true;
                        timer.reset();
                    }
                }
            } else {
                if (egapSlot != -1) {
                    if (timer.passedMillise(nextDelay)) {
                        this.swapOffHand(egapSlot);
                        timer.reset();
                    }
                } else if (gapSlot != -1) {
                    if (timer.passedMillise(nextDelay)) {
                        this.swapOffHand(gapSlot);
                        timer.reset();
                    }
                }
            }
        } else if (this.offhandItems.is(OffhandItem.Projectile)) {
            ItemStack offHand = InventoryUtils.getOffhandStack();
            ItemStack bestProjectile = InventoryUtils.getBestProjectile();
            if (bestProjectile != null) {
                int slot = InventoryUtils.getItemStackSlot(bestProjectile);
                boolean shouldSwap = offHand.getItem() != Items.EGG && offHand.getItem() != Items.SNOWBALL || offHand.getCount() < bestProjectile.getCount();
                if (shouldSwap && slot != -1 && timer.passedMillise(nextDelay)) this.swapOffHand(slot);
            }
        } else if (this.offhandItems.is(OffhandItem.FishingRod)) {
            ItemStack offHand = InventoryUtils.getOffhandStack();
            int slotx = InventoryUtils.getItemSlot(Items.FISHING_ROD);
            if (slotx != -1 && timer.passedMillise(nextDelay) && offHand.getItem() != Items.FISHING_ROD)
                this.swapOffHand(slotx);
        } else if (this.offhandItems.is(OffhandItem.Block)) {
            ItemStack offHand = InventoryUtils.getOffhandStack();
            ItemStack bestBlock = InventoryUtils.getBestBlock();
            if (bestBlock != null) {
                int slotx = InventoryUtils.getItemStackSlot(bestBlock);
                boolean shouldSwapx = !InventoryUtils.isValidStack(offHand) || offHand.getCount() < bestBlock.getCount();
                if (shouldSwapx && slotx != -1 && timer.passedMillise(nextDelay)) this.swapOffHand(slotx);
            }
        }

        if (this.switchGoldenApple.getValue() && !this.offhandItems.is(OffhandItem.GoldenApple)) {
            int targetSlotIdx = this.goldenAppleSlot.getValue() - 1;
            int egapSlot = InventoryUtils.getItemSlot(Items.ENCHANTED_GOLDEN_APPLE);
            int gapSlot = InventoryUtils.getItemSlot(Items.GOLDEN_APPLE);
            int bestGapSlot = (egapSlot != -1) ? egapSlot : gapSlot;
            if (bestGapSlot != -1) {
                ItemStack currentInSlot = InventoryUtils.getInventoryStack(targetSlotIdx);
                ItemStack bestGapItem = InventoryUtils.getInventoryStack(bestGapSlot);
                if (currentInSlot.getItem() != bestGapItem.getItem() || (currentInSlot.getCount() < bestGapItem.getCount() && currentInSlot.getItem() == bestGapItem.getItem())) {
                    this.swapItem(targetSlotIdx, bestGapItem);
                }
            }
        }

        if (this.switchBlock.getValue()) {
            int blockSlotIndex = this.blockSlot.getValue() - 1;
            ItemStack currentBlock = InventoryUtils.getInventoryStack(blockSlotIndex);
            ItemStack bestBlock = InventoryUtils.getBestBlock();
            if (bestBlock != null && (bestBlock.getCount() > currentBlock.getCount() || !InventoryUtils.isValidStack(currentBlock)) && !this.offhandItems.is(OffhandItem.Block)) {
                this.swapItem(blockSlotIndex, bestBlock);
            }
            if ((float) InventoryUtils.getBlockCountInInventory() > this.maxBlockSize.getValue()) {
                this.throwItem(InventoryUtils.getWorstBlock());
            }
        }

        if (this.switchSword.getValue()) {
            int slotIndex = this.swordSlot.getValue() - 1;
            ItemStack currentSword = InventoryUtils.getInventoryStack(slotIndex);
            ItemStack bestSword = InventoryUtils.getBestSword();
            ItemStack bestShapeAxe = InventoryUtils.getBestShapeAxe();
            if (InventoryUtils.getAxeDamage(bestShapeAxe) > InventoryUtils.getSwordDamage(bestSword))
                bestSword = bestShapeAxe;
            if (bestSword != null) {
                float currentDamage = InventoryUtils.isSword(currentSword) ? InventoryUtils.getSwordDamage(currentSword) : InventoryUtils.getAxeDamage(currentSword);
                float bestWeaponDamage = InventoryUtils.isSword(bestSword) ? InventoryUtils.getSwordDamage(bestSword) : InventoryUtils.getAxeDamage(bestSword);
                if (bestWeaponDamage > currentDamage) this.swapItem(slotIndex, bestSword);
            }
        }

        if (this.switchPickaxe.getValue()) {
            int slotIndex = this.pickaxeSlot.getValue() - 1;
            ItemStack bestPickaxe = InventoryUtils.getBestPickaxe();
            ItemStack currentPickaxe = InventoryUtils.getInventoryStack(slotIndex);
            if (InventoryUtils.isPickaxe(bestPickaxe) && (InventoryUtils.getToolScore(bestPickaxe) > InventoryUtils.getToolScore(currentPickaxe) || !InventoryUtils.isPickaxe(currentPickaxe)))
                this.swapItem(slotIndex, bestPickaxe);
        }

        if (this.switchAxe.getValue()) {
            int slotIndex = this.axeSlot.getValue() - 1;
            ItemStack bestAxe = InventoryUtils.getBestAxe();
            ItemStack currentAxe = InventoryUtils.getInventoryStack(slotIndex);
            if (bestAxe != null && bestAxe.getItem() instanceof AxeItem && (InventoryUtils.getToolScore(bestAxe) > InventoryUtils.getToolScore(currentAxe) || !(currentAxe.getItem() instanceof AxeItem)))
                this.swapItem(slotIndex, bestAxe);
        }

        if (this.switchRod.getValue() && !this.offhandItems.is(OffhandItem.FishingRod)) {
            int slotIndex = this.rodSlot.getValue() - 1;
            ItemStack bestRod = InventoryUtils.getFishingRod();
            ItemStack currentRod = InventoryUtils.getInventoryStack(slotIndex);
            if (!(currentRod.getItem() instanceof FishingRodItem)) this.swapItem(slotIndex, bestRod);
        }

        if (this.switchBow.getValue()) {
            int slotIndex = this.bowSlot.getValue() - 1;
            ItemStack currentBow = InventoryUtils.getInventoryStack(slotIndex);
            ItemStack bestBow;
            float bestScore, currentScore;
            if (this.preferBow.is(BowPriority.Crossbow)) {
                bestBow = InventoryUtils.getBestCrossbow();
                bestScore = InventoryUtils.getCrossbowScore(bestBow);
                currentScore = InventoryUtils.getCrossbowScore(currentBow);
            } else if (this.preferBow.is(BowPriority.PowerBow)) {
                bestBow = InventoryUtils.getBestPowerBow();
                bestScore = InventoryUtils.getPowerBowScore(bestBow);
                currentScore = InventoryUtils.getPowerBowScore(currentBow);
            } else {
                bestBow = InventoryUtils.getBestPunchBow();
                bestScore = InventoryUtils.getPunchBowScore(bestBow);
                currentScore = InventoryUtils.getPunchBowScore(currentBow);
            }
            if (bestBow == null) {
                bestBow = InventoryUtils.getBestCrossbow();
                bestScore = InventoryUtils.getCrossbowScore(bestBow);
                currentScore = InventoryUtils.getCrossbowScore(currentBow);
            }
            if (bestBow == null) {
                bestBow = InventoryUtils.getBestPowerBow();
                bestScore = InventoryUtils.getPowerBowScore(bestBow);
                currentScore = InventoryUtils.getPowerBowScore(currentBow);
            }
            if (bestBow == null) {
                bestBow = InventoryUtils.getBestPunchBow();
                bestScore = InventoryUtils.getPunchBowScore(bestBow);
                currentScore = InventoryUtils.getPunchBowScore(currentBow);
            }
            if (bestBow != null && bestScore > currentScore) this.swapItem(slotIndex, bestBow);
            if ((float) InventoryUtils.getItemCount(Items.ARROW) > this.maxArrowSize.getValue())
                this.throwItem(InventoryUtils.getWorstArrow());
        }

        if (this.switchEnderPearl.getValue())
            this.swapItem(this.enderPearlSlot.getValue() - 1, Items.ENDER_PEARL);
        if (this.switchWaterBucket.getValue())
            this.swapItem(this.waterBucketSlot.getValue() - 1, Items.WATER_BUCKET);
        if (this.switchFireball.getValue())
            this.swapItem(this.fireballSlot.getValue() - 1, Items.FIRE_CHARGE);

        if (this.keepProjectile.getValue()) {
            if ((float) (InventoryUtils.getItemCount(Items.EGG) + InventoryUtils.getItemCount(Items.SNOWBALL)) > this.maxProjectileSize.getValue())
                this.throwItem(InventoryUtils.getWorstProjectile());
            if (this.switchProjectile.getValue() && !this.offhandItems.is(OffhandItem.Projectile)) {
                int pSlot = this.projectileSlot.getValue() - 1;
                if (InventoryUtils.getItemCount(Items.EGG) > 0) this.swapItem(pSlot, Items.EGG);
                else if (InventoryUtils.getItemCount(Items.SNOWBALL) > 0) this.swapItem(pSlot, Items.SNOWBALL);
            }
        }

        if (this.throwItems.getValue()) {
            List<Integer> slots = IntStream.range(0, mc.player.getInventory().getNonEquipmentItems().size()).boxed().collect(Collectors.toList());
            Collections.shuffle(slots);
            for (Integer slotIdx : slots) {
                ItemStack stack = InventoryUtils.getInventoryStack(slotIdx);
                if (!stack.isEmpty() && !this.isItemUseful(stack) && timer.passedMillise(nextDelay)) {
                    this.throwItem(stack);
                    timer.reset();
                    return;
                }
            }
        }

    }

    private void swapOffHand(int slot) {
        if (slot < 9) {
            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, slot + 36, 40, ContainerInput.SWAP, mc.player);
        } else {
            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, slot, 40, ContainerInput.SWAP, mc.player);
        }
        this.inventoryOpen = true;
        timer.reset();
    }

    private void throwItem(ItemStack item) {
        int nextDelay = Math.max(minDelay.getValue(), (int) (this.delay.getValue() + random.nextGaussian() * 50));
        if (InventoryUtils.isItemValid(item) && timer.passedMillise(nextDelay)) {
            int itemSlot = InventoryUtils.getItemStackSlot(item);
            if (itemSlot != -1) {
                if (itemSlot < 9) {
                    mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, itemSlot + 36, 1, ContainerInput.THROW, mc.player);
                } else {
                    mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, itemSlot, 1, ContainerInput.THROW, mc.player);
                }
                this.inventoryOpen = true;
                timer.reset();
            }
        }
    }

    private void swapItem(int targetSlot, ItemStack bestItem) {
        ItemStack currentSlot = InventoryUtils.getInventoryStack(targetSlot);
        int nextDelay = Math.max(minDelay.getValue(), (int) (this.delay.getValue() + random.nextGaussian() * 50));
        if (InventoryUtils.isItemValid(currentSlot) && bestItem != currentSlot && timer.passedMillise(nextDelay)) {
            int bestItemSlot = InventoryUtils.getItemStackSlot(bestItem);
            if (bestItemSlot != -1) {
                if (bestItemSlot < 9) {
                    mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, bestItemSlot + 36, targetSlot, ContainerInput.SWAP, mc.player);
                } else {
                    mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, bestItemSlot, targetSlot, ContainerInput.SWAP, mc.player);
                }
                this.inventoryOpen = true;
                timer.reset();
            }
        }
    }

    private void swapItem(int targetSlot, Item item) {
        ItemStack currentSlot = InventoryUtils.getInventoryStack(targetSlot);
        int nextDelay = Math.max(minDelay.getValue(), (int) (this.delay.getValue() + random.nextGaussian() * 50));
        if (InventoryUtils.isItemValid(currentSlot) && timer.passedMillise(nextDelay)) {
            int bestItemSlot = InventoryUtils.getItemSlot(item);
            if (bestItemSlot != -1) {
                ItemStack bestItemStack = InventoryUtils.getInventoryStack(bestItemSlot);
                if (currentSlot.getItem() != item || currentSlot.getItem() == item && currentSlot.getCount() < bestItemStack.getCount()) {
                    if (bestItemSlot < 9) {
                        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, bestItemSlot + 36, targetSlot, ContainerInput.SWAP, mc.player);
                    } else {
                        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, bestItemSlot, targetSlot, ContainerInput.SWAP, mc.player);
                    }
                    this.inventoryOpen = true;
                    timer.reset();
                }
            }
        }
    }

}
