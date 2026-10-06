package compozart;

import compozart.compose.Composer;
import compozart.compose.Composition;
import compozart.io.AsepriteWriter;
import compozart.io.Exporter;
import compozart.io.PngWriter;
import compozart.model.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static compozart.test.Check.*;

public class FormatsTest {
    static Palette palette() {
        Palette p = Palette.catppuccinMocha();
        p.add(new Palette.Swatch(0x80ff0000, "half red"));
        return p;
    }

    public void testIndexedPngReadsBack() throws Exception {
        Palette pal = palette();
        byte[] px = {0, 1, 2, 27, 26, 0};
        byte[] png = PngWriter.write(3, 2, px, pal);
        // PLTE holds exactly the palette (ImageIO pads its color model to 256, so read the chunk itself)
        int plte = new String(png, java.nio.charset.StandardCharsets.ISO_8859_1).indexOf("PLTE");
        eq(28 * 3, ByteBuffer.wrap(png, plte - 4, 4).getInt());
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        eq(3, img.getWidth());
        eq(2, img.getHeight());
        IndexColorModel cm = (IndexColorModel) img.getColorModel();
        eq(0, cm.getAlpha(0));
        eq(128, cm.getAlpha(27));
        eq(255, cm.getAlpha(1));
        eq(0xf5e0dc, cm.getRGB(1) & 0xffffff);
        int[] row = new int[3];
        img.getRaster().getPixels(0, 1, 3, 1, row);
        eq(27, row[0]);
        eq(26, row[1]);
        eq(0, row[2]);
    }

    public void testOpaquePaletteStillHasTrnsForClear() throws Exception {
        byte[] png = PngWriter.write(1, 1, new byte[]{0}, Palette.catppuccinMocha());
        yes(new String(png, java.nio.charset.StandardCharsets.ISO_8859_1).contains("tRNS"), "index 0 needs tRNS");
    }

    /** Minimal reader for what AsepriteWriter produces. */
    record Ase(int width, int height, int depth, int colors, List<String> layers, List<int[]> cels, List<byte[]> pixels,
               List<Integer> paletteArgb) {
    }

    static Ase readAse(byte[] data) throws Exception {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        eq("file size", data.length, b.getInt(0));
        eq(0xA5E0, b.getShort(4) & 0xffff);
        eq(1, b.getShort(6) & 0xffff);
        int w = b.getShort(8) & 0xffff, h = b.getShort(10) & 0xffff, depth = b.getShort(12) & 0xffff;
        eq(0, data[28] & 0xff); // transparent index
        int colors = b.getShort(32) & 0xffff;
        int pos = 128;
        int frameSize = b.getInt(pos);
        eq("frame size", data.length - 128, frameSize);
        eq(0xF1FA, b.getShort(pos + 4) & 0xffff);
        int chunks = b.getInt(pos + 12);
        pos += 16;
        List<String> layers = new ArrayList<>();
        List<int[]> cels = new ArrayList<>();
        List<byte[]> pixels = new ArrayList<>();
        List<Integer> pal = new ArrayList<>();
        for (int i = 0; i < chunks; i++) {
            int size = b.getInt(pos);
            int type = b.getShort(pos + 4) & 0xffff;
            int d = pos + 6;
            if (type == 0x2004) {
                int len = b.getShort(d + 16) & 0xffff;
                layers.add(new String(data, d + 18, len, java.nio.charset.StandardCharsets.UTF_8));
            } else if (type == 0x2005) {
                int layer = b.getShort(d) & 0xffff, x = b.getShort(d + 2), y = b.getShort(d + 4);
                eq(2, b.getShort(d + 7) & 0xffff);
                int cw = b.getShort(d + 16) & 0xffff, ch = b.getShort(d + 18) & 0xffff;
                cels.add(new int[]{layer, x, y, cw, ch});
                Inflater inf = new Inflater();
                inf.setInput(data, d + 20, size - 26);
                byte[] out = new byte[cw * ch];
                eq(out.length, inf.inflate(out));
                inf.end();
                pixels.add(out);
            } else if (type == 0x2019) {
                int n = b.getInt(d);
                int p = d + 20;
                for (int k = 0; k < n; k++) {
                    int flags = b.getShort(p) & 0xffff;
                    int argb = ((data[p + 5] & 0xff) << 24) | ((data[p + 2] & 0xff) << 16) | ((data[p + 3] & 0xff) << 8) | (data[p + 4] & 0xff);
                    pal.add(argb);
                    p += 6;
                    if ((flags & 1) != 0) p += 2 + (b.getShort(p) & 0xffff);
                }
                eq("palette chunk length", pos + size, p);
            }
            pos += size;
        }
        eq("chunks fill the frame", data.length, pos);
        return new Ase(w, h, depth, colors, layers, cels, pixels, pal);
    }

