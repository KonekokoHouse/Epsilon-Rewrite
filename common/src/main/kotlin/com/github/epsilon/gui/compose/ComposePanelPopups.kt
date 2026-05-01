package com.github.epsilon.gui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt

@Composable
internal fun LegacyPopupHost(
    state: ComposePanelUiState,
    popupAnchors: Map<String, Rect>,
    rootSize: IntSize
) {
    val popupState = state.popupState ?: return
    when (popupState) {
        is ComposeMessagePopupState -> LegacyModalPopup(
            title = popupState.title,
            message = popupState.message,
            detail = popupState.detail,
            confirmLabel = popupState.confirmLabel,
            dismissLabel = null,
            destructive = false,
            onConfirm = state.onPopupConfirm,
            onDismiss = state.onPopupDismiss
        )

        is ComposeConfirmPopupState -> LegacyModalPopup(
            title = popupState.title,
            message = popupState.message,
            detail = popupState.detail,
            confirmLabel = popupState.confirmLabel,
            dismissLabel = popupState.dismissLabel,
            destructive = popupState.destructive,
            onConfirm = state.onPopupConfirm,
            onDismiss = state.onPopupDismiss
        )

        is ComposeEnumPopupState -> LegacyAnchoredPopup(
            anchorId = popupState.settingId,
            popupAnchors = popupAnchors,
            rootSize = rootSize,
            popupWidth = 210.dp,
            estimatedHeight = (popupState.options.size.coerceAtMost(ComposePanelDimensions.enumMaxVisibleItems) * 30).dp + 18.dp,
            onDismiss = state.onPopupDismiss
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .width(210.dp)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = popupState.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = (ComposePanelDimensions.enumMaxVisibleItems * 30).dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    popupState.options.forEachIndexed { index, option ->
                        val selected = index == popupState.selectedIndex
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    state.onSettingEnumSelected(popupState.setting, index)
                                    state.onPopupDismiss()
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.88f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (selected) {
                                    Text(
                                        text = "✓",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = option,
                                    modifier = Modifier.weight(1f),
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        is ComposeColorPopupState -> LegacyAnchoredPopup(
            anchorId = popupState.settingId,
            popupAnchors = popupAnchors,
            rootSize = rootSize,
            popupWidth = 228.dp,
            estimatedHeight = if (popupState.allowAlpha) 212.dp else 184.dp,
            onDismiss = state.onPopupDismiss
        ) {
            Column(
                modifier = Modifier
                    .width(228.dp)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                Color(popupState.red, popupState.green, popupState.blue, popupState.alpha),
                                RoundedCornerShape(8.dp)
                            )
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = popupState.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatColorHex(
                                popupState.red,
                                popupState.green,
                                popupState.blue,
                                popupState.alpha,
                                popupState.allowAlpha
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                PopupColorChannel(
                    label = ComposePanelI18n.channelRed.text(),
                    value = popupState.red
                ) { red ->
                    state.onSettingColorChanged(popupState.setting, red, popupState.green, popupState.blue, popupState.alpha)
                }
                PopupColorChannel(
                    label = ComposePanelI18n.channelGreen.text(),
                    value = popupState.green
                ) { green ->
                    state.onSettingColorChanged(popupState.setting, popupState.red, green, popupState.blue, popupState.alpha)
                }
                PopupColorChannel(
                    label = ComposePanelI18n.channelBlue.text(),
                    value = popupState.blue
                ) { blue ->
                    state.onSettingColorChanged(popupState.setting, popupState.red, popupState.green, blue, popupState.alpha)
                }
                if (popupState.allowAlpha) {
                    PopupColorChannel(
                        label = ComposePanelI18n.channelAlpha.text(),
                        value = popupState.alpha
                    ) { alpha ->
                        state.onSettingColorChanged(popupState.setting, popupState.red, popupState.green, popupState.blue, alpha)
                    }
                }
            }
        }
    }
}

@Composable
private fun LegacyModalPopup(
    title: String,
    message: String,
    detail: String?,
    confirmLabel: String,
    dismissLabel: String?,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dismissSource = remember { MutableInteractionSource() }
    val consumeSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .clickable(interactionSource = dismissSource, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .width(260.dp)
                .clickable(interactionSource = consumeSource, indication = null, onClick = {}),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!detail.isNullOrBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (dismissLabel != null) {
                        OutlinedButton(onClick = onDismiss) {
                            Text(dismissLabel)
                        }
                    }
                    if (dismissLabel != null) {
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(6.dp))
                    }
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun LegacyAnchoredPopup(
    anchorId: String,
    popupAnchors: Map<String, Rect>,
    rootSize: IntSize,
    popupWidth: Dp,
    estimatedHeight: Dp,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val dismissSource = remember { MutableInteractionSource() }
    val consumeSource = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val popupWidthPx = with(density) { popupWidth.roundToPx() }
    val popupHeightPx = with(density) { estimatedHeight.roundToPx() }
    val anchor = popupAnchors[anchorId]
    val placement = calculatePopupPlacement(anchor, rootSize, popupWidthPx, popupHeightPx)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = dismissSource, indication = null, onClick = onDismiss)
    ) {
        Surface(
            modifier = Modifier
                .offset { IntOffset(placement.first, placement.second) }
                .clickable(interactionSource = consumeSource, indication = null, onClick = {}),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp
        ) {
            content()
        }
    }
}

@Composable
private fun PopupColorChannel(label: String, value: Int, onChanged: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value / 255f,
            onValueChange = { onChanged((it * 255f).roundToInt().coerceIn(0, 255)) }
        )
    }
}

private fun calculatePopupPlacement(
    anchor: Rect?,
    rootSize: IntSize,
    popupWidthPx: Int,
    popupHeightPx: Int
): Pair<Int, Int> {
    if (rootSize == IntSize.Zero) {
        return 0 to 0
    }

    if (anchor == null) {
        return ((rootSize.width - popupWidthPx) / 2).coerceAtLeast(0) to ((rootSize.height - popupHeightPx) / 2).coerceAtLeast(0)
    }

    val horizontalMargin = 8
    val verticalMargin = 8
    val preferredX = (anchor.right - popupWidthPx).roundToInt()
    val clampedX = preferredX.coerceIn(horizontalMargin, (rootSize.width - popupWidthPx - horizontalMargin).coerceAtLeast(horizontalMargin))

    val belowY = (anchor.bottom + 6f).roundToInt()
    val aboveY = (anchor.top - popupHeightPx - 6f).roundToInt()
    val maxY = (rootSize.height - popupHeightPx - verticalMargin).coerceAtLeast(verticalMargin)
    val finalY = when {
        belowY <= maxY -> belowY
        aboveY >= verticalMargin -> aboveY
        else -> maxY
    }

    return clampedX to finalY
}

