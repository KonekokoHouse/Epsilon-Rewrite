package com.github.epsilon.modules.impl.render;

import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;

public class TestGui extends Module {

    public static final TestGui INSTANCE = new TestGui();

    private TestGui() {
        super("Test Gui", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        mc.setScreen(DropdownScreen.INSTANCE);
    }

}
