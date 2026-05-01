package com.github.epsilon.gui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.github.epsilon.gui.skija.SkijaOffscreenRenderer
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL45.*
import java.awt.event.KeyEvent as AwtKeyEvent
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ComposeSurface<T>(
    initialState: T,
    private val content: @Composable (T) -> Unit
) : AutoCloseable {
    private val glRenderer = SkijaOffscreenRenderer()
    private var composeScene: ComposeScene? = null
    private var sceneWidth = 0
    private var sceneHeight = 0
    private var sceneDensityScale = 1f
    private var currentState by mutableStateOf(initialState)
    private var isFocused = false
    private var pointerInside = false
    private var primaryPressed = false
    private var secondaryPressed = false
    private var tertiaryPressed = false
    private var backPressed = false
    private var forwardPressed = false
    private val windowInfo = OffscreenWindowInfo().apply { isWindowFocused = true }
    private val platformContext = object : PlatformContext by PlatformContext.Empty {
        override val windowInfo: WindowInfo
            get() = this@ComposeSurface.windowInfo
    }

    fun render(width: Int, height: Int, densityScale: Float = 1f, state: T): SkijaOffscreenRenderer.PresentedFrame {
        require(width > 0 && height > 0) { "Compose surface size must be positive." }
        require(densityScale > 0f) { "Compose surface density scale must be positive." }
        ensureScene(width, height, densityScale)
        currentState = state

        val scene = requireNotNull(composeScene) { "Compose scene is not available." }

        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0)
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0)
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0)

        return glRenderer.render(width, height) { canvas ->
            scene.render(canvas.asComposeCanvas(), System.nanoTime())
        }
    }

    fun handleMouseMoved(x: Float, y: Float, inside: Boolean, modifiers: Int = 0) {
        val scene = composeScene ?: return
        val hadInside = pointerInside
        val hasButtonsDown = hasPressedButtons()
        if (!inside && !hadInside && !hasButtonsDown) {
            return
        }

        val position = Offset(x, y)
        val keyboardModifiers = keyboardModifiers(modifiers)
        if (inside && !hadInside) {
            scene.sendPointerEvent(
                eventType = PointerEventType.Enter,
                position = position,
                buttons = pointerButtons(),
                keyboardModifiers = keyboardModifiers
            )
        }

        scene.sendPointerEvent(
            eventType = PointerEventType.Move,
            position = position,
            buttons = pointerButtons(),
            keyboardModifiers = keyboardModifiers
        )

        if (!inside && hadInside && !hasButtonsDown) {
            scene.sendPointerEvent(
                eventType = PointerEventType.Exit,
                position = position,
                buttons = pointerButtons(),
                keyboardModifiers = keyboardModifiers
            )
        }

        pointerInside = inside
    }

    fun handleMousePressed(x: Float, y: Float, inside: Boolean, button: Int, modifiers: Int): Boolean {
        val scene = composeScene ?: return false
        if (!inside) {
            clearFocus()
            handleMouseMoved(x, y, false, modifiers)
            return false
        }

        val pointerButton = pointerButton(button) ?: return false
        val position = Offset(x, y)
        val keyboardModifiers = keyboardModifiers(modifiers)
        if (!pointerInside) {
            scene.sendPointerEvent(
                eventType = PointerEventType.Enter,
                position = position,
                buttons = pointerButtons(),
                keyboardModifiers = keyboardModifiers
            )
        }

        updateButtonState(button, true)
        scene.sendPointerEvent(
            eventType = PointerEventType.Press,
            position = position,
            buttons = pointerButtons(),
            keyboardModifiers = keyboardModifiers,
            button = pointerButton
        )
        pointerInside = true
        isFocused = true
        windowInfo.isWindowFocused = true
        return true
    }

    fun handleMouseReleased(x: Float, y: Float, inside: Boolean, button: Int, modifiers: Int): Boolean {
        val scene = composeScene ?: return false
        val hadButtonsDown = hasPressedButtons()
        val pointerButton = pointerButton(button)
        if (!inside && !hadButtonsDown) {
            return false
        }

        updateButtonState(button, false)
        val position = Offset(x, y)
        val keyboardModifiers = keyboardModifiers(modifiers)
        if (pointerButton != null) {
            scene.sendPointerEvent(
                eventType = PointerEventType.Release,
                position = position,
                buttons = pointerButtons(),
                keyboardModifiers = keyboardModifiers,
                button = pointerButton
            )
        }

        if (!inside && pointerInside && !hasPressedButtons()) {
            scene.sendPointerEvent(
                eventType = PointerEventType.Exit,
                position = position,
                buttons = pointerButtons(),
                keyboardModifiers = keyboardModifiers
            )
        }

        pointerInside = inside
        return true
    }

    fun handleMouseDragged(x: Float, y: Float, inside: Boolean, modifiers: Int): Boolean {
        if (!hasPressedButtons()) {
            handleMouseMoved(x, y, inside, modifiers)
            return inside
        }

        val scene = composeScene ?: return false
        scene.sendPointerEvent(
            eventType = PointerEventType.Move,
            position = Offset(x, y),
            buttons = pointerButtons(),
            keyboardModifiers = keyboardModifiers(modifiers)
        )
        pointerInside = inside
        return true
    }

    fun handleMouseScroll(x: Float, y: Float, inside: Boolean, scrollX: Float, scrollY: Float, modifiers: Int): Boolean {
        val scene = composeScene ?: return false
        if (!inside && !pointerInside) {
            return false
        }

        scene.sendPointerEvent(
            eventType = PointerEventType.Scroll,
            position = Offset(x, y),
            scrollDelta = Offset(scrollX, scrollY),
            buttons = pointerButtons(),
            keyboardModifiers = keyboardModifiers(modifiers)
        )
        pointerInside = inside
        return true
    }

    fun handleKeyPressed(keyCode: Int, modifiers: Int): Boolean {
        if (!isFocused) {
            return false
        }
        return sendComposeKeyEvent(keyCode, modifiers, KeyEventType.KeyDown, 0)
    }

    fun handleKeyReleased(keyCode: Int, modifiers: Int): Boolean {
        if (!isFocused) {
            return false
        }
        return sendComposeKeyEvent(keyCode, modifiers, KeyEventType.KeyUp, 0)
    }

    fun handleCharTyped(codePoint: Int, modifiers: Int = 0): Boolean {
        if (!isFocused) {
            return false
        }
        return sendComposeKeyEvent(GLFW_KEY_UNKNOWN, modifiers, KeyEventType.KeyDown, codePoint)
    }

    fun clearFocus() {
        isFocused = false
        windowInfo.isWindowFocused = false
    }

    fun invalidate() {
        recreateScene(sceneWidth, sceneHeight, sceneDensityScale)
    }

    override fun close() {
        composeScene?.close()
        composeScene = null
        sceneWidth = 0
        sceneHeight = 0
        sceneDensityScale = 1f
        clearFocus()
        pointerInside = false
        primaryPressed = false
        secondaryPressed = false
        tertiaryPressed = false
        backPressed = false
        forwardPressed = false
        glRenderer.close()
    }

    private fun ensureScene(width: Int, height: Int, densityScale: Float) {
        if (composeScene == null || sceneWidth != width || sceneHeight != height || sceneDensityScale != densityScale) {
            recreateScene(width, height, densityScale)
        }
    }

    private fun recreateScene(width: Int, height: Int, densityScale: Float) {
        composeScene?.close()
        composeScene = null

        if (width <= 0 || height <= 0) {
            sceneWidth = 0
            sceneHeight = 0
            sceneDensityScale = 1f
            return
        }

        sceneWidth = width
        sceneHeight = height
        sceneDensityScale = densityScale.coerceAtLeast(MIN_DENSITY_SCALE)
        windowInfo.isWindowFocused = isFocused
        windowInfo.containerSize = IntSize(width, height)
        pointerInside = false
        primaryPressed = false
        secondaryPressed = false
        tertiaryPressed = false
        backPressed = false
        forwardPressed = false
        composeScene = CanvasLayersComposeScene(
            density = Density(sceneDensityScale),
            size = IntSize(width, height),
            coroutineContext = EmptyCoroutineContext,
            platformContext = platformContext
        ).also { scene ->
            scene.setContent {
                content(currentState)
            }
        }
    }

    private fun sendComposeKeyEvent(keyCode: Int, modifiers: Int, type: KeyEventType, codePoint: Int): Boolean {
        val scene = composeScene ?: return false
        return scene.sendKeyEvent(
            KeyEvent(
                key = composeKey(keyCode),
                type = type,
                codePoint = codePoint,
                isCtrlPressed = modifiers and MODIFIER_CONTROL != 0,
                isMetaPressed = modifiers and MODIFIER_META != 0,
                isAltPressed = modifiers and MODIFIER_ALT != 0,
                isShiftPressed = modifiers and MODIFIER_SHIFT != 0
            )
        )
    }

    private fun hasPressedButtons(): Boolean {
        return primaryPressed || secondaryPressed || tertiaryPressed || backPressed || forwardPressed
    }

    private fun updateButtonState(button: Int, pressed: Boolean) {
        when (button) {
            0 -> primaryPressed = pressed
            1 -> secondaryPressed = pressed
            2 -> tertiaryPressed = pressed
            3 -> backPressed = pressed
            4 -> forwardPressed = pressed
        }
    }

    private fun pointerButton(button: Int): PointerButton? {
        return when (button) {
            0 -> PointerButton.Primary
            1 -> PointerButton.Secondary
            2 -> PointerButton.Tertiary
            3 -> PointerButton.Back
            4 -> PointerButton.Forward
            else -> null
        }
    }

    private fun pointerButtons(): PointerButtons {
        return PointerButtons(
            isPrimaryPressed = primaryPressed,
            isSecondaryPressed = secondaryPressed,
            isTertiaryPressed = tertiaryPressed,
            isBackPressed = backPressed,
            isForwardPressed = forwardPressed
        )
    }

    private fun keyboardModifiers(modifiers: Int): PointerKeyboardModifiers {
        return PointerKeyboardModifiers(
            isCtrlPressed = modifiers and MODIFIER_CONTROL != 0,
            isMetaPressed = modifiers and MODIFIER_META != 0,
            isAltPressed = modifiers and MODIFIER_ALT != 0,
            isShiftPressed = modifiers and MODIFIER_SHIFT != 0
        ).also { windowInfo.keyboardModifiers = it }
    }

    private fun composeKey(keyCode: Int): Key {
        return when (keyCode) {
            GLFW_KEY_UNKNOWN -> Key.Unknown
            GLFW_KEY_SPACE -> Key.Spacebar
            GLFW_KEY_APOSTROPHE -> Key.Apostrophe
            GLFW_KEY_COMMA -> Key.Comma
            GLFW_KEY_MINUS -> Key.Minus
            GLFW_KEY_PERIOD -> Key.Period
            GLFW_KEY_SLASH -> Key.Slash
            GLFW_KEY_SEMICOLON -> Key.Semicolon
            GLFW_KEY_EQUAL -> Key.Equals
            GLFW_KEY_LEFT_BRACKET -> Key.LeftBracket
            GLFW_KEY_BACKSLASH -> Key.Backslash
            GLFW_KEY_RIGHT_BRACKET -> Key.RightBracket
            GLFW_KEY_GRAVE_ACCENT -> Key.Grave
            GLFW_KEY_ESCAPE -> Key.Escape
            GLFW_KEY_ENTER -> Key.Enter
            GLFW_KEY_TAB -> Key.Tab
            GLFW_KEY_BACKSPACE -> Key.Backspace
            GLFW_KEY_INSERT -> Key.Insert
            GLFW_KEY_DELETE -> Key.Delete
            GLFW_KEY_RIGHT -> Key.DirectionRight
            GLFW_KEY_LEFT -> Key.DirectionLeft
            GLFW_KEY_DOWN -> Key.DirectionDown
            GLFW_KEY_UP -> Key.DirectionUp
            GLFW_KEY_PAGE_UP -> Key.PageUp
            GLFW_KEY_PAGE_DOWN -> Key.PageDown
            GLFW_KEY_HOME -> Key.MoveHome
            GLFW_KEY_END -> Key.MoveEnd
            GLFW_KEY_CAPS_LOCK -> Key.CapsLock
            GLFW_KEY_SCROLL_LOCK -> Key.ScrollLock
            GLFW_KEY_PRINT_SCREEN -> Key.PrintScreen
            GLFW_KEY_PAUSE -> Key.Break
            GLFW_KEY_F1 -> Key.F1
            GLFW_KEY_F2 -> Key.F2
            GLFW_KEY_F3 -> Key.F3
            GLFW_KEY_F4 -> Key.F4
            GLFW_KEY_F5 -> Key.F5
            GLFW_KEY_F6 -> Key.F6
            GLFW_KEY_F7 -> Key.F7
            GLFW_KEY_F8 -> Key.F8
            GLFW_KEY_F9 -> Key.F9
            GLFW_KEY_F10 -> Key.F10
            GLFW_KEY_F11 -> Key.F11
            GLFW_KEY_F12 -> Key.F12
            GLFW_KEY_KP_0 -> Key.NumPad0
            GLFW_KEY_KP_1 -> Key.NumPad1
            GLFW_KEY_KP_2 -> Key.NumPad2
            GLFW_KEY_KP_3 -> Key.NumPad3
            GLFW_KEY_KP_4 -> Key.NumPad4
            GLFW_KEY_KP_5 -> Key.NumPad5
            GLFW_KEY_KP_6 -> Key.NumPad6
            GLFW_KEY_KP_7 -> Key.NumPad7
            GLFW_KEY_KP_8 -> Key.NumPad8
            GLFW_KEY_KP_9 -> Key.NumPad9
            GLFW_KEY_KP_DECIMAL -> Key.NumPadDot
            GLFW_KEY_KP_DIVIDE -> Key.NumPadDivide
            GLFW_KEY_KP_MULTIPLY -> Key.NumPadMultiply
            GLFW_KEY_KP_SUBTRACT -> Key.NumPadSubtract
            GLFW_KEY_KP_ADD -> Key.NumPadAdd
            GLFW_KEY_KP_ENTER -> Key.NumPadEnter
            GLFW_KEY_KP_EQUAL -> Key.NumPadEquals
            GLFW_KEY_LEFT_SHIFT -> Key(AwtKeyEvent.VK_SHIFT, AwtKeyEvent.KEY_LOCATION_LEFT)
            GLFW_KEY_RIGHT_SHIFT -> Key(AwtKeyEvent.VK_SHIFT, AwtKeyEvent.KEY_LOCATION_RIGHT)
            GLFW_KEY_LEFT_CONTROL -> Key(AwtKeyEvent.VK_CONTROL, AwtKeyEvent.KEY_LOCATION_LEFT)
            GLFW_KEY_RIGHT_CONTROL -> Key(AwtKeyEvent.VK_CONTROL, AwtKeyEvent.KEY_LOCATION_RIGHT)
            GLFW_KEY_LEFT_ALT -> Key(AwtKeyEvent.VK_ALT, AwtKeyEvent.KEY_LOCATION_LEFT)
            GLFW_KEY_RIGHT_ALT -> Key(AwtKeyEvent.VK_ALT, AwtKeyEvent.KEY_LOCATION_RIGHT)
            GLFW_KEY_LEFT_SUPER -> Key(AwtKeyEvent.VK_META, AwtKeyEvent.KEY_LOCATION_LEFT)
            GLFW_KEY_RIGHT_SUPER -> Key(AwtKeyEvent.VK_META, AwtKeyEvent.KEY_LOCATION_RIGHT)
            in GLFW_KEY_0..GLFW_KEY_9,
            in GLFW_KEY_A..GLFW_KEY_Z -> Key(keyCode)
            else -> Key.Unknown
        }
    }

    private class OffscreenWindowInfo : WindowInfo {
        override var isWindowFocused: Boolean = false
        override var keyboardModifiers: PointerKeyboardModifiers = PointerKeyboardModifiers()
        override var containerSize: IntSize = IntSize.Zero
    }

    companion object {
        private const val MIN_DENSITY_SCALE = 0.1f
        private const val MODIFIER_SHIFT = 0x1
        private const val MODIFIER_CONTROL = 0x2
        private const val MODIFIER_ALT = 0x4
        private const val MODIFIER_META = 0x8
    }
}

