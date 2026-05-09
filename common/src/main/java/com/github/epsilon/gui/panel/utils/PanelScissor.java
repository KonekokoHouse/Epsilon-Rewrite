package com.github.epsilon.gui.panel.utils;

import com.github.epsilon.graphics.renderers.*;
import com.github.epsilon.gui.panel.PanelLayout;
import net.minecraft.client.Minecraft;

public class PanelScissor {

    private PanelScissor() {
    }

    public static void apply(PanelLayout.Rect rect, RectRenderer rectRenderer, RoundRectRenderer roundRectRenderer, RoundRectOutlineRenderer roundRectOutlineRenderer, ShadowRenderer shadowRenderer, TextRenderer textRenderer, int guiHeight) {
        int scale = Minecraft.getInstance().getWindow().getGuiScale();
        int x = Math.round(rect.x() * scale);
        int y = Math.round((guiHeight - rect.bottom()) * scale);
        int width = Math.round(rect.width() * scale);
        int height = Math.round(rect.height() * scale);
        rectRenderer.setScissor(x, y, width, height);
        roundRectRenderer.setScissor(x, y, width, height);
        roundRectOutlineRenderer.setScissor(x, y, width, height);
        shadowRenderer.setScissor(x, y, width, height);
        textRenderer.setScissor(x, y, width, height);
    }

    public static void clear(RectRenderer rectRenderer, RoundRectRenderer roundRectRenderer, RoundRectOutlineRenderer roundRectOutlineRenderer, ShadowRenderer shadowRenderer, TextRenderer textRenderer) {
        rectRenderer.clearScissor();
        roundRectRenderer.clearScissor();
        roundRectOutlineRenderer.clearScissor();
        shadowRenderer.clearScissor();
        textRenderer.clearScissor();
    }

}
