package critter;

import critter.color.OkLab;
import critter.model.Palette;

import static critter.test.Check.*;

public class OkLabTest {
    public void testReferenceValues() {
        OkLab.Lab white = OkLab.fromRgb(0xffffff);
        near(1.0, white.l(), 1e-4);
        near(0.0, white.a(), 1e-4);
        near(0.0, white.b(), 1e-4);
        OkLab.Lab black = OkLab.fromRgb(0x000000);
        near(0.0, black.l(), 1e-9);
        // sRGB red is roughly L 0.628, a 0.225, b 0.126
        OkLab.Lab red = OkLab.fromRgb(0xff0000);
        near(0.628, red.l(), 1e-3);
        near(0.225, red.a(), 1e-3);
        near(0.126, red.b(), 1e-3);
    }

    public void testRoundTripEveryMochaColor() {
        Palette p = Palette.catppuccinMocha();
        for (int i = 1; i < p.size(); i++) {
            int rgb = p.argb(i) & 0xffffff;
            OkLab.Lab lab = OkLab.fromRgb(rgb);
            eq(rgb, OkLab.toRgb(lab));
            OkLab.Mapped m = OkLab.map(lab.toLch());
            eq(rgb, m.rgb());
            no(m.clamped(), "palette colors are in gamut");
        }
    }

    public void testGamutMappingKeepsLightnessAndHue() {
        OkLab.Lch wild = new OkLab.Lch(0.7, 0.4, 150);
        OkLab.Mapped m = OkLab.map(wild);
        yes(m.clamped(), "should be clamped");
        OkLab.Lch back = OkLab.fromRgb(m.rgb()).toLch();
        near(0.7, back.l(), 0.01);
        near(150, back.h(), 2);
        yes(back.c() < 0.4, "chroma reduced");
    }

    public void testLchConversion() {
        OkLab.Lab lab = new OkLab.Lab(0.5, 0.1, -0.1);
        OkLab.Lab round = lab.toLch().toLab();
        near(lab.a(), round.a(), 1e-12);
        near(lab.b(), round.b(), 1e-12);
        near(315, lab.toLch().h(), 1e-9);
    }
}
