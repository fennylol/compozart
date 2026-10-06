package critter.model;

/** A lossless placement: {@code p -> sym·p + (tx, ty)}, applied to pixel coordinates. */
public record Xform(Sym sym, int tx, int ty) {
    public static final Xform IDENTITY = new Xform(Sym.IDENTITY, 0, 0);

    public int x(int x, int y) {
        return sym.x(x, y) + tx;
    }

    public int y(int x, int y) {
        return sym.y(x, y) + ty;
    }

    public Dir apply(Dir dir) {
        return sym.apply(dir);
    }

    public boolean mirrored() {
        return sym.mirrored();
    }

    public Xform inverse() {
        Sym inv = sym.inverse();
        return new Xform(inv, -inv.x(tx, ty), -inv.y(tx, ty));
    }

    /** The placement that applies {@code this} first, then {@code next}. */
    public Xform then(Xform next) {
        return new Xform(sym.then(next.sym), next.x(tx, ty), next.y(tx, ty));
    }

    /**
     * Places a child so its root anchor and the parent's named anchor point into each other
     * across one pixel edge. The child's root pixel lands on the pixel just past that edge.
     *
     * @param parent   the parent's placement
     * @param socket   the named anchor on the parent
     * @param plug     the child's root anchor
     */
    public static Xform attach(Xform parent, NamedAnchor socket, RootAnchor plug) {
        int px = parent.x(socket.x, socket.y);
        int py = parent.y(socket.x, socket.y);
        Dir d = parent.apply(socket.dir);
        boolean mirrored = parent.mirrored() ^ socket.mirrored;
        Sym sym = Sym.mapping(plug.dir(), d.opposite(), mirrored);
        int tx = px + d.dx - sym.x(plug.x(), plug.y());
        int ty = py + d.dy - sym.y(plug.x(), plug.y());
        return new Xform(sym, tx, ty);
    }
}
