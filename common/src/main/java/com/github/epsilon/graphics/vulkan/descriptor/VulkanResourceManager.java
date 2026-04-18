package com.github.epsilon.graphics.vulkan.descriptor;

import com.mojang.blaze3d.vulkan.VulkanUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.*;

import static org.lwjgl.vulkan.VK12.*;

/**
 * Vulkan 资源管理器：负责 DescriptorPool/DescriptorSet 的复用与回收。
 * <p>
 * 该类会按布局规格聚合为池键进行分桶，优先复用可用池，
 * 从而避免频繁创建与销毁 descriptor pool。
 */
public final class VulkanResourceManager implements AutoCloseable {

    private static final int DEFAULT_POOL_MAX_SETS = 32;

    private final VkDevice device;
    private final int poolMaxSets;
    private final Map<PoolKey, List<DescriptorPoolState>> poolsByKey = new HashMap<>();
    private boolean closed;

    /**
     * 使用默认池容量创建资源管理器。
     */
    public VulkanResourceManager(VkDevice device) {
        this(device, DEFAULT_POOL_MAX_SETS);
    }

    /**
     * 创建资源管理器。
     *
     * @param poolMaxSets 单个 descriptor pool 可分配的 set 上限
     */
    public VulkanResourceManager(VkDevice device, int poolMaxSets) {
        this.device = Objects.requireNonNull(device, "device");
        if (poolMaxSets <= 0) {
            throw new IllegalArgumentException("poolMaxSets must be > 0");
        }
        this.poolMaxSets = poolMaxSets;
    }

    /**
     * 分配并写入一个 descriptor set。
     * <p>
     * 返回的 {@link ManagedDescriptorSet} 需在不用时 close，以便归还到池。
     */
    public synchronized ManagedDescriptorSet allocateDescriptorSet(
            long descriptorSetLayout,
            DescriptorLayoutSpec layoutSpec,
            List<DescriptorSetWrite> writes
    ) {
        ensureOpen();
        Objects.requireNonNull(layoutSpec, "layoutSpec");
        Objects.requireNonNull(writes, "writes");

        PoolKey key = PoolKey.fromLayoutSpec(layoutSpec);
        DescriptorPoolState pool = findOrCreatePool(key);
        long descriptorSet = allocateFromPool(pool, descriptorSetLayout);

        try {
            updateDescriptorSet(descriptorSet, layoutSpec.bindings(), writes);
            pool.allocatedSets++;
            return new ManagedDescriptorSet(this, pool, descriptorSet);
        } catch (RuntimeException e) {
            freeDescriptorSet(pool, descriptorSet);
            throw e;
        }
    }

    private DescriptorPoolState findOrCreatePool(PoolKey key) {
        List<DescriptorPoolState> candidates = poolsByKey.computeIfAbsent(key, ignored -> new ArrayList<>());
        for (DescriptorPoolState candidate : candidates) {
            if (candidate.allocatedSets < candidate.maxSets) {
                return candidate;
            }
        }

        DescriptorPoolState created = createPoolState(key);
        candidates.add(created);
        return created;
    }

    private DescriptorPoolState createPoolState(PoolKey key) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(key.descriptorCounts.size(), stack);

