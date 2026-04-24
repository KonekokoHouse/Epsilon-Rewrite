package com.github.epsilon.modules.impl.combat.crystalaura;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.vulkan.buffer.VulkanOutputBuffer;
import com.github.epsilon.graphics.vulkan.buffer.VulkanStd430Buffer;
import com.github.epsilon.graphics.vulkan.compute.VulkanComputePipeline;
import com.github.epsilon.graphics.vulkan.compute.VulkanComputeUtils;
import com.github.epsilon.graphics.vulkan.descriptor.DescriptorLayoutSpec;
import com.github.epsilon.graphics.vulkan.descriptor.DescriptorSetWrite;
import com.github.epsilon.graphics.vulkan.descriptor.VulkanResourceManager;
import com.github.epsilon.graphics.vulkan.shader.Glsl2SpirVCompiler;
import net.minecraft.world.Difficulty;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.VK12.*;

/**
 * GPU 加速水晶爆炸伤害计算器。
 *
 * <h3>SSBO 布局</h3>
 * <ul>
 *   <li>binding 0: 体素网格（{@link TerrainVoxelization}）</li>
 *   <li>binding 1: 任务缓冲（放置点 + 目标信息）</li>
 *   <li>binding 2: 结果缓冲（每任务一个 float）</li>
 * </ul>
 */
public class CrystalDamageCompute {

    /** 每个 Task 的 std430 大小：5 × vec4 = 80 bytes */
    private static final int TASK_STRIDE = 80;

    /** Task header: uint taskCount + 3×uint padding = 16 bytes */
    private static final int TASK_HEADER_BYTES = 16;

    /** 最大并行任务数 */
    public static final int MAX_TASKS = 512;

    /** Task buffer 总大小 */
    private static final long TASK_BUFFER_SIZE = TASK_HEADER_BYTES + (long) MAX_TASKS * TASK_STRIDE;

    /** Result buffer 总大小 */
    private static final long RESULT_BUFFER_SIZE = (long) MAX_TASKS * Float.BYTES;

    private boolean initialized;
    private @Nullable VulkanStd430Buffer voxelBuffer;
    private @Nullable VulkanStd430Buffer taskBuffer;
    private @Nullable VulkanOutputBuffer resultBuffer;
    private @Nullable VulkanComputePipeline pipeline;
    private @Nullable VulkanResourceManager resourceManager;
    private @Nullable VulkanResourceManager.ManagedDescriptorSet descriptorSet;
    private @Nullable VulkanComputeUtils computeUtils;

    private final DescriptorLayoutSpec layoutSpec = DescriptorLayoutSpec.builder()
            .addSsbo(0)
            .addSsbo(1)
            .addSsbo(2)
            .build();

    private final TerrainVoxelization voxelization = new TerrainVoxelization();

    private int taskCount;
    private final List<GpuTask> pendingTasks = new ArrayList<>(MAX_TASKS);

    private record GpuTask(
            Vec3 crystalPos, float radius,
            Vec3 targetPos, float halfWidth, float height,
            float armor, float toughness, float enchantProt,
            float difficulty, float resistanceMultiplier, float applyDifficulty
    ) {}

