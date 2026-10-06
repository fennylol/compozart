package critter.ui;

import java.awt.Point;
import java.util.LinkedHashSet;
import java.util.Set;

/** Drawing symmetry inside one node. Mirror axes go through the canvas center. */
public enum Symmetry {
    NONE("None"),
    LEFT_RIGHT("Left / right"),
    TOP_BOTTOM("Top / bottom"),
    QUAD("Quad"),
    DIAGONAL_MAIN("Diagonal \\"),
    DIAGONAL_ANTI("Diagonal /");

    public final String label;

    Symmetry(String label) {
        this.label = label;
    }

    /** The pixel and its mirror images on a canvas of the given size, without duplicates. */
    public Set<Point> images(int x, int y, int size) {
        int m = size - 1;
        Set<Point> out = new LinkedHashSet<>();
        out.add(new Point(x, y));
        switch (this) {
            case NONE -> {
            }
            case LEFT_RIGHT -> out.add(new Point(m - x, y));
            case TOP_BOTTOM -> out.add(new Point(x, m - y));
            case QUAD -> {
                out.add(new Point(m - x, y));
                out.add(new Point(x, m - y));
                out.add(new Point(m - x, m - y));
            }
            case DIAGONAL_MAIN -> out.add(new Point(y, x));
            case DIAGONAL_ANTI -> out.add(new Point(m - y, m - x));
        }
        return out;
    }

    @Override
    public String toString() {
        return label;
    }
}
