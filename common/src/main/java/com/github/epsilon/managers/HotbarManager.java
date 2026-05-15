package com.github.epsilon.managers;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.TickEvent;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.rotation.Priority;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class HotbarManager {

    public static final HotbarManager INSTANCE = new HotbarManager();

    /**
     * 热键栏切换策略。
     * <p>
     * None 只使用当前手中物品；Normal 会可见切换到目标槽位；Silent 会保存原槽位并在恢复时切回；
     * InvSwitch 使用容器 SWAP 操作临时把物品换到当前槽位。
     */
    public enum SwapMode {
        None,
        Normal,
        Silent,
        InvSwitch
    }

    private final Minecraft mc = Minecraft.getInstance();
    private final List<HotbarRequest> tickRequests = new ArrayList<>();
    private final List<RestoreRequest> restoreRequests = new ArrayList<>();

    public int[] invSlots;
    public int previousSlot = -1;

    private boolean hideSwitchAnimation;
    private int hideSwitchTicks;

    private HotbarManager() {
        EventBus.INSTANCE.subscribe(this);
    }

    /**
     * 判断主手物品是否符合条件。
     *
     * @param predicate 物品判断器
     * @return 主手物品符合条件时返回 true
     */
    public boolean testInMainHand(Predicate<ItemStack> predicate) {
        return predicate.test(mc.player.getMainHandItem());
    }

    /**
     * 判断副手物品是否符合条件。
     *
     * @param predicate 物品判断器
     * @return 副手物品符合条件时返回 true
     */
    public boolean testInOffHand(Predicate<ItemStack> predicate) {
        return predicate.test(mc.player.getOffhandItem());
    }

    /**
     * 在副手、主手和热键栏中查找指定物品。
     *
     * @param items 目标物品
     * @return 查找结果，slot 为 -1 表示未找到
     */
    public FindItemResult findInHotbar(Item... items) {
        return findInHotbar(itemStack -> {
            for (Item item : items) {
                if (itemStack.getItem() == item) return true;
            }
            return false;
        });
    }

    /**
     * 在副手、主手和热键栏中查找物品。
     *
     * @param isGood 物品判断器
     * @return 查找结果，slot 为 -1 表示未找到
     */
    public FindItemResult findInHotbar(Predicate<ItemStack> isGood) {
        if (testInOffHand(isGood)) {
            return new FindItemResult(40, mc.player.getOffhandItem().getCount(), mc.player.getOffhandItem().getMaxStackSize());
        } else if (testInMainHand(isGood)) {
            return new FindItemResult(mc.player.getInventory().getSelectedSlot(), mc.player.getMainHandItem().getCount(), mc.player.getMainHandItem().getMaxStackSize());
        }

        return find(isGood, 0, 8);
    }

    /**
     * 在整个玩家物品栏中查找指定物品。
     *
     * @param items 目标物品
     * @return 查找结果，slot 为 -1 表示未找到
     */
    public FindItemResult find(Item... items) {
        return find(itemStack -> {
            for (Item item : items) {
                if (itemStack.getItem() == item) return true;
            }
            return false;
        });
    }

    /**
     * 在整个玩家物品栏中查找物品。
     *
     * @param isGood 物品判断器
     * @return 查找结果，slot 为 -1 表示未找到
     */
    public FindItemResult find(Predicate<ItemStack> isGood) {
        return find(isGood, 0, mc.player.getInventory().getContainerSize());
    }

    /**
     * 在指定槽位范围中查找物品。
     *
     * @param isGood 物品判断器
     * @param start  起始槽位，包含
     * @param end    结束槽位，包含
     * @return 查找结果，slot 为 -1 表示未找到
     */
    public FindItemResult find(Predicate<ItemStack> isGood, int start, int end) {
        int slot = -1, count = 0, maxCount = 0;

        for (int i = start; i <= end; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);

            if (isGood.test(stack)) {
                if (slot == -1) slot = i;
                count += stack.getCount();
                maxCount += stack.getMaxStackSize();
            }
        }

        return new FindItemResult(slot, count, maxCount);
    }

    /**
     * 按切换策略查找物品。
     * <p>
     * None 只检查当前手；InvSwitch 会搜索整个背包；其他模式只搜索副手、主手和热键栏。
     *
     * @param mode   切换策略
     * @param isGood 物品判断器
     * @return 查找结果，slot 为 -1 表示未找到
     */
    public FindItemResult find(SwapMode mode, Predicate<ItemStack> isGood) {
        return switch (mode) {
            case None -> findInHands(isGood);
            case InvSwitch -> find(isGood);
            case Normal, Silent -> findInHotbar(isGood);
        };
    }

    /**
     * 按切换策略查找指定物品。
     *
     * @param mode  切换策略
     * @param items 目标物品
     * @return 查找结果，slot 为 -1 表示未找到
     */
    public FindItemResult find(SwapMode mode, Item... items) {
        return find(mode, itemStack -> {
            for (Item item : items) {
                if (itemStack.getItem() == item) return true;
            }
            return false;
        });
    }

    /**
     * 只在当前主手和副手中查找物品。
     *
     * @param isGood 物品判断器
     * @return 查找结果，slot 为 -1 表示未找到
     */
    public FindItemResult findInHands(Predicate<ItemStack> isGood) {
        if (testInOffHand(isGood)) {
            return new FindItemResult(40, mc.player.getOffhandItem().getCount(), mc.player.getOffhandItem().getMaxStackSize());
        }
        if (testInMainHand(isGood)) {
            return new FindItemResult(mc.player.getInventory().getSelectedSlot(), mc.player.getMainHandItem().getCount(), mc.player.getMainHandItem().getMaxStackSize());
        }
        return new FindItemResult(-1, 0, 0);
    }

    /**
     * 请求在本 tick 选择最优先的热键栏切换并执行回调。
     * <p>
     * 与 RotationManager 的 applyRotation 类似，多个请求会按 priority 选出最高优先级的一项。
     *
     * @param slot     目标热键栏槽位
     * @param saveSwap 是否保存当前槽位，供后续 swapBack 使用
     * @param callback 切换完成后的回调
     */
    public void applySwap(int slot, boolean saveSwap, Consumer<HotbarApplyRecord> callback) {
        applySwap(slot, saveSwap, Priority.Lowest, callback);
    }

    /**
     * 请求在本 tick 执行一次热键栏切换。
     *
     * @param slot     目标热键栏槽位
     * @param saveSwap 是否保存当前槽位，供后续 swapBack 使用
     * @param callback 切换完成后的回调
     */
    public void apply(int slot, boolean saveSwap, Consumer<HotbarApplyRecord> callback) {
        applySwap(slot, saveSwap, callback);
    }

    /**
     * 请求在本 tick 按优先级执行一次热键栏切换。
     *
     * @param slot     目标热键栏槽位
     * @param saveSwap 是否保存当前槽位，供后续 swapBack 使用
     * @param priority 优先级
     * @param callback 切换完成后的回调
     */
    public void applySwap(int slot, boolean saveSwap, Priority priority, Consumer<HotbarApplyRecord> callback) {
        final Priority safePriority = priority == null ? Priority.Lowest : priority;
        tickRequests.add(new HotbarRequest(slot, saveSwap, safePriority.priority, callback));
    }

    /**
     * 请求在本 tick 按优先级执行一次热键栏切换。
     *
     * @param slot     目标热键栏槽位
     * @param saveSwap 是否保存当前槽位，供后续 swapBack 使用
     * @param priority 优先级
     * @param callback 切换完成后的回调
     */
    public void apply(int slot, boolean saveSwap, Priority priority, Consumer<HotbarApplyRecord> callback) {
        applySwap(slot, saveSwap, priority, callback);
    }

    /**
     * 请求在本 tick 按数值优先级执行一次热键栏切换。
     *
     * @param slot     目标热键栏槽位
     * @param saveSwap 是否保存当前槽位，供后续 swapBack 使用
     * @param priority 数值优先级
     * @param callback 切换完成后的回调
     */
    public void applySwap(int slot, boolean saveSwap, int priority, Consumer<HotbarApplyRecord> callback) {
        tickRequests.add(new HotbarRequest(slot, saveSwap, Math.max(Priority.Lowest.priority, priority), callback));
    }

    /**
     * 请求在本 tick 按数值优先级执行一次热键栏切换。
     *
     * @param slot     目标热键栏槽位
     * @param saveSwap 是否保存当前槽位，供后续 swapBack 使用
     * @param priority 数值优先级
     * @param callback 切换完成后的回调
     */
    public void apply(int slot, boolean saveSwap, int priority, Consumer<HotbarApplyRecord> callback) {
        applySwap(slot, saveSwap, priority, callback);
    }

    /**
     * 根据策略立即切换到目标槽位。
     * <p>
     * Silent 和 InvSwitch 会自动登记恢复任务，默认在第二个 Tick 还原。
     *
     * @param mode 切换策略
     * @param slot 目标槽位
     */
    public void swap(SwapMode mode, int slot) {
        swap(mode, slot, 2);
    }

    /**
     * 根据策略立即切换到目标槽位，并指定自动恢复延迟。
     * <p>
     * Silent 和 InvSwitch 会自动登记恢复任务。
     *
     * @param mode              切换策略
     * @param slot              目标槽位
     * @param restoreDelayTicks 自动恢复延迟 tick 数
     */
    public void swap(SwapMode mode, int slot, int restoreDelayTicks) {
        switch (mode) {
            case Normal -> swap(slot, false);
            case Silent -> swap(slot, true, restoreDelayTicks);
            case InvSwitch -> invSwap(slot, restoreDelayTicks);
            case None -> {
            }
        }
    }

    /**
     * 根据策略立即切换到查找结果所在槽位。
     *
     * @param mode   切换策略
     * @param result 查找结果
     */
    public void swap(SwapMode mode, FindItemResult result) {
        swap(mode, result, 2);
    }

    /**
     * 根据策略立即切换到查找结果所在槽位，并指定自动恢复延迟。
     *
     * @param mode              切换策略
     * @param result            查找结果
     * @param restoreDelayTicks 自动恢复延迟 tick 数
     */
    public void swap(SwapMode mode, FindItemResult result, int restoreDelayTicks) {
        if (result != null && result.found() && result.isMainHand()) {
            swap(mode, result.slot(), restoreDelayTicks);
        }
    }

    /**
     * 立即切换热键栏槽位。
     * <p>
     * saveSwap 为 true 且切换成功时，会自动登记恢复任务，默认在第二个 Tick 切回。
     *
     * @param slot     目标热键栏槽位
     * @param saveSwap 是否保存当前槽位，供后续 swapBack 使用
     */
    public void swap(int slot, boolean saveSwap) {
        swap(slot, saveSwap, 2);
    }

    /**
     * 立即切换热键栏槽位，并指定自动恢复延迟。
     * <p>
     * saveSwap 为 true 且切换成功时，会自动登记恢复任务。
     *
     * @param slot              目标热键栏槽位
     * @param saveSwap          是否保存当前槽位，供自动恢复使用
     * @param restoreDelayTicks 自动恢复延迟 tick 数
     */
    public void swap(int slot, boolean saveSwap, int restoreDelayTicks) {
        applySlot(slot, saveSwap);
        if (saveSwap) {
            queueRestore(SwapMode.Silent, restoreDelayTicks);
        }
    }

    /**
     * 延迟到第二个 Tick 切回保存的热键栏槽位。
     */
    public void swapBack() {
        swapBack(2);
    }

    /**
     * 延迟指定 Tick 数后切回保存的热键栏槽位。
     *
     * @param delayTicks 延迟 tick 数
     */
    public void swapBack(int delayTicks) {
        queueRestore(SwapMode.Silent, delayTicks);
    }

    /**
     * 立即切回保存的热键栏槽位。
     */
    public void swapBackNow() {
        if (previousSlot == -1) return;
        swap(previousSlot, false);
        previousSlot = -1;
    }

    /**
     * 立即执行背包 SWAP，把指定槽位物品临时换到当前热键栏。
     * <p>
     * 切换成功后会自动登记恢复任务，默认在第二个 Tick 还原。
     *
     * @param slot 背包或热键栏槽位
     */
    public void invSwap(int slot) {
        invSwap(slot, 2);
    }

    /**
     * 立即执行背包 SWAP，并指定自动恢复延迟。
     *
     * @param slot              背包或热键栏槽位
     * @param restoreDelayTicks 自动恢复延迟 tick 数
     */
    public void invSwap(int slot, int restoreDelayTicks) {
        if (!isValidSlot(slot) || mc.gameMode == null) return;

        int containerSlot = slot;
        if (slot < 9) containerSlot += 36;
        else if (slot == 40) containerSlot = 45;

        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, containerSlot, selectedSlot, ContainerInput.SWAP, mc.player);
        invSlots = new int[]{containerSlot, selectedSlot};
        hideSwitchAnimation = true;
        hideSwitchTicks = 2;
        queueRestore(SwapMode.InvSwitch, restoreDelayTicks);
    }

    /**
     * 延迟到第二个 Tick 还原最近一次背包 SWAP。
     */
    public void invSwapBack() {
        invSwapBack(2);
    }

    /**
     * 延迟指定 Tick 数后还原最近一次背包 SWAP。
     *
     * @param delayTicks 延迟 tick 数
     */
    public void invSwapBack(int delayTicks) {
        queueRestore(SwapMode.InvSwitch, delayTicks);
    }

    /**
     * 立即还原最近一次背包 SWAP。
     */
    public void invSwapBackNow() {
        if (invSlots == null || invSlots.length < 2) return;
        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, invSlots[0], invSlots[1], ContainerInput.SWAP, mc.player);
        hideSwitchAnimation = true;
        hideSwitchTicks = 2;
    }

    /**
     * 按策略延迟到第二个 Tick 还原切换。
     *
     * @param mode 切换策略
     */
    public void restore(SwapMode mode) {
        restore(mode, 2);
    }

    /**
     * 按策略延迟指定 Tick 数还原切换。
     *
     * @param mode       切换策略
     * @param delayTicks 延迟 tick 数
     */
    public void restore(SwapMode mode, int delayTicks) {
        queueRestore(mode, delayTicks);
    }

    /**
     * 按策略立即还原切换。
     *
     * @param mode 切换策略
     */
    public void restoreNow(SwapMode mode) {
        switch (mode) {
            case Silent -> swapBackNow();
            case InvSwitch -> invSwapBackNow();
            case None, Normal -> {
            }
        }
    }

    /**
     * 当前是否应该隐藏热键栏切换动画和物品名提示。
     *
     * @return 需要隐藏时返回 true
     */
    public boolean shouldHideSwitchAnimation() {
        return hideSwitchAnimation;
    }

    /**
     * 手动标记接下来的切换动画需要隐藏。
     */
    public void markSwitchAnimationHidden() {
        hideSwitchAnimation = true;
        hideSwitchTicks = Math.max(hideSwitchTicks, 2);
    }

    private void queueRestore(SwapMode mode, int delayTicks) {
        if (mode == null || mode == SwapMode.None || mode == SwapMode.Normal) return;
        int safeDelay = Math.max(0, delayTicks);
        restoreRequests.removeIf(request -> request.mode() == mode);
        int executeTick = mc.player == null ? safeDelay : mc.player.tickCount + safeDelay;
        restoreRequests.add(new RestoreRequest(mode, executeTick));
    }

    private HotbarApplyRecord applySlot(int slot, boolean saveSwap) {
        int beforeSlot = mc.player.getInventory().getSelectedSlot();
        boolean changed = false;

        if (isHotbarSlot(slot) && beforeSlot != slot) {
            if (saveSwap && previousSlot == -1) {
                previousSlot = beforeSlot;
            } else if (!saveSwap) {
                previousSlot = -1;
            }

            mc.player.getInventory().setSelectedSlot(slot);
            changed = true;
            hideSwitchAnimation = true;
            hideSwitchTicks = 2;
        }

        return new HotbarApplyRecord(beforeSlot, mc.player.getInventory().getSelectedSlot(), slot, changed);
    }

    private boolean isValidSlot(int slot) {
        return isHotbarSlot(slot) || slot == 40 || (slot >= 0 && slot < mc.player.getInventory().getContainerSize());
    }

    private boolean isHotbarSlot(int slot) {
        return slot >= 0 && slot <= 8;
    }

    @EventHandler(priority = -250)
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) {
            tickRequests.clear();
            restoreRequests.clear();
            hideSwitchAnimation = false;
            hideSwitchTicks = 0;
            return;
        }

        if (!tickRequests.isEmpty()) {
            HotbarRequest selectedRequest = tickRequests.getFirst();
            for (int i = 1; i < tickRequests.size(); i++) {
                HotbarRequest request = tickRequests.get(i);
                if (request.priorityValue() > selectedRequest.priorityValue()) {
                    selectedRequest = request;
                }
            }

            HotbarApplyRecord record = applySlot(selectedRequest.slot(), selectedRequest.saveSwap());

            if (selectedRequest.callback() != null) {
                try {
                    selectedRequest.callback().accept(record);
                } catch (Exception ignored) {
                }
            }

            if (selectedRequest.saveSwap()) {
                queueRestore(SwapMode.Silent, 2);
            }

            tickRequests.clear();
        }

        if (!restoreRequests.isEmpty()) {
            tickRestoreRequests();
        } else if (hideSwitchTicks > 0) {
            hideSwitchTicks--;
            if (hideSwitchTicks <= 0) {
                hideSwitchAnimation = false;
            }
        }
    }

    private void tickRestoreRequests() {
        for (int i = restoreRequests.size() - 1; i >= 0; i--) {
            RestoreRequest request = restoreRequests.get(i);
            if (mc.player.tickCount < request.executeTick()) {
                continue;
            }

            restoreNow(request.mode());
            restoreRequests.remove(i);
        }
    }

    private record HotbarRequest(int slot, boolean saveSwap, int priorityValue, Consumer<HotbarApplyRecord> callback) {
    }

    private record RestoreRequest(SwapMode mode, int executeTick) {
    }

    public record HotbarApplyRecord(int previousSlot, int selectedSlot, int requestedSlot, boolean changed) {
    }

}
