package com.github.epsilon.gui.panel.dsl;

import com.github.epsilon.graphics.renderers.*;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelLayout;
import com.github.epsilon.gui.panel.component.PanelElements;
import com.github.epsilon.gui.panel.utils.PanelContentBuffer;

import java.awt.*;
import java.util.List;

/**
 * 将 {@link PanelUiTree} 编译为具体 renderer 调用的编译器。
 * <p>
 * 该类只负责把声明式节点翻译进对应批次，不直接负责真正的 draw/flush 时机。
 */
public class PanelUiCompiler {

    private PanelUiCompiler() {
    }

    /**
     * 将 UI 树编译进不含阴影的目标 renderer 组合。
     *
     * @param tree              待编译的 UI 树
     * @param roundRectRenderer 圆角矩形 renderer
     * @param rectRenderer      矩形 renderer
     * @param textRenderer      文本 renderer
     */
    public static void render(PanelUiTree tree, RoundRectRenderer roundRectRenderer, RectRenderer rectRenderer, TextRenderer textRenderer) {
        render(tree, null, roundRectRenderer, null, rectRenderer, textRenderer);
    }

    /**
     * 将 UI 树编译进完整的 renderer 组合。
     * <p>
     * 若树中包含 viewport 节点，其子树会被继续编译到对应的 {@link PanelContentBuffer} 中，
     * 并在后续 flush 阶段按裁剪区域输出。
     *
     * @param tree              待编译的 UI 树
     * @param shadowRenderer    阴影 renderer，可为空
     * @param roundRectRenderer 圆角矩形 renderer
     * @param rectRenderer      矩形 renderer
     * @param textRenderer      文本 renderer
     */
    public static void render(PanelUiTree tree, ShadowRenderer shadowRenderer, RoundRectRenderer roundRectRenderer, RectRenderer rectRenderer, TextRenderer textRenderer) {
        render(tree, shadowRenderer, roundRectRenderer, null, rectRenderer, textRenderer);
    }

    public static void render(PanelUiTree tree, ShadowRenderer shadowRenderer, RoundRectRenderer roundRectRenderer,
                              RoundRectOutlineRenderer roundRectOutlineRenderer, RectRenderer rectRenderer, TextRenderer textRenderer) {
        renderNodes(tree.nodes(), new RenderTarget(shadowRenderer, roundRectRenderer, roundRectOutlineRenderer, rectRenderer, textRenderer));
    }

