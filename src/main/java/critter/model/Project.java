package critter.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Everything that describes one creature: the palette, the node library, the root and the seed. */
public final class Project {
    public static final int FORMAT_VERSION = 1;

    public Palette palette;
    public final List<Node> nodes = new ArrayList<>();
    /** The node at the top of the creature, or null when none is chosen. */
    public NodeRef root;
    public long seed;

    public Project(Palette palette) {
        this.palette = palette;
    }

    /** A new project: Catppuccin Mocha and a single empty "body" node as the root. */
    public static Project createDefault() {
        Project p = new Project(Palette.catppuccinMocha());
        Node body = new Node("body", "", Node.DEFAULT_SIZE);
        p.nodes.add(body);
        p.root = body.ref();
        p.seed = newSeed();
        return p;
    }

    public static long newSeed() {
        return new Random().nextInt(Integer.MAX_VALUE);
    }

    public Project copy() {
        Project p = new Project(palette.copy());
        for (Node n : nodes) p.nodes.add(n.copy());
        p.root = root;
        p.seed = seed;
        return p;
    }

    public Node find(NodeRef ref) {
        return ref == null ? null : find(ref.name(), ref.variant());
    }

    public Node find(String name, String variant) {
        String v = variant == null ? "" : variant;
        for (Node n : nodes) if (n.name.equals(name) && n.variant.equals(v)) return n;
        return null;
    }

    /** Every node with the given name, in library order. */
    public List<Node> named(String name) {
        List<Node> out = new ArrayList<>();
        for (Node n : nodes) if (n.name.equals(name)) out.add(n);
        return out;
    }

    public Node rootNode() {
        return find(root);
    }

    /** Applies an old-to-new palette index map to every pixel. */
    public void remapPixels(int[] map) {
        for (Node n : nodes) {
            byte[] px = n.pixels();
            for (int i = 0; i < px.length; i++) px[i] = (byte) map[px[i] & 0xff];
        }
    }

    public boolean usesColor(int index) {
        for (Node n : nodes) for (byte b : n.pixels()) if ((b & 0xff) == index) return true;
        return false;
    }

    /** Renames every reference to a node name: anchor targets and the root. */
    public void renameTarget(String oldName, String newName) {
        for (Node n : nodes) for (NamedAnchor a : n.anchors) if (a.target.equals(oldName)) a.target = newName;
        if (root != null && root.name().equals(oldName)) root = new NodeRef(newName, root.variant());
    }

    /** A name of the form {@code base}, {@code base 2}, ... that no node uses with this variant. */
    public String uniqueName(String base, String variant) {
        if (find(base, variant) == null) return base;
        for (int i = 2; ; i++) if (find(base + " " + i, variant) == null) return base + " " + i;
    }

    /** A variant name of the form {@code base}, {@code base 2}, ... unused for this node name. */
    public String uniqueVariant(String name, String base) {
        if (find(name, base) == null) return base;
        for (int i = 2; ; i++) if (find(name, base + " " + i) == null) return base + " " + i;
    }
}
