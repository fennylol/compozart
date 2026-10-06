package critter.compose;

import critter.model.NamedAnchor;
import critter.model.Node;
import critter.model.Xform;

import java.util.*;

/**
 * A composed creature: the instance tree plus its pixels, rasterized per layer.
 * Pixels are palette indices. Coordinates in the rasters are relative to ({@link #minX}, {@link #minY}).
 */
public final class Composition {
    public final Instance root;
    /** Every instance, in depth-first tree order. This is also the draw order within a layer. */
    public final List<Instance> instances;
    public final List<String> warnings;
    public final boolean truncated;
    public final int paletteSize;

    /** Bounds of all non-clear pixels in world coordinates. Width and height are 0 when there are none. */
    public final int minX, minY, width, height;
    /** Non-empty layers, lowest first. Each is width × height, row-major. */
    public final SortedMap<Integer, byte[]> layers;
    /** All layers composed, top pixel wins. */
    public final byte[] flat;
    /** For each flat pixel, the id of the instance that drew it, or -1. */
    public final int[] owner;

    Composition(Instance root, List<Instance> instances, List<String> warnings, boolean truncated, int paletteSize) {
        this.root = root;
        this.instances = List.copyOf(instances);
        this.warnings = List.copyOf(warnings);
        this.truncated = truncated;
        this.paletteSize = paletteSize;

        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (Instance inst : instances) {
            Node n = inst.node;
            Xform xf = inst.xform;
            int s = n.size();
            byte[] px = n.pixels();
            for (int y = 0; y < s; y++) {
                for (int x = 0; x < s; x++) {
                    if (px[y * s + x] == 0) continue;
                    int wx = xf.x(x, y), wy = xf.y(x, y);
                    x0 = Math.min(x0, wx);
                    y0 = Math.min(y0, wy);
                    x1 = Math.max(x1, wx);
                    y1 = Math.max(y1, wy);
                }
            }
        }
        if (x0 > x1) {
            minX = minY = width = height = 0;
            layers = Collections.emptySortedMap();
            flat = new byte[0];
            owner = new int[0];
            return;
        }
        minX = x0;
        minY = y0;
        width = x1 - x0 + 1;
        height = y1 - y0 + 1;
        flat = new byte[width * height];
        owner = new int[width * height];
        Arrays.fill(owner, -1);

        SortedMap<Integer, List<Instance>> byLayer = new TreeMap<>();
        for (Instance inst : instances) byLayer.computeIfAbsent(inst.layer, k -> new ArrayList<>()).add(inst);
        SortedMap<Integer, byte[]> out = new TreeMap<>();
        for (var e : byLayer.entrySet()) {
            byte[] buf = new byte[width * height];
            boolean any = false;
            for (Instance inst : e.getValue()) {
                Node n = inst.node;
                Xform xf = inst.xform;
                int s = n.size();
                byte[] px = n.pixels();
                for (int y = 0; y < s; y++) {
                    for (int x = 0; x < s; x++) {
                        byte v = px[y * s + x];
                        if (v == 0) continue;
                        int i = (xf.y(x, y) - minY) * width + (xf.x(x, y) - minX);
                        buf[i] = v;
                        flat[i] = v;
                        owner[i] = inst.id;
                        any = true;
                    }
                }
            }
            if (any) out.put(e.getKey(), buf);
        }
        layers = Collections.unmodifiableSortedMap(out);
    }

    public boolean isEmpty() {
        return width == 0;
    }

    /** The instance that drew the world pixel (x, y), or null. */
    public Instance instanceAt(int x, int y) {
        int lx = x - minX, ly = y - minY;
        if (lx < 0 || ly < 0 || lx >= width || ly >= height) return null;
        int id = owner[ly * width + lx];
        return id < 0 ? null : instances.get(id);
    }

    /**
     * A string that changes whenever the shape of the tree changes, but not when only pixels change.
     * The tree view uses it to avoid rebuilding while you draw.
     */
    public String structureKey() {
        StringBuilder sb = new StringBuilder();
        for (Instance inst : instances) {
            sb.append(inst.id).append(':').append(inst.node.ref()).append('@').append(inst.layer)
                    .append('<').append(inst.parent == null ? -1 : inst.parent.id).append('/').append(inst.anchorIndex);
            for (Instance.Slot s : inst.slots) {
                NamedAnchor a = s.anchor;
                sb.append('|').append(a.target).append(',').append(a.mirrored).append(',').append(a.layerModifier)
                        .append(',').append(s.child == null ? "-" : s.child.id)
                        .append(',').append(s.problem).append(',').append(s.note);
            }
            sb.append('\n');
        }
        sb.append(warnings);
        return sb.toString();
    }
}
