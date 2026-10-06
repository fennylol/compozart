package critter;

import critter.model.*;

import static critter.test.Check.*;

public class SymTest {
    public void testGroupIsClosed() {
        for (Sym a : Sym.ALL) {
            for (Sym b : Sym.ALL) yes(Sym.ALL.contains(a.then(b)), a + " then " + b + " left the group");
            eq(Sym.IDENTITY, a.then(a.inverse()));
        }
    }

    public void testFourRotationsAndFourReflections() {
        eq(4L, Sym.ALL.stream().filter(Sym::mirrored).count());
        eq(4L, Sym.ALL.stream().filter(s -> !s.mirrored()).count());
    }

    public void testMappingIsUniquePerHandedness() {
        for (Dir from : Dir.values()) {
            for (Dir to : Dir.values()) {
                for (boolean m : new boolean[]{false, true}) {
                    Sym s = Sym.mapping(from, to, m);
                    eq(to, s.apply(from));
                    eq(m, s.mirrored());
                }
            }
        }
    }

    public void testClockwiseOnScreen() {
        Sym cw = Sym.mapping(Dir.N, Dir.E, false);
        eq(Dir.S, cw.apply(Dir.E));
        eq(Dir.W, cw.apply(Dir.S));
    }

    public void testXformInverseAndThen() {
        for (Sym s : Sym.ALL) {
            Xform xf = new Xform(s, 5, -3);
            Xform round = xf.then(xf.inverse());
            eq(Xform.IDENTITY, round);
            eq(7, xf.inverse().x(xf.x(7, 2), xf.y(7, 2)));
            eq(2, xf.inverse().y(xf.x(7, 2), xf.y(7, 2)));
        }
    }

    public void testAttachSharesOneEdge() {
        // Parent socket at (3,2) pointing N; child plug at (1,3) pointing S. No rotation needed.
        NamedAnchor socket = new NamedAnchor("eye", 3, 2, Dir.N);
        RootAnchor plug = new RootAnchor(1, 3, Dir.S);
        Xform xf = Xform.attach(Xform.IDENTITY, socket, plug);
        eq(Sym.IDENTITY, xf.sym());
        eq(3, xf.x(1, 3));
        eq(1, xf.y(1, 3));
    }

    public void testAttachRotates() {
        // Socket points E at (7,4); plug points S, so the child turns until its plug points W.
        NamedAnchor socket = new NamedAnchor("arm", 7, 4, Dir.E);
        RootAnchor plug = new RootAnchor(2, 3, Dir.S);
        Xform xf = Xform.attach(Xform.IDENTITY, socket, plug);
        eq(Dir.W, xf.apply(Dir.S));
        eq(8, xf.x(2, 3));
        eq(4, xf.y(2, 3));
        // The pixel just inside the child (opposite its plug direction) extends further east.
        eq(9, xf.x(2, 2));
        eq(4, xf.y(2, 2));
    }

    public void testMirrorReflectsAcrossRootArrow() {
        NamedAnchor socket = new NamedAnchor("arm", 4, 0, Dir.N);
        RootAnchor plug = new RootAnchor(2, 5, Dir.S);
        Xform plain = Xform.attach(Xform.IDENTITY, socket, plug);
        socket.mirrored = true;
        Xform mirrored = Xform.attach(Xform.IDENTITY, socket, plug);
        // The plug pixel stays put.
        eq(plain.x(2, 5), mirrored.x(2, 5));
        eq(plain.y(2, 5), mirrored.y(2, 5));
        // A pixel to the plug's right lands on the other side.
        eq(plain.x(3, 4) - 4, 4 - mirrored.x(3, 4));
        eq(plain.y(3, 4), mirrored.y(3, 4));
    }

    public void testMirrorOnMirroredParentCancels() {
        Xform parent = new Xform(Sym.mapping(Dir.N, Dir.N, true), 0, 0);
        NamedAnchor socket = new NamedAnchor("x", 0, 0, Dir.N);
        socket.mirrored = true;
        Xform child = Xform.attach(parent, socket, new RootAnchor(0, 0, Dir.S));
        no(child.mirrored(), "two mirrors should cancel");
        socket.mirrored = false;
        yes(Xform.attach(parent, socket, new RootAnchor(0, 0, Dir.S)).mirrored(), "handedness should be inherited");
    }
}
