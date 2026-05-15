package com.github.epsilon.gui.dropdown;

import com.github.epsilon.graphics.renderers.*;
import net.minecraft.client.Minecraft;

public final class DropdownRenderer {

    private final ShadowRenderer shadow = new ShadowRenderer();
    private final RoundRectRenderer roundRect = new RoundRectRenderer();
    private final RoundRectOutlineRenderer outline = new RoundRectOutlineRenderer();
    private final RectRenderer rect = new RectRenderer();
    private final TextRenderer text = new TextRenderer();
    private final TriangleRenderer triangle = new TriangleRenderer();

    public ShadowRenderer shadow() {
        return shadow;
    }

    public RoundRectRenderer roundRect() {
        return roundRect;
    }

    public RoundRectOutlineRenderer outline() {
        return outline;
    }

    public RectRenderer rect() {
        return rect;
    }

    public TextRenderer text() {
        return text;
    }

    public TriangleRenderer triangle() {
        return triangle;
    }

    public void setScissor(float guiX, float guiY, float guiW, float guiH, int guiHeight) {
        int scale = Minecraft.getInstance().getWindow().getGuiScale();
        int x = Math.round(guiX * scale);
        int y = Math.round((guiHeight - guiY - guiH) * scale);
        int w = Math.round(guiW * scale);
        int h = Math.round(guiH * scale);
        shadow.setScissor(x, y, w, h);
        roundRect.setScissor(x, y, w, h);
        outline.setScissor(x, y, w, h);
        rect.setScissor(x, y, w, h);
        triangle.setScissor(x, y, w, h);
        text.setScissor(x, y, w, h);
    }

    public void clearScissor() {
        shadow.clearScissor();
        roundRect.clearScissor();
        outline.clearScissor();
        rect.clearScissor();
        triangle.clearScissor();
        text.clearScissor();
    }

    public void flush() {
        shadow.drawAndClear();
        roundRect.drawAndClear();
        outline.drawAndClear();
        rect.drawAndClear();
        triangle.drawAndClear();
        text.drawAndClear();
    }

    public void close() {
        shadow.close();
        roundRect.close();
        outline.close();
        rect.close();
        text.close();
        triangle.close();
    }

}
