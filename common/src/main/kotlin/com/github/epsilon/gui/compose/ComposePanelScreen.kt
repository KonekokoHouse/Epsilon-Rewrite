package com.github.epsilon.gui.compose

import com.github.epsilon.addon.EpsilonAddon
import com.github.epsilon.gui.panel.MD3Theme
import com.github.epsilon.gui.panel.PanelLayout
import com.github.epsilon.gui.panel.PanelState
import com.github.epsilon.managers.AddonManager
import com.github.epsilon.managers.ConfigManager
import com.github.epsilon.managers.FriendManager
import com.github.epsilon.managers.ModuleManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.ClientSetting
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.settings.impl.ButtonSetting
import com.github.epsilon.settings.impl.ColorSetting
import com.github.epsilon.settings.impl.DoubleSetting
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.settings.impl.IntSetting
import com.github.epsilon.settings.impl.KeybindSetting
import com.github.epsilon.settings.impl.StringSetting
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.awt.Color as AwtColor

object ComposePanelScreen : Screen(Component.literal("PanelGuiCompose")) {
    private val panelState = PanelState()
    private var composeSurface: ComposeSurface<ComposePanelUiState>? = null

    private var listeningModule: Module? = null
    private var listeningSetting: KeybindSetting? = null
    private var friendInput = ""
    private var configInput = ""

    private var modalPopupState: ComposePopupState? = null
    private var popupConfirmAction: (() -> Unit)? = null
    private var enumPopupSetting: EnumSetting<*>? = null
    private var colorPopupSetting: ColorSetting? = null

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val bounds = panelBounds()
        val client = minecraft
        val renderScale = client.window.guiScale.coerceAtLeast(1)
        val densityScale = renderScale * PANEL_VISUAL_SCALE

        graphics.fill(0, 0, width, height, 0x7A000000)

