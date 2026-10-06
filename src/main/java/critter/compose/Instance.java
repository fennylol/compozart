package critter.compose;

import critter.model.NamedAnchor;
import critter.model.Node;
import critter.model.Xform;

import java.util.ArrayList;
import java.util.List;

/** One placed copy of a node in the composed creature. */
public final class Instance {
    public final int id;
    public final Node node;
    public final Instance parent;
    /** Index of the parent's named anchor this instance hangs from, or -1 for the root. */
    public final int anchorIndex;
    public final Xform xform;
    public final int layer;
    /** One slot per named anchor on {@link #node}, in anchor order. */
    public final List<Slot> slots = new ArrayList<>();

    Instance(int id, Node node, Instance parent, int anchorIndex, Xform xform, int layer) {
        this.id = id;
        this.node = node;
        this.parent = parent;
        this.anchorIndex = anchorIndex;
        this.xform = xform;
        this.layer = layer;
    }

    /** The named anchor on the parent this instance hangs from, or null for the root. */
    public NamedAnchor socket() {
        return parent == null ? null : parent.node.anchors.get(anchorIndex);
    }

    /** What happened at one named anchor during composition. */
    public static final class Slot {
        public final int anchorIndex;
        public final NamedAnchor anchor;
        /** The attached instance, or null when nothing attached. */
        public Instance child;
        /** Why nothing attached when that is a mistake, or null. */
        public String problem;
        /** Why nothing attached when that is expected (depth reached), or null. */
        public String note;

        Slot(int anchorIndex, NamedAnchor anchor) {
            this.anchorIndex = anchorIndex;
            this.anchor = anchor;
        }
    }
}
