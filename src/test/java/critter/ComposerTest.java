package critter;

import critter.compose.*;
import critter.model.*;

import java.util.List;

import static critter.test.Check.*;

public class ComposerTest {
    static Project project() {
        Project p = new Project(Palette.catppuccinMocha());
        p.seed = 42;
        return p;
    }

    static Node node(Project p, String name, String variant, int size) {
        Node n = new Node(name, variant, size);
        p.nodes.add(n);
        return n;
    }

    static NamedAnchor socket(Node n, String target, int x, int y, Dir d) {
        NamedAnchor a = new NamedAnchor(target, x, y, d);
        n.anchors.add(a);
        return a;
    }

    static long count(Composition c, String name) {
        return c.instances.stream().filter(i -> i.node.name.equals(name)).count();
    }

    /** body 8x8 with one "eye" socket pointing N; eye 4x4 with its plug at the bottom. */
    static Project bodyAndEye() {
        Project p = project();
        Node body = node(p, "body", "", 8);
        body.set(3, 2, 1);
        socket(body, "eye", 3, 2, Dir.N);
        Node eye = node(p, "eye", "", 4);
        eye.root = new RootAnchor(1, 3, Dir.S);
        eye.set(1, 3, 2);
        eye.set(2, 2, 3);
        p.root = body.ref();
        return p;
    }

    public void testNoRoot() {
        Project p = project();
        Composition c = Composer.compose(p);
        eq(null, c.root);
        yes(c.isEmpty(), "should be empty");
        eq(1, c.warnings.size());
    }

    public void testPlacesChildAcrossEdge() {
        Composition c = Composer.compose(bodyAndEye());
        eq(2, c.instances.size());
        Instance eye = c.instances.get(1);
        eq(3, eye.xform.x(1, 3));
        eq(1, eye.xform.y(1, 3));
        eq(1, eye.layer);
        // world (3,1) is the eye's plug pixel, (3,2) is the body's socket pixel
        eq(eye, c.instanceAt(3, 1));
        eq(c.root, c.instanceAt(3, 2));
    }

    public void testBoundsAndFlat() {
        Composition c = Composer.compose(bodyAndEye());
        // pixels at world (3,2) body, (3,1) eye plug, (4,0) eye's (2,2)
        eq(3, c.minX);
        eq(0, c.minY);
        eq(2, c.width);
        eq(3, c.height);
        eq((byte) 3, c.flat[0 * 2 + 1]);
        eq((byte) 2, c.flat[1 * 2 + 0]);
        eq((byte) 1, c.flat[2 * 2 + 0]);
    }

    public void testLayersAndTopWins() {
        Project p = project();
        Node body = node(p, "body", "", 4);
        body.set(1, 1, 1);
        NamedAnchor back = socket(body, "patch", 1, 2, Dir.N); // child lands on (1,1), behind
        back.layerModifier = -1;
        Node patch = node(p, "patch", "", 1);
        patch.root = new RootAnchor(0, 0, Dir.S);
        patch.set(0, 0, 5);
        p.root = body.ref();

        Composition c = Composer.compose(p);
        eq(-1, c.instances.get(1).layer);
        eq(List.of(-1, 0), List.copyOf(c.layers.keySet()));
        eq((byte) 1, c.flat[0]);            // body on layer 0 covers the patch
        eq((byte) 5, c.layers.get(-1)[0]);  // the patch is still in its own layer

        back.layerModifier = 1;
        c = Composer.compose(p);
        eq((byte) 5, c.flat[0]);
    }

    public void testGrandchildLayerIsRelative() {
        Project p = project();
        Node body = node(p, "body", "", 4);
        NamedAnchor a = socket(body, "arm", 0, 0, Dir.N);
        a.layerModifier = -1;
        Node arm = node(p, "arm", "", 4);
        arm.root = new RootAnchor(0, 3, Dir.S);
        socket(arm, "hand", 0, 0, Dir.N);
        Node hand = node(p, "hand", "", 4);
        hand.root = new RootAnchor(0, 3, Dir.S);
        p.root = body.ref();
        Composition c = Composer.compose(p);
        eq(List.of(0, -1, 0), c.instances.stream().map(i -> i.layer).toList());
    }

    public void testSameLayerTreeOrder() {
        // Two children on the same layer overlap; the later anchor draws on top.
        Project p = project();
        Node body = node(p, "body", "", 4);
        socket(body, "a", 1, 2, Dir.N);
        socket(body, "b", 1, 2, Dir.N);
        for (String name : new String[]{"a", "b"}) {
            Node n = node(p, name, "", 1);
            n.root = new RootAnchor(0, 0, Dir.S);
            n.set(0, 0, name.equals("a") ? 7 : 8);
        }
        p.root = body.ref();
        eq((byte) 8, Composer.compose(p).flat[0]);
    }

