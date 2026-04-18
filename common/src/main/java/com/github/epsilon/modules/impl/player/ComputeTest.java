package com.github.epsilon.modules.impl.world;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.tick.TickEvent;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.vulkan.buffer.VulkanOutputBuffer;
import com.github.epsilon.graphics.vulkan.buffer.VulkanStd430Buffer;
import com.github.epsilon.graphics.vulkan.compute.VulkanComputePipeline;
import com.github.epsilon.graphics.vulkan.descriptor.DescriptorSetWrite;
import com.github.epsilon.graphics.vulkan.descriptor.DescriptorLayoutSpec;
import com.github.epsilon.graphics.vulkan.descriptor.VulkanResourceManager;
import com.github.epsilon.graphics.vulkan.shader.Glsl2SpirVCompiler;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.List;

import static org.lwjgl.vulkan.KHRSynchronization2.*;
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

    private @Nullable VkCommandBuffer cmdBuf;
    private long fence;

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
        if (!initialized || cmdBuf == null || pipeline == null || inputBuffer == null || outputBuffer == null) {
            return;
        }

        generateInput();
        recordCommands(cmdBuf, pipeline, inputBuffer, outputBuffer);
        submitAndWait(cmdBuf);
        readOutput(outputBuffer);
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
            cmdBuf = allocateCommandBuffer();
            fence = createFence();
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

    private VkCommandBuffer allocateCommandBuffer() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var pCmd = stack.mallocPointer(1);
            var allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .commandPool(LuminRenderSystem.vulkanContext.cmdPool())
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);

            VulkanUtils.crashIfFailure(
                    vkAllocateCommandBuffers(LuminRenderSystem.vulkanContext.device(), allocInfo, pCmd),
                    "Failed to allocate command buffer"
            );
            return new VkCommandBuffer(pCmd.get(0), LuminRenderSystem.vulkanContext.device());
        }
    }

    private long createFence() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var pFence = stack.mallocLong(1);
            var createInfo = VkFenceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            VulkanUtils.crashIfFailure(
                    vkCreateFence(LuminRenderSystem.vulkanContext.device(), createInfo, null, pFence),
                    "Failed to create fence");
            return pFence.get(0);
        }
    }

    private void recordCommands(
            VkCommandBuffer cmd,
            VulkanComputePipeline pipeline,
            VulkanStd430Buffer inputBuffer,
            VulkanOutputBuffer outputBuffer
    ) {
        if (descriptorSet == null) {
            throw new IllegalStateException("Descriptor set is not initialized");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkResetCommandBuffer(cmd, 0);

            var beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

            vkBeginCommandBuffer(cmd, beginInfo);

            inputBuffer.map(cmd);

            var beforeCompute = VkBufferMemoryBarrier2KHR.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER_2_KHR)
                    .srcStageMask(VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR)
                    .srcAccessMask(VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR)
                    .dstStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR)
                    .dstAccessMask(VK_ACCESS_2_SHADER_STORAGE_READ_BIT_KHR)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(inputBuffer.gpuBuffer())
                    .offset(0)
                    .size(BUFFER_SIZE);

            var beforeComputeDep = VkDependencyInfoKHR.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DEPENDENCY_INFO_KHR)
                    .pBufferMemoryBarriers(beforeCompute);

            vkCmdPipelineBarrier2KHR(cmd, beforeComputeDep);

            vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline.pipeline());
            vkCmdBindDescriptorSets(
                    cmd,
                    VK_PIPELINE_BIND_POINT_COMPUTE,
                    pipeline.pipelineLayout(),
                    0,
                    stack.longs(descriptorSet.handle()),
                    null
            );

            final int computeLocalSizeX = 64;
            final int groupCountX = (ELEMENT_COUNT + computeLocalSizeX - 1) / computeLocalSizeX;
            vkCmdDispatch(cmd, groupCountX, 1, 1);

            var beforeReadback = VkBufferMemoryBarrier2KHR.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER_2_KHR)
                    .srcStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR)
                    .srcAccessMask(VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR)
                    .dstStageMask(VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR)
                    .dstAccessMask(VK_ACCESS_2_TRANSFER_READ_BIT_KHR)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(outputBuffer.gpuBuffer())
                    .offset(0)
                    .size(BUFFER_SIZE);

            var beforeReadbackDep = VkDependencyInfoKHR.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DEPENDENCY_INFO_KHR)
                    .pBufferMemoryBarriers(beforeReadback);

            vkCmdPipelineBarrier2KHR(cmd, beforeReadbackDep);

            outputBuffer.copyToReadback(cmd, BUFFER_SIZE);
            vkEndCommandBuffer(cmd);
        }
    }

    private void submitAndWait(VkCommandBuffer cmd) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkResetFences(LuminRenderSystem.vulkanContext.device(), stack.longs(fence));

            var submitInfo = VkSubmitInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(stack.pointers(cmd));

            vkQueueSubmit(LuminRenderSystem.vulkanContext.graphicsQueue().vkQueue(), submitInfo, fence);
            vkWaitForFences(LuminRenderSystem.vulkanContext.device(), stack.longs(fence), true, Long.MAX_VALUE);
        }
    }

    private void readOutput(VulkanOutputBuffer outputBuffer) {
        ByteBuffer out = outputBuffer.readMapped(BUFFER_SIZE);
        for (int i = 0; i < 8; i++) {
            float value = out.getFloat(i * BYTES_PER_FLOAT);
            System.out.println("[ComputeTest] out[" + i + "] = " + value);
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

        if (fence != VK_NULL_HANDLE) {
            vkDestroyFence(LuminRenderSystem.vulkanContext.device(), fence, null);
            fence = VK_NULL_HANDLE;
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

        if (cmdBuf != null) {
            vkFreeCommandBuffers(LuminRenderSystem.vulkanContext.device(), LuminRenderSystem.vulkanContext.cmdPool(), cmdBuf);
            cmdBuf = null;
        }
        initialized = false;
        dispatched = false;
    }
}
