package compozart.ui;

import compozart.text.L10n;

import java.awt.Point;
import java.util.LinkedHashSet;
import java.util.Set;

/** Drawing symmetry inside one node. Mirror axes go through the canvas center. */
public enum Symmetry {
    NONE("symmetry.none"),
    LEFT_RIGHT("symmetry.leftRight"),
    TOP_BOTTOM("symmetry.topBottom"),
    QUAD("symmetry.quad"),
    DIAGONAL_MAIN("symmetry.diagonalMain"),
    DIAGONAL_ANTI("symmetry.diagonalAnti");

    /** The key of the mode's name in the language file. */
    public final String key;

    Symmetry(String key) {
        this.key = key;
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
        return L10n.t(key);
    }
}
