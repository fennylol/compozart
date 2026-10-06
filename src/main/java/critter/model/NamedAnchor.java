package critter.model;

/** A socket on a node. Any node whose name matches {@link #target} can attach here. */
public final class NamedAnchor {
    public String target;
    public int x, y;
    public Dir dir;
    /** Which variant of the target to attach. Null means a random pick; "" is the unnamed variant. */
    public String variant;
    /** Random anchors with the same target and group share one pick. Blank means no group. */
    public String group = "";
    /** The child's layer relative to this node's layer. */
    public int layerModifier = 1;
    /** Reflects the child and its whole subtree across the child's root arrow. */
    public boolean mirrored;
    /** How many times a repeating target appears below this anchor. Null means unset. */
    public Integer depth;
    /** The variant used for the last repetition. Null means none; "" is the unnamed variant. */
    public String endVariant;

    public NamedAnchor(String target, int x, int y, Dir dir) {
        this.target = target;
        this.x = x;
        this.y = y;
        this.dir = dir;
    }

    public NamedAnchor copy() {
        NamedAnchor a = new NamedAnchor(target, x, y, dir);
        a.variant = variant;
        a.group = group;
        a.layerModifier = layerModifier;
        a.mirrored = mirrored;
        a.depth = depth;
        a.endVariant = endVariant;
        return a;
    }
}
