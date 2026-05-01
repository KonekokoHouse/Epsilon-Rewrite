package com.github.epsilon.gui.compose

import androidx.compose.runtime.Immutable
import com.github.epsilon.gui.panel.PanelState
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.settings.impl.ButtonSetting
import com.github.epsilon.settings.impl.ColorSetting
import com.github.epsilon.settings.impl.DoubleSetting
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.settings.impl.IntSetting
import com.github.epsilon.settings.impl.KeybindSetting
import com.github.epsilon.settings.impl.StringSetting

@Immutable
data class ComposePanelThemeState(
    val isLight: Boolean = false,
    val backgroundArgb: Int = 0xFF0F1116.toInt(),
    val surfaceArgb: Int = 0xFF171A21.toInt(),
    val surfaceVariantArgb: Int = 0xFF20242D.toInt(),
    val outlineArgb: Int = 0xFF6A7280.toInt(),
    val primaryArgb: Int = 0xFF9D8CFF.toInt(),
    val primaryContainerArgb: Int = 0xFF3B3156.toInt(),
    val secondaryContainerArgb: Int = 0xFF2A3140.toInt(),
    val onPrimaryArgb: Int = 0xFF0F0F14.toInt(),
    val onSurfaceArgb: Int = 0xFFF2F4F8.toInt(),
    val onSurfaceVariantArgb: Int = 0xFFB6BECA.toInt(),
    val errorArgb: Int = 0xFFFF6B6B.toInt()
)

@Immutable
data class ComposeCategoryItemState(
    val category: Category,
    val title: String,
    val count: Int,
    val selected: Boolean
)

@Immutable
data class ComposeModuleItemState(
    val module: Module,
    val id: String,
    val title: String,
    val subtitle: String,
    val keybindLabel: String,
    val enabled: Boolean,
    val selected: Boolean
)

sealed interface ComposeSettingItemState {
    val id: String
    val title: String
    val summary: String?
}

@Immutable
data class ComposeBoolSettingItemState(
    val setting: BoolSetting,
    override val id: String,
    override val title: String,
    override val summary: String?,
    val value: Boolean
) : ComposeSettingItemState

@Immutable
data class ComposeIntSettingItemState(
    val setting: IntSetting,
    override val id: String,
    override val title: String,
    override val summary: String?,
    val value: Int,
    val min: Int,
    val max: Int,
    val step: Int,
    val percentageMode: Boolean
) : ComposeSettingItemState

@Immutable
data class ComposeDoubleSettingItemState(
    val setting: DoubleSetting,
    override val id: String,
    override val title: String,
    override val summary: String?,
    val value: Double,
    val min: Double,
    val max: Double,
    val step: Double,
    val percentageMode: Boolean
) : ComposeSettingItemState

@Immutable
data class ComposeStringSettingItemState(
    val setting: StringSetting,
    override val id: String,
    override val title: String,
    override val summary: String?,
    val value: String
) : ComposeSettingItemState

@Immutable
data class ComposeEnumSettingItemState(
    val setting: EnumSetting<*>,
    override val id: String,
    override val title: String,
    override val summary: String?,
    val selectedIndex: Int,
    val selectedLabel: String,
    val options: List<String>
) : ComposeSettingItemState

@Immutable
data class ComposeColorSettingItemState(
    val setting: ColorSetting,
    override val id: String,
    override val title: String,
    override val summary: String?,
    val red: Int,
    val green: Int,
    val blue: Int,
    val alpha: Int,
    val allowAlpha: Boolean
) : ComposeSettingItemState

@Immutable
data class ComposeKeybindSettingItemState(
    val setting: KeybindSetting,
    override val id: String,
    override val title: String,
    override val summary: String?,
    val keyLabel: String,
    val listening: Boolean
) : ComposeSettingItemState

@Immutable
data class ComposeButtonSettingItemState(
    val setting: ButtonSetting,
    override val id: String,
    override val title: String,
    override val summary: String?
) : ComposeSettingItemState

@Immutable
data class ComposeDetailState(
    val title: String,
    val subtitle: String,
    val enabled: Boolean,
    val bindMode: Module.BindMode,
    val keybindLabel: String,
    val hidden: Boolean,
    val showModuleControls: Boolean,
    val listeningModuleKeybind: Boolean,
    val settings: List<ComposeSettingItemState>,
    val emptyMessage: String? = null
)

@Immutable
data class ComposeClientSettingsTabState(
    val tab: PanelState.ClientSettingTab,
    val title: String,
    val selected: Boolean
)

@Immutable
data class ComposeFriendItemState(
    val name: String
)

@Immutable
data class ComposeConfigItemState(
    val name: String,
    val active: Boolean
)

@Immutable
data class ComposeAddonItemState(
    val addonId: String,
    val title: String,
    val subtitle: String,
    val moduleCount: Int,
    val selected: Boolean
)

@Immutable
data class ComposeAddonDetailState(
    val title: String,
    val addonId: String,
    val version: String,
    val authors: String,
    val description: String,
    val moduleCount: Int,
    val settings: List<ComposeSettingItemState>
)

