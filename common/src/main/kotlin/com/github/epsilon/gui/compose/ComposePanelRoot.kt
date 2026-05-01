package com.github.epsilon.gui.compose

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun ComposePanelApp(state: ComposePanelUiState) {
    val theme = state.theme
    val popupAnchors = remember { mutableStateMapOf<String, Rect>() }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }

    val colorScheme = remember(theme) {
        if (theme.isLight) {
            lightColorScheme(
                primary = Color(theme.primaryArgb),
                onPrimary = Color(theme.onPrimaryArgb),
                primaryContainer = Color(theme.primaryContainerArgb),
                secondaryContainer = Color(theme.secondaryContainerArgb),
                surface = Color(theme.surfaceArgb),
                surfaceVariant = Color(theme.surfaceVariantArgb),
                background = Color(theme.backgroundArgb),
                onSurface = Color(theme.onSurfaceArgb),
                onSurfaceVariant = Color(theme.onSurfaceVariantArgb),
                outline = Color(theme.outlineArgb),
                error = Color(theme.errorArgb)
            )
        } else {
            darkColorScheme(
                primary = Color(theme.primaryArgb),
                onPrimary = Color(theme.onPrimaryArgb),
                primaryContainer = Color(theme.primaryContainerArgb),
                secondaryContainer = Color(theme.secondaryContainerArgb),
                surface = Color(theme.surfaceArgb),
                surfaceVariant = Color(theme.surfaceVariantArgb),
                background = Color(theme.backgroundArgb),
                onSurface = Color(theme.onSurfaceArgb),
                onSurfaceVariant = Color(theme.onSurfaceVariantArgb),
                outline = Color(theme.outlineArgb),
                error = Color(theme.errorArgb)
            )
        }
    }

    MaterialTheme(colorScheme = colorScheme) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { rootSize = it },
            color = Color.Transparent
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(ComposePanelDimensions.outerPadding),
                    horizontalArrangement = Arrangement.spacedBy(ComposePanelDimensions.sectionGap)
                ) {
                    RailColumn(state = state)
                    if (state.clientSettingMode && state.clientSettings != null) {
                        ClientSettingsColumn(
                            state = state,
                            clientSettings = state.clientSettings,
                            registerPopupAnchor = { id, rect -> popupAnchors[id] = rect },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    } else {
                        ModuleColumn(
                            state = state,
                            modifier = Modifier
                                .weight(ComposePanelDimensions.moduleColumnWeight)
                                .fillMaxHeight()
                        )
                        DetailColumn(
                            state = state,
                            registerPopupAnchor = { id, rect -> popupAnchors[id] = rect },
                            modifier = Modifier
                                .weight(ComposePanelDimensions.detailColumnWeight)
                                .fillMaxHeight()
                        )
                    }
                }

                LegacyPopupHost(
                    state = state,
                    popupAnchors = popupAnchors,
                    rootSize = rootSize
                )
            }
        }
    }
}

@Composable
private fun RailColumn(state: ComposePanelUiState) {
    val railWidth by animateDpAsState(
        targetValue = if (state.sidebarExpanded) {
            ComposePanelDimensions.railExpandedWidth
        } else {
            ComposePanelDimensions.railCollapsedWidth
        },
        label = "compose-panel-rail-width"
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxHeight()
            .width(railWidth),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f)
        ),
        shape = RoundedCornerShape(ComposePanelDimensions.panelShape)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ComposePanelDimensions.compactSectionPadding)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(ComposePanelDimensions.rowGap)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ComposePanelDimensions.compactGap)
            ) {
                CircleBadge(
                    text = "ε",
                    emphasized = true,
                    modifier = Modifier.size(ComposePanelDimensions.badgeSize + 4.dp)
                )
                if (state.sidebarExpanded) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = state.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                TextButton(onClick = state.onSidebarToggle) {
                    Text(if (state.sidebarExpanded) ComposePanelI18n.panelCollapse.text() else ComposePanelI18n.panelExpand.text())
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ComposePanelDimensions.compactGap)
            ) {
                state.categories.forEach { category ->
                    RailItem(
                        primary = category.title.take(1).uppercase(Locale.getDefault()),
                        title = category.title,
                        trailing = category.count.toString(),
                        expanded = state.sidebarExpanded,
                        selected = category.selected,
                        onClick = { state.onCategorySelected(category.category) }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            RailItem(
                primary = "⚙",
                title = ComposePanelI18n.clientSettings.text(),
                trailing = null,
                expanded = state.sidebarExpanded,
                selected = state.clientSettingMode,
                onClick = state.onClientSettingSelected
            )
        }
    }
}

