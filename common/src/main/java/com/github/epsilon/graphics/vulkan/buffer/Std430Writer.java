package com.github.epsilon.graphics.vulkan.buffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * std430 布局写入器。
 * <p>
 * 将常见标量/向量/矩阵按 std430 对齐规则写入目标 ByteBuffer。
 */
public final class Std430Writer {

    private final ByteBuffer target;

    /**
     * 创建写入器，并将目标缓冲区设为小端序。
     */
    public Std430Writer(ByteBuffer target) {
        this.target = target.order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * 清空写入位置。
     */
    public void clear() {
        target.clear();
    }

    /**
     * 当前写入位置。
     */
    public int position() {
        return target.position();
    }

    /**
     * 已写入字节数。
     */
    public int writtenBytes() {
        return target.position();
    }

    /**
     * 按给定对齐值补齐当前位置。
     */
    public Std430Writer align(int alignment) {
        if (alignment <= 0 || (alignment & (alignment - 1)) != 0) {
            throw new IllegalArgumentException("alignment must be a positive power of two");
        }

        int p = target.position();
        int aligned = (p + alignment - 1) & -alignment;
        while (target.position() < aligned) {
            target.put((byte) 0);
        }
        return this;
    }

    /**
     * 写入 int（4 字节对齐）。
     */
    public Std430Writer putInt(int value) {
        align(4);
        target.putInt(value);
        return this;
    }

    /**
     * 写入 uint（按 int 存储）。
     */
    public Std430Writer putUInt(int value) {
        return putInt(value);
    }

    /**
     * 写入 float（4 字节对齐）。
     */
    public Std430Writer putFloat(float value) {
        align(4);
        target.putFloat(value);
        return this;
    }

    /**
     * 写入 vec2（8 字节对齐）。
     */
    public Std430Writer putVec2(float x, float y) {
        align(8);
        target.putFloat(x).putFloat(y);
        return this;
    }

    /**
     * 写入 vec3（按 16 字节槽位写入，补齐一个 float）。
     */
    public Std430Writer putVec3(float x, float y, float z) {
        // std430 vec3 has 16-byte base alignment in structs.
        align(16);
        target.putFloat(x).putFloat(y).putFloat(z).putFloat(0.0f);
        return this;
    }

    /**
     * 写入 vec4（16 字节对齐）。
     */
    public Std430Writer putVec4(float x, float y, float z, float w) {
        align(16);
        target.putFloat(x).putFloat(y).putFloat(z).putFloat(w);
        return this;
    }

    /**
     * 写入 mat4（16 个 float，16 字节对齐）。
     */
    public Std430Writer putMat4(float[] matrix16) {
        if (matrix16.length != 16) {
            throw new IllegalArgumentException("mat4 expects 16 floats");
        }
        align(16);
        for (float value : matrix16) {
            target.putFloat(value);
        }
        return this;
    }

    /**
     * 按指定对齐写入原始字节。
     */
    public Std430Writer putBytes(ByteBuffer src, int alignment) {
        align(alignment);
        target.put(src.duplicate());
        return this;
    }
}
