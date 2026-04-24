package com.github.epsilon.modules.impl.world;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.tick.TickEvent;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.vulkan.buffer.VulkanOutputBuffer;
import com.github.epsilon.graphics.vulkan.buffer.VulkanStd430Buffer;
import com.github.epsilon.graphics.vulkan.compute.VulkanComputePipeline;
import com.github.epsilon.graphics.vulkan.compute.VulkanComputeUtils;
import com.github.epsilon.graphics.vulkan.descriptor.DescriptorSetWrite;
import com.github.epsilon.graphics.vulkan.descriptor.DescriptorLayoutSpec;
import com.github.epsilon.graphics.vulkan.descriptor.VulkanResourceManager;
import com.github.epsilon.graphics.vulkan.shader.Glsl2SpirVCompiler;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.List;

import static org.lwjgl.vulkan.VK12.*;

public class ComputeTest extends Module {

    public static final ComputeTest INSTANCE = new ComputeTest();

    private static final int ELEMENT_COUNT = 128;
    private static final int BYTES_PER_FLOAT = Float.BYTES;
    private static final int BUFFER_SIZE = ELEMENT_COUNT * BYTES_PER_FLOAT;

    private boolean initialized;
    private boolean dispatched;

    private @Nullable VulkanOutputBuffer outputBuffer;
    private @Nullable VulkanStd430Buffer inputBuffer;
    private @Nullable VulkanComputePipeline pipeline;
    private @Nullable VulkanResourceManager resourceManager;
    private @Nullable VulkanResourceManager.ManagedDescriptorSet descriptorSet;
    private @Nullable VulkanComputeUtils computeUtils;

    private final DescriptorLayoutSpec layoutSpec = DescriptorLayoutSpec.builder()
            .addSsbo(0)
            .addSsbo(1)
            .build();

    private ComputeTest() {
        super("Compute Test", Category.WORLD);
    }

    @Override
    protected void onEnable() {
        dispatched = false;
    }

    @Override
    protected void onDisable() {
        dispatched = false;
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;
        if (vulkanCheck()) return;
        if (dispatched) return;

        ensureInitialized();
        if (!initialized || pipeline == null || inputBuffer == null
                || outputBuffer == null || descriptorSet == null || computeUtils == null) {
            return;
        }

        generateInput();

        final int computeLocalSizeX = 64;
        final int groupCountX = (ELEMENT_COUNT + computeLocalSizeX - 1) / computeLocalSizeX;

        computeUtils.dispatchAndWait(
                new VulkanStd430Buffer[]{ inputBuffer },
                outputBuffer,
                pipeline,
                descriptorSet.handle(),
                groupCountX, 1, 1,
                BUFFER_SIZE
        );

        readOutput();
        dispatched = true;
    }

    private void ensureInitialized() {
        if (initialized) return;
        if (vulkanCheck()) return;

        try {
            String computeSource = loadComputeShaderSource();
            ByteBuffer spirv;
            try (Glsl2SpirVCompiler compiler = new Glsl2SpirVCompiler(computeSource)) {
                compiler.compile();
                spirv = compiler.getSpirV();
            }

            inputBuffer = new VulkanStd430Buffer(
                    LuminRenderSystem.vulkanContext.vma(),
                    BUFFER_SIZE,
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
            );

            outputBuffer = new VulkanOutputBuffer(
                    LuminRenderSystem.vulkanContext.vma(),
                    BUFFER_SIZE,
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
            );

            pipeline = new VulkanComputePipeline(
                    LuminRenderSystem.vulkanContext.device(),
                    spirv,
                    layoutSpec
            );

            if (resourceManager == null) {
                resourceManager = new VulkanResourceManager(LuminRenderSystem.vulkanContext.device());
            }

            descriptorSet = resourceManager.allocateDescriptorSet(
                    pipeline.descriptorSetLayout(),
                    layoutSpec,
                    List.of(
                            DescriptorSetWrite.storageBuffer(0, inputBuffer.gpuBuffer(), BUFFER_SIZE),
                            DescriptorSetWrite.storageBuffer(1, outputBuffer.gpuBuffer(), BUFFER_SIZE)
                    )
            );

            computeUtils = new VulkanComputeUtils();
            initialized = true;
        } catch (Exception e) {
            e.printStackTrace();
            destroyResources();
            initialized = false;
        }
    }

    private String loadComputeShaderSource() {
        ByteBuffer shaderSource = ResourceLocationUtils.loadResource(
                ResourceLocationUtils.getIdentifier("shaders/compute/compute_test.csh")
        );
        try {
            return MemoryUtil.memUTF8(shaderSource);
        } finally {
            MemoryUtil.memFree(shaderSource);
        }
    }

    private void readOutput() {
        if (outputBuffer == null || computeUtils == null) return;
        float[] results = computeUtils.readFloats(outputBuffer, 8);
        for (int i = 0; i < results.length; i++) {
            System.out.println("[ComputeTest] out[" + i + "] = " + results[i]);
        }
    }

    private void generateInput() {
        if (inputBuffer == null) return;
        inputBuffer.writer().clear();
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            inputBuffer.writer().putFloat(i);
        }
    }

    private void destroyResources() {
        if (vulkanCheck()) {
            initialized = false;
            return;
        }

        if (computeUtils != null) {
            computeUtils.close();
            computeUtils = null;
        }

        if (descriptorSet != null) {
            descriptorSet.close();
            descriptorSet = null;
        }

        if (resourceManager != null) {
            resourceManager.close();
            resourceManager = null;
        }

        if (pipeline != null) {
            pipeline.close();
            pipeline = null;
        }

        if (outputBuffer != null) {
            outputBuffer.close();
            outputBuffer = null;
        }

        if (inputBuffer != null) {
            inputBuffer.close();
            inputBuffer = null;
        }

        initialized = false;
        dispatched = false;
    }

    public void destroy() {
        if (initialized) {
            destroyResources();
        }
    }
}
