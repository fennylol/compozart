package compozart.ui;

public enum Tool {
    DRAW("Draw", "draw", "draw"),
    ERASER("Eraser", "eraser", "eraser"),
    FILL("Fill", "fill", "fill"),
    SELECT("Select", "select", "select"),
    EYEDROPPER("Eyedropper", "eyedropper", "eyedropper"),
    LINE("Line", "line", "line"),
    RECT("Rectangle", "rect", "rect"),
    ANCHOR("Anchor", "anchor", "anchor");

    /** The name shown to the user, the id used in key bindings, and the toolbar icon. */
    public final String label, id, icon;

    Tool(String label, String id, String icon) {
        this.label = label;
        this.id = id;
        this.icon = icon;
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