            int idx = 0;
            for (var entry : key.descriptorCounts.entrySet()) {
                int totalCount = Math.multiplyExact(entry.getValue(), poolMaxSets);
                poolSizes.get(idx)
                        .type(entry.getKey())
                        .descriptorCount(totalCount);
                idx++;
            }

            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .flags(VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
                    .pPoolSizes(poolSizes)
                    .maxSets(poolMaxSets);

            var pPool = stack.mallocLong(1);
            VulkanUtils.crashIfFailure(
                    vkCreateDescriptorPool(device, poolInfo, null, pPool),
                    "Can't create reusable descriptor pool"
            );

            return new DescriptorPoolState(pPool.get(0), poolMaxSets);
        }
    }

    private long allocateFromPool(DescriptorPoolState pool, long descriptorSetLayout) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                    .descriptorPool(pool.pool)
                    .pSetLayouts(stack.longs(descriptorSetLayout));

            var pSet = stack.mallocLong(1);
            VulkanUtils.crashIfFailure(
                    vkAllocateDescriptorSets(device, allocInfo, pSet),
                    "Can't allocate descriptor set"
            );
            return pSet.get(0);
        }
    }

    private void updateDescriptorSet(
            long descriptorSet,
            List<DescriptorBindingSpec> bindingSpecs,
            List<DescriptorSetWrite> writes
    ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Map<Integer, DescriptorBindingSpec> specByBinding = new LinkedHashMap<>();
            for (DescriptorBindingSpec spec : bindingSpecs) {
                specByBinding.put(spec.binding(), spec);
            }

            VkWriteDescriptorSet.Buffer vkWrites = VkWriteDescriptorSet.calloc(writes.size(), stack);

            for (int i = 0; i < writes.size(); i++) {
                DescriptorSetWrite write = writes.get(i);
                DescriptorBindingSpec expected = specByBinding.get(write.binding());
                if (expected == null) {
                    throw new IllegalArgumentException("Binding " + write.binding() + " is not declared in layout spec");
                }
                if (expected.descriptorType() != write.descriptorType()) {
                    throw new IllegalArgumentException("Descriptor type mismatch at binding " + write.binding());
                }
                if (write.descriptorCount() > expected.descriptorCount()) {
                    throw new IllegalArgumentException("Descriptor count exceeds layout declaration at binding " + write.binding());
                }

                VkWriteDescriptorSet vkWrite = vkWrites.get(i)
                        .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                        .dstSet(descriptorSet)
                        .dstBinding(write.binding())
                        .dstArrayElement(0)
                        .descriptorType(write.descriptorType())
                        .descriptorCount(write.descriptorCount());

                if (write.isBufferDescriptor()) {
                    if (write.buffer() == VK_NULL_HANDLE) {
                        throw new IllegalArgumentException("Buffer handle is required for binding " + write.binding());
                    }
                    VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                            .buffer(write.buffer())
                            .offset(write.offset())
                            .range(write.range());
                    vkWrite.pBufferInfo(bufferInfo);
                } else if (write.isImageDescriptor()) {
                    if (write.imageView() == VK_NULL_HANDLE) {
                        throw new IllegalArgumentException("Image view is required for binding " + write.binding());
                    }
                    VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                            .sampler(write.sampler())
                            .imageView(write.imageView())
                            .imageLayout(write.imageLayout());
                    vkWrite.pImageInfo(imageInfo);
                } else {
                    throw new IllegalArgumentException("Unsupported descriptor type " + write.descriptorType());
                }
            }

            vkUpdateDescriptorSets(device, vkWrites, null);
        }
    }

    private void freeDescriptorSet(DescriptorPoolState pool, long descriptorSet) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VulkanUtils.crashIfFailure(
                    vkFreeDescriptorSets(device, pool.pool, stack.longs(descriptorSet)),
                    "Can't free descriptor set"
            );
        }
    }

    private synchronized void release(DescriptorPoolState pool, long descriptorSet) {
        if (closed || descriptorSet == VK_NULL_HANDLE) {
            return;
        }

        freeDescriptorSet(pool, descriptorSet);
        pool.allocatedSets = Math.max(0, pool.allocatedSets - 1);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("VulkanResourceManager is already closed");
        }
    }

    @Override
    /**
     * 关闭管理器并销毁其创建的所有 descriptor pool。
     */
    public synchronized void close() {
        if (closed) return;

        for (List<DescriptorPoolState> states : poolsByKey.values()) {
            for (DescriptorPoolState state : states) {
                vkDestroyDescriptorPool(device, state.pool, null);
            }
        }

        poolsByKey.clear();
        closed = true;
    }

    /**
     * 由资源管理器分配的 descriptor set 租约对象。
     * <p>
     * close 后会自动执行 vkFreeDescriptorSets 归还到所属池。
     */
    public static final class ManagedDescriptorSet implements AutoCloseable {

        private final VulkanResourceManager manager;
        private final DescriptorPoolState pool;
        private long descriptorSet;

        private ManagedDescriptorSet(VulkanResourceManager manager, DescriptorPoolState pool, long descriptorSet) {
            this.manager = manager;
            this.pool = pool;
            this.descriptorSet = descriptorSet;
        }

        /**
         * 返回底层 VkDescriptorSet 句柄。
         */
        public long handle() {
            return descriptorSet;
        }

        /**
         * 归还 descriptor set 到资源管理器。
         */
        @Override
        public void close() {
            if (descriptorSet == VK_NULL_HANDLE) {
                return;
            }
            manager.release(pool, descriptorSet);
            descriptorSet = VK_NULL_HANDLE;
        }
    }

    private static final class DescriptorPoolState {

        private final long pool;
        private final int maxSets;
        private int allocatedSets;

        private DescriptorPoolState(long pool, int maxSets) {
            this.pool = pool;
            this.maxSets = maxSets;
        }
    }

    private static final class PoolKey {

        private final Map<Integer, Integer> descriptorCounts;

        private PoolKey(Map<Integer, Integer> descriptorCounts) {
            this.descriptorCounts = descriptorCounts;
        }

        private static PoolKey fromLayoutSpec(DescriptorLayoutSpec spec) {
            Map<Integer, Integer> counts = new TreeMap<>();
            for (DescriptorBindingSpec binding : spec.bindings()) {
                counts.merge(binding.descriptorType(), binding.descriptorCount(), Integer::sum);
            }
            return new PoolKey(Map.copyOf(counts));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PoolKey poolKey)) return false;
            return Objects.equals(descriptorCounts, poolKey.descriptorCounts);
        }

        @Override
        public int hashCode() {
            return Objects.hash(descriptorCounts);
        }
    }
}
