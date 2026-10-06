package compozart.model;

/** One of the four cardinal directions, with y pointing down. */
public enum Dir {
    N(0, -1), E(1, 0), S(0, 1), W(-1, 0);

    public final int dx, dy;

    Dir(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public Dir opposite() {
        return of(-dx, -dy);
    }

    public Dir clockwise() {
        return values()[(ordinal() + 1) % 4];
    }

    public Dir counterClockwise() {
        return values()[(ordinal() + 3) % 4];
    }

    public static Dir of(int dx, int dy) {
        for (Dir d : values()) if (d.dx == dx && d.dy == dy) return d;
        throw new IllegalArgumentException("not a cardinal direction: " + dx + "," + dy);
    }

    /** The direction whose axis dominates the vector, preferring horizontal on ties. */
    public static Dir nearest(double dx, double dy) {
        if (Math.abs(dx) >= Math.abs(dy)) return dx >= 0 ? E : W;
        return dy >= 0 ? S : N;
    }
}
