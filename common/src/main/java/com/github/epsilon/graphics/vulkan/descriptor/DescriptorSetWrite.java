package com.github.epsilon.graphics.vulkan.descriptor;

import static org.lwjgl.vulkan.VK12.*;

/**
 * 描述一次 vkUpdateDescriptorSets 的单个写入项。
 *
 * <p>该结构同时支持 buffer 与 image 类型 descriptor，
 * 实际使用时由 descriptorType 决定字段生效范围。</p>
 */
public record DescriptorSetWrite(
        int binding,
        int descriptorType,
        int descriptorCount,
        long buffer,
        long offset,
        long range,
        long sampler,
        long imageView,
        int imageLayout
) {

    public DescriptorSetWrite {
        if (binding < 0) {
            throw new IllegalArgumentException("binding must be >= 0");
        }
        if (descriptorCount <= 0) {
            throw new IllegalArgumentException("descriptorCount must be > 0");
        }
    }

    /**
     * 创建 storage buffer 写入项。
     */
    public static DescriptorSetWrite storageBuffer(int binding, long buffer, long range) {
        return new DescriptorSetWrite(binding, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, buffer, 0L, range, VK_NULL_HANDLE, VK_NULL_HANDLE, VK_IMAGE_LAYOUT_UNDEFINED);
    }

    /**
     * 创建 uniform buffer 写入项。
     */
    public static DescriptorSetWrite uniformBuffer(int binding, long buffer, long range) {
        return new DescriptorSetWrite(binding, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1, buffer, 0L, range, VK_NULL_HANDLE, VK_NULL_HANDLE, VK_IMAGE_LAYOUT_UNDEFINED);
    }

    /**
     * 创建 combined image sampler 写入项。
     */
    public static DescriptorSetWrite combinedImageSampler(int binding, long sampler, long imageView, int imageLayout) {
        return new DescriptorSetWrite(binding, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1, VK_NULL_HANDLE, 0L, 0L, sampler, imageView, imageLayout);
    }

    /**
     * 创建 sampled image 写入项。
     */
    public static DescriptorSetWrite sampledImage(int binding, long imageView, int imageLayout) {
        return new DescriptorSetWrite(binding, VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE, 1, VK_NULL_HANDLE, 0L, 0L, VK_NULL_HANDLE, imageView, imageLayout);
    }

    /**
     * 创建 storage image 写入项。
     */
    public static DescriptorSetWrite storageImage(int binding, long imageView, int imageLayout) {
        return new DescriptorSetWrite(binding, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, 1, VK_NULL_HANDLE, 0L, 0L, VK_NULL_HANDLE, imageView, imageLayout);
    }

    /**
     * 判断是否为 buffer 类 descriptor。
     */
    public boolean isBufferDescriptor() {
        return descriptorType == VK_DESCRIPTOR_TYPE_STORAGE_BUFFER || descriptorType == VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
    }

    /**
     * 判断是否为 image 类 descriptor。
     */
    public boolean isImageDescriptor() {
        return descriptorType == VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
                || descriptorType == VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE
                || descriptorType == VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    }
}