        MD3Theme.syncFromSettings()
        val state = buildUiState()
        val renderedPanel = surface().render(bounds.width * renderScale, bounds.height * renderScale, densityScale, state)
        graphics.blit(
            renderedPanel.textureView,
            renderedPanel.sampler,
            bounds.left,
            bounds.top,
            bounds.left + bounds.width,
            bounds.top + bounds.height,
            0.0f,
            1.0f,
            1.0f,
            0.0f
        )
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        val bounds = panelBounds()
        val inside = bounds.contains(event.x(), event.y())
        if (!inside && event.button() == 0) {
            composeSurface?.clearFocus()
            if (activePopupState() != null) {
                dismissPopup()
                return true
            }
            if (ClientSetting.INSTANCE.closeOnOutside.value) {
                minecraft.setScreen(null)
            }
            return true
        }
        if (surface().handleMousePressed(bounds.localX(event.x()), bounds.localY(event.y()), inside, event.button(), event.modifiers())) {
            return true
        }
        return super.mouseClicked(event, isDoubleClick)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val bounds = panelBounds()
        if (surface().handleMouseReleased(bounds.localX(event.x()), bounds.localY(event.y()), bounds.contains(event.x(), event.y()), event.button(), event.modifiers())) {
            return true
        }
        return super.mouseReleased(event)
    }

    override fun mouseMoved(x: Double, y: Double) {
        super.mouseMoved(x, y)
        val bounds = panelBounds()
        surface().handleMouseMoved(bounds.localX(x), bounds.localY(y), bounds.contains(x, y))
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val bounds = panelBounds()
        if (surface().handleMouseDragged(bounds.localX(event.x()), bounds.localY(event.y()), bounds.contains(event.x(), event.y()), event.modifiers())) {
            return true
        }
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean {
        val bounds = panelBounds()
        if (surface().handleMouseScroll(bounds.localX(x), bounds.localY(y), bounds.contains(x, y), scrollX.toFloat(), scrollY.toFloat(), 0)) {
            return true
        }
        return super.mouseScrolled(x, y, scrollX, scrollY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (activePopupState() != null && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            dismissPopup()
            return true
        }
        if (handleKeyCapture(event.key())) {
            return true
        }
        if (surface().handleKeyPressed(event.key(), event.modifiers())) {
            return true
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }
        return super.keyPressed(event)
    }

    override fun keyReleased(event: KeyEvent): Boolean {
        if (listeningModule != null || listeningSetting != null) {
            return true
        }
        if (surface().handleKeyReleased(event.key(), event.modifiers())) {
            return true
        }
        return super.keyReleased(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (listeningModule != null || listeningSetting != null) {
            return true
        }
        if (surface().handleCharTyped(event.codepoint(), 0)) {
            return true
        }
        return super.charTyped(event)
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        composeSurface?.invalidate()
    }

    override fun removed() {
        super.removed()
        composeSurface?.clearFocus()
        composeSurface?.close()
        composeSurface = null
        listeningModule = null
        listeningSetting = null
        dismissPopup()
    }

    override fun onClose() {
        composeSurface?.clearFocus()
        listeningModule = null
        listeningSetting = null
        dismissPopup()
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false

    private fun handleKeyCapture(keyCode: Int): Boolean {
        val module = listeningModule
        if (module != null) {
            when (keyCode) {
                GLFW.GLFW_KEY_ESCAPE -> Unit
                GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE -> module.setKeyBind(-1)
                else -> module.setKeyBind(keyCode)
            }
            listeningModule = null
            return true
        }

        val setting = listeningSetting
        if (setting != null) {
            when (keyCode) {
                GLFW.GLFW_KEY_ESCAPE -> Unit
                GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE -> setting.setValue(-1)
                else -> setting.setValue(keyCode)
            }
            listeningSetting = null
            return true
        }

        return false
    }

    private fun buildUiState(): ComposePanelUiState {
        val client = minecraft
        val worldName = client.currentServer?.name ?: if (client.level != null) {
            ComposePanelI18n.singleplayer.text()
        } else {
            ComposePanelI18n.noWorld.text()
        }
        val playerName = client.player?.name?.string ?: "Player"
        val categories = Category.entries.map { category ->
            ComposeCategoryItemState(
                category = category,
                title = category.getName(),
                count = ModuleManager.INSTANCE.modules.count { it.category == category },
                selected = !panelState.isClientSettingMode && panelState.selectedCategory == category
            )
        }
        val modules = panelState.visibleModules.map { module ->
            ComposeModuleItemState(
                module = module,
                id = module.name,
                title = module.translatedName,
                subtitle = buildModuleSubtitle(module),
                keybindLabel = keyLabel(module.keyBind),
                enabled = module.isEnabled,
                selected = !panelState.isClientSettingMode && panelState.selectedModule == module
            )
        }
        val detail = if (panelState.isClientSettingMode) {
            ComposeDetailState(
                title = ComposePanelI18n.clientSettings.text(),
                subtitle = ComposePanelI18n.clientSettingsSubtitle.text(),
                enabled = false,
                bindMode = Module.BindMode.Toggle,
                keybindLabel = keyLabel(-1),
                hidden = false,
                showModuleControls = false,
                listeningModuleKeybind = false,
                settings = emptyList(),
                emptyMessage = null
            )
        } else {
            buildDetailState(
                title = panelState.selectedModule?.translatedName ?: ComposePanelI18n.noModule.text(),
                subtitle = panelState.selectedModule?.let(::buildModuleSubtitle) ?: ComposePanelI18n.detailSelectHint.text(),
                module = panelState.selectedModule,
                showModuleControls = panelState.selectedModule != null
            )
        }
        val clientSettings = if (panelState.isClientSettingMode) buildClientSettingsState() else null
        return ComposePanelUiState(
            title = "Open Epsilon",
            subtitle = "$worldName · $playerName",
            sidebarExpanded = panelState.isSidebarExpanded,
            clientSettingMode = panelState.isClientSettingMode,
            categories = categories,
            modules = modules,
            searchQuery = panelState.searchQuery,
            selectedCategoryLabel = if (panelState.isClientSettingMode) ComposePanelI18n.modules.text() else panelState.selectedCategory.getName(),
            theme = currentThemeState(),
            detail = detail,
            clientSettings = clientSettings,
            popupState = activePopupState(),
            onSidebarToggle = { panelState.toggleSidebarExpanded() },
            onCategorySelected = { category ->
                listeningModule = null
                listeningSetting = null
                dismissPopup()
                panelState.setClientSettingMode(false)
                panelState.selectedCategory = category
            },
            onClientSettingSelected = {
                listeningModule = null
                listeningSetting = null
                dismissPopup()
                panelState.setClientSettingMode(true)
            },
            onSearchQueryChanged = { panelState.searchQuery = it },
            onModuleSelected = { module ->
                listeningModule = null
                listeningSetting = null
                dismissPopup()
                panelState.setClientSettingMode(false)
                panelState.selectedModule = module
            },
            onModuleToggle = { module ->
                panelState.setClientSettingMode(false)
                module.toggle()
            },
            onDetailEnabledChanged = { enabled ->
                panelState.selectedModule?.setEnabled(enabled)
            },
            onDetailBindModeChanged = { mode ->
                panelState.selectedModule?.bindMode = mode
            },
            onDetailVisibilityChanged = { hidden ->
                panelState.selectedModule?.isHidden = hidden
            },
            onModuleKeybindCaptureRequested = {
                dismissPopup()
                listeningSetting = null
                listeningModule = panelState.selectedModule
            },
            onSettingBoolChanged = { setting, value -> setting.value = value },
            onSettingIntChanged = { setting, value -> setting.value = value },
            onSettingDoubleChanged = { setting, value -> setting.value = value },
            onSettingStringChanged = { setting, value -> setting.value = value },
            onSettingEnumSelected = { setting, index -> selectEnumIndex(setting, index) },
            onSettingColorChanged = { setting, r, g, b, a ->
                setting.value = AwtColor(r, g, b, if (setting.isAllowAlpha) a else 255)
            },
            onSettingEnumPopupRequested = { setting ->
                dismissPopup()
                enumPopupSetting = setting
            },
            onSettingColorPopupRequested = { setting ->
                dismissPopup()
                colorPopupSetting = setting
            },
            onSettingKeybindCaptureRequested = { setting ->
                dismissPopup()
                listeningModule = null
                listeningSetting = setting
            },
            onSettingButtonInvoked = { setting -> setting.value.run() },
            onPopupConfirm = {
                val action = popupConfirmAction
                dismissPopup()
                action?.invoke()
            },
            onPopupDismiss = { dismissPopup() }
        )
    }

    private fun buildClientSettingsState(): ComposeClientSettingsState {
        val currentTab = panelState.clientSettingTab
        if (configInput.isBlank()) {
            configInput = ConfigManager.INSTANCE.activeConfigName
        }

        val generalSettings = ClientSetting.INSTANCE.settings
            .filter(Setting<*>::isAvailable)
            .mapNotNull(::mapSetting)

        val friends = FriendManager.INSTANCE.friends
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .map(::ComposeFriendItemState)

        val configs = ConfigManager.INSTANCE.listConfigs().map { name ->
            ComposeConfigItemState(name = name, active = name == ConfigManager.INSTANCE.activeConfigName)
        }

        val addons = AddonManager.INSTANCE.addons
        val selectedAddon = resolveSelectedAddon(addons)
        val addonItems = addons.map { addon ->
            ComposeAddonItemState(
                addonId = addon.addonId,
                title = addon.displayName,
                subtitle = addon.addonId,
                moduleCount = addon.registeredModules.size,
                selected = selectedAddon?.addonId == addon.addonId
            )
        }
        val selectedAddonDetail = selectedAddon?.let { addon ->
            ComposeAddonDetailState(
                title = addon.displayName,
                addonId = addon.addonId,
                version = addon.version.ifBlank { "-" },
                authors = addon.authors.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "-",
                description = addon.description,
                moduleCount = addon.registeredModules.size,
                settings = addon.settings.filter(Setting<*>::isAvailable).mapNotNull(::mapSetting)
            )
        }

        return ComposeClientSettingsState(
            title = ComposePanelI18n.clientSettings.text(),
            subtitle = ComposePanelI18n.clientSettingsSubtitle.text(),
            tabs = listOf(
                ComposeClientSettingsTabState(PanelState.ClientSettingTab.GENERAL, ComposePanelI18n.generalTab.text(), currentTab == PanelState.ClientSettingTab.GENERAL),
                ComposeClientSettingsTabState(PanelState.ClientSettingTab.FRIEND, ComposePanelI18n.friendTab.text(), currentTab == PanelState.ClientSettingTab.FRIEND),
                ComposeClientSettingsTabState(PanelState.ClientSettingTab.CONFIG, ComposePanelI18n.configTab.text(), currentTab == PanelState.ClientSettingTab.CONFIG),
                ComposeClientSettingsTabState(PanelState.ClientSettingTab.ADDON, ComposePanelI18n.addonTab.text(), currentTab == PanelState.ClientSettingTab.ADDON)
            ),
            selectedTab = currentTab,
            generalSettings = generalSettings,
            friendInput = friendInput,
            friends = friends,
            configInput = configInput,
            configs = configs,
            activeConfigName = ConfigManager.INSTANCE.activeConfigName,
            addons = addonItems,
            selectedAddon = selectedAddonDetail,
            onTabSelected = { tab ->
                listeningSetting = null
                dismissPopup()
                panelState.clientSettingTab = tab
                if (tab == PanelState.ClientSettingTab.CONFIG && configInput.isBlank()) {
                    configInput = ConfigManager.INSTANCE.activeConfigName
                }
            },
            onFriendInputChanged = { friendInput = it.take(32) },
            onFriendAddRequested = { addFriend() },
            onFriendRemoved = { name ->
                FriendManager.INSTANCE.removeFriend(name)
                ConfigManager.INSTANCE.saveNow()
            },
            onConfigInputChanged = { configInput = it },
            onConfigSaveAsRequested = { saveConfigAs() },
            onConfigReloadRequested = { reloadConfig() },
            onConfigExportRequested = { exportConfig() },
            onConfigImportRequested = { importConfig() },
            onConfigSelected = { name -> switchConfig(name) },
            onConfigDeleteRequested = { name -> confirmDeleteConfig(name) },
            onAddonSelected = { addonId ->
                listeningSetting = null
                dismissPopup()
                panelState.selectedAddonId = addonId
                panelState.setAddonDetailScroll(0f)
            }
        )
    }

    private fun buildDetailState(
        title: String,
        subtitle: String,
        module: Module?,
        showModuleControls: Boolean
    ): ComposeDetailState {
        if (module == null) {
            return ComposeDetailState(
                title = title,
                subtitle = subtitle,
                enabled = false,
                bindMode = Module.BindMode.Toggle,
                keybindLabel = keyLabel(-1),
                hidden = false,
                showModuleControls = false,
                listeningModuleKeybind = false,
                settings = emptyList(),
                emptyMessage = ComposePanelI18n.detailSelectHint.text()
            )
        }

        val settings = module.settings
            .filter(Setting<*>::isAvailable)
            .mapNotNull(::mapSetting)

        return ComposeDetailState(
            title = title,
            subtitle = subtitle,
            enabled = module.isEnabled,
            bindMode = module.bindMode,
            keybindLabel = keyLabel(module.keyBind),
            hidden = module.isHidden,
            showModuleControls = showModuleControls,
            listeningModuleKeybind = showModuleControls && listeningModule == module,
            settings = settings,
            emptyMessage = if (settings.isEmpty()) ComposePanelI18n.settingsEmpty.text() else null
        )
    }

    private fun resolveSelectedAddon(addons: List<EpsilonAddon>): EpsilonAddon? {
        if (addons.isEmpty()) {
            if (panelState.selectedAddonId.isNotEmpty()) {
                panelState.selectedAddonId = ""
            }
            return null
        }

        addons.firstOrNull { it.addonId == panelState.selectedAddonId }?.let { return it }
        val fallback = addons.first()
        panelState.selectedAddonId = fallback.addonId
        return fallback
    }

    private fun addFriend() {
        val name = friendInput.trim()
        if (name.isEmpty()) {
            return
        }
        if (!FriendManager.INSTANCE.isFriend(name)) {
            FriendManager.INSTANCE.addFriend(name)
            ConfigManager.INSTANCE.saveNow()
        }
        friendInput = ""
    }

    private fun saveConfigAs() {
        val targetName = configInput.trim()
        if (targetName.isEmpty()) {
            return
        }
        try {
            val savedName = ConfigManager.INSTANCE.saveAsConfig(targetName)
            configInput = savedName
            showMessagePopup(
                title = ComposePanelI18n.configSaveSuccessTitle.text(),
                message = ComposePanelI18n.configSaveSuccessMessage.text(),
                detail = savedName
            )
        } catch (exception: Exception) {
            showErrorPopup(ComposePanelI18n.configSaveError.text(), exception)
        }
    }

    private fun reloadConfig() {
        try {
            ConfigManager.INSTANCE.reloadOrThrow()
            showMessagePopup(
                title = ComposePanelI18n.configReloadSuccessTitle.text(),
                message = ComposePanelI18n.configReloadSuccessMessage.text(),
                detail = ConfigManager.INSTANCE.activeConfigName
            )
        } catch (exception: Exception) {
            showErrorPopup(ComposePanelI18n.configReloadError.text(), exception)
        }
    }

    private fun exportConfig() {
        try {
            val exported = ConfigManager.INSTANCE.exportActiveConfigToZip(configInput)
            val exportName = exported.fileName?.toString() ?: exported.toString()
            showMessagePopup(
                title = ComposePanelI18n.configExportSuccessTitle.text(),
                message = ComposePanelI18n.configExportSuccessMessage.text(),
                detail = exportName
            )
        } catch (exception: Exception) {
            showErrorPopup(ComposePanelI18n.configExportError.text(), exception)
        }
    }

    private fun importConfig() {
        val zipPath = configInput.trim()
        if (zipPath.isEmpty()) {
            return
        }
        try {
            val importedName = ConfigManager.INSTANCE.importConfigFromZip(zipPath)
            configInput = importedName
            showMessagePopup(
                title = ComposePanelI18n.configImportSuccessTitle.text(),
                message = ComposePanelI18n.configImportSuccessMessage.text(),
                detail = importedName
            )
        } catch (exception: Exception) {
            showErrorPopup(ComposePanelI18n.configImportError.text(), exception)
        }
    }

    private fun switchConfig(name: String) {
        if (name == ConfigManager.INSTANCE.activeConfigName) {
            configInput = name
            return
        }
        try {
            ConfigManager.INSTANCE.switchConfig(name)
            configInput = name
            showMessagePopup(
                title = ComposePanelI18n.configSwitchSuccessTitle.text(),
                message = ComposePanelI18n.configSwitchSuccessMessage.text(),
                detail = name
            )
        } catch (exception: Exception) {
            showErrorPopup(ComposePanelI18n.configSwitchError.text(), exception)
        }
    }

    private fun confirmDeleteConfig(name: String) {
        dismissPopup()
        popupConfirmAction = {
            try {
                val deleted = ConfigManager.INSTANCE.deleteConfig(name)
                if (!deleted) {
                    showMessagePopup(
                        title = ComposePanelI18n.configErrorTitle.text(),
                        message = ComposePanelI18n.configDeleteLastError.text()
                    )
                } else {
                    if (configInput.trim() == name) {
                        configInput = ConfigManager.INSTANCE.activeConfigName
                    }
                    showMessagePopup(
                        title = ComposePanelI18n.configDeleteSuccessTitle.text(),
                        message = ComposePanelI18n.configDeleteSuccessMessage.text(),
                        detail = name
                    )
                }
            } catch (exception: Exception) {
                showErrorPopup(ComposePanelI18n.configDeleteError.text(), exception)
            }
        }
        modalPopupState = ComposeConfirmPopupState(
            title = ComposePanelI18n.configDeleteConfirmTitle.text(),
            message = ComposePanelI18n.configDeleteConfirmMessage.text(),
            detail = name,
            confirmLabel = ComposePanelI18n.configDeleteConfirmConfirm.text(),
            dismissLabel = ComposePanelI18n.configDeleteConfirmCancel.text(),
            destructive = true
        )
    }

    private fun showMessagePopup(title: String, message: String, detail: String? = null) {
        dismissPopup()
        modalPopupState = ComposeMessagePopupState(
            title = title,
            message = message,
            detail = detail,
            confirmLabel = ComposePanelI18n.popupOk.text()
        )
    }

    private fun showErrorPopup(message: String, exception: Exception) {
        val detail = exception.message?.takeIf { it.isNotBlank() } ?: exception.javaClass.simpleName
        dismissPopup()
        modalPopupState = ComposeMessagePopupState(
            title = ComposePanelI18n.configErrorTitle.text(),
            message = message,
            detail = detail,
            confirmLabel = ComposePanelI18n.popupOk.text()
        )
    }

    private fun activePopupState(): ComposePopupState? {
        val modal = modalPopupState
        if (modal != null) {
            return modal
        }
        val enumSetting = enumPopupSetting
        if (enumSetting != null) {
            return ComposeEnumPopupState(
                setting = enumSetting,
                settingId = settingId(enumSetting),
                title = enumSetting.displayName,
                selectedIndex = enumSetting.modeIndex,
                options = enumSetting.modes.indices.map(enumSetting::getTranslatedValueByIndex)
            )
        }
        val colorSetting = colorPopupSetting
        if (colorSetting != null) {
            return ComposeColorPopupState(
                setting = colorSetting,
                settingId = settingId(colorSetting),
                title = colorSetting.displayName,
                red = colorSetting.value.red,
                green = colorSetting.value.green,
                blue = colorSetting.value.blue,
                alpha = colorSetting.value.alpha,
                allowAlpha = colorSetting.isAllowAlpha
            )
        }
        return null
    }

    private fun dismissPopup() {
        modalPopupState = null
        popupConfirmAction = null
        enumPopupSetting = null
        colorPopupSetting = null
    }

    private fun mapSetting(setting: Setting<*>): ComposeSettingItemState? {
        val title = setting.displayName
        val summary = setting.name
        return when (setting) {
            is BoolSetting -> ComposeBoolSettingItemState(
                setting = setting,
                id = settingId(setting),
                title = title,
                summary = summary,
                value = setting.value
            )

            is IntSetting -> ComposeIntSettingItemState(
                setting = setting,
                id = settingId(setting),
                title = title,
                summary = summary,
                value = setting.value,
                min = setting.min,
                max = setting.max,
                step = setting.step,
                percentageMode = setting.isPercentageMode
            )

            is DoubleSetting -> ComposeDoubleSettingItemState(
                setting = setting,
                id = settingId(setting),
                title = title,
                summary = summary,
                value = setting.value,
                min = setting.min,
                max = setting.max,
                step = setting.step,
                percentageMode = setting.isPercentageMode
            )

            is StringSetting -> ComposeStringSettingItemState(
                setting = setting,
                id = settingId(setting),
                title = title,
                summary = summary,
                value = setting.value
            )

            is EnumSetting<*> -> ComposeEnumSettingItemState(
                setting = setting,
                id = settingId(setting),
                title = title,
                summary = summary,
                selectedIndex = setting.modeIndex,
                selectedLabel = setting.translatedValue,
                options = setting.modes.indices.map(setting::getTranslatedValueByIndex)
            )

            is ColorSetting -> ComposeColorSettingItemState(
                setting = setting,
                id = settingId(setting),
                title = title,
                summary = summary,
                red = setting.value.red,
                green = setting.value.green,
                blue = setting.value.blue,
                alpha = setting.value.alpha,
                allowAlpha = setting.isAllowAlpha
            )

            is KeybindSetting -> ComposeKeybindSettingItemState(
                setting = setting,
                id = settingId(setting),
                title = title,
                summary = summary,
                keyLabel = keyLabel(setting.value),
                listening = listeningSetting == setting
            )

            is ButtonSetting -> ComposeButtonSettingItemState(
                setting = setting,
                id = settingId(setting),
                title = title,
                summary = summary
            )

            else -> null
        }
    }

    private fun selectEnumIndex(setting: EnumSetting<*>, index: Int) {
        val modes = setting.modes
        if (index !in modes.indices) {
            return
        }
        setting.setMode(modes[index].toString())
    }

    private fun surface(): ComposeSurface<ComposePanelUiState> {
        return composeSurface ?: ComposeSurface(ComposePanelUiState.EMPTY) { ComposePanelApp(it) }.also {
            composeSurface = it
        }
    }

    private fun currentThemeState(): ComposePanelThemeState {
        return ComposePanelThemeState(
            isLight = MD3Theme.isLightTheme(),
            backgroundArgb = MD3Theme.SURFACE.rgb,
            surfaceArgb = MD3Theme.SURFACE_CONTAINER.rgb,
            surfaceVariantArgb = MD3Theme.SURFACE_CONTAINER_HIGH.rgb,
            outlineArgb = MD3Theme.OUTLINE.rgb,
            primaryArgb = MD3Theme.PRIMARY.rgb,
            primaryContainerArgb = MD3Theme.PRIMARY_CONTAINER.rgb,
            secondaryContainerArgb = MD3Theme.SECONDARY_CONTAINER.rgb,
            onPrimaryArgb = MD3Theme.ON_PRIMARY.rgb,
            onSurfaceArgb = MD3Theme.TEXT_PRIMARY.rgb,
            onSurfaceVariantArgb = MD3Theme.TEXT_SECONDARY.rgb,
            errorArgb = MD3Theme.ERROR.rgb
        )
    }

    private fun buildModuleSubtitle(module: Module): String {
        val categoryLabel = module.category?.getName() ?: ComposePanelI18n.clientSettings.text()
        val addonId = module.addonId?.takeIf { it.isNotBlank() } ?: "epsilon"
        return "$categoryLabel · $addonId"
    }

    private fun settingId(setting: Setting<*>): String {
        return "${setting.javaClass.simpleName}:${setting.name}"
    }

    private fun keyLabel(keyCode: Int): String {
        if (keyCode < 0) {
            return ComposePanelI18n.keybindNone.text()
        }
        return InputConstants.Type.KEYSYM.getOrCreate(keyCode).displayName.string
    }

    private fun panelBounds(): PanelBounds {
        val railWidth = if (panelState.isSidebarExpanded) MD3Theme.RAIL_EXPANDED_WIDTH else MD3Theme.RAIL_COLLAPSED_WIDTH
        val layout = PanelLayout.compute(width, height, railWidth)
        return PanelBounds(
            left = layout.panel().x().toInt(),
            top = layout.panel().y().toInt(),
            width = layout.panel().width().toInt(),
            height = layout.panel().height().toInt()
        )
    }

    private data class PanelBounds(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int
    ) {
        private fun scale(): Int = MinecraftScaleHolder.scale()

        fun contains(x: Double, y: Double): Boolean {
            return x >= left && x < left + width && y >= top && y < top + height
        }

        fun localX(x: Double): Float {
            return ((x - left) * scale()).toFloat()
        }

        fun localY(y: Double): Float {
            return ((y - top) * scale()).toFloat()
        }
    }

    private object MinecraftScaleHolder {
        fun scale(): Int {
            return Minecraft.getInstance().window.guiScale.coerceAtLeast(1)
        }
    }

    private const val PANEL_VISUAL_SCALE = 0.50f
}