@Immutable
data class ComposeClientSettingsState(
    val title: String,
    val subtitle: String,
    val tabs: List<ComposeClientSettingsTabState>,
    val selectedTab: PanelState.ClientSettingTab,
    val generalSettings: List<ComposeSettingItemState>,
    val friendInput: String,
    val friends: List<ComposeFriendItemState>,
    val configInput: String,
    val configs: List<ComposeConfigItemState>,
    val activeConfigName: String,
    val addons: List<ComposeAddonItemState>,
    val selectedAddon: ComposeAddonDetailState?,
    val onTabSelected: (PanelState.ClientSettingTab) -> Unit,
    val onFriendInputChanged: (String) -> Unit,
    val onFriendAddRequested: () -> Unit,
    val onFriendRemoved: (String) -> Unit,
    val onConfigInputChanged: (String) -> Unit,
    val onConfigSaveAsRequested: () -> Unit,
    val onConfigReloadRequested: () -> Unit,
    val onConfigExportRequested: () -> Unit,
    val onConfigImportRequested: () -> Unit,
    val onConfigSelected: (String) -> Unit,
    val onConfigDeleteRequested: (String) -> Unit,
    val onAddonSelected: (String) -> Unit
)

sealed interface ComposePopupState

@Immutable
data class ComposeMessagePopupState(
    val title: String,
    val message: String,
    val detail: String? = null,
    val confirmLabel: String
) : ComposePopupState

@Immutable
data class ComposeConfirmPopupState(
    val title: String,
    val message: String,
    val detail: String? = null,
    val confirmLabel: String,
    val dismissLabel: String,
    val destructive: Boolean = false
) : ComposePopupState

@Immutable
data class ComposeEnumPopupState(
    val setting: EnumSetting<*>,
    val settingId: String,
    val title: String,
    val selectedIndex: Int,
    val options: List<String>
) : ComposePopupState

@Immutable
data class ComposeColorPopupState(
    val setting: ColorSetting,
    val settingId: String,
    val title: String,
    val red: Int,
    val green: Int,
    val blue: Int,
    val alpha: Int,
    val allowAlpha: Boolean
) : ComposePopupState

@Immutable
data class ComposePanelUiState(
    val title: String,
    val subtitle: String,
    val sidebarExpanded: Boolean,
    val clientSettingMode: Boolean,
    val categories: List<ComposeCategoryItemState>,
    val modules: List<ComposeModuleItemState>,
    val searchQuery: String,
    val selectedCategoryLabel: String,
    val theme: ComposePanelThemeState,
    val detail: ComposeDetailState,
    val clientSettings: ComposeClientSettingsState? = null,
    val popupState: ComposePopupState? = null,
    val onSidebarToggle: () -> Unit,
    val onCategorySelected: (Category) -> Unit,
    val onClientSettingSelected: () -> Unit,
    val onSearchQueryChanged: (String) -> Unit,
    val onModuleSelected: (Module) -> Unit,
    val onModuleToggle: (Module) -> Unit,
    val onDetailEnabledChanged: (Boolean) -> Unit,
    val onDetailBindModeChanged: (Module.BindMode) -> Unit,
    val onDetailVisibilityChanged: (Boolean) -> Unit,
    val onModuleKeybindCaptureRequested: () -> Unit,
    val onSettingBoolChanged: (BoolSetting, Boolean) -> Unit,
    val onSettingIntChanged: (IntSetting, Int) -> Unit,
    val onSettingDoubleChanged: (DoubleSetting, Double) -> Unit,
    val onSettingStringChanged: (StringSetting, String) -> Unit,
    val onSettingEnumSelected: (EnumSetting<*>, Int) -> Unit,
    val onSettingColorChanged: (ColorSetting, Int, Int, Int, Int) -> Unit,
    val onSettingEnumPopupRequested: (EnumSetting<*>) -> Unit,
    val onSettingColorPopupRequested: (ColorSetting) -> Unit,
    val onSettingKeybindCaptureRequested: (KeybindSetting) -> Unit,
    val onSettingButtonInvoked: (ButtonSetting) -> Unit,
    val onPopupConfirm: () -> Unit,
    val onPopupDismiss: () -> Unit
) {
    companion object {
        val EMPTY = ComposePanelUiState(
            title = "Open Epsilon",
            subtitle = "Compose Panel",
            sidebarExpanded = true,
            clientSettingMode = false,
            categories = emptyList(),
            modules = emptyList(),
            searchQuery = "",
            selectedCategoryLabel = "",
            theme = ComposePanelThemeState(),
            detail = ComposeDetailState(
                title = "No module selected",
                subtitle = "",
                enabled = false,
                bindMode = Module.BindMode.Toggle,
                keybindLabel = "None",
                hidden = false,
                showModuleControls = false,
                listeningModuleKeybind = false,
                settings = emptyList(),
                emptyMessage = ""
            ),
            clientSettings = null,
            popupState = null,
            onSidebarToggle = {},
            onCategorySelected = {},
            onClientSettingSelected = {},
            onSearchQueryChanged = {},
            onModuleSelected = {},
            onModuleToggle = {},
            onDetailEnabledChanged = {},
            onDetailBindModeChanged = {},
            onDetailVisibilityChanged = {},
            onModuleKeybindCaptureRequested = {},
            onSettingBoolChanged = { _, _ -> },
            onSettingIntChanged = { _, _ -> },
            onSettingDoubleChanged = { _, _ -> },
            onSettingStringChanged = { _, _ -> },
            onSettingEnumSelected = { _, _ -> },
            onSettingColorChanged = { _, _, _, _, _ -> },
            onSettingEnumPopupRequested = {},
            onSettingColorPopupRequested = {},
            onSettingKeybindCaptureRequested = {},
            onSettingButtonInvoked = {},
            onPopupConfirm = {},
            onPopupDismiss = {}
        )
    }
}
