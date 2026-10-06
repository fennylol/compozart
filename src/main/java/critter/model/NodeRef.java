package critter.model;

/** Identifies a node by name and variant. A blank variant is the empty string. */
public record NodeRef(String name, String variant) {
    public NodeRef {
        variant = variant == null ? "" : variant;
    }

    public static NodeRef of(Node n) {
        return new NodeRef(n.name, n.variant);
    }

    @Override
    public String toString() {
        return variant.isEmpty() ? name : name + " [" + variant + "]";
    }
}
