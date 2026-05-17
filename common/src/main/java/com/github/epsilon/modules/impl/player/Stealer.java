package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.TickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.InventoryUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Stealer extends Module {

    public static final Stealer INSTANCE = new Stealer();

    private Stealer() {
        super("Stealer", Category.PLAYER);
    }

    private final IntSetting minDelay = intSetting("Min Delay", 50, 0, 1000, 50);
    private final IntSetting delay = intSetting("Delay", 50, 0, 1000, 50);
    private final BoolSetting closeDelay = boolSetting("Close Delay", true);
    private final IntSetting cDelay = intSetting("Close Delay Value", 150, 0, 1000, 1, closeDelay::getValue);
    private final BoolSetting pickEnderChest = boolSetting("Ender Chest", false);

    private Screen lastTickScreen;

    private static final TimerUtils timer = new TimerUtils();
    private static final Random random = new Random();

    public boolean isWorking() {
        return !timer.hasDelayed(3);
    }

    public static boolean isItemUseful(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        } else if (InventoryUtils.isGodItem(stack) || InventoryUtils.isSharpnessAxe(stack)) {
            return true;
        } else if (InventoryUtils.isArmor(stack)) {
            float protection = InventoryUtils.getProtection(stack);
            float bestArmor = InventoryUtils.getBestArmorScore(InventoryUtils.getArmorSlot(stack));
            return !(protection <= bestArmor);
        } else if (InventoryUtils.isSword(stack)) {
            float damage = InventoryUtils.getSwordDamage(stack);
            float bestDamage = InventoryUtils.getBestSwordDamage();
            return !(damage <= bestDamage);
        } else if (InventoryUtils.isPickaxe(stack)) {
            float score = InventoryUtils.getToolScore(stack);
            float bestScore = InventoryUtils.getBestPickaxeScore();
            return !(score <= bestScore);
        } else if (stack.getItem() instanceof AxeItem) {
            float score = InventoryUtils.getToolScore(stack);
            float bestScore = InventoryUtils.getBestAxeScore();
            return !(score <= bestScore);
        } else if (stack.getItem() instanceof ShovelItem) {
            float score = InventoryUtils.getToolScore(stack);
            float bestScore = InventoryUtils.getBestShovelScore();
            return !(score <= bestScore);
        } else if (stack.getItem() instanceof CrossbowItem) {
            float score = InventoryUtils.getCrossbowScore(stack);
            float bestScore = InventoryUtils.getBestCrossbowScore();
            return !(score <= bestScore);
        } else if (stack.getItem() instanceof BowItem && InventoryUtils.isPunchBow(stack)) {
            float score = InventoryUtils.getPunchBowScore(stack);
            float bestScore = InventoryUtils.getBestPunchBowScore();
            return !(score <= bestScore);
        } else if (stack.getItem() instanceof BowItem && InventoryUtils.isPowerBow(stack)) {
            float score = InventoryUtils.getPowerBowScore(stack);
            float bestScore = InventoryUtils.getBestPowerBowScore();
            return !(score <= bestScore);
        } else if (stack.getItem() == Items.COMPASS) {
            return !InventoryUtils.hasItem(stack.getItem());
        } else if (stack.getItem() == Items.WATER_BUCKET && InventoryUtils.getItemCount(Items.WATER_BUCKET) >= InvManager.INSTANCE.waterBucketCount.getValue()) {
            return false;
        } else if (stack.getItem() == Items.LAVA_BUCKET && InventoryUtils.getItemCount(Items.LAVA_BUCKET) >= InvManager.INSTANCE.lavaBucketCount.getValue()) {
            return false;
        } else if (stack.getItem() instanceof BlockItem
                && InventoryUtils.isValidStack(stack)
                && InventoryUtils.getBlockCountInInventory() + stack.getCount() >= InvManager.INSTANCE.maxBlockSize.getValue()) {
            return false;
        } else if (stack.getItem() == Items.ARROW && InventoryUtils.getItemCount(Items.ARROW) + stack.getCount() >= InvManager.INSTANCE.maxArrowSize.getValue()) {
            return false;
        } else if (stack.getItem() instanceof FishingRodItem && InventoryUtils.getItemCount(Items.FISHING_ROD) >= 1) {
            return false;
        } else if (stack.getItem() != Items.SNOWBALL && stack.getItem() != Items.EGG
                || InventoryUtils.getItemCount(Items.SNOWBALL) + InventoryUtils.getItemCount(Items.EGG) + stack.getCount() < InvManager.INSTANCE.maxProjectileSize.getValue()
                && InvManager.INSTANCE.keepProjectile.getValue()
        ) {
            return !(stack.getItem() instanceof StandingAndWallBlockItem) && InventoryUtils.isCommonItemUseful(stack);
        } else {
            return false;
        }
    }

    private static boolean isBestItemInChest(ChestMenu menu, ItemStack stack) {
        if (!InventoryUtils.isGodItem(stack) && !InventoryUtils.isSharpnessAxe(stack)) {
            for (int i = 0; i < menu.getRowCount() * 9; i++) {
                ItemStack checkStack = menu.getSlot(i).getItem();
                if (InventoryUtils.isArmor(stack) && InventoryUtils.isArmor(checkStack)) {
                    if (InventoryUtils.getArmorSlot(stack) == InventoryUtils.getArmorSlot(checkStack)
                            && InventoryUtils.getProtection(checkStack) > InventoryUtils.getProtection(stack)) {
                        return false;
                    }
                } else if (InventoryUtils.isSword(stack) && InventoryUtils.isSword(checkStack)) {
                    if (InventoryUtils.getSwordDamage(checkStack) > InventoryUtils.getSwordDamage(stack)) {
                        return false;
                    }
                } else if (InventoryUtils.isPickaxe(stack) && InventoryUtils.isPickaxe(checkStack)) {
                    if (InventoryUtils.getToolScore(checkStack) > InventoryUtils.getToolScore(stack)) {
                        return false;
                    }
                } else if (stack.getItem() instanceof AxeItem && checkStack.getItem() instanceof AxeItem) {
                    if (InventoryUtils.getToolScore(checkStack) > InventoryUtils.getToolScore(stack)) {
                        return false;
                    }
                } else if (stack.getItem() instanceof ShovelItem
                        && checkStack.getItem() instanceof ShovelItem
                        && InventoryUtils.getToolScore(checkStack) > InventoryUtils.getToolScore(stack)) {
                    return false;
                }
            }

            return true;
        } else {
            return true;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;
        Screen currentScreen = mc.screen;
        if (currentScreen instanceof AbstractContainerScreen<?> container && container.getMenu() instanceof ChestMenu menu) {
            if (currentScreen != this.lastTickScreen) {
                timer.reset();
            } else {
                String chestTitle = container.getTitle().getString();
                String chest = Component.translatable("container.chest").getString();
                String largeChest = Component.translatable("container.chestDouble").getString();
                String enderChest = Component.translatable("container.enderchest").getString();
                if (chestTitle.equals(chest)
                        || chestTitle.equals(largeChest)
                        || chestTitle.equals("Chest")
                        || this.pickEnderChest.getValue() && chestTitle.equals(enderChest)
                ) {
                    int nextDelay = Math.max(minDelay.getValue(), (int) (this.delay.getValue() + random.nextGaussian() * 50));
                    if (this.isChestEmpty(menu) && timer.passedMillise(nextDelay)) {
                        if (mc.player != null && closeDelay.getValue() && timer.passedMillise(cDelay.getValue())) {
                            mc.player.closeContainer();
                            timer.reset();
                        }

                    } else {
                        List<Integer> slots = IntStream.range(0, menu.getRowCount() * 9).boxed().collect(Collectors.toList());
                        Collections.shuffle(slots);

                        for (Integer pSlotId : slots) {
                            ItemStack stack = menu.getSlot(pSlotId).getItem();
                            if (isItemUseful(stack) && isBestItemInChest(menu, stack) && timer.passedMillise(nextDelay)) {
                                mc.gameMode.handleContainerInput(menu.containerId, pSlotId, 0, ContainerInput.QUICK_MOVE, mc.player);
                                timer.reset();
                                break;
                            }
                        }
                    }
                }
            }
        }

        this.lastTickScreen = currentScreen;
    }

    private boolean isChestEmpty(ChestMenu menu) {
        for (int i = 0; i < menu.getRowCount() * 9; i++) {
            ItemStack item = menu.getSlot(i).getItem();
            if (!item.isEmpty() && isItemUseful(item) && isBestItemInChest(menu, item)) {
                return false;
            }
        }

        return true;
    }

}
