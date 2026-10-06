package compozart.model;

/** The plug that attaches a node to a parent's named anchor. */
public record RootAnchor(int x, int y, Dir dir) {
    /** The default for a new node: the middle pixel, leaning toward (0, 0) on even sizes, pointing down. */
    public static RootAnchor centered(int size) {
        int c = (size - 1) / 2;
        return new RootAnchor(c, c, Dir.S);
    }

    public RootAnchor at(int nx, int ny) {
        return new RootAnchor(nx, ny, dir);
    }

    public RootAnchor facing(Dir d) {
        return new RootAnchor(x, y, d);
    }
}
