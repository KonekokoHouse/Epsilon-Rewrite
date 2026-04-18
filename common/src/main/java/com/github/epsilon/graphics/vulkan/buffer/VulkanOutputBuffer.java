package com.github.epsilon.graphics.vulkan.buffer;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK12.*;

/**
 * 计算输出回读缓冲封装。
 * <p>
 * 内部维护 GPU 输出 buffer 与 CPU 可读 readback buffer，
 * 通过 vkCmdCopyBuffer 将结果复制到映射内存。
 *
 * <p>同步由调用方负责：
 * <ul>
 *     <li>命令录制时插入合适的 pipeline barrier</li>
 *     <li>CPU 读取前等待 fence 完成</li>
 * </ul>
 */
public final class VulkanOutputBuffer implements AutoCloseable {

    private final VulkanBuffer gpu;
    private final VulkanBuffer readback;

    /**
     * 创建输出回读缓冲。
     */
    public VulkanOutputBuffer(long allocator, long sizeBytes, int gpuUsageFlags) {
        this.gpu = VulkanBuffer.create(
                allocator,
                sizeBytes,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT | gpuUsageFlags,
                VMA_MEMORY_USAGE_GPU_ONLY,
                0,
                false
        );

        this.readback = VulkanBuffer.create(
                allocator,
                sizeBytes,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VMA_MEMORY_USAGE_GPU_TO_CPU,
                VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT | VMA_ALLOCATION_CREATE_MAPPED_BIT,
                true
        );
    }

    /**
     * 快速创建 storage buffer 用途的输出回读缓冲。
     */
    public static VulkanOutputBuffer storageBuffer(long allocator, long sizeBytes) {
        return new VulkanOutputBuffer(allocator, sizeBytes, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
    }

    /**
     * 返回 GPU 输出 buffer 句柄。
     */
    public long gpuBuffer() {
        return gpu.handle();
    }

    /**
     * 返回 readback buffer 句柄。
     */
    public long readbackBuffer() {
        return readback.handle();
    }

    /**
     * 返回缓冲总字节数。
     */
    public long size() {
        return gpu.size();
    }

    /**
     * 兼容方法：复制全部字节到 readback。
     */
    public void map(VkCommandBuffer cmdBuf) {
        copyToReadback(cmdBuf, size());
    }

    /**
     * 复制全部字节到 readback。
     */
    public void copyToReadback(VkCommandBuffer cmdBuf) {
        copyToReadback(cmdBuf, size());
    }

    /**
     * 复制指定字节数到 readback。
     */
    public void copyToReadback(VkCommandBuffer cmdBuf, long byteCount) {
        Objects.requireNonNull(cmdBuf, "cmdBuf");
        validateRange(0, byteCount);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCopy.Buffer region = VkBufferCopy.calloc(1, stack)
                    .srcOffset(0)
                    .dstOffset(0)
                    .size(byteCount);
            vkCmdCopyBuffer(cmdBuf, gpu.handle(), readback.handle(), region);
        }
    }

    /**
     * 读取 [0, byteCount) 范围数据。
     */
    public ByteBuffer readMapped(long byteCount) {
        return readMapped(0, byteCount);
    }

    /**
     * 读取指定范围数据，返回小端序切片。
     */
    public ByteBuffer readMapped(long offset, long byteCount) {
        validateRange(offset, byteCount);

        readback.invalidate(offset, byteCount);

        ByteBuffer source = readback.mappedData().duplicate();
        int start = Math.toIntExact(offset);
        int end = Math.toIntExact(offset + byteCount);
        source.position(start);
        source.limit(end);
        return source.slice().order(ByteOrder.LITTLE_ENDIAN);
    }

    private void validateRange(long offset, long byteCount) {
        if (offset < 0 || byteCount < 0 || offset + byteCount > size()) {
            throw new IllegalArgumentException("Invalid range: offset=" + offset + ", byteCount=" + byteCount);
        }
    }

    /**
     * 销毁 readback 与 GPU buffer。
     */
    @Override
    public void close() {
        readback.close();
        gpu.close();
    }
}
