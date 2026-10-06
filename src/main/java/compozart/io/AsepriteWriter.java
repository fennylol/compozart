package compozart.io;

import compozart.model.Palette;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.Deflater;

/**
 * Writes single-frame indexed {@code .aseprite} files.
 * Format reference: https://github.com/aseprite/aseprite/blob/main/docs/ase-file-specs.md
 */
public final class AsepriteWriter {
    private static final int CHUNK_LAYER = 0x2004;
    private static final int CHUNK_CEL = 0x2005;
    private static final int CHUNK_PALETTE = 0x2019;

    /** A layer of the sprite. Pixels cover the whole sprite; the writer crops each cel to its content. */
    public record Layer(String name, byte[] indices) {
    }

    private AsepriteWriter() {
    }

    /** @param layers bottom layer first */
    public static byte[] write(int width, int height, Palette palette, List<Layer> layers) {
        if (width <= 0 || height <= 0 || width > 65535 || height > 65535) throw new IllegalArgumentException("bad sprite size");
        Buf frame = new Buf();
        int chunks = 0;

        // Palette
        Buf pal = new Buf();
        int n = palette.size();
        pal.dword(n);
        pal.dword(0);
        pal.dword(n - 1);
        pal.zeros(8);
        for (int i = 0; i < n; i++) {
            Palette.Swatch s = palette.get(i);
            boolean named = !s.name().isEmpty();
            pal.word(named ? 1 : 0);
            int c = s.argb();
            pal.byte_(c >> 16);
            pal.byte_(c >> 8);
            pal.byte_(c);
            pal.byte_(c >>> 24);
            if (named) pal.string(s.name());
        }
        chunk(frame, CHUNK_PALETTE, pal);
        chunks++;

        for (Layer layer : layers) {
            Buf l = new Buf();
            l.word(1 | 2);  // visible, editable
            l.word(0);      // normal layer
            l.word(0);      // child level
            l.word(0);
            l.word(0);
            l.word(0);      // blend mode: normal
            l.byte_(255);   // opacity
            l.zeros(3);
            l.string(layer.name());
            chunk(frame, CHUNK_LAYER, l);
            chunks++;
        }

        for (int li = 0; li < layers.size(); li++) {
            byte[] px = layers.get(li).indices();
            int x0 = width, y0 = height, x1 = -1, y1 = -1;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (px[y * width + x] == 0) continue;
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                }
            }
            if (x1 < 0) continue;
            int w = x1 - x0 + 1, h = y1 - y0 + 1;
            byte[] crop = new byte[w * h];
            for (int y = 0; y < h; y++) System.arraycopy(px, (y0 + y) * width + x0, crop, y * w, w);

            Buf c = new Buf();
            c.word(li);
            c.word(x0);
            c.word(y0);
            c.byte_(255);  // opacity
            c.word(2);     // compressed image
            c.word(0);     // z-index
            c.zeros(5);
            c.word(w);
            c.word(h);
            c.bytes(zlib(crop));
            chunk(frame, CHUNK_CEL, c);
            chunks++;
        }

        Buf file = new Buf();
        int frameSize = 16 + frame.size();
        int fileSize = 128 + frameSize;
        // Header (128 bytes)
        file.dword(fileSize);
        file.word(0xA5E0);
        file.word(1);          // frames
        file.word(width);
        file.word(height);
        file.word(8);          // color depth: indexed
        file.dword(1);         // flags: layer opacity is valid
        file.word(100);        // speed (deprecated)
        file.dword(0);
        file.dword(0);
        file.byte_(0);         // transparent index
        file.zeros(3);
        file.word(n);          // number of colors
        file.byte_(1);         // pixel width
        file.byte_(1);         // pixel height
        file.word(0);          // grid x
        file.word(0);          // grid y
        file.word(16);         // grid width
        file.word(16);         // grid height
        file.zeros(84);
        // Frame header (16 bytes)
        file.dword(frameSize);
        file.word(0xF1FA);
        file.word(Math.min(chunks, 0xFFFF));
        file.word(100);        // frame duration (ms)
        file.zeros(2);
        file.dword(chunks);
        file.bytes(frame.toByteArray());
        return file.toByteArray();
    }

    private static void chunk(Buf frame, int type, Buf data) {
        frame.dword(6 + data.size());
        frame.word(type);
        frame.bytes(data.toByteArray());
    }

    private static byte[] zlib(byte[] data) {
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            d.setInput(data);
            d.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            while (!d.finished()) out.write(buf, 0, d.deflate(buf));
            return out.toByteArray();
        } finally {
            d.end();
        }
    }

    /** Little-endian byte buffer. */
    private static final class Buf extends ByteArrayOutputStream {
        void byte_(int v) {
            write(v & 0xff);
        }

        void word(int v) {
            write(v & 0xff);
            write((v >> 8) & 0xff);
        }

        void dword(int v) {
            word(v);
            word(v >>> 16);
        }

        void zeros(int n) {
            for (int i = 0; i < n; i++) write(0);
        }

        void bytes(byte[] b) {
            write(b, 0, b.length);
        }

        void string(String s) {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            word(b.length);
            bytes(b);
        }
    }
}
