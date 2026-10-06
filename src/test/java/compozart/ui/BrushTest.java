package compozart.ui;

import java.awt.Point;
import java.util.List;
import java.util.Set;

import static compozart.test.Check.*;

public class BrushTest {
    public void testSmallBrushes() {
        eq(List.of(new Point(0, 0)), Brush.footprint(1));
        eq(4, Brush.footprint(2).size());       // 2x2 square
        eq(5, Brush.footprint(3).size());       // plus
        yes(Brush.footprint(3).contains(new Point(0, -1)), "plus has its top arm");
        no(Brush.footprint(3).contains(new Point(1, 1)), "plus has no corners");
        eq(12, Brush.footprint(4).size());      // 4x4 without corners
        eq(21, Brush.footprint(5).size());      // 5x5 without corners
    }

    public void testRoundAndCentered() {
        for (int d = 1; d <= Brush.MAX; d++) {
            List<Point> fp = Brush.footprint(d);
            int minX = fp.stream().mapToInt(p -> p.x).min().orElseThrow();
            int maxX = fp.stream().mapToInt(p -> p.x).max().orElseThrow();
            eq("width of size " + d, d, maxX - minX + 1);
            yes(fp.contains(new Point(0, 0)), "covers the cursor pixel at size " + d);
            for (Point p : fp) yes(fp.contains(new Point(p.y, p.x)), "symmetric across the diagonal at size " + d);
        }
    }

    public void testClampsAndStamps() {
        eq(Brush.footprint(Brush.MAX).size(), Brush.footprint(99).size());
        eq(1, Brush.footprint(0).size());
        Set<Point> s = Brush.stamp(List.of(new Point(5, 5), new Point(6, 5)), 3);
        eq(8, s.size()); // two overlapping plus shapes
    }
}
