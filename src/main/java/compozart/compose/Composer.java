package compozart.compose;

import compozart.model.*;

import java.util.*;

/**
 * Builds the instance tree for a project and rasterizes it into layers.
 * See SPEC.md, "Composition".
 */
public final class Composer {
    public static final int MAX_INSTANCES = 4096;

    private final Project project;
    private final NameGraph graph;
    private final List<Instance> instances = new ArrayList<>();
    private final Set<String> warnings = new LinkedHashSet<>();
    private boolean truncated;

    /**
     * A running repetition count for one cycle of names.
     *
     * @param name       the name being counted
     * @param remaining  how many more instances of that name may be placed
     * @param endVariant the variant for the last one, or blank
     * @param sinceLast  other names in the cycle placed since the counted name last appeared
     */
    private record Counter(String name, int remaining, String endVariant, Set<String> sinceLast) {
    }

    private Composer(Project project) {
        this.project = project;
        this.graph = new NameGraph(project);
    }

    public static Composition compose(Project project) {
        return new Composer(project).run();
    }

    private Composition run() {
        Node rootNode = project.rootNode();
        Instance root = null;
        if (project.root == null) {
            warnings.add("No root node is selected.");
        } else if (rootNode == null) {
            warnings.add("The root node " + project.root + " does not exist.");
        } else {
            root = place(rootNode, null, -1, Xform.IDENTITY, 0, 0x5eedL, Map.of());
        }
        if (truncated) warnings.add("Stopped at " + MAX_INSTANCES + " instances.");
        return new Composition(root, instances, new ArrayList<>(warnings), truncated, project.palette.size());
    }

    private Instance place(Node node, Instance parent, int anchorIndex, Xform xf, int layer, long path,
                           Map<Integer, Counter> counters) {
        Instance inst = new Instance(instances.size(), node, parent, anchorIndex, xf, layer);
        instances.add(inst);
        for (int i = 0; i < node.anchors.size(); i++) {
            NamedAnchor a = node.anchors.get(i);
            Instance.Slot slot = new Instance.Slot(i, a);
            inst.slots.add(slot);
            if (instances.size() >= MAX_INSTANCES) {
                truncated = true;
                slot.problem = "instance limit reached";
                continue;
            }

            Map<Integer, Counter> childCounters = counters;
            String forcedVariant = null;
            if (graph.onCycle(a.target)) {
                int comp = graph.componentOf(a.target);
                boolean inside = graph.sameCycle(node.name, a.target);
                Counter c = counters.get(comp);
                if (!inside || c == null) {
                    if (!inside && a.depth == null) {
                        warnings.add("Depth is not set on " + node.ref() + " → " + a.target + "; using 1.");
                    }
                    int depth = a.depth == null ? 1 : Math.max(0, a.depth);
                    c = new Counter(a.target, depth, a.endVariant, Set.of());
                }
                Counter next;
                if (a.target.equals(c.name)) {
                    if (c.remaining <= 0) {
                        slot.note = "depth reached";
                        continue;
                    }
                    if (c.remaining == 1 && c.endVariant != null) forcedVariant = c.endVariant;
                    next = new Counter(c.name, c.remaining - 1, c.endVariant, Set.of());
                } else {
                    // A loop inside the cycle that skips the counted name runs once, then stops.
                    if (c.sinceLast.contains(a.target)) {
                        slot.note = "loop does not pass through " + c.name;
                        continue;
                    }
                    Set<String> seen = new HashSet<>(c.sinceLast);
                    seen.add(a.target);
                    next = new Counter(c.name, c.remaining, c.endVariant, seen);
                }
                Map<Integer, Counter> m = new HashMap<>(counters);
                m.put(comp, next);
                childCounters = m;
            }

            long childPath = mix(path, i + 1);
            Counter active = childCounters.get(graph.componentOf(a.target));
            String avoid = active != null && a.target.equals(active.name) ? active.endVariant : null;
            Node child = resolve(a, forcedVariant, avoid, childPath, slot);
            if (child == null) continue;
            if (child.root == null) {
                slot.problem = child.ref() + " has no root anchor";
                continue;
            }
            Xform childXf = Xform.attach(xf, a, child.root);
            slot.child = place(child, inst, i, childXf, layer + a.layerModifier, childPath, childCounters);
        }
        return inst;
    }

    /**
     * Picks the node that attaches at an anchor, or records why none does.
     *
     * @param avoid a variant random picks skip when anything else is available (the end variant of a repetition)
     */
    private Node resolve(NamedAnchor a, String forcedVariant, String avoid, long path, Instance.Slot slot) {
        if (forcedVariant != null) {
            Node n = project.find(a.target, forcedVariant);
            if (n == null) slot.problem = "no node " + new NodeRef(a.target, forcedVariant);
            return n;
        }
        if (a.variant != null) {
            Node n = project.find(a.target, a.variant);
            if (n == null) slot.problem = "no node " + new NodeRef(a.target, a.variant);
            return n;
        }
        if (a.target.isEmpty()) {
            slot.note = "no target set";
            return null;
        }
        List<Node> all = project.named(a.target);
        List<Node> candidates = all.stream().filter(n -> n.root != null).toList();
        if (candidates.isEmpty()) {
            if (all.isEmpty()) slot.note = "no node named " + a.target;
            else slot.problem = "no variant of " + a.target + " has a root anchor";
            return null;
        }
        if (avoid != null) {
            List<Node> rest = candidates.stream().filter(n -> !n.variant.equals(avoid)).toList();
            if (!rest.isEmpty()) candidates = rest;
        }
        long key = a.group.isEmpty()
                ? mix(project.seed, path)
                : mix(mix(project.seed, a.target.hashCode()), a.group.hashCode() * 31L + 7);
        return candidates.get((int) Math.floorMod(key, (long) candidates.size()));
    }

    /** SplitMix64 over a combined pair: stable across runs and platforms. */
    static long mix(long a, long b) {
        long z = a * 0x9E3779B97F4A7C15L + b + 0x632BE59BD9B4E019L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