    public void ensureInitialized() {
        if (initialized) return;

        try {
            ByteBuffer shaderSource = ResourceLocationUtils.loadResource(
                    ResourceLocationUtils.getIdentifier("shaders/compute/crystal_damage.csh")
            );
            String glsl;
            try {
                glsl = MemoryUtil.memUTF8(shaderSource);
            } finally {
                MemoryUtil.memFree(shaderSource);
            }

            ByteBuffer spirv;
            try (Glsl2SpirVCompiler compiler = new Glsl2SpirVCompiler(glsl)) {
                compiler.compile();
                spirv = compiler.getSpirV();
            }

            voxelBuffer = new VulkanStd430Buffer(
                    LuminRenderSystem.vulkanContext.vma(),
                    TerrainVoxelization.BUFFER_SIZE,
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
            );

            taskBuffer = new VulkanStd430Buffer(
                    LuminRenderSystem.vulkanContext.vma(),
                    TASK_BUFFER_SIZE,
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
            );

            resultBuffer = new VulkanOutputBuffer(
                    LuminRenderSystem.vulkanContext.vma(),
                    RESULT_BUFFER_SIZE,
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
            );

            pipeline = new VulkanComputePipeline(
                    LuminRenderSystem.vulkanContext.device(),
                    spirv,
                    layoutSpec
            );

            resourceManager = new VulkanResourceManager(LuminRenderSystem.vulkanContext.device());

            descriptorSet = resourceManager.allocateDescriptorSet(
                    pipeline.descriptorSetLayout(),
                    layoutSpec,
                    List.of(
                            DescriptorSetWrite.storageBuffer(0, voxelBuffer.gpuBuffer(), TerrainVoxelization.BUFFER_SIZE),
                            DescriptorSetWrite.storageBuffer(1, taskBuffer.gpuBuffer(), TASK_BUFFER_SIZE),
                            DescriptorSetWrite.storageBuffer(2, resultBuffer.gpuBuffer(), RESULT_BUFFER_SIZE)
                    )
            );

            computeUtils = new VulkanComputeUtils();
            initialized = true;
        } catch (Exception e) {
            e.printStackTrace();
            destroy();
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 开始新一轮任务提交：更新体素网格，清空任务列表。
     */
    public void beginFrame() {
        if (!initialized) return;
        voxelization.update();
        taskCount = 0;
        pendingTasks.clear();
    }

    /**
     * 添加一个伤害计算任务。
     *
     * @param crystalPos 水晶爆炸中心
     * @param radius     爆炸半径
     * @param targetPos  目标脚底位置
     * @param halfWidth  目标半宽
     * @param height     目标高度
     * @param armor      目标护甲值
     * @param toughness  目标护甲韧性
     * @param enchantProt 附魔保护等级总和 (clamped 0-20)
     * @param difficulty 难度 (0=Peaceful,1=Easy,2=Normal,3=Hard)
     * @return 任务索引，用于后续读取结果；-1 表示已满
     */
    public int addTask(Vec3 crystalPos, float radius,
                       Vec3 targetPos, float halfWidth, float height,
                       float armor, float toughness, float enchantProt,
                       float difficulty, float resistanceMultiplier, float applyDifficulty) {
        if (taskCount >= MAX_TASKS) return -1;
        int idx = taskCount++;
        pendingTasks.add(new GpuTask(
                crystalPos, radius, targetPos, halfWidth, height,
                armor, toughness, enchantProt, difficulty,
                resistanceMultiplier, applyDifficulty
        ));
        return idx;
    }

    /**
     * 将难度枚举转为 shader 使用的 float。
     */
    public static float difficultyToFloat(Difficulty d) {
        return switch (d) {
            case PEACEFUL -> 0f;
            case EASY -> 1f;
            case NORMAL -> 2f;
            case HARD -> 3f;
        };
    }

    /**
     * 写入所有 buffer → dispatch → fence wait → 可安全 readResult。
     */
    public void dispatch() {
        if (!initialized || taskCount == 0) return;
        if (voxelBuffer == null || taskBuffer == null || resultBuffer == null
                || pipeline == null || descriptorSet == null || computeUtils == null) return;

        // 写入体素数据
        voxelBuffer.writer().clear();
        voxelization.writeTo(voxelBuffer.writer());

        // 写入任务数据
        writeTaskBuffer();

        computeUtils.dispatchAndWait(
                new VulkanStd430Buffer[]{ voxelBuffer, taskBuffer },
                resultBuffer,
                pipeline,
                descriptorSet.handle(),
                taskCount, 1, 1,
                (long) taskCount * Float.BYTES
        );
    }

    private void writeTaskBuffer() {
        var writer = taskBuffer.writer();
        writer.clear();

        writer.putInt(taskCount);
        writer.putInt(0);
        writer.putInt(0);
        writer.putInt(0);

        for (GpuTask task : pendingTasks) {
            writer.putVec4((float) task.crystalPos.x, (float) task.crystalPos.y, (float) task.crystalPos.z, task.radius);
            writer.putVec4((float) task.targetPos.x, (float) task.targetPos.y, (float) task.targetPos.z, 0f);
            writer.putVec4(task.halfWidth, task.height, 0f, 0f);
            writer.putVec4(task.armor, task.toughness, task.enchantProt, task.difficulty);
            writer.putVec4(task.resistanceMultiplier, task.applyDifficulty, 0f, 0f);
        }
    }

    /**
     * 读取指定任务的计算结果。
     * 必须在 {@link #dispatch()} 返回后调用。
     */
    public float readResult(int taskIndex) {
        if (!initialized || resultBuffer == null || computeUtils == null
                || taskIndex < 0 || taskIndex >= taskCount) return 0f;
        return computeUtils.readFloat(resultBuffer, taskIndex);
    }

    /**
     * 批量读取所有任务结果。
     */
    public float[] readAllResults() {
        if (!initialized || resultBuffer == null || computeUtils == null || taskCount == 0) return new float[0];
        return computeUtils.readFloats(resultBuffer, taskCount);
    }

    public int getTaskCount() {
        return taskCount;
    }

    public void invalidateTerrain() {
        voxelization.invalidate();
    }

    public void destroy() {
        if (computeUtils != null) { computeUtils.close(); computeUtils = null; }
        if (descriptorSet != null) { descriptorSet.close(); descriptorSet = null; }
        if (resourceManager != null) { resourceManager.close(); resourceManager = null; }
        if (pipeline != null) { pipeline.close(); pipeline = null; }
        if (resultBuffer != null) { resultBuffer.close(); resultBuffer = null; }
        if (taskBuffer != null) { taskBuffer.close(); taskBuffer = null; }
        if (voxelBuffer != null) { voxelBuffer.close(); voxelBuffer = null; }
        initialized = false;
    }
}
