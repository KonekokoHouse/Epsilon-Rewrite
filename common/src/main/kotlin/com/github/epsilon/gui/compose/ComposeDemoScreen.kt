package com.github.epsilon.gui.compose

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ComposeDemoScreen : Screen(Component.literal("ComposeDemo")) {
    private val composeSurface = ComposeDemoSurface()

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        graphics.fill(0, 0, width, height, 0x66000000)

        val panel = getPanelLayout()
        graphics.fill(
            panel.left - 8,
            panel.top - 8,
            panel.left + panel.width + 8,
            panel.top + panel.height + 8,
            0x88000000.toInt()
        )

        val client = minecraft
        val progress = ((System.currentTimeMillis() / 40L) % 100L) / 100.0f
        val worldName = client.currentServer?.name ?: if (client.level != null) "Singleplayer" else "No World"
        val playerName = client.player?.name?.string ?: "Player"
        val state = ComposeDemoState(
            worldName = worldName,
            playerName = playerName,
            progress = progress,
            accentArgb = 0xFF8E7CFF.toInt(),
            backgroundArgb = 0xFF10131A.toInt()
        )
        val scale = DEMO_SCALE
        val renderedPanel = composeSurface.render(
            width = (panel.width * scale).roundToInt(),
            height = (panel.height * scale).roundToInt(),
            densityScale = scale,
            state = state
        )
        graphics.blit(
            renderedPanel.textureView,
            renderedPanel.sampler,
            panel.left,
            panel.top,
            panel.left + panel.width,
            panel.top + panel.height,
            0.0f,
            1.0f,
            1.0f,
            0.0f
        )
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        val panel = getPanelLayout()
        if (composeSurface.handleMousePressed(panel.localX(event.x(), DEMO_SCALE), panel.localY(event.y(), DEMO_SCALE), panel.contains(event.x(), event.y()), event.button(), event.modifiers())) {
            return true
        }
        return super.mouseClicked(event, isDoubleClick)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val panel = getPanelLayout()
        if (composeSurface.handleMouseReleased(panel.localX(event.x(), DEMO_SCALE), panel.localY(event.y(), DEMO_SCALE), panel.contains(event.x(), event.y()), event.button(), event.modifiers())) {
            return true
        }
        return super.mouseReleased(event)
    }

    override fun mouseMoved(x: Double, y: Double) {
        super.mouseMoved(x, y)
        val panel = getPanelLayout()
        composeSurface.handleMouseMoved(panel.localX(x, DEMO_SCALE), panel.localY(y, DEMO_SCALE), panel.contains(x, y))
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val panel = getPanelLayout()
        if (composeSurface.handleMouseDragged(panel.localX(event.x(), DEMO_SCALE), panel.localY(event.y(), DEMO_SCALE), panel.contains(event.x(), event.y()), event.modifiers())) {
            return true
        }
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean {
        val panel = getPanelLayout()
        if (composeSurface.handleMouseScroll(panel.localX(x, DEMO_SCALE), panel.localY(y, DEMO_SCALE), panel.contains(x, y), scrollX.toFloat(), scrollY.toFloat(), 0)) {
            return true
        }
        return super.mouseScrolled(x, y, scrollX, scrollY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == 256) {
            onClose()
            return true
        }
        if (composeSurface.handleKeyPressed(event.key(), event.modifiers())) {
            return true
        }
        return super.keyPressed(event)
    }

    override fun keyReleased(event: KeyEvent): Boolean {
        if (composeSurface.handleKeyReleased(event.key(), event.modifiers())) {
            return true
        }
        return super.keyReleased(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (composeSurface.handleCharTyped(event.codepoint(), 0)) {
            return true
        }
        return super.charTyped(event)
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        composeSurface.invalidate()
    }

    override fun onClose() {
        composeSurface.clearFocus()
        super.onClose()
        minecraft.setScreen(ComposePanelScreen)
    }

    override fun removed() {
        super.removed()
        composeSurface.clearFocus()
        composeSurface.close()
    }

    override fun isPauseScreen(): Boolean {
        return false
    }

    private fun getPanelLayout(): PanelLayout {
        val panelWidth = min(PANEL_MAX_WIDTH, max(PANEL_MIN_WIDTH, width - PANEL_MARGIN * 2))
        val panelHeight = min(PANEL_MAX_HEIGHT, max(PANEL_MIN_HEIGHT, height - PANEL_MARGIN * 2))
        val panelLeft = (width - panelWidth) / 2
        val panelTop = (height - panelHeight) / 2
        return PanelLayout(panelLeft, panelTop, panelWidth, panelHeight)
    }

    private data class PanelLayout(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int
    ) {
        fun contains(x: Double, y: Double): Boolean {
            return x >= left && x < left + width && y >= top && y < top + height
        }

        fun localX(x: Double, scale: Float): Float {
            return ((x - left) * scale).toFloat()
        }

        fun localY(y: Double, scale: Float): Float {
            return ((y - top) * scale).toFloat()
        }
    }

    companion object {
        private const val DEMO_SCALE = 1.0f
        private const val PANEL_MARGIN = 28
        private const val PANEL_MIN_WIDTH = 280
        private const val PANEL_MIN_HEIGHT = 220
        private const val PANEL_MAX_WIDTH = 520
        private const val PANEL_MAX_HEIGHT = 420
    }
}