    public void testExplicitVariant() {
        Project p = bodyAndEye();
        Node angry = node(p, "eye", "angry", 4);
        angry.root = new RootAnchor(0, 3, Dir.S);
        p.find("body", "").anchors.get(0).variant = "angry";
        eq(angry, Composer.compose(p).instances.get(1).node);
        p.find("body", "").anchors.get(0).variant = "sad";
        Composition c = Composer.compose(p);
        eq(1, c.instances.size());
        eq("no node eye [sad]", c.root.slots.get(0).problem);
    }

    public void testRandomIsDeterministicAndUsesAllVariants() {
        Project p = project();
        Node body = node(p, "body", "", 16);
        for (int i = 0; i < 16; i++) socket(body, "eye", i, 15, Dir.S);
        for (String v : new String[]{"", "a", "b", "c"}) {
            Node e = node(p, "eye", v, 1);
            e.root = new RootAnchor(0, 0, Dir.N);
        }
        p.root = body.ref();
        List<NodeRef> first = Composer.compose(p).instances.stream().skip(1).map(i -> i.node.ref()).toList();
        List<NodeRef> again = Composer.compose(p).instances.stream().skip(1).map(i -> i.node.ref()).toList();
        eq(first, again);
        yes(first.stream().distinct().count() > 1, "16 independent picks should not all match");
        p.seed = 43;
        List<NodeRef> other = Composer.compose(p).instances.stream().skip(1).map(i -> i.node.ref()).toList();
        no(first.equals(other), "a new seed should change the picks");
    }

    public void testGroupSharesPick() {
        Project p = project();
        Node body = node(p, "body", "", 16);
        for (int i = 0; i < 16; i++) socket(body, "eye", i, 15, Dir.S).group = "pair";
        for (String v : new String[]{"", "a", "b", "c"}) {
            Node e = node(p, "eye", v, 1);
            e.root = new RootAnchor(0, 0, Dir.N);
        }
        p.root = body.ref();
        for (long seed = 0; seed < 20; seed++) {
            p.seed = seed;
            eq(1L, Composer.compose(p).instances.stream().skip(1).map(i -> i.node.ref()).distinct().count());
        }
    }

    public void testMissingTargetIsANoteNotAProblem() {
        Project p = bodyAndEye();
        p.nodes.removeIf(n -> n.name.equals("eye"));
        Instance.Slot s = Composer.compose(p).root.slots.get(0);
        eq(null, s.problem);
        eq("no node named eye", s.note);
    }

    public void testChildWithoutRootAnchor() {
        Project p = bodyAndEye();
        p.find("eye", "").root = null;
        eq("no variant of eye has a root anchor", Composer.compose(p).root.slots.get(0).problem);
    }

    static Project tail(int depth, String endVariant) {
        Project p = project();
        Node body = node(p, "body", "", 4);
        NamedAnchor a = socket(body, "tail", 3, 1, Dir.E);
        a.depth = depth;
        a.endVariant = endVariant;
        Node tail = node(p, "tail", "", 2);
        tail.root = new RootAnchor(0, 0, Dir.W);
        socket(tail, "tail", 1, 0, Dir.E);
        Node tip = node(p, "tail", "tip", 2);
        tip.root = new RootAnchor(0, 0, Dir.W);
        p.root = body.ref();
        return p;
    }

    public void testDepthCountsSegments() {
        Project p = tail(5, null);
        p.nodes.remove(p.find("tail", "tip"));
        Composition c = Composer.compose(p);
        eq(5L, count(c, "tail"));
        eq(List.of(), c.warnings);
        // segments march east two pixels at a time
        eq(4, c.instances.get(1).xform.x(0, 0));
        eq(12, c.instances.get(5).xform.x(0, 0));
        eq(5, c.instances.get(5).layer);
    }

    public void testEndVariant() {
        Project p = tail(3, "tip");
        p.find("tail", "tip").anchors.clear();
        Composition c = Composer.compose(p);
        eq(3L, count(c, "tail"));
        eq("tip", c.instances.get(3).node.variant);
        eq("", c.instances.get(2).node.variant);
        p.nodes.remove(p.find("tail", "tip"));
        p.find("body", "").anchors.get(0).endVariant = "stub";
        eq("no node tail [stub]", Composer.compose(p).instances.get(2).slots.get(0).problem);
    }

