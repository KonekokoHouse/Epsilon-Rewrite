package com.github.epsilon.graphics.vulkan.shader;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.Objects;

import static org.lwjgl.util.shaderc.Shaderc.*;

/**
 * GLSL -> SPIR-V 编译器封装（基于 shaderc）。
 */
public final class Glsl2SpirVCompiler implements AutoCloseable {

    private final String glslShaderSource;
    private final int shaderKind;
    private final String sourceName;
    private final String entryPoint;

    private final long compiler;
    private final long options;
    private long result;

    /**
     * 使用默认 compute 类型与 main 入口创建编译器。
     */
    public Glsl2SpirVCompiler(String glslShaderSource) {
        this(glslShaderSource, shaderc_glsl_compute_shader, "inline.comp", "main");
    }

    /**
     * 创建编译器。
     *
     * @param shaderKind shaderc 的 shader 类型常量
     */
    public Glsl2SpirVCompiler(String glslShaderSource, int shaderKind, String sourceName, String entryPoint) {
        this.glslShaderSource = Objects.requireNonNull(glslShaderSource, "glslShaderSource");
        this.shaderKind = shaderKind;
        this.sourceName = Objects.requireNonNull(sourceName, "sourceName");
        this.entryPoint = Objects.requireNonNull(entryPoint, "entryPoint");

        this.compiler = shaderc_compiler_initialize();
        if (this.compiler == 0L) {
            throw new IllegalStateException("Failed to initialize shaderc compiler");
        }

        this.options = shaderc_compile_options_initialize();
        if (this.options == 0L) {
            shaderc_compiler_release(this.compiler);
            throw new IllegalStateException("Failed to initialize shaderc compile options");
        }

        shaderc_compile_options_set_target_env(this.options, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_2);
        shaderc_compile_options_set_target_spirv(this.options, shaderc_spirv_version_1_5);
    }

    /**
     * 执行编译。
     *
     * @throws IllegalStateException 当编译失败时抛出并携带错误日志
     */
    public void compile() {
        if (result != 0L) {
            shaderc_result_release(result);
            result = 0L;
        }

        result = shaderc_compile_into_spv(
                compiler,
                glslShaderSource,
                shaderKind,
                sourceName,
                entryPoint,
                options
        );

        if (result == 0L) {
            throw new IllegalStateException("shaderc returned null result handle");
        }

        int status = shaderc_result_get_compilation_status(result);
        if (status != shaderc_compilation_status_success) {
            String error = shaderc_result_get_error_message(result);
            throw new IllegalStateException("Shader compilation failed: " + error);
        }
    }

    /**
     * 获取编译结果 SPIR-V 字节码副本。
     *
     * @return 可独立持有的 ByteBuffer
     */
    public ByteBuffer getSpirV() {
        if (result == 0L) {
            throw new IllegalStateException("Shader is not compiled. Call compile() first.");
        }

        ByteBuffer compiled = shaderc_result_get_bytes(result);
        if (compiled == null) {
            throw new IllegalStateException("shaderc returned empty SPIR-V data");
        }
        ByteBuffer copy = BufferUtils.createByteBuffer(compiled.remaining());
        copy.put(compiled.duplicate());
        copy.flip();
        return copy;
    }

    /**
     * 释放 shaderc 编译器相关资源。
     */
    @Override
    public void close() {
        if (result != 0L) {
            shaderc_result_release(result);
            result = 0L;
        }
        shaderc_compile_options_release(options);
        shaderc_compiler_release(compiler);
    }
}
