package com.github.epsilon.gui.panel.adapter;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelLayout;
import com.github.epsilon.gui.panel.component.SettingRow;
import com.github.epsilon.gui.panel.component.setting.ColorSettingRow;
import com.github.epsilon.gui.panel.component.setting.DoubleSettingRow;
import com.github.epsilon.gui.panel.component.setting.EnumSettingRow;
import com.github.epsilon.gui.panel.component.setting.IntSettingRow;
import com.github.epsilon.gui.panel.popup.ColorPickerPopup;
import com.github.epsilon.gui.panel.popup.EnumSelectPopup;
import com.github.epsilon.gui.panel.popup.PanelPopupHost;
import com.github.epsilon.settings.Setting;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SettingListController {

    private final PanelPopupHost popupHost;
    private final TextRenderer measureTextRenderer = new TextRenderer();
    private final Map<Setting<?>, SettingRow<?>> rowCache = new HashMap<>();
    private final List<SettingEntry> settingEntries = new ArrayList<>();

    private SettingEntry draggingSliderEntry;

    public SettingListController(PanelPopupHost popupHost) {
        this.popupHost = popupHost;
    }

    public PanelPopupHost getPopupHost() {
        return popupHost;
    }

    public boolean isPopupHovered(int mouseX, int mouseY) {
        return popupHost.getActivePopup() != null && popupHost.getActivePopup().getBounds().contains(mouseX, mouseY);
    }

    public void prepareLayout(List<Setting<?>> settings) {
        rowCache.keySet().removeIf(setting -> settings == null || !settings.contains(setting));
        settingEntries.clear();
    }

    public void layoutRows(List<Setting<?>> settings, PanelLayout.Rect viewport, float scroll, float rowWidth, RowRenderCallback callback) {
        prepareLayout(settings);
        appendRows(settings, viewport, scroll, rowWidth, callback);
    }

    public void appendRows(List<Setting<?>> settings, PanelLayout.Rect viewport, float scroll, float rowWidth, RowRenderCallback callback) {
        float rowY = viewport.y() - scroll;
        for (Setting<?> setting : settings) {
            SettingRow<?> row = rowCache.computeIfAbsent(setting, SettingViewFactory::create);
            if (row == null) {
                continue;
            }

            PanelLayout.Rect rowBounds = new PanelLayout.Rect(viewport.x(), rowY, rowWidth, row.getHeight());
            settingEntries.add(new SettingEntry(row, rowBounds));
            callback.render(setting, row, rowBounds);
            rowY += row.getHeight() + MD3Theme.ROW_GAP;
        }
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick, PanelLayout.Rect popupBounds) {
        return mouseClicked(event, isDoubleClick, popupBounds, null);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick, PanelLayout.Rect popupBounds, RowClickInterceptor interceptor) {
        if (event.button() != 0) {
            return false;
        }

        clearFocus();
        for (SettingEntry entry : settingEntries) {
            if (interceptor != null && interceptor.handle(entry.row, entry.bounds, event, isDoubleClick)) {
                draggingSliderEntry = null;
                return true;
            }
            if (entry.row instanceof IntSettingRow intRow && intRow.mouseClicked(entry.bounds, event, isDoubleClick)) {
                draggingSliderEntry = intRow.isDragging() ? entry : null;
                return true;
            }
            if (entry.row instanceof DoubleSettingRow doubleRow && doubleRow.mouseClicked(entry.bounds, event, isDoubleClick)) {
                draggingSliderEntry = doubleRow.isDragging() ? entry : null;
                return true;
            }
            if (entry.row instanceof EnumSettingRow enumRow && entry.row.mouseClicked(entry.bounds, event, isDoubleClick)) {
                popupHost.open(createEnumPopup(enumRow, entry.bounds, popupBounds));
                draggingSliderEntry = null;
                return true;
            }
            if (entry.row instanceof ColorSettingRow colorRow && entry.row.mouseClicked(entry.bounds, event, isDoubleClick)) {
                popupHost.open(createColorPopup(colorRow, entry.bounds, popupBounds));
                draggingSliderEntry = null;
                return true;
            }
            if (entry.row.mouseClicked(entry.bounds, event, isDoubleClick)) {
                draggingSliderEntry = null;
                return true;
            }
        }

        draggingSliderEntry = null;
        return false;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingSliderEntry == null) {
            return false;
        }

        draggingSliderEntry.row.mouseReleased(draggingSliderEntry.bounds, event);
        draggingSliderEntry = null;
        return true;
    }

    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        if (draggingSliderEntry == null || event.button() != 0) {
            return false;
        }
        if (draggingSliderEntry.row instanceof IntSettingRow intRow) {
            intRow.updateFromMouse(draggingSliderEntry.bounds, event.x());
            return true;
        }
        if (draggingSliderEntry.row instanceof DoubleSettingRow doubleRow) {
            doubleRow.updateFromMouse(draggingSliderEntry.bounds, event.x());
            return true;
        }
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        for (SettingEntry entry : settingEntries) {
            if (entry.row.keyPressed(event)) {
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        for (SettingEntry entry : settingEntries) {
            if (entry.row.charTyped(event)) {
                return true;
            }
        }
        return false;
    }

    public boolean preeditUpdated(@Nullable PreeditEvent event) {
        for (SettingEntry entry : settingEntries) {
            if (entry.row.preeditUpdated(event)) {
                return true;
            }
        }
        return false;
    }

    public void clearFocus() {
        draggingSliderEntry = null;
        for (SettingRow<?> row : rowCache.values()) {
            row.setFocused(false);
        }
    }

    public void clearAll() {
        clearFocus();
        settingEntries.clear();
        rowCache.clear();
    }

    private EnumSelectPopup createEnumPopup(EnumSettingRow enumRow, PanelLayout.Rect rowBounds, PanelLayout.Rect popupBounds) {
        PanelLayout.Rect chipBounds = enumRow.getChipBounds(measureTextRenderer, rowBounds);
        int optionCount = enumRow.getSetting().getModes().length;
        int visibleCount = Math.min(optionCount, EnumSelectPopup.MAX_VISIBLE_ITEMS);
        float popupHeight = visibleCount * 24.0f + 12.0f;
        float popupWidth = Math.max(108.0f, chipBounds.width() + 24.0f);
        float popupX = Math.max(popupBounds.x() + MD3Theme.PANEL_VIEWPORT_INSET, chipBounds.right() - popupWidth);
        float popupY = chipBounds.bottom() + 4.0f;
        float maxBottom = popupBounds.bottom() - MD3Theme.PANEL_VIEWPORT_INSET;
        if (popupY + popupHeight > maxBottom) {
            popupY = chipBounds.y() - popupHeight - 4.0f;
        }
        return new EnumSelectPopup(new PanelLayout.Rect(popupX, popupY, popupWidth, popupHeight), chipBounds, enumRow.getSetting());
    }

    private ColorPickerPopup createColorPopup(ColorSettingRow colorRow, PanelLayout.Rect rowBounds, PanelLayout.Rect popupBounds) {
        PanelLayout.Rect swatchBounds = colorRow.getSwatchBounds(rowBounds);
        int channelCount = colorRow.getSetting().isAllowAlpha() ? 4 : 3;
        float popupWidth = 156.0f;
        float popupHeight = 58.0f + channelCount * 24.0f;
        float popupX = Math.max(popupBounds.x() + MD3Theme.PANEL_VIEWPORT_INSET, swatchBounds.right() - popupWidth);
        float popupY = swatchBounds.bottom() + 4.0f;
        float maxBottom = popupBounds.bottom() - MD3Theme.PANEL_VIEWPORT_INSET;
        if (popupY + popupHeight > maxBottom) {
            popupY = swatchBounds.y() - popupHeight - 4.0f;
        }
        return new ColorPickerPopup(new PanelLayout.Rect(popupX, popupY, popupWidth, popupHeight), swatchBounds, colorRow.getSetting());
    }

    @FunctionalInterface
    public interface RowRenderCallback {
        void render(Setting<?> setting, SettingRow<?> row, PanelLayout.Rect rowBounds);
    }

    @FunctionalInterface
    public interface RowClickInterceptor {
        boolean handle(SettingRow<?> row, PanelLayout.Rect rowBounds, MouseButtonEvent event, boolean isDoubleClick);
    }

    private record SettingEntry(SettingRow<?> row, PanelLayout.Rect bounds) {
    }

}
