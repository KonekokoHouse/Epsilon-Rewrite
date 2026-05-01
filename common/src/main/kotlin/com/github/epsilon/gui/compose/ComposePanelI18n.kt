package com.github.epsilon.gui.compose

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent
import com.github.epsilon.assets.i18n.TranslateComponent

internal object ComposePanelI18n {
    private fun gui(suffix: String): TranslateComponent = EpsilonTranslateComponent.create("gui", suffix)
    private fun keybind(suffix: String): TranslateComponent = EpsilonTranslateComponent.create("keybind", suffix)
    private fun module(suffix: String): TranslateComponent = EpsilonTranslateComponent.create("module", suffix)

    val clientSettings = gui("clientsettings")
    val modules = gui("modules")
    val noModule = gui("no_module")
    val search = gui("search")

    val generalTab = gui("tab.general")
    val friendTab = gui("tab.friend")
    val configTab = gui("tab.config")
    val addonTab = gui("tab.addon")

    val panelExpand = gui("panel.sidebar.expand")
    val panelCollapse = gui("panel.sidebar.collapse")
    val singleplayer = gui("panel.world.singleplayer")
    val noWorld = gui("panel.world.none")
    val modulesEmpty = gui("panel.modules.empty")
    val detailSelectHint = gui("panel.detail.select_hint")
    val clientSettingsSubtitle = gui("panel.clientsettings.subtitle")

    val moduleEnabled = gui("panel.module.enabled")
    val moduleDisabled = gui("panel.module.disabled")
    val moduleHidden = gui("panel.module.hidden_label")
    val moduleTriggerMode = gui("panel.module.trigger_mode")
    val moduleKeybindListening = gui("panel.module.keybind.listening")

    val friendEmpty = gui("friend.empty")
    val friendInputPlaceholder = gui("friend.input.placeholder")
    val friendInputLabel = gui("panel.friend.input.label")
    val friendAdd = gui("panel.friend.action.add")
    val friendRemove = gui("panel.friend.action.remove")

    val configInputLabel = gui("panel.config.input.label")
    val configInputPlaceholder = gui("config.input.placeholder")
    val configCurrent = gui("config.current")
    val configSwitchHint = gui("config.switch_hint")
    val configEmpty = gui("config.empty")
    val configSaveAs = gui("config.action.saveas")
    val configReload = gui("config.action.reload")
    val configExport = gui("config.action.export")
    val configImport = gui("config.action.import")
    val configDelete = gui("panel.config.action.delete")
    val configDeleteConfirmTitle = gui("config.delete.confirm.title")
    val configDeleteConfirmMessage = gui("config.delete.confirm.message")
    val configDeleteConfirmConfirm = gui("config.delete.confirm.confirm")
    val configDeleteConfirmCancel = gui("config.delete.confirm.cancel")
    val configErrorTitle = gui("config.error.title")
    val configSaveError = gui("config.error.save")
    val configReloadError = gui("config.error.reload")
    val configExportError = gui("config.error.export")
    val configImportError = gui("config.error.import")
    val configSwitchError = gui("config.error.switch")
    val configDeleteError = gui("config.error.delete")
    val configDeleteLastError = gui("config.error.delete_last")
    val configSaveSuccessTitle = gui("panel.config.save.success.title")
    val configSaveSuccessMessage = gui("panel.config.save.success.message")
    val configReloadSuccessTitle = gui("panel.config.reload.success.title")
    val configReloadSuccessMessage = gui("panel.config.reload.success.message")
    val configExportSuccessTitle = gui("config.export.success.title")
    val configExportSuccessMessage = gui("config.export.success.message")
    val configImportSuccessTitle = gui("panel.config.import.success.title")
    val configImportSuccessMessage = gui("panel.config.import.success.message")
    val configSwitchSuccessTitle = gui("panel.config.switch.success.title")
    val configSwitchSuccessMessage = gui("panel.config.switch.success.message")
    val configDeleteSuccessTitle = gui("panel.config.delete.success.title")
    val configDeleteSuccessMessage = gui("panel.config.delete.success.message")

    val addonSelectHint = gui("panel.addon.select_hint")
    val addonDescriptionEmpty = gui("panel.addon.description.empty")
    val addonEmpty = gui("addon.empty")
    val addonNoSettings = gui("addon.no_settings")
    val addonInfoId = gui("addon.info.id")
    val addonInfoVersion = gui("addon.info.version")
    val addonInfoAuthors = gui("addon.info.authors")
    val addonInfoModules = gui("addon.info.modules")

    val settingsEmpty = gui("panel.settings.empty")
    val execute = gui("panel.setting.execute")
    val enumPrevious = gui("panel.setting.enum.previous")
    val enumNext = gui("panel.setting.enum.next")
    val colorEdit = gui("panel.setting.color.edit")
    val channelRed = gui("panel.setting.color.channel.red")
    val channelGreen = gui("panel.setting.color.channel.green")
    val channelBlue = gui("panel.setting.color.channel.blue")
    val channelAlpha = gui("panel.setting.color.channel.alpha")

    val popupOk = gui("panel.popup.ok")
    val popupCancel = gui("panel.popup.cancel")

    val bindModeToggle = keybind("toggle")
    val bindModeHold = keybind("hold")
    val keybindNone = keybind("none")
    val moduleVisible = module("visible")
    val moduleHiddenState = module("hidden")
}

internal fun TranslateComponent.text(): String = translatedName




