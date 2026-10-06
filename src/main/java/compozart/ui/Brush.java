package compozart.ui;

import java.awt.Point;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Round brushes for the draw tool and the eraser. A brush's size is its width in pixels. */
final class Brush {
    static final int MIN = 1, MAX = 32;

    private Brush() {
    }

    /**
     * The pixels a brush covers, as offsets from the pixel under the cursor. Even sizes lean up and left.
     * The radius is trimmed slightly so small brushes look round: size 3 is a plus, size 2 a square.
     */
    static List<Point> footprint(int size) {
        int d = Math.max(MIN, Math.min(MAX, size));
        double c = (d - 1) / 2.0;
        double r2 = d * d / 4.0 - 0.5;
        int shift = (d - 1) / 2;
        List<Point> out = new ArrayList<>();
        for (int y = 0; y < d; y++) {
            for (int x = 0; x < d; x++) {
                double dx = x - c, dy = y - c;
                if (d == 1 || dx * dx + dy * dy <= r2) out.add(new Point(x - shift, y - shift));
            }
        }
        return out;
    }

    /** Every pixel a stroke through {@code centers} covers, without duplicates. */
    static Set<Point> stamp(Iterable<Point> centers, int size) {
        List<Point> fp = footprint(size);
        Set<Point> out = new LinkedHashSet<>();
        for (Point c : centers) for (Point o : fp) out.add(new Point(c.x + o.x, c.y + o.y));
        return out;
    }
}
