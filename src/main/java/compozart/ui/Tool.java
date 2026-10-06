package compozart.ui;

import compozart.text.L10n;

public enum Tool {
    DRAW("tool.draw.name", "draw", "draw"),
    ERASER("tool.eraser.name", "eraser", "eraser"),
    FILL("tool.fill.name", "fill", "fill"),
    SELECT("tool.select.name", "select", "select"),
    EYEDROPPER("tool.eyedropper.name", "eyedropper", "eyedropper"),
    LINE("tool.line.name", "line", "line"),
    RECT("tool.rect.name", "rect", "rect"),
    ANCHOR("tool.anchor.name", "anchor", "anchor");

    /** The key of the tool's name, the id used in key bindings, and the toolbar icon. */
    public final String key, id, icon;

    Tool(String key, String id, String icon) {
        this.key = key;
        this.id = id;
        this.icon = icon;
    }

    /** The tool's name in the current language. */
    public String label() {
        return L10n.t(key);
    }

    /** Tools that paint with the round brush. */
    public boolean brushed() {
        return this == DRAW || this == ERASER;
    }

    /** Tools whose strokes the symmetry mode mirrors. */
    public boolean symmetric() {
        return this == DRAW || this == ERASER || this == FILL || this == LINE || this == RECT;
    }
}
