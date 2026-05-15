package com.github.epsilon.gui.dropdown;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DropdownScreen extends Screen {

    public static final DropdownScreen INSTANCE = new DropdownScreen();

    private DropdownScreen() {
        super(Component.literal("DropdownGui"));
    }

}