    public void testAsepriteStructure() throws Exception {
        Palette pal = palette();
        byte[] l0 = new byte[4 * 3];
        byte[] l1 = new byte[4 * 3];
        l0[0] = 1;
        l1[1 * 4 + 2] = 27;
        l1[2 * 4 + 3] = 5;
        byte[] data = AsepriteWriter.write(4, 3, pal, List.of(new AsepriteWriter.Layer("layer 0", l0),
                new AsepriteWriter.Layer("layer 1", l1), new AsepriteWriter.Layer("empty", new byte[12])));
        Ase a = readAse(data);
        eq(4, a.width);
        eq(3, a.height);
        eq(8, a.depth);
        eq(28, a.colors);
        eq(List.of("layer 0", "layer 1", "empty"), a.layers);
        eq(2, a.cels.size()); // the empty layer has no cel
        eq("0,0,0,1,1", join(a.cels.get(0)));
        eq("1,2,1,2,2", join(a.cels.get(1)));
        eq(27, a.pixels.get(1)[0] & 0xff);
        eq(0, a.pixels.get(1)[1] & 0xff);
        eq(5, a.pixels.get(1)[3] & 0xff);
        eq(28, a.paletteArgb.size());
        eq(0x80ff0000, (int) a.paletteArgb.get(27));
        eq(0xfff5e0dc, (int) a.paletteArgb.get(1));
    }

    static String join(int[] v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) sb.append(i > 0 ? "," : "").append(v[i]);
        return sb.toString();
    }

    static Composition twoLayers() {
        Project p = new Project(Palette.catppuccinMocha());
        Node body = new Node("body", "", 2);
        body.set(0, 0, 1);
        body.set(1, 1, 2);
        NamedAnchor back = new NamedAnchor("spot", 1, 1, Dir.E);
        back.layerModifier = -3;
        body.anchors.add(back);
        Node spot = new Node("spot", "", 1);
        spot.root = new RootAnchor(0, 0, Dir.W);
        spot.set(0, 0, 3);
        p.nodes.add(body);
        p.nodes.add(spot);
        p.root = body.ref();
        return Composer.compose(p);
    }

    public void testFlatExportPadAndScale() throws Exception {
        Composition c = twoLayers();
        eq(3, c.width);
        eq(2, c.height);
        byte[] png = Exporter.export(Exporter.Format.FLAT_PNG, c, Palette.catppuccinMocha(), 1, 2);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        eq(10, img.getWidth());
        eq(8, img.getHeight());
        eq(1, img.getRaster().getSample(2, 2, 0));
        eq(1, img.getRaster().getSample(3, 3, 0));
        eq(0, img.getRaster().getSample(1, 1, 0));
        eq(3, img.getRaster().getSample(6, 4, 0));
    }

    public void testLayerZip() throws Exception {
        Composition c = twoLayers();
        byte[] zip = Exporter.export(Exporter.Format.LAYER_ZIP, c, Palette.catppuccinMocha(), 0, 1);
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                names.add(e.getName());
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                in.transferTo(buf);
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(buf.toByteArray()));
                eq(3, img.getWidth());
                yes(img.getColorModel() instanceof IndexColorModel, "indexed");
            }
        }
        eq(List.of("layer_00.png", "layer_03.png"), names);
    }

    public void testAsepriteExportLayers() throws Exception {
        Ase a = readAse(Exporter.export(Exporter.Format.ASEPRITE, twoLayers(), Palette.catppuccinMocha(), 2, 1));
        eq(7, a.width);
        eq(6, a.height);
        eq(List.of("layer 0", "layer 3"), a.layers);
        eq("0,4,3,1,1", join(a.cels.get(0)));
    }

    public void testEmptyExportFails() {
        Composition c = Composer.compose(Project.createDefault());
        throwsA(IllegalStateException.class, () -> Exporter.export(Exporter.Format.FLAT_PNG, c, Palette.catppuccinMocha(), 0, 1));
    }
}
