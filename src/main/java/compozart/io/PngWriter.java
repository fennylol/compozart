package compozart.io;

import compozart.model.Palette;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/** Writes 8-bit indexed PNGs (color type 3) with a PLTE chunk and, when needed, a tRNS chunk. */
public final class PngWriter {
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    private PngWriter() {
    }

    public static byte[] write(int width, int height, byte[] indices, Palette palette) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("empty image");
        if (indices.length != width * height) throw new IllegalArgumentException("pixel count does not match size");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(SIGNATURE);

            ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(ihdr);
            d.writeInt(width);
            d.writeInt(height);
            d.writeByte(8);  // bit depth
            d.writeByte(3);  // color type: indexed
            d.writeByte(0);  // compression
            d.writeByte(0);  // filter
            d.writeByte(0);  // interlace
            chunk(out, "IHDR", ihdr.toByteArray());

            int n = palette.size();
            byte[] plte = new byte[n * 3];
            int lastTranslucent = -1;
            for (int i = 0; i < n; i++) {
                int c = palette.argb(i);
                plte[i * 3] = (byte) (c >> 16);
                plte[i * 3 + 1] = (byte) (c >> 8);
                plte[i * 3 + 2] = (byte) c;
                if ((c >>> 24) != 255) lastTranslucent = i;
            }
            chunk(out, "PLTE", plte);
            if (lastTranslucent >= 0) {
                // tRNS may stop at the last non-opaque entry; the rest default to opaque.
                byte[] trns = new byte[lastTranslucent + 1];
                for (int i = 0; i <= lastTranslucent; i++) trns[i] = (byte) (palette.argb(i) >>> 24);
                chunk(out, "tRNS", trns);
            }

            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
            try (DeflaterOutputStream z = new DeflaterOutputStream(raw, deflater)) {
                for (int y = 0; y < height; y++) {
                    z.write(0); // filter: none
                    z.write(indices, y * width, width);
                }
            } finally {
                deflater.end();
            }
            chunk(out, "IDAT", raw.toByteArray());
            chunk(out, "IEND", new byte[0]);
            return out.toByteArray();
        } catch (IOException e) {
            throw new AssertionError(e); // in-memory streams do not fail
        }
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        DataOutputStream d = new DataOutputStream(out);
        byte[] t = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        d.writeInt(data.length);
        d.write(t);
        d.write(data);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        d.writeInt((int) crc.getValue());
    }
}
