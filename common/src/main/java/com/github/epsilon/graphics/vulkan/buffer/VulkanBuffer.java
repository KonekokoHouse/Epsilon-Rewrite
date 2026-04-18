package com.github.epsilon.graphics.vulkan.buffer;

import com.mojang.blaze3d.vulkan.VulkanUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK12.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK12.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;

/**
 * Vulkan Buffer + VMA Allocation 封装。
 * <p>
 * 支持可选持久映射，适用于上传缓冲或回读缓冲等场景。
 */
public final class VulkanBuffer implements AutoCloseable {

    private final long allocator;
    private final long buffer;
    private final long allocation;
    private final long size;
    private ByteBuffer mappedData;

    private VulkanBuffer(long allocator, long buffer, long allocation, long size, ByteBuffer mappedData) {
        this.allocator = allocator;
        this.buffer = buffer;
        this.allocation = allocation;
        this.size = size;
        this.mappedData = mappedData;
    }

    /**
     * 创建一个 VulkanBuffer。
     */
    public static VulkanBuffer create(
            long allocator,
            long size,
            int usage,
            int memoryUsage,
            int allocationFlags,
            boolean mapped
    ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferCreateInfo = VkBufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(size)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            VmaAllocationCreateInfo allocationCreateInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(memoryUsage)
                    .flags(allocationFlags);

            var pBuffer = stack.mallocLong(1);
            PointerBuffer pAllocation = stack.mallocPointer(1);
            VmaAllocationInfo allocationInfo = mapped ? VmaAllocationInfo.calloc(stack) : null;

            VulkanUtils.crashIfFailure(
                    vmaCreateBuffer(allocator, bufferCreateInfo, allocationCreateInfo, pBuffer, pAllocation, allocationInfo),
                    "Can't create Vulkan buffer"
            );

            ByteBuffer mappedData = null;
            if (mapped) {
                long mappedPtr = allocationInfo.pMappedData();
                if (mappedPtr == MemoryUtil.NULL) {
                    PointerBuffer pMapped = stack.mallocPointer(1);
                    VulkanUtils.crashIfFailure(
                            vmaMapMemory(allocator, pAllocation.get(0), pMapped),
                            "Can't map Vulkan buffer memory"
                    );
                    mappedPtr = pMapped.get(0);
                }
                mappedData = MemoryUtil.memByteBuffer(mappedPtr, Math.toIntExact(size)).order(ByteOrder.LITTLE_ENDIAN);
            }

            return new VulkanBuffer(allocator, pBuffer.get(0), pAllocation.get(0), size, mappedData);
        }
    }

    /**
     * 返回 VkBuffer 句柄。
     */
    public long handle() {
        return buffer;
    }

    /**
     * 返回 VMA Allocation 句柄。
     */
    public long allocation() {
        return allocation;
    }

    /**
     * 返回 buffer 大小（字节）。
     */
    public long size() {
        return size;
    }

    /**
     * 获取映射后的内存视图。
     *
     * @throws IllegalStateException 当该 buffer 未映射时抛出
     */
    public ByteBuffer mappedData() {
        if (mappedData == null) {
            throw new IllegalStateException("Buffer is not mapped");
        }
        return mappedData;
    }

    /**
     * 将 CPU 写入刷新到设备可见（用于非 coherent 内存）。
     */
    public void flush(long offset, long byteCount) {
        vmaFlushAllocation(allocator, allocation, offset, byteCount);
    }

    /**
     * 将设备写入失效到 CPU 可见（用于回读）。
     */
    public void invalidate(long offset, long byteCount) {
        vmaInvalidateAllocation(allocator, allocation, offset, byteCount);
    }

    @Override
    public void close() {
        vmaDestroyBuffer(allocator, buffer, allocation);
        mappedData = null;
    }
}
