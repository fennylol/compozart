package compozart.io;

import compozart.compose.Composition;
import compozart.model.Palette;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Turns a composition into export files. All exports crop to the creature's bounds plus padding.
 * Layer numbers are shifted so the lowest non-empty layer becomes 0.
 */
public final class Exporter {
    public enum Format {
        FLAT_PNG("Flat PNG", ".png"),
        LAYER_ZIP("Layer zip", ".zip"),
        ASEPRITE("Aseprite", ".aseprite"),
        GODOT_2D("Godot 2D scene", ".tscn"),
        GODOT_3D("Godot 3D scene", ".tscn");

        public final String label, extension;

        Format(String label, String extension) {
            this.label = label;
            this.extension = extension;
        }

        public boolean scalable() {
            return this == FLAT_PNG || this == LAYER_ZIP;
        }

        /** Image formats crop to the creature and take padding; scenes keep every part as its own sprite. */
        public boolean padded() {
            return !godot();
        }

        public boolean godot() {
            return this == GODOT_2D || this == GODOT_3D;
        }
    }

    /** An indexed image. */
    public record Raster(int width, int height, byte[] indices) {
    }

    private Exporter() {
    }

    public static byte[] export(Format format, Composition c, Palette palette, int padding, int scale) {
        if (c.isEmpty()) throw new IllegalStateException("There is nothing to export: the creature has no pixels.");
        if (padding < 0) throw new IllegalArgumentException("padding must not be negative");
        if (scale < 1) throw new IllegalArgumentException("scale must be at least 1");
        return switch (format) {
            case FLAT_PNG -> {
                Raster r = scale(pad(c.flat, c.width, c.height, padding), scale);
                yield PngWriter.write(r.width, r.height, r.indices, palette);
            }
            case LAYER_ZIP -> layerZip(c, palette, padding, scale);
            case ASEPRITE -> aseprite(c, palette, padding);
            case GODOT_2D, GODOT_3D -> godot(format, c, palette, GodotScene.DEFAULT_PIXEL_SIZE);
        };
    }

    /** A Godot scene as UTF-8 text. {@code pixelSize} is 3D units per pixel and only matters for the 3D scene. */
    public static byte[] godot(Format format, Composition c, Palette palette, double pixelSize) {
        if (!format.godot()) throw new IllegalArgumentException(format + " is not a Godot format");
        if (pixelSize <= 0) throw new IllegalArgumentException("pixel size must be positive");
        return GodotScene.write(c, palette, format == Format.GODOT_3D, pixelSize).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public static String layerName(int shiftedLayer) {
        return "layer " + shiftedLayer;
    }

    private static byte[] layerZip(Composition c, Palette palette, int padding, int scale) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int base = c.layers.firstKey();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                for (Map.Entry<Integer, byte[]> e : c.layers.entrySet()) {
                    Raster r = scale(pad(e.getValue(), c.width, c.height, padding), scale);
                    zip.putNextEntry(new ZipEntry(String.format("layer_%02d.png", e.getKey() - base)));
                    zip.write(PngWriter.write(r.width, r.height, r.indices, palette));
                    zip.closeEntry();
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static byte[] aseprite(Composition c, Palette palette, int padding) {
        int base = c.layers.firstKey();
        List<AsepriteWriter.Layer> layers = new ArrayList<>();
        int w = c.width + 2 * padding, h = c.height + 2 * padding;
        for (Map.Entry<Integer, byte[]> e : c.layers.entrySet()) {
            Raster r = pad(e.getValue(), c.width, c.height, padding);
            layers.add(new AsepriteWriter.Layer(layerName(e.getKey() - base), r.indices));
        }
        return AsepriteWriter.write(w, h, palette, layers);
    }

    static Raster pad(byte[] px, int w, int h, int p) {
        if (p == 0) return new Raster(w, h, px);
        int nw = w + 2 * p, nh = h + 2 * p;
        byte[] out = new byte[nw * nh];
        for (int y = 0; y < h; y++) System.arraycopy(px, y * w, out, (y + p) * nw + p, w);
        return new Raster(nw, nh, out);
    }

    static Raster scale(Raster r, int s) {
        if (s == 1) return r;
        int nw = r.width * s, nh = r.height * s;
        byte[] out = new byte[nw * nh];
        for (int y = 0; y < nh; y++) {
            int sy = y / s;
            for (int x = 0; x < nw; x++) out[y * nw + x] = r.indices[sy * r.width + x / s];
        }
        return new Raster(nw, nh, out);
    }
}