@Composable
private fun ModuleColumn(state: ComposePanelUiState, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
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
                text = state.selectedCategoryLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = state.onSearchQueryChanged,
                modifier = Modifier
                    .fillMaxWidth(),
                label = { Text(ComposePanelI18n.search.text()) },
                supportingText = {
                    Text(
                        text = if (state.modules.isEmpty()) {
                            ComposePanelI18n.modulesEmpty.text()
                        } else {
                            "${state.modules.size} ${ComposePanelI18n.modules.text()}"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                singleLine = true
            )
            val moduleScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .verticalScroll(moduleScrollState),
                verticalArrangement = Arrangement.spacedBy(ComposePanelDimensions.compactGap)
            ) {
                state.modules.forEach { module ->
                    ModuleRow(
                        state = module,
                        onSelect = { state.onModuleSelected(module.module) },
                        onToggle = { state.onModuleToggle(module.module) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailColumn(
    state: ComposePanelUiState,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.detail.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.detail.subtitle.isNotBlank()) {
                        Text(
                            text = state.detail.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (state.detail.showModuleControls) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (state.detail.enabled) ComposePanelI18n.moduleEnabled.text() else ComposePanelI18n.moduleDisabled.text(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Switch(
                            checked = state.detail.enabled,
                            onCheckedChange = state.onDetailEnabledChanged
                        )
                    }
                }
            }

            if (state.detail.showModuleControls) {
                ModuleControlPanel(state = state)
            }

            if (state.detail.emptyMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(ComposePanelDimensions.sectionShape))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.detail.emptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                SettingsColumn(
                    panelState = state,
                    registerPopupAnchor = registerPopupAnchor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun EmptyPanelHint(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ComposePanelDimensions.rowShape),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModuleControlPanel(state: ComposePanelUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ComposePanelDimensions.sectionShape),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(ComposePanelDimensions.compactGap)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ComposePanelDimensions.compactGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = state.onModuleKeybindCaptureRequested) {
                    Text(
                        if (state.detail.listeningModuleKeybind) {
                            ComposePanelI18n.moduleKeybindListening.text()
                        } else {
                            state.detail.keybindLabel
                        }
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = ComposePanelI18n.moduleHidden.text(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = state.detail.hidden,
                    onCheckedChange = state.onDetailVisibilityChanged
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(ComposePanelDimensions.compactGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ComposePanelI18n.moduleTriggerMode.text(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SegmentedChoice(
                    leftLabel = ComposePanelI18n.bindModeToggle.text(),
                    rightLabel = ComposePanelI18n.bindModeHold.text(),
                    leftSelected = state.detail.bindMode == com.github.epsilon.modules.Module.BindMode.Toggle,
                    onLeftClick = { state.onDetailBindModeChanged(com.github.epsilon.modules.Module.BindMode.Toggle) },
                    onRightClick = { state.onDetailBindModeChanged(com.github.epsilon.modules.Module.BindMode.Hold) }
                )
            }
        }
    }
}

@Composable
private fun RailItem(
    primary: String,
    title: String,
    trailing: String?,
    expanded: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.90f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ComposePanelDimensions.rowShape))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(ComposePanelDimensions.compactGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleBadge(text = primary, emphasized = selected)
        if (expanded) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = contentColor,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (trailing != null) {
                    Text(
                        text = trailing,
                        color = contentColor.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun CircleBadge(
    text: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(ComposePanelDimensions.badgeSize)
            .clip(CircleShape)
            .background(
                if (emphasized) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ModuleRow(
    state: ComposeModuleItemState,
    onSelect: () -> Unit,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ComposePanelDimensions.rowShape))
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(ComposePanelDimensions.rowShape),
        color = if (state.selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = ComposePanelDimensions.rowPaddingHorizontal,
                    vertical = ComposePanelDimensions.rowPaddingVertical
                ),
            horizontalArrangement = Arrangement.spacedBy(ComposePanelDimensions.compactGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f)
            ) {
                Text(
                    text = state.keybindLabel,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Switch(
                checked = state.enabled,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@Composable
private fun SegmentedChoice(
    leftLabel: String,
    rightLabel: String,
    leftSelected: Boolean,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SegmentedChoiceButton(label = leftLabel, selected = leftSelected, onClick = onLeftClick)
        SegmentedChoiceButton(label = rightLabel, selected = !leftSelected, onClick = onRightClick)
    }
}

@Composable
private fun SegmentedChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}



