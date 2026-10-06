package critter.model;

/** The plug that attaches a node to a parent's named anchor. */
public record RootAnchor(int x, int y, Dir dir) {
    public RootAnchor at(int nx, int ny) {
        return new RootAnchor(nx, ny, dir);
    }

    public RootAnchor facing(Dir d) {
        return new RootAnchor(x, y, d);
    }
}
