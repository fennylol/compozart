package critter.ui;

public enum Tool {
    PENCIL("Pencil", "pencil"),
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

    /** Tools whose strokes the symmetry mode mirrors. */
    public boolean symmetric() {
        return this == PENCIL || this == ERASER || this == FILL || this == LINE || this == RECT;
    }
}
