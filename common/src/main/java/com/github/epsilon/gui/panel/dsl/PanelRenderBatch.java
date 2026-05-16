package com.github.epsilon.gui.panel.dsl;

import com.github.epsilon.graphics.renderers.*;

/**
 * 面板 UI 的通用 renderer 批次封装。
 * <p>
 * 一个批次持有阴影、圆角矩形、圆角矩形描边、矩形、三角形和文本六类 renderer，负责承接
 * {@link PanelUiCompiler} 的编译输出，并在统一阶段执行 flush 或 clear。
 */
public record PanelRenderBatch(ShadowRenderer shadowRenderer, RoundRectRenderer roundRectRenderer,
                               RoundRectOutlineRenderer roundRectOutlineRenderer, RectRenderer rectRenderer,
                               TriangleRenderer triangleRenderer, TextRenderer textRenderer) {

    public PanelRenderBatch() {
        this(ShadowRenderer.create(), RoundRectRenderer.create(), RoundRectOutlineRenderer.create(), RectRenderer.create(),
                TriangleRenderer.create(), TextRenderer.create());
    }

    public PanelRenderBatch(ShadowRenderer shadowRenderer, RoundRectRenderer roundRectRenderer,
                            RoundRectOutlineRenderer roundRectOutlineRenderer, RectRenderer rectRenderer,
                            TextRenderer textRenderer) {
        this(shadowRenderer, roundRectRenderer, roundRectOutlineRenderer, rectRenderer,
                TriangleRenderer.create(), textRenderer);
    }

    public void render(PanelUiTree tree) {
        PanelUiCompiler.render(tree, shadowRenderer, roundRectRenderer, roundRectOutlineRenderer, rectRenderer, triangleRenderer, textRenderer);
    }

    public void flush() {
        shadowRenderer.draw();
        roundRectRenderer.draw();
        roundRectOutlineRenderer.draw();
        rectRenderer.draw();
        triangleRenderer.draw();
        textRenderer.draw();
    }

    public void clear() {
        shadowRenderer.clear();
        roundRectRenderer.clear();
        roundRectOutlineRenderer.clear();
        rectRenderer.clear();
        triangleRenderer.clear();
        textRenderer.clear();
    }

    public void flushAndClear() {
        flush();
        clear();
    }

}
