package com.github.epsilon.gui.skija

import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.FramebufferFormat
import org.jetbrains.skia.GLBackendState
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin
import org.lwjgl.opengl.GL11C
import org.lwjgl.opengl.GL13C
import org.lwjgl.opengl.GL15C
import org.lwjgl.opengl.GL20C
import org.lwjgl.opengl.GL30C
import org.lwjgl.opengl.GL32C

class SkijaOffscreenRenderer(
    private val bufferCount: Int = DEFAULT_BUFFER_COUNT
) : AutoCloseable {
    private var width = 0
    private var height = 0
    private var buffers: Array<RenderBuffer?> = emptyArray()
    private var nextBufferIndex = 0
    private var displayedBufferIndex = -1
    private var submittedFrameSequence = 0L
    private var context: DirectContext? = null
    private var sampler: GpuSampler? = null

    init {
        require(bufferCount >= 2) { "Offscreen renderer must use at least double buffering." }
    }

    fun render(width: Int, height: Int, render: (Canvas) -> Unit): PresentedFrame {
        RenderSystem.assertOnRenderThread()
        ensureBuffers(width, height)
        promoteCompletedBuffers()

        val buffer = acquireWritableBuffer()
        val directContext = requireNotNull(context) { "Skia direct context is not available." }
        val previousState = GlStateSnapshot.capture()
        directContext.resetGL(
            GLBackendState.RENDER_TARGET,
            GLBackendState.TEXTURE_BINDING,
            GLBackendState.VIEW,
            GLBackendState.BLEND,
            GLBackendState.VERTEX,
            GLBackendState.STENCIL,
            GLBackendState.PIXEL_STORE,
            GLBackendState.PROGRAM,
            GLBackendState.FIXED_FUNCTION,
            GLBackendState.MISC
        )
        try {
            val canvas = buffer.surface.canvas
            canvas.clear(0)
            render(canvas)
            directContext.flushAndSubmit(buffer.surface, true)
        } finally {
            previousState.restore()
            directContext.resetGLAll()
        }

        buffer.markSubmitted(++submittedFrameSequence)
        displayedBufferIndex = buffer.index
        nextBufferIndex = (nextBufferIndex + 1) % buffers.size
        return PresentedFrame(
            textureView = buffer.textureView,
            sampler = requireNotNull(sampler) { "Offscreen sampler is not available." }
        )
    }

    private fun ensureBuffers(requestedWidth: Int, requestedHeight: Int) {
        require(requestedWidth > 0 && requestedHeight > 0) {
            "Offscreen surface must be created with a positive size."
        }

        if (buffers.isNotEmpty() && width == requestedWidth && height == requestedHeight) {
            return
        }

        destroyBuffers()
        context = context ?: DirectContext.makeGL()
        sampler = sampler ?: RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        width = requestedWidth
        height = requestedHeight
        buffers = Array(bufferCount) { index -> createBuffer(index, requestedWidth, requestedHeight) }
        nextBufferIndex = 0
        displayedBufferIndex = -1
        submittedFrameSequence = 0L
    }

    private fun createBuffer(index: Int, width: Int, height: Int): RenderBuffer {
        val device = RenderSystem.getDevice()
        val texture = device.createTexture(
            "epsilon_compose_demo_$index",
            GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_RENDER_ATTACHMENT,
            TextureFormat.RGBA8,
            width,
            height,
            1,
            1
        )
        val textureView = device.createTextureView(texture)
        val glTexture = texture as? GlTexture
            ?: error("Compose offscreen rendering requires Minecraft's OpenGL texture backend.")

        val previousFramebuffer = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING)
        val framebufferId = GL30C.glGenFramebuffers()
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebufferId)
        try {
            GL30C.glFramebufferTexture2D(
                GL30C.GL_FRAMEBUFFER,
                GL30C.GL_COLOR_ATTACHMENT0,
                GL11C.GL_TEXTURE_2D,
                glTexture.glId(),
                0
            )
            check(GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER) == GL30C.GL_FRAMEBUFFER_COMPLETE) {
                "Skia offscreen framebuffer is incomplete."
            }
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, previousFramebuffer)
        }

        val renderTarget = BackendRenderTarget.makeGL(
            width,
            height,
            0,
            8,
            framebufferId,
            FramebufferFormat.GR_GL_RGBA8
        )
        val surface = Surface.makeFromBackendRenderTarget(
            requireNotNull(context),
            renderTarget,
            SurfaceOrigin.BOTTOM_LEFT,
            SurfaceColorFormat.RGBA_8888,
            ColorSpace.sRGB
        ) ?: error("Failed to create a Skia surface for the Minecraft GPU texture.")

        return RenderBuffer(index, texture, textureView, framebufferId, renderTarget, surface)
    }

    private fun destroyBuffers() {
        buffers.forEach { it?.close() }
        buffers = emptyArray()
        width = 0
        height = 0
        nextBufferIndex = 0
        displayedBufferIndex = -1
        submittedFrameSequence = 0L
    }

    private fun promoteCompletedBuffers() {
        var newestCompleted: RenderBuffer? = null
        for (buffer in buffers) {
            val candidate = buffer ?: continue
            if (candidate.tryCompleteSubmission() && candidate.submittedSequence > (newestCompleted?.submittedSequence ?: Long.MIN_VALUE)) {
                newestCompleted = candidate
            }
        }

        if (newestCompleted != null) {
            displayedBufferIndex = newestCompleted.index
        }
    }

    private fun acquireWritableBuffer(): RenderBuffer {
        repeat(buffers.size) { offset ->
            val index = (nextBufferIndex + offset) % buffers.size
            val candidate = requireNotNull(buffers[index]) { "Skia render buffer was not created." }
            if (candidate.index == displayedBufferIndex) {
                return@repeat
            }
            if (candidate.isWritable()) {
                nextBufferIndex = index
                return candidate
            }
        }

        val fallbackIndex = if (displayedBufferIndex == -1) nextBufferIndex else (displayedBufferIndex + 1) % buffers.size
        val fallback = requireNotNull(buffers[fallbackIndex]) { "Skia render buffer was not created." }
        fallback.awaitWritable()
        nextBufferIndex = fallbackIndex
        return fallback
    }

    override fun close() {
        RenderSystem.assertOnRenderThread()
        destroyBuffers()
        context?.close()
        context = null
    }

    data class PresentedFrame(
        val textureView: GpuTextureView,
        val sampler: GpuSampler
    )

    private class RenderBuffer(
        val index: Int,
        val texture: GpuTexture,
        val textureView: GpuTextureView,
        val framebufferId: Int,
        val renderTarget: BackendRenderTarget,
        val surface: Surface
    ) : AutoCloseable {
        var submittedSequence: Long = Long.MIN_VALUE
            private set
        private var submissionComplete = true
        private var fenceSync = 0L

        fun markSubmitted(sequence: Long) {
            deleteFence()
            submittedSequence = sequence
            submissionComplete = false
            fenceSync = GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
            GL11C.glFlush()
        }

        fun tryCompleteSubmission(): Boolean {
            if (!submissionComplete && fenceSync != 0L) {
                val result = GL32C.glClientWaitSync(fenceSync, 0, 0)
                if (result == GL32C.GL_ALREADY_SIGNALED || result == GL32C.GL_CONDITION_SATISFIED || result == GL32C.GL_WAIT_FAILED) {
                    submissionComplete = true
                    deleteFence()
                }
            }
            return submissionComplete
        }

        fun isWritable(): Boolean {
            return submissionComplete
        }

        fun awaitWritable() {
            if (!submissionComplete && fenceSync != 0L) {
                GL32C.glClientWaitSync(fenceSync, GL32C.GL_SYNC_FLUSH_COMMANDS_BIT, Long.MAX_VALUE)
                submissionComplete = true
                deleteFence()
            }
        }

        private fun deleteFence() {
            if (fenceSync != 0L) {
                GL32C.glDeleteSync(fenceSync)
                fenceSync = 0L
            }
        }

        override fun close() {
            deleteFence()
            surface.close()
            renderTarget.close()
            if (framebufferId != 0) {
                GL30C.glDeleteFramebuffers(framebufferId)
            }
            textureView.close()
            texture.close()
        }
    }

    private class GlStateSnapshot(
        private val framebufferBinding: Int,
        private val activeTexture: Int,
        private val textureBinding2D: Int,
        private val currentProgram: Int,
        private val vertexArrayBinding: Int,
        private val arrayBufferBinding: Int,
        private val viewport: IntArray,
        private val scissorEnabled: Boolean,
        private val scissorBox: IntArray,
        private val blendEnabled: Boolean
    ) {
        fun restore() {
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebufferBinding)
            GL20C.glUseProgram(currentProgram)
            GL30C.glBindVertexArray(vertexArrayBinding)
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, arrayBufferBinding)
            GL13C.glActiveTexture(activeTexture)
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, textureBinding2D)
            GL11C.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
            if (scissorEnabled) {
                GL11C.glEnable(GL11C.GL_SCISSOR_TEST)
            } else {
                GL11C.glDisable(GL11C.GL_SCISSOR_TEST)
            }
            GL11C.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3])
            if (blendEnabled) {
                GL11C.glEnable(GL11C.GL_BLEND)
            } else {
                GL11C.glDisable(GL11C.GL_BLEND)
            }
        }

        companion object {
            fun capture(): GlStateSnapshot {
                return GlStateSnapshot(
                    framebufferBinding = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING),
                    activeTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE),
                    textureBinding2D = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D),
                    currentProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM),
                    vertexArrayBinding = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING),
                    arrayBufferBinding = GL11C.glGetInteger(GL15C.GL_ARRAY_BUFFER_BINDING),
                    viewport = IntArray(4).also { GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, it) },
                    scissorEnabled = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST),
                    scissorBox = IntArray(4).also { GL11C.glGetIntegerv(GL11C.GL_SCISSOR_BOX, it) },
                    blendEnabled = GL11C.glIsEnabled(GL11C.GL_BLEND)
                )
            }
        }
    }

    companion object {
        private const val DEFAULT_BUFFER_COUNT = 3
    }
}

