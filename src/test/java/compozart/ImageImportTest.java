package compozart;

import compozart.io.ImageImport;
import compozart.model.Node;
import compozart.model.Palette;
import compozart.model.Project;

import java.awt.image.BufferedImage;

import static compozart.test.Check.*;

public class ImageImportTest {
    static BufferedImage image(int w, int h, int... argb) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < argb.length; i++) img.setRGB(i % w, i / w, argb[i]);
        return img;
    }

    static final int MAUVE = 0xffcba6f7;      // index 4 in Mocha
    static final int ODD = 0xff123456;        // not in Mocha
    static final int ALMOST_RED = 0xfff38ba9; // one step from Mocha red (index 5)

    public void testAnalysis() {
        Project p = Project.createDefault();
        ImageImport.Analysis a = ImageImport.analyze(image(2, 2, MAUVE, ODD, ODD, 0x00000000), p.palette);
        eq(2, a.colors());
        eq(1, a.inPalette());
        eq(1, a.fresh());
        eq(Palette.MAX - 27, a.room());
        eq(0, a.overflow());
    }

    public void testAddColorsReusesAndAppends() {
        Project p = Project.createDefault();
        ImageImport.Result r = ImageImport.apply(p, image(2, 2, MAUVE, ODD, ODD, 0x00000000), ImageImport.Mode.ADD_COLORS, "x", "");
        Node n = r.node();
        eq(1, r.added());
        eq(0, r.approximated());
        eq(28, p.palette.size());
        eq(ODD, p.palette.argb(27));
        eq(4, n.get(0, 0));
        eq(27, n.get(1, 0));
        eq(27, n.get(0, 1));
        eq(0, n.get(1, 1));
    }

    public void testNearestLeavesPaletteAlone() {
        Project p = Project.createDefault();
        ImageImport.Result r = ImageImport.apply(p, image(2, 1, ALMOST_RED, MAUVE), ImageImport.Mode.NEAREST, "x", "");
        eq(27, p.palette.size());
        eq(5, r.node().get(0, 0));
        eq(4, r.node().get(1, 0));
        eq(1, r.approximated());
    }

    public void testNearlyClearBecomesClear() {
        Project p = Project.createDefault();
        Node n = ImageImport.apply(p, image(2, 1, 0x05ffffff, 0x80cba6f7), ImageImport.Mode.ADD_COLORS, "x", "").node();
        eq(0, n.get(0, 0));
        eq(0x80cba6f7, p.palette.argb(n.get(1, 0))); // partial alpha is kept as its own entry
    }

    public void testNonSquareIsCentered() {
        Project p = Project.createDefault();
        Node n = ImageImport.apply(p, image(4, 1, MAUVE, MAUVE, MAUVE, MAUVE), ImageImport.Mode.ADD_COLORS, "x", "").node();
        eq(4, n.size());
        eq(4, n.get(0, 1)); // row (4 - 1) / 2 = 1
        eq(0, n.get(0, 0));
        Node tall = ImageImport.apply(p, image(1, 3, MAUVE, MAUVE, MAUVE), ImageImport.Mode.ADD_COLORS, "y", "").node();
        eq(4, tall.get(1, 2));
        eq(0, tall.get(0, 0));
    }

    public void testFullPaletteFallsBackToNearest() {
        Project p = Project.createDefault();
        while (!p.palette.full()) p.palette.add(new Palette.Swatch(0xff000000 | p.palette.size(), ""));
        ImageImport.Result r = ImageImport.apply(p, image(1, 1, ODD), ImageImport.Mode.ADD_COLORS, "x", "");
        eq(0, r.added());
        eq(1, r.approximated());
        yes(r.node().get(0, 0) > 0, "snaps to a visible color");
    }

    public void testMostUsedColorsGetTheRoom() {
        Project p = Project.createDefault();
        while (p.palette.size() < Palette.MAX - 1) p.palette.add(new Palette.Swatch(0xff000000 | p.palette.size(), ""));
        // one slot left: the color used three times wins it
        ImageImport.Result r = ImageImport.apply(p, image(4, 1, 0xffaa0000, 0xff00aa00, 0xff00aa00, 0xff00aa00),
                ImageImport.Mode.ADD_COLORS, "x", "");
        eq(1, r.added());
        eq(0xff00aa00, p.palette.argb(Palette.MAX - 1));
        eq(1, r.approximated());
    }

    public void testTooLarge() {
        Project p = Project.createDefault();
        BufferedImage big = new BufferedImage(Node.MAX_SIZE + 1, 1, BufferedImage.TYPE_INT_ARGB);
        yes(ImageImport.analyze(big, p.palette).tooLarge(), "flagged");
        throwsA(IllegalArgumentException.class, () -> ImageImport.apply(p, big, ImageImport.Mode.NEAREST, "x", ""));
    }
}
