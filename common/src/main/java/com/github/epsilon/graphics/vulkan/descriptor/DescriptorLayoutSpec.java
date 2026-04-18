package com.github.epsilon.graphics.vulkan.descriptor;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.VK12.VK_SHADER_STAGE_COMPUTE_BIT;

/**
 * 不可变的 DescriptorSetLayout 规格描述。
 */
public final class DescriptorLayoutSpec {

    private final List<DescriptorBindingSpec> bindings;

    private DescriptorLayoutSpec(List<DescriptorBindingSpec> bindings) {
        this.bindings = List.copyOf(bindings);
        if (this.bindings.isEmpty()) {
            throw new IllegalArgumentException("At least one descriptor binding is required");
        }
    }

    /**
     * 创建布局构建器。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 返回布局中所有 binding 规格。
     */
    public List<DescriptorBindingSpec> bindings() {
        return bindings;
    }

    /**
     * DescriptorLayoutSpec 构建器。
     */
    public static final class Builder {

        private final List<DescriptorBindingSpec> bindings = new ArrayList<>();

        /**
         * 追加一个通用 binding 规格。
         */
        public Builder addBinding(DescriptorBindingSpec binding) {
            bindings.add(binding);
            return this;
        }

        /**
         * 追加一个默认计算阶段 SSBO binding。
         */
        public Builder addSsbo(int binding) {
            return addSsbo(binding, 1, VK_SHADER_STAGE_COMPUTE_BIT);
        }

        /**
         * 追加一个 SSBO binding。
         */
        public Builder addSsbo(int binding, int descriptorCount, int stageFlags) {
            return addBinding(DescriptorBindingSpec.ssbo(binding, descriptorCount, stageFlags));
        }

        /**
         * 追加一个组合采样纹理 binding。
         */
        public Builder addTexture(int binding, int descriptorCount, int stageFlags) {
            return addBinding(DescriptorBindingSpec.combinedImageSampler(binding, descriptorCount, stageFlags));
        }

        /**
         * 追加一个 sampled image binding。
         */
        public Builder addSampledImage(int binding, int descriptorCount, int stageFlags) {
            return addBinding(DescriptorBindingSpec.sampledImage(binding, descriptorCount, stageFlags));
        }

        /**
         * 追加一个 storage image binding。
         */
        public Builder addStorageImage(int binding, int descriptorCount, int stageFlags) {
            return addBinding(DescriptorBindingSpec.storageImage(binding, descriptorCount, stageFlags));
        }

        /**
         * 追加一个 UBO binding。
         */
        public Builder addUniformBuffer(int binding, int descriptorCount, int stageFlags) {
            return addBinding(DescriptorBindingSpec.uniformBuffer(binding, descriptorCount, stageFlags));
        }

        /**
         * 构建不可变布局规格。
         */
        public DescriptorLayoutSpec build() {
            return new DescriptorLayoutSpec(bindings);
        }
    }
}
