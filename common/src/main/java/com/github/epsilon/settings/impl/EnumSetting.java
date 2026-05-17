package com.github.epsilon.settings.impl;

import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.settings.Setting;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class EnumSetting<E extends Enum<E>> extends Setting<E> {

    private final Map<E, TranslateComponent> modeTranslations = new HashMap<>();
    private final E[] constants;

    public EnumSetting(String name, E defaultValue, Dependency dependency, Consumer<E> onChanged) {
        super(name, dependency, onChanged);
        this.value = defaultValue;
        this.defaultValue = defaultValue;

        Class<E> enumClass = defaultValue.getDeclaringClass();
        constants = enumClass.getEnumConstants();
    }

    @Override
    public void initTranslateComponent(TranslateComponent component) {
        super.initTranslateComponent(component);
        modeTranslations.clear();
        for (E m : constants) {
            modeTranslations.put(m, component.createChild(m.toString().toLowerCase()));
        }
    }

    public String getTranslatedValue() {
        TranslateComponent comp = modeTranslations.get(value);
        return comp != null ? comp.getTranslatedName() : value.toString();
    }

    @SuppressWarnings("unchecked")
    public String getTranslatedValueByIndex(int index) {
        if (index < 0 || index >= constants.length) return "";
        E enumValue = constants[index];
        TranslateComponent comp = modeTranslations.get(enumValue);
        return comp != null ? comp.getTranslatedName() : enumValue.toString();
    }

    public boolean is(E enumValue) {
        return this.value.equals(enumValue);
    }

    public boolean is(String string) {
        return this.getValue().toString().equalsIgnoreCase(string);
    }

    public void setMode(String mode) {
        for (E e : constants) {
            if (Objects.equals(e.toString(), mode)) {
                setValue(e);
            }
        }
    }

    public void setModeSilently(String mode) {
        for (E e : constants) {
            if (Objects.equals(e.toString(), mode)) {
                setValueSilently(e);
            }
        }
    }

    public void setMode(E mode) {
        setValue(mode);
    }

    public int getModeIndex() {
        int index = 0;
        for (E e : constants) {
            if (e == value) return index;
            index++;
        }
        return -1;
    }

    public E[] getModes() {
        return constants;
    }
}
