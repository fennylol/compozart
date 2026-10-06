package compozart.io;

import compozart.color.OkLab;
import compozart.model.Node;
import compozart.model.Palette;
import compozart.model.Project;
import compozart.text.L10n;

import java.awt.image.BufferedImage;
import java.util.*;

/**
 * Turns an image into a node whose pixels are palette indices.
 * Colors either join the palette (exact) or snap to the nearest existing entry.
 */
public final class ImageImport {
    /** Pixels with less alpha than this become clear. */
    public static final int CLEAR_BELOW = 8;

    public enum Mode {
        /** Reuse palette colors that match exactly and append the rest, most used first, while there is room. */
        ADD_COLORS,
        /** Leave the palette alone and use the closest entry for every pixel. */
        NEAREST
    }

    /** What an import would do, for showing before committing. */
    public record Analysis(int width, int height, int colors, int inPalette, int fresh, int room) {
        public boolean tooLarge() {
            return Math.max(width, height) > Node.MAX_SIZE;
        }

        /** New colors that will not fit and will snap to the nearest entry instead. */
        public int overflow() {
            return Math.max(0, fresh - room);
        }
    }

    /**
     * @param node        the new node
     * @param added       palette entries appended for this image
     * @param approximated pixels whose color was not in the palette and snapped to the nearest entry
     */
    public record Result(Node node, int added, int approximated) {
    }

    private ImageImport() {
    }

    public static Analysis analyze(BufferedImage img, Palette palette) {
        Map<Integer, Integer> counts = colorCounts(img);
        Set<Integer> existing = exactColors(palette);
        int in = 0;
        for (int c : counts.keySet()) if (existing.contains(c)) in++;
        return new Analysis(img.getWidth(), img.getHeight(), counts.size(), in, counts.size() - in,
                Palette.MAX - palette.size());
    }

    /**
     * Builds the node and, in {@link Mode#ADD_COLORS}, appends colors to the project's palette.
     * Non-square images are centered on a square canvas, leaning toward (0, 0).
     */
    public static Result apply(Project project, BufferedImage img, Mode mode, String name, String variant) {
        int w = img.getWidth(), h = img.getHeight();
        int size = Math.max(w, h);
        if (size > Node.MAX_SIZE) {
            throw new IllegalArgumentException(L10n.t("import.error.tooLarge", "width", w, "height", h, "max", Node.MAX_SIZE));
        }
        Palette pal = project.palette;
        Map<Integer, Integer> counts = colorCounts(img);
        Map<Integer, Integer> index = new HashMap<>();
        for (int i = 1; i < pal.size(); i++) index.putIfAbsent(pal.argb(i), i);

        int added = 0;
        if (mode == Mode.ADD_COLORS) {
            List<Map.Entry<Integer, Integer>> missing = new ArrayList<>();
            for (var e : counts.entrySet()) if (!index.containsKey(e.getKey())) missing.add(e);
            missing.sort(Map.Entry.<Integer, Integer>comparingByValue().reversed()
                    .thenComparing(Map.Entry.comparingByKey()));
            for (var e : missing) {
                if (pal.full()) break;
                index.put(e.getKey(), pal.add(new Palette.Swatch(e.getKey(), "")));
                added++;
            }
        }

        Nearest nearest = new Nearest(pal);
        Node node = new Node(name, variant, size);
        int ox = (size - w) / 2, oy = (size - h) / 2;
        int approximated = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = normalize(img.getRGB(x, y));
                int i;
                if (argb == 0) i = 0;
                else {
                    Integer exact = index.get(argb);
                    if (exact != null) i = exact;
                    else {
                        i = nearest.find(argb);
                        approximated++;
                    }
                }
                node.set(ox + x, oy + y, i);
            }
        }
        return new Result(node, added, approximated);
    }

    /** Fully clear for nearly invisible pixels; otherwise unchanged. */
    static int normalize(int argb) {
        return (argb >>> 24) < CLEAR_BELOW ? 0 : argb;
    }

    private static Map<Integer, Integer> colorCounts(BufferedImage img) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int c = normalize(img.getRGB(x, y));
                if (c != 0) counts.merge(c, 1, Integer::sum);
            }
        }
        return counts;
    }

    private static Set<Integer> exactColors(Palette p) {
        Set<Integer> s = new HashSet<>();
        for (int i = 1; i < p.size(); i++) s.add(p.argb(i));
        return s;
    }

    /** Closest palette entry by OKLab distance, with alpha counted too. Index 0 is never chosen for visible pixels. */
    private static final class Nearest {
        private final double[][] lab;
        private final double[] alpha;
        private final Map<Integer, Integer> cache = new HashMap<>();

        Nearest(Palette p) {
            lab = new double[p.size()][];
            alpha = new double[p.size()];
            for (int i = 1; i < p.size(); i++) {
                OkLab.Lab c = OkLab.fromRgb(p.argb(i) & 0xffffff);
                lab[i] = new double[]{c.l(), c.a(), c.b()};
                alpha[i] = (p.argb(i) >>> 24) / 255.0;
            }
        }

        int find(int argb) {
            return cache.computeIfAbsent(argb, c -> {
                OkLab.Lab q = OkLab.fromRgb(c & 0xffffff);
                double qa = (c >>> 24) / 255.0;
                int best = 0;
                double bestD = Double.MAX_VALUE;
                for (int i = 1; i < lab.length; i++) {
                    double dl = q.l() - lab[i][0], da = q.a() - lab[i][1], db = q.b() - lab[i][2], dA = (qa - alpha[i]) * 0.5;
                    double d = dl * dl + da * da + db * db + dA * dA;
                    if (d < bestD) {
                        bestD = d;
                        best = i;
                    }
                }
                return best;
            });
        }
    }
}
