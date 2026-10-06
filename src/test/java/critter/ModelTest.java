package critter;

import critter.model.*;

import static critter.test.Check.*;

public class ModelTest {
    public void testMochaPalette() {
        Palette p = Palette.catppuccinMocha();
        eq(27, p.size());
        eq(0, p.argb(0));
        eq("rosewater", p.get(1).name());
        eq(0xfff5e0dc, p.argb(1));
        eq(0xff11111b, p.argb(26));
    }

    public void testIndexZeroIsLocked() {
        Palette p = Palette.catppuccinMocha();
        throwsA(IllegalArgumentException.class, () -> p.set(0, new Palette.Swatch(0xffffffff, "x")));
        throwsA(IllegalArgumentException.class, () -> p.remove(0, 1));
        throwsA(IllegalArgumentException.class, () -> p.move(0, 3));
        throwsA(IllegalArgumentException.class, () -> p.move(3, 0));
    }

    public void testPaletteCapacity() {
        Palette p = new Palette();
        for (int i = 1; i < Palette.MAX; i++) p.add(new Palette.Swatch(0xff000000 | i, ""));
        yes(p.full(), "should be full");
        throwsA(IllegalStateException.class, () -> p.add(new Palette.Swatch(0, "")));
    }

    public void testMoveKeepsAppearance() {
        Project proj = Project.createDefault();
        Node n = proj.nodes.get(0);
        n.set(0, 0, 2);
        n.set(1, 0, 5);
        int c2 = proj.palette.argb(2), c5 = proj.palette.argb(5);
        int[] map = proj.palette.move(2, 5);
        proj.remapPixels(map);
        eq(c2, proj.palette.argb(n.get(0, 0)));
        eq(c5, proj.palette.argb(n.get(1, 0)));
        eq(5, n.get(0, 0));
        eq(4, n.get(1, 0));
    }

    public void testRemoveRemaps() {
        Project proj = Project.createDefault();
        Node n = proj.nodes.get(0);
        n.set(0, 0, 3);
        n.set(1, 0, 4);
        n.set(2, 0, 8);
        int c8 = proj.palette.argb(8);
        int c4 = proj.palette.argb(4);
        yes(proj.usesColor(3), "uses 3");
        proj.remapPixels(proj.palette.remove(3, 8));
        eq(26, proj.palette.size());
        eq(c8, proj.palette.argb(n.get(0, 0)));
        eq(c4, proj.palette.argb(n.get(1, 0)));
        eq(c8, proj.palette.argb(n.get(2, 0)));
    }

    public void testResizeFixedCorners() {
        Node n = new Node("a", "", 4);
        n.set(0, 0, 1);
        n.set(3, 3, 2);
        n.root = new RootAnchor(3, 3, Dir.S);
        n.anchors.add(new NamedAnchor("b", 0, 0, Dir.N));

        Node grow = n.copy();
        eq(0, grow.resize(8, 2, 2)); // keep bottom-right
        eq(8, grow.size());
        eq(1, grow.get(4, 4));
        eq(2, grow.get(7, 7));
        eq(new RootAnchor(7, 7, Dir.S), grow.root);
        eq(4, grow.anchors.get(0).x);

        Node center = n.copy();
        eq(0, center.resize(6, 1, 1));
        eq(1, center.get(1, 1));
        eq(2, center.get(4, 4));

        Node shrink = n.copy();
        eq(1, shrink.anchorsLostByResize(2, 0, 0));
        eq(1, shrink.resize(2, 0, 0)); // root anchor at (3,3) falls off
        eq(null, shrink.root);
        eq(1, shrink.anchors.size());
        eq(1, shrink.get(0, 0));
    }

    public void testCopyIsDeep() {
        Project p = Project.createDefault();
        p.nodes.get(0).anchors.add(new NamedAnchor("x", 1, 1, Dir.E));
        Project c = p.copy();
        c.nodes.get(0).set(0, 0, 9);
        c.nodes.get(0).anchors.get(0).mirrored = true;
        c.palette.set(1, new Palette.Swatch(0xff000000, "black"));
        eq(0, p.nodes.get(0).get(0, 0));
        no(p.nodes.get(0).anchors.get(0).mirrored, "anchor copied");
        eq("rosewater", p.palette.get(1).name());
    }

    public void testRenameTarget() {
        Project p = Project.createDefault();
        p.nodes.get(0).anchors.add(new NamedAnchor("eye", 1, 1, Dir.E));
        p.renameTarget("body", "torso");
        p.renameTarget("eye", "eyes");
        eq(new NodeRef("torso", ""), p.root);
        eq("eyes", p.nodes.get(0).anchors.get(0).target);
    }

    public void testUniqueNames() {
        Project p = Project.createDefault();
        eq("body 2", p.uniqueName("body", ""));
        eq("body", p.uniqueName("body", "x"));
        eq("copy", p.uniqueVariant("body", "copy"));
    }
}
