package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.gui.dropdown.DropdownRenderer;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.managers.ConfigManager;
import com.github.epsilon.managers.FriendManager;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class FriendDropdownPanel extends AbstractDropdownPanel {

    private static final TranslateComponent titleComponent = EpsilonTranslateComponent.create("gui", "tab.friend");
    private static final TranslateComponent noFriendsComponent = EpsilonTranslateComponent.create("gui", "friend.empty");
    private static final TranslateComponent placeholderComponent = EpsilonTranslateComponent.create("gui", "friend.input.placeholder");

    private static final float ROW_HEIGHT = 20.0f;
    private static final float FIELD_HEIGHT = 18.0f;
    private static final float GAP = 4.0f;
    private static final float PADDING = 6.0f;

    private final StringBuilder input = new StringBuilder();
    private boolean focused;

    public FriendDropdownPanel(int panelIndex) {
        super("friend", titleComponent, "", panelIndex);
    }

    @Override
    protected float computeContentHeight() {
        int friendCount = FriendManager.INSTANCE.getFriends().size();
        return PADDING * 2.0f + FIELD_HEIGHT + GAP + Math.max(ROW_HEIGHT, friendCount * (ROW_HEIGHT + GAP));
    }

    @Override
    protected void drawPanelContent(DropdownRenderer renderer, int mouseX, int mouseY, float visibleHeight) {
        float fieldX = x + PADDING;
        float fieldY = y + DropdownTheme.PANEL_HEADER_HEIGHT + PADDING - scroll;
        float fieldW = width - PADDING * 2.0f - 24.0f;
        renderer.roundRect().addRoundRect(fieldX, fieldY, fieldW, FIELD_HEIGHT, DropdownTheme.INPUT_RADIUS, DropdownTheme.inputSurface(focused));
        String text = input.isEmpty() && !focused ? placeholderComponent.getTranslatedName() : input.toString();
        if (focused && System.currentTimeMillis() % 1000 > 500) text += "|";
        renderer.text().addText(trimToWidth(text, DropdownTheme.SETTING_TEXT_SCALE, fieldW - 8.0f, renderer),
                fieldX + 4.0f, fieldY + (FIELD_HEIGHT - renderer.text().getHeight(DropdownTheme.SETTING_TEXT_SCALE)) * 0.5f,
                DropdownTheme.SETTING_TEXT_SCALE, input.isEmpty() && !focused ? MD3Theme.TEXT_MUTED : MD3Theme.TEXT_PRIMARY);

        float addX = fieldX + fieldW + GAP;
        renderer.roundRect().addRoundRect(addX, fieldY, 20.0f, FIELD_HEIGHT, DropdownTheme.BUTTON_RADIUS,
                isHovered(mouseX, mouseY, addX, fieldY, 20.0f, FIELD_HEIGHT) ? MD3Theme.PRIMARY : MD3Theme.PRIMARY_CONTAINER);
        renderer.text().addText("+", addX + 7.0f, fieldY + 2.0f, 0.62f, MD3Theme.ON_PRIMARY_CONTAINER);

        List<String> friends = FriendManager.INSTANCE.getFriends().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        float rowY = fieldY + FIELD_HEIGHT + GAP;
        if (friends.isEmpty()) {
            renderer.text().addText(noFriendsComponent.getTranslatedName(), x + PADDING, rowY + 4.0f, 0.55f, MD3Theme.TEXT_MUTED);
            return;
        }
        for (String name : friends) {
            boolean hovered = isHovered(mouseX, mouseY, x + PADDING, rowY, width - PADDING * 2.0f, ROW_HEIGHT);
            renderer.roundRect().addRoundRect(x + PADDING, rowY, width - PADDING * 2.0f, ROW_HEIGHT, DropdownTheme.BUTTON_RADIUS,
                    hovered ? MD3Theme.SURFACE_CONTAINER_HIGH : MD3Theme.SURFACE_CONTAINER_LOW);
            renderer.text().addText(trimToWidth(name, DropdownTheme.SETTING_TEXT_SCALE, width - 38.0f, renderer),
                    x + PADDING + 6.0f, rowY + (ROW_HEIGHT - renderer.text().getHeight(DropdownTheme.SETTING_TEXT_SCALE)) * 0.5f,
                    DropdownTheme.SETTING_TEXT_SCALE, MD3Theme.TEXT_PRIMARY);
            float removeX = x + width - PADDING - 18.0f;
            renderer.text().addText("x", removeX + 5.0f, rowY + 3.0f, 0.54f,
                    isHovered(mouseX, mouseY, removeX, rowY + 1.0f, 16.0f, 16.0f) ? MD3Theme.ERROR : MD3Theme.TEXT_MUTED);
            rowY += ROW_HEIGHT + GAP;
        }
    }

    @Override
    protected boolean mouseClickedContent(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        float fieldX = x + PADDING;
        float fieldY = y + DropdownTheme.PANEL_HEADER_HEIGHT + PADDING - scroll;
        float fieldW = width - PADDING * 2.0f - 24.0f;
        if (isHovered(mouseX, mouseY, fieldX, fieldY, fieldW, FIELD_HEIGHT)) {
            focused = true;
            return true;
        }
        if (isHovered(mouseX, mouseY, fieldX + fieldW + GAP, fieldY, 20.0f, FIELD_HEIGHT)) {
            addFriend();
            focused = true;
            return true;
        }
        focused = false;

        float rowY = fieldY + FIELD_HEIGHT + GAP;
        for (String name : FriendManager.INSTANCE.getFriends().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()) {
            float removeX = x + width - PADDING - 18.0f;
            if (isHovered(mouseX, mouseY, removeX, rowY + 1.0f, 16.0f, 16.0f)) {
                FriendManager.INSTANCE.removeFriend(name);
                ConfigManager.INSTANCE.saveNow();
                return true;
            }
            rowY += ROW_HEIGHT + GAP;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused) return false;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            addFriend();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !input.isEmpty()) {
            input.deleteCharAt(input.length() - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            focused = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(String typedText) {
        if (!focused || typedText.isEmpty() || input.length() >= 32) return false;
        input.append(typedText);
        return true;
    }

    @Override
    public boolean hasActiveInput() {
        return focused;
    }

    private void addFriend() {
        String name = input.toString().trim();
        if (!name.isEmpty() && !FriendManager.INSTANCE.isFriend(name)) {
            FriendManager.INSTANCE.addFriend(name);
            ConfigManager.INSTANCE.saveNow();
        }
        input.setLength(0);
    }

}
