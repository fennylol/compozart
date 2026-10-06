package compozart.ui;

public enum Tool {
    DRAW("Draw", "draw"),
    ERASER("Eraser", "eraser"),
    FILL("Fill", "fill"),
    SELECT("Select", "select"),
    EYEDROPPER("Eyedropper", "eyedropper"),
    LINE("Line", "line"),
    RECT("Rectangle", "rect"),
    ANCHOR("Anchor", "anchor");

    public final String label, id;

    Tool(String label, String id) {
        this.label = label;
        this.id = id;
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
