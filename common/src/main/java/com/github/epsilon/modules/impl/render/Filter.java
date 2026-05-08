package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.AfterRender3DEvent;
import com.github.epsilon.graphics.shaders.FilterShader;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.ColorSetting;

import java.awt.*;

public class Filter extends Module {

    public static final Filter INSTANCE = new Filter();

    private final ColorSetting baseColor = colorSetting("Base Color", new Color(70, 70, 150, 50), true);

    private Filter() {
        super("Filter", Category.RENDER);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onAfterRender3D(AfterRender3DEvent event) {
        if (nullCheck()) {
            return;
        }

        Color color = this.baseColor.getValue();
        if (color.getAlpha() <= 0) {
            return;
        }

        FilterShader.INSTANCE.renderMainTarget(color);
    }

}
