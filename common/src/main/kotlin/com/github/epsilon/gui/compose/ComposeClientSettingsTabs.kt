package com.github.epsilon.gui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.epsilon.gui.panel.PanelState
import java.util.Locale

@Composable
internal fun ClientSettingsColumn(
    state: ComposePanelUiState,
    clientSettings: ComposeClientSettingsState,
    registerPopupAnchor: (String, Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ),
        shape = RoundedCornerShape(ComposePanelDimensions.panelShape)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ComposePanelDimensions.sectionPadding),
            verticalArrangement = Arrangement.spacedBy(ComposePanelDimensions.rowGap)
        ) {
            Text(
                text = clientSettings.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (clientSettings.subtitle.isNotBlank()) {
                Text(
                    text = clientSettings.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ClientSettingsTabRow(clientSettings)
            when (clientSettings.selectedTab) {
                PanelState.ClientSettingTab.GENERAL -> ClientSettingsGeneralTab(state, clientSettings, registerPopupAnchor)
                PanelState.ClientSettingTab.FRIEND -> ClientSettingsFriendTab(clientSettings)
                PanelState.ClientSettingTab.CONFIG -> ClientSettingsConfigTab(clientSettings)
                PanelState.ClientSettingTab.ADDON -> ClientSettingsAddonTab(state, clientSettings, registerPopupAnchor)
            }
        }
    }
}

@Composable
private fun ClientSettingsTabRow(clientSettings: ComposeClientSettingsState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        clientSettings.tabs.forEach { tab ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (tab.selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                    )
                    .clickable { clientSettings.onTabSelected(tab.tab) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.title,
                    color = if (tab.selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ClientSettingsGeneralTab(
    state: ComposePanelUiState,
    clientSettings: ComposeClientSettingsState,
    registerPopupAnchor: (String, Rect) -> Unit
) {
    GenericSettingsColumn(
        settings = clientSettings.generalSettings,
        panelState = state,
        registerPopupAnchor = registerPopupAnchor,
        modifier = Modifier.fillMaxSize(),
        emptyMessage = ComposePanelI18n.settingsEmpty.text()
    )
}

@Composable
private fun ClientSettingsFriendTab(clientSettings: ComposeClientSettingsState) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(ComposePanelDimensions.rowGap)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ComposePanelDimensions.rowGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = clientSettings.friendInput,
                onValueChange = clientSettings.onFriendInputChanged,
                modifier = Modifier.weight(1f),
                label = { Text(ComposePanelI18n.friendInputLabel.text()) },
                placeholder = { Text(ComposePanelI18n.friendInputPlaceholder.text()) },
                singleLine = true
            )
            Button(onClick = clientSettings.onFriendAddRequested) {
                Text(ComposePanelI18n.friendAdd.text())
            }
        }
        val friendScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(friendScrollState),
            verticalArrangement = Arrangement.spacedBy(ComposePanelDimensions.compactGap)
        ) {
            if (clientSettings.friends.isEmpty()) {
                EmptyPanelHint(ComposePanelI18n.friendEmpty.text())
            } else {
                clientSettings.friends.forEach { friend ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(ComposePanelDimensions.rowShape),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(ComposePanelDimensions.compactGap),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.32f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = friend.name.take(1).uppercase(Locale.getDefault()),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = friend.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(onClick = { clientSettings.onFriendRemoved(friend.name) }) {
                                Text(ComposePanelI18n.friendRemove.text(), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientSettingsConfigTab(clientSettings: ComposeClientSettingsState) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(ComposePanelDimensions.rowGap)
    ) {
        OutlinedTextField(
            value = clientSettings.configInput,
            onValueChange = clientSettings.onConfigInputChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(ComposePanelI18n.configInputLabel.text()) },
            placeholder = { Text(ComposePanelI18n.configInputPlaceholder.text()) },
            supportingText = {
                Text(
                    text = "${ComposePanelI18n.configCurrent.text()}: ${clientSettings.activeConfigName}",
                    style = MaterialTheme.typography.labelSmall
                )
            },
            singleLine = true
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ComposePanelDimensions.compactGap)
        ) {
            Button(onClick = clientSettings.onConfigSaveAsRequested, modifier = Modifier.weight(1f)) {
                Text(ComposePanelI18n.configSaveAs.text())
            }
            OutlinedButton(onClick = clientSettings.onConfigReloadRequested, modifier = Modifier.weight(1f)) {
                Text(ComposePanelI18n.configReload.text())
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ComposePanelDimensions.compactGap)
        ) {
            OutlinedButton(onClick = clientSettings.onConfigExportRequested, modifier = Modifier.weight(1f)) {
                Text(ComposePanelI18n.configExport.text())
            }
            OutlinedButton(onClick = clientSettings.onConfigImportRequested, modifier = Modifier.weight(1f)) {
                Text(ComposePanelI18n.configImport.text())
            }
        }
        val configScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(configScrollState),
            verticalArrangement = Arrangement.spacedBy(ComposePanelDimensions.compactGap)
        ) {
            if (clientSettings.configs.isEmpty()) {
                EmptyPanelHint(ComposePanelI18n.configEmpty.text())
            } else {
                clientSettings.configs.forEach { config ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { clientSettings.onConfigSelected(config.name) },
                        shape = RoundedCornerShape(ComposePanelDimensions.rowShape),
                        color = if (config.active) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(ComposePanelDimensions.compactGap),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = config.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (config.active) ComposePanelI18n.configCurrent.text() else ComposePanelI18n.configSwitchHint.text(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (config.active) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = ComposePanelI18n.configCurrent.text(),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            TextButton(onClick = { clientSettings.onConfigDeleteRequested(config.name) }) {
                                Text(ComposePanelI18n.configDelete.text(), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientSettingsAddonTab(
    state: ComposePanelUiState,
    clientSettings: ComposeClientSettingsState,
    registerPopupAnchor: (String, Rect) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(ComposePanelDimensions.rowGap)
    ) {
        val addonListScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(0.30f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(ComposePanelDimensions.sectionShape))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f))
                .padding(8.dp)
                .verticalScroll(addonListScrollState),
            verticalArrangement = Arrangement.spacedBy(ComposePanelDimensions.compactGap)
        ) {
            if (clientSettings.addons.isEmpty()) {
                EmptyPanelHint(ComposePanelI18n.addonEmpty.text())
            } else {
                clientSettings.addons.forEach { addon ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { clientSettings.onAddonSelected(addon.addonId) },
                        shape = RoundedCornerShape(ComposePanelDimensions.rowShape),
                        color = if (addon.selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.54f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.34f)
                        }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
                            Text(
                                text = addon.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = addon.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${addon.moduleCount} ${ComposePanelI18n.modules.text()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(0.70f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(ComposePanelDimensions.rowGap)
        ) {
            val selectedAddon = clientSettings.selectedAddon
            if (selectedAddon == null) {
                EmptyPanelHint(ComposePanelI18n.addonSelectHint.text())
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(ComposePanelDimensions.sectionShape),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = selectedAddon.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${ComposePanelI18n.addonInfoId.text()}: ${selectedAddon.addonId} · ${ComposePanelI18n.addonInfoVersion.text()}: ${selectedAddon.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${ComposePanelI18n.addonInfoAuthors.text()}: ${selectedAddon.authors}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = selectedAddon.description.ifBlank { ComposePanelI18n.addonDescriptionEmpty.text() },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${ComposePanelI18n.addonInfoModules.text()}: ${selectedAddon.moduleCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                GenericSettingsColumn(
                    settings = selectedAddon.settings,
                    panelState = state,
                    registerPopupAnchor = registerPopupAnchor,
                    modifier = Modifier.weight(1f),
                    emptyMessage = ComposePanelI18n.addonNoSettings.text()
                )
            }
        }
    }
}