    public void testDepthZero() {
        Project p = tail(0, null);
        eq(0L, count(Composer.compose(p), "tail"));
        eq("depth reached", Composer.compose(p).root.slots.get(0).note);
    }

    public void testMissingDepthWarnsAndUsesOne() {
        Project p = tail(1, null);
        p.nodes.remove(p.find("tail", "tip"));
        p.find("body", "").anchors.get(0).depth = null;
        Composition c = Composer.compose(p);
        eq(1L, count(c, "tail"));
        eq(1, c.warnings.size());
    }

    public void testCyclicRootUsesOwnDepth() {
        Project p = tail(5, null);
        p.nodes.remove(p.find("tail", "tip"));
        p.root = new NodeRef("tail", "");
        eq(2L, count(Composer.compose(p), "tail")); // root + default depth 1
        p.find("tail", "").anchors.get(0).depth = 4;
        eq(5L, count(Composer.compose(p), "tail"));
    }

    public void testTwoNodeCycle() {
        Project p = project();
        Node body = node(p, "body", "", 4);
        socket(body, "a", 0, 0, Dir.N).depth = 3;
        Node a = node(p, "a", "", 1);
        a.root = new RootAnchor(0, 0, Dir.S);
        socket(a, "b", 0, 0, Dir.N);
        Node b = node(p, "b", "", 1);
        b.root = new RootAnchor(0, 0, Dir.S);
        socket(b, "a", 0, 0, Dir.N);
        p.root = body.ref();
        Composition c = Composer.compose(p);
        eq(3L, count(c, "a"));
        eq(3L, count(c, "b"));
    }

    public void testLoopThatSkipsCountedNameStops() {
        // a <-> b and b <-> c: b and c could ping-pong forever without passing through a.
        Project p = project();
        Node body = node(p, "body", "", 4);
        socket(body, "a", 0, 0, Dir.N).depth = 2;
        Node a = node(p, "a", "", 1);
        a.root = new RootAnchor(0, 0, Dir.S);
        socket(a, "b", 0, 0, Dir.N);
        Node b = node(p, "b", "", 1);
        b.root = new RootAnchor(0, 0, Dir.S);
        socket(b, "a", 0, 0, Dir.N);
        socket(b, "c", 0, 0, Dir.N);
        Node cn = node(p, "c", "", 1);
        cn.root = new RootAnchor(0, 0, Dir.S);
        socket(cn, "b", 0, 0, Dir.N);
        p.root = body.ref();
        Composition c = Composer.compose(p);
        no(c.truncated, "should terminate on its own");
        eq(2L, count(c, "a"));
    }

    public void testBranchingIsCapped() {
        Project p = tail(30, null);
        p.nodes.remove(p.find("tail", "tip"));
        socket(p.find("tail", ""), "tail", 1, 1, Dir.E);
        Composition c = Composer.compose(p);
        yes(c.truncated, "should hit the cap");
        eq(Composer.MAX_INSTANCES, c.instances.size());
    }

    public void testBranchesKeepTheirOwnCount() {
        Project p = tail(3, null);
        p.nodes.remove(p.find("tail", "tip"));
        socket(p.find("tail", ""), "tail", 1, 1, Dir.E);
        // 1 + 2 + 4 segments
        eq(7L, count(Composer.compose(p), "tail"));
    }

    public void testParentGhostMatchesComposition() {
        Project p = bodyAndEye();
        p.find("body", "").anchors.get(0).mirrored = true;
        p.find("body", "").anchors.get(0).dir = Dir.E;
        Node eye = p.find("eye", "");
        List<ParentGhost.Candidate> cands = ParentGhost.candidates(p, eye);
        eq(1, cands.size());
        Xform ghost = ParentGhost.parentInChildFrame(cands.get(0), eye);
        Composition c = Composer.compose(p);
        Xform eyeWorld = c.instances.get(1).xform;
        // parent pixel -> child frame -> world must equal the parent's world placement (identity)
        eq(Xform.IDENTITY, ghost.then(eyeWorld));
    }

    public void testStructureKeyIgnoresPixels() {
        Project p = bodyAndEye();
        String k1 = Composer.compose(p).structureKey();
        p.find("eye", "").set(0, 0, 4);
        eq(k1, Composer.compose(p).structureKey());
        p.find("body", "").anchors.get(0).mirrored = true;
        no(k1.equals(Composer.compose(p).structureKey()), "mirroring changes the structure");
    }
}
