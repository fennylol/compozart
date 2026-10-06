package critter.model;

import java.util.ArrayList;
import java.util.List;

/** A square pixel canvas of palette indices, with at most one root anchor and any number of named anchors. */
public final class Node {
    public static final int DEFAULT_SIZE = 32;
    public static final int MAX_SIZE = 256;

    public String name;
    public String variant;
    private int size;
    private byte[] pixels;
    public RootAnchor root;
    public final List<NamedAnchor> anchors = new ArrayList<>();

    public Node(String name, String variant, int size) {
        if (size < 1 || size > MAX_SIZE) throw new IllegalArgumentException("node size out of range: " + size);
        this.name = name;
        this.variant = variant == null ? "" : variant;
        this.size = size;
        this.pixels = new byte[size * size];
    }

    public int size() {
        return size;
    }

    public boolean contains(int x, int y) {
        return x >= 0 && y >= 0 && x < size && y < size;
    }

    public int get(int x, int y) {
        return pixels[y * size + x] & 0xff;
    }

    public void set(int x, int y, int index) {
        pixels[y * size + x] = (byte) index;
    }

    /** The raw pixel array, row-major. Callers must not keep it across edits. */
    public byte[] pixels() {
        return pixels;
    }

    public NodeRef ref() {
        return NodeRef.of(this);
    }

    public Node copy() {
        Node n = new Node(name, variant, size);
        System.arraycopy(pixels, 0, n.pixels, 0, pixels.length);
        n.root = root;
        for (NamedAnchor a : anchors) n.anchors.add(a.copy());
        return n;
    }

    /**
     * Resizes the canvas. {@code fixX} and {@code fixY} pick which part stays fixed:
     * 0 for left/top, 1 for center, 2 for right/bottom.
     *
     * @return the number of anchors removed because they fell outside
     */
    public int resize(int newSize, int fixX, int fixY) {
        if (newSize < 1 || newSize > MAX_SIZE) throw new IllegalArgumentException("node size out of range: " + newSize);
        int dx = resizeShift(size, newSize, fixX);
        int dy = resizeShift(size, newSize, fixY);
        byte[] next = new byte[newSize * newSize];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < newSize && ny < newSize) next[ny * newSize + nx] = pixels[y * size + x];
            }
        }
        int removed = 0;
        if (root != null) {
            RootAnchor moved = root.at(root.x() + dx, root.y() + dy);
            if (inside(moved.x(), moved.y(), newSize)) root = moved;
            else {
                root = null;
                removed++;
            }
        }
        for (var it = anchors.iterator(); it.hasNext(); ) {
            NamedAnchor a = it.next();
            a.x += dx;
            a.y += dy;
            if (!inside(a.x, a.y, newSize)) {
                it.remove();
                removed++;
            }
        }
        size = newSize;
        pixels = next;
        return removed;
    }

    /** How many anchors a resize would remove, without changing anything. */
    public int anchorsLostByResize(int newSize, int fixX, int fixY) {
        Node probe = copy();
        return probe.resize(newSize, fixX, fixY);
    }

    static int resizeShift(int oldSize, int newSize, int fix) {
        return switch (fix) {
            case 0 -> 0;
            case 1 -> Math.floorDiv(newSize - oldSize, 2);
            case 2 -> newSize - oldSize;
            default -> throw new IllegalArgumentException("fix must be 0, 1 or 2");
        };
    }

    private static boolean inside(int x, int y, int size) {
        return x >= 0 && y >= 0 && x < size && y < size;
    }
}
