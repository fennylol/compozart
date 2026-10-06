package critter.compose;

import critter.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * The faded parent drawn behind a node in the canvas view.
 * A candidate is any parent node with an anchor that would attach the edited node.
 */
public final class ParentGhost {
    public record Candidate(Node parent, int anchorIndex) {
        public NamedAnchor anchor() {
            return parent.anchors.get(anchorIndex);
        }

        public String label() {
            return parent.ref() + " → " + anchor().target + " #" + (anchorIndex + 1);
        }
    }

    private ParentGhost() {
    }

    /** Every (parent, anchor) pair that can attach {@code child}, in library and anchor order. */
    public static List<Candidate> candidates(Project p, Node child) {
        List<Candidate> out = new ArrayList<>();
        if (child.root == null) return out;
        for (Node n : p.nodes) {
            for (int i = 0; i < n.anchors.size(); i++) {
                NamedAnchor a = n.anchors.get(i);
                if (!a.target.equals(child.name)) continue;
                boolean fits = a.variant == null || a.variant.equals(child.variant)
                        || child.variant.equals(a.endVariant);
                if (fits) out.add(new Candidate(n, i));
            }
        }
        return out;
    }

    /** Where the parent sits in the child's own canvas frame, with the child fixed in place. */
    public static Xform parentInChildFrame(Candidate c, Node child) {
        Xform childInParent = Xform.attach(Xform.IDENTITY, c.anchor(), child.root);
        return childInParent.inverse();
    }
}
