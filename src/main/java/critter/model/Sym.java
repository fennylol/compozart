package critter.model;

import java.util.List;

/**
 * One of the 8 symmetries of the square, as an integer matrix [a b; c d] acting on (x, y).
 * These are the only rotations and reflections that map a pixel grid onto itself.
 */
public record Sym(int a, int b, int c, int d) {
    public static final Sym IDENTITY = new Sym(1, 0, 0, 1);

    public static final List<Sym> ALL = List.of(
            IDENTITY,
            new Sym(0, -1, 1, 0),   // 90° clockwise on screen (y down)
            new Sym(-1, 0, 0, -1),  // 180°
            new Sym(0, 1, -1, 0),   // 270°
            new Sym(-1, 0, 0, 1),   // flip x
            new Sym(1, 0, 0, -1),   // flip y
            new Sym(0, 1, 1, 0),    // transpose
            new Sym(0, -1, -1, 0)); // anti-transpose

    public int det() {
        return a * d - b * c;
    }

    /** True for the four reflections, which reverse handedness. */
    public boolean mirrored() {
        return det() < 0;
    }

    public int x(int x, int y) {
        return a * x + b * y;
    }

    public int y(int x, int y) {
        return c * x + d * y;
    }

    public Dir apply(Dir dir) {
        return Dir.of(x(dir.dx, dir.dy), y(dir.dx, dir.dy));
    }

    /** The symmetry that applies {@code this} first, then {@code next}. */
    public Sym then(Sym next) {
        return new Sym(
                next.a * a + next.b * c, next.a * b + next.b * d,
                next.c * a + next.d * c, next.c * b + next.d * d);
    }

    /** Orthogonal matrices invert by transposing. */
    public Sym inverse() {
        return new Sym(a, c, b, d);
    }

    /** The one symmetry with the given handedness that maps {@code from} onto {@code to}. */
    public static Sym mapping(Dir from, Dir to, boolean mirrored) {
        for (Sym s : ALL) {
            if (s.mirrored() == mirrored && s.apply(from) == to) return s;
        }
        throw new AssertionError("no symmetry maps " + from + " to " + to);
    }
}
