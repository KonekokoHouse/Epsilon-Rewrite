package com.github.epsilon.settings;

import com.github.epsilon.assets.i18n.TranslateComponent;

public class SettingGroup {

    private final String name;
    private TranslateComponent translateComponent;
    private boolean collapsed;

    public SettingGroup(String name) {
        this.name = name;
    }

    public void initTranslateComponent(TranslateComponent component) {
        this.translateComponent = component;
    }

    public TranslateComponent getTranslateComponent() {
        return translateComponent;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return translateComponent != null ? translateComponent.getTranslatedName() : name;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    public void toggleCollapsed() {
        collapsed = !collapsed;
    }

}