    private static void renderNodes(List<PanelUiTree.UiNode> nodes, RenderTarget target) {
        for (PanelUiTree.UiNode node : nodes) {
            if (node instanceof PanelUiTree.GroupNode(List<PanelUiTree.UiNode> children1)) {
                renderNodes(children1, target);
                continue;
            }
            if (node instanceof PanelUiTree.ShadowNode(
                    float x2, float y2, float width1, float height1, float topLeft, float topRight, float bottomRight,
                    float bottomLeft, float blurRadius, java.awt.Color color2
            )) {
                if (target.shadowRenderer() != null) {
                    target.shadowRenderer().addShadow(
                            x2, y2, width1, height1,
                            topLeft, topRight, bottomRight, bottomLeft,
                            blurRadius, color2
                    );
                }
                continue;
            }
            if (node instanceof PanelUiTree.RoundRectNode(
                    float x1, float y1, float width, float height, float radiusTopLeft, float radiusTopRight,
                    float radiusBottomRight, float radiusBottomLeft, java.awt.Color color1
            )) {
                target.roundRectRenderer().addRoundRect(
                        x1, y1, width, height,
                        radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                        color1
                );
                continue;
            }
            if (node instanceof PanelUiTree.RectNode(
                    float x1, float y1, float width, float height, java.awt.Color color1
            )) {
                target.rectRenderer().addRect(x1, y1, width, height, color1);
                continue;
            }
            if (node instanceof PanelUiTree.TextNode(
                    String text, float x, float y, float scale, java.awt.Color color,
                    TtfFontLoader fontLoader
            )) {
                if (fontLoader != null) {
                    target.textRenderer().addText(text, x, y, scale, color, fontLoader);
                } else {
                    target.textRenderer().addText(text, x, y, scale, color);
                }
                continue;
            }
            if (node instanceof PanelUiTree.MarqueeTextNode(
                    String text, float x, float y, float scale, java.awt.Color color,
                    TtfFontLoader fontLoader, PanelLayout.Rect clip
            )) {
                if (target.buffer() != null) {
                    target.buffer().addMarqueeText(new PanelContentBuffer.MarqueeTextDraw(text, x, y, scale, color, fontLoader, clip));
                } else if (fontLoader != null) {
                    target.textRenderer().addText(text, x, y, scale, color, fontLoader);
                } else {
                    target.textRenderer().addText(text, x, y, scale, color);
                }
                continue;
            }
            if (node instanceof PanelUiTree.ButtonNode(
                    float x, float y, float width, float height, float radius, Color background,
                    String label, float labelScale, Color labelColor
            )) {
                renderButton(target, x, y, width, height, radius, background, label, labelScale, labelColor);
                continue;
            }
            if (node instanceof PanelUiTree.SwitchNode(
                    PanelLayout.Rect bounds, float toggleProgress, float hoverProgress
            )) {
                renderSwitch(target, bounds, toggleProgress, hoverProgress);
                continue;
            }
            if (node instanceof PanelUiTree.FilledFieldNode(
                    PanelLayout.Rect bounds, boolean focused, float hoverProgress
            )) {
                renderFilledField(target, bounds, focused, hoverProgress);
                continue;
            }
            if (node instanceof PanelUiTree.InputNode(PanelUiTree.InputElement element)) {
                renderInput(target, element);
                continue;
            }
            if (node instanceof PanelUiTree.AssistChipNode(
                    PanelLayout.Rect bounds, String label, float textScale, Color background, Color foreground,
                    String trailingIcon, float trailingIconScale, TtfFontLoader trailingIconFont
            )) {
                target.roundRectRenderer().addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), MD3Theme.CONTROL_RADIUS, background);
                float textY = bounds.y() + (bounds.height() - target.textRenderer().getHeight(textScale)) / 2.0f - 1.0f;
                target.textRenderer().addText(label, bounds.x() + 8.0f, textY, textScale, foreground);
                if (trailingIcon != null && !trailingIcon.isEmpty() && trailingIconFont != null) {
                    float iconWidth = target.textRenderer().getWidth(trailingIcon, trailingIconScale, trailingIconFont);
                    float iconY = bounds.y() + (bounds.height() - target.textRenderer().getHeight(trailingIconScale, trailingIconFont)) / 2.0f - 1.0f;
                    target.textRenderer().addText(trailingIcon, bounds.right() - 8.0f - iconWidth, iconY, trailingIconScale, foreground, trailingIconFont);
                }
                continue;
            }
            if (node instanceof PanelUiTree.SegmentedControlNode(
                    PanelLayout.Rect bounds, String leadingLabel, String trailingLabel, float progress,
                    float hoverProgress
            )) {
                float outerRadius = MD3Theme.CONTROL_RADIUS;
                float shellInset = 1.0f;
                float innerX = bounds.x() + shellInset;
                float innerY = bounds.y() + shellInset;
                float innerWidth = bounds.width() - shellInset * 2.0f;
                float innerHeight = bounds.height() - shellInset * 2.0f;
                float segmentWidth = innerWidth / 2.0f;
                float indicatorInset = 1.5f;
                float indicatorWidth = segmentWidth - indicatorInset * 2.0f;
                float indicatorX = innerX + indicatorInset + segmentWidth * progress;
                float indicatorY = innerY + indicatorInset;
                float indicatorHeight = innerHeight - indicatorInset * 2.0f;
                float indicatorRadius = Math.max(4.0f, outerRadius - 2.0f);
                float labelScale = 0.52f;
                float labelY = innerY + (innerHeight - target.textRenderer().getHeight(labelScale)) / 2.0f - 1.0f;
                Color inactiveLabel = MD3Theme.segmentedControlInactiveLabel();
                Color activeLabel = MD3Theme.segmentedControlActiveLabel();

                target.roundRectRenderer().addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), outerRadius, MD3Theme.OUTLINE_SOFT);
                target.roundRectRenderer().addRoundRect(innerX, innerY, innerWidth, innerHeight, Math.max(outerRadius - shellInset, 1.0f), MD3Theme.segmentedControlSurface());
                if (hoverProgress > 0.01f) {
                    target.roundRectRenderer().addRoundRect(innerX, innerY, innerWidth, innerHeight, Math.max(outerRadius - shellInset, 1.0f),
                            MD3Theme.stateLayer(MD3Theme.TEXT_PRIMARY, hoverProgress, MD3Theme.isLightTheme() ? 10 : 14));
                }
                target.rectRenderer().addRect(innerX + segmentWidth - 0.5f, innerY + 3.0f, 1.0f, innerHeight - 6.0f, MD3Theme.OUTLINE_SOFT);
                target.roundRectRenderer().addRoundRect(indicatorX, indicatorY, indicatorWidth, indicatorHeight, indicatorRadius, MD3Theme.segmentedControlIndicator());
                float leadingWidth = target.textRenderer().getWidth(leadingLabel, labelScale);
                float trailingWidth = target.textRenderer().getWidth(trailingLabel, labelScale);
                target.textRenderer().addText(leadingLabel, innerX + (segmentWidth - leadingWidth) / 2.0f, labelY, labelScale, MD3Theme.lerp(activeLabel, inactiveLabel, progress));
                target.textRenderer().addText(trailingLabel, innerX + segmentWidth + (segmentWidth - trailingWidth) / 2.0f, labelY, labelScale, MD3Theme.lerp(inactiveLabel, activeLabel, progress));
                continue;
            }
            if (node instanceof PanelUiTree.IconButtonNode(
                    PanelLayout.Rect bounds, String label, float scale, Color tone, float hoverProgress
            )) {
                target.roundRectRenderer().addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), bounds.height() / 2.0f,
                        MD3Theme.stateLayer(tone, hoverProgress, 32));
                Color labelColor = MD3Theme.lerp(MD3Theme.TEXT_MUTED, tone, hoverProgress);
                float labelWidth = target.textRenderer().getWidth(label, scale);
                float labelHeight = target.textRenderer().getHeight(scale);
                target.textRenderer().addText(label,
                        bounds.x() + (bounds.width() - labelWidth) / 2.0f,
                        bounds.y() + (bounds.height() - labelHeight) / 2.0f - 1.0f,
                        scale,
                        labelColor);
                continue;
            }
            if (node instanceof PanelUiTree.PopupCardNode(
                    PanelLayout.Rect bounds, float radius, float blurRadius, Color shadowColor, Color surfaceColor
            )) {
                renderPopupCard(target, bounds, radius, blurRadius, shadowColor, surfaceColor);
                continue;
            }
            if (node instanceof PanelUiTree.SliderNode(
                    PanelLayout.Rect bounds, float progress, float trackRadius, Color trackColor, float activeEndInset,
                    float activeMinWidth, Color activeColor, float handleWidth, float handleHeight, float handleRadius,
                    Color handleColor
            )) {
                renderSlider(target, bounds, progress, trackRadius, trackColor, activeEndInset, activeMinWidth, activeColor, handleWidth, handleHeight, handleRadius, handleColor);
                continue;
            }
            if (node instanceof PanelUiTree.ViewportNode(
                    PanelContentBuffer buffer, PanelLayout.Rect viewport, int guiHeight, float scroll, float maxScroll,
                    float contentHeight, List<PanelUiTree.UiNode> children
            )) {
                renderNodes(children, RenderTarget.forContentBuffer(buffer));
                buffer.queueViewport(viewport, guiHeight, scroll, maxScroll, contentHeight);
            }
        }
    }

    private static void renderButton(RenderTarget target, float x, float y, float width, float height, float radius, Color background, String label, float labelScale, Color labelColor) {
        target.roundRectRenderer().addRoundRect(x, y, width, height, radius, background);
        float labelWidth = target.textRenderer().getWidth(label, labelScale);
        float labelHeight = target.textRenderer().getHeight(labelScale);
        target.textRenderer().addText(label,
                x + (width - labelWidth) / 2.0f,
                y + (height - labelHeight) / 2.0f - 1.0f,
                labelScale,
                labelColor);
    }

    private static void renderPopupCard(RenderTarget target, PanelLayout.Rect bounds, float radius, float blurRadius, Color shadowColor, Color surfaceColor) {
        if (target.shadowRenderer() != null) {
            target.shadowRenderer().addShadow(bounds.x(), bounds.y(), bounds.width(), bounds.height(), radius, blurRadius, shadowColor);
        }
        target.roundRectRenderer().addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), radius, surfaceColor);
    }

    private static void renderSlider(RenderTarget target, PanelLayout.Rect bounds, float progress, float trackRadius, Color trackColor, float activeEndInset, float activeMinWidth, Color activeColor, float handleWidth, float handleHeight, float handleRadius, Color handleColor) {
        float clampedProgress = Math.clamp(progress, 0.0f, 1.0f);
        float safeHandleWidth = Math.max(1.0f, handleWidth);
        float handleX = bounds.x() + bounds.width() * clampedProgress - safeHandleWidth / 2.0f;
        float handleY = bounds.centerY() - handleHeight / 2.0f;
        float activeWidth = Math.max(activeMinWidth, bounds.width() * clampedProgress - activeEndInset);

        target.roundRectRenderer().addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), trackRadius, trackColor);
        if (activeWidth > 0.0f) {
            float clampedActiveWidth = Math.min(bounds.width(), activeWidth);
            target.roundRectRenderer().addRoundRect(bounds.x(), bounds.y(), clampedActiveWidth, bounds.height(), trackRadius, 1.0f, 1.0f, trackRadius, activeColor);
        }
        target.roundRectRenderer().addRoundRect(handleX, handleY, safeHandleWidth, handleHeight, handleRadius, handleColor);
    }

    private static void renderSwitch(RenderTarget target, PanelLayout.Rect bounds, float toggleProgress, float hoverProgress) {
        PanelElements.drawSwitch(target.roundRectRenderer(), target.roundRectOutlineRenderer(), bounds, toggleProgress, hoverProgress);
    }

    private static void renderFilledField(RenderTarget target, PanelLayout.Rect bounds, boolean focused, float hoverProgress) {
        target.roundRectRenderer().addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), MD3Theme.CONTROL_RADIUS, MD3Theme.filledFieldSurface(focused, hoverProgress));
    }

    private static void renderInput(RenderTarget target, PanelUiTree.InputElement element) {
        PanelLayout.Rect bounds = element.bounds();
        renderFilledField(target, bounds, element.focused(), element.hoverProgress());

        if (element.focusRingProgress() > 0.01f && element.focusRingInset() > 0.0f) {
            float inset = element.focusRingInset() * element.focusRingProgress();
            target.roundRectRenderer().addRoundRect(
                    bounds.x() - inset,
                    bounds.y() - inset,
                    bounds.width() + inset * 2.0f,
                    bounds.height() + inset * 2.0f,
                    MD3Theme.CONTROL_RADIUS + inset,
                    MD3Theme.withAlpha(element.focusRingColor(), (int) (48 * element.focusRingProgress()))
            );
        }

        String text = element.text();
        if (text != null && !text.isEmpty()) {
            float textX = bounds.x() + element.textInset();
            float textY = bounds.y() + (bounds.height() - target.textRenderer().getHeight(element.textScale())) / 2.0f - 1.0f;

            PanelUiTree.SelectionRange selection = element.selection();
            if (selection != null && element.selectionColor() != null) {
                int start = Math.clamp(selection.start(), 0, text.length());
                int end = Math.clamp(selection.end(), start, text.length());
                if (end > start) {
                    float selectionX = textX + target.textRenderer().getWidth(text.substring(0, start), element.textScale());
                    float selectionWidth = target.textRenderer().getWidth(text.substring(start, end), element.textScale());
                    target.rectRenderer().addRect(selectionX, bounds.y() + 3.0f, selectionWidth, bounds.height() - 6.0f, element.selectionColor());
                }
            }

            target.textRenderer().addText(text, textX, textY, element.textScale(), element.textColor());

            if (element.caretIndex() != null && element.caretColor() != null) {
                int caretIndex = Math.clamp(element.caretIndex(), 0, text.length());
                float caretX = textX + target.textRenderer().getWidth(text.substring(0, caretIndex), element.textScale());
                target.rectRenderer().addRect(caretX, bounds.y() + 4.0f, 1.0f, bounds.height() - 8.0f, element.caretColor());
            }
        }

        if (element.trailingHint() != null && !element.trailingHint().isBlank() && element.trailingHintColor() != null) {
            float hintWidth = target.textRenderer().getWidth(element.trailingHint(), element.trailingHintScale());
            float hintY = bounds.y() + (bounds.height() - target.textRenderer().getHeight(element.trailingHintScale())) / 2.0f - 1.0f;
            float hintX = bounds.right() - element.textInset() - hintWidth;
            target.textRenderer().addText(element.trailingHint(), hintX, hintY, element.trailingHintScale(), element.trailingHintColor());
        }
    }

    private record RenderTarget(ShadowRenderer shadowRenderer, RoundRectRenderer roundRectRenderer,
                                RoundRectOutlineRenderer roundRectOutlineRenderer, RectRenderer rectRenderer,
                                TextRenderer textRenderer, PanelContentBuffer buffer) {
        private RenderTarget(ShadowRenderer shadowRenderer, RoundRectRenderer roundRectRenderer, RoundRectOutlineRenderer roundRectOutlineRenderer, RectRenderer rectRenderer, TextRenderer textRenderer) {
            this(shadowRenderer, roundRectRenderer, roundRectOutlineRenderer, rectRenderer, textRenderer, null);
        }

        private static RenderTarget forContentBuffer(PanelContentBuffer buffer) {
            return new RenderTarget(buffer.shadowRenderer(), buffer.roundRectRenderer(), buffer.roundRectOutlineRenderer(), buffer.rectRenderer(), buffer.textRenderer(), buffer);
        }
    }

}
