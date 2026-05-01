package com.github.epsilon.gui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun SettingsColumn(
    panelState: ComposePanelUiState,
    registerPopupAnchor: (String, Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    GenericSettingsColumn(
        settings = panelState.detail.settings,
        panelState = panelState,
        registerPopupAnchor = registerPopupAnchor,
        modifier = modifier,
        emptyMessage = panelState.detail.emptyMessage ?: ComposePanelI18n.settingsEmpty.text()
    )
}

@Composable
internal fun GenericSettingsColumn(
    settings: List<ComposeSettingItemState>,
    panelState: ComposePanelUiState,
    registerPopupAnchor: (String, Rect) -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String
) {
    val settingsScrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(settingsScrollState),
        verticalArrangement = Arrangement.spacedBy(ComposePanelDimensions.compactGap)
    ) {
        if (settings.isEmpty()) {
            EmptyPanelHint(emptyMessage)
        } else {
            settings.forEach { setting ->
                when (setting) {
                    is ComposeBoolSettingItemState -> BooleanSettingCard(setting) {
                        panelState.onSettingBoolChanged(setting.setting, it)
                    }

                    is ComposeIntSettingItemState -> IntSettingCard(setting) {
                        panelState.onSettingIntChanged(setting.setting, it)
                    }

                    is ComposeDoubleSettingItemState -> DoubleSettingCard(setting) {
                        panelState.onSettingDoubleChanged(setting.setting, it)
                    }

                    is ComposeStringSettingItemState -> StringSettingCard(setting) {
                        panelState.onSettingStringChanged(setting.setting, it)
                    }

                    is ComposeEnumSettingItemState -> EnumSettingCard(
                        state = setting,
                        registerPopupAnchor = registerPopupAnchor,
                        onOpenPopup = { panelState.onSettingEnumPopupRequested(setting.setting) }
                    )

                    is ComposeColorSettingItemState -> ColorSettingCard(
                        state = setting,
                        registerPopupAnchor = registerPopupAnchor,
                        onOpenPopup = { panelState.onSettingColorPopupRequested(setting.setting) }
                    )

                    is ComposeKeybindSettingItemState -> KeybindSettingCard(setting) {
                        panelState.onSettingKeybindCaptureRequested(setting.setting)
                    }

                    is ComposeButtonSettingItemState -> ButtonSettingCard(setting) {
                        panelState.onSettingButtonInvoked(setting.setting)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingContainer(
    title: String,
    summary: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ComposePanelDimensions.rowShape),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}

@Composable
private fun BooleanSettingCard(state: ComposeBoolSettingItemState, onChanged: (Boolean) -> Unit) {
    SettingContainer(title = state.title, summary = state.summary) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (state.value) ComposePanelI18n.moduleEnabled.text() else ComposePanelI18n.moduleDisabled.text(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Switch(checked = state.value, onCheckedChange = onChanged)
        }
    }
}

@Composable
private fun IntSettingCard(state: ComposeIntSettingItemState, onChanged: (Int) -> Unit) {
    val range = (state.max - state.min).coerceAtLeast(1)
    val sliderValue = (state.value - state.min).toFloat() / range.toFloat()
    SettingContainer(title = state.title, summary = state.summary) {
        Text(
            text = formatIntValue(state.value, state.percentageMode),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Slider(
            value = sliderValue,
            onValueChange = { normalized ->
                val raw = state.min + normalized * range
                onChanged(roundToIntStep(raw.roundToInt(), state.min, state.max, state.step))
            }
        )
    }
}

@Composable
private fun DoubleSettingCard(state: ComposeDoubleSettingItemState, onChanged: (Double) -> Unit) {
    val range = (state.max - state.min).takeIf { it > 0.0 } ?: 1.0
    val sliderValue = ((state.value - state.min) / range).toFloat().coerceIn(0f, 1f)
    SettingContainer(title = state.title, summary = state.summary) {
        Text(
            text = formatDoubleValue(state.value, state.percentageMode),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Slider(
            value = sliderValue,
            onValueChange = { normalized ->
                val raw = state.min + normalized * range
                onChanged(roundToDoubleStep(raw, state.min, state.max, state.step))
            }
        )
    }
}

@Composable
private fun StringSettingCard(state: ComposeStringSettingItemState, onChanged: (String) -> Unit) {
    SettingContainer(title = state.title, summary = state.summary) {
        OutlinedTextField(
            value = state.value,
            onValueChange = onChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            maxLines = 4
        )
    }
}

@Composable
private fun EnumSettingCard(
    state: ComposeEnumSettingItemState,
    registerPopupAnchor: (String, Rect) -> Unit,
    onOpenPopup: () -> Unit
) {
    SettingContainer(title = state.title, summary = state.summary) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { registerPopupAnchor(state.id, it.boundsInRoot()) }
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenPopup),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.34f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.selectedLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "▾",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSettingCard(
    state: ComposeColorSettingItemState,
    registerPopupAnchor: (String, Rect) -> Unit,
    onOpenPopup: () -> Unit
) {
    SettingContainer(title = state.title, summary = state.summary) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { registerPopupAnchor(state.id, it.boundsInRoot()) }
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenPopup),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.34f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(state.red, state.green, state.blue, state.alpha))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatColorHex(state.red, state.green, state.blue, state.alpha, state.allowAlpha),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "RGBA(${state.red}, ${state.green}, ${state.blue}, ${state.alpha})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onOpenPopup) {
                    Text(ComposePanelI18n.colorEdit.text())
                }
            }
        }
    }
}

@Composable
private fun KeybindSettingCard(state: ComposeKeybindSettingItemState, onCaptureRequest: () -> Unit) {
    SettingContainer(title = state.title, summary = state.summary) {
        OutlinedButton(onClick = onCaptureRequest) {
            Text(if (state.listening) ComposePanelI18n.moduleKeybindListening.text() else state.keyLabel)
        }
    }
}

@Composable
private fun ButtonSettingCard(state: ComposeButtonSettingItemState, onInvoke: () -> Unit) {
    SettingContainer(title = state.title, summary = state.summary) {
        Button(
            onClick = onInvoke,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(ComposePanelI18n.execute.text())
        }
    }
}

internal fun roundToIntStep(value: Int, min: Int, max: Int, step: Int): Int {
    val safeStep = step.coerceAtLeast(1)
    val normalized = ((value - min).toFloat() / safeStep.toFloat()).roundToInt() * safeStep + min
    return normalized.coerceIn(min, max)
}

internal fun roundToDoubleStep(value: Double, min: Double, max: Double, step: Double): Double {
    val safeStep = if (step <= 0.0) 1.0 else step
    val normalized = (((value - min) / safeStep).roundToInt() * safeStep + min).coerceIn(min, max)
    return String.format(Locale.US, "%.4f", normalized).toDouble()
}

internal fun formatIntValue(value: Int, percentageMode: Boolean): String {
    return if (percentageMode) "$value%" else value.toString()
}

internal fun formatDoubleValue(value: Double, percentageMode: Boolean): String {
    val base = if (value % 1.0 == 0.0) value.roundToInt().toString() else String.format(Locale.US, "%.2f", value)
    return if (percentageMode) "$base%" else base
}

internal fun formatColorHex(red: Int, green: Int, blue: Int, alpha: Int, allowAlpha: Boolean): String {
    return if (allowAlpha) {
        String.format(Locale.US, "#%02X%02X%02X%02X", red, green, blue, alpha)
    } else {
        String.format(Locale.US, "#%02X%02X%02X", red, green, blue)
    }
}


