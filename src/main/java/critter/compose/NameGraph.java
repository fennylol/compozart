package critter.compose;

import critter.model.NamedAnchor;
import critter.model.Node;
import critter.model.Project;

import java.util.*;

/**
 * The graph of node names: name A points at name B when some node named A has an anchor targeting B.
 * Strongly connected components tell which names repeat.
 */
public final class NameGraph {
    private final Map<String, Set<String>> edges = new HashMap<>();
    private final Map<String, Integer> component = new HashMap<>();
    private final Set<String> cyclic = new HashSet<>();

    public NameGraph(Project p) {
        for (Node n : p.nodes) {
            Set<String> out = edges.computeIfAbsent(n.name, k -> new LinkedHashSet<>());
            for (NamedAnchor a : n.anchors) {
                out.add(a.target);
                edges.computeIfAbsent(a.target, k -> new LinkedHashSet<>());
            }
        }
        tarjan();
    }

    /** True when the name can reach itself, so anchors targeting it repeat. */
    public boolean onCycle(String name) {
        return cyclic.contains(name);
    }

    /** True when both names are in the same cycle. */
    public boolean sameCycle(String a, String b) {
        Integer ca = component.get(a), cb = component.get(b);
        return ca != null && ca.equals(cb) && cyclic.contains(a);
    }

    public int componentOf(String name) {
        return component.getOrDefault(name, -1);
    }

    /** An anchor needs a depth when it starts a repetition: its target repeats and its own node is outside that cycle. */
    public boolean startsRepetition(String sourceName, NamedAnchor a) {
        return onCycle(a.target) && !sameCycle(sourceName, a.target);
    }

    // Iterative Tarjan, so deep name chains cannot overflow the stack.
    private void tarjan() {
        Map<String, Integer> index = new HashMap<>(), low = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String> onStack = new HashSet<>();
        int[] counter = {0};
        int[] comp = {0};
        for (String start : edges.keySet()) {
            if (index.containsKey(start)) continue;
            Deque<Map.Entry<String, Iterator<String>>> work = new ArrayDeque<>();
            index.put(start, counter[0]);
            low.put(start, counter[0]++);
            stack.push(start);
            onStack.add(start);
            work.push(Map.entry(start, edges.get(start).iterator()));
            while (!work.isEmpty()) {
                var top = work.peek();
                String v = top.getKey();
                Iterator<String> it = top.getValue();
                if (it.hasNext()) {
                    String w = it.next();
                    if (!index.containsKey(w)) {
                        index.put(w, counter[0]);
                        low.put(w, counter[0]++);
                        stack.push(w);
                        onStack.add(w);
                        work.push(Map.entry(w, edges.get(w).iterator()));
                    } else if (onStack.contains(w)) {
                        low.put(v, Math.min(low.get(v), index.get(w)));
                    }
                } else {
                    work.pop();
                    if (!work.isEmpty()) {
                        String parent = work.peek().getKey();
                        low.put(parent, Math.min(low.get(parent), low.get(v)));
                    }
                    if (low.get(v).equals(index.get(v))) {
                        List<String> members = new ArrayList<>();
                        String w;
                        do {
                            w = stack.pop();
                            onStack.remove(w);
                            component.put(w, comp[0]);
                            members.add(w);
                        } while (!w.equals(v));
                        if (members.size() > 1 || edges.get(v).contains(v)) cyclic.addAll(members);
                        comp[0]++;
                    }
                }
            }
        }
    }
}
