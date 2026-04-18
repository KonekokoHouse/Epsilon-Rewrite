package com.github.epsilon.graphics.vulkan.buffer;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.util.Objects;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK12.*;

/**
 * std430 数据上传缓冲封装。
 * <p>
 * 内部包含 staging(host visible) 与 gpu(device local) 两个 buffer，
 * 通过 vkCmdCopyBuffer 将 CPU 写入数据上传到 GPU。
 */
public final class VulkanStd430Buffer implements AutoCloseable {

    private final VulkanBuffer staging;
    private final VulkanBuffer gpu;
    private final Std430Writer writer;

    /**
     * 创建 std430 上传缓冲。
     *
     * @param gpuUsageFlags 目标 GPU buffer 用途位（会自动附加 TRANSFER_DST）
     */
    public VulkanStd430Buffer(long allocator, long sizeBytes, int gpuUsageFlags) {
        this.staging = VulkanBuffer.create(
                allocator,
                sizeBytes,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VMA_MEMORY_USAGE_CPU_TO_GPU,
                VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT | VMA_ALLOCATION_CREATE_MAPPED_BIT,
                true
        );

        this.gpu = VulkanBuffer.create(
                allocator,
                sizeBytes,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | gpuUsageFlags,
                VMA_MEMORY_USAGE_GPU_ONLY,
                0,
                false
        );

        this.writer = new Std430Writer(staging.mappedData());
    }

    /**
     * 快速创建 storage buffer 用途的 std430 上传缓冲。
     */
    public static VulkanStd430Buffer storageBuffer(long allocator, long sizeBytes) {
        return new VulkanStd430Buffer(allocator, sizeBytes, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
    }

    /**
     * 获取 std430 写入器。
     */
    public Std430Writer writer() {
        return writer;
    }

    /**
     * 返回 GPU 侧 buffer 句柄。
     */
    public long gpuBuffer() {
        return gpu.handle();
    }

    /**
     * 返回 staging buffer 句柄。
     */
    public long stagingBuffer() {
        return staging.handle();
    }

    /**
     * 返回缓冲总字节数。
     */
    public long size() {
        return gpu.size();
    }

    /**
     * 兼容方法：将当前已写入数据复制到 GPU。
     */
    public void map(VkCommandBuffer cmdBuf) {
        copy(cmdBuf, writer.writtenBytes());
    }

    /**
     * 将当前已写入字节数复制到 GPU。
     */
    public void copy(VkCommandBuffer cmdBuf) {
        copy(cmdBuf, writer.writtenBytes());
    }

    /**
     * 复制指定字节数到 GPU。
     */
    public void copy(VkCommandBuffer cmdBuf, long byteCount) {
        Objects.requireNonNull(cmdBuf, "cmdBuf");

        if (byteCount < 0 || byteCount > size()) {
            throw new IllegalArgumentException("byteCount out of range: " + byteCount);
        }

        staging.flush(0, byteCount);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCopy.Buffer regions = VkBufferCopy.calloc(1, stack)
                    .srcOffset(0)
                    .dstOffset(0)
                    .size(byteCount);

            vkCmdCopyBuffer(cmdBuf, staging.handle(), gpu.handle(), regions);
        }
    }

    /**
     * 销毁 staging 与 GPU buffer。
     */
    @Override
    public void close() {
        gpu.close();
        staging.close();
    }
}
