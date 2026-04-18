package com.github.epsilon.graphics.vulkan.descriptor;

import static org.lwjgl.vulkan.VK12.*;

/**
 * 描述 DescriptorSetLayout 中的单个 binding 规格。
 *
 * @param binding         binding 槽位
 * @param descriptorType  Vulkan descriptor 类型
 * @param descriptorCount 数量（数组长度）
 * @param stageFlags      可见 shader stage
 */
public record DescriptorBindingSpec(int binding, int descriptorType, int descriptorCount, int stageFlags) {

    public DescriptorBindingSpec {
        if (binding < 0) {
            throw new IllegalArgumentException("binding must be >= 0");
        }
        if (descriptorCount <= 0) {
            throw new IllegalArgumentException("descriptorCount must be > 0");
        }
    }

    /**
     * 创建计算阶段常用的 SSBO binding（数量为 1）。
     */
    public static DescriptorBindingSpec ssbo(int binding) {
        return new DescriptorBindingSpec(binding, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, VK_SHADER_STAGE_COMPUTE_BIT);
    }

    /**
     * 创建 SSBO binding。
     */
    public static DescriptorBindingSpec ssbo(int binding, int descriptorCount, int stageFlags) {
        return new DescriptorBindingSpec(binding, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, descriptorCount, stageFlags);
    }

    /**
     * 创建 UBO binding。
     */
    public static DescriptorBindingSpec uniformBuffer(int binding, int descriptorCount, int stageFlags) {
        return new DescriptorBindingSpec(binding, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, descriptorCount, stageFlags);
    }

    /**
     * 创建组合采样纹理 binding（sampler + image）。
     */
    public static DescriptorBindingSpec combinedImageSampler(int binding, int descriptorCount, int stageFlags) {
        return new DescriptorBindingSpec(binding, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, descriptorCount, stageFlags);
    }

    /**
     * 创建 sampled image binding（不含 sampler）。
     */
    public static DescriptorBindingSpec sampledImage(int binding, int descriptorCount, int stageFlags) {
        return new DescriptorBindingSpec(binding, VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE, descriptorCount, stageFlags);
    }

    /**
     * 创建 storage image binding。
     */
    public static DescriptorBindingSpec storageImage(int binding, int descriptorCount, int stageFlags) {
        return new DescriptorBindingSpec(binding, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, descriptorCount, stageFlags);
    }
}
